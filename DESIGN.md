# Dependency Intelligence — sistema visual

## Dirección: Observatorio de Evidencias

Dependency Intelligence es una mesa de revisión para rastrear una dependencia desde su origen hasta una decisión respaldada. El usuario debe poder responder sin buscar en varias pantallas: qué repositorio y análisis está viendo, qué hallazgo requiere atención, con qué evidencia cuenta y cuál es el siguiente paso disponible.

La identidad propia combina un cielo de trabajo azul tinta, papel mineral en el tema claro y marcas de latón para orientar la mirada. La metáfora de observatorio se limita a la ilustración de acceso y a pequeños marcadores de ruta: las vistas operativas siguen siendo precisas, legibles y basadas en datos reales. La forma distintiva del producto aparece en la composición editorial del dashboard, la navegación por tareas y los registros de evidencia. Las referencias Berry, Angular Dashboards y Corona se usaron para estudiar patrones de navegación, jerarquía y densidad; no se reutilizó su código, composición literal ni identidad.

El tema oscuro es predeterminado. Se conservan los modos claro y sistema. El diseño sirve por igual a ingeniería y seguridad; las prioridades se expresan en la evidencia y el lenguaje, no en interfaces separadas por rol.

## Identidad y color

Los tokens viven en `frontend/src/theme/_tokens.scss` y `frontend/src/theme/_appearance.scss`. Los componentes deben consumirlos en lugar de fijar colores de marca localmente.

| Función | Claro | Oscuro | Uso |
| --- | --- | --- | --- |
| Tinta / navegación | `#182333` | `#101722` | Sidebar, marca y superficies de contexto |
| Fondo de trabajo | `#F0EFE8` | `#111923` | Lienzo principal |
| Superficie | `#F9F8F2` | `#192431` | Formularios, paneles y áreas de trabajo |
| Superficie elevada | `#FFFDF7` | `#303D4C` | Menús, popovers y diálogos |
| Texto principal | `#1D2732` | `#F1EFE8` | Títulos, tablas y contenido |
| Texto secundario | `#435263` | `#CED5D9` | Contexto, instrucciones y metadatos |
| Borde de control | `#74808A` | `#81909D` | Límites legibles de controles |
| Acción / anotación | `#8A511E` | `#F0B45F` | Acción primaria, selección y atención |
| Éxito | `#337756` | `#7CC69D` | Estado confirmado |
| Advertencia | `#A66A22` | `#EFBD6B` | Precaución |
| Riesgo alto | `#AD593C` | `#E99271` | Atención alta |
| Crítico | `#B44749` | `#ED7773` | Atención crítica |

Los colores semánticos siempre van acompañados por etiqueta o explicación. No usar el acento como decoración ni como única señal de prioridad. Revisar contraste de texto, borde y estados en ambos temas con WCAG 2.2 AA.

## Tipografía y ritmo

Usar IBM Plex Sans, servida desde los archivos locales incluidos. No descargar fuentes. Títulos sobrios, cifras alineadas con numerales tabulares y texto de código en una pila monoespaciada del sistema.

| Nivel | Tamaño / peso |
| --- | --- |
| Título de página | 26–32 px / 600 |
| Título de sección | 18–21 px / 600 |
| Título de panel o fila | 15–17 px / 500–600 |
| Texto y control | 14–16 px / 400–600 |
| Tabla, etiqueta y metadato | 12–14 px / 400–500 |

Ritmo base de 4 px: `4, 8, 12, 16, 20, 24, 32, 40`. Usar gutters de 24–32 px en escritorio y 16 px en móvil. Los controles son compactos pero táctiles; paneles casi cuadrados evitan la apariencia de kit de tarjetas. Radio de 4 px en controles, 3 px en superficies operativas y hasta 8 px en diálogos. Reservar sombras para elementos flotantes que deban separarse del flujo.

## Navegación y arquitectura de información

Mantener destinos y visibilidad por permisos existentes. Organizar el sidebar por intención: **Espacio** (dashboard y repositorios), **Analizar** (análisis e inventario), **Revisar** (vulnerabilidades y reportes), **Conocimiento** (documentos y asistente), y **Equipo y cuenta** (organización y preferencias). Mantener Nexo, notificaciones, idioma/tema y cuenta en la barra superior global.

La ubicación actual debe ser visible en la navegación y el encabezado. Los enlaces de salto son anclas a secciones que existen, con nombre claro, foco de teclado y destino resaltado. Las vistas largas ordenan el contenido así: alcance y tarea, acción principal, cobertura/estado, filtros, resultados y procedencia. Priorizar relaciones y siguientes pasos antes de detalles técnicos secundarios.

## Patrones reutilizables

- **Shell:** sidebar estable en escritorio, barra global compacta y una región de contenido. En móvil, el sidebar se convierte en drawer con scrim, botón de cierre y foco accesible.
- **Encabezado de vista:** título en escala de producto, contexto breve si ayuda, y acción principal visible sin competir con el título.
- **Dashboard:** mesa de trabajo asimétrica que antepone el registro OSV real y los próximos hallazgos priorizados; métricas solo cuando proceden del backend. Las listas enlazan directamente con su revisión.
- **Registros y tablas:** encabezados explícitos, datos relacionados juntos, columnas numéricas alineadas y rutas/identificadores que se parten. El desplazamiento horizontal, si es necesario, queda dentro del contenedor de tabla.
- **Filtros:** etiquetas persistentes, valor seleccionado visible y forma clara de limpiar. El resultado vacío diferencia falta de datos de cero coincidencias.
- **Estados:** carga, error, reintento, vacío, parcial y éxito tienen mensaje y acción pertinente. Nunca presentar resultado faltante o incompleto como seguro.
- **Superficies:** una superficie por área de trabajo. Evitar tarjetas anidadas, paneles duplicados y bordes alrededor de cada fila cuando el espacio y alineación bastan.
- **Iconografía:** usar el set SVG local `DiIcon`, con trazo consistente. Acompañar iconos de navegación con texto; los botones solo con icono necesitan nombre accesible. No introducir emojis ni dependencias externas de iconos.
- **Diálogos:** reservar para decisiones enfocadas; preservar la gestión existente de foco, Escape, cancelación y estados de envío.

Las ilustraciones del acceso son SVG originales y decorativas, no representan métricas ni repositorios. No llenar espacios con datos inventados. Los módulos todavía no implementados deben seguir marcados como **En preparación**.

## Interacción y movimiento

Todo control mantiene estados de reposo, hover, foco visible, pulsado, deshabilitado y cargando. Las transiciones de color y posición duran 160–240 ms. Usar aparición corta y escalonada en los elementos prioritarios del primer plano; no animar cada tarjeta o fila repetidamente. La ilustración de acceso puede tener movimiento sutil. El movimiento comunica entrada, selección o progreso real, nunca sustituye etiquetas ni muestra porcentajes ficticios.

Respetar `prefers-reduced-motion` con duración mínima o cero y conservar visible el estado final. No añadir destellos, RGB, parallax ni movimiento continuo a las superficies de trabajo.

## Diseño adaptable y accesibilidad

Diseñar y revisar a 375, 390, 768, 1024 y 1440 px. Reorganizar en lugar de reducir proporcionalmente; ocultar la ilustración de acceso cuando compita con el formulario. No permitir scroll horizontal del documento. Asegurar controles de al menos 44 × 44 px cuando su disposición lo permita, zoom, foco de 2–3 px, navegación por teclado, landmarks semánticos, labels persistentes y estado/error anunciados. No depender de color, hover o animación para explicar un resultado. Preservar pegado y autocompletado en acceso.

## Restricciones de producto

Mantener Angular standalone, Angular Material, CDK, RxJS, Signals y Sass. Reutilizar las integraciones existentes y respetar navegación, permisos, datos y acciones ya conectados. No cambiar API, autenticación, autorización, backend, base de datos o framework por razones visuales. No añadir dependencias sin necesidad demostrada. No inventar métricas ni sustituir respuestas reales por mocks.

## Validación

Compilar frontend, revisar capturas en todos los breakpoints, probar tema claro/oscuro, foco, reduced motion y estados disponibles. Revisar las interacciones con datos reales antes de declarar validados filtros, tablas, gráficas o conexiones de módulos autenticados. La base Compose de esta revisión no tiene cuentas, organizaciones ni repositorios; por ello, la inspección visual autenticada continúa pendiente del bootstrap seguro y datos autorizados. El build correcto por sí solo no cierra la revisión visual.
