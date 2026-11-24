# Ollama, embeddings y pgvector en Dependency Intelligence

Estado operativo local: 24 de noviembre de 2026.

## Qué quedó instalado y comprobado

- Ollama para Windows, versión cliente `0.40.17`, instalado mediante el instalador oficial. La firma del ejecutable instalado aparece como válida y emitida por Ollama Inc.
- Modelo `embeddinggemma-2:latest`, descargado con Ollama. `/api/tags` reportó el digest completo `f4981a15b99858ad1f2e0729416e526be7b4f428291b40082b9e4f7b27b5f62f`.
- Una llamada real a `POST /api/embed` generó un vector de **768 valores numéricos finitos**.
- `.env` local ignorado usa la URL interna `http://ollama:11434`, nombre del modelo y digest verificado. Los datos del volumen no se añadieron al repositorio.
- Imagen oficial `ollama/ollama:0.40.17` descargada (8.52 GB extraida); contenedor Compose saludable y aislado, sin puerto publicado. El modelo persiste en `ollama_data`.
- Modelo de generación fijado a `gemini-3.8-flash`; falta `GEMINI_API_KEY`, por lo que todavía no se puede probar la generación Gemini.

## Cómo se conecta cada parte

```text
Navegador
  └─ Angular: pantalla Asistente
       ├─ POST /api/organizations/{id}/assistant/index
       └─ POST /api/organizations/{id}/assistant/ask
              │
              ▼
       Spring Boot dentro de Docker
          ├─ Ollama en Compose: ollama:11434
          ├─ PostgreSQL + extensión pgvector
          └─ Gemini: generación de respuesta, cuando sus credenciales estén configuradas
```

Compose conecta el backend y Ollama por DNS interno (`http://ollama:11434`). El contenedor de Ollama descarga el modelo al volumen persistente; no publica puertos al anfitrion. La red privada de IA no tiene acceso desde el navegador ni desde la red local. La descarga inicial necesita Internet; si falla, el backend y los modulos no relacionados con IA pueden iniciar normalmente.
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

El backend ya accede a Ollama. Desde su contenedor, `/api/tags` coincidió con el digest configurado y `/api/embed` devolvió HTTP 200 con un vector de 768 dimensiones y valores finitos. La generación final con Gemini no está probada: falta la clave. La base no contiene documentos todavía, así que no se ha validado una respuesta RAG con citas sobre archivos reales.

## PostgreSQL y Docker

Compose aplicó V29 sobre la base PostgreSQL existente sin borrar sus datos. La extensión `vector` está en versión 0.8.7; `document_chunk_vectors` tiene RLS habilitado y forzado, y el usuario runtime posee permisos. Se creó un respaldo pre-migración en `%TEMP%\dependency-intelligence-pre-v29.dump` (3.302.164 bytes). `/health` respondió `UP`.

## Qué falta para completar la petición 10

1. Obtener una API key de Gemini AI Studio y guardarla solo en `.env` (`GEMINI_API_KEY`). El modelo `gemini-3.8-flash` ya está configurado.
2. Cargar documentos de prueba autorizados, indexarlos y validar una respuesta RAG con citas, permisos y abstención.
3. Completar las regresiones focalizadas de RLS, dimensiones, persistencia/citas y fallos de proveedores. El build de este bloque omitió la suite larga; no se añadieron pruebas.
4. La vista del asistente está disponible en `http://localhost:8089/?view=assistant`; requiere sesión autenticada y documentos accesibles para producir respuestas con evidencia.

Ollama y pgvector ya funcionan en Compose; no se necesita Firewall de Windows. En otro equipo, `ollama_data` mantiene el modelo después de la primera descarga.
## Archivos principales

- Application: `backend/application/src/main/java/com/dependencyintelligence/assistant/`
- Adaptadores/API: `backend/infrastructure/src/main/java/com/dependencyintelligence/assistant/`
- Migración: `backend/infrastructure/src/main/resources/db/migration/V29__enable_tenant_scoped_document_vectors.sql`
- Compose e imagen PostgreSQL: `compose.yaml`, `infra/database/Dockerfile.pgvector`
- Interfaz: `frontend/src/app/features/assistant/`
- Configuración local: `.env` (ignorado por Git); plantilla sin secretos: `.env.example`

## Referencias oficiales

- [Instalador oficial de Ollama para Windows](https://ollama.com/download/windows)
- [Modelo embeddinggemma-2 y API `/api/embed`](https://ollama.com/library/embeddinggemma-2)
- [pgvector: almacenamiento, distancias cosine e índices](https://github.com/pgvector/pgvector)
- [Gemini `generateContent`](https://ai.google.dev/api/generate-content)
