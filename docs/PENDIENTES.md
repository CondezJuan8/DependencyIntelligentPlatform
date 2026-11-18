# Pendientes de Dependency Intelligence

Actualizado: 18 de noviembre de 2026, tras la decisión de admisión por correo.

**Instrucción vigente del usuario:** conservar aquí los pendientes y trabajarlos juntos cuando él lo indique. El usuario autorizó comenzar la integración de repositorios GitHub; las demás tareas siguen sin iniciarse hasta que se acuerden.

Este archivo reúne configuración pendiente, validaciones de lo entregado y funciones por implementar. El alcance y las decisiones técnicas se mantienen en el [contexto maestro](DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md), la [arquitectura](ARQUITECTURA_BACKEND.md), la [especificación database](CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md) y el [stack](STACK_TECNOLOGICO_DEPENDENCY_INTELLIGENCE.md). No hay fecha total nueva comprometida.

## Lo que ya está implementado

- Backend separado en Domain, Application e Infrastructure; database V1–V17. V1–V15 aplicadas/catalogadas con 124 tablas, 87 con RLS forzado y 218 FK; V16/V17 están implementadas pero falta aplicarlas/verificarlas en PostgreSQL.
- Acceso local, sesiones/CSRF, creación/listado de organizaciones y autorización de repositorios/scans por rol/equipo.
- Verificación de correo y recuperación de contraseña con SMTP/Mailpit, tokens de un uso y revocación de sesiones.
- Admisión de responsables: aviso individual a los administradores verificados con aprobar/rechazar inmediato, tokens de un uso y caducidad. La pantalla persistente «Esperando confirmación» se mantiene. Verificar el recorrido en Mailpit.
- Google/GitHub y vinculación/desvinculación segura en backend y Angular, comprobados con proveedor HTTP local de prueba. Faltan credenciales y consentimiento reales.
- Parser CycloneDX Maven acotado e importación persistente; todavía sin flujo de análisis real completo.
- Paleta del usuario aplicada mediante tokens y Angular Material a las pantallas existentes.
- Invitaciones SMTP, aceptación, gestión de membresías/roles, equipos y permisos por repositorio en backend/Angular. Probados con PostgreSQL y Mailpit; revisión visual conjunta pendiente. [Recorrido y contratos](INVITACIONES_EQUIPOS_PERMISOS.md).
- Rediseño según las tres imágenes del usuario: acceso/registro, shell lateral, dashboard con estados vacíos y organización con tabla, filtros, pestañas y diálogos. Build correcto; falta aceptación visual del usuario. [Diseño](DISENO_FRONTEND.md).
- Integración de GitHub App: instalación y verificación de pertenencia mediante autorización OAuth/PKCE, selección e importación de repositorios, interfaz responsive móvil/escritorio. Falta configurar credenciales de la GitHub App del usuario y hacer el recorrido real.

El detalle y la evidencia por bloque están en la [bitácora](BITACORA_TRABAJO.md). Las tablas creadas y las pruebas con proveedores simulados no significan que el producto esté listo para usar en su totalidad.

## Configurar y validar juntos lo entregado

- [x] **Arranque nativo para revisión:** frontend http://localhost:8088/, API con health UP y proxy operativo, Mailpit http://localhost:8025/. Base separada dependency_preview y servicios activos al cerrar el bloque 12. Esto no completa revisión visual ni verificación de Compose.

- [ ] **Google:** registrar/configurar cliente propio, consentimiento, audiencia/cuentas de prueba y callback; guardar ID/secret fuera del repositorio y activar el proveedor. Probar login, cuenta existente, vínculo, cancelación y errores con Google real.
- [ ] **GitHub login:** elegir/configurar el registro de aplicación, credenciales, callback y permisos de identidad; probar consentimiento, correo principal verificado, login y vínculo con GitHub real. No equivale a autorizar repositorios.
- [ ] Revisar el rediseño del bloque 13 y la interfaz de repositorios en localhost:8088: login/registro, dashboard, navegación móvil, tablas y organización. La revisión automática de frontend no sustituye la aceptación del usuario.
- [ ] **GitHub App real:** configurar la app propiedad de `CondezJuan8`, sus callbacks/permisos y secretos locales según [GITHUB_REPOSITORIOS](GITHUB_REPOSITORIOS.md); completar instalación/importación real y comprobar el acceso desde móvil y escritorio.
- [ ] **Arranque Compose:** compilación y arranque comprobados el 18/11: backend health UP, frontend HTTP 200, PostgreSQL y Mailpit saludables; V15 aplicada al volumen existente. Falta aplicar V16/V17, reiniciar Compose y comprobar explícitamente que los datos de negocio sobreviven el reinicio.
- [ ] **Servidor y correo:** configurar SMTP real para entregar avisos de nuevas solicitudes de acceso con aprobación/rechazo; comprobar entrega y comportamiento ante escáneres que ejecutan enlaces. Mailpit cubre el aviso local, no entrega externa.

Pasos y callbacks en [IDENTIDAD_GOOGLE_GITHUB](IDENTIDAD_GOOGLE_GITHUB.md), acceso en [ACCESO_LOCAL](ACCESO_LOCAL.md), correo en [VERIFICACION_Y_RECUPERACION](VERIFICACION_Y_RECUPERACION.md) y revisión de colores en [DISENO_FRONTEND](DISENO_FRONTEND.md). Nunca escribir secretos en este archivo ni enviarlos por chat.

## Funciones por implementar

- [ ] Configuración compartida de organización: tema del espacio, idioma y más parámetros deben persistirse en el backend con autorización y controles adaptables; las preferencias actuales de apariencia/idioma son locales al navegador.
- [ ] Importación desde carpeta del equipo: selector/upload seguro, límites de tamaño/tipo, almacenamiento temporal y análisis aislado Maven/npm con progreso/errores. El navegador no puede guardar una ruta local del usuario en el servidor.

Orden de referencia según las dependencias del maestro; se elige el bloque concreto con el usuario al retomar.

| Área | Pendiente | Criterio de cierre |
| --- | --- | --- |
| Arranque local Docker | La instancia actual tiene administrador de plataforma; falta documentar/probar el bootstrap repetible para instalaciones nuevas | Crear el primer administrador una sola vez mediante un mecanismo local controlado, con secretos fuera de Git y sin habilitar auto-administración pública |
| Identidad y organización | Backend/UI de invitaciones y gestión entregados en bloque 11; pendiente revisión conjunta y paginación de historiales de invitaciones grandes | Completar aceptación visual/operativa sin considerar pendiente el código ya probado |
| Integración de repositorios GitHub | Instalación de App, verificación OAuth/PKCE, listado e importación de metadatos | Configurar y verificar instalación real, revocar/desconectar, leer commits/archivos y procesar eventos idempotentes |
| Repositorios y análisis | Pantallas, worker/jobs, estados, errores, reintentos/recuperación e historial con commit real | Solicitar y completar análisis trazable, con fallos recuperables |
| SBOM y ecosistemas | Carga HTTP, scanner aislado Maven/npm, cobertura del parser y relaciones con evidencia | Procesar proyectos reales de ambos ecosistemas y guardar componentes/aristas sin inferencias falsas |
| Vulnerabilidades | Adaptador OSV, coincidencia por versión, evidencia y hallazgos | Mostrar vulnerabilidades reproducibles del snapshot analizado |
| Inventario y grafo | API/UI con búsqueda, filtros, paginación, relaciones y rutas de riesgo | Navegar dependencias y distinguir directa/transitiva solo cuando exista evidencia |
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
