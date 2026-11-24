# Ollama, embeddings y pgvector en Dependency Intelligence

Estado de la configuración local: 23 de noviembre de 2026.

## Qué quedó instalado y comprobado

- Ollama para Windows, versión cliente `0.40.17`, instalado mediante el instalador oficial. La firma del ejecutable instalado aparece como válida y emitida por Ollama Inc.
- Modelo `embeddinggemma-2:latest`, descargado con Ollama. `/api/tags` reportó el digest completo `f4981a15b99858ad1f2e0729416e526be7b4f428291b40082b9e4f7b27b5f62f`.
- Una llamada real a `POST /api/embed` generó un vector de **768 valores numéricos finitos**.
- `.env` local contiene `OLLAMA_BASE_URL=http://ollama:11434`, el nombre/digest del modelo y `OLLAMA_MODELS_HOST_PATH` apuntando a `%USERPROFILE%\.ollama\models`. `.env` está ignorado por Git; estos valores no se añadieron al repositorio.
- La API de Ollama nativa en Windows sigue escuchando únicamente en `127.0.0.1:11434`. Para evitar permisos de firewall, Compose ahora define el contenedor oficial `ollama/ollama:0.40.17` en la red interna `assistant_internal`, sin publicar el puerto en Windows. Monta el caché local de modelos como solo lectura; el backend apunta al nombre del servicio `ollama`. **La configuración está preparada, pero todavía no probada en contenedor:** Docker Desktop no estaba ejecutándose durante este bloque, así que no pude arrancar el servicio ni verificar el acceso desde backend.
- No se configuró Gemini: faltan `GEMINI_API_KEY` y `GEMINI_MODEL`. No se guardan claves en Git ni se envían en esta guía.

## Cómo se conecta cada parte

```text
Navegador
  └─ Angular: pantalla Asistente
       ├─ POST /api/organizations/{id}/assistant/index
       └─ POST /api/organizations/{id}/assistant/ask
              │
              ▼
       Spring Boot dentro de Docker
          ├─ Ollama en Compose: http://ollama:11434 (red interna, sin puerto publicado)
          ├─ PostgreSQL + extensión pgvector
          └─ Gemini: generación de respuesta, cuando sus credenciales estén configuradas
```

El backend y Ollama comparten la red Docker `assistant_internal`, marcada `internal: true`; no se necesita que el proceso nativo de Windows escuche en otra interfaz ni abrir una regla de firewall. El contenedor Ollama monta `OLLAMA_MODELS_HOST_PATH` en `/root/.ollama/models` como solo lectura y sirve el modelo ya descargado. La URL interna en `.env` indica la configuración deseada, no prueba que el contenedor esté funcionando.

La integración sigue la arquitectura hexagonal:

- **Application:** `AskAssistant` e `IndexDocuments` son puertos de entrada; los casos de uso coordinan la indexación y el RAG. Sus puertos de salida describen embeddings, recuperación, generación y persistencia, sin depender de Spring ni JPA.
- **Infrastructure:** `OllamaEmbeddingsAdapter` llama a `/api/tags` y `/api/embed`; `GeminiAnswerAdapter` llama a `generateContent`; `JdbcAssistantKnowledgeBase` guarda y recupera los vectores y las conversaciones.
- **API:** `AssistantController` exige `assistant.query` y limita cada operación a repositorios visibles para el usuario y documentos accesibles con `document.read`.
- **Frontend:** `AssistantWorkspace` expone la preparación de documentos, preguntas y citas con extractos. Todavía no tiene una vista de historial que permita reanudar conversaciones tras recargar.

## Qué es un embedding

Un embedding representa un texto como una lista de números. Ollama transforma cada fragmento documental —y luego cada pregunta— en un vector. En este equipo, el modelo devolvió 768 números por entrada. Fragmentos y preguntas deben vectorizarse con el **mismo modelo y revisión** para que su distancia tenga sentido.

El sistema no fija de antemano la dimensión. En la primera indexación registra el modelo, digest/revisión y dimensión observada. Ollama comprueba que el nombre y digest configurados aparezcan en `/api/tags`; la aplicación valida que todos los vectores del lote tengan el mismo tamaño y valores finitos.

## Cómo trabaja pgvector

pgvector es una extensión de PostgreSQL que añade un tipo para vectores y operadores de distancia. Así, los vectores permanecen junto con sus fragmentos, documentos, organizaciones y relaciones de acceso.

La migración [V29](../backend/infrastructure/src/main/resources/db/migration/V29__enable_tenant_scoped_document_vectors.sql) hace lo siguiente:

1. Activa `vector` en PostgreSQL.
2. Crea `document_chunk_vectors`, relacionada mediante una clave foránea compuesta con `document_chunk_embeddings`.
3. Guarda `embedding` con tipo `vector` sin declarar una dimensión fija. Un trigger compara `vector_dims(embedding)` con la dimensión registrada en `embedding_models` y rechaza discrepancias.
4. Fuerza RLS y exige que `organization_id` coincida con el tenant de la transacción.

Cuando una pregunta llega al backend, Ollama crea su vector. SQL ordena los fragmentos autorizados con el operador de distancia cosine `<=>` y limita la recuperación a seis resultados. El score se calcula como `1 - distancia`. La consulta incluye únicamente documentos activos, versiones originales retenidas y repositorios que pasaron el control `document.read`.

Por ahora la búsqueda es exacta y no hay un índice ANN como HNSW. Esto evita elegir parámetros de rendimiento sin mediciones reales, pero puede tardar más al crecer mucho el corpus. Antes de añadir un índice se deben medir tamaño, latencia y calidad de recuperación con documentos representativos.

## Flujo de preparación e interrogación

### Preparar documentos

1. Angular solicita indexación para una organización.
2. El controlador valida `assistant.query`, obtiene repositorios visibles y conserva solo aquellos donde el usuario tiene `document.read`.
3. Application pide los fragmentos pendientes de documentos activos y versiones retenidas.
4. El caso de uso consulta primero un embedding de verificación para conocer modelo, revisión y dimensión; después procesa hasta **32 fragmentos por solicitud**, en lotes de hasta 16.
5. Ollama devuelve los vectores. Infrastructure registra el modelo, guarda la metadata del embedding y escribe el vector en PostgreSQL/pgvector.
6. Si quedan fragmentos, la UI lo indica y se puede repetir la acción. Este proceso aún es síncrono: no hay una cola durable ni barra de progreso del servidor.

### Preguntar con RAG

1. Angular envía la pregunta y, si existe, el ID de conversación.
2. El controlador vuelve a validar permisos y calcula el conjunto de repositorios permitidos. No confía en IDs de repositorio enviados por el navegador.
3. Ollama vectoriza la pregunta. pgvector busca hasta seis fragmentos cercanos dentro del tenant y alcance autorizados.
4. Si no hay repositorios o evidencia, el caso de uso se abstiene y explica que no encontró evidencia suficiente.
5. Si hay evidencia, Gemini recibe la pregunta y esos pasajes; el prompt trata su contenido como datos no confiables, pide ignorar instrucciones incrustadas, responder en el idioma de la pregunta y citar afirmaciones como `[1]`.
6. La respuesta, conversación y relaciones a los fragmentos citados se guardan. La respuesta API devuelve los extractos y metadatos para que la interfaz muestre sus fuentes.

La aplicación devuelve un error de proveedor `503` si Ollama/Gemini no está disponible o no está configurado. Una consulta a Gemini no se ha probado en este equipo porque todavía no hay clave ni modelo configurados.

## PostgreSQL y Docker

La imagen Compose de PostgreSQL se construye desde la base existente `postgres:17.7-alpine` y compila pgvector `0.8.7`, para mantener la misma base de sistema que usa el volumen actual. V29 está en el repositorio, pero **no se ha aplicado**: la base activa continúa en V28 y sus diagramas/catálogo siguen reflejando esa versión. Los builds aislados de la imagen, backend y frontend pasaron. No se recrearon los contenedores ni se alteró el volumen.

## Qué falta para terminar la conexión

1. Iniciar Docker Desktop y levantar el nuevo servicio Ollama; confirmar que reconoce el manifiesto/modelo montado. Si no, descargar `embeddinggemma-2:latest` dentro del contenedor.
2. Recrear solo el backend para que lea `http://ollama:11434`; comprobar desde la red Docker `GET /api/tags` y `POST /api/embed`.
3. Actualizar PostgreSQL a la imagen pgvector y aplicar V29; después regenerar el catálogo y diagramas desde la base aplicada.
4. Configurar el modelo Gemini y la clave en `.env` sin compartirlos por chat.
5. Ejecutar pruebas focalizadas de RLS/permisos, dimensión, persistencia/citas, abstención y fallos de proveedores; después revisar el recorrido con documentos reales.

## Archivos principales

- Application: `backend/application/src/main/java/com/dependencyintelligence/assistant/`
- Adaptadores/API: `backend/infrastructure/src/main/java/com/dependencyintelligence/assistant/`
- Migración: `backend/infrastructure/src/main/resources/db/migration/V29__enable_tenant_scoped_document_vectors.sql`
- Compose e imagen PostgreSQL: `compose.yaml`, `infra/database/Dockerfile.pgvector`
- Ollama aislado para Compose: `ollama/ollama:0.40.17`, red `assistant_internal`, caché local montado en solo lectura.
- Interfaz: `frontend/src/app/features/assistant/`
- Configuración local: `.env` (ignorado por Git); plantilla sin secretos: `.env.example`

## Referencias oficiales

- [Instalador oficial de Ollama para Windows](https://ollama.com/download/windows)
- [Modelo embeddinggemma-2 y API `/api/embed`](https://ollama.com/library/embeddinggemma-2)
- [pgvector: almacenamiento, distancias cosine e índices](https://github.com/pgvector/pgvector)
- [Gemini `generateContent`](https://ai.google.dev/api/generate-content)
