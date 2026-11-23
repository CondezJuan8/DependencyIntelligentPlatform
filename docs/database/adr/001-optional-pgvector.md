# ADR 001 — Vectores y dimensión de embeddings

Estado: ampliada por la implementación inicial de la petición 10 (bloque 75). Fecha original: 14 de noviembre de 2026. Revisión: 23 de noviembre de 2026.

El usuario eligió Ollama para embeddings y Gemini para generación. No se eligió un modelo concreto. La revisión/digest del modelo Ollama se suministra y valida contra `/api/tags`; la dimensión se mide en la primera respuesta real de embeddings. No se fija una dimensión por adelantado y no se guardan claves de proveedor en el repositorio.

## Decisión vigente

Flyway V29 incorpora pgvector en el esquema normal del producto y crea `document_chunk_vectors` con el tipo `vector` sin dimensión declarada. `embedding_models.dimension` registra la dimensión observada. Un trigger rechaza cualquier vector que no coincida con el modelo; FK compuesta, `organization_id`, FORCE RLS y policy tenant aíslan los datos.

Compose construye pgvector 0.8.7 encima de `postgres:17.7-alpine`, preservando la familia de sistema base de la imagen original. V29 no se aplicó en esta sesión; el estado del volumen activo no se pudo consultar desde este entorno.

No se crea índice ANN todavía. La primera consulta usa distancia cosine exacta; se medirá con corpus representativo antes de elegir índice/operadores. Un cambio de modelo/revisión genera índice independiente y reembeddings, no convierte dimensiones existentes.

## Consecuencias y validación pendiente

La imagen pgvector es necesaria para que la migración estándar funcione en Compose. El usuario runtime no obtiene privilegios para crear extensiones; Flyway ejecuta la migración con el usuario administrador local configurado. Tras aplicar V29 se deben regenerar catálogo/diccionario/diagramas y comprobar dimensión inválida, RLS, permisos y consulta cosine. No hay modelo Ollama ni Gemini configurados en el entorno actual; no se afirma que la función esté lista para uso real.