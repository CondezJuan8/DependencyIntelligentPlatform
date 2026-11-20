# Paleta y criterios de revisión del frontend

### Bloque 51 — mapa de dependencias por capas

El grafo de Inventario ahora distribuye proyecto, directas y transitivas de izquierda a derecha por profundidad. Angular consolida ocurrencias con la misma identidad de paquete, versión y ecosistema en una tarjeta y conserva rutas/ámbitos para el detalle; las relaciones compartidas convergen en ese nodo. Las tarjetas muestran nombre/versión, los bordes identifican tipo, y búsqueda resalta coincidencias con su ruta de padres. El panel separa dependencias declaradas y paquetes que utilizan el nodo. El listado accesible mantiene todas las entradas; el canvas limita a 1.000 nodos por respuesta, prioriza niveles cercanos y enfoca búsquedas/selecciones grandes. Cytoscape usa layout preset y cálculo BFS O(V+E); no se añadió biblioteca ni se cambió API/base de datos. La revisión de tipos de TypeScript (44 archivos) y el parseo del template pasan; build completa y revisión visual en Compose quedan pendientes porque Node/npm CLI y el host de navegador no están disponibles. Ver [diseño](DISENO_FRONTEND.md), [stack](STACK_TECNOLOGICO_DEPENDENCY_INTELLIGENCE.md), [pendientes](PENDIENTES.md) y [bitácora](BITACORA_TRABAJO.md).
## Bloque 48 — grafo legible por niveles

El canvas representa el proyecto en el centro, las dependencias directas en el anillo interior y las transitivas en el exterior. Las etiquetas se orientan hacia fuera, se resumen en el dibujo y conservan su nombre completo en la lista y el detalle. Las aristas no repiten texto: flecha y leyenda explican el sentido. La guía está localizada en inglés/español y conserva la alternativa de lista accesible, selección, búsqueda, zoom y ajuste. Verificado visualmente con un snapshot Maven de seis dependencias directas; el anillo transitivo se llena solo con relaciones observadas. Sigue pendiente la revisión responsive con un lockfile npm/transitivas y datos grandes.

## Bloque 45 — grafo interactivo responsive y accesible

Inventario añade controles etiquetados para repositorio, ecosistema y búsqueda; la visualización permite zoom y ajuste. El canvas se presenta como imagen interactiva y se acompaña de una lista HTML de paquetes con botones operables por teclado, estado seleccionado anunciado, panel de detalle y rutas/fuente legibles. Los colores de etiqueta/superficie/borde Cytoscape se vuelven a leer cuando cambia `data-theme`, además del fondo SCSS basado en tokens. En móvil, filtros y leyenda se apilan, los contadores pasan a dos columnas y la lista/detalle usan una columna. Se comunica un estado vacío útil si no hay fuentes o dependencias soportadas. Revisar en móvil/escritorio, tema claro/oscuro y un lockfile grande: la generación/revisión visual automática no se hizo.

## Bloque 44 — avisos expandibles, procedencia y ciclo de repositorio

Cada aviso es una tarjeta desplegable con GHSA/severidad, escenario, acción, versiones corregidas, detalle técnico y fuentes oficiales. «Buscar dónde revisar en el código» muestra ruta/línea y extracto; commit/SHA identifican el archivo fuente revisado. Los estados sin coincidencias o con límites explican sus alcances; la UI no presenta un patrón textual como prueba de explotabilidad. La procedencia por ocurrencia queda abierta y ocupa todo el ancho disponible para origen, commit y hash. El favicon carga el SVG de marca.

El propietario puede retirar un repositorio con confirmación; se explica que GitHub no borra el remoto y el historial local queda conservado. «Sincronizar acceso con GitHub» refresca autorización. Build y despliegue en `localhost:8089` completados; la revisión visual manual en escritorio/móvil y con avisos reales queda pendiente.

## Bloque 43 — lectura de vulnerabilidades

Dashboard presenta cuatro categorías con texto, color semántico y conteo de versiones por repositorio; muestra hasta seis avisos con paquete@versión, repositorio y enlace a OSV. Las evaluaciones pendientes o con error llevan un mensaje separado y no se presentan como seguras. Tipografía, alturas de línea, tamaño de texto y aire de paneles aumentan para aprovechar el espacio. En Inventario las explicaciones y recomendaciones aparecen antes del detalle técnico desplegable; referencias largas ocupan varias líneas sin estrechar el contenido. El diseño reutiliza tokens de modo claro/oscuro. Revisión visual responsive y aceptación del usuario siguen pendientes.

## Bloque 41 - Inventario unificado, mobile-first y accesible

La vista agrupa las versiones de Maven y npm en un catálogo con búsqueda y filtros por ecosistema, tipo directo/transitivo y ámbito. Una sola acción analiza ambos formatos y presenta resultados parciales indicando el estado de cada scanner. Cada ocurrencia conserva acceso desplegable a la fuente del ZIP, hash, commit cuando existe, manifiesto y fecha.

El riesgo no se colorea como seguro o vulnerable: aparece como **sin evaluar** hasta integrar OSV. Los indicadores cuentan paquetes/ versiones exactos y ocurrencias detectadas, sin mezclarlos entre ecosistemas. Controles con etiqueta persistente, foco visible, estados anunciados, `details/summary` operables por teclado, colores semánticos con texto y cortes responsive desde celular completan la pantalla. El bloque no agrega íconos externos, fuentes, dependencias ni colores fuera de los tokens existentes.

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

## Ajustes de revisión previa — bloque 15

17 de noviembre de 2026. Se continúa el rediseño con prioridad móvil según la retroalimentación del usuario. El login utiliza una columna compacta móvil y conserva el panel de marca en escritorio; se redujeron espacios para alturas pequeñas sin ocultar contenido. En Organización, las tablas cambian a tarjetas apiladas en móvil con rótulos por campo y sin desplazamiento horizontal. La barra superior adapta búsqueda y controles a pantallas estrechas; en anchos inferiores a 360 px compacta el espacio de notificaciones.

Se activa traducción global de etiquetas y textos estáticos al alternar ES/EN, incluidos aria-label, placeholder, title y data-label de tarjetas. Los valores de usuario/repositorio marcados data-no-translate se conservan tal cual. El tema usa tokens claros/oscuros y la tipografía local IBM Plex Sans. El layout de acceso usa 100svh; en dispositivos extremadamente bajos o con zoom el contenido puede requerir desplazamiento para seguir siendo accesible, por lo que no se bloquea el overflow a la fuerza.

## Ajustes de idioma y preferencias del encabezado - bloque 27

Sidebar, buscador y resultados usan el mismo catalogo de traducciones. La busqueda compara tanto la etiqueta localizada como la original. Los controles de idioma/tema del encabezado siguen disponibles aunque la organizacion tenga preferencias compartidas: estas preferencias compartidas son los valores iniciales y el usuario puede establecer un override local, persistido en el navegador.
