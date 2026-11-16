# Paleta y criterios de revisión del frontend

## Rediseño basado en referencias del usuario — bloque 13

16 de noviembre de 2026. El usuario entregó tres imágenes de organización, login y dashboard, y delegó el diseño del registro. Se toma su composición visual como referencia, manteniendo español, la paleta oficial y los contratos existentes. La imagen no autoriza planes comerciales, datos de ejemplo presentados como reales ni funciones nuevas de GitHub/IA.

### Pantallas entregadas

- **Acceso y registro:** composición de dos paneles, marca navy con ilustración propia de red/escudo en SVG, formulario claro, pestañas de acceso, iconos y control de visibilidad de contraseña. Registro con nombre, correo, contraseña/confirmación y reglas existentes. No se añade un «recordarme» que prometa persistencia distinta de la sesión actual. Google/GitHub aparecen según la disponibilidad real; si no están habilitados, botones desactivados con explicación.
- **Espacio de trabajo:** sidebar navy con indicador activo, topbar con búsqueda de secciones, avatar con iniciales reales, opciones de cuenta, cierre de sesión, navegación móvil y enlace de salto al contenido. Navegación `?view=` con lista de páginas permitidas, recarga y atrás/adelante del navegador.
- **Dashboard:** tarjetas, panel de grafo, evolución y organizaciones siguiendo la distribución de la referencia. El número de organizaciones procede de la API. Repositorios/dependencias/riesgo permanecen sin cifra evaluada; el grafo es una ilustración de estado vacío. No se inventan vulnerabilidades, tendencias, porcentajes, actividad ni respuestas del asistente. Los módulos futuros llevan estado «En preparación».
- **Organización:** selección de espacio, pestañas Miembros/Invitaciones/Equipos/Permisos, tabla con iniciales, equipos, roles y estados, búsqueda/filtro local, paginación de miembros en bloques de diez y diálogos para las operaciones existentes. Foco atrapado/restaurado con CDK, Escape y confirmaciones; un formulario cancelado de acceso se reinicializa al abrirlo otra vez. La lista de invitaciones conserva el límite de cien del backend.
- **Configuración:** perfil real y métodos conectados en su sección propia. Las invitaciones recibidas abren Organización para mantener accesible la aceptación tras iniciar sesión.

### Sistema visual

Marca e ilustración vectoriales propias en frontend/public/brand-mark.svg y auth-network.svg; iconos SVG locales mediante DiIcon. No se copiaron archivos personales de Pictures al repositorio ni se descargan fuentes, iconos o imágenes externos. Tipografía del sistema con fallback Inter/Segoe UI/Arial; Angular Material usa Segoe UI y densidad -2. Colores principales reutilizados mediante tokens. Se añaden tokens auxiliares text-subtle (#64748B), text-secondary (#536784), surface-muted (#F1F5F9) y border-soft (#D5E0EF). Bloques reutilizables de tarjetas, diálogos y estados en theme/_workspace.scss.

Componentes separados para AuthFrame, ApplicationShell, Sidebar, Topbar, Dashboard, Metrics y vista previa del asistente. Se conserva carga diferida con @defer y estados de carga/error. Layouts con breakpoints de escritorio/móvil, tablas con desplazamiento horizontal, modales de alto acotado y respeto de prefers-reduced-motion. Son reglas implementadas, pendientes de aceptación visual en dispositivos reales.

### Verificación y revisión disponible

Build de producción correcto: 406,37 kB iniciales, dentro del presupuesto de 500 kB y sin avisos de tamaño por componente. Se dividieron componentes/utilidades para resolver los avisos de estilos, sin ampliar los límites. Siete comprobaciones directas de selección de páginas/lista permitida correctas, formato Prettier correcto y frontend HTTP 200 con backend UP. No se ejecutan pruebas backend porque no cambian APIs, permisos ni datos.

La herramienta de browser indicó host indisponible. La alternativa nativa localizó Chrome, pero detuvo la captura al no poder verificar la URL con suficiente confianza. No se continuó automatizando la ventana ni se declara revisión visual completada. El entorno nativo del bloque 12 permanece activo en http://localhost:8088 para que el usuario revise; correo en http://localhost:8025. Login/registro se ven cerrando sesión desde el avatar. Recoger observaciones antes de iniciar la conexión de repositorios GitHub.

Las secciones siguientes conservan la especificación de color y evidencia del bloque 08.

Decisión del usuario del 15 de noviembre de 2026. Esta paleta es la referencia visual de Dependency Intelligence. Los colores se centralizan en tokens CSS emitidos desde Sass y se conectan con Angular Material. [Tokens](../frontend/src/theme/_tokens.scss), [tema global](../frontend/src/styles.scss).

## Paleta principal y distribución

| Color | Hex | Uso |
| --- | --- | --- |
| Midnight Navy | `#101827` | Sidebar y navegación oscura al incorporar navegación funcional |
| Electric Blue | `#2563EB` | Botones principales, enlaces sobre blanco y foco de teclado |
| Cyan | `#06B6D4` | Acentos y elementos activos sobre navy |
| Slate | `#1E293B` | Texto secundario |
| Background | `#F8FAFC` | Fondo general |
| White | `#FFFFFF` | Header y tarjetas |
| Texto principal | `#0F172A` | Contenido y títulos |
| Bordes | `#E2E8F0` | Separadores y contornos decorativos de tarjetas |

Los controles interactivos usan un contorno más visible `#64748B`. El cian sobre blanco tiene contraste 2,43:1. Se recomienda azul eléctrico para enlaces de texto sobre blanco y cian para estados activos sobre navy, donde alcanza 7,32:1. Para texto cian sobre blanco se dispone de la variante `#0E7490`, con contraste 5,36:1.

## Colores semánticos

| Estado | Color principal | Texto sobre superficie clara | Superficie |
| --- | --- | --- | --- |
| Seguro / correcto | `#16A34A` | `#166534` | `#F0FDF4` |
| Advertencia | `#EAB308` | `#854D0E` | `#FEFCE8` |
| Riesgo alto | `#F97316` | `#9A3412` | `#FFF7ED` |
| Crítico / error | `#DC2626` | `#991B1B` | `#FEF2F2` |
| Información | `#2563EB` | `#1E40AF` | `#EFF6FF` |

Las variantes oscuras permiten leer etiquetas sobre fondos claros. Cada estado debe acompañarse de un texto o icono con etiqueta que explique su significado. La elección de semántica debe corresponder al dato real. Las confirmaciones actuales de verificación/cambio de contraseña usan verde y los errores usan rojo.

## Aplicación actual

Fondo, header, tarjetas, formularios de acceso/recuperación, organizaciones, botones Material, errores y aviso de verificación consumen la paleta centralizada. El token de sidebar queda preparado para la navegación funcional de los módulos posteriores. Las nuevas pantallas deben reutilizar los tokens para evitar valores hex dispersos.

Angular Material conserva sus APIs de theming. Los overrides fijan colores primarios, superficies, contornos y errores. El alcance de este bloque es el color y su documentación. La referencia del framework se contrastó también con el Sass instalado. [Angular Material theming](https://material.angular.dev/guide/theming).

## Comprobaciones

Build Angular de producción correcto. Se calcularon 15 combinaciones de texto con los tokens reales y todas superan 4,5:1. Los contornos de controles superan 3:1 sobre su superficie. Ejemplos, blanco sobre azul 5,17:1, texto principal sobre blanco 17,85:1 y rojo crítico sobre blanco 4,83:1. Se comprobó que el CSS de producción contiene los tokens y el override primario.

La referencia de texto normal utiliza 4,5:1, con excepciones definidas por el estándar. Estas comprobaciones cubren los pares seleccionados. La evaluación visual y de accesibilidad completa requiere recorrer las pantallas. [WCAG 2.2 contraste mínimo](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html).

La revisión en navegador conectado quedó pendiente porque su host continúa indisponible. Las pantallas deberán revisarse en escritorio/móvil, con teclado y en sus estados de carga/error cuando se levante el entorno local.

## Hitos de revisión con el usuario

El usuario quiere probar recorridos interactivos y dar observaciones de diseño o errores durante el desarrollo. Avisar en la conversación al cerrar cada hito, indicando qué se puede probar, cómo arrancarlo y los límites comprobados. Mantener esos avisos en próximos bloques de trabajo.

| Hito | Recorrido para revisar | Estado |
| --- | --- | --- |
| Primera revisión local de acceso y colores | Registro, login, organización, correo en Mailpit, verificación, recuperación y logout | Código disponible con API/SMTP/build comprobados. Pendientes el arranque mediante Compose y la revisión visual con el usuario |
| Primera revisión con datos reales | GitHub autorizado, repositorio elegido, análisis de un commit y consulta del inventario con estados de carga/error | Pendiente de integración GitHub, worker/scanner e inventario HTTP/UI. Avisar al completar el recorrido reproducible |
| Revisión del primer piloto | Equipos/permisos, grafo, vulnerabilidades e impacto con pruebas aisladas y propuesta de PR aprobable | Pendiente según el contexto maestro |

La primera revisión puede comenzar por acceso y apariencia al levantar el entorno. La revisión con dependencias reales llegará al completar el segundo recorrido. Antes de anunciar un hito como disponible, verificar su arranque y recorrido, documentar errores conocidos y señalar cualquier parte pendiente de comprobar. Las instrucciones actuales de acceso y correo están en [ACCESO_LOCAL](ACCESO_LOCAL.md) y [VERIFICACION_Y_RECUPERACION](VERIFICACION_Y_RECUPERACION.md).
