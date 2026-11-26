### Estado vigente — OpenAI y harness de solo lectura (23/11/2026)

OpenAI reemplaza a Gemini para generación; Ollama conserva embeddings y pgvector la recuperación. El adaptador de OpenAI usa salida estructurada y no ofrece herramientas para cambiar repositorios. Solo devuelve diagnóstico y un prompt de reparación para revisión humana. La clave `OPENAI_API_KEY` está vacía en `.env` local; configura tu propia clave allí, nunca en el chat. Los siguientes registros de Gemini son históricos y quedaron sustituidos por el bloque 79. Ver [controles OWASP](ASISTENTE_IA_SEGURIDAD_OWASP.md).
# Ollama, embeddings y pgvector en Dependency Intelligence

Estado operativo local: 23 de noviembre de 2026.

## Qué quedó instalado y comprobado

- Ollama para Windows, versión cliente `0.40.17`, instalado mediante el instalador oficial. La firma del ejecutable instalado aparece como válida y emitida por Ollama Inc.
- Modelo `embeddinggemma-2:latest`, descargado con Ollama. `/api/tags` reportó el digest completo `f4981a15b99858ad1f2e0729416e526be7b4f428291b40082b9e4f7b27b5f62f`.
- Una llamada real a `POST /api/embed` generó un vector de **768 valores numéricos finitos**.
- `.env` local ignorado usa la URL interna `http://ollama:11434`, nombre del modelo y digest verificado. Los datos del volumen no se añadieron al repositorio.
- Imagen oficial `ollama/ollama:0.40.17` descargada (8.52 GB extraida); contenedor Compose saludable y aislado, sin puerto publicado. El modelo persiste en `ollama_data`.
- Modelo `gemini-3.5-flash-lite` activo. La clave `GEMINI_API_KEY` está configurada solo en `.env`; una solicitud mínima de generación desde el backend devolvió HTTP 200 con un candidato.

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

Cuando una pregunta llega al backend, Ollama crea su vector y el backend conserva también el texto original de la pregunta para la recuperación lexical. La consulta obtiene hasta 40 candidatos por similitud cosine de pgvector y hasta 40 por búsqueda textual PostgreSQL (`websearch_to_tsquery` sobre el índice GIN de fragmentos). Fusiona ambos rankings con Reciprocal Rank Fusion (RRF, constante 60) y entrega los seis fragmentos con mejor puntuación combinada. Un fragmento presente en ambos rankings sube por evidencia coincidente; los fragmentos exclusivos de una modalidad también pueden competir. Solo participan documentos activos, la versión retenida vigente y contenido dentro del tenant/repositorios que pasaron `document.read`.

La fusión es la etapa de reranking actual; no es un modelo de reranking entrenado. El límite de 40 candidatos por modalidad y la constante RRF son parámetros iniciales en código que aún requieren medición con preguntas y documentos representativos. No se cambia el permiso ni el alcance al mezclar rankings.

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

Desde el backend, `/api/tags` coincidió con el digest configurado y `/api/embed` devolvió HTTP 200 con un vector de 768 dimensiones y valores finitos. Gemini `gemini-3.5-flash-lite` devolvió HTTP 200 y un candidato ante una solicitud mínima sin datos documentales. La biblioteca puede permanecer vacía: el asistente no requiere que cada usuario cargue documentos; sin evidencia autorizada, se abstiene. La prueba de recuperación y citas con documentos propios queda a decisión del usuario.

## PostgreSQL y Docker

Compose aplicó V29 sobre la base PostgreSQL existente sin borrar sus datos. La extensión `vector` está en versión 0.8.7; `document_chunk_vectors` tiene RLS habilitado y forzado, y el usuario runtime posee permisos. Se creó un respaldo pre-migración en `%TEMP%\dependency-intelligence-pre-v29.dump` (3.302.164 bytes). `/health` respondió `UP`.

## Estado de uso de la petición 10

- Ollama, embeddings y pgvector están activos en Compose. La pantalla está disponible en `http://localhost:8089/?view=assistant`.
- La clave Gemini es local y la generación básica ya respondió HTTP 200 con el modelo `gemini-3.5-flash-lite`. No se incluye ni se imprime el secreto.
- Importar documentos es opcional. El asistente puede abrirse sin documentos; si no hay evidencia autorizada, se abstiene. Para probar recuperación y citas, la persona puede importar documentos que tenga permiso de consultar.
- Se mantienen pendientes la validación visual/autenticada con una cuenta de usuario y las regresiones focalizadas de citas/RLS en documentos reales; no se inventaron resultados de esa revisión.
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
# Estado vigente del proveedor de generación

La generación Gemini documentada en los bloques históricos fue sustituida por OpenAI Responses API. Consultar [ASISTENTE_IA_SEGURIDAD_OWASP](ASISTENTE_IA_SEGURIDAD_OWASP.md) para configuración, controles y límites vigentes. Ollama continúa siendo el proveedor de embeddings y pgvector el almacenamiento vectorial.
