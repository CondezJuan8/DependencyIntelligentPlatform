# Diagramas de base de datos

Siete archivos **nativos editables de Excalidraw**, generados desde `docs/database/schema-catalog.json`, extraído de PostgreSQL real. Abrir con Excalidraw mediante importar archivo; los datos son locales y no contienen credenciales ni fixtures.

1. `01-system-data-context.excalidraw`: fuentes, almacenamiento y flujo local; separa módulos futuros.
2. `02-identity-access-integrations.excalidraw`: identidad, RBAC, integraciones, repositorios y catálogos.
3. `03-scans-sbom-components-graph.excalidraw`: jobs, snapshots, SBOM, paquete/versión/presencia y grafo.
4. `04-vulnerability-intelligence.excalidraw`: advisories, rangos, severidades, procedencia y findings.
5. `05-documentation-rag-pgvector.excalidraw`: versiones/chunks, modelos, embeddings opcionales y citas.
6. `06-change-impact-reporting-notifications.excalidraw`: impacto, reportes, notificaciones y auditoría.
7. `07-tenant-isolation-rls.excalidraw`: roles SQL, contexto transaccional, RLS y FKs compuestas.

Leyenda: azul global, violeta tenant/RLS, verde puente, gris referencia externa al módulo. Las flechas de los diagramas de entidades derivan de FKs reales, con cardinalidad hijo N → padre 1 o 0..1 según nullability. Todos los nombres de constraints se conservan en `customData` del conector. Las cajas muestran PK/FK y claves UNIQUE; el diccionario contiene las columnas restantes. Las tablas externas se repiten para mostrar todas las relaciones sin fingir que pertenecen al módulo.

Regeneración y validación estructural: `python3 tools/database/render_docs.py`. Se comprueban 116 tablas, 246 FKs, IDs únicos y referencias de conectores/containers. No se realizó una validación visual con la aplicación Excalidraw ni se afirma una exportación SVG/PNG desde ella. Los diagramas de contexto y RLS son flujos conceptuales, no relaciones SQL adicionales.
