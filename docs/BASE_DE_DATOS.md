# Base de datos del MVP

PostgreSQL con migraciones Flyway versionadas y `ddl-auto: validate`. V1 se conserva sin modificar para respetar su checksum. V2 crea el esquema transaccional; tanto una base vacía como una base con V1 se actualizan en el arranque. No se borran volúmenes ni se usa `baseline-on-migrate` o generación automática del esquema por Hibernate.

| Tabla | Responsabilidad |
| --- | --- |
| organizations | Organización y slug único |
| users | Identidad externa por subject y correo sin duplicados por mayúsculas |
| memberships | Membresía usuario/organización con rol |
| integrations | Identidad y estado de instalaciones externas, sin tokens |
| repositories | Repositorio único por organización, proveedor e ID externo |
| scans | Historial de solicitudes, referencia Git, commit, scanner, estado y tiempos |
| scan_artifacts | Referencia al SBOM/artefacto y checksum SHA-256 |
| components | Catálogo por organización con PURL que identifica paquete y versión |
| scan_components | Ocurrencias por snapshot y `bom_ref`, con dependencia directa/transitiva/desconocida |
| dependency_relationships | Aristas entre ocurrencias dentro del mismo snapshot |
| vulnerabilities | Catálogo público por fuente e ID, payload y fecha de consulta |
| vulnerability_findings | Vulnerabilidad observada en una ocurrencia de un snapshot y versiones corregidas reportadas |
| audit_events | Acción, actor, recurso y metadatos por organización |

Se usan UUID y fechas `TIMESTAMPTZ`. Los identificadores de agregados los genera la aplicación. Las restricciones `NOT NULL`, `CHECK`, `UNIQUE` y FK validan identidad, estado y coherencia. No se almacena contraseña de usuario: el `subject` reserva la integración futura con identidad externa. No se almacenan credenciales de proveedores en JSON.

Las FK compuestas `(organization_id, id)` impiden asociar un repositorio a una integración de otra organización o un scan a un repositorio ajeno. El grafo y los hallazgos incluyen además `scan_id` y `bom_ref`, impidiendo mezclar snapshots. No se aplica borrado en cascada: la eliminación y retención de historia necesitarán casos de uso explícitos.

El catálogo de componentes pertenece a cada organización para no compartir nombres de paquetes privados. Un mismo componente puede aparecer varias veces en un snapshot con distintos `bom_ref`. Los componentes raíz del SBOM deben representarse como ocurrencias cuando se importen sus aristas. Las vulnerabilidades públicas se comparten, pero su presencia en repositorios pertenece al snapshot de cada organización. Los alias y rangos de versiones se conservan inicialmente en `raw_payload`; no existe todavía lógica de normalización o consulta a OSV.

Los índices cubren membresías por usuario, repositorios por integración, historial de scans, solicitudes pendientes, búsqueda de componentes, recorrido inverso del grafo, hallazgos y auditoría. No se añade pgvector: documentos y RAG corresponden a una fase posterior.

## Pruebas reproducibles

`mvn verify -Pdatabase-it` desde `backend/` ejecuta `DatabaseIT`. Por defecto crea un PostgreSQL 17.7 desechable mediante Testcontainers. También admite una base de prueba explícita mediante variables `TEST_DATABASE_*` (ver [ARQUITECTURA_BACKEND.md](ARQUITECTURA_BACKEND.md)).

Se verifica:

- Arranque Spring con Flyway y validación de mappings JPA.
- Bloqueo de los controladores reales de negocio, incluso con CSRF válido.
- Registro, consulta y solicitud de análisis con adaptadores reales.
- Duplicados dentro de una organización y reutilización del ID externo en otra.
- Lectura y solicitud de scans con organización ajena.
- Rechazo SQL de relaciones entre organizaciones y entre snapshots.
- Aristas válidas, autorreferencias inválidas y hallazgos ligados a una ocurrencia existente.
- Cronología y consistencia del estado del scan.
- Migración V1 → V2, validación de checksums y repetición sin migraciones pendientes.

Las restricciones entre organizaciones aseguran integridad referencial. No hay RLS ni autenticación/membresías operativas: las rutas de negocio permanecen denegadas hasta implementar esos controles.

La validación local de esta reorganización se ejecutó con Maven 3.9.11, JDK 25 compilando con `--release 21` y PostgreSQL 18.6 temporal. El contenedor configurado continúa usando PostgreSQL 17.7. Docker no estaba disponible en este entorno, por lo que la construcción Compose y la variante Testcontainers quedan sin ejecutar aquí.
