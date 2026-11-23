### Bloque 72 — análisis y cobertura en una sola sección (23/11/2026)

Se unificó la información repetida del Dashboard en «Análisis recientes»: estado completo/parcial, totales por estado, versiones, elementos sin resolver, ecosistema y fecha por snapshot. Se retiró el panel separado de cobertura. La imagen del frontend se reconstruyó y se actualizó únicamente ese contenedor; HTTP 200 y chunk verificados.

### Bloque 71 — actualización del frontend Compose (23/11/2026)

Se reconstruyó y recreó solo el servicio frontend. El recurso de Dashboard servido en `localhost:8089` responde HTTP 200 y el chunk actual no contiene el grafo ni el antiguo panel de tendencia. Se conservaron el contador de versiones y la cobertura. Backend, base, scanner y Mailpit continuaron activos. La revisión visual del usuario aún falta.

### Bloque 70 — Dashboard (petición 6, 23/11/2026)

La vista usa `/api/account/workspace` como límite de autorización: OWNER ve sus repositorios y MEMBER únicamente los asignados. Presenta organizaciones (solo a cuentas no MEMBER), repositorios accesibles, versiones únicas por repositorio/ecosistema/paquete/versión, repositorios con snapshots, recuentos por cuatro categorías OSV, avisos priorizados y estado de cobertura completa/parcial. El conteo «sin avisos conocidos» requiere una evaluación OSV exitosa. Las consultas por repositorio se limitan a cuatro en paralelo.

Las acciones abren el repositorio seleccionado directamente en Inventario; los hallazgos llevan al inventario de su repositorio. El Dashboard quedó disponible para MEMBER y conserva sus restricciones de datos. Los estados vacíos distinguen falta de repositorios, de análisis y de resultados OSV; se eliminó el panel de tendencia sin historial. El Dashboard distingue Maven y npm y muestra los elementos sin resolver del scanner.

**Estado:** implementación frontend terminada. Queda pendiente la revisión interactiva del usuario con sesión OWNER y MEMBER, especialmente permisos de lectura por repositorio, temas claro/oscuro, idioma y vista móvil. No se alteraron API, esquema ni snapshots.

### Bloque 66 — Inventario (petición 5, 22/11/2026)

- **Catálogo del espacio (RF-041–046).** Agrupa por ecosistema, namespace y nombre exacto. Cuenta versiones distintas y repositorios accesibles. Usa el último análisis completado por ecosistema y repositorio. En cuentas con varios espacios se selecciona una organización y, si hace falta, un repositorio.
- **Búsqueda y prioridad.** Busca nombre, coordenada, versión, archivo y repositorio. Combina filtros de versión exacta, ecosistema, ámbito, tipo, estado OSV y severidad. Los filtros se aplican a la misma aparición antes de agrupar. La severidad representa avisos observados. Los resultados desactualizados, incompletos o pendientes de consulta aparecen como datos por revisar.
- **Alcance y rendimiento.** El catálogo y las apariciones se consultan sobre PostgreSQL y se paginan. La pantalla pide 25 resultados por página. La API filtra por los repositorios que la cuenta puede leer antes de entrar a la transacción de la organización. La vista por repositorio conserva las acciones de análisis y OSV. Agregar o consultar un filtro del catálogo usa lecturas guardadas.


### Bloque 65 — carga de 300 MiB y consistencia del análisis (22/11/2026)

- **Carga local:** hasta 300 MiB de contenido (314.572.800 bytes) y 50.000 archivos. Nginx y Spring/Tomcat admiten un cuerpo multipart de 512 MiB para incluir nombres y metadatos. El ZIP remoto de GitHub también admite hasta 300 MiB. Los lectores del scanner admiten 50.000 archivos y hasta 512 MiB descomprimidos. Los límites por POM, lockfile y SBOM siguen protegiendo cada parser.
- **Una fuente por análisis:** Inventario usa `POST /dependency-analysis`. Application abre una fuente autorizada que ambos lectores leen de forma independiente. Se conserva el mismo SHA-256 y el mismo commit de GitHub en los dos resultados. Los endpoints históricos siguen disponibles. Una fuente sin el manifiesto de un ecosistema deja un resultado explícito de ausencia. Si falla un ecosistema se conserva su último inventario válido y se muestra la fecha.

### Bloque 64 — auditoría previa de scanner y OSV (petición 4, 22/11/2026)

Esta sección registra la implementación que se auditó antes de los cambios del bloque 65.

#### Flujo actual

1. **Analizar repositorio**, en Inventario, solicita en paralelo `POST /maven-analysis` y `POST /npm-analysis` dentro del repositorio autorizado. Cada endpoint abre la fuente GitHub fijada a un commit o el ZIP local identificado por SHA-256. Los dos endpoints abren la fuente de forma independiente. `POST /scans` tiene su propio contrato de solicitud y el botón actual usa los endpoints por ecosistema.
2. **Deteccion y persistencia.** cdxgen genera CycloneDX en un contenedor aislado con la fuente en solo lectura. Los parsers concilian componentes del SBOM con POM y lockfiles; persisten nombre, version, archivo, alcance, procedencia y clasificacion directa/transitiva/unknown basada en evidencia.
3. **Evaluación.** Tras obtener un scan, Inventario solicita `POST /vulnerability-analysis`. **Consultar OSV** permite repetir solo la evaluación. El backend toma el último scan completado por ecosistema, incluidos los nombres antiguos de scanner, y consulta las versiones guardadas. Envía `Maven` con `groupId:artifactId` y `npm` con el nombre completo, incluido `@scope/paquete`. La respuesta se asocia por posición en cada lote y se recorren los `next_page_token` por consulta. Se guarda la evidencia por scan y ocurrencia, incluido el ID OSV si faltan alias CVE o severidad. [Contrato oficial de OSV por lotes](https://google.github.io/osv.dev/post-v1-querybatch/)

#### Cobertura por ecosistema

| | Qué puede confirmar hoy | Límite vigente |
| --- | --- | --- |
| Maven | Componentes exactos que aporta CycloneDX. Si cdxgen detecta POM pero devuelve cero componentes, la lectura estática recupera declaraciones directas con versiones explícitas o resolubles mediante propiedades, padres y BOM presentes en el ZIP. | La cobertura es parcial. El modo seguro mantiene deshabilitados Maven, plugins y scripts. Padres o BOM remotos y transitivas sin evidencia quedan fuera de la cobertura garantizada. |
| npm | Versiones resueltas del `package-lock.json`, conservando paquetes con scope y clasificando el alcance directo/transitivo cuando la fuente lo permite. Si cdxgen detecta el lockfile pero devuelve cero componentes, el lector de respaldo usa lockfiles v2/v3 y presenta el resultado recuperado como parcial. | Sin lockfile compatible no se puede confirmar el árbol instalado. Un rango de `package.json` por sí solo no permite evaluar una versión exacta. El respaldo no concilia componentes omitidos por un SBOM que ya trae algún resultado. |

El adaptador cdxgen admite hasta 50.000 entradas, 512 MiB descomprimidos y un SBOM de 25 MiB. Ambos lectores de respaldo tienen un límite de 5.000 entradas y 120 MiB descomprimidos. Maven limita además el respaldo a 64 POM de hasta 1 MiB cada uno. Una carpeta admitida por la carga puede superar el límite del lector de respaldo. El detalle de cobertura debe identificar el límite que se alcanzó.

#### Interpretación en Inventario

| Resultado | Qué indica |
| --- | --- |
| Versión o vínculo sin confirmar | El scanner informa referencias pendientes. Una versión sin resolver queda fuera de la consulta. Una versión exacta con vínculo `UNKNOWN` sí puede evaluarse. |
| `MATCHED` | OSV devolvió al menos un aviso asociado a la versión exacta. La severidad desconocida se muestra como tal. |
| `NO_KNOWN_ADVISORIES` | OSV devolvió una consulta completa sin avisos para esa versión a la hora registrada. La cobertura de OSV depende de sus fuentes y de cuándo se actualizan. |
| `INCOMPLETE` / `UNAVAILABLE` | La evaluación quedó incompleta o el proveedor falló. Se preserva el inventario y se muestran los avisos con evidencia disponible. |
| `STALE` / `NOT_ASSESSED` | Existe evidencia anterior desactualizada o falta evaluar la versión para ese scan. La vigencia local se configura y vale 24 horas por defecto en Compose. |

#### Criterios de aceptación pendientes

1. **Fuente y asociación.** Fijar una única fuente reproducible por acción de análisis y verificar que Inventario muestre OSV para el mismo `scanId`. Hoy Maven y npm pueden tomar cada uno el commit vigente al abrir la fuente. La UI agrupa OSV por coordenadas y versión sin comprobar `scanId`, y un error del nuevo OSV puede dejar visible la evaluación anterior. Mostrar la procedencia de resultados previos cuando se conserven. Las versiones corregidas deben corresponder a la entrada `affected` del paquete y ecosistema seleccionados. Hoy la extracción genérica reúne los `fixed` de todas las entradas del aviso.
2. **Recuperación.** Diferenciar manifiesto ausente, proyecto compatible sin dependencias, versión sin resolver y fallo del scanner. Cuando falle un reanálisis, mantener visible el último inventario válido con su fecha y su estado. Hoy el backend conserva scans previos, pero el botón puede ocultar el inventario anterior del ecosistema que falló hasta recargar. Evaluar los resultados incompletos de cdxgen y los límites distintos del respaldo con fixtures controlados, sin ejecutar código de repositorios.
3. **Recorrido autenticado.** Reanalizar un Maven multimódulo y un npm con paquete scoped y transitiva, a través de Inventario. Comparar fuente, manifiestos, componentes, clasificacion directa/transitiva, respuesta OSV, persistencia y pantalla. Usar respuestas controladas para verificar la versión afectada frente a la corregida para el mismo aviso, paginación, ausencia de alias/severidad, fallo y reintento independiente de OSV. Incluir un repositorio real por ecosistema y registrar los resultados para el scan exacto. Mantener el adaptador OSV y limitar cualquier cambio a fallos comprobados.

**Estado de la petición 4.** Requisitos y límites consolidados mediante revisión estática. Los bloques anteriores registran pruebas focales, un smoke test del contenedor y evidencia de PostgreSQL para OMEGA y Bot de WhatsApp. El recorrido autenticado de extremo a extremo con la versión posterior al bloque 59 sigue pendiente. La siguiente petición define la experiencia de Inventario. [Guía del scanner](../infra/scanner/README.md) · [Estados de OSV](OSV_VULNERABILIDADES.md)

### Bloque 63 — fuentes GitHub y locales (petición 3, 22/11/2026)

#### Importación y procedencia

| | GitHub | Carpeta local |
| --- | --- | --- |
| Alta | El propietario o administrador de la organización vincula una instalación de GitHub App con la misma sesión y OAuth/PKCE. GitHub concede acceso a los repositorios seleccionados y después el responsable elige cuáles agregar al espacio. La API vuelve a verificar la selección y admite hasta 500 por importación. | El responsable selecciona una carpeta desde el navegador. Se aceptan hasta 50.000 archivos y 300 MiB de contenido. Se validan las rutas y se excluyen carpetas generadas, `.git` y archivos de credenciales conocidos. Cada subida crea un repositorio local con ID propio. |
| Fuente de análisis y descarga | Se verifica el acceso de la instalación, se toma el commit actual de la rama predeterminada y se descarga un ZIP por SHA de commit. El ZIP temporal tiene un límite de 300 MiB. El resultado registra el commit y el SHA-256 del archivo. | Se lee el ZIP privado persistente de la organización y se verifica su SHA-256. La fuente representa los archivos cargados en ese momento y cada análisis referencia ese contenido por hash. |
| Actualización | Abrir la gestión de repositorios o pulsar «Sincronizar acceso con GitHub» actualiza los metadatos y la disponibilidad. El flujo de importación registra metadatos. Solicitar el análisis es una acción separada. | Los cambios posteriores en el equipo requieren otra carga. La interfaz no ofrece reemplazar el ZIP conservando el ID del repositorio. |

El máximo de la carga local mide el contenido antes de empaquetarlo. El de GitHub mide el ZIP recibido. El permiso de la GitHub App para este flujo es `Contents: Read` y `Metadata: Read`. La lectura para análisis usa la instalación vinculada al espacio. El inicio de sesión con GitHub se gestiona en el flujo de identidad.

#### Permisos, retiro y retención

- **Permisos.** Conectar, importar y sincronizar exige `repository.manage` a nivel de organización. Descargar requiere `repository.read` y solicitar análisis requiere `scan.create` sobre el repositorio. Los miembros y lectores ven los repositorios habilitados a los que pueden acceder por rol y permiso directo o de equipo. La API permite retirar un repositorio concreto con `repository.manage` sobre ese recurso. La interfaz actual muestra esa acción desde el panel de gestión de la organización y oculta dicho panel a cuentas `MEMBER`, incluso si administran un repositorio asignado.
- **Retirar GitHub del espacio.** Se deshabilita el registro local y se conserva el historial de análisis. El repositorio remoto permanece en GitHub. Si GitHub retira un repositorio de la selección autorizada, la siguiente sincronización lo deshabilita. El adaptador interpreta un 404 al solicitar el token de instalación como revocación y marca la integración `REVOKED` junto con la deshabilitación de sus repositorios vinculados. Los accesos de lectura y análisis dejan de estar disponibles cuando el repositorio queda deshabilitado.
- **Volver a agregar GitHub.** La sincronización no reactiva altas previas. Una importación explícita reutiliza la fila por organización, proveedor e ID externo, lo que conserva el historial. Los permisos previos pueden volver a surtir efecto porque siguen asociados a esa fila. Queda pendiente mostrar qué accesos se recuperarán para que el responsable pueda revisarlos antes de reactivar el repositorio.
- **Retirar una carpeta local.** La operación deshabilita el registro y borra el ZIP privado y sus metadatos de fuente. Los resultados anteriores se conservan en la base. La copia permanece en el volumen privado mientras está activa. La política de plazos, copias de seguridad y borrado exigible se define en la petición 15. Para GitHub, el ZIP se elimina al cerrar el recurso temporal y recuperar código histórico depende de que el proveedor mantenga el commit disponible y autorizado.

#### Qué falta para aceptar el ciclo completo

1. Implementar la desconexión explícita desde la plataforma (RF-014). Debe bloquear las nuevas lecturas y análisis de la instalación y conservar historial. La interfaz debe distinguir retirar un repositorio del espacio, desconectar la integración del espacio y desinstalar la App en GitHub.
2. Implementar recepción de webhooks firmados y procesamiento idempotente (RF-012–013), con sincronización manual como respaldo. Verificar que una revocación real deshabilite solo los repositorios afectados y que un fallo temporal del proveedor deje el último estado conocido disponible para diagnóstico, con una indicación de que no se pudo verificar. Separar la revisión del permiso del disparo de análisis ante eventos configurados.
3. Validar el recorrido con una cuenta con acceso por equipo y otra sin acceso, tanto para GitHub como para una carpeta local. Verificar descarga, inicio de análisis, retiro, reconexión y revisión de permisos previos. Incluir revisión en móvil u otro dispositivo con una URL accesible y validación de una carpeta cerca del límite de 50.000 archivos.

**Estado de la petición 3.** Requisitos y diferencias entre orígenes consolidados. El código de la aplicación se revisó de forma estática. La evidencia previa en Dinamita acredita importación de dos repositorios GitHub, una carpeta local y descarga ZIP. La automatización, desconexión y aceptación del ciclo de revocación quedan por implementar o validar según los puntos anteriores. La siguiente petición documenta y valida el contrato entre scanner y OSV.

### Bloque 62 — auditoría de identidad, roles y configuración (petición 2, 22/11/2026)

La matriz siguiente se verificó contra los casos de uso de Identity/Organization, controladores, consultas de autorización, componentes Angular y migraciones V5/V14/V16/V17/V18. No representa una propuesta nueva de roles.

#### Identidad y admisión

- `platform_administrators` identifica administradores globales por separado del rol organizacional. La administración de plataforma puede aprobar/rechazar responsables, habilitar/deshabilitar y retirar cuentas, gestionar recursos globales y consultar auditoría. Las acciones administrativas piden motivo; se protege al último administrador activo.
- `users.account_type` hoy solo distingue `OWNER` y `MEMBER`; `admission_status` es `PENDING_REVIEW`, `APPROVED` o `REJECTED`. El registro abierto crea una cuenta OWNER pendiente; el administrador recibe los enlaces aprobar/rechazar. Aprobar desde el correo habilita la cuenta y confirma el correo. El registro invitado crea MEMBER aprobado, pero debe utilizar el correo de invitación verificado.
- La admisión de un propietario en la plataforma es distinta de aceptar una invitación de organización. La invitación exige correo coincidente y verificado, aceptación y aprobación manual posterior del propietario que invitó.
- Google OIDC y GitHub OAuth/PKCE soportan login y vinculación explícita; no fusionan cuentas por correo coincidente. Vincular exige correo verificado y sesión reciente; no se permite retirar el último método de acceso. **Estado del entorno:** según las instrucciones operativas vigentes, las credenciales sociales están desactivadas en Compose; la implementación de proveedor existe, pero el consentimiento real requiere activación/configuración y prueba. OAuth de identidad no sustituye GitHub App para repositorios.

#### Roles y alcance efectivo

| Nivel | Capacidades observadas en el código | Límite |
| --- | --- | --- |
| Administrador de plataforma | Acceso global a la vista de administración y acciones registradas con motivo. | No es un rol dentro de cada organización; se controla en tabla aparte. |
| Propietario de organización (`OWNER`) | Todos los permisos organizacionales/repositorios; crea organización, invita, revoca y aprueba invitaciones; puede asignar roles OWNER/ADMIN; conserva el último propietario. | Solo la persona propietaria que emitió la invitación puede aprobar/revocar esa invitación. |
| Administrador de organización (`ADMIN`) | Permisos amplios de operación de la organización y repositorios; gestiona miembros, equipos y permisos. | No puede asignar OWNER/ADMIN. La API actual restringe invitar, revocar y aprobar invitaciones al OWNER aunque el rol ADMIN tenga `organization.manage`; documentar este límite como comportamiento vigente. |
| Miembro (`MEMBER`) | Puede recibir permisos sobre repositorios concretos por equipo o directamente; el rol define el máximo de capacidades disponibles. `repository.manage` se incorporó para que un MEMBER pueda administrar un repositorio que tenga asignado. | No tiene permisos organizacionales globales por ese grant. El acceso efectivo requiere cuenta/correo/admisión/membresía/repositorio activos y un grant para esa operación/repo. |
| Lector (`VIEWER`) | Lectura del repositorio, scans/documentos e informes según matriz del rol. | No puede crear análisis, gestionar repositorios ni modificar accesos; grants de equipo no elevan capacidades que el rol no concede. |

**Regla de autorización verificada:** cuenta activa, correo verificado, admisión aprobada, organización/membresía activas y repositorio habilitado; para MEMBER/VIEWER además se requiere permiso de rol y grant coincidente del equipo o permiso directo en ese repositorio. `repository_scope` limita a equipos completos o a repositorios explícitamente asignados. Los OWNER/ADMIN organizacionales operan sobre el ámbito organizacional. Los códigos de permiso se presentan en UI como textos legibles.

**Gestión de invitaciones/permisos:** las invitaciones solo asignan MEMBER o VIEWER, seleccionan equipo y repositorio, expiran a 72 horas por defecto, y se guardan mediante hash del token. Tras aceptar la persona, el estado queda `AWAITING_OWNER`; el OWNER invitante aprueba en la plataforma. Para una persona que administre solo un repositorio, hoy se usa MEMBER + permiso `repository.manage` limitado a ese repositorio, no el rol organizacional ADMIN. El OWNER puede asignar administración por equipo o directa. En la tabla, una cuenta MEMBER con `repository.manage` en un repositorio se identificará como «Administradora del repositorio A», conservando el rol MEMBER y sin permisos organizacionales.

#### Configuración y experiencia de miembros

- Perfil: nombre editable con validación personal; correo no editable desde Configuración.
- Apariencia/idioma personal: override guardado en el navegador; apariencia/idioma del espacio: persistidos para la organización, modificables por OWNER/ADMIN y heredados por miembros.
- Seguridad: solicitar cambio de contraseña por enlace privado; métodos Google/GitHub vinculables/desvinculables con reautenticación y protección del último método.
- La navegación actualmente excluye Dashboard y Organización a cuentas `MEMBER`. El usuario ya decidió que **los miembros deben tener Dashboard con datos limitados a sus repositorios asignados**; la navegación todavía no implementa ese cambio. Organización continúa siendo una pantalla de gestión.

#### Estado de la petición 2

La matriz actual y sus límites están comprobados/documentados. Se confirma el Dashboard de MEMBER limitado a sus repositorios y la etiqueta «Administradora del repositorio A» para el permiso `repository.manage` en ese recurso. La petición 2 queda cerrada como requisitos; la implementación de UI queda en la fase de pulido. No se ha cambiado código ni esquema; se continúa con la petición 3.

### Bloque 61 — alcance del producto y plan de 15 peticiones (22 de noviembre de 2026)

#### Paso 1 — objetivo y alcance vigente: cerrado

**Propósito:** Dependency Intelligence ayuda a los equipos a saber qué dependencias usan sus repositorios, qué riesgos conocidos se asocian a sus versiones y qué impacto tendría cambiarlas, mostrando evidencia trazable para tomar decisiones.

**Usuarios prioritarios:** responsables de repositorios/líderes técnicos y seguridad/DevSecOps, con la misma prioridad. Administración de plataforma, propietarios, administradores de organización, miembros y lectores tienen funciones y permisos distintos.

**Meta:** construir el producto completo definido en este maestro, entregándolo por fases. Primero debe funcionar localmente; después se prepara el despliegue en servidor. El anterior plazo de dos semanas/50 horas era para una demo local y quedó sustituido: no limita el alcance ni es fecha de aceptación vigente.

**Primer piloto acordado:** autenticación y organización; repositorios GitHub y carpetas locales; Java/Maven y JavaScript/npm; equipos y permisos por repositorio; análisis con inventario y OSV separados; y evaluación aislada de cambios con resultados revisables y propuesta de PR que requiere aprobación. La IA/documentación, reportes y demás capacidades de la visión completa se implementan por fases según el plan, no se declaran terminadas por existir sus tablas o mockups.

**Criterio de producto listo:** recorridos de extremo a extremo con repositorios y datos reales; autorización efectiva por organización/equipo/repositorio; resultados con procedencia y límites explícitos; fallos recuperables que no borren inventario válido; despliegue reproducible; y respaldo/restauración y aceptación de cada módulo. “Pantalla visible” no significa capacidad terminada.

**Límites confirmados:** Gemini será el LLM de la plataforma; Ollama generará embeddings. Codex no se integra al producto sin petición explícita. Las notificaciones comienzan dentro de la aplicación; el correo de notificaciones se deja para después. Modelo, versión, dimensión de embedding y cuotas se decidirán con evidencia al diseñar la fase de IA.

#### Plan de cierre del contexto maestro — 14 peticiones funcionales

Estas peticiones sirven para concretar alcance, decisiones y criterios de aceptación del maestro; no representan quince commits ni prometen que el producto entero quede implementado en quince turnos.

1. **Objetivo y alcance:** misión, usuarios y definición de piloto/producto completo. **Cerrado en el bloque 61.**
2. **Identidad, roles y ajustes:** acceso, aprobación, matriz de permisos, organización, perfil, preferencias y métodos de acceso. **Requisitos cerrados en el bloque 62.**
3. **Repositorios:** flujo GitHub App frente a carpeta local, importación, sincronización/revocación, asignaciones y retención de fuente. **Requisitos cerrados en el bloque 63.**
4. **Scanner y OSV:** detección, versiones/coordenadas, cobertura, estados de consulta, hallazgos y manejo de fallos Maven/npm. **Requisitos cerrados en el bloque 64.**
5. **Inventario:** alcance global o por repositorio, catálogo, búsqueda, filtros, evidencia y estados. **Implementado en el bloque 66.**
6. **Dashboard:** indicadores y acciones útiles, fuentes de datos, estados vacíos y enlaces a los módulos; sin cifras simuladas. **Implementado en el bloque 70; revisión interactiva pendiente.**
7. **Pantalla Vulnerabilidades:** agrupación, severidad, filtros, priorización, estados de revisión, versiones corregidas y referencias.
8. **Pantalla Análisis:** inicio y seguimiento, commit, progreso, estados, errores, reintentos e historial de ejecuciones.
9. **Centro de documentación:** tipos de archivo, carga, extracción, asociación a repositorios/componentes, versiones, permisos, retención y eliminación.
10. **Arquitectura de IA documental, explícita:** Ollama y selección verificable de modelo/dimensión para embeddings; pgvector, migración, esquema, índices y aislamiento por organización; recuperación (incluida búsqueda híbrida si se decide); RAG con fragmentos y citas; orquestador; chat asistente con Gemini; permisos en recuperación, respuesta sin evidencia, fallos y evaluación de calidad. No activar pgvector en el núcleo antes de decidir e implementar esta fase documental.
11. **Historial, comparación y recomendaciones:** snapshots por commit, cambios de paquetes/versiones y recomendación sustentada en fuentes.
12. **Reportes:** destinatarios, informes técnicos/ejecutivos, filtros, vista previa, formatos exportables y programación.
13. **Impacto y validación de cambios:** ADD/UPDATE/REMOVE/REPLACE, escenarios, pruebas aisladas, evidencia, estados de confianza y propuesta de PR con aprobación humana.
14. **Notificaciones y requisitos transversales:** eventos in-app y preferencias; seguridad/privacidad, accesibilidad, rendimiento, retención, observabilidad, HTTPS/secretos, servidor, backup/restauración y criterios de aceptación final.


### Bloque 60 — mapa actual del producto y las cuatro pantallas pendientes (22 de noviembre de 2026)

Este apartado refleja el código actual, contrastado con `frontend/src/app/core/navigation.ts`, `application-shell.html` y los componentes de pantalla. Si una capacidad se reutiliza dentro de otra vista, se distingue de tener una pantalla completa propia.

| Opción del sidebar | Qué ofrece hoy en el código | Estado comprobado |
| --- | --- | --- |
| **Administración** | Solo la ve una cuenta administradora de plataforma. Tiene métricas de propietarios/solicitudes, miembros, repositorios, equipos, organizaciones e invitaciones; pestañas para solicitudes, cuentas, repositorios, equipos, organizaciones, membresías, invitaciones y auditoría. Cambios sensibles piden motivo y confirmación. | Implementada. La vista trabaja con listas acotadas (hasta 200 filas; auditoría hasta 100) y consulta `/api/platform/overview` y `/api/platform/actions`. |
| **Mis repositorios** | Para propietarios/admins: seleccionar organización, instalar/vincular GitHub App, sincronizar acceso, importar repositorios seleccionados, subir carpeta local (hasta 300 MiB/50.000 archivos), descargar y retirar del espacio. Para miembros: lista solo repositorios habilitados para su acceso. | Implementada. GitHub App y carga local usan permisos por organización. El funcionamiento de OAuth depende de credenciales habilitadas en el entorno activo. |
| **Dashboard** | Resumen de repositorios accesibles, snapshots Maven/npm, cobertura del scanner y avisos OSV por severidad; las cuentas MEMBER solo ven sus repositorios asignados. | Implementado en bloque 70 con consultas acotadas, navegación directa a Inventario y estados explícitos. Falta revisión interactiva con las cuentas del usuario. |
| **Inventario** | Ejecuta Maven y npm para el repositorio, muestra paquetes/versiones por archivo/ámbito, búsqueda, filtros, procedencia/hash/commit y detalles de avisos OSV; permite consultar OSV aparte y abrir evidencia de código para los casos soportados. | Implementada para Maven/npm con cobertura explícita. El último ajuste añade recuperación Maven desde padres/propiedades/BOM locales cuando el SBOM viene vacío. La revalidación autenticada de OMEGA tras ese cambio sigue pendiente. |
| **Vulnerabilidades** | El propósito será centralizar hallazgos, paquetes/versiones afectadas, severidad/fuente y repositorios afectados. Actualmente OSV aparece en Dashboard e Inventario. | **Pantalla pendiente.** La navegación la dirige al estado general “En preparación”; no existe un componente de página propio ni filtros globales de hallazgos. |
| **Análisis** | Aquí correspondería iniciar/seguir ejecuciones, revisar commit/estado/progreso, fallos, reintentos e historial/comparación. | **Pantalla pendiente.** Hoy el botón “Analizar repositorio” está dentro de Inventario; esta ruta del sidebar no tiene pantalla de análisis ni orquestación visible. El historial/comparación de snapshots no está terminado. |
| **Documentación** | Debería gestionar documentos privados, versiones, relación con repositorios/componentes, extracción, indexación y búsqueda con citas; la visión aprobada usa Gemini para IA y Ollama para embeddings. | **Pantalla pendiente.** No hay componente ni flujo de documentos/IA en la UI o API del producto verificado en esta revisión. No integrar Codex sin petición explícita. |
| **Reportes** | Debería producir exportaciones técnicas/ejecutivas, con filtros, preview y salida trazable; el maestro considera PDF/CSV/JSON/SBOM. | **Pantalla pendiente.** El sidebar muestra el estado genérico de preparación; no se encontró página, catálogo ni endpoint de generación/exportación. |
| **Organización** | Seleccionar/crear espacio; pestañas de miembros, invitaciones, equipos y permisos. Buscar/filtrar/paginar miembros; invitar al equipo/repositorio; aceptar por correo y esperar aprobación del propietario; ajustar roles, miembros de equipos y permisos por equipo o directos al repositorio. | Implementada en frontend/backend. Las invitaciones están limitadas a las últimas 100 y los miembros se muestran paginados de 10 en 10. |
| **Configuración** | Perfil personal (correo de solo lectura), preferencias personales y del espacio (tema/idioma), recuperación de contraseña y métodos de acceso conectados/desvinculados. | Implementada. Tema/idioma del espacio requieren propietario/admin; tema/idioma personales son overrides locales. Los proveedores sociales requieren credenciales activas en el entorno para completar OAuth. |

**Pantallas pendientes confirmadas por el usuario:** Vulnerabilidades, Análisis, Documentación y Reportes. No empezar a pulirlas hasta que el usuario priorice su orden. Antes de ese bloque, el usuario pidió terminar este contexto maestro y después trabajar visualmente esas cuatro.

**Observación histórica del bloque 60:** el Dashboard conservaba paneles sin datos y la navegación ocultaba la vista para MEMBER. El bloque 70 corrigió ambos puntos. Los textos de preparación de otras pantallas siguen describiendo módulos que no están implementados como vistas completas; Inventory ya ofrece análisis e inventario y el Dashboard integra las evaluaciones OSV.

**Acceso fuera del sidebar:** registro con correo/contraseña, verificación y aprobación administrativa, login, recuperación y página de espera; vincular identidad social es distinto de autorizar GitHub App para repositorios. Administración solo aparece para administradores de plataforma; desde el bloque 70, MEMBER también accede a Dashboard limitado al workspace autorizado.

**No se hizo cambio de código ni pruebas en este bloque;** fue una revisión estática del shell, navegación, componentes Angular y controladores existentes. La referencia histórica de pantallas propuestas sigue abajo, pero para el estado vigente prevalece esta tabla y `docs/PENDIENTES.md`.

### Bloque 59 — resolver local Maven y ordenar Inventario (22 de noviembre de 2026)

La instancia real había mostrado que cdxgen detectó un POM, pero no generó componentes. El fallback Maven ahora construye modelos acotados solo con POM incluidos en el ZIP autorizado: lee POM multimódulo, hereda propiedades y `dependencyManagement` del padre local y aplica versiones de BOM importados que también estén presentes en el repositorio. No ejecuta Maven, plugins ni código del proyecto y no descarga fuentes externas. Los paquetes que obtiene se conservan como declaraciones directas; no se inventan transitivas. Las referencias que no se puedan verificar mantienen cobertura parcial y ya no se presentan como errores del inventario: su explicación está plegada en el estado de cobertura del ecosistema y disponible bajo demanda.

Inventario reorganiza análisis, resultados y catálogo: resumen legible por ecosistema, diferencia entre dependencias con versión y elementos por confirmar, buscador, filtros rápidos de seguridad y filtros avanzados plegables, conteo visible y etiquetas explicativas. Se ajustó el mensaje posterior al análisis para que sea neutral y la clasificación desconocida no parezca una alerta crítica. El resultado de cdxgen y OSV conserva estados separados.

Verificación focal: `CdxgenMavenManifestReaderTest` 3/3, `CdxgenNpmLockfileReaderTest` 1/1 y `CdxgenScannerAdapterTest` 2/2; build Angular completado. No se repitió el análisis autenticado de OMEGA, así que la mejora sobre ese repositorio se debe confirmar desde Inventario. Maven remoto, transitivas no aportadas por el SBOM y perfiles complejos siguen fuera de la cobertura confirmada. Ver [bitácora](BITACORA_TRABAJO.md), [scanner](../infra/scanner/README.md) y [pendientes](PENDIENTES.md).

# CONTEXTO MAESTRO — DEPENDENCY INTELLIGENCE
## Documento de continuidad para migrar de cuenta de ChatGPT

### Bloque 58 — corregir pérdida del inventario Maven y validar OSV en la instancia (22 de noviembre de 2026)

La revisión de PostgreSQL en Compose encontró el defecto real: `OMEGA-main` tenía un scan `cdxgen-maven` completado con un POM detectado, pero cero componentes guardados. En los scans recientes npm, `cdxgen` también emitió SBOM vacío; el lector existente recuperó 270 componentes de OMEGA y 291 de Bot de WhatsApp desde los lockfiles. OSV sí está complementando los scans npm recientes: hay 270 evaluaciones para OMEGA y 291 para Bot; Bot tiene 11 evaluaciones `MATCHED` con IDs GHSA enlazados a paquete/versión, y OMEGA no tuvo avisos conocidos. Los datos existentes muestran que OSV conserva evaluaciones asociadas al mismo `scan_id`.

Se corrigió el adaptador Maven: al encontrar POM pero SBOM vacío, vuelve a leer el ZIP de forma limitada y recupera declaraciones con versión exacta usando el lector seguro existente. Nunca inventa versión ni afirmar cobertura transitiva sin evidencia. El inventario queda `PARTIAL`, explicando límites de herencia/BOM y transitivas; si nada se puede resolver, deja el manifiesto y los elementos pendientes visibles. Se añadió regresión OSV para conservar un aviso `MATCHED` con ID OSV aunque falten alias/severidad y asociarlo a la ocurrencia exacta del scan.

Verificación focal: 10 pruebas de scanner, Maven, npm y OSV pasaron; incluye paginación, error del proveedor, paquete npm scoped, fallback Maven vacío y persistencia de resultado positivo/negativo. El runtime se validó contra scans reales guardados en PostgreSQL. El backend actualizado ya está en Compose; falta volver a analizar OMEGA desde Inventario y comprobar sus resultados en la UI. Maven transitivo seguro sigue siendo cobertura parcial. Ver [bitácora](BITACORA_TRABAJO.md), [scanner](../infra/scanner/README.md) y [pendientes](PENDIENTES.md).
### Bloque 57 — validación de scanner y OSV (22 de noviembre de 2026)

La revisión confirma que scanner y OSV son etapas independientes y complementarias: cdxgen genera CycloneDX y persiste componentes y clasificaciones; OSV lee el último scan por ecosistema y guarda la evaluación asociada, sin volver a escanear ni borrar inventario si falla. Los reportes focales existentes del 20/11 muestran `CdxgenScannerAdapterTest` 2/2, `CdxgenNpmLockfileReaderTest` 1/1 y `OsvRestAdapterTest` 2/2. No se pudieron repetir en este entorno: el primer intento no tenía acceso a Maven Central; el reintento inició descargas autorizadas pero se detuvo antes de llegar a ejecutar tests. La app Compose respondió `/health` con `UP`; el endpoint de health protegido devolvió 401 sin sesión.

Consulta directa, de solo lectura, a la API oficial OSV el 22/11: `org.apache.logging.log4j:log4j-core` 2.14.1 reportó `CVE-2021-44228`; 2.17.1 no reportó ese CVE, aunque devolvió otros avisos. `lodash` 4.17.20 reportó `CVE-2021-23337`; 4.17.21 no reportó ese CVE, aunque devolvió otros avisos. Esto confirma respuesta y búsqueda por coordenadas/versión en el proveedor, no prueba que la integración de la instancia haya procesado esas respuestas.

**Pendiente para cerrar la validación:** ejecutar un análisis autenticado desde Inventario sobre repositorios reales Maven y npm, comprobar componentes/cobertura guardados, ejecutar o repetir **Consultar OSV**, y confirmar que sus estados/hallazgos se muestran en Inventario. La automatización de navegador no estuvo disponible en esta sesión (Node runtime no encontrado), y no se usaron credenciales ni se modificaron repositorios/datos. Maven transitivo seguro sigue fuera de cobertura garantizada; npm puede quedar parcial cuando cdxgen omite componentes y se activa el lector de lockfile.

### Bloque 56 — Inventario y OSV complementarios (20 de noviembre de 2026)

Se corrigió un hueco entre nombres de scanners: OSV ahora selecciona el último scan por ecosistema entre `maven-manifest`/`npm-lockfile` históricos y `cdxgen-maven`/`cdxgen-npm` actuales, deduplicando por ecosistema. La carga de OSV ya no silencia fallos y se puede repetir desde **Consultar OSV** sin volver a escanear. Si cdxgen produce un SBOM npm vacío pese a detectar `package-lock.json`, el parser acotado recupera el inventario como cobertura parcial. El diagnóstico de la base activa encontró tres snapshots npm vacíos preexistentes; requieren reanálisis por el usuario y se mantienen intactos. La implementación y límites están en [bitácora](BITACORA_TRABAJO.md) y [pendientes](PENDIENTES.md).





### Bloque 50 — muestra npm importable para probar Inventario (20 de noviembre de 2026)

Se preparó `samples/npm-axios-demo` como proyecto local mínimo con `package.json` y `package-lock.json` v3. Declara Axios 1.7.9 directamente y ocho paquetes instalados transitivos, con rutas de dependencia representativas; no tiene `node_modules`, scripts ni código ejecutable. La prueba de la plataforma es importar esa carpeta en Mis repositorios y ejecutar el análisis npm desde Inventario. El fixture se validó como JSON y estructura; aún no se subió ni escaneó en la instancia. Ver [bitácora](BITACORA_TRABAJO.md) y [pendientes](PENDIENTES.md).
### Bloque 49 — diagnóstico de Inventario npm (20 de noviembre de 2026)

En la instancia de Compose, Inventario solo ofrece `Formula1Simulator · local`; allí Maven muestra seis componentes y npm figura «Aún sin analizar». El repositorio de ejemplo Axios todavía no está importado en ese espacio, por lo que la ausencia de componentes npm no demuestra un fallo del parser. La inspección del código confirma soporte de `package-lock.json` v2/v3 y existe una prueba focal con lockfile sintético; falta el recorrido con un repositorio npm real. Siguiente: importar Axios (`axios/axios`, `v1.x`), seleccionar ese repo, ejecutar el análisis y revisar componentes persistidos. No se modificaron análisis ni datos en este diagnóstico. Ver [bitácora](BITACORA_TRABAJO.md) y [pendientes](PENDIENTES.md).




### Bloque 46 — ampliación del límite de archivos locales (20 de noviembre de 2026)

El usuario pidió conservar el tamaño máximo y ampliar la cantidad de archivos por carpeta. Se mantienen **100 MiB de contenido total** y **50.000 archivos** como nuevo límite. El navegador, el caso de uso Application, el controlador y el CHECK de `local_repository_sources.file_count` se alinean; V24 cambia únicamente esa restricción. Spring/Tomcat aceptan 100.001 partes/parámetros (nombre + ruta + archivo por entrada) y 200 MiB de cuerpo multipart para los metadatos/rutas sin elevar el contenido de archivos permitido; Nginx queda en el mismo máximo de cuerpo. Pruebas focales cubren el borde de 50.000 y el rechazo de 50.001; el test existente conserva el rechazo por encima de 100 MiB. La descarga de ZIP GitHub mantiene su límite separado. Ver [bitácora](BITACORA_TRABAJO.md), [arquitectura](ARQUITECTURA_BACKEND.md), [base de datos](CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md) y [pendientes](PENDIENTES.md).



### Bloque 44 — evidencia de código y ciclo de vida del repositorio (20 de noviembre de 2026)

Inventario permite abrir cada aviso y ver su significado, acción sugerida, versiones corregidas publicadas por OSV, referencia oficial y detalle técnico. Para los dos advisories Jackson conocidos, la API revisa de forma acotada los archivos Java/Kotlin/Groovy/Scala del ZIP actual y devuelve archivo, línea, fragmento y motivo de revisión; la respuesta incluye proveedor, commit si existe y SHA-256. Es una pista estática y no determina explotabilidad. La procedencia del manifiesto queda expandida y legible, y la pestaña usa el SVG de marca.

El propietario puede retirar repositorios con `repository.manage`: los de GitHub se deshabilitan localmente y conservan historial; los locales también eliminan ZIP privado y metadatos fuente. «Sincronizar acceso con GitHub» vuelve a consultar los repositorios autorizados; los retirados se deshabilitan y una instalación que ya no autoriza el token se marca REVOKED. La comprobación ocurre al abrir o sincronizar la vista, no por webhook ni en segundo plano. Sin migraciones ni dependencias externas. Build y servicio local validados; revisión visual y con advisories reales pendiente. Detalle en [arquitectura](ARQUITECTURA_BACKEND.md), [diseño](DISENO_FRONTEND.md), [stack](STACK_TECNOLOGICO_DEPENDENCY_INTELLIGENCE.md), [pendientes](PENDIENTES.md) y [bitácora](BITACORA_TRABAJO.md).

### Bloque 43 — vulnerabilidades legibles en Dashboard e Inventario

El Dashboard consulta los snapshots Maven, npm y OSV de cada repositorio accesible. Resume versiones por cuatro categorías semánticas: crítico, riesgo alto, advertencia y sin avisos conocidos. Una versión solo puede aparecer como «sin avisos conocidos» con respuesta OSV exitosa; consultas parciales, vencidas o fallidas se informan como pendientes. Se muestran avisos con repositorio, paquete, versión e hipervínculo a la fuente OSV. Inventario añade explicación sencilla y acción sugerida para GHSA-wjgm-6hv5-3cvf y GHSA-wv8q-qhhj-9h54, las versiones corregidas publicadas y secciones desplegables para la descripción técnica y referencias. Tipografía y espacio aumentan para facilitar lectura. Sin cambios backend, DB ni dependencias. Build/deploy y estado de `localhost:8089` documentados en [bitácora](BITACORA_TRABAJO.md); pendientes actualizados en [PENDIENTES](PENDIENTES.md).

### Bloque 42 - Consulta de vulnerabilidades OSV por versión

La pantalla Inventario presenta Maven y npm en una sola vista por repositorio: un botón ejecuta ambos analizadores y después evalúa sus versiones exactas con OSV. Los paquetes se agrupan por ecosistema, namespace y nombre; cada identidad muestra versiones exactas y ocurrencias por archivo/ámbito. Incluye búsqueda, filtros, conteos, procedencia de fuente/hash/commit y estados accesibles. El bloque 42 agrega avisos y evidencia con estado fresco/parcial/no disponible; detalles y límites en [OSV_VULNERABILIDADES](OSV_VULNERABILIDADES.md).


**Fecha de elaboración:** 12 de noviembre de 2026  
**Propósito:** conservar el contexto, decisiones, ideas, requisitos, arquitectura, pantallas, investigación pendiente y próximos pasos conversados sobre el proyecto Dependency Intelligence, para continuar el trabajo desde otra cuenta de ChatGPT.

> Este documento consolida el contexto disponible de las conversaciones. Distingue lo que ya se propuso de lo que está decidido. Las propuestas no deben tratarse como decisiones aprobadas si el usuario todavía no las confirmó. Tampoco se deben interpretar ejemplos, datos de mockups o estimaciones como resultados reales.

## Decisiones vigentes y estado de continuidad - 19 de noviembre de 2026

### Bloque 34 — Fuentes local/GitHub y datos de la instancia

La GitHub App de `CondezJuan8` está instalada en GitHub (ID `183677764`), con `Contents: Read` y dos repositorios autorizados. El propietario completó OAuth/PKCE en Dinamita: PostgreSQL registra una instalación activa y dos repositorios GitHub habilitados. También registra un repositorio LOCAL importado desde una carpeta propia (446 archivos, ZIP privado de 30.110.249 bytes). El usuario confirmó la visualización y descarga de las tres fuentes. La vista distingue LOCAL/GitHub y sirve ZIP autorizado con SHA-256; GitHub incluye commit fijo. Análisis aislado Maven/npm, validación/extracción segura del ZIP e inventario siguen pendientes. La instancia conserva solo dos cuentas activas (administrador y `condez.juanp@gmail.com`); Mamitapuebla quedó suspendida con historial preservado mediante identidad histórica anonimizada. Ver [GitHub/repositorios](GITHUB_REPOSITORIOS.md), [pendientes](PENDIENTES.md) y [bitácora](BITACORA_TRABAJO.md).

Bloque 35: la captura mostró una pestaña con código anterior y el flujo de instalación no llegó al callback; la base seguía en cero. **Conectar GitHub** detecta ahora una instalación única y dirige a OAuth directamente, sin repetir la instalación. El frontend recarga páginas restauradas del historial y Nginx evita cachear `index.html`. Falta confirmar el consentimiento real y la importación en la sesión del usuario; no se declara terminada la conexión hasta ver filas en `github_installations` y `repositories`.

Bloque 36: el usuario completó el consentimiento. PostgreSQL confirma una instalación GitHub activa asociada a Dinamita y dos repositorios habilitados; la descarga ZIP funcionó. La API GitHub permitió consultar commit y redirigió el ZIP fijado por SHA a `codeload.github.com`. La importación local excluye archivos de credenciales conocidos tanto en navegador como en Application. La corrección puntual de la segunda fila GitHub se documenta en [GitHub/repositorios](GITHUB_REPOSITORIOS.md) y [bitácora](BITACORA_TRABAJO.md).

Bloque 37: la carga real de una carpeta falló primero por el límite Nginx de 20 MiB, luego por el máximo predeterminado de partes multipart de Tomcat y finalmente por una ruta de almacenamiento que no apuntaba al volumen privado. Se alinearon los tres límites/ruta con el contrato de 100 MiB y 2.000 archivos. El POST real devolvió HTTP 200; el usuario confirmó listado y descarga. PostgreSQL registra una fuente LOCAL con 446 archivos y ZIP de 30.110.249 bytes. El análisis Maven/npm sigue pendiente.

Bloque 38: API `POST/GET /api/organizations/{organizationId}/repositories/{repositoryId}/maven-analysis` y vista inicial Inventario disponibles en `localhost:8089`. V20 conserva origen/hash del ZIP y conteo de manifiestos/dependencias sin resolver. El analizador lee POMs con XML endurecido desde el ZIP sin extraer ni ejecutar código, registra solo dependencias directas con versión explícita y muestra SHA, commit GitHub cuando corresponde, scope y ruta del manifiesto. Build Docker correcto, backend/frontend activos, Flyway V20 y `/health` UP. No se ha hecho aún una prueba autenticada desde la sesión del usuario. Herencia/BOM/transitivas/npm/OSV y worker aislado siguen pendientes.

### Bloque 32 — Bootstrap seguro del primer administrador

Application incorpora el caso de uso y los puertos; Infrastructure ofrece un comando de consola solo bajo el perfil `platform-admin-bootstrap`, expuesto como servicio Compose opt-in sin HTTP. El operador ingresa nombre/correo y contraseña confirmada en terminal; esta no se pasa por argumentos ni entorno. El alta se permite una sola vez con cero usuarios, organizaciones y administradores, verificado bajo bloqueo transaccional; crea una cuenta OWNER activa, aprobada y con correo verificado, hash PBKDF2, asignación de administrador global y auditoría. Pruebas focalizadas: dos unitarias y una integración PostgreSQL temporal correctas; `docker compose config --quiet` válido. En el bloque 33 se verificó el arranque de consola hasta la primera pregunta. Compose ya cuenta con administrador y organizaciones. Procedimiento: [ACCESO_LOCAL](ACCESO_LOCAL.md); estado y dependencias: [PENDIENTES](PENDIENTES.md).

### Bloque 31 — Configuracion local de GitHub App

La App `dependency-intelligence-platform` de `CondezJuan8` esta creada. Slug, App ID, Client ID, Client Secret y llave privada se configuraron localmente en `.env`, ignorado por Git; nunca registrar secretos en documentos. Una consulta JWT RS256 de solo lectura a `GET /app` devolvio HTTP 200 con la identidad esperada. La instalación se confirmó posteriormente en el bloque 34; faltan consentimiento OAuth/PKCE e importación real desde la plataforma. Ver [GITHUB_REPOSITORIOS](GITHUB_REPOSITORIOS.md) y [bitacora](BITACORA_TRABAJO.md).

### Bloque 29 — GitHub App y origen local

La GitHub App de `CondezJuan8` aún no existe. El CTA de repositorios abre el formulario oficial de creación; una App creada debe configurarse en `.env` antes de habilitar la instalación en repositorios. Implementada la carga de carpetas locales a un ZIP privado del servidor, repositorio `local` en la organización y listado para administrar permisos existentes. V19 en PostgreSQL: 127 tablas, 90 con FORCE RLS y 225 FK. La carga no ejecuta análisis; checkout/lectura de fuentes, jobs aislados Maven/npm y resultados siguen pendientes. Ver [GitHub/repo](GITHUB_REPOSITORIOS.md), [bitácora](BITACORA_TRABAJO.md) y [pendientes](PENDIENTES.md).

### Actualización de integración GitHub

El usuario autorizó iniciar la integración de repositorios GitHub. El bloque actual entrega instalación de GitHub App, verificación OAuth/PKCE de que la identidad autenticada tiene acceso a la instalación, selección/importación de metadatos y vista responsive móvil/escritorio. Configuración/aceptación real con `CondezJuan8`, checkout de código, escaneo y despliegue accesible desde otros dispositivos siguen pendientes. Guía: [GITHUB_REPOSITORIOS](GITHUB_REPOSITORIOS.md).

Bloques 23-26: repository.manage scoped y permisos directos (V16/V17), ajustes compartidos de organizacion (V18). PostgreSQL local en V18: 126 tablas de negocio, 89 FORCE RLS, 223 FK. Build frontend/backend correctos; el IT de V18 queda pendiente por fallo DNS al resolver Maven Central.

Las secciones numeradas posteriores conservan el contexto histórico. Las decisiones de este apartado, confirmadas por el usuario, prevalecen sobre propuestas pendientes o límites de entregas anteriores. Referencias prioritarias: este contexto, `ARQUITECTURA_BACKEND.md`, `CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md` y `STACK_TECNOLOGICO_DEPENDENCY_INTELLIGENCE.md`.

Instrucción posterior al bloque 09: pendientes consolidados en [PENDIENTES.md](PENDIENTES.md), para implementarlos y validarlos juntos cuando el usuario lo indique. El orden propuesto más abajo queda como referencia; no inicia automáticamente el siguiente bloque.

Decisión vigente del flujo de admisión (18 de noviembre de 2026): cada administrador activo con correo verificado recibe un mensaje con enlaces Aprobar y Rechazar. El primer clic aplica la decisión inmediatamente sin login ni segundo clic. Los enlaces son de un solo uso, vencen (24 horas por defecto) y una decisión consume las otras de la solicitud. Aprobar confirma el correo y habilita el acceso; rechazar deja la cuenta sin acceso. La pantalla «Esperando confirmación» persiste al cerrar la página y el usuario puede volver a consultar el estado. GET no decide; sin embargo, un escáner que ejecute JavaScript y haga POST podría consumir el enlace, riesgo aceptado por la decisión de primer clic. Ver `VERIFICACION_Y_RECUPERACION.md`.

Bloque 20 (18 de noviembre de 2026): alta directa pendiente envía correo individual a administradores activos con correo confirmado, con enlaces inmediatos de aprobar/rechazar. Tokens aleatorios persistidos como hashes, dirigidos al admin y a la cuenta pendiente, de un solo uso y con vencimiento. La decisión queda auditada bajo la identidad del administrador destinatario. V15 extiende `user_access_tokens` sin añadir tablas. Smoke HTTP cubrió aprobar, rechazar, repetición inválida y confirmación del correo. La pantalla de espera no cambia.

Bloque 13, 16 de noviembre de 2026: antes de GitHub, el usuario priorizó rediseñar frontend y proporcionó imágenes de login, dashboard y organización; registro delegado. Entregados acceso de dos paneles, navegación lateral/topbar, dashboard con estados honestos y organización con tabla, filtros y pestañas. APIs y esquema permanecen iguales. Entorno local activo para revisión, build correcto y validación visual automática bloqueada por herramienta. Detalle y recorrido en [DISENO_FRONTEND](DISENO_FRONTEND.md). Siguiente acción: recoger observaciones del usuario, sin iniciar otra integración.

### Decisiones cerradas

- Objetivo: todo el producto listo para usar, por fases; primero local y después servidor. Disponibilidad declarada: 25 horas semanales. El plan de demo/50 horas ya no limita alcance o aceptación; no hay nueva fecha total comprometida.
- Nueva base implementada mediante V4–V17 con 125 tablas de negocio, 88 con RLS forzado y 221 FK, medido desde el catálogo PostgreSQL local. V14 agrega asignaciones directas y administración de plataforma; V15 admite decisiones desde correo; V17 permite permisos directos por miembro/repositorio. Catálogo, diccionario y siete diagramas regenerados y validados. Pgvector sigue opcional.
- Acceso: correo/contraseña, Google y GitHub, con recuperación/verificación y vinculación segura de identidades. Login GitHub y acceso a repositorios mediante instalación/autorización son funciones distintas.
- Ecosistemas: Java/Maven y JavaScript/npm.
- Paleta visual confirmada, navy #101827, azul #2563EB, cian #06B6D4, slate #1E293B, fondo #F8FAFC, blanco #FFFFFF y semánticos verde/amarillo/naranja/rojo. Enlaces sobre blanco en azul para contraste. Distribución, tokens y variantes legibles en `DISENO_FRONTEND.md`.
- El usuario quiere probar la plataforma y revisar colores/errores durante el desarrollo. Avisar al cerrar hitos interactivos. Primero acceso y colores al levantar el entorno local, después GitHub → repositorio → análisis → inventario con datos reales. Indicar alcance y comprobaciones pendientes en cada aviso.
- Primer piloto con equipos y permisos por repositorio; pruebas de cambios aisladas y propuesta de PR sujeta a aprobación. Roles personalizados no se consideran confirmados.
- IA de la plataforma: Gemini. Embeddings: Ollama. No integrar ni planificar Codex como proveedor del producto hasta nueva instrucción explícita del usuario. Su uso como herramienta de desarrollo no equivale a una integración del producto.
- Notificaciones iniciales dentro de la aplicación; correo de notificaciones después. El correo de recuperación/verificación de cuentas se trata por separado.
- Política documental delegada: conservar originales en almacenamiento privado para descarga autorizada y reindexación; retención/eliminación configurable por organización y borrado de temporales. Sustituye la propuesta histórica de originales solo en el equipo del cliente. Su implementación está pendiente.
- Modelos concretos, dimensión de embeddings, límites de proveedores y periodos operativos se verifican/configuran al implementar, sin inventarlos. No se promete gratuidad de Gemini ni disponibilidad ilimitada.

### Matriz de funciones por completar

Estado a partir del repositorio, no del número de tablas o carpetas. Una función se acepta con backend, acceso autorizado, interfaz aplicable y resultado verificable; una tabla creada no equivale a una función terminada.

| Área / requisitos del maestro | Estado real | Qué falta para aceptarla | Dependencias |
| --- | --- | --- | --- |
| Identidad, organizaciones y permisos — RF-001–006 | Acceso local, organización, autorización, recuperación/verificación SMTP y proveedores externos con fixture. Bloque 11 entrega invitaciones SMTP, aceptación y gestión HTTP/UI de membresías, roles, equipos y permisos por repositorio | Configurar clientes y probar consentimiento real Google/GitHub; entrega durable, paginación de historiales grandes y revisión visual conjunta | Credenciales externas y operación de correo/sesiones |
| GitHub — RF-007–014 | Vinculación OAuth/PKCE, importación de repositorios seleccionados y descarga por commit confirmadas en Dinamita. Sincronización bajo demanda y detección de revocación implementadas. | Desconexión explícita, prueba de revocación real, recorrido desde otro dispositivo y webhooks firmados e idempotentes. Ver bloque 63. | GitHub App, credenciales y jobs |
| Repositorios y análisis — RF-015–021 | Registro/consulta y solicitud de scan por API con autorización de rol/equipo y RLS | Interfaz de repositorios/scans, worker, estados, errores, historial y commit real | Jobs, GitHub, scanner |
| SBOM/componentes — RF-022–032 | cdxgen aislado integrado en Inventario para Maven y npm. Persiste componentes y su clasificacion directa/transitiva respaldada por evidencia, junto con el estado de cobertura. | Validar el recorrido autenticado y distinguir fallos y cobertura incompleta. Maven transitivo sigue parcial. Ver bloque 64. | Fuente autorizada, scanner, artefactos |
| Vulnerabilidades — RF-033–040 | OSV integrado por versión exacta. Guarda evaluaciones con evidencia por scan y ocurrencia. | Verificar asociación en UI por scan, mostrar rangos y versiones corregidas del paquete correcto y validar fallo y reintento. Ver bloque 64. | OSV, snapshots de scanner, Inventario |
| Historial/comparación — RF-053–059 | Snapshots importados con procedencia | Consulta por commit y comparación añadidos/eliminados/actualizados del mismo repositorio | Nuevo modelo, inventario |
| Recomendaciones — RF-060–065 | Pendiente | Versiones candidatas/corregidas, cambios documentados, riesgos, evidencia y pruebas sugeridas | OSV, releases/documentos, comparación |
| Alertas — RF-066–070 | Pendiente | Eventos, deduplicación, suscripción, estado y entrega in-app; correo después | Jobs, hallazgos/cambios |
| SBOM/artefactos — RF-071–074 | Solo hash/procedencia; original no retenido | Conservar/consultar/exportar SBOM y comparar snapshots; formatos y trazabilidad | Almacenamiento privado, permisos |
| Documentos/IA — RF-075–080 | Pendiente | Originales privados, versiones, asociaciones, extracción/OCR, búsqueda híbrida, Ollama, Gemini y respuestas citadas con permisos | Jobs, almacenamiento, modelos/configuración, pgvector aislado |
| Reportes — RF-086–090 | Pendiente | Informes técnicos/ejecutivos, PDF/CSV/JSON/SBOM, artefactos trazables y programación posterior | Inventario, hallazgos, permisos, jobs |
| Auditoría y requisitos no funcionales | Health, FK/RLS/roles SQL, append-only, evento al crear organización, CSRF, hash de contraseña y límite local de intentos | Ampliar auditoría a casos de uso, logs/métricas, límites coordinados, retención, backup/restauración y servidor | Transversal a cada fase |
| Interfaz — pantallas del apartado 6 | Bloque 13: acceso/registro, navegación lateral/topbar, dashboard con organizaciones reales/estados vacíos y organización con pestañas/tabla/filtros/diálogos | Aceptación visual en escritorio/móvil, correcciones del usuario y pantallas con datos de los módulos aún pendientes | Contratos y funciones por módulo |

### Estado de la base y del código existente

- Tres módulos Maven Domain/Application/Infrastructure; Domain y Application sin Spring/JPA.
- Flyway V1–V17 con 125 tablas, 88 con RLS forzado y 221 FK. Diccionario y siete diagramas validados desde el catálogo aplicado. V12 permite consultar organizaciones propias; V13 soporta revocación de sesiones, V14 administración de plataforma, V15 decisiones de admisión y V17 permisos directos por miembro/repositorio.
- F03 y F04 funcionan como parser e importación persistente acotados, con validación previa, SHA-256, procedencia y rollback. Falta entrada HTTP e inventario visual. Importaciones manuales no inventan commit/scanner ni clasificaciones no respaldadas.
- Registro/login/logout, creación/listado de organizaciones y restauración de sesión disponibles en Angular. APIs de repositorios/scans autorizadas con rol y equipo. Las sesiones viven en memoria del backend y vencen por inactividad. Docker y el recorrido visual completo siguen pendientes. Ver `ACCESO_LOCAL.md`.
- Verificación y recuperación operativas con tokens aleatorios, hash, caducidad y uso único. Mailpit 1.33.1 recibe los correos locales. Cola de recuperación acotada en memoria, con reenvío manual ante fallo/reinicio. SMTP y PostgreSQL requieren reenvío si una aceptación de correo precede a un commit fallido. Configuración y pruebas en `VERIFICACION_Y_RECUPERACION.md`.
- Bloque 09: Google OIDC y GitHub OAuth con PKCE, state/nonce, sesión propia y vínculo explícito. No se fusionan correos coincidentes; se protege el último método disponible y se revocan otras sesiones al vincular. Sin secretos configurados, ambos proveedores están deshabilitados. Contratos, activación y límites en [IDENTIDAD_GOOGLE_GITHUB](IDENTIDAD_GOOGLE_GITHUB.md). No implica acceso a repositorios GitHub.
- Bloque 11 autorizado por el usuario: invitaciones con token hash/SMTP, aceptación por correo coincidente verificado, gestión de roles/estados, equipos y permisos por repositorio. Conserva el último OWNER y restringe a ADMIN ante OWNER/ADMIN. Pruebas con PostgreSQL/Mailpit y build correctos; revisión visual pendiente. [Recorrido y contratos](INVITACIONES_EQUIPOS_PERMISOS.md). No cambia el esquema V1–V13.
- La transición separa paquete/versión/presencia y normaliza los módulos. Casos de uso con tenant transaccional y credenciales runtime/migración separadas. Autorización consulta cuenta, organización, membresía, rol y equipo activos en cada petición. Los workers y servicios restantes se desarrollan por la matriz.

### Orden de implementación propuesto

1. Base y contratos hasta V13, con artefactos derivados del catálogo aplicado. Mantener migraciones incrementales y verificar modelo/dimensión al activar vectores.
2. Revisar con el usuario invitaciones y gestión de equipos/permisos entregadas en bloque 11. Acceso local en bloque 06, correo en bloque 07 y proveedores/vinculación en bloque 09. Configurar clientes reales Google/GitHub y verificar consentimiento cuando estén disponibles; completar entrega durable y operación para servidor. Elegir el siguiente bloque con el usuario según PENDIENTES.
4. Comparación, recomendaciones e impacto; pruebas aisladas y propuesta de PR con aprobación para cerrar el primer piloto.
5. Documentos privados, extracción/búsqueda, embeddings Ollama y asistente Gemini; reportes y alertas in-app según sus dependencias.
6. Completar visión empresarial, automatizaciones/reportes programados y operación en servidor. Correo de notificaciones después de in-app. Codex queda fuera hasta nueva instrucción.

Estos pasos no autorizan contrataciones, publicación ni PR sobre repositorios externos en este bloque. No se presume una fecha del piloto: equipos/permisos y pruebas aisladas/PR amplían el trabajo y exigen reestimación. Mantener bitácora por bloque, pruebas relevantes y commits coherentes con emoji.

### Criterio de producto listo para usar

Arranque reproducible; usuarios y organización operativos; permisos efectivos; recorridos reales sin fixtures presentados como producción; trabajos recuperables; evidencias/citas y estados de error; configuración/secretos externos al código; backup y restauración comprobados; documentación y pruebas de aceptación de los módulos entregados. Las pantallas o tablas vacías no satisfacen este criterio.

---

# 1. Resumen ejecutivo del proyecto

**Dependency Intelligence** es una propuesta de plataforma SaaS B2B para empresas de software. Su objetivo es ayudar a equipos de ingeniería, arquitectura, DevSecOps y seguridad a entender qué dependencias utilizan sus aplicaciones, qué riesgos presentan, dónde se propagan y qué podría ocurrir si se actualizan, eliminan o sustituyen.

La visión no es crear simplemente otro escáner de vulnerabilidades. La propuesta busca combinar:

1. **Dependency Inventory:** inventario centralizado de dependencias directas y transitivas de los repositorios.
2. **Vulnerability Intelligence:** identificación y seguimiento de vulnerabilidades asociadas a paquetes y versiones.
4. **Change Impact Analysis:** análisis de consecuencias potenciales de modificar una dependencia.
5. **Documentation Intelligence:** documentación técnica centralizada y conectada con repositorios, componentes y versiones.
6. **AI Assistant:** asistente contextual que consulta los datos estructurados y la documentación de la organización, responde con evidencia y explica riesgos.
7. **Reporting Center:** reportes técnicos y ejecutivos, exportaciones y seguimiento.
8. **Enterprise capabilities:** integraciones, auditoría, permisos, multi-tenancy y capacidades avanzadas para organizaciones.

Posicionamiento conceptual propuesto:

**“Understand. Predict. Protect.”**

La plataforma pretende que una organización pueda entender su ecosistema de dependencias, anticipar el impacto de cambios técnicos y gestionar riesgos con evidencia.

## 1.1 Problema que busca resolver

En empresas con varios repositorios, servicios y equipos, la información de dependencias suele estar fragmentada. Es difícil responder rápidamente preguntas como:

- ¿Qué librerías y versiones utiliza realmente la organización?
- ¿En cuáles repositorios aparece una dependencia específica?
- ¿Una dependencia es directa o transitiva?
- ¿Qué vulnerabilidades afectan a qué proyectos?
- ¿Qué versión está corregida y qué evidencia lo respalda?
- Si actualizamos o eliminamos un paquete, ¿qué otros componentes podrían verse afectados?
- ¿Qué servicios, proyectos o documentos dependen de esa versión?
- ¿Qué cambios son urgentes y cuáles pueden planificarse?
- ¿Qué documentación interna explica cómo se utiliza una librería?
- ¿Cómo se puede presentar el estado de dependencias y riesgos a dirección, arquitectura o seguridad?

La propuesta consiste en convertir datos dispersos de repositorios, SBOM, vulnerabilidades, versiones y documentación en una visión navegable y trazable.

## 1.2 Usuarios potenciales

Perfiles que podrían utilizar o comprar el producto:

- CTO / VP of Engineering.
- Engineering Manager.
- Software Architect.
- DevSecOps Engineer.
- Application Security Engineer.
- Product Security / Vulnerability Management.
- Líderes de equipos de desarrollo.
- Equipos de cumplimiento y auditoría técnica.

El usuario final puede ser un desarrollador o arquitecto; el comprador podría ser un responsable de ingeniería, seguridad o tecnología. Esta hipótesis comercial todavía debe validarse.

## 1.3 Mercado objetivo

La intención conversada es construir algo que pueda ofrecerse a empresas ya establecidas, con especial interés en el mercado de Estados Unidos. No se ha validado todavía un segmento exacto, tamaño de empresa, industria ni comprador económico.

---

# 2. Origen y contexto de decisión

Dependency Intelligence es uno de los dos finalistas de una evaluación de oportunidades SaaS B2B:

- **Enterprise Commission Management**
- **Dependency Intelligence**

El usuario solicitó extraer la información de ambos finalistas para compartirla en otro chat. Después, el trabajo se concentró en Dependency Intelligence.

La motivación es construir un producto tecnológicamente interesante, no una aplicación CRUD sencilla. El proyecto debe tener una propuesta empresarial clara, funcionalidades defendibles y potencial de venta a compañías de Estados Unidos.

El usuario también preguntó por tecnologías como bases de datos vectoriales y embeddings. La orientación aplicada al proyecto fue:

- Los datos operativos y estructurados —repositorios, paquetes, versiones, vulnerabilidades, relaciones, análisis, usuarios— deben almacenarse principalmente en una base relacional.
- Una base vectorial o `pgvector` puede aportar valor para buscar y consultar documentación técnica mediante embeddings.
- No se debe usar una base vectorial como sustituto de consultas relacionales para inventarios, versiones o vulnerabilidades exactas.
- La IA no debe ser la fuente de verdad para determinar si una dependencia es vulnerable o si una actualización es segura.

---

# 3. Alcance funcional y módulos del producto

## 3.1 Dependency Inventory

Inventario consolidado de los componentes detectados en los repositorios conectados.

Debe permitir:

- Ver todas las dependencias detectadas.
- Distinguir dependencias directas y transitivas.
- Identificar nombre, ecosistema, versión y paquete.
- Saber en qué repositorios aparece cada dependencia.
- Consultar las distintas versiones de un mismo paquete presentes en la organización.
- Buscar y filtrar por nombre, ecosistema, versión, repositorio y otros atributos.
- Navegar desde una dependencia hacia sus repositorios y relaciones.
- Consultar el historial de presencia y cambios de dependencias.
- Conservar la procedencia de los datos y el commit analizado.

## 3.2 Vulnerability Intelligence

Identificación de vulnerabilidades que afecten a las dependencias detectadas.

Debe permitir:

- Consultar vulnerabilidades por paquete, ecosistema y versión.
- Asociar vulnerabilidades con dependencias y repositorios.
- Mostrar identificadores como OSV ID y, cuando exista, CVE.
- Mostrar severidad cuando la fuente la proporcione.
- Mostrar versiones afectadas y versiones corregidas cuando estén disponibles.
- Evitar duplicados al normalizar resultados de diferentes fuentes.
- Mostrar el estado de actualización de los datos.
- Identificar en qué repositorios aparece una dependencia vulnerable.
- Mantener trazabilidad de la fuente de cada hallazgo.

La fuente inicial propuesta es **OSV (Open Source Vulnerabilities)**. No se debe asumir que OSV cubre todos los ecosistemas, todos los tipos de vulnerabilidad ni todos los metadatos. La cobertura y el tratamiento de severidad/versiones deben investigarse y probarse.

## 3.4 Scan History

Historial de análisis por repositorio y organización.

Debe registrar:

- Fecha y hora.
- Repositorio.
- Commit o referencia analizada.
- Estado del análisis.
- Herramienta y versión del scanner.
- Componentes detectados.
- Vulnerabilidades detectadas.
- Errores o advertencias.
- Duración del análisis.
- Diferencias frente al análisis anterior.

Debe permitir comparar snapshots y detectar componentes agregados, eliminados o actualizados.

## 3.5 Change Impact Analysis

Módulo para evaluar qué podría ocurrir al agregar, actualizar, eliminar o sustituir una dependencia.

La idea conversada es que el usuario pueda seleccionar:

- Repositorio.
- Paquete.
- Versión actual.
- Versión objetivo.
- Tipo de operación: agregar, actualizar, eliminar o sustituir.

La plataforma produciría un análisis sin modificar inicialmente el repositorio.

Resultados potenciales:

- Repositorios afectados.
- Componentes que dependen del paquete.
- Dependencias transitivas relacionadas.
- Cambios documentados entre versiones.
- Breaking changes identificados en changelogs o release notes.
- Vulnerabilidades que se corrigen o introducen según la información disponible.
- Posibles incompatibilidades.
- Documentos que podrían requerir revisión.
- Pruebas sugeridas.
- Evidencia y nivel de confianza.

### Clasificación obligatoria de resultados

La plataforma debe distinguir claramente:

1. **Impacto conocido:** existe una relación demostrada por SBOM, manifest, lockfile, metadatos o evidencia verificable.
2. **Impacto potencial:** existe una ruta o cambio que merece revisión, pero no se ha demostrado que produzca un fallo.
3. **Impacto validado:** una prueba, análisis estático o ejecución controlada confirmó el efecto.


### Evolución futura: Simulate Change

Una capacidad avanzada podría:

1. Crear una rama temporal.
2. Modificar el manifest o lockfile.
3. Instalar/resolver la nueva versión.
4. Ejecutar pruebas.
5. Comparar resultados.
6. Generar un informe.
7. Proponer un Pull Request sujeto a aprobación humana.

Esta capacidad pertenece a la visión completa; la restricción del MVP de dos semanas quedó sustituida por entregas por fases.

## 3.6 Documentation Center

Centro para cargar, organizar, relacionar y consultar documentación técnica.

Tipos de documentos contemplados:

- PDF.
- DOCX.
- Markdown.
- TXT.
- README.
- OpenAPI.
- ADR (Architecture Decision Records).
- Manuales técnicos.
- Changelogs y release notes.
- Documentación interna de librerías, servicios y procesos.

Funciones propuestas:

- Cargar documentos.
- Asociar documentos con una organización.
- Asociar documentos con repositorios, microservicios, librerías, componentes o versiones.
- Indexar texto para búsqueda.
- Buscar semánticamente.
- Consultar documentación desde el asistente de IA.
- Mostrar fuentes y fragmentos que respaldan una respuesta.
- Detectar documentos que podrían quedar desactualizados cuando cambia una dependencia.
- Marcar documentos para revisión, sin declarar automáticamente que están incorrectos.

Integraciones futuras mencionadas: Notion, Confluence y documentación almacenada en GitHub.

## 3.7 AI Assistant / Documentation Intelligence

Se propuso una IA contextual, no un chatbot genérico.

Debe poder responder preguntas relacionadas con los datos reales de la organización, por ejemplo:

- ¿Dónde utilizamos esta dependencia?
- ¿Qué repositorios están afectados por esta vulnerabilidad?
- Explícame esta vulnerabilidad.
- ¿Qué versión corregida existe según la fuente?
- ¿Qué dependencias transitivas llegan a este paquete?
- ¿Qué podría verse afectado si actualizamos esta librería?
- ¿Qué documentación interna explica el uso de este componente?
- ¿Qué riesgos de dependencias tiene esta organización?
- ¿Qué diferencias hay entre dos versiones?
- ¿Qué pruebas deberíamos ejecutar antes de actualizar?

### Arquitectura de IA propuesta

No entrenar un modelo propio inicialmente. Usar un patrón RAG (Retrieval-Augmented Generation):

1. **PostgreSQL relacional:** datos operativos y estructurados.
2. **pgvector:** embeddings de documentos, fragmentos, manuales, ADR, README, OpenAPI y release notes.
3. **LLM:** interpreta la pregunta, consulta fuentes autorizadas y redacta una explicación.
4. **Capa de recuperación:** busca documentos y datos relevantes.
5. **Control de permisos:** el asistente solo puede consultar información a la que el usuario tiene acceso.
6. **Citas/evidencias:** las respuestas deben mostrar de dónde sale la información.

El asistente debe distinguir entre:
- Evidencia documentada.
- Relación demostrada.
- Inferencia.
- Hipótesis o riesgo potencial.

No debe afirmar que una actualización es segura sin evidencia. No debe decidir por sí mismo si una vulnerabilidad existe; eso corresponde a fuentes de vulnerabilidades y lógica determinista.

### Límites de seguridad de IA

- No dar acceso libre del LLM a SQL.
- Usar consultas parametrizadas, funciones permitidas o servicios de dominio.
- Validar permisos antes de recuperar datos.
- Evitar que documentos maliciosos o instrucciones dentro de ellos controlen el sistema.
- No exponer secretos o datos de otros tenants.
- Registrar procedencia de las respuestas.
- Controlar consumo, rate limits y abuso.

## 3.8 Reporting Center

Sistema de informes técnicos y ejecutivos.

Tipos de reportes propuestos:

- **Executive Report:** resumen del estado general de dependencias y riesgos.
- **Dependency Report:** inventario de paquetes, versiones, ecosistemas y repositorios.
- **Vulnerability Report:** vulnerabilidades, severidad, paquetes afectados y correcciones conocidas.
- **Impact Report:** posibles consecuencias de cambios.
- **Repository Report:** estado de cada repositorio.
- **Change Report:** diferencias entre análisis o versiones.
- **SBOM Export:** exportación de inventarios.

Formatos contemplados:

- PDF.
- CSV.
- JSON.
- CycloneDX.
- SPDX.

Evolución posterior:
- Reportes semanales o mensuales.
- Envíos programados.
- Reportes por organización, equipo, repositorio o proyecto.
- Reportes para auditoría y dirección.

---

# 4. Requisitos funcionales recopilados

Los siguientes requisitos son el borrador funcional que se propuso en la conversación. Los identificadores son una organización de trabajo, no una especificación validada formalmente. Se debe conservar la trazabilidad y revisar duplicados o cambios de prioridad al convertirlos en backlog.

## 4.1 Usuarios, organizaciones y permisos

- **RF-001:** permitir registro/inicio de sesión de usuarios.
- **RF-002:** permitir crear o pertenecer a una organización.
- **RF-003:** asociar usuarios con organizaciones.
- **RF-004:** gestionar roles.
- **RF-005:** controlar permisos por rol.
- **RF-006:** aislar los datos de cada organización (multi-tenancy).

## 4.2 Integración con GitHub

- **RF-007:** conectar una cuenta u organización de GitHub mediante GitHub App.
- **RF-008:** permitir seleccionar repositorios autorizados.
- **RF-009:** almacenar la información necesaria de la instalación, sin exponer secretos.
- **RF-010:** consultar metadatos del repositorio.
- **RF-011:** recuperar información de archivos/manifests necesaria para el análisis.
- **RF-012:** recibir webhooks de GitHub.
- **RF-013:** iniciar análisis ante eventos configurados, por ejemplo push.
- **RF-014:** permitir desconectar/revocar la integración.

## 4.3 Gestión de repositorios y análisis

- **RF-015:** listar repositorios conectados.
- **RF-016:** mostrar estado del repositorio.
- **RF-017:** iniciar un análisis manual.
- **RF-018:** consultar el estado de un análisis.
- **RF-019:** consultar el historial de análisis.
- **RF-020:** asociar cada análisis con un commit o referencia concreta.
- **RF-021:** mostrar errores y advertencias del análisis.

## 4.4 Detección de dependencias y SBOM

- **RF-022:** detectar dependencias desde los archivos compatibles.
- **RF-023:** soportar inicialmente ecosistemas seleccionados.
- **RF-024:** detectar dependencias directas.
- **RF-025:** detectar dependencias transitivas cuando la herramienta y los archivos disponibles lo permitan.
- **RF-026:** identificar nombre del paquete.
- **RF-027:** identificar ecosistema.
- **RF-028:** identificar versión.
- **RF-029:** generar un SBOM.
- **RF-030:** soportar CycloneDX como formato inicial.
- **RF-031:** almacenar los componentes detectados.
- **RF-032:** almacenar la clasificacion y procedencia de componentes.

## 4.5 Vulnerabilidades

- **RF-033:** consultar vulnerabilidades de las dependencias.
- **RF-034:** asociar hallazgos con paquete, ecosistema y versión.
- **RF-035:** mostrar identificador de vulnerabilidad.
- **RF-036:** mostrar severidad cuando esté disponible.
- **RF-037:** mostrar rangos de versiones afectadas.
- **RF-038:** mostrar versión corregida cuando la fuente la proporcione.
- **RF-039:** asociar vulnerabilidades con repositorios.
- **RF-040:** normalizar y evitar duplicados.

## 4.6 Inventario global

- **RF-041:** mostrar inventario consolidado de dependencias.
- **RF-042:** buscar dependencias.
- **RF-043:** filtrar por ecosistema, versión y repositorio.
- **RF-044:** distinguir directas y transitivas.
- **RF-045:** mostrar repositorios donde aparece una dependencia.
- **RF-046:** mostrar múltiples versiones del mismo paquete.

## 4.7 Historial y comparación

- **RF-053:** conservar snapshots de análisis.
- **RF-054:** consultar un análisis por commit.
- **RF-055:** comparar dos análisis.
- **RF-056:** detectar dependencias agregadas.
- **RF-057:** detectar dependencias eliminadas.
- **RF-058:** detectar dependencias actualizadas.
- **RF-059:** conservar fecha, fuente y procedencia de cada snapshot.

## 4.8 Recomendaciones de actualización

- **RF-060:** sugerir versiones candidatas de actualización cuando exista información fiable.
- **RF-061:** identificar versiones corregidas.
- **RF-062:** mostrar riesgos asociados a la actualización.
- **RF-063:** mostrar breaking changes documentados cuando se disponga de evidencia.
- **RF-064:** mostrar fuentes y evidencia de la recomendación.
- **RF-065:** sugerir pruebas que deberían ejecutarse antes de actualizar.

## 4.9 Alertas y seguimiento

- **RF-066:** detectar cambios relevantes en dependencias.
- **RF-067:** detectar nuevas vulnerabilidades.
- **RF-068:** notificar cambios de severidad o estado.
- **RF-069:** permitir consultar el estado de una alerta.
- **RF-070:** evitar notificaciones duplicadas.

## 4.10 SBOM y exportación

- **RF-071:** conservar SBOM generados.
- **RF-072:** consultar SBOM por repositorio y análisis.
- **RF-073:** exportar SBOM.
- **RF-074:** comparar SBOM/snapshots.

## 4.11 Documentación, IA, análisis de cambios y reportes

- **RF-075:** permitir cargar documentación técnica.
- **RF-076:** asociar documentos con organizaciones, repositorios y componentes.
- **RF-077:** indexar documentos para búsqueda semántica.
- **RF-078:** permitir consultas sobre documentación mediante IA.
- **RF-079:** mostrar referencias y evidencias de las respuestas.
- **RF-080:** permitir consultas en lenguaje natural sobre el inventario y los riesgos, respetando permisos.
- **RF-081:** analizar el impacto potencial de actualizar una dependencia.
- **RF-082:** analizar el impacto potencial de eliminar una dependencia.
- **RF-083:** mostrar repositorios y componentes relacionados/afectados.
- **RF-084:** identificar breaking changes documentados.
- **RF-085:** simular un cambio sin modificar inicialmente el repositorio.
- **RF-086:** generar reportes técnicos.
- **RF-087:** generar reportes ejecutivos.
- **RF-088:** exportar reportes a PDF, CSV y JSON.
- **RF-089:** exportar SBOM en formatos soportados.
- **RF-090:** permitir programar reportes en una fase posterior.
- **RF-091:** detectar documentación potencialmente desactualizada y marcarla para revisión.

---

# 5. Requisitos no funcionales recopilados

Los siguientes requisitos se plantearon como categorías y criterios iniciales. Los valores concretos de rendimiento, disponibilidad y retención todavía deben definirse.

## 5.1 Seguridad

- Comunicación HTTPS.
- Gestión segura de tokens y secretos.
- Validación de firma de webhooks.
- Autorización en cada recurso.
- Aislamiento de datos por organización.
- Ejecución aislada del scanner.
- No ejecutar scripts arbitrarios del repositorio sin controles.
- No montar el Docker socket en el contenedor del scanner.
- No entregar secretos innecesarios al proceso de análisis.
- Límites de CPU, memoria y tiempo.
- Auditoría de acciones relevantes.
- No registrar tokens, credenciales ni secretos en logs.
- Protección contra acceso cruzado entre tenants.
- Protección de documentos y resultados recuperados por IA.

## 5.2 Rendimiento

- Ejecutar análisis de forma asíncrona.
- No mantener una petición HTTP abierta durante todo el escaneo.
- Procesar dependencias en lotes cuando sea conveniente.
- Establecer límites de duración.
- Medir duración de cada etapa.
- Definir objetivos medibles de respuesta para dashboard, búsqueda y consulta de resultados.

## 5.3 Disponibilidad y confiabilidad

- Un fallo temporal de GitHub u OSV no debe destruir análisis previos.
- Implementar reintentos controlados.
- Procesar webhooks de forma idempotente.
- Mantener estados claros del análisis.
- Asociar los resultados con commit y fecha.
- Manejar errores parciales.
- Tener estrategia de backup y recuperación.
- Evitar duplicar análisis ante eventos repetidos.

## 5.4 Escalabilidad

- Soportar múltiples organizaciones.
- Soportar varios repositorios por organización.
- Limitar y controlar análisis paralelos.
- Usar índices adecuados.
- Separar trabajos pesados de peticiones web.
- Diseñar el almacenamiento para crecer en snapshots, componentes y vulnerabilidades.

## 5.5 Mantenibilidad

- Separar dominio, aplicación e infraestructura.
- Mantener integraciones intercambiables.
- Implementar pruebas unitarias y de integración.
- Documentar APIs.
- Gestionar cambios de esquema mediante migraciones.
- Evitar que el dominio dependa directamente de OSV, GitHub o cdxgen.

## 5.6 Interoperabilidad

- Utilizar CycloneDX como formato inicial de SBOM.
- Considerar SPDX.
- Utilizar identificadores normalizados como PURL cuando corresponda.
- Soportar varios ecosistemas de paquetes a futuro.
- Mantener adaptadores para proveedores externos.

## 5.7 Privacidad y retención

- Minimizar los datos obtenidos de repositorios.
- Definir qué contenido se almacena y por cuánto tiempo.
- Eliminar archivos temporales al terminar el análisis.
- Permitir revocar la integración con GitHub.
- No conservar secretos detectados accidentalmente.
- Definir políticas de retención y eliminación de documentos y análisis.

## 5.8 Usabilidad y accesibilidad

- Interfaz consistente.
- Navegación clara entre repositorios, dependencias y vulnerabilidades.
- Resultados técnicos explicados de forma comprensible.
- Diseño responsive.
- Estados vacíos, de carga y error bien definidos.

## 5.9 Observabilidad

- Logs estructurados.
- Métricas de duración y resultado de análisis.
- Métricas de llamadas a proveedores.
- Seguimiento de trabajos/colas.
- Registro de errores.
- Procedencia de resultados.
- Trazabilidad entre webhook, job, scan, SBOM y vulnerabilidades.

## 5.10 Requisitos no funcionales añadidos para IA, documentación e informes

- **RNF-050:** la IA debe respetar permisos y aislamiento multi-tenant.
- **RNF-051:** las respuestas de IA deben presentar evidencias o referencias cuando sea posible.
- **RNF-052:** documentos cargados deben procesarse de forma segura.
- **RNF-053:** la IA debe diferenciar hechos, inferencias e incertidumbres.
- **RNF-054:** los resultados de impacto deben estar ligados a versiones, commits y fuentes.
- **RNF-055:** los reportes deben ser trazables y reproducibles.
- **RNF-056:** el uso de IA debe tener límites de consumo, rate limiting y controles contra abuso.

---

# 6. Pantallas y navegación propuestas

Se planteó una interfaz SaaS B2B con navegación lateral.

## 6.1 Pantallas principales

1. **Login / Access**
   - Inicio de sesión.
   - Recuperación de acceso.
   - Selección de organización si el usuario pertenece a varias.

2. **Onboarding / GitHub Integration**
   - Explicar conexión.
   - Instalar GitHub App.
   - Seleccionar organización y repositorios.
   - Mostrar permisos solicitados.
   - Confirmar conexión.

3. **Overview / Dashboard**
   - Resumen de repositorios.
   - Dependencias detectadas.
   - Vulnerabilidades por severidad.
   - Cambios recientes.
   - Estado de análisis.
   - Riesgos destacados.
   - Accesos rápidos.

4. **Repositories**
   - Lista de repositorios conectados.
   - Lenguaje/ecosistema detectado.
   - Último análisis.
   - Estado.
   - Número de dependencias y vulnerabilidades.
   - Acción de análisis manual.

5. **Repository Detail**
   - Overview.
   - Dependencies.
   - Vulnerabilities.
   - Scan History.
   - Commit analizado y fecha.

6. **Dependencies / Inventory**
   - Inventario global.
   - Nombre, ecosistema, versión.
   - Directa/transitiva.
   - Repositorios donde aparece.
   - Filtros y búsqueda.

7. **Dependency Detail**
   - Información del paquete.
   - Versiones detectadas.
   - Repositorios relacionados.
   - Vulnerabilidades.
   - Dependencias que lo incluyen.
   - Historial de cambios.
   - Documentación asociada.
   - Acceso a análisis de impacto.

8. **Vulnerabilities**
   - Listado de vulnerabilidades.
   - Severidad.
   - Paquete/versión afectada.
   - Repositorios afectados.
   - Estado de corrección.
   - Fuente.

   - Filtros por repositorio, paquete y riesgo.
   - Navegación por rutas.

10. **Scan History**
    - Historial de análisis.
    - Estado, commit, fecha y duración.
    - Comparación entre snapshots.

11. **Integrations**
    - GitHub.
    - Proveedores de vulnerabilidades.
    - Integraciones futuras.

12. **Settings / Organization Management**
    - Organización.
    - Usuarios.
    - Roles.
    - Preferencias.
    - Seguridad.

## 6.2 Pantallas nuevas propuestas

13. **AI Assistant**
    - Consultas contextuales.
    - Historial de preguntas.
    - Respuestas con evidencia.
    - Acceso contextual desde otras pantallas.

14. **Documentation Center**
    - Carga y listado de documentos.
    - Asociación a repositorios/componentes.
    - Búsqueda.
    - Estado de indexación.
    - Documentos por revisar.

15. **Change Impact Analysis**
    - Selección de paquete y versión.
    - Simulación.
    - Repositorios y rutas afectadas.
    - Breaking changes documentados.
    - Riesgos, pruebas y evidencia.

16. **Reporting Center**
    - Catálogo de reportes.
    - Filtros.
    - Vista previa.
    - Exportación.
    - Programación futura.

## 6.3 Navegación lateral sugerida

- Overview
- Repositories
- Dependencies
- Vulnerabilities
- Documentation
- Change Impact
- AI Assistant
- Reports
- Scan History
- Integrations
- Settings

No es necesario construir toda la navegación en la primera entrega. Las pantallas de IA, documentación avanzada, Change Impact y reportes programados pueden quedar para fases posteriores.

## 6.4 Pantallas con mayor valor visual y funcional

- Dashboard: permite entender el estado general.
- Dependency Detail: concentra el contexto de un paquete.
- Change Impact Analysis: puede convertirse en diferenciador si se implementa con evidencia real.

Las imágenes o mockups previos eran ilustrativos; no representan datos reales ni un diseño final aprobado.

---

# 7. Arquitectura técnica propuesta

## 7.1 Stack candidato

| Capa | Tecnología propuesta | Propósito |
|---|---|---|
| Frontend | Angular + TypeScript | Aplicación web SaaS |
| Backend | Java + Spring Boot | API, lógica de negocio e integraciones |
| Base de datos | PostgreSQL | Datos operativos y relacionales |
| Vector search | pgvector, fase posterior | Embeddings y búsqueda semántica documental |
| Repositorios | GitHub App / GitHub API | Conexión y lectura autorizada |
| Scanner | cdxgen | Generación de SBOM |
| Formato SBOM | CycloneDX | Inventario interoperable |
| Vulnerabilidades | OSV API | Consulta de vulnerabilidades |
| Contenedores | Docker | Aislamiento de ejecución |
| Fuente opcional | deps.dev | Metadatos adicionales, sujeto a validación |
| Scanner opcional | OSV-Scanner | Alternativa o complemento, sujeto a evaluación |
| PostgreSQL alojado opcional | Neon Free | Prototipo remoto, sujeto a límites del plan |

El usuario trabaja con Java/Spring Boot, Angular/TypeScript y PostgreSQL, por lo que el stack aprovecha tecnologías que ya conoce.

## 7.2 Flujo principal de análisis

1. El usuario instala/autoriza la GitHub App.
2. La aplicación recibe la instalación y los repositorios autorizados.
3. El backend registra la integración y los repositorios.
4. El usuario solicita un análisis o llega un webhook configurado.
5. El backend crea un trabajo asíncrono.
6. Se obtiene el commit o referencia exacta.
7. Se recuperan los archivos requeridos del repositorio.
8. Se ejecuta cdxgen en un entorno aislado y limitado.
9. Se genera un SBOM CycloneDX.
10. El backend parsea el SBOM.
11. Se normalizan componentes, versiones, PURL y clasificacion.
12. Se persisten el análisis, componentes.
13. Se consultan vulnerabilidades en OSV por lotes.
14. Se normalizan vulnerabilidades y se asocian con componentes.
15. Se actualiza el estado del análisis.

## 7.3 Integración OSV

Endpoints de referencia que se mencionaron:

- `POST https://api.osv.dev/v1/querybatch`
- `GET https://api.osv.dev/v1/vulns/{vulnerability_id}`

La consulta requiere datos adecuados de paquete, ecosistema y versión. Para el MVP conviene agrupar consultas con `querybatch`, controlar lotes, manejar respuestas parciales y evitar consultas redundantes.

La respuesta de OSV debe mapearse a un modelo interno. No se debe acoplar el dominio al JSON externo.


## 7.4 SBOM y cdxgen

- cdxgen se propuso como herramienta inicial para generar SBOM.
- CycloneDX es el formato inicial recomendado.
- Se debe comprobar la calidad de detección por ecosistema.
- La detección de transitivas depende del ecosistema, archivos disponibles, lockfiles y comportamiento de la herramienta.
- Se deben probar repositorios con y sin lockfiles.
- Se deben evaluar monorepos y proyectos multi-módulo.
- No se debe prometer cobertura total a partir de un solo escaneo.

## 7.5 GitHub App

La GitHub App es preferible a pedir tokens personales de larga duración.

La investigación debe confirmar:

- Permisos mínimos requeridos.
- Acceso a repositorios privados.
- Instalación a nivel de organización.
- Obtención y renovación de installation access tokens.
- Webhooks y validación de firma.
- Rate limits.
- Revocación/desinstalación.
- Compatibilidad futura con GitHub Enterprise Server.
- Qué datos se leen y almacenan.

## 7.6 Arquitectura interna

Se recomendó separar:

- **Domain:** entidades y reglas del negocio.
- **Application:** casos de uso y orquestación.
- **Infrastructure:** PostgreSQL, GitHub, OSV, cdxgen, almacenamiento y proveedores.
- **API / Presentation:** controladores REST y DTOs.
- **Workers:** ejecución asíncrona de análisis y tareas pesadas.

Posibles módulos de dominio:

- Organization / Identity.
- Repository.
- Integration.
- Scan.
- SBOM / Component.
- Vulnerability.
- Documentation.
- Impact Analysis.
- Reporting / Notification.

La arquitectura puede seguir principios de arquitectura hexagonal y DDD, sin sobredimensionar el diseño para el MVP. No se debe crear complejidad de microservicios sin necesidad; un monolito modular con workers puede ser más viable al inicio.

---

# 8. Modelo de datos conceptual

No se ha aprobado un esquema SQL definitivo. Las siguientes entidades son una base para investigar y diseñar.

## 8.1 Entidades principales

- **Organization:** organización cliente.
- **User:** usuario.
- **Membership:** relación usuario-organización y rol.
- **Integration:** integración externa instalada.
- **GitHubInstallation:** identificador y estado de instalación.
- **Repository:** repositorio conectado.
- **Scan:** ejecución de análisis.
- **ScanArtifact:** SBOM u otros artefactos generados.
- **Component:** paquete/componente normalizado.
- **ScanComponent:** presencia de un componente en un análisis.
- relación entre componentes.
- **Vulnerability:** vulnerabilidad normalizada.
- **ComponentVulnerability:** asociación entre componente y vulnerabilidad.
- **Document:** documento cargado o sincronizado.
- **DocumentChunk:** fragmentos indexados.
- **DocumentAssociation:** asociación de documento con repositorio/componente/organización.
- **ImpactAnalysis:** análisis de cambio.
- **ImpactFinding:** hallazgo de impacto con tipo, evidencia y confianza.
- **Report:** reporte generado.
- **Notification:** alerta o notificación.
- **AuditEvent:** registro de acciones relevantes.

## 8.2 Consideraciones de diseño

- Los componentes deben poder identificarse de forma estable por ecosistema y paquete, preferiblemente con PURL cuando aplique.
- La versión debe ser parte del contexto de presencia del componente en un análisis.
- No confundir el catálogo de componentes con la aparición concreta de un componente en un repositorio y commit.
- Las relaciones deben asociarse al snapshot/scan que las detectó.
- La vulnerabilidad debe guardar identificador y procedencia externa.
- Mantener datos originales o referencias suficientes para auditoría y reprocesamiento.
- La asociación documento-componente debe permitir relaciones de muchos a muchos.
- Los embeddings deben vincularse con el documento, fragmento, organización y permisos.
- Los resultados de impacto deben guardar versión actual, versión objetivo, commit base, fuentes y método utilizado.

---

# 9. API REST conceptual

No se han cerrado endpoints definitivos. Los siguientes son ejemplos de organización para convertir en contrato OpenAPI.

## 9.1 Autenticación y organizaciones

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/me`
- `GET /api/organizations`
- `POST /api/organizations`
- `GET /api/organizations/{organizationId}`
- `GET /api/organizations/{organizationId}/members`
- `POST /api/organizations/{organizationId}/members`

## 9.2 GitHub e integraciones

- `GET /api/integrations`
- `POST /api/integrations/github/install`
- `GET /api/integrations/github/callback`
- `GET /api/integrations/github/repositories`
- `POST /api/integrations/github/repositories`
- `DELETE /api/integrations/{integrationId}`
- `POST /api/webhooks/github`

## 9.3 Repositorios y análisis

- `GET /api/repositories`
- `GET /api/repositories/{repositoryId}`
- `POST /api/repositories/{repositoryId}/scans`
- `GET /api/repositories/{repositoryId}/scans`
- `GET /api/scans/{scanId}`
- `GET /api/scans/{scanId}/components`
- `GET /api/scans/{scanId}/sbom`
- `GET /api/scans/{scanId}/diff`

## 9.4 Dependencias y vulnerabilidades

- `GET /api/dependencies`
- `GET /api/dependencies/{dependencyId}`
- `GET /api/dependencies/{dependencyId}/repositories`
- `GET /api/dependencies/{dependencyId}/vulnerabilities`
- `GET /api/vulnerabilities`
- `GET /api/vulnerabilities/{vulnerabilityId}`
- `GET /api/repositories/{repositoryId}/vulnerabilities`



## 9.6 Documentación e IA

- `POST /api/documents`
- `GET /api/documents`
- `GET /api/documents/{documentId}`
- `DELETE /api/documents/{documentId}`
- `POST /api/documents/{documentId}/index`
- `GET /api/documents/search`
- `POST /api/assistant/query`

## 9.7 Análisis de impacto

- `POST /api/impact-analyses`
- `GET /api/impact-analyses`
- `GET /api/impact-analyses/{analysisId}`
- `POST /api/impact-analyses/{analysisId}/simulate`

## 9.8 Reportes

- `GET /api/reports`
- `POST /api/reports`
- `GET /api/reports/{reportId}`
- `GET /api/reports/{reportId}/download`
- `POST /api/reports/schedules` (fase posterior)

Estos endpoints son propuestas, no contratos implementados ni validados.

---

# 10. Seguridad del scanner y de los repositorios

Una advertencia importante de la conversación: el repositorio conectado es contenido no confiable.

El scanner no debe ejecutar arbitrariamente scripts del proyecto.

Controles recomendados:

- Ejecutar cdxgen en contenedor aislado.
- Usar una imagen controlada y versiones fijadas.
- No montar el Docker socket.
- No usar privilegios elevados.
- Limitar CPU, memoria, almacenamiento y duración.
- No pasar credenciales de GitHub al contenedor si no son necesarias.
- Evitar acceso de red saliente salvo lo indispensable.
- Montar el contenido del repositorio como solo lectura cuando sea posible.
- Eliminar archivos temporales.
- Limitar tamaño de repositorio y artefactos.
- Validar rutas para evitar path traversal.
- Registrar scanner y versión.
- Manejar fallos y timeouts.
- Probar comportamiento con proyectos malformados o maliciosos.

---

# 11. Plan de MVP en dos semanas (histórico; no vigente)

Este apartado describe la demo local de dos semanas como antecedente histórico. El objetivo vigente es completar el producto por fases, primero local y después servidor; el plazo anterior no define alcance ni aceptación.

## 11.1 MVP recomendado

**Objetivo:** demostrar el flujo completo desde GitHub hasta un inventario de dependencias y vulnerabilidades visualizable.

Alcance mínimo:

1. Conectar GitHub.
2. Seleccionar repositorios.
3. Iniciar análisis manual.
4. Ejecutar cdxgen de forma aislada.
5. Generar CycloneDX SBOM.
6. Parsear y clasificar componentes.
7. Guardar resultados en PostgreSQL.
8. Consultar OSV.
9. Mostrar inventario.
10. Mostrar vulnerabilidades.
11. Mostrar detalle de repositorio.
13. Guardar historial de análisis.
14. Comparar snapshots de forma básica.
15. Generar un reporte/exportación básica.

## 11.2 Exclusiones del plan histórico de dos semanas

Salvo que el MVP ya esté terminado y exista capacidad real:

- RAG completo.
- Chatbot con acceso amplio a toda la plataforma.
- Integración con Notion/Confluence.
- Simulación que modifica repositorios.
- Creación automática de Pull Requests.
- Análisis real de compatibilidad entre versiones.
- Breaking changes inferidos sin fuente.
- Reportes programados.
- SSO empresarial.
- GitHub Enterprise Server.
- Multitud de ecosistemas.
- Microservicios distribuidos.
- Motor avanzado de políticas.

## 11.3 Priorización sugerida por fases

### Fase 1 — MVP, primeros 14 días

- GitHub App.
- Repositorios.
- Scanner.
- SBOM.
- Inventario.
- OSV.
- Vulnerabilidades.
- Dashboard.
- Historial.
- Reporte básico.

### Fase 2 — Documentation Intelligence

- Documentation Center.
- Carga y extracción de documentos.
- Asociación con repositorios y componentes.
- Indexación.
- pgvector.
- RAG acotado.
- AI Assistant con referencias.

### Fase 3 — Change Intelligence

- Simulación de cambios.
- Comparación de versiones.
- Breaking changes con fuentes.
- Análisis de impacto.
- Documentación potencialmente afectada.
- Notificaciones.

### Fase 4 — Enterprise Intelligence

- Reportes programados.
- Integraciones adicionales.
- Auditoría avanzada.
- Políticas.
- Análisis más avanzado.
- PR asistido y remediación con aprobación humana.
- SSO y capacidades empresariales según demanda validada.

---

# 12. Decisiones del MVP que quedaron pendientes

En una conversación se propusieron cinco decisiones para cerrar el MVP. El usuario todavía no confirmó expresamente las opciones:

1. **Ecosistemas:** Java/Maven + JavaScript/npm (recomendación inicial).
2. **Scanner:** cdxgen + OSV API.
3. **Alcance:** inventario + vulnerabilidades + historial.
5. **Ejecución:** análisis manual + webhook push.

No presentar estas opciones como decisiones definitivas. Retomarlas y confirmarlas cuando se cierre el alcance.

---

# 13. Investigación pendiente

Estas son las áreas que se identificaron para investigar antes de afirmar capacidades o cerrar requisitos.

## 13.1 GitHub App

- Permisos mínimos.
- Acceso a repositorios públicos y privados.
- Instalación por organización.
- Tokens de instalación y duración.
- Webhooks y eventos útiles.
- Validación de firmas.
- Rate limits.
- Revocación.
- GitHub Enterprise.
- Qué archivos se necesitan realmente para el scanner.

## 13.2 Scanner y ecosistemas

- Comparar cdxgen y OSV-Scanner.
- Cobertura real de Maven y npm.
- Directas y transitivas.
- Lockfiles obligatorios u opcionales.
- Comportamiento sin lockfiles.
- Monorepos.
- Maven multi-module.
- Gradle.
- npm workspaces.
- Versiones dinámicas.
- Dependencias privadas.
- Dependencias descargadas desde registries privados.
- Diferencias entre SBOM generado y árbol resuelto por el gestor de paquetes.
- Reproducibilidad del análisis.

## 13.3 Vulnerabilidades

- Cobertura de OSV por ecosistema.
- Diferencia entre OSV ID y CVE.
- Severidad y CVSS.
- Rangos de versiones afectadas.
- Versiones fixed.
- Vulnerabilidades sin severidad.
- Duplicados y alias.
- Frecuencia de actualización.
- CISA KEV como fuente complementaria futura.
- Cómo mostrar incertidumbre o datos incompletos.

## 13.4 Seguridad

- Aislamiento de scanner.
- Contenedores.
- Límites de recursos.
- Tokens y secretos.
- Archivos temporales.
- Datos almacenados.
- Aislamiento multi-tenant.
- Seguridad de webhooks.
- Prompt injection en documentos y repositorios.
- Seguridad de archivos PDF/DOCX.
- Permisos de IA.

## 13.5 Compatibilidad y cambios

- Changelogs.
- Release notes.
- SemVer y sus límites.
- Breaking changes documentados.
- Comparación de APIs.
- Compatibilidad binaria.
- Pruebas automatizadas.
- Diferencia entre una actualización con versión mayor y un breaking change real.
- Cómo justificar una recomendación con evidencia.

## 13.6 Enterprise readiness

- SSO.
- SAML/OIDC.
- GitHub Enterprise Server.
- Auditoría.
- Retención.
- Residencia de datos.
- Controles de acceso.
- Requisitos de vendor security.
- Exportación y cumplimiento.

## 13.7 Validación comercial

- Quién es el comprador.
- Quién usa el producto.
- Qué problema es suficientemente costoso para pagar.
- Diferenciación frente a Dependabot, Snyk, Sonatype y otras plataformas.
- Qué herramientas ya tiene el cliente.
- Por qué compraría un producto adicional.
- Precio y modelo de cobro.
- Segmento de empresa inicial.
- Proceso de venta y prueba piloto.

---

# 14. Competencia y diferenciación: hipótesis a validar

Se mencionaron como referencias competitivas o alternativas del mercado:

- GitHub Dependabot.
- Snyk.
- Sonatype.
- Otras herramientas de SCA, SBOM y gestión de vulnerabilidades.

No se realizó en este documento una investigación competitiva actualizada ni se deben atribuir capacidades específicas a esos productos sin comprobar sus fuentes oficiales.

La hipótesis de diferenciación de Dependency Intelligence no debe limitarse a “también detectamos vulnerabilidades”. El valor potencial estaría en conectar:

- Inventario organizacional.
- Relaciones y rutas de dependencias.
- Vulnerabilidades.
- Historial.
- Documentación interna.
- Análisis de cambios.
- Explicaciones y reportes con evidencia.

Hay que demostrar que esa combinación resuelve un problema que las herramientas existentes no resuelven bien para el segmento elegido.

---

# 15. Principios de producto que no se deben perder

1. **La evidencia es más importante que una respuesta convincente.**
2. No presentar una inferencia como hecho.
3. No declarar que un servicio se rompe sin pruebas.
4. No declarar una actualización segura sin evidencia.
5. Las vulnerabilidades deben proceder de fuentes identificables.
6. Mantener commit, versión, fecha y procedencia.
7. Diferenciar inventario actual de snapshots históricos.
8. Separar datos estructurados de documentación semántica.
9. La IA debe explicar y ayudar a explorar, no sustituir el motor determinista.
10. Respetar permisos y aislamiento de organización.
11. Diseñar el MVP para demostrar valor de extremo a extremo.
12. Evitar construir funciones empresariales avanzadas antes de validar el problema comercial.
13. No ejecutar código no confiable del repositorio sin aislamiento y controles.
14. No confundir SBOM con un mapa de comunicación en tiempo de ejecución.
15. Cada recomendación debe incluir fuente, evidencia, incertidumbre y limitaciones.

---

# 16. Contexto del desarrollador y restricciones de ejecución

Este contexto ayuda a adaptar futuras explicaciones y planes técnicos:

- Tecnologías conocidas/practicadas: Java, Spring Boot, PostgreSQL, Angular y TypeScript.
- Ha trabajado conceptos de arquitectura hexagonal, DDD, APIs REST, Git Flow y organización por capas.
- Busca profundizar en arquitectura empresarial, no solo construir pantallas.
- Prefiere respuestas concretas, técnicas y objetivas.
- Le sirven entregables como:
  - SRS.
  - Historias de usuario.
  - Criterios de aceptación.
  - Backlog priorizado.
  - Diagramas de arquitectura.
  - Modelo entidad-relación.
  - Endpoints REST.
  - Contratos OpenAPI.
  - Plan de implementación.
  - Estrategia de pruebas.
  - Decisiones técnicas justificadas.
- No quiere propuestas genéricas ni funcionalidades simples sin diferenciación.
- La referencia al MVP de dos semanas es histórica y fue sustituida por el objetivo de producto completo por fases.
- Busca potencial de comercialización B2B, especialmente en Estados Unidos.

---

# 17. Estado actual del trabajo

## Ya conversado/propuesto

- Concepto y posicionamiento de Dependency Intelligence.
- Problema que busca resolver.
- Módulos funcionales principales.
- Borrador de requisitos funcionales RF-001 a RF-091.
- Categorías de requisitos no funcionales.
- Pantallas y navegación.
- Integración GitHub.
- cdxgen y CycloneDX.
- OSV API.
- PostgreSQL.
- Arquitectura modular/hexagonal.
- Inventario y vulnerabilidades.
- Historial y snapshots.
- Documentación técnica.
- IA contextual con RAG.
- Change Impact Analysis.
- Reporting Center.
- Roadmap por cuatro fases.
- Advertencias de seguridad y límites de la IA.

## No está confirmado o implementado según el contexto disponible

- No se han confirmado las cinco decisiones del MVP.
- No existe aquí un SRS formal aprobado.
- No existe un esquema SQL definitivo.
- No existe un contrato OpenAPI definitivo.
- No se ha demostrado una integración GitHub funcional.
- No se ha ejecutado cdxgen contra repositorios reales en el contexto disponible.
- No se ha validado la cobertura real de OSV para los ecosistemas objetivo.
- No se ha implementado el asistente IA.
- No se ha implementado el centro documental.
- No se ha implementado Change Impact.
- No se ha validado comercialmente el producto.
- No se ha completado un análisis competitivo actualizado.
- Los mockups/imágenes vistos anteriormente eran ilustrativos, no interfaces finales ni resultados reales.

---

# 18. Próximos pasos recomendados para continuar en la nueva cuenta

Orden sugerido para no perder tiempo:

## Paso 1 — Cerrar el MVP (proceso histórico sustituido por el bloque 61)

Confirmar:
- Ecosistemas iniciales.
- Herramienta de SBOM.
- Fuente de vulnerabilidades.
- Tipo de análisis.
- Qué quedó fuera del demo histórico de dos semanas.

## Paso 2 — Investigar las integraciones críticas

Prioridad:
1. GitHub App.
2. cdxgen para Maven/npm.
3. CycloneDX.
4. OSV API.

Probar cada integración con repositorios reales de prueba, no diseñar únicamente desde documentación.

## Paso 3 — Crear el SRS v1

Convertir los requisitos en:
- Identificador.
- Descripción.
- Prioridad (Must/Should/Could/Won't).
- Historia de usuario.
- Criterios de aceptación.
- Dependencias.
- Riesgos.
- Estado (por investigar, definido, implementado, probado).

## Paso 4 — Diseñar el modelo de datos

Definir entidades, relaciones, restricciones, índices y estrategia multi-tenant. Mantener los snapshots de análisis como unidades trazables.

## Paso 5 — Definir contratos API

Crear OpenAPI para:
- GitHub.
- Repositorios.
- Scans.
- SBOM.
- Componentes.
- Vulnerabilidades.
- Historial.

## Paso 6 — Construir el flujo vertical

Priorizar un flujo que funcione de principio a fin:

**Conectar repositorio → analizar commit → generar SBOM → persistir dependencias → consultar OSV → mostrar resultados.**

## Paso 8 — Validar diferenciación y mercado

Comparar con alternativas existentes y entrevistar potenciales usuarios/compradores antes de construir IA avanzada o funciones empresariales complejas.

---

# 19. Instrucciones para el siguiente ChatGPT

Al continuar este proyecto, utiliza este archivo como contexto maestro.

1. No vuelvas a empezar la ideación desde cero.
2. No vuelvas a preguntar qué es Dependency Intelligence: ya está definido en este documento.
3. Trata el MVP de dos semanas como antecedente histórico; el objetivo vigente es el producto completo por fases.
4. No asumas que las propuestas pendientes ya fueron aprobadas.
5. Si se necesitan datos actuales de GitHub, OSV, cdxgen, CycloneDX, deps.dev, competidores o precios, investiga fuentes oficiales actualizadas.
6. No inventes cobertura, límites, endpoints ni capacidades de proveedores.
7. No recomiendes construir un chatbot genérico como prioridad.
8. La IA debe trabajar sobre datos y documentos recuperados con permisos, y responder con evidencia.
9. No uses embeddings para reemplazar el modelo relacional de dependencias y vulnerabilidades.
10. No afirmes impacto real cuando solo hay una relación potencial.
11. Prioriza entregables implementables: SRS, backlog, modelo de datos, endpoints, diagramas y plan de desarrollo.
12. Mantén el lenguaje técnico claro y explica las decisiones arquitectónicas.
13. Cuando el usuario pida avanzar, utiliza el contexto ya disponible y evita hacerle repetir decisiones o antecedentes.

**Punto de continuación recomendado:** cerrar el alcance exacto del MVP y convertir los requisitos RF/RNF en un backlog priorizado con historias de usuario y criterios de aceptación. Después, diseñar el modelo de datos y los contratos API del flujo vertical de análisis.

## Estado al bloque 26 (18 de noviembre de 2026)

Se implementaron preferencias compartidas de tema e idioma por organizacion mediante V18, API y Angular. Base local en V18: 126 tablas de negocio, 89 con FORCE RLS y 223 FK. Ver `docs/database/implementation-report.md` y `docs/PENDIENTES.md`. Build frontend/backend correcto; la prueba focalizada de integracion sigue pendiente tras fallo DNS de Maven Central en el contenedor aislado.
## Diagnostico GitHub - bloque 28 (18 de noviembre de 2026)

El flujo real todavia requiere configurar la GitHub App del propietario. Las variables `GITHUB_APP_*` faltan en el `.env` local. La UI ahora informa configuracion incompleta frente a permisos insuficientes por organizacion y solo habilita instalacion si ambas condiciones estan listas. Prueba real aun pendiente; callbacks de esta instancia usan localhost:8089. Detalle: [GITHUB_REPOSITORIOS](GITHUB_REPOSITORIOS.md).
