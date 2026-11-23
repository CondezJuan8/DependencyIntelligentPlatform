## Retiro de almacenamiento fuera del alcance V27

23 de noviembre de 2026. Se anadio una migracion aditiva para instalaciones existentes; conserva el historial V1-V26 y retira almacenamiento de an?lisis que dej? de utilizarse. El catalogo documentado queda en 126 tablas, 90 con ENABLE+FORCE RLS y 224 FK. No se aplico esta migracion a los entornos activos durante este cambio.

## Ajustes compartidos de organizacion, bloque 26

18 de noviembre de 2026. Migracion V18 aplicada con Flyway en PostgreSQL local. Catalogo real: 126 tablas de negocio, 89 con ENABLE+FORCE RLS y 223 FK. Catalogo schema.json, diccionario y siete diagramas regenerados; IDs/bindings unicos y todas las FK cubiertas.

`organization_settings` contiene tema SYSTEM/LIGHT/DARK, idioma ES/EN y auditoria de modificacion. Backfill de organizaciones existentes y alta automatica para nuevas organizaciones. RLS permite lectura a miembros activos verificados y cambios solo a OWNER/ADMIN activos. API REST y caso de uso hexagonal aplican authorization y audit. Angular comparte las preferencias al elegir una organizacion y conserva las personales para acceso/registro.

Build backend y Angular correctos. La IT focalizada no se completo: el contenedor Maven no pudo resolver Maven Central por DNS. Requiere repeticion cuando la red Maven funcione. Compose backend/frontend activos y health UP; base, volumen y Mailpit no se reiniciaron.
# Estado real de implementación de la base de datos

## Límite de carpetas locales V24, bloque 46

20 de noviembre de 2026. Flyway aplicó V24 al Compose local. `local_repository_sources.file_count` acepta de 1 a 50.000, sin tabla/columna nueva ni cambio en 128 tablas, 91 FORCE RLS o 227 FK. El CHECK de V24 fue consultado en PostgreSQL después del arranque del backend. El contenido agregado permanece limitado a 100 MiB en Application; esta migración solo cambia la cantidad de archivos. No se alteraron diagramas de relaciones.

## Evaluación OSV por versión V22/V23, bloque 42

19 de noviembre de 2026. Flyway aplicó V22 y V23 al PostgreSQL local sin reiniciar la base ni tocar sus volúmenes. El catálogo actual contiene 128 tablas de negocio, 91 con ENABLE+FORCE RLS y 227 FK. `scan_vulnerability_assessments` queda tenant-scoped y enlazada a `scan_components`; la nueva tabla confirma ambos flags RLS activos. V22 conserva snapshots de respuesta y error por componente/versión; V23 deja al runtime solo leer e insertar y mantiene el catálogo público separado. La evaluación y la API se describen en [OSV_VULNERABILIDADES](../OSV_VULNERABILIDADES.md).

## Repositorios locales V19, bloque 29

18 de noviembre de 2026. Flyway aplicó V19 al PostgreSQL local y conservó el volumen existente. `local_repository_sources` registra tenant, repositorio, llave privada, SHA-256, tamaño ZIP, número de archivos y membresía de carga; V19 habilita FORCE RLS/privilegios runtime. Catálogo estructural: 127 tablas de negocio, 90 con FORCE RLS y 225 FK. `schema.json`, diccionario y diagramas regenerados desde PostgreSQL; IDs/bindings únicos y todas las FK cubiertas. No se exportaron filas de negocio. La persistencia de bytes va al volumen Compose `repository_data`, no a PostgreSQL.

## Aplicación scoped y permisos directos V16/V17, bloque 25

18 de noviembre de 2026. Se reconstruyó la imagen del backend y se reinició únicamente ese servicio; PostgreSQL, Mailpit y el volumen persistente permanecieron activos. Flyway validó las 17 migraciones y aplicó V16/V17 al PostgreSQL local, ahora en V17. Conteo desde catálogo: 125 tablas de negocio, 88 con FORCE RLS y 221 FK. Exportación estructural regeneró `schema.json`, diccionario y los siete diagramas Excalidraw/SVG/PNG; IDs, bindings y cobertura completa de FK validados. V17 es la tabla 125.

Prueba focalizada `OrganizationAccessIT#teamPermissionsAreIntersectedWithRolesAndRevocationsApplyToRealRequests`: 1/1 correcta con PostgreSQL 17.7 y Mailpit temporales aislados en la red Compose; las 35 pruebas unitarias de Domain/Application/Infrastructure también pasaron. El recorrido comprobó invitación equipo/repositorio, lectura autorizada, intersección de permisos con rol, permiso administrativo scoped, revocación por membresía/equipo y grant directo por miembro/repositorio. Los fixtures quedaron alineados con aprobación del propietario y parámetros obligatorios de invitación. Las tres dependencias efímeras se retiraron; no se modificó la base temporalmente probada ni se borró el volumen del usuario. No se compararon filas de negocio antes/después.

Herramientas usadas: Docker Compose/Desktop, Maven/Failsafe, PostgreSQL/psql, Mailpit y Python/Pillow temporales para generar diagramas. Sin dependencias nuevas en el producto.

## Decisión de admisión V15, bloque 20

18 de noviembre de 2026. V14 agregó `member_repository_assignments`, `platform_administrators` y `platform_audit_events`. V15 amplía `user_access_tokens` con administrador destinatario, cuenta objetivo y acción Aprobar/Rechazar, restricciones de coherencia e índice parcial de decisiones pendientes; no agrega tablas. Tras aplicar V15 al PostgreSQL local y regenerar artefactos desde catálogo, hay 124 tablas de negocio, 87 con FORCE RLS y 218 FK. Catálogo validado con 7 diagramas nativos, IDs/bindings únicos y las FK cubiertas. Pruebas y flujo HTTP documentados en [verificación y recuperación](../VERIFICACION_Y_RECUPERACION.md).

## Invitaciones y equipos sin cambio de esquema, bloque 11

15 de noviembre de 2026. Servicios HTTP/UI implementados para membership_invitations/invitation_roles, organization_memberships/membership_roles, organization_teams/team_memberships y repository_team_access, con auditoría transaccional. Se reutilizan V5 y permisos runtime; sin migración nueva ni cambios de catálogo/diagramas. V1–V13, 121 tablas, 85 con FORCE RLS y 208 FK. Ocho pruebas de gestión y cinco de acceso local correctas con PostgreSQL nativo temporal y runtime limitado; SMTP real mediante Mailpit. Aceptación y democión concurrentes comprobadas. No se actualizó una base externa del usuario. [Recorrido, contratos y límites](../INVITACIONES_EQUIPOS_PERMISOS.md).

## Identidad externa sin cambio de esquema, bloque 09

15 de noviembre de 2026. Google/GitHub y vinculación explícita utilizan las tablas existentes, restricciones únicas de user_identities y users.credential_version. No se añade migración: V1–V13, 121 tablas, 85 con FORCE RLS y 208 FK. Los diagramas y el catálogo conservan vigencia. Diez pruebas de identidad externa y cinco de acceso local correctas con PostgreSQL nativo temporal y rol runtime limitado; el intercambio OAuth usa proveedores HTTP locales de prueba. No se configuraron clientes reales ni instalaciones de repositorios. [Configuración y límites](../IDENTIDAD_GOOGLE_GITHUB.md).

## Actualización de recuperación V13, bloque 07

15 de noviembre de 2026. V13 añade users.credential_version con default cero y un índice por usuario/propósito/creación en user_access_tokens. Permanecen 121 tablas, 85 con FORCE RLS y 208 FK. Catálogo, diccionario y diagramas regenerados. V1–V12 conservan sus checksums.

20 pruebas distintas pertinentes correctas con PostgreSQL nativo 18.6, Mailpit 1.33.1 nativo, JDK 25 con destino 21 y Maven 3.9.11. Incluyen seis de recuperación, cinco de acceso local, dos métodos de migración y siete de seguridad/arquitectura/límite de peticiones. SMTP real comprobado mediante API de Mailpit y hash confirmado en PostgreSQL. Se amplió la espera inicial de ocho a veinte segundos para la entrega asíncrona y se repitió únicamente la clase afectada al ajustar la comprobación de caducidad tras bloqueo. Build Angular y cuatro aserciones directas del parser de enlaces correctos.

Sesiones anteriores se rechazan al incrementar versión. Tokens de un uso, propósito, expiración, reenvío, cooldown, confirmación concurrente y rollback de emisión comprobados. SMTP y PostgreSQL conservan un límite de entrega entre sistemas y la cola de recuperación es local en memoria. La revisión visual sigue pendiente por host de navegador indisponible, y Compose queda por ejecutar. Detalles en [VERIFICACION_Y_RECUPERACION](../VERIFICACION_Y_RECUPERACION.md). Las secciones siguientes conservan el historial de bloques anteriores.

## Actualización de acceso V12, bloque 06

15 de noviembre de 2026. V12 añade `app_private.current_user_id()` y políticas SELECT de descubrimiento propio en organizations/organization_memberships. Contexto `app.user_id` local a transacción, obtenido de la sesión. La estructura conserva 121 tablas, 85 con FORCE RLS y 208 FK. Exportación, diccionario y diagramas regenerados desde el catálogo aplicado. V1–V11 conservan sus checksums.

Registro/login/logout y creación/listado de organizaciones disponibles en HTTP/Angular. Roles/equipos autorizan operaciones existentes de repositorios/scans. Se crea auditoría al crear organización. Sesiones de servidor en memoria. Estado funcional, decisiones, contratos y límites de navegador en [ACCESO_LOCAL](../ACCESO_LOCAL.md).

Se verificaron 18 casos distintos pertinentes en este bloque con PostgreSQL nativo 18.6, JDK 25 con destino 21 y Maven 3.9.11. Seis de seguridad/arquitectura, cinco HTTP/sesión/permisos, cuatro runtime/RLS, dos de arranque/migración y uno del límite de intentos. La primera actualización detectó una función compartida existente entre esquemas de prueba. Se corrigió V12 antes de entregar y se repitieron los dos casos de migración y los HTTP afectados en otra base desechable. La validación de cuerpo se comprobó junto con la prueba de límite de intentos. Resultado final correcto. Build Angular de producción correcto con Node 24.22.1/npm 11.16.0. API health UP, frontend HTTP 200 y cookie HttpOnly/SameSite=Lax comprobados.

La revisión visual quedó pendiente por host de navegador indisponible y timeout de autorización de Chrome. Docker, pgvector y proveedores de IA conservan su estado pendiente. Los procesos locales y PostgreSQL temporal se detienen al finalizar. A continuación se conserva el informe del bloque 05 como historial de la transición V11.

Referencia: [CODEX_DATABASE_DEPENDENCY_INTELLIGENCE](../CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md). Bloque 05 de la [bitácora](../BITACORA_TRABAJO.md), 14 de noviembre de 2026. Esquema probado en una base temporal; no se actualizó una base externa del usuario ni se declara terminado el producto.

## Resultado

**121 tablas de negocio, 85 con ENABLE/FORCE RLS y 208 FK**, sin contar Flyway. V1–V3 no se reescriben; se añaden ocho migraciones V4–V11. El [diccionario](data-dictionary.md) explica cada tabla; [schema.json](schema.json) procede del catálogo de PostgreSQL.

| Migración | Función |
| --- | --- |
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



Infrastructure delimita tenant mediante `TenantTransactions`, también en el bootstrap. Domain/Application siguen sin Spring/JPA. Credenciales Flyway/runtime separadas y guard contra datasource propietario/privilegiado. Aprovisionamiento del login comprobado dos veces para verificar idempotencia.

## Verificación ejecutada

PostgreSQL **18.6 nativo temporal**, Maven 3.9.11, JDK local 25 y compilación destino Java 21. Compose conserva PostgreSQL 17.7/Java 21; el entorno nativo no prueba esas imágenes.

Primera selección: 8 unitarias/arquitectura/seguridad y 22 de integración, todas correctas. Clases: ImportSbomUseCaseTest, SecurityConfigurationTest, HexagonalArchitectureTest, DatabaseIT, DemoBootstrapIT, SbomImportIT, NormalizedSchemaIT, RuntimeDatabaseIT. Tras V11: solo 7 de NormalizedSchemaIT y 2 métodos de migración/arranque de DatabaseIT, 9 correctas. **31 pruebas distintas**; ocho se repitieron por cambios pertinentes, no son 39 casos diferentes.

Cobertura:

- Instalación limpia, actualización desde V1/V3 con datos, checksums intactos y validación JPA.
- FK del mismo tenant/repositorio/snapshot/paquete/análisis; equipos, comparación, documentos/citas, reportes y entregas.
- Login runtime real sin DDL/propiedad: lecturas/escrituras ajenas y escritura de catálogos rechazadas; contexto transaccional eliminado y cambio interno de tenant rechazado.
- Importador y JPA con ese login; rollback/reimportación/bootstrap.
- Auditoría append-only; VALIDATED exige prueba terminada del mismo análisis; resultados terminados inmutables, PR con aprobación y reintentos acotados.

Siete Excalidraw nativos más SVG/PNG y diccionario generados desde catálogo aplicado. Comprobación estructural: 121 tablas, 208 FK, IDs Únicos y bindings existentes. Se inspeccionaron exportaciones locales; no apertura interactiva en Excalidraw. No se midieron rendimiento/concurrencia ni se ejecutaron Docker, pgvector o IA. Se detiene el servidor temporal al cerrar el bloque.

## Pendientes de producto


RLS no autentica; el tenant de ruta no acredita permisos. Identidades globales requieren acceso restringido por autenticación. APIs de negocio continúan denegadas. No se declara 5NF universal: [normalización](normalization.md) documenta dependencias y excepciones.
