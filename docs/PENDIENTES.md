# Pendientes de Dependency Intelligence

Actualizado: 20 de noviembre de 2026, bloque 55.

## Bloque 55 — scanner de dependencias y cobertura OSV (implementado; recorrido autenticado pendiente)

Se conectó el flujo usado por el botón de Inventario a cdxgen secure 13.8.0 mediante un sidecar Docker aislado. Los readers Maven/npm convierten SBOM CycloneDX en el mismo modelo de inventario y grafo; V25 registra cobertura `COMPLETE/PARTIAL`; el importador CycloneDX ahora admite Maven/npm y guarda las aristas declaradas. El análisis Maven se marca parcial porque el modo seguro no ejecuta Maven y no verifica el árbol transitivo completo. Se añadieron regresiones focales de mapper, extracción aislada, paquete scoped y paginación/fallo OSV.

**Verificado:** 18 pruebas focales, 5 pruebas `SbomImportIT` con PostgreSQL desechable, build Angular, runtime cdxgen 13.8.0 en Compose y V25 aplicada. El sidecar encontró npm scoped directo + transitivo y sus dos relaciones. Para Maven multimódulo encontró versiones heredadas/gestionadas en dos módulos, conservó ambos paquetes sin inventar aristas y marcó la cobertura parcial/relación `UNKNOWN`. El frontend y backend de Compose se actualizaron; el frontend responde HTTP 200. OSV se probó con respuestas controladas, no en vivo. No se completó todavía una solicitud autenticada de análisis desde la sesión del usuario.

**Pendiente:** recorrido autenticado desde Inventario sobre repositorios reales; confirmar en la UI que se actualizan inventario, relaciones y vulnerabilidades después del análisis, y observar el comportamiento con un error real de OSV. Maven transitivo sigue limitado por el modo seguro: la cobertura se informa parcial y no se afirman relaciones no emitidas por el SBOM. Ver [scanner](../infra/scanner/README.md) y [bitácora](BITACORA_TRABAJO.md).

Ver [scanner](../infra/scanner/README.md) y [bitácora](BITACORA_TRABAJO.md).

**Bloque 54 implementado, revisión de UI pendiente:** el grafo ya ofrece vista general/local, profundidad y dirección, búsqueda centrada, filtros combinables por ecosistema/módulo/tipo/ámbito, grupos por namespace y contador de nodos/aristas observadas. No se crean aristas que atraviesen dependencias ocultas. El DTO del grafo todavía no incluye severidad de vulnerabilidad. Con datos reales se midió `OMEGA-main` (277 paquetes; 492 relaciones de paquete) y `Bot-de-Whatsapp-plantilla--main` (284 paquetes consolidados; 411 relaciones de paquete); el layout Cytoscape headless tomó 297 ms y 306 ms. El caso de seis paquetes fue solo un fixture derivado de OMEGA, porque no existe un repo de ese tamaño en la base activa. Falta recorrido visual en navegador; la sesión CUA no pudo verificar la URL de Chrome. Ver [bitácora](BITACORA_TRABAJO.md).

**Próxima comprobación prioritaria:** recorrido interactivo del bloque 54 con los dos repositorios reales y un repositorio independiente de seis paquetes cuando esté disponible; revisar búsqueda, filtros, vista local, zoom, ajuste, tema y tamaños móvil/escritorio. El build actualizado está servido en `localhost:8089` (página y chunk del grafo HTTP 200); no se hizo una prueba visual ni de bloqueo de interfaz en esta sesión.

Actualizado: 20 de noviembre de 2026, bloque 51 (mapa de dependencias por capas).

**Estado del entorno y datos:** no se consultó la base de datos activa durante el bloque 52, por lo que el contenido actual de repositorios/scans debe confirmarse en la instancia local al abrir la plataforma. La muestra de npm permanece en `samples/npm-axios-demo` (Axios directo + ocho paquetes transitivos en lockfile v3); no se afirma que Omega/Bot de WhatsApp estén importados o analizados desde esta sesión.

**Estado del grafo e Inventario:** el bloque 52 conserva el mapa por capas (hasta 1.000 nodos), agrega una red force-directed (hasta 350 nodos), mantiene lista/búsqueda completas y filtros de seguridad por versión. TypeScript, parseo de templates y build Angular pasan; falta revisión visual con Omega/Bot de WhatsApp, temas claro/oscuro y móvil/escritorio. La vista actualizada está en `http://localhost:8090/?view=inventory`; la API usa el backend activo de 8089. Ver [diseño](DISENO_FRONTEND.md) y [bitácora](BITACORA_TRABAJO.md).

Bloque 35: el botón principal detecta una instalación única y lleva directamente a OAuth/PKCE; el navegador recibe `index.html` sin caché y recarga la pestaña restaurada al volver desde GitHub. El propietario completó el recorrido. La base registra la instalación y dos repositorios; la descarga ZIP funcionó en la vista del usuario.

**Instrucción vigente:** continuar por bloques funcionales aprobados y registrar cada entrega en esta lista y en la bitácora. El bootstrap seguro ya está implementado; sincronizar el estado de GitHub requiere pulsar «Sincronizar acceso con GitHub» o volver a cargar la vista de repositorios. No hay webhook ni sincronización en segundo plano en este bloque.

Este archivo reúne configuración pendiente, validaciones de lo entregado y funciones por implementar. El alcance y las decisiones técnicas se mantienen en el [contexto maestro](DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md), la [arquitectura](ARQUITECTURA_BACKEND.md), la [especificación database](CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md) y el [stack](STACK_TECNOLOGICO_DEPENDENCY_INTELLIGENCE.md). No hay fecha total nueva comprometida.

## Estado del bloque 26 - Configuracion de organizacion

Tema e idioma compartidos implementados de extremo a extremo: V18 crea `organization_settings` con valores por defecto, seguridad RLS y permisos runtime; el alta de organizaciones incluye la fila inicial. La API deja leer a miembros activos y solo OWNER/ADMIN pueden editar. Angular aplica la preferencia del espacio en la sesion, conserva preferencias personales para login/registro y ofrece controles en Configuracion. V18 ya esta en PostgreSQL local: 126 tablas, 89 con FORCE RLS y 223 FK. Build Angular y backend correctos; el IT focalizado queda pendiente porque Maven Central no resolvio DNS desde el contenedor de prueba. Servicios de revision activos en http://localhost:8089/.
## Lo que ya está implementado

- Bootstrap inicial seguro implementado: comando interactivo local sin endpoint HTTP, transaccion de un solo uso, restriccion a base sin usuarios/organizaciones/admins, hash PBKDF2, cuenta OWNER aprobada/verificada y auditoria. Compose ya cuenta con un administrador activo. Ver [ACCESO_LOCAL](ACCESO_LOCAL.md).

- Backend separado en Domain, Application e Infrastructure; database V1-V24 aplicada y verificada en PostgreSQL Compose: 128 tablas de negocio, 91 con FORCE RLS y 227 FK. V24 permite registrar hasta 50.000 archivos por fuente local, preservando el máximo de 100 MiB del contenido.
- Acceso local, sesiones/CSRF, creación/listado de organizaciones y autorización de repositorios/scans por rol/equipo.
- Verificación de correo y recuperación de contraseña con SMTP/Mailpit, tokens de un uso y revocación de sesiones.
- Admisión de responsables: aviso individual a los administradores verificados con aprobar/rechazar inmediato, tokens de un uso y caducidad. La pantalla persistente «Esperando confirmación» se mantiene. Verificar el recorrido en Mailpit.
- Google/GitHub y vinculación/desvinculación segura en backend y Angular. La GitHub App de repositorios ya tiene credenciales locales y consentimiento real en Dinamita; el login social de Google/GitHub aún requiere su propia configuración y comprobación real.
- Parser CycloneDX Maven acotado e importación persistente; todavía sin flujo de análisis real completo.
- Importación local de carpetas al almacenamiento privado y registro tenant-scoped del repositorio. El proceso no ejecuta todavía un escaneo automático.
- Paleta del usuario aplicada mediante tokens y Angular Material a las pantallas existentes.
- Invitaciones SMTP, aceptación, gestión de membresías/roles, equipos y permisos por repositorio en backend/Angular. Probados con PostgreSQL y Mailpit; revisión visual conjunta pendiente. [Recorrido y contratos](INVITACIONES_EQUIPOS_PERMISOS.md).
- Rediseño según las tres imágenes del usuario: acceso/registro, shell lateral, dashboard con estados vacíos y organización con tabla, filtros, pestañas y diálogos. Build correcto; falta aceptación visual del usuario. [Diseño](DISENO_FRONTEND.md).
- Integración de GitHub App: instalación y verificación de pertenencia mediante autorización OAuth/PKCE, selección e importación de repositorios, interfaz responsive móvil/escritorio. App instalada bajo `CondezJuan8` con dos repositorios autorizados; credenciales locales completas en `.env` ignorado por Git. Vinculación e importación reales completadas en Dinamita; falta sincronización automática ante cambios posteriores en GitHub.

El detalle y la evidencia por bloque están en la [bitácora](BITACORA_TRABAJO.md). Las tablas creadas y las pruebas con proveedores simulados no significan que el producto esté listo para usar en su totalidad.

## Configurar y validar juntos lo entregado

- [x] **Arranque nativo para revisión:** frontend http://localhost:8088/, API con health UP y proxy operativo, Mailpit http://localhost:8025/. Base separada dependency_preview y servicios activos al cerrar el bloque 12. Esto no completa revisión visual ni verificación de Compose.

- [ ] **Google:** registrar/configurar cliente propio, consentimiento, audiencia/cuentas de prueba y callback; guardar ID/secret fuera del repositorio y activar el proveedor. Probar login, cuenta existente, vínculo, cancelación y errores con Google real.
- [ ] **GitHub login:** elegir/configurar el registro de aplicación, credenciales, callback y permisos de identidad; probar consentimiento, correo principal verificado, login y vínculo con GitHub real. No equivale a autorizar repositorios.
- [ ] Revisar el rediseño del bloque 13 y la interfaz de repositorios en localhost:8088: login/registro, dashboard, navegación móvil, tablas y organización. La revisión automática de frontend no sustituye la aceptación del usuario.
- [x] **GitHub App real en escritorio local:** OAuth/PKCE completado en `localhost:8089`, instalación de `CondezJuan8` activa en Dinamita, dos repositorios importados y descarga ZIP confirmada por el usuario. Queda pendiente revisión en móvil/otro dispositivo y automatizar sincronización ante cambios de selección en GitHub.
- [x] **Arranque Compose y migraciones:** Flyway valida V1-V24; V24 aplicada. 128 tablas, 91 FORCE RLS, 227 FK. Health UP, PostgreSQL y Mailpit saludables; el volumen de base se conservó.
- [ ] **Servidor y correo:** configurar SMTP real para entregar avisos de nuevas solicitudes de acceso con aprobación/rechazo; comprobar entrega y comportamiento ante escáneres que ejecutan enlaces. Mailpit cubre el aviso local, no entrega externa.

Pasos y callbacks en [IDENTIDAD_GOOGLE_GITHUB](IDENTIDAD_GOOGLE_GITHUB.md), acceso en [ACCESO_LOCAL](ACCESO_LOCAL.md), correo en [VERIFICACION_Y_RECUPERACION](VERIFICACION_Y_RECUPERACION.md) y revisión de colores en [DISENO_FRONTEND](DISENO_FRONTEND.md). Nunca escribir secretos en este archivo ni enviarlos por chat.

## Funciones por implementar

- [x] Configuracion compartida de organizacion: tema SYSTEM/LIGHT/DARK e idioma ES/EN persistidos en backend con autorizacion OWNER/ADMIN y lectura para miembros activos; V18 aplicada y verificada en PostgreSQL local. Parametros adicionales quedan pendientes.
- [x] Importación desde carpeta: selector web, exclusión de `.git`/dependencias/artefactos de build/credenciales conocidas, límite de 100 MiB/50.000 archivos, ZIP privado persistente, repositorio local tenant-scoped y autorización mediante permisos existentes. La carga histórica de 446 archivos se confirmó antes de V24; el nuevo borde de 50.000 está cubierto por test focal y queda pendiente validación de carga real.
- [ ] Análisis de repositorios: Maven lee declaraciones directas concretas; npm lee paquetes del lockfile v2/v3 con tipo directo/transitivo. Falta validación real del usuario, aristas padre-hijo, Maven transitivo/herencia/BOM, npm v1 y jobs aislados/progreso/reintentos.

Orden de referencia según las dependencias del maestro; se elige el bloque concreto con el usuario al retomar.

| Área | Pendiente | Criterio de cierre |
| --- | --- | --- |
| Arranque local Docker | Arranque interactivo verificado. La instancia de revisión tiene 7 cuentas, 1 administrador activo y 2 organizaciones | Para conectar GitHub, entrar con una membresía OWNER o ADMIN de la organización elegida |
| Identidad y organización | Backend/UI de invitaciones y gestión entregados en bloque 11; pendiente revisión conjunta y paginación de historiales de invitaciones grandes | Completar aceptación visual/operativa sin considerar pendiente el código ya probado |
| Integración de repositorios GitHub | Instalación/OAuth/PKCE, listado, importación, descarga y sincronización bajo demanda; deshabilita repositorios retirados y detecta instalación revocada al abrir/actualizar el espacio | Falta webhook o sincronización periódica inmediata, y revisión del usuario retirando/revocando acceso real; las fuentes remotas viven mientras GitHub las autorice |
| Repositorios y análisis | Pantallas, worker/jobs, estados, errores, reintentos/recuperación e historial con commit real | Solicitar y completar análisis trazable, con fallos recuperables |
| SBOM y ecosistemas | Carga, parsers Maven/npm con cobertura ampliada, scanner aislado y relaciones con evidencia | Procesar proyectos reales y guardar componentes/aristas verificables sin inferencias |
| Vulnerabilidades | Consulta OSV por versión exacta, snapshots con evidencia tenant-scoped, Dashboard por cuatro niveles, fixes oficiales y búsqueda estática por archivo/línea para dos advisories Jackson (bloques 42–44) | Revisar con avisos reales del usuario; ampliar patrones/cobertura y decidir más adelante análisis de flujo real, sin afirmar que las heurísticas prueban explotabilidad |
| Inventario y grafo | Catálogo UI agregado, API/vista Cytoscape por repositorio, búsqueda/filtro/detalle y lista textual implementados. npm persiste relaciones alcanzables del lockfile v2/v3; Maven enlaza proyecto con dependencias explícitas de POM. Pendientes: resolución Maven transitiva, vistas multi-repositorio/paginación, riesgo en nodos y validación con datos reales | Navegar paquetes/versiones y relaciones con evidencia trazable; directa/transitiva solo cuando la fuente lo respalde |
| Historial y comparación | Consultas por commit y comparación de snapshots del mismo repositorio | Identificar añadidos, eliminados y actualizados con procedencia |
| Recomendaciones | Versiones candidatas/corregidas, riesgo y evidencia documental | Proponer cambios explicables y pruebas pertinentes |
| Impacto y primer piloto | ADD/UPDATE/REMOVE/REPLACE, estados KNOWN/POTENTIAL/VALIDATED, pruebas aisladas y propuesta de PR aprobable | Ejecutar validación aislada y presentar una propuesta revisable; acciones sobre repositorios sujetas a aprobación en el producto |
| Artefactos y documentos | Retención privada de SBOM/originales, versiones, descarga/exportación autorizada, reindexación y limpieza de temporales | Conservar y consultar originales con permisos y política de retención/eliminación |
| Búsqueda e IA | Extracción/OCR, búsqueda híbrida, embeddings Ollama y asistente Gemini con citas/permisos | Responder con evidencia autorizada; fijar modelos/dimensión y activar pgvector solo cuando se implemente esa fase |
| Alertas | Eventos, deduplicación, preferencias y entrega in-app | Consultar y gestionar notificaciones dentro de la plataforma; correo de notificaciones después |
| Reportes | Informes técnicos/ejecutivos y exportación PDF/CSV/JSON/SBOM; programación posterior | Exportaciones trazables con permisos sobre los datos |
| Interfaz general | Navegación, pantallas de módulos, estados de carga/error y accesibilidad | Recorridos completos con datos reales y revisión del usuario |

## Operación y requisitos transversales

- [ ] Definir y completar persistencia/recuperación de sesiones para el despliegue; actualmente viven en memoria.
- [ ] Entrega durable y reintentos de recuperación/verificación/invitaciones; resolver el límite entre aceptación SMTP y commit de base. Recuperación usa cola en memoria e invitaciones envío síncrono transaccional.
- [ ] Ampliar auditoría a los nuevos casos de uso y completar logs/métricas útiles, sin secretos.
- [ ] Concretar límites coordinados y proxy de confianza; el límite de intentos actual es por proceso.
- [ ] Configurar HTTPS, cookies seguras, origen público y callbacks exactos al pasar a servidor.
- [ ] Definir retención, limpieza, almacenamiento y gestión de secretos según el entorno.
- [ ] Comprobar backup/restauración, migraciones y despliegue reproducible en el entorno de destino.
- [ ] Mantener migraciones incrementales y actualizar diagramas/diccionario únicamente si cambia el esquema.
- [ ] Verificar cada bloque con las pruebas necesarias y evidencia real, sin repetir suites por rutina.

## Cómo retomaremos

1. El usuario indica cuándo y qué bloque trabajamos juntos.
2. Revisamos estado y dependencias; identificamos configuración o decisiones necesarias, sin pedir permisos ya concedidos para ese bloque.
3. Implementamos y verificamos lo acordado. Actualizamos esta lista, maestro y bitácora, con herramientas usadas y límites comprobados.
4. Marcamos como terminado solo lo que tenga evidencia y guardamos commits coherentes con emoji y descripción en español.

El primer recorrido pendiente de revisión es acceso y colores. El siguiente hito funcional es **GitHub → repositorio → análisis → inventario con datos reales**; todavía no está completado. Codex queda fuera de la IA del producto hasta petición explícita del usuario. No se presupone una cuota, modelo ni gratuidad de proveedores.
## Estado del bloque 27 - Sidebar, busqueda y preferencias de encabezado

Se tradujeron las etiquetas de navegacion y resultados del buscador; ahora puede buscarse usando el nombre mostrado en ingles. El selector de idioma y tema del encabezado vuelve a estar activo con una organizacion. El ajuste compartido queda como valor predeterminado y cada persona puede aplicar un override local desde el encabezado; se conserva en este navegador. Frontend actualizado en http://localhost:8089/ y responde HTTP 200. Falta tu revision visual.
## Estado del bloque 28 - Diagnostico GitHub

La pantalla ahora comprueba si la cuenta puede administrar GitHub en la organizacion y si la GitHub App esta configurada. Explica el requisito y no deja pulsar Instalar cuando falta configuracion o rol. En el `.env` local faltan las variables `GITHUB_APP_*`, por lo que aun hace falta registrar/configurar la App y guardar sus secretos localmente antes de probar OAuth e importacion. Callback vigente: `http://localhost:8089/api/integrations/github/app-callback`.

### Actualización del bloque 43

El tablero de vulnerabilidades y las explicaciones de advisories ya están implementados en frontend. Build y despliegue de `localhost:8089` se verificarán en este bloque; la revisión visual conjunta con repositorios reales todavía está pendiente.

Estado final del bloque 43: frontend compilado, desplegado y disponible en `http://localhost:8089`; portada y bundle HTTP 200. La revisión visual con datos reales queda para el usuario.
