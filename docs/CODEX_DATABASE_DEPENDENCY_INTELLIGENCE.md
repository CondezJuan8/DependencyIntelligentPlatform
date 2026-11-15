# Especificación para Codex: diseño e implementación de la base de datos de Dependency Intelligence

**Tipo de entregable:** especificación ejecutable para Codex\
**Motor:** PostgreSQL\
**Backend:** Java + Spring Boot + Spring Data JPA/Hibernate + Flyway\
**Arquitectura:** monolito modular, DDD y arquitectura hexagonal\
**Diagramas:** archivos nativos de Excalidraw (`.excalidraw`)\
**Estado:** diseño propuesto derivado de los tres documentos fuente; las
decisiones todavía abiertas se deben conservar como configurables y
documentar, no inventar.

## Decisiones vigentes y estado real

Referencia vigente de diseño. Núcleo implementado hasta V12 con 121 tablas, 85 con RLS forzado y 208 FK. V12 permite descubrir organizaciones propias del usuario autenticado. Acceso local y autorización de repositorios entregados en bloque 06. Estado, diagramas, diccionario y normalización se consultan en [database/README.md](database/README.md). Servicios restantes y pgvector opcional según el [informe](database/implementation-report.md).

Decisiones confirmadas por el usuario, que prevalecen sobre las propuestas históricas:

- Producto completo listo para usar, por fases: primero local y después servidor. El plan previo de demo de dos semanas/50 horas ya no limita el alcance ni define la aceptación. No hay nueva fecha total comprometida.
- Correo/contraseña, Google y GitHub como métodos de acceso. Login GitHub y autorización/instalación para acceder a repositorios son flujos distintos. Modelar credenciales locales e identidades externas vinculadas al usuario.
- Java/Maven y JavaScript/npm. El parser actual solo cubre el subconjunto Maven de ejemplo; npm sigue pendiente.
- Primer piloto con equipos y permisos por repositorio, pruebas aisladas de cambios y propuesta de PR sujeta a aprobación. No se dan por confirmados roles personalizados.
- Gemini dentro de la plataforma y Ollama para embeddings. Modelo, versión, dimensión y límites concretos se verificarán al implementar. No se garantiza gratuidad ni una cuota de proveedor.
- No integrar ni agendar Codex dentro del producto hasta una nueva instrucción explícita del usuario. Codex como herramienta de desarrollo es independiente de esto.
- Notificaciones iniciales dentro de la aplicación; correo de notificaciones después. El correo de verificación/recuperación de cuentas es una necesidad separada de autenticación.
- Criterio documental delegado: conservar originales en almacenamiento privado, con descarga/reindexación autorizadas y retención/eliminación configurable por organización. Eliminar temporales tras procesamiento o expiración. Esta decisión sustituye la propuesta histórica de originales solo locales; aún falta implementarla.

No se renumeran ni reescriben migraciones aplicadas. La transición del modelo actual al objetivo debe incluir migraciones y adaptación de los casos de uso existentes.

------------------------------------------------------------------------

## 0. Instrucción principal para Codex

Actúa como arquitecto de datos senior y desarrollador backend
especializado en PostgreSQL, Java y Spring Boot. Debes **diseñar e
implementar una base de datos completa para Dependency Intelligence**,
no limitarte a describirla.

Antes de modificar archivos:

1.  Inspecciona el repositorio y detecta su estructura, convenciones,
    versión de Java/Spring Boot, configuración de PostgreSQL, estrategia
    de migraciones y módulos existentes.
2.  Identifica si ya existen entidades, migraciones o diagramas.
    Reutiliza lo correcto y evita duplicados.
3.  Lee esta especificación completa.
4.  Presenta un plan breve de implementación y los riesgos/decisiones
    que requieren confirmación.
5.  Implementa el esquema, las migraciones Flyway, las restricciones,
    índices, documentación y diagramas.
6.  Ejecuta las pruebas disponibles. Si hay Docker y Testcontainers
    configurados, valida las migraciones contra una instancia real de
    PostgreSQL.
7.  No afirmes que una migración o diagrama está validado si no lo
    ejecutaste.

**No generes una base de datos CRUD mínima.** El modelo debe cubrir la
visión funcional completa del producto: identidad, organizaciones,
permisos, GitHub, repositorios, análisis, SBOM, componentes, relaciones,
vulnerabilidades, inventario, historial, cambios, documentos, RAG, IA,
reportes, notificaciones, auditoría y procesamiento asíncrono.

**No conviertas todas las fases en requisitos del MVP.** El esquema debe
permitir la evolución por módulos, pero las migraciones y extensiones de
fase futura deben estar claramente separadas. No obligues a activar
pgvector ni a pagar un proveedor de IA para ejecutar el núcleo inicial.

------------------------------------------------------------------------

## 1. Fuentes analizadas y alcance

Codex debe tomar como fuente los tres archivos del proyecto:

1.  `DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md`
2.  `Requerimientos.md`
3.  `STACK_TECNOLOGICO_DEPENDENCY_INTELLIGENCE.md`

### 1.1 Visión funcional que debe quedar soportada

Dependency Intelligence es una plataforma SaaS B2B para entender,
rastrear y analizar las dependencias de software de una organización.
Sus módulos son:

-   Identity & Organization.
-   Access Control / RBAC.
-   GitHub Integration.
-   Repository Management.
-   Scan Management / Scan History.
-   SBOM & Components.
-   Dependency Relationships / Dependency Graph.
-   Vulnerability Intelligence.
-   Inventory.
-   Snapshot Comparison.
-   Update Recommendations.
-   Change Impact Analysis.
-   Documentation Center.
-   AI Assistant / RAG.
-   Reporting Center.
-   Notifications.
-   Audit.
-   Background Jobs / Processing.

### 1.2 Flujo principal del producto

`Organización → instala GitHub App → selecciona repositorios → se solicita un análisis → se obtiene un commit concreto → cdxgen genera SBOM CycloneDX → se normalizan componentes y relaciones → se consultan vulnerabilidades OSV → se persisten hallazgos y evidencias → se visualizan inventario, riesgos, historial y grafo.`

### 1.3 Flujo documental posterior

`Usuario carga PDF/documento por HTTPS → archivo temporal validado → trabajo asíncrono → Apache Tika/PDFBox y OCR cuando aplique → fragmentos con página/sección/orden → embeddings → PostgreSQL + pgvector → recuperación híbrida con permisos → respuesta de IA con citas.`

La política vigente conserva originales en almacenamiento privado y
texto indexado, embeddings y metadatos en los sistemas correspondientes.
Eliminar los temporales al terminar o expirar su procesamiento y aplicar
retención/eliminación de originales por organización. Registrar hash y
procedencia sin afirmar que un hash sustituye al original. La propuesta
anterior de `Requerimientos.md` queda como antecedente histórico.

### 1.4 Principios de datos obligatorios

-   PostgreSQL es la fuente de verdad de los datos estructurados.
-   `pgvector` se reserva para búsqueda semántica documental; nunca
    sustituye el inventario relacional, las versiones ni los hallazgos
    de vulnerabilidad.
-   Cada resultado debe poder rastrearse hasta organización,
    repositorio, commit, análisis, herramienta, versión y fuente.
-   Separar el catálogo lógico de un paquete de su versión y de su
    presencia en un repositorio/scan.
-   Las relaciones entre dependencias pertenecen al snapshot que las
    detectó.
-   Diferenciar impacto **KNOWN**, **POTENTIAL** y **VALIDATED**. No
    afirmar que un servicio se comunica con otro porque aparezcan
    dependencias relacionadas.
-   No almacenar tokens de GitHub ni secretos en texto plano.
-   La IA no es fuente de verdad para vulnerabilidades ni puede ejecutar
    SQL libre.
-   No almacenar asociaciones multivaluadas como arrays dentro de una
    fila.
-   No duplicar atributos que puedan derivarse por joins, salvo
    justificación de rendimiento con una vista/materialización mantenida
    y documentada.

------------------------------------------------------------------------

## 2. Decisiones técnicas y convenciones

### 2.1 PostgreSQL y extensiones

-   Usar una versión de PostgreSQL compatible con la versión disponible
    en el proyecto y fijarla en Docker/CI.
-   UUID como identificador primario (`uuid`). Generación en aplicación
    o `gen_random_uuid()` según versión y estándar del repositorio.
-   Timestamps con zona horaria: `timestamptz`, persistidos en UTC.
-   Fechas de calendario cuando realmente sean fechas sin hora: `date`.
-   Texto Unicode con `text` o `varchar` solo cuando exista un límite de
    negocio real.
-   `numeric` para cantidades decimales; nunca `float` para métricas que
    requieran exactitud.
-   `jsonb` únicamente para payloads originales externos, configuración
    flexible justificada o metadatos técnicos no usados como atributos
    canónicos de negocio.
-   No usar arrays PostgreSQL para representar relaciones de negocio.
-   No usar enums PostgreSQL para estados que puedan evolucionar sin
    migración compleja; preferir catálogos de estados o `CHECK` si el
    conjunto es estable y local.
-   `pgvector` debe instalarse en una migración de fase documental,
    separada de las migraciones esenciales del MVP.
-   No instalar extensiones que no se usen.

### 2.2 Convenciones de nombres

-   Tablas y columnas en `snake_case`, nombres en inglés, consistentes
    con el stack.
-   Tablas en plural o singular: inspeccionar convención del repositorio
    y usar una sola. Si no existe, usar nombres en plural.
-   PK: `id`.
-   FK: `<entidad>_id`.
-   Columnas de tenant: `organization_id`.
-   Fechas: `created_at`, `updated_at`, `deleted_at` solo si el borrado
    lógico está justificado.
-   No usar `status` como texto libre.
-   Tablas puente con nombres semánticos (`organization_memberships`,
    `role_permissions`, `document_components`).
-   Añadir `created_at`/`updated_at` solo cuando tengan sentido; no
    agregar timestamps a cada tabla puente por inercia.

### 2.3 Multi-tenancy

La plataforma es multi-tenant. La organización es el límite principal de
propiedad y acceso.

-   Las tablas operativas propiedad de una organización deben tener
    `organization_id NOT NULL`.
-   Las FKs entre datos de tenant deben garantizar que ambos registros
    pertenecen a la misma organización. Usar claves únicas compuestas
    `(organization_id, id)` y FKs compuestas cuando corresponda.
-   No confiar únicamente en filtros de Java para aislar tenants.
-   Diseñar Row-Level Security (RLS) para tablas tenant-scoped.
    Activarla de forma segura y documentar cómo establecer el contexto
    de tenant por transacción/ conexión.
-   La aplicación debe usar un rol SQL no propietario de las tablas y
    sin `BYPASSRLS`.
-   Activar `FORCE ROW LEVEL SECURITY` donde sea viable.
-   Evitar que un contexto de tenant quede reutilizado en una conexión
    del pool. Establecerlo transaccionalmente con `SET LOCAL` o
    mecanismo equivalente y limpiarlo automáticamente al terminar la
    transacción.
-   En tests, demostrar que una organización no puede leer, insertar,
    actualizar ni enlazar registros de otra.
-   Los catálogos globales (ecosistemas, fuentes, herramientas, permisos
    globales) pueden no llevar `organization_id`; documentar por qué son
    globales.

**Importante:** RLS no sustituye autorización de negocio. Spring
Security y los servicios de dominio deben comprobar membership, roles y
permisos.

### 2.4 Normalización: objetivo hasta 5NF

Diseña el modelo con objetivo de 5NF donde corresponda y documenta las
dependencias funcionales y las descomposiciones relevantes.

-   **1NF:** valores atómicos; nada de listas de repositorios,
    versiones, roles, vulnerabilidades o asociaciones en una celda.
-   **2NF:** cada atributo no clave depende de toda la clave candidata;
    revisar especialmente tablas puente con PK compuestas.
-   **3NF:** eliminar dependencias transitivas; no repetir nombre de
    ecosistema, proveedor, rol, severidad o estado en todas las tablas
    si existe catálogo.
-   **BCNF:** para cada dependencia funcional no trivial, el
    determinante debe ser superclave, cuando aplique.
-   **4NF:** separar hechos multivaluados independientes, por ejemplo
    aliases de una vulnerabilidad, referencias, licencias y asociaciones
    documentales.
-   **5NF:** separar relaciones ternarias o de mayor grado en relaciones
    binarias cuando la descomposición sea sin pérdida y las
    restricciones puedan expresarse correctamente. Usar tablas puente
    explícitas para M:N y asociaciones independientes.

No declares que todo el esquema está formalmente demostrado en 5NF sin
revisar las dependencias funcionales de cada relación. Incluye en la
documentación una matriz por módulo con: claves candidatas, dependencias
funcionales principales, forma normal alcanzada y excepciones
justificadas.

**No sacrifiques rendimiento o integridad por una etiqueta de
normalización.** Si se necesita una vista materializada o tabla de
lectura, mantenerla como proyección derivada, explicar su fuente y
estrategia de refresco, y no convertirla en fuente de verdad.

------------------------------------------------------------------------

## 3. Modelo lógico por dominios y tablas requeridas

Los nombres siguientes son la propuesta de referencia. Codex puede
adaptarlos a las convenciones reales del repositorio, pero debe mantener
las entidades y relaciones funcionales. Toda tabla debe tener propósito,
PK, FK, restricciones, índices y alcance de tenant documentados.

### 3.1 Identity, Organizations y RBAC

  -----------------------------------------------------------------------
  Tabla                               Propósito / atributos principales
  ----------------------------------- -----------------------------------
  `users`                             Identidad global: `id`, `email`,
                                      `email_verified_at`,
                                      `password_hash` solo si
                                      autenticación local,
                                      `display_name`, `avatar_url`,
                                      `is_active`, timestamps. Email
                                      normalizado y único. No duplicar
                                      datos personales por organización.

  `organizations`                     Tenant: `id`, `name`, `slug`,
                                      `legal_name` opcional, `status_id`,
                                      `created_at`, `updated_at`. Slug
                                      único según política.

  `organization_memberships`          Relación usuario-organización:
                                      `organization_id`, `user_id`,
                                      `membership_status_id`,
                                      `joined_at`, `invited_by_user_id`.
                                      Unique `(organization_id,user_id)`.

  `roles`                             Catálogo de roles, global o con
                                      scope de organización si el
                                      producto permite roles
                                      personalizados.

  `permissions`                       Catálogo de permisos atómicos, por
                                      ejemplo `repository.read`,
                                      `scan.create`, `document.read`,
                                      `report.export`,
                                      `organization.manage`.

  `role_permissions`                  Puente M:N entre rol y permiso.

  `membership_roles`                  Puente M:N entre membresía y rol;
                                      permite varios roles sin arrays.

  `membership_invitations`            Invitaciones pendientes: tenant,
                                      email normalizado, token hash,
                                      expiración, invitador, estado y
                                      aceptación. Nunca guardar token de
                                      invitación en texto plano.

  `organization_teams`                Equipos internos opcionales para
                                      agrupar usuarios.

  `team_memberships`                  Puente M:N entre equipos y
                                      membresías.

  `repository_team_access`            Permisos de equipo sobre
                                      repositorios si se implementa
                                      autorización por equipo; no
                                      duplicar roles como texto.
  -----------------------------------------------------------------------

Reglas: - Un usuario puede pertenecer a varias organizaciones. - La
membresía es la relación tenant-aware entre usuario y organización. - Un
usuario no obtiene acceso a una organización por conocer un UUID. - Los
roles y permisos se resuelven con relaciones, no con columnas
CSV/JSON. - Si se usa un proveedor OIDC externo, guardar el vínculo en
una tabla `user_identities` con `provider`, `provider_subject`,
`user_id`; no asumir autenticación local obligatoria.

### 3.2 Catálogos compartidos

  -----------------------------------------------------------------------
  Tabla                               Propósito / atributos principales
  ----------------------------------- -----------------------------------
  `integration_providers`             Proveedores soportados: `github`,
                                      futuros GitLab/Bitbucket/Azure
                                      DevOps.

  `package_ecosystems`                Ecosistemas normalizados: Maven,
                                      npm, PyPI, NuGet, Go, etc. Código
                                      estable y nombre.

  `dependency_scopes`                 `runtime`, `development`, `test`,
                                      `optional`, `provided`, etc.

  `dependency_relationship_types`     `depends_on`, `contains`,
                                      `provides`, `optional_dependency`,
                                      etc. según CycloneDX y mapeo
                                      interno.

  `vulnerability_sources`             Fuentes como OSV; código, nombre y
                                      URL base.

  `scanner_tools`                     Herramientas como cdxgen,
                                      OSV-Scanner si se incorpora.

  `document_types`                    PDF, DOCX, Markdown, TXT, README,
                                      OpenAPI, ADR, changelog, etc.

  `license_catalog`                   Identificador SPDX cuando aplique,
                                      nombre y URL.

  `severity_levels`                   Severidades normalizadas (UNKNOWN,
                                      LOW, MEDIUM, HIGH, CRITICAL) con
                                      orden.

  `job_types`                         Tipos de trabajos asíncronos.

  `job_statuses`                      PENDING, RUNNING, COMPLETED,
                                      FAILED, CANCELLED, RETRYING, según
                                      necesidad.

  `notification_channels`             IN_APP, EMAIL, WEBHOOK, futuros
                                      canales.

  `report_types`                      EXECUTIVE, DEPENDENCY,
                                      VULNERABILITY, IMPACT, REPOSITORY,
                                      CHANGE, SBOM.

  `report_formats`                    PDF, CSV, JSON, CYCLONEDX, SPDX.
  -----------------------------------------------------------------------

No crear catálogos por cada palabra del dominio sin necesidad. Para
conjuntos pequeños e inmutables, un `CHECK` puede ser mejor. Los
catálogos que reciban referencias FK deben tener claves y códigos únicos
estables.

### 3.3 Integraciones y repositorios

  -----------------------------------------------------------------------
  Tabla                               Propósito / atributos principales
  ----------------------------------- -----------------------------------
  `integrations`                      Conexión de un tenant con un
                                      proveedor: `organization_id`,
                                      `provider_id`,
                                      `external_account_id`,
                                      `external_account_login`, `status`,
                                      `connected_at`, `disconnected_at`,
                                      `credential_reference`. Unique por
                                      organización/proveedor/cuenta
                                      externa. No guardar tokens en
                                      claro.

  `github_installations`              Datos propios de GitHub App:
                                      `integration_id`,
                                      `installation_external_id`,
                                      `account_type`,
                                      `permissions_snapshot` solo si es
                                      útil y controlado, timestamps. ID
                                      externo único por proveedor.

  `repositories`                      Repositorio conectado: tenant,
                                      integración,
                                      `external_repository_id`,
                                      `owner_login`, `name`, `full_name`,
                                      `html_url`, `default_branch`,
                                      `visibility`, `is_archived`,
                                      `is_enabled`, `last_synced_at`.
                                      Unique por tenant e ID externo.

  `repository_branches`               Ramas rastreadas opcionalmente:
                                      repositorio, nombre, último commit
                                      observado, default flag.

  `repository_commits`                Commit observado: repositorio, SHA,
                                      autor/fecha si se necesita, mensaje
                                      opcional y URL. Unique
                                      `(repository_id, commit_sha)`. No
                                      duplicar SHA como si fuera una
                                      entidad global sin scope.

  `webhook_events`                    Eventos entrantes idempotentes:
                                      proveedor, delivery ID,
                                      integración, evento, recibido,
                                      procesado, estado, hash/payload
                                      original restringido. Unique
                                      `(provider_id, delivery_id)`.

  `integration_sync_runs`             Historial de sincronización de
                                      instalaciones/repositorios y
                                      errores.

  `repository_settings`               Configuración por repositorio, solo
                                      atributos que no sean relaciones
                                      independientes.
  -----------------------------------------------------------------------

Seguridad: - `credential_reference` debe apuntar a un gestor de secretos
o a un valor cifrado con clave externa; no guardar private keys ni
installation tokens en texto plano. - No guardar permisos como una lista
CSV. - Validar firma de webhooks antes de persistir el evento como
confiable. - No almacenar contenido de repositorios que no sea necesario
para generar el SBOM.

### 3.4 Background jobs y trazabilidad de procesos

  -----------------------------------------------------------------------
  Tabla                               Propósito / atributos principales
  ----------------------------------- -----------------------------------
  `background_jobs`                   Cola persistente MVP: tenant
                                      nullable solo para trabajos
                                      globales explícitos; tipo, estado,
                                      payload mínimo,
                                      `deduplication_key`,
                                      `available_at`, `locked_at`,
                                      `locked_by`, `attempt_count`,
                                      `max_attempts`, `last_error_code`,
                                      `last_error_message`, timestamps.

  `background_job_attempts`           Cada intento: job, número, inicio,
                                      fin, resultado, error resumido,
                                      worker.

  `background_job_events`             Historial de transiciones y eventos
                                      de ejecución.
  -----------------------------------------------------------------------

Reglas: - El trabajo pesado no se ejecuta dentro de una petición HTTP
larga. - Crear job y devolver identificador. - Reintentos limitados y
backoff. - Idempotencia por clave de deduplicación. - Evitar guardar
secretos o payloads completos de repositorios en `payload`. - RabbitMQ
no es requisito inicial; la tabla persistente y worker bastan para el
MVP.

### 3.5 Scans, SBOM y snapshots

  -----------------------------------------------------------------------
  Tabla                               Propósito / atributos principales
  ----------------------------------- -----------------------------------
  `scan_profiles`                     Configuración reutilizable de
                                      análisis por
                                      organización/repositorio:
                                      ecosistemas, flags y políticas
                                      configurables; no almacenar listas
                                      relacionales en JSON.

  `scans`                             Unidad de análisis: tenant,
                                      repositorio, commit, branch/ref,
                                      estado, tipo
                                      manual/webhook/scheduled,
                                      solicitado por, inicio/fin,
                                      duración, scanner tool/version,
                                      correlación/job.

  `scan_stages`                       Etapas del análisis:
                                      checkout/fetch, SBOM generation,
                                      parse, normalization, vulnerability
                                      lookup, completed.

  `scan_stage_executions`             Ejecución de etapa con estado,
                                      timestamps, métricas, error.

  `scan_logs`                         Eventos de diagnóstico
                                      estructurados, con redacción de
                                      secretos y retención limitada.

  `scan_artifacts`                    Artefactos generados: SBOM
                                      CycloneDX, logs sanitizados,
                                      resultados externos; metadata,
                                      formato, hash, tamaño, ubicación
                                      segura. No guardar binarios enormes
                                      en filas si se usa object storage.

  `sbom_documents`                    Metadatos del SBOM: scan, serial
                                      number, spec version, format,
                                      generated_at, hash, artifact_id.

  `sbom_external_references`          Referencias externas del SBOM si se
                                      requiere persistirlas de forma
                                      consultable.

  `scan_comparisons`                  Comparación entre dos scans del
                                      mismo repositorio/tenant.

  `scan_component_changes`            Cambios ADDED, REMOVED, UPDATED,
                                      UNCHANGED entre snapshots;
                                      referencias a componentes/versiones
                                      y evidencia. Solo almacenar si
                                      aporta historial y
                                      reproducibilidad; derivable de
                                      comparación, por lo que documentar
                                      su naturaleza como resultado
                                      persistido.
  -----------------------------------------------------------------------

Restricciones: - El scan pertenece a un repositorio y un commit
concreto. - No duplicar scans por webhook repetido: idempotencia por
repositorio, commit, perfil/configuración y tipo de ejecución según la
política. - No borrar scans previos cuando llega un análisis nuevo. -
Los estados deben tener transiciones controladas. - Una comparación solo
puede unir scans del mismo repositorio y tenant. - Los artefactos deben
conservar hash, formato, herramienta y origen.

### 3.6 Catálogo de paquetes, versiones y presencia en scans

Separar el paquete de su versión y de su aparición concreta en un scan.

  -----------------------------------------------------------------------
  Tabla                               Propósito / atributos principales
  ----------------------------------- -----------------------------------
  `components`                        Identidad lógica del paquete:
                                      `package_ecosystem_id`, `namespace`
                                      nullable, `name`,
                                      `normalized_name`, `purl_type` si
                                      se normaliza aparte, `description`
                                      solo si el catálogo lo provee.
                                      Unique por ecosistema + namespace
                                      normalizado + nombre normalizado.

  `component_identifiers`             Identificadores alternativos: PURL,
                                      CPE, SWID u otros; tipo + valor
                                      único en el ámbito adecuado. Una
                                      fila por identificador.

  `component_versions`                Versión exacta textual del paquete,
                                      `component_id`, `version`,
                                      `normalized_version` opcional,
                                      `published_at` si existe. Unique
                                      `(component_id, version)`. Nunca
                                      asumir que toda versión sigue
                                      SemVer.

  `scan_components`                   Aparición de un component_version
                                      en un scan: tenant, scan,
                                      component_version, bom_ref, group,
                                      scope, direct/transitive derivado
                                      del grafo, hashes/metadata de
                                      fuente solo si necesario. Unique
                                      `(scan_id, bom_ref)` y restricción
                                      de pertenencia.

  `dependency_edges`                  Relación dirigida dentro de un
                                      scan: `scan_id`,
                                      `source_scan_component_id`,
                                      `target_scan_component_id`,
                                      `relationship_type_id`, optional
                                      scope/evidence. Evitar duplicados.
                                      El grafo se reconstruye desde esta
                                      tabla.

  `component_hashes`                  Hashes de artefactos/paquetes:
                                      component_version, algoritmo,
                                      digest. Una fila por
                                      algoritmo/hash.

  `component_licenses`                Asociación
                                      componente-versión/licencia o
                                      presencia por scan según
                                      procedencia. Modelar cuidadosamente
                                      si la licencia es declarada por
                                      versión o detectada en un SBOM
                                      específico; no mezclar ambos
                                      hechos.

  `scan_component_licenses`           Puente para licencias declaradas en
                                      la aparición concreta del SBOM, si
                                      las licencias pueden variar por
                                      artefacto o fuente.

  `component_external_references`     Referencias de catálogo/versión, si
                                      se consultan de forma
                                      independiente.

  `component_authors` /               Solo si el producto realmente
  `component_maintainers`             consulta y utiliza
                                      autores/mantenedores; no crear por
                                      especulación.
  -----------------------------------------------------------------------

Reglas fundamentales: - `components` representa identidad del paquete,
no una instalación. - `component_versions` representa una versión
publicada/referenciable del paquete. - `scan_components` representa que
esa versión aparece en un análisis concreto. - `dependency_edges`
representa la relación observada entre dos apariciones dentro del mismo
scan. - La condición directa/transitiva debe derivarse de la estructura
de aristas o almacenarse como clasificación de la aparición solo si el
SBOM proporciona evidencia clara. No mantener dos fuentes de verdad
inconsistentes. - El componente raíz de la aplicación puede modelarse
como componente raíz del SBOM, separado de dependencias de terceros. -
Las rutas del grafo se calculan desde `dependency_edges`; no almacenar
todas las rutas posibles en una tabla salvo necesidad demostrada. -
`bom_ref` es una referencia dentro de un SBOM, no necesariamente un PURL
ni un ID global.

### 3.7 Vulnerability Intelligence

  -------------------------------------------------------------------------------
  Tabla                               Propósito / atributos principales
  ----------------------------------- -------------------------------------------
  `vulnerabilities`                   Vulnerabilidad canónica: fuente primaria,
                                      ID externo, resumen/descripción
                                      normalizada, fecha publicada/modificada,
                                      estado. Unique
                                      `(vulnerability_source_id, external_id)`.

  `vulnerability_aliases`             IDs alternativos (CVE, GHSA u otros) como
                                      filas separadas; unique por
                                      tipo/valor/fuente cuando aplique. No usar
                                      un array de aliases.

  `vulnerability_references`          URLs/referencias externas en filas
                                      independientes.

  `vulnerability_severities`          Evaluaciones de severidad por
                                      fuente/método: catálogo severity, score,
                                      vector CVSS, versión CVSS, fuente y fecha.
                                      Una vulnerabilidad puede tener varias
                                      evaluaciones.

  `vulnerability_affected_packages`   Paquete/ecosistema afectado según el
                                      advisory.

  `vulnerability_affected_ranges`     Rangos afectados por paquete y
                                      vulnerabilidad; tipo de rango y
                                      límites/eventos.

  `vulnerability_range_events`        Eventos de introducción, límite afectado y
                                      fix, con secuencia y valor de versión.

  `vulnerability_fixed_versions`      Versiones corregidas indicadas por la
                                      fuente, separadas como valores.

  `vulnerability_source_records`      Payload original JSONB, hash, fetched_at,
                                      source, parser version. Conservar solo lo
                                      necesario para auditoría/reprocesamiento y
                                      proteger datos.

  `scan_vulnerability_findings`       Hallazgo por scan_component y
                                      vulnerabilidad: estado de coincidencia,
                                      severidad aplicada, evidencia, fuente,
                                      detectado_at, dismissed/resolution status
                                      si existe flujo.

  `finding_evidence`                  Evidencias de por qué se asoció la
                                      vulnerabilidad: rango, versión observada,
                                      identificador de paquete, respuesta del
                                      proveedor.
  -------------------------------------------------------------------------------

Reglas: - No asumir que una vulnerabilidad siempre tiene severidad o
versión corregida. - No confundir `OSV ID` con CVE; aliases se almacenan
por separado. - Mantener fuente y fecha de consulta. - Evitar duplicados
de hallazgos al reprocesar la misma respuesta. - La coincidencia entre
una versión instalada y un rango vulnerable debe ser determinista,
reproducible y probada por ecosistema. - Los datos originales de OSV se
conservan como evidencia, pero el modelo relacional normalizado es el
que se consulta en la aplicación.

### 3.8 Recomendaciones y Change Impact Analysis

  -----------------------------------------------------------------------
  Tabla                               Propósito / atributos principales
  ----------------------------------- -----------------------------------
  `impact_analyses`                   Solicitud de análisis: tenant,
                                      repositorio, scan base,
                                      componente/versión actual, tipo de
                                      operación (ADD, UPDATE, REMOVE,
                                      REPLACE), versión objetivo si
                                      aplica, commit base, estado,
                                      solicitante, método/versión.

  `impact_findings`                   Resultado individual: tipo de
                                      impacto, nivel
                                      KNOWN/POTENTIAL/VALIDATED,
                                      severidad/riesgo si aplica,
                                      confianza, resumen, estado de
                                      revisión.

  `impact_finding_entities`           Entidades relacionadas con el
                                      hallazgo (repositorio, componente,
                                      versión, documento, vulnerabilidad)
                                      mediante modelo tipado o tablas
                                      puente específicas. Evitar una FK
                                      polimórfica sin integridad
                                      referencial.

  `impact_evidences`                  Evidencia asociada: tipo, origen,
                                      referencia, extracto sanitizado,
                                      ubicación/página, fecha, hash.

  `impact_recommendations`            Recomendación técnica: acción,
                                      prioridad, explicación y
                                      fundamento.

  `impact_recommendation_evidences`   Puente M:N entre recomendaciones y
                                      evidencias.

  `impact_test_suggestions`           Pruebas recomendadas por resultado,
                                      descripción, categoría y prioridad.

  `version_change_records`            Changelog/release notes
                                      normalizados por paquete y
                                      versiones, con fuente y fechas, si
                                      se incorpora la extracción.

  `change_record_evidences`           Evidencia documental de cambios
                                      entre versiones.

  `document_review_flags`             Marcar documentos potencialmente
                                      desactualizados por cambio; estado
                                      y motivo, sin declarar que están
                                      incorrectos automáticamente.
  -----------------------------------------------------------------------

Reglas: - Todo análisis se asocia a repositorio, scan/commit base,
versión actual y versión objetivo cuando aplique. - Guardar método,
versión de reglas, fuentes y evidencias para reproducibilidad. -
Clasificar resultados como KNOWN, POTENTIAL o VALIDATED. - La confianza
numérica, si se utiliza, debe tener escala y significado documentados. -
Un resultado POTENTIAL no se presenta como rotura confirmada. - La
operación inicial no modifica el repositorio. - No inferir comunicación
runtime de microservicios desde un SBOM.

### 3.9 Documentation Center y RAG

  ----------------------------------------------------------------------------------
  Tabla                               Propósito / atributos principales
  ----------------------------------- ----------------------------------------------
  `documents`                         Documento lógico tenant-scoped: nombre, tipo,
                                      origen, estado, descripción, uploaded_by,
                                      política de retención, created_at.

  `document_versions`                 Versión/edición del documento: SHA-256,
                                      tamaño, MIME validado, fecha de carga,
                                      extractor version, estado de indexación,
                                      referencia privada al original retenido y
                                      referencia temporal solo mientras exista
                                      el archivo de trabajo correspondiente.

  `document_associations`             Asociación de documento con
                                      organización/repositorio/componente/versión,
                                      si se usa un tipo de entidad genérico,
                                      acompañarlo de validación fuerte; preferir
                                      puentes específicos (`document_repositories`,
                                      `document_components`,
                                      `document_component_versions`) para FKs reales
                                      y 5NF.

  `document_repositories`             Puente documento-repositorio.

  `document_components`               Puente documento-componente.

  `document_component_versions`       Puente documento-versión de componente.

  `document_chunks`                   Fragmentos extraídos: document_version,
                                      ordinal, texto, page_start/page_end,
                                      section_path, token_count, content_hash,
                                      metadata técnica mínima. Unique
                                      `(document_version_id, ordinal)`.

  `embedding_models`                  Proveedor, nombre del modelo, dimensión,
                                      versión y configuración relevante. No asumir
                                      modelo o dimensión no confirmados.

  `document_chunk_embeddings`         Vector de cada fragmento y modelo. Unique
                                      `(document_chunk_id, embedding_model_id)`. La
                                      dimensión debe ser consistente con el modelo.

  `document_processing_runs`          Historial de
                                      extracción/OCR/chunking/indexación,
                                      herramienta y versión, estado, métricas y
                                      errores.

  `document_processing_artifacts`     Artefactos temporales o derivados necesarios
                                      para diagnóstico, con retención limitada y
                                      almacenamiento seguro.

  `assistant_conversations`           Conversaciones del asistente dentro de una
                                      organización, creadas por usuario y con
                                      control de acceso.

  `assistant_messages`                Mensajes de usuario/asistente, orden, modelo,
                                      estado, consumo y versión de prompt. No
                                      almacenar secretos.

  `assistant_message_evidences`       Fragmentos/documentos/datos estructurados
                                      usados para respaldar una respuesta,
                                      posición/cita y relevancia.

  `assistant_model_providers`         Catálogo de proveedores de embeddings/LLM.

  `assistant_model_versions`          Modelos concretos y parámetros no secretos.

  `assistant_prompt_versions`         Versiones de instrucciones/prompts del
                                      sistema, si se necesita reproducibilidad.
  ----------------------------------------------------------------------------------

Reglas de retención: - Original privado con tamaño y tipo validados,
análisis antimalware si está disponible y política de eliminación por
organización. Eliminar los archivos temporales al finalizar o expirar
el trabajo. - Guardar hash SHA-256, texto extraído, página/sección y
procedencia. - Un hash no permite volver a abrir el PDF. - Autorizar
descarga y reindexación del original retenido; si se elimina según
política, reindexar puede exigir nueva carga. - Las citas por página
solo son fiables si se preservó la ubicación durante extracción. -
Tablas, imágenes, sellos y firmas pueden requerir el original. - No
borrar el original automáticamente solo por terminar la indexación. -
Recuperación RAG siempre filtrada por organización y permisos antes de
pasar fragmentos al LLM. - No guardar vectores de diferentes dimensiones
en una misma columna tipada sin una estrategia explícita.

**pgvector:** separar la migración `Vx__enable_pgvector.sql` de las
migraciones core. Si el modelo/dimensión no está definido, dejar la
columna/indexación vectorial como decisión pendiente y documentar la
alternativa. No inventar la dimensión del modelo Ollama seleccionado.

### 3.10 Reporting Center

  -----------------------------------------------------------------------
  Tabla                               Propósito / atributos principales
  ----------------------------------- -----------------------------------
  `report_definitions`                Tipos/definiciones de reportes
                                      disponibles.

  `report_jobs`                       Solicitud de generación: tenant,
                                      tipo, formato, filtros,
                                      solicitante, estado, fecha y
                                      parámetros.

  `report_artifacts`                  Archivo generado: job, hash,
                                      tamaño, content type, ubicación,
                                      expiración y fecha de generación.

  `report_schedules`                  Programación futura
                                      semanal/mensual, zona horaria,
                                      destinatarios y estado. Implementar
                                      en fase posterior.

  `report_schedule_recipients`        Destinatarios separados en filas,
                                      no arrays.
  -----------------------------------------------------------------------

Formatos: PDF, CSV, JSON, CycloneDX y SPDX. Un reporte siempre debe
poder rastrearse al scope y al snapshot usado. No almacenar filtros
complejos como un JSON canónico si deben consultarse relacionalmente;
JSONB puede utilizarse para parámetros de ejecución no estructurales.

### 3.11 Notifications y alertas

  -----------------------------------------------------------------------
  Tabla                               Propósito / atributos principales
  ----------------------------------- -----------------------------------
  `notification_rules`                Reglas configuradas por tenant:
                                      nueva vulnerabilidad, cambio de
                                      severidad, dependencia
                                      nueva/eliminada/actualizada, etc.

  `notification_subscriptions`        Suscripción de usuarios/membresías
                                      a eventos/reglas.

  `notifications`                     Evento/notificación lógica con
                                      tenant, tipo, entidad relacionada,
                                      título/resumen, estado y
                                      deduplication key.

  `notification_deliveries`           Entrega por canal y destinatario,
                                      estado, intentos, fecha y error.

  `notification_delivery_attempts`    Historial de intentos de entrega.
  -----------------------------------------------------------------------

Reglas: - Evitar duplicados mediante clave de deduplicación y
restricciones únicas. - Separar el evento lógico de sus entregas por
canal. - No almacenar varios destinatarios en una cadena separada por
comas. - No enviar correos dentro de la transacción que crea el evento;
generar trabajo asíncrono.

### 3.12 Audit y compliance

  -----------------------------------------------------------------------
  Tabla                               Propósito / atributos principales
  ----------------------------------- -----------------------------------
  `audit_events`                      Registro de acciones: tenant, actor
                                      user/membership, acción, recurso,
                                      fecha, resultado, IP/agent si la
                                      política lo permite, correlation
                                      ID.

  `audit_event_changes`               Cambios de atributos auditables
                                      como filas, evitando guardar
                                      secretos o valores sensibles.

  `data_retention_policies`           Política por tenant/tipo de dato:
                                      periodo, acción, base
                                      legal/configuración y estado.

  `data_deletion_requests`            Solicitudes de eliminación de
                                      documentos, integraciones o datos,
                                      estado y ejecución.
  -----------------------------------------------------------------------

Auditoría: - Append-only para eventos. - No actualizar ni borrar eventos
ordinarios; definir mecanismo administrativo excepcional. - Redactar
secretos, tokens y contenido sensible. - Retención configurable y
documentada. - No almacenar datos personales o IP sin necesidad y
política explícita.

------------------------------------------------------------------------

## 4. Relaciones esenciales y reglas de integridad

Codex debe traducir estas reglas a FK, UNIQUE, CHECK, EXCLUDE cuando
corresponda, índices y validaciones de dominio:

1.  `organization_memberships` tiene UNIQUE
    `(organization_id, user_id)`.
2.  Una membership solo referencia un usuario y organización existentes.
3.  `repositories` pertenece a una integración de la misma organización.
4.  `scans` pertenece a un repositorio de la misma organización.
5.  `scan_components` pertenece a un scan y una component_version
    existente.
6.  `dependency_edges` conecta dos `scan_components` del mismo scan.
7.  `scan_vulnerability_findings` asocia un hallazgo con un
    `scan_component` del mismo tenant y una vulnerabilidad canónica.
8.  `scan_comparisons` compara dos scans del mismo repositorio/tenant y
    no debe permitir comparar un scan consigo mismo.
9.  `document_chunks` pertenece a una `document_version`.
10. `document_chunk_embeddings` pertenece a un fragmento y un modelo de
    embedding.
11. `assistant_message_evidences` solo referencia evidencia accesible al
    tenant y al usuario.
12. `impact_analyses` referencia el scan base y repositorio compatibles.
13. `impact_findings` pertenece al análisis correspondiente.
14. `report_jobs` y `report_artifacts` pertenecen al mismo tenant.
15. `notifications` y `notification_deliveries` no pueden cruzar
    organizaciones.
16. Todas las tablas tenant-scoped deben tener índices por
    `organization_id` en las consultas habituales.
17. Las tablas puente deben tener UNIQUE/PK para impedir duplicados.
18. FKs deben tener `ON DELETE` explícito y deliberado. Evitar `CASCADE`
    indiscriminado en datos históricos/auditables.

Para cumplir FKs compuestas tenant-safe, considera
`UNIQUE (organization_id, id)` en tablas tenant-scoped y referencias
`(organization_id, foreign_id)`. No uses FK simple si permitiría asociar
entidades de tenants diferentes.

------------------------------------------------------------------------

## 5. Índices y rendimiento

Diseñar índices a partir de consultas reales. Como mínimo evaluar:

-   `users(email_normalized)` UNIQUE.
-   `organizations(slug)` UNIQUE.
-   `organization_memberships(organization_id, user_id)` UNIQUE.
-   `repositories(organization_id, integration_id, external_repository_id)`
    UNIQUE.
-   `repositories(organization_id, full_name)`.
-   `scans(organization_id, repository_id, created_at DESC)`.
-   `scans(repository_id, commit_sha, scan_profile_id)` para
    idempotencia según política.
-   `scan_components(scan_id, component_version_id)`.
-   `scan_components(organization_id, component_version_id)`.
-   `dependency_edges(scan_id, source_scan_component_id)` y
    `(scan_id, target_scan_component_id)`.
-   `components(package_ecosystem_id, normalized_name, namespace)`.
-   `component_versions(component_id, version)` UNIQUE.
-   `vulnerabilities(vulnerability_source_id, external_id)` UNIQUE.
-   `vulnerability_aliases(alias_type, alias_value)`.
-   `scan_vulnerability_findings(organization_id, scan_component_id, vulnerability_id)`.
-   `document_chunks(document_version_id, ordinal)` UNIQUE.
-   índices para filtros tenant-scoped y estados de procesamiento.
-   índices de búsqueda textual PostgreSQL para campos/documentos que
    realmente lo necesiten.
-   índices vectoriales pgvector únicamente cuando se fije la dimensión
    y se midan consultas representativas.

No crear índices duplicados de PK/UNIQUE. No crear índices
indiscriminadamente. Documentar los índices compuestos según el patrón
de consulta y orden de columnas.

------------------------------------------------------------------------

## 6. Seguridad de datos

1.  RLS tenant-scoped.
2.  Rol de migración separado del rol runtime.
3.  Runtime role sin privilegios de DDL, superuser ni `BYPASSRLS`.
4.  Contexto tenant transaccional y no persistente entre conexiones.
5.  Secretos fuera de las tablas de negocio; guardar referencias a
    secret manager o valores cifrados con clave externa.
6.  Redacción de tokens/secretos en `scan_logs`,
    `background_job_attempts`, `webhook_events` y auditoría.
7.  Evitar payloads completos con código fuente o datos privados salvo
    necesidad.
8.  RLS y autorización de aplicación en tablas documentales y RAG.
9.  No permitir consultas del LLM a SQL libre.
10. Definir retención y borrado para documentos, logs, artefactos y
    datos de proveedor.

------------------------------------------------------------------------

## 7. Flyway: estructura de migraciones

Usar Flyway y migraciones SQL versionadas. No depender de
`ddl-auto=update` en entornos compartidos.

Estructura orientativa:

``` text
src/main/resources/db/migration/
  V001__create_identity_and_organizations.sql
  V002__create_rbac_and_memberships.sql
  V003__create_catalogs.sql
  V004__create_integrations_and_repositories.sql
  V005__create_background_jobs.sql
  V006__create_scans_and_artifacts.sql
  V007__create_components_and_dependency_graph.sql
  V008__create_vulnerability_intelligence.sql
  V009__create_scan_comparisons_and_changes.sql
  V010__create_change_impact.sql
  V011__create_documentation_center.sql
  V012__create_reporting_center.sql
  V013__create_notifications.sql
  V014__create_audit_and_retention.sql
  V015__enable_tenant_rls.sql
  V016__seed_reference_catalogs.sql
  # Fase documental:
  V101__enable_pgvector.sql
  V102__create_document_embeddings.sql
```

Adaptar el número y orden a las migraciones existentes. No renumerar
migraciones ya aplicadas. Las migraciones aplicadas son inmutables:
crear una nueva para corregirlas.

### 7.1 Reglas Flyway

-   Cada migración debe ser determinista.
-   No incluir datos de clientes reales.
-   Crear índices y restricciones de manera compatible con el tamaño
    esperado.
-   Si se usa `CREATE INDEX CONCURRENTLY`, comprobar el comportamiento
    transaccional de Flyway y configurarlo explícitamente.
-   Las semillas de catálogos deben ser idempotentes
    (`INSERT ... ON CONFLICT`) y usar códigos estables.
-   No insertar usuarios/contraseñas por defecto en producción.
-   Documentar cómo configurar extensiones en el entorno.
-   La migración pgvector no debe impedir ejecutar el core sin la
    extensión, si el producto todavía no usa RAG.

------------------------------------------------------------------------

## 8. Excalidraw: diagramas obligatorios

Genera **archivos editables nativos de Excalidraw**, no imágenes pegadas
ni solo diagramas Mermaid.

Ubicación:

``` text
docs/database/
  README.md
  data-dictionary.md
  normalization.md
  diagrams/
    01-system-data-context.excalidraw
    02-identity-access-integrations.excalidraw
    03-scans-sbom-components-graph.excalidraw
    04-vulnerability-intelligence.excalidraw
    05-documentation-rag-pgvector.excalidraw
    06-change-impact-reporting-notifications.excalidraw
    07-tenant-isolation-rls.excalidraw
```

### 8.1 Reglas visuales

-   Usar la estructura JSON válida de Excalidraw.
-   Los archivos deben abrirse y editarse en https://excalidraw.com.
-   Mantener elementos organizados, alineados y legibles; evitar un
    único diagrama gigante.
-   Usar cajas por entidad con nombre y atributos clave (PK/FK).
-   Marcar claramente PK, FK, UNIQUE y tablas puente.
-   Dibujar relaciones con conectores y cardinalidades `1`, `0..1`,
    `1..N`, `M..N`.
-   Diferenciar catálogos globales, tablas tenant-scoped y tablas
    puente.
-   Leyenda consistente en todos los diagramas.
-   Incluir título, propósito y alcance.
-   No omitir relaciones por simplificar.
-   No dibujar relaciones que no existan en el esquema SQL.
-   Los diagramas deben derivarse del esquema real implementado, no al
    revés.
-   Mantener `appState`, `elements` y `files` compatibles con el formato
    actual de Excalidraw. Asignar IDs únicos a cada elemento.
-   No generar un archivo con extensión `.excalidraw` que contenga
    Mermaid o texto plano.

### 8.2 Contenido de cada diagrama

**01 --- System Data Context** - Usuarios, organización/tenant,
frontend, backend, PostgreSQL. - GitHub App/API, cdxgen/CycloneDX,
OSV. - Worker de análisis. - Documentos temporales, extractor/OCR,
proveedor embeddings/LLM, pgvector. - Separar fuentes externas y
almacenamiento propio. - Mostrar originales privados, temporales que se
eliminan y retención/eliminación configurable por organización.

**02 --- Identity, Access & Integrations** - users, organizations,
memberships, roles, permissions, role_permissions, membership_roles. -
integrations, github_installations, repositories, repository_branches,
repository_commits, webhook_events. - Cardinalidades y tenant boundary.

**03 --- Scans, SBOM, Components & Graph** - background_jobs, scans,
scan_stages, scan_stage_executions, scan_artifacts, sbom_documents. -
components, component_identifiers, component_versions, scan_components,
dependency_edges. - Mostrar separación catálogo/versión/presencia en
scan y grafo snapshot-scoped.

**04 --- Vulnerability Intelligence** - vulnerabilities,
vulnerability_sources, vulnerability_aliases, vulnerability_references,
vulnerability_severities. - vulnerability_affected_packages,
vulnerability_affected_ranges, vulnerability_range_events,
vulnerability_fixed_versions. - scan_vulnerability_findings,
finding_evidence, scan_components. - Mostrar procedencia de OSV y
asociación hallazgo → aparición concreta en scan.

**05 --- Documentation & RAG** - documents, document_versions,
document_repositories, document_components,
document_component_versions. - document_chunks, embedding_models,
document_chunk_embeddings, document_processing_runs. -
assistant_conversations, assistant_messages,
assistant_message_evidences. - Mostrar flujo y filtro por
tenant/permisos.

**06 --- Change Impact, Reporting & Notifications** - impact_analyses,
impact_findings, impact_evidences, impact_recommendations,
impact_test_suggestions. - report_jobs, report_artifacts,
report_schedules. - notification_rules, notification_subscriptions,
notifications, notification_deliveries. - audit_events.

**07 --- Tenant Isolation & RLS** - Tablas globales vs tenant-scoped. -
organization_id y FKs compuestas. - contexto tenant por transacción. -
RLS policy. - rol runtime sin BYPASSRLS. - flujo de autorización de
aplicación.

### 8.3 Validación de diagramas

-   Abrir/validar JSON con parser.
-   Verificar `type`, `version`, `source`, `elements`, `appState`,
    `files`.
-   Verificar IDs únicos y referencias de conectores válidas.
-   Comprobar que cada entidad y relación importante del SQL aparezca en
    el diagrama correspondiente.
-   Si hay exportación a SVG/PNG disponible, generar una vista previa
    adicional, pero los `.excalidraw` editables son obligatorios.

------------------------------------------------------------------------

## 9. Diccionario de datos y normalización

Crear `docs/database/data-dictionary.md` con:

-   Descripción funcional de cada tabla.
-   Columna, tipo PostgreSQL, nullable, default, PK/FK/UNIQUE/CHECK.
-   Alcance global o tenant-scoped.
-   Relaciones entrantes/salientes.
-   Índices y consultas que optimizan.
-   Política de borrado/retención.
-   Módulo propietario (DDD bounded context).
-   Si es dato fuente, snapshot, evidencia o proyección.

Crear `docs/database/normalization.md` con:

1.  Criterios de 1NF, 2NF, 3NF, BCNF, 4NF y 5NF.
2.  Claves candidatas y dependencias funcionales relevantes.
3.  Descomposiciones de relaciones M:N y multivaluadas.
4.  Justificación de `jsonb` donde se utilice.
5.  Excepciones y proyecciones de lectura.
6.  Casos que podrían parecer redundantes pero son snapshots históricos
    intencionales.
7.  Cómo evitar anomalías de inserción, actualización y eliminación.

No basta con escribir "está en 5NF". Mostrar ejemplos concretos: -
usuario ↔ organización ↔ rol separados en memberships y
membership_roles. - rol ↔ permiso separado en role_permissions. -
paquete ↔ versión separado en components/component_versions. - versión ↔
scan separado en scan_components. - scan_component ↔ scan_component
separado por dependency_edges. - vulnerabilidad ↔ alias y vulnerabilidad
↔ referencias separados. - documento ↔ repositorio/componente/versión
separado en puentes. - chunk ↔ embedding_model separado en
document_chunk_embeddings. - notificación ↔ canal/destinatario separado
en deliveries. - recomendación ↔ evidencia separado en tabla puente.

------------------------------------------------------------------------

## 10. Reglas de modelado que no se deben violar

-   No crear una tabla `dependencies` que mezcle paquete, versión,
    repositorio y scan como una única entidad.
-   No guardar `repository_ids`, `roles`, `permissions`, `aliases`,
    `references`, `licenses`, `recipients` o `affected_versions` como
    arrays/CSV.
-   No usar un FK polimórfico genérico (`entity_type`, `entity_id`) como
    única integridad de relaciones críticas. Preferir puentes
    específicos o supertipo con FK real.
-   No usar embeddings para inventario o comparación de versiones.
-   No duplicar datos de organización dentro de cada objeto si pueden
    derivarse; `organization_id` repetido en tablas operativas está
    permitido y recomendado para aislamiento/consultas, siempre que FKs
    compuestas garanticen consistencia.
-   No almacenar severidad como texto libre en cada finding si existe
    catálogo y procedencia.
-   No asumir SemVer universal.
-   No asumir que una dependencia transitiva siempre puede detectarse.
-   No asumir que un paquete vulnerable está explotable en runtime.
-   No afirmar comunicación runtime entre microservicios desde el grafo
    de dependencias.
-   Conservar originales en almacenamiento privado y aplicar la política
    de retención/eliminación de cada organización.
-   No almacenar tokens, claves privadas ni secretos en claro.
-   No crear microservicios ni una base de grafos para este modelo
    inicial.
-   No agregar Redis, RabbitMQ o Kubernetes sin necesidad comprobada.
-   No activar pgvector en el núcleo si la fase RAG no está siendo
    implementada.

------------------------------------------------------------------------

## 11. Seeds y datos de referencia

Crear semillas mínimas e idempotentes para: - Ecosistemas de paquetes
iniciales y futuros como catálogo, sin afirmar que todos se implementan
en MVP. - Tipos de relación CycloneDX soportados. - Fuentes de
vulnerabilidades (OSV). - Herramienta cdxgen y formato CycloneDX. -
Severidades. - Estados de scan/job/document/impact/report. - Roles base
(OWNER, ADMIN, MEMBER, VIEWER) y permisos mínimos. - Canales de
notificación. - Tipos y formatos de reportes. - Tipos documentales.

No sembrar usuarios reales, credenciales, instalaciones GitHub ni
organizaciones de prueba en producción. Los fixtures de desarrollo deben
estar separados de los seeds de referencia.

------------------------------------------------------------------------

## 12. Pruebas requeridas

### 12.1 Migraciones y esquema

-   Levantar PostgreSQL limpio y ejecutar todas las migraciones desde
    cero.
-   Ejecutar migraciones en una base con versión previa, si existe.
-   Verificar constraints, índices, FKs y seeds.
-   Verificar que las migraciones no dependan de estado manual.
-   Validar instalación de pgvector solo en perfil/fase documental.

### 12.2 Normalización e integridad

-   No se pueden insertar memberships duplicadas.
-   No se pueden asignar roles/permisos duplicados.
-   No se puede crear una dependencia edge entre componentes de scans
    distintos.
-   No se puede enlazar un repository de otro tenant a una integration.
-   No se puede enlazar un scan de otro tenant a un repository.
-   No se puede asociar un finding con un scan_component de otro tenant.
-   No se puede crear una comparación entre repositorios distintos.
-   No se pueden insertar duplicados de
    aliases/references/embeddings/entregas.
-   Un documento puede asociarse a varios repositorios/componentes sin
    arrays.
-   Un chunk puede tener embeddings de diferentes modelos, cada uno con
    dimensión correcta.

### 12.3 RLS / aislamiento

Crear pruebas negativas con dos organizaciones A y B: - A no lee
registros de B. - A no actualiza ni elimina registros de B. - A no puede
insertar una FK hacia un registro de B. - Sin tenant context, el rol
runtime no accede a tablas protegidas. - Al devolver una conexión al
pool, el tenant anterior no queda activo. - Un usuario miembro de A pero
no de B no puede consultar B aunque conozca sus UUID.

### 12.4 Pruebas funcionales del modelo

Crear fixtures que demuestren: 1. Una organización tiene varios
usuarios. 2. Un usuario pertenece a varias organizaciones. 3. Una
organización conecta una GitHub App y varios repositorios. 4. Un
repositorio tiene múltiples scans para commits distintos. 5. Un scan
tiene múltiples componentes y relaciones. 6. Un mismo paquete tiene
múltiples versiones en diferentes repositorios. 7. Una versión puede
aparecer en múltiples scans. 8. Una vulnerabilidad puede tener varios
aliases, referencias, rangos y severidades. 9. Un hallazgo se asocia a
una aparición concreta del componente. 10. Dos scans se comparan y
producen componentes agregados/eliminados/actualizados. 11. Un documento
se asocia a repositorios y componentes diferentes. 12. Un documento
tiene versiones, chunks y embeddings. 13. Una consulta del asistente
conserva evidencias citables. 14. Un análisis de impacto tiene hallazgos
conocidos/potenciales/validados y evidencias. 15. Un reporte genera
artefactos rastreables. 16. Una notificación lógica puede tener varias
entregas. 17. Auditoría conserva eventos append-only.

------------------------------------------------------------------------

## 13. Integración con Java / Spring Boot

-   Usar Spring Data JPA/Hibernate para persistencia habitual.
-   Usar JDBC/SQL nativo cuando una consulta compleja, RLS, pgvector o
    análisis de grafo lo requiera.
-   Mantener entidades JPA sin lógica de negocio excesiva.
-   No mapear payloads JSONB a objetos de dominio como si fueran datos
    canónicos.
-   Respetar arquitectura hexagonal: dominio no depende de JPA, Flyway,
    PostgreSQL ni proveedores externos.
-   Definir repositorios/puertos por agregado o módulo.
-   No generar una entidad JPA por cada catálogo si el repositorio usa
    otra estrategia; documentar la decisión.
-   Evitar `FetchType.EAGER` indiscriminado.
-   Evitar N+1 y colecciones grandes.
-   No serializar entidades JPA directamente en endpoints.
-   Usar DTOs y consultas de lectura específicas.
-   Para tablas de alta cardinalidad (scan_components, dependency_edges,
    findings, chunks), evaluar batch inserts y paginación por cursor.
-   El contexto de tenant debe estar integrado con la transacción de
    Spring y la política RLS.

------------------------------------------------------------------------

## 14. Entregables finales obligatorios

Al terminar, entregar:

1.  Migraciones Flyway SQL completas.
2.  Configuración necesaria para PostgreSQL y extensiones opcionales.
3.  Entidades/modelos Java y repositorios solo donde corresponda a la
    arquitectura existente.
4.  Seeds idempotentes.
5.  Índices, FKs, UNIQUE, CHECK y políticas RLS.
6.  `docs/database/README.md`.
7.  `docs/database/data-dictionary.md`.
8.  `docs/database/normalization.md`.
9.  Los siete archivos `.excalidraw` indicados.
10. Exportaciones SVG/PNG si el entorno permite generarlas.
11. Tests de migración, integridad, multi-tenancy y RLS.
12. `docs/database/implementation-report.md` con:

-   tablas implementadas;
-   número de migraciones;
-   extensiones requeridas/opcionales;
-   decisiones tomadas;
-   decisiones pendientes;
-   resultados reales de pruebas;
-   limitaciones;
-   instrucciones para levantar la base de datos localmente.

------------------------------------------------------------------------

## 15. Criterios de aceptación

El trabajo se considera terminado únicamente cuando:

Estado del bloque 05: la cobertura de esquema, creación/actualización Flyway, separación de conceptos, FK/RLS, normalización, documentación y pruebas se verifican en [implementation-report.md](database/implementation-report.md). Los diagramas tienen validación estructural nativa y exportaciones inspeccionadas; su apertura interactiva en Excalidraw no se ejecutó. Esta lista se conserva como criterio de revisión, no como prueba de servicios o recorridos completos. El vector físico queda opcional según la ADR; no se afirma búsqueda vectorial implementada.

-   [ ] El esquema cubre los módulos de Dependency Intelligence y no
    solo el MVP.
-   [ ] La base se puede crear desde cero con Flyway.
-   [ ] Las tablas operativas tienen tenant boundary claro.
-   [ ] Las FKs compuestas evitan cruces de organización.
-   [ ] RLS está implementado y probado o, si el repositorio no permite
    implementarlo todavía, existe una ADR con el bloqueo exacto y una
    alternativa temporal segura.
-   [ ] Los paquetes, versiones y apariciones por scan están separados.
-   [ ] Las relaciones del grafo están vinculadas a un scan.
-   [ ] Los hallazgos de vulnerabilidad tienen fuente y evidencia.
-   [ ] El historial de scans y comparaciones es reproducible.
-   [ ] Change Impact conserva clasificación KNOWN/POTENTIAL/VALIDATED.
-   [ ] Documentación, chunks, embeddings y citas están modelados sin
    confundir originales privados retenidos con archivos temporales.
-   [ ] Reportes, notificaciones y auditoría tienen trazabilidad.
-   [ ] La documentación explica normalización hasta 5NF sin
    afirmaciones vacías.
-   [ ] Los diagramas son archivos editables válidos de Excalidraw.
-   [ ] Los diagramas coinciden con el esquema SQL implementado.
-   [ ] Las pruebas se ejecutaron y sus resultados están documentados.

------------------------------------------------------------------------

## 16. Decisiones que Codex no debe inventar

Los documentos fuente dejan abiertas algunas decisiones. No las cierres
arbitrariamente:

1.  Versión exacta de PostgreSQL.
2.  Versión de Java y Spring Boot.
3.  Cobertura adicional a Maven y npm, que ya están confirmados.
4.  Modelo, versión y dimensión exacta del embedding Ollama.
5.  Periodos concretos y excepciones de retención por organización.
6.  Configuración concreta de los métodos confirmados: correo/contraseña,
    Google y GitHub; vinculación de identidades y sesiones.
7.  Política de severidad/priorización de vulnerabilidades.
8.  Retención de logs, SBOM, scans y auditoría.
9.  Roles personalizados y excepciones; equipos y permisos por repositorio
    ya están confirmados para el primer piloto.
10. Si se persistirán todas las comparaciones o se calcularán bajo
    demanda.

Cuando una decisión afecte el DDL: - documentar la alternativa
recomendada; - crear una abstracción o migración aislada cuando sea
posible; - dejar una nota `TODO/DECISION REQUIRED`; - no inventar datos
o políticas; - mantener el core funcional sin activar servicios externos
opcionales.

------------------------------------------------------------------------

## 17. Resultado esperado de Codex

No respondas solamente con una propuesta. Modifica el repositorio y crea
los artefactos reales.

Al finalizar, presenta un resumen conciso con: - módulos y tablas
creadas; - archivos de migración; - diagramas Excalidraw creados; -
validaciones ejecutadas; - errores encontrados; - decisiones pendientes
que requieren al equipo.

**Prioridad:** integridad referencial, aislamiento multi-tenant,
trazabilidad histórica y normalización real. La estética del diagrama
nunca debe prevalecer sobre la fidelidad del modelo.
