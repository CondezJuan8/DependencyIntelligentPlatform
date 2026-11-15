# ADR 001 — Vectores opcionales y dimensión comprobada

Estado: separación implementada; activación pendiente. Fecha: 14 de noviembre de 2026.

El usuario eligió Ollama para embeddings y Gemini para generación. No eligió modelos/revisiones ni se verificó dimensión. El núcleo debe arrancar sin extensión vectorial, modelos descargados o claves. Codex no forma parte del producto.

## Decisión

V8 crea `embedding_models` y `document_chunk_embeddings`: modelo/revisión/dimensión, estado y referencia externa. Son metadatos; no ejecutan embeddings, búsqueda vectorial o RAG. No se siembra ningún modelo/dimensión.

`db/optional/pgvector/V101__enable_document_vectors.sql` queda fuera de `classpath:db/migration`. Al seleccionar el modelo, instalar pgvector compatible en un entorno de prueba y comprobar dimensión con una respuesta real. Solo entonces incluir explícitamente ambas ubicaciones en Flyway y el placeholder `embeddingDimensions` con ese entero. La cuenta de migración necesita autorización para instalar la extensión; runtime no recibe ese privilegio.

V101 crea `document_chunk_vectors` con vector de dimensión fija, FK compuesta a metadatos, comprobación de dimensión del modelo, FORCE RLS y grants limitados. No elige Índice ANN antes de medir consultas/distancia/volumen. Otra dimensión requiere migración posterior explícita y reembeddings; no editar una migración aplicada ni cambiar silenciosamente el tipo.

## Consecuencias

V1–V11 se probaron sin pgvector. Diccionario/diagramas describen ese núcleo y no muestran vectores físicos como instalados. Activar V101 exige regenerar catálogo/diagramas y validar dimensiones erróneas, aislamiento, consultas, Índices y escritor/lector de vectores. **V101 no se ejecutó en este bloque** y no se declara integrada ni lista para producción.
