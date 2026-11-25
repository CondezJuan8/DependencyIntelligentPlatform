# Dependency Intelligence — Evidence Desk

## Dirección

La interfaz acompaña una revisión verificable: fuente autorizada → snapshot observado → evaluación OSV. La persona necesita decidir qué revisar y volver al origen del dato sin confundir una consulta pendiente con seguridad confirmada.

Se consideraron tres direcciones:

- **Mesa de evidencia:** lista priorizada de avisos y procedencia, con lectura compacta y acciones junto a la evidencia.
- **Observatorio de riesgo:** panorama de severidades y actividad; se descartó como estructura principal porque sugiere vigilancia continua y tendencias que el producto no ofrece.
- **Consola de ingeniería:** densidad alta y muchas superficies; se descartó porque convertiría cada módulo en un panel operativo genérico.

La mesa de evidencia guía el Dashboard, el acceso y las vistas de revisión. Sus referencias son el registro de cambios de software y la lectura de informes técnicos, sin imitar papel, una consola ni una herramienta conocida. La navegación de trabajo permanece predecible; la identidad está en la relación visible entre fuente, snapshot, estado y siguiente acción.

## Principios de composición

- Cada vista expresa su tarea: acceso, conexión de fuentes, ejecución, catálogo, triage, biblioteca, conversación, reporte o administración.
- El Dashboard usa una cola de evidencia, fuentes accesibles y registro de análisis; no inventa tendencias ni muestra estadísticas sin datos.
- Las cifras resumen se alinean en registros, no en una cuadrícula de tarjetas. Listas y tablas usan separadores, agrupación y jerarquía antes que contenedores anidados.
- Los controles de una fila permanecen junto al dato al que afectan. Una acción solo aparece cuando su handler real y sus permisos la respaldan.
- La navegación agrupa destinos por intención y conserva búsqueda global, Nexo, avisos, preferencias y cuenta.
- **En preparación** identifica rutas que aún no ofrecen una función completa.

## Color y temas

Los roles semánticos se implementan en `frontend/src/theme/_tokens.scss` y `frontend/src/theme/_appearance.scss`. El selector existente persiste la preferencia de usuario/organización/sistema; oscuro es el inicio.

| Rol | Claro | Oscuro | Uso |
| --- | --- | --- | --- |
| Lienzo | `#F4F5F8` | `#0C1018` | Área general |
| Superficie | `#FFFFFF` | `#131925` | Formularios, lectura y listas |
| Superficie secundaria | `#EAEDF3` | `#1D2635` | Selección y agrupación |
| Navegación | `#10131B` | `#0A0D13` | Índice y marca |
| Texto | `#171C29` | `#F1F3F8` | Contenido |
| Acción | `#5148E5` | `#AAA4FF` | Acción primaria y foco |
| Señal | `#087F99` | `#75D8E5` | Procedencia, actividad y Nexo |
| Éxito | `#13835B` | `#5ED5A1` | Estado confirmado con etiqueta |
| Precaución | `#A96712` | `#F1BB68` | Estado parcial o por revisar |
| Riesgo alto | `#CF573C` | `#FF9A77` | Clasificación OSV alta |
| Riesgo crítico | `#C52F50` | `#FF6D88` | Clasificación OSV crítica |

Indigo y cian son señales puntuales, no fondos ni texto degradado. Riesgo, cobertura y resultado también se expresan mediante etiquetas. No hay resplandor permanente: una consulta solo comunica actividad cuando el backend indica trabajo en curso.

## Tipografía, forma y movimiento

- IBM Plex Sans local para la interfaz; numerales tabulares para conteos; pila monoespaciada solo para código, versiones y hashes.
- Base espacial de 4 px; márgenes de contenido adaptables. Radios pequeños en controles; filas y secciones usan líneas completas o espacio.
- Sombras para popovers y diálogos, no para elevar cada contenido.
- Iconos SVG del componente `DiIcon`; ningún glifo o emoji como icono.
- Transiciones de 160–240 ms para foco, selección y apertura. Entrada puntual en acceso; estados finales siguen disponibles con movimiento reducido.
- Selección, foco, desplazamiento y texto de código usan los tokens del tema.

## Adaptación y accesibilidad

La navegación se convierte en drawer en móvil. La evidencia apila origen, detalle y acción; las listas conservan lectura lineal. Revisar 375, 390, 768, 1024, 1440 y 1920 px antes de declarar una ruta validada.

Mantener landmarks, labels, teclado, foco visible, zoom, estados `role=status/alert`, contraste WCAG AA y `prefers-reduced-motion`. El acceso conserva `autocomplete`, pegado, pestañas accesibles, validación y flujos de contraseña y proveedor.

## Límites funcionales

Preservar Angular standalone, Material/CDK, TypeScript, RxJS y Sass. No cambiar API, autenticación, autorización, backend, base de datos ni datos reales. El análisis es manual; la información OSV representa evidencia guardada y cobertura observada, no una conclusión de seguridad.

La base Compose actual está vacía. Sin bootstrap y registros autorizados no se validan visualmente las rutas autenticadas ni sus datos; no se deben rellenar con mocks.
