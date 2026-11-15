# Estado real de implementación de la base de datos

Referencia: [CODEX_DATABASE_DEPENDENCY_INTELLIGENCE](../CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md). Bloque 05 de la [bitácora](../BITACORA_TRABAJO.md), 14 de noviembre de 2026. Esquema probado en una base temporal; no se actualizó una base externa del usuario ni se declara terminado el producto.

## Resultado

**121 tablas de negocio, 85 con ENABLE/FORCE RLS y 208 FK**, sin contar Flyway. V1–V3 no se reescriben; se añaden ocho migraciones V4–V11. El [diccionario](data-dictionary.md) explica cada tabla; [schema.json](schema.json) procede del catálogo de PostgreSQL.

| Migración | Función |
| --- | --- |
| V4 | Paquete/versión/identificadores/aparición separados; aristas entre apariciones del mismo snapshot |
| V5 | Identidad local/externa, membresías, RBAC, equipos, permisos por repositorio e instalaciones GitHub |
| V6 | Jobs/intentos/etapas, commits, artefactos, SBOM, comparaciones, hashes y licencias |
| V7 | Advisories, aliases, severidades, fuentes/rangos/fixes y evidencia de hallazgos privados |
| V8 | Documentos/versiones/chunks, procesamiento, metadatos de modelos, conversaciones y citas tipadas |
| V9 | Impacto, simulaciones/pruebas, propuestas PR, reportes, alertas, auditoría, retención y eliminación |
| V10 | Roles SQL, RLS forzado, grants mínimos, FK/Índices y auditoría append-only |
| V11 | Evidencias del mismo análisis, reintentos acotados e inmutabilidad de resultados/procedencia terminados |

Se siembran códigos de referencia, no usuarios de la plataforma, claves, modelos/dimensiones ni ejecuciones/hallazgos ficticios. Paquetes privados por organización; advisories públicos compartidos. Autores/maintainers eran condicionales sin caso de uso y no se añaden por anticipado.

V101 vectorial se entrega fuera de la ubicación Flyway predeterminada y no fue ejecutada: faltan modelo/dimensión comprobados y pgvector. V8 permite metadatos sin extensión: [ADR](adr/001-optional-pgvector.md).

## Transición y compatibilidad

La conversión conserva organizaciones, sujetos históricos, membresías/roles, repositorios, snapshots, procedencia, versiones/PURL alternativos, apariciones, aristas y hallazgos. Fixes multivaluados pasan a filas. `sbom_imports` pasa a `sbom_documents`. Se conservan checksums previos. Datos incompatibles requieren revisión; no se eliminan silenciosamente para forzar el arranque.

Adaptadores existentes compatibles con paquete/versión/identificadores separados. La importación conserva cada bom-ref y UNKNOWN, no inventa commit/scanner/grafo y revierte Íntegramente ante conflicto. El parser continúa acotado a Maven; el catálogo npm no implementa su parser.

Infrastructure delimita tenant mediante `TenantTransactions`, también en el bootstrap. Domain/Application siguen sin Spring/JPA. Credenciales Flyway/runtime separadas y guard contra datasource propietario/privilegiado. Aprovisionamiento del login comprobado dos veces para verificar idempotencia.

## Verificación ejecutada

PostgreSQL **18.6 nativo temporal**, Maven 3.9.11, JDK local 25 y compilación destino Java 21. Compose conserva PostgreSQL 17.7/Java 21; el entorno nativo no prueba esas imágenes.

Primera selección: 8 unitarias/arquitectura/seguridad y 22 de integración, todas correctas. Clases: ImportSbomUseCaseTest, SecurityConfigurationTest, HexagonalArchitectureTest, DatabaseIT, DemoBootstrapIT, SbomImportIT, NormalizedSchemaIT, RuntimeDatabaseIT. Tras V11: solo 7 de NormalizedSchemaIT y 2 métodos de migración/arranque de DatabaseIT, 9 correctas. **31 pruebas distintas**; ocho se repitieron por cambios pertinentes, no son 39 casos diferentes.

Cobertura:

- Instalación limpia, actualización desde V1/V3 con datos, checksums intactos y validación JPA.
- Dos PURL con qualifiers convertidos al mismo paquete/versión, manteniendo apariciones/aristas; identidad, roles, advisory, base explícita del hash de payload y fixes preservados.
- FK del mismo tenant/repositorio/snapshot/paquete/análisis; equipos, comparación, documentos/citas, reportes y entregas.
- Login runtime real sin DDL/propiedad: lecturas/escrituras ajenas y escritura de catálogos rechazadas; contexto transaccional eliminado y cambio interno de tenant rechazado.
- Importador y JPA con ese login; rollback/reimportación/bootstrap.
- Auditoría append-only; VALIDATED exige prueba terminada del mismo análisis; resultados terminados inmutables, PR con aprobación y reintentos acotados.

Siete Excalidraw nativos más SVG/PNG y diccionario generados desde catálogo aplicado. Comprobación estructural: 121 tablas, 208 FK, IDs Únicos y bindings existentes. Se inspeccionaron exportaciones locales; no apertura interactiva en Excalidraw. No se midieron rendimiento/concurrencia ni se ejecutaron Docker, pgvector o IA. Se detiene el servidor temporal al cerrar el bloque.

## Pendientes de producto

Las tablas están disponibles; faltan servicios y recorridos. Sigue registro/login local, verificación/recuperación/sesiones, autorización de membresía/permisos y HTTP/UI. Después Google/GitHub, instalación GitHub, worker/scanner Maven/npm, OSV, inventario/grafo/comparación e impacto. Almacenamiento documental, Ollama/Gemini, reportes/alertas, ejecución de retención y operación/backup requieren implementación y aceptación.

RLS no autentica; el tenant de ruta no acredita permisos. Identidades globales requieren acceso restringido por autenticación. APIs de negocio continúan denegadas. No se declara 5NF universal: [normalización](normalization.md) documenta dependencias y excepciones.
