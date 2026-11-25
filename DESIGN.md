# Dependency Intelligence — sistema visual

## Dirección: Atlas de Evidencias

Dependency Intelligence se entiende como un atlas de trabajo: cada repositorio abre un terreno; cada dependencia tiene una ubicación verificable; cada aviso se conecta a una fuente y a un siguiente paso. La interfaz conserva los patrones familiares de una aplicación web empresarial. La identidad aparece en el terreno de color, las líneas de procedencia y el tratamiento de estados, sin convertir tablas o controles en ilustraciones.

El tono es preciso, tranquilo y humano. Ingeniería y seguridad comparten una misma lectura: origen, versión, cobertura, estado y evidencia. Mantener el tema oscuro como predeterminado y ofrecer los modos claro y sistema existentes.

## Identidad y paleta

La paleta **Field Atlas** sustituye la paleta azul anterior por autorización explícita del usuario del 24/11/2026. Los valores se centralizan en `frontend/src/theme/_tokens.scss` y `frontend/src/theme/_appearance.scss`; los componentes consumen esos tokens.

| Función | Claro | Oscuro | Uso |
| --- | --- | --- | --- |
| Marca y navegación | `#14231A` | `#111B15` | Sidebar, identidad y superficies de acceso |
| Acción principal | `#3E6B3B` | `#B9E47C` | Acciones primarias; el color del texto cambia con el tema |
| Acento de mapa | `#91B94E` | `#83D5B5` | Selección, trazado y progreso |
| Enlace | `#315B38` | `#C7E99A` | Navegación y referencias |
| Lienzo | `#F3F4EC` | `#101713` | Fondo de página |
| Superficie | `#FBFCF6` | `#18221B` | Formularios y áreas de trabajo |
| Superficie elevada | `#FFFFFF` | `#263329` | Menús y diálogos |
| Superficie secundaria | `#E9EDE3` | `#222E25` | Filtros, hover y agrupación |
| Texto principal | `#17231A` | `#EFF5EB` | Títulos y contenido |
| Texto secundario | `#4A5E4D` | `#C4D0C0` | Ayuda, explicación y metadatos |
| Texto tenue | `#657568` | `#A7B6A5` | Información de prioridad baja |
| Borde | `#DBE2D6` | `#35453A` | Separación y límites de control |

Los estados de éxito, advertencia, riesgo alto y crítico conservan tokens semánticos y contrastes propios. El nombre del estado siempre acompaña al color. El token `--di-on-primary` mantiene legible el texto de las acciones en ambos temas.

## Tipografía

Usar IBM Plex Sans, ya incluida localmente. Usar numerales tabulares para conteos, versiones, tiempos e identificadores; reservar la fuente monoespaciada del sistema para código, coordenadas y hashes.

| Nivel | Tamaño y peso |
| --- | --- |
| Título de página | 27–33 px / 600 |
| Título de sección | 18–20 px / 600 |
| Subtítulo | 15–16 px / 600 |
| Texto y controles | 14–16 px / 400–600 |
| Tablas y metadatos | 12–14 px / 400–500 |

Usar interlineado de 1.45–1.6 para lectura. Mantener el texto largo entre 60 y 75 caracteres por línea y dejar que valores extensos se partan sin ensanchar la página.

## Espaciado, formas y superficies

Mantener el ritmo base de 4 px con los tokens existentes: 4, 8, 12, 16, 20, 24, 32 y 40 px. Usar gutters de 24–30 px en escritorio y 16 px en móvil. Controles de 6 px de radio, paneles de 10 px y diálogos de 14 px. Reservar sombras suaves con desplazamiento para menús y diálogos; los paneles de trabajo usan borde y superficie, sin sombras decorativas.

## Iconografía y marca

Conservar la geometría de marca y usar los colores del Atlas de Evidencias en `frontend/public/brand-mark.svg`. Los iconos de navegación y acciones salen del conjunto SVG local `DiIcon`, con trazo uniforme. Preferir icono junto a etiqueta; cada botón solo-icono conserva nombre accesible y estado. No usar emojis, fuentes de iconos externas ni dependencias de iconografía nuevas.

## Rasgo visual: ruta de evidencia

Una ruta fina conecta los estados que el producto ya conoce: selección, análisis, evaluación OSV y revisión de evidencia. El indicador del sidebar señala la sección actual; el diálogo de análisis dibuja los pasos y distingue completo, activo y pendiente. Las líneas topográficas de `frontend/public/atlas-contours.svg` y el gráfico de acceso `frontend/public/auth-network.svg` ambientan el lienzo sin presentar datos ficticios. El gráfico es ilustración y no representa repositorios, métricas ni resultados reales.

## Componentes reutilizables

- **Shell:** rail de navegación de 248 px, barra superior de 72 px y una única región principal desplazable. En móvil, la navegación abre como drawer con scrim y cierre visible.
- **Ubicación actual:** la barra superior muestra la sección activa; el sidebar mantiene el grupo, icono y estado `aria-current`.
- **Encabezado:** título conciso, descripción de la tarea cuando aporte contexto y máximo una acción primaria.
- **Área de evidencia:** una superficie por trabajo, con alcance, resumen respaldado por datos, filtros, resultados y procedencia.
- **Tablas:** encabezados claros, columnas numéricas alineadas, rutas/IDs que pueden partirse y scroll dentro de la tabla cuando sea necesario.
- **Filtros:** etiquetas persistentes, selección visible y acción para limpiar; no depender de hover.
- **Estados:** nombre más color semántico; vacíos separados para falta de repositorio, análisis, resultados o coincidencias filtradas.
- **Carga, error y cobertura:** conservar mensajes, reintentos, cancelación y etapas existentes. No inventar porcentajes, métricas ni estados.
- **Diálogo:** solo para tareas que requieren atención enfocada; conservar trap/restauración de foco y salida por Escape donde sea segura.

Mantener Angular standalone, Angular Material, CDK, RxJS, Signals y Sass. Reutilizar integraciones y comportamientos ya existentes; no cambiar contratos, sesiones, permisos ni datos.

## Estados y movimiento

Controles interactivos ofrecen reposo, hover, foco, pulsado, deshabilitado y pendiente según corresponda. Transiciones de color, borde y transformación duran 160–240 ms. Usar una entrada sutil al cambiar de vista; el movimiento de la ruta del análisis comunica progreso y no presenta porcentajes inventados. Respetar `prefers-reduced-motion` y dejar el estado final visible sin animación.

## Responsive y accesibilidad

Diseñar desde 375 px y revisar 390, 768, 1024 y 1440 px. No provocar scroll horizontal en la página; las tablas pueden desplazarse en su propio contenedor. Controles principales con objetivos de 44 × 44 px cuando el espacio lo permita. Mantener foco visible de 2–3 px, orden de teclado lógico, regiones semánticas, etiquetas persistentes, nombres accesibles, mensajes de estado anunciados, zoom y contraste WCAG 2.2 AA. El color, hover y movimiento nunca son la única señal.

## Navegación y orden de información

Conservar los destinos `?view=` y su visibilidad por rol. Agruparlos por trabajo: espacio, análisis y evidencia, conocimiento, administración. Mantener Nexo, notificaciones, idioma/tema y cuenta en la barra global. Conservar enlaces directos entre repositorio, análisis, inventario, avisos y reportes.

En vistas largas, mantener navegación por secciones disponibles bajo la introducción. Son anclas a destinos reales, no un indicador de finalización; no mostrar enlaces a estados ocultos o no disponibles. Ordenar información como tarea y alcance, acción principal, estado/cobertura, filtros, resultados y procedencia.

## Revisión de calidad

Revisar ambos temas, escritorio y móvil, contraste, foco, estados vacíos/de carga/error/parciales, y funcionamiento de controles existentes. El build no sustituye la revisión visual. La base Compose no contiene cuentas, organizaciones ni repositorios; hasta completar el bootstrap seguro, las pantallas autenticadas no deben validarse con mocks ni declararse como revisadas con datos reales.
