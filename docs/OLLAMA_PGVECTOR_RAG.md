# Nexo: embeddings, RAG y pgvector

Referencia técnica del código actual al 25/11/2026. No contiene credenciales.

## Flujo

~~~text
Angular
  → POST /api/organizations/{organizationId}/assistant/ask
  → backend valida sesión, organización y permisos
  → recupera evidencia autorizada de documentos, scans, OSV e informes
  → Ollama genera vector; PostgreSQL busca texto/vectores
  → Gemini redacta con citas
  → PostgreSQL guarda chat, mensaje y citas
~~~

`POST /api/organizations/{organizationId}/assistant/index` prepara fragmentos documentales sin vector. El frontend lo ejecuta automáticamente antes de una pregunta y continúa si hay progreso/pendientes. La acción manual permite reintentar. Si hay pendientes, no se asume corpus completo.

## Embeddings

Un embedding es una lista de números que representa rasgos semánticos de un texto para comparar significado aproximado; no es resumen ni respuesta.

Ollama usa `GET /api/tags` para comprobar modelo/revisión y `POST /api/embed` para obtener vectores. Compose configura `embeddinggemma-2:latest` en `http://ollama:11434`, host interno Docker. La revisión comprobada en este entorno devolvió vectores de 768 valores; backend verifica dimensión y finitud, sin fijar esa dimensión para todo modelo. Pregunta y fragmentos deben usar mismo modelo/revisión.

## Base de datos

PostgreSQL guarda documentos, versiones, texto extraído, fragmentos, metadatos de modelo, embeddings, conversaciones y citas. pgvector almacena `document_chunk_vectors` asociada a organización, documento, versión, fragmento y modelo. Compose usa PostgreSQL 17.7 y pgvector 0.8.7; migración V29 introdujo vectores y el checkout llega a V37.

La dimensión se valida frente al modelo. El backend fija organización por transacción; tablas documentales/vectoriales aplican RLS forzada. Recuperación exige permisos, la similitud no concede acceso.

## Preparación documental

1. El original va a volumen privado; Tika 4.2.0 extrae texto.
2. PDF usa extracción automática y Tesseract OCR cuando se requiere, para inglés/español.
3. Límite: 50 MiB, 500 páginas PDF, máximo 50 páginas OCR.
4. Ollama vectoriza fragmentos en lotes y los guarda con modelo/revisión.
5. La UI intenta indexar pendientes antes de preguntar. Fragmentos sin vector pueden no recuperarse.

La biblioteca incluye contexto maestro por repo y referencias generales de vulnerabilidad. Se filtra por tenant, repositorio y permiso `document.read`.

## RAG híbrido y reranking

RAG (*Retrieval-Augmented Generation*) recupera evidencia antes de generar:

1. **Vectorial:** similitud pgvector.
2. **Textual:** PostgreSQL `websearch_to_tsquery` e índice GIN, útil para nombres exactos.
3. **Fusión:** Reciprocal Rank Fusion (RRF), constante 60, fusiona hasta 40 candidatos por vía y completa el cupo restante hasta seis evidencias en total, incluidas las fuentes estructuradas del espacio.
4. **Contexto de espacio:** Application suma evidencia de scans, OSV e informes si hay permiso.
5. **Generación:** Gemini recibe la pregunta y pasajes elegidos; Nexo cita hechos y se abstiene si falta evidencia.

RRF es la etapa de reranking actual, no un modelo entrenado. La búsqueda vectorial es exacta, sin índice ANN/HNSW. Los límites iniciales requieren medición con documentos/preguntas representativos.

## Orquestador, proveedores y privacidad

La coordinación vive en casos de uso y puertos de Application, no en Airflow/n8n. Domain/Application no dependen de Spring/JPA; Infrastructure implementa SQL, HTTP y adaptadores.

Ollama genera embeddings localmente. Gemini recibe pregunta y pasajes recuperados, no el repo entero por defecto. La clave se lee desde `.local-secrets/gemini_api_key` en montaje de solo lectura, no llega al navegador. Cuota/costo depende de cuenta; no se garantiza gratuidad. OSV recibe coordenadas/versiones para consultas de avisos, no genera texto.

Nexo es de solo lectura, no cambia repositorios ni crea PR. Los chats y citas se vinculan a usuario/organización y sus propietarios los pueden borrar. Documentos fuera del alcance no deben participar.

## Diagnóstico

- UI: `http://localhost:8189/?view=assistant`; health `http://localhost:8189/health`.
- API: `GET .../assistant/conversations`, `POST .../assistant/index` y `POST .../assistant/ask`.
- Ollama desde backend: `http://ollama:11434`; no se publica como localhost.
- PostgreSQL: `database:5432` interno.

Revisa sesión/permisos, health del backend, Ollama/modelo/revisión, documentos pendientes, fuentes autorizadas y configuración/cuota Gemini. Reiniciar backend puede invalidar las sesiones en memoria.
