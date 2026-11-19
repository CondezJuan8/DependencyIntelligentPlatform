# Base de datos de Dependency Intelligence

Fuente vigente en [CODEX_DATABASE_DEPENDENCY_INTELLIGENCE](../CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md). Esquema aplicado hasta **V19**, con **127 tablas de negocio, 90 con RLS forzado y 225 FK**. Flyway se excluye del conteo. V19 agrega `local_repository_sources`, catálogo tenant-scoped de archivos ZIP privados para carpetas subidas. Catalogo, diccionario y diagramas se exportan del PostgreSQL local migrado.

- [Informe de implementación y pruebas](implementation-report.md).
- [Diccionario: función, columnas, claves, Índices, políticas y referencias de cada tabla](data-dictionary.md).
- [Normalización y decisiones de integridad](normalization.md).
- [ADR: vectores opcionales y dimensión pendiente](adr/001-optional-pgvector.md).
- [Catálogo exportado](schema.json) y [validación de diagramas](diagram-validation.json).

## Arranque y actualización

Para una instalación nueva, copiar `.env.example` a `.env` sin sobrescribir una configuración existente. Configurar contraseñas distintas para `POSTGRES_PASSWORD` (migración/administración local) y `POSTGRES_APP_PASSWORD` (runtime, mínimo 12 caracteres). Los valores de ejemplo son solo para desarrollo local. Desde la raíz:

```sh
docker compose up --build -d
```

El entrypoint crea dependency_app. Flyway aplica V1-V19; el datasource usa el login limitado dependency_app, miembro de dependency_runtime. Los archivos de proyectos locales persisten en el volumen Docker `repository_data`, separado del volumen PostgreSQL.

Las cargas locales se limitan a 100 MiB y 2.000 archivos por carpeta. Los originales empaquetados viven fuera de la base; conserva y respalda también el volumen `repository_data` para poder recuperar los repositorios importados.

**Volumen existente:** los scripts initdb solo se ejecutan al inicializarlo. Añadir `POSTGRES_APP_PASSWORD` a `.env`, recrear el servicio de base para recibir la variable y aprovisionar antes del backend:

```sh
docker compose up -d database
docker compose exec database sh /docker-entrypoint-initdb.d/10-runtime.sh
docker compose up --build -d backend frontend
```

Conservar el volumen. Respaldar y probar la actualización en una copia antes de usar datos importantes. V1–V12 mantienen sus checksums en el bloque de recuperación. V13 añade una columna y un índice. Las cuentas anteriores reciben credential_version cero. Los detalles de transición están en el informe.

Sin Docker, ejecutar `infra/database/provision-runtime.sql` con `psql`, administrador autorizado y `POSTGRES_APP_PASSWORD` en el entorno. Es idempotente y permite rotar la contraseña. Para el JAR configurar `DB_HOST`, `DB_PORT`, `POSTGRES_DB`, `DB_RUNTIME_USER`, `POSTGRES_APP_PASSWORD`, `MIGRATION_USER` y `MIGRATION_PASSWORD`. Las dos Últimas admiten `POSTGRES_USER`/`POSTGRES_PASSWORD` como fallback local. La creación inicial de roles requiere administración; en servidor separar aprovisionamiento, migración y runtime.

`RuntimeDatabaseRoleGuard` rechaza el datasource de negocio si es propietario, permite DDL o tiene capacidades de mantenimiento/BYPASSRLS. No desactivarlo para resolver una configuración productiva. Los fixtures usan administración separada; las pruebas runtime mantienen el control activado.

## Aislamiento e integridad

`TenantTransactions` establece `app.organization_id` local a la transacción JPA/JDBC e impide cambiar de organización dentro de ella. Commit/rollback elimina el contexto. Sin contexto de usuario ni organización se ocultan filas tenant. Durante el listado, `app.user_id` autenticado permite consultar sus membresías/organizaciones activas mediante las políticas SELECT de V12. USING, WITH CHECK y FK compuestas protegen las operaciones tenant.

Los contextos proceden de la sesión y una operación autorizada. Los endpoints de repositorios/scans comprueban cuenta, organización y membresía activas, permiso de rol y permiso de equipo aplicable dentro de la transacción tenant. RLS depende de los contextos fijados por el backend. Usuarios/identidades globales son privados del subsistema de autenticación. La configuración y cobertura de acceso están en [ACCESO_LOCAL](../ACCESO_LOCAL.md).

Roles capability NOLOGIN: `dependency_runtime`, `dependency_migrator`, `dependency_catalog_writer`, `dependency_audit_admin`. Catálogos de lectura para runtime; auditoría INSERT/SELECT y UPDATE/DELETE denegados. La administración excepcional requiere otro rol. Superusuarios y BYPASSRLS pueden eludir RLS aun con FORCE: [PostgreSQL](https://www.postgresql.org/docs/17/ddl-rowsecurity.html).

## Comprobaciones

Desde `backend/`, JDK 21 y Maven:

```sh
mvn verify -Pdatabase-it
```

Usa PostgreSQL 17.7 de Testcontainers con Docker. Alternativa: `TEST_DATABASE_URL`, `TEST_DATABASE_USER`, `TEST_DATABASE_PASSWORD` apuntando exclusivamente a una base desechable; los fixtures crean esquemas y roles. Para cambios solo de restricciones seleccionar clases/métodos afectados con `-Dit.test=...`. Resultados realmente obtenidos: [informe](implementation-report.md).

## Diagramas y regeneración

Archivos JSON nativos editables en Excalidraw, acompañados de SVG y PNG:

1. [Contexto del sistema](diagrams/01-system-data-context.excalidraw).
2. [Identidad, permisos e integraciones](diagrams/02-identity-access-integrations.excalidraw).
3. [Scans, SBOM, componentes y grafo](diagrams/03-scans-sbom-components-graph.excalidraw).
4. [Vulnerabilidades y evidencia](diagrams/04-vulnerability-intelligence.excalidraw).
5. [Documentos y RAG; vectores opcionales](diagrams/05-documentation-rag-pgvector.excalidraw).
6. [Impacto, reportes, notificaciones y retención](diagrams/06-change-impact-reporting-notifications.excalidraw).
7. [Aislamiento y roles SQL](diagrams/07-tenant-isolation-rls.excalidraw).

Desde una copia migrada, exportar con una conexión autorizada y variables de conexión locales, sin registrar contraseñas:

```sh
psql -X -qAt -v ON_ERROR_STOP=1 -f infra/database/export-schema.sql -o docs/database/schema.json
python infra/database/generate-documentation.py docs/database/schema.json docs/database
```

El export contiene estructura/politicas/roles, nunca datos de negocio ni contrasenas. El generador usa Python estandar; Pillow anade PNG. Diagramas 2-6 representan FK reales; diagramas 1/7 flujos logicos. Validacion confirma IDs, bindings y las 225 FK.

Verificación disponible de Docker, Excalidraw, pgvector y revisión visual del acceso en [implementation-report](implementation-report.md) y [ACCESO_LOCAL](../ACCESO_LOCAL.md).
