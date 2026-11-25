# PORFAVOR · Guía para las cinco skills de Codex

Usa `PORFAVOR_Plantillas_Visuales.pdf` como referencia de diseño y `PORFAVOR_Plantillas.html` para navegar por las pantallas. Las personas, repositorios y avisos del prototipo son ejemplos. Antes de implementar, abre la versión vigente de `DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md`, `DESIGN.md` y los componentes Angular de tu rama.

## Distribución propuesta

| Skill | Pantallas a cargo | Entrega |
| --- | --- | --- |
| 1. Sistema visual y navegación | 03 Área de trabajo global | Tokens claro/oscuro, componentes compartidos, navegación y buscador de secciones, menú móvil y notificaciones. |
| 2. Identidad y permisos | 01 Acceso y registro, 02 Estado de la cuenta, 09 Organización, 13 Configuración y 14 Administración | Flujos de identidad, roles y acciones disponibles, gestión de accesos y auditoría. |
| 3. Repositorios y análisis | 05 Mis repositorios, 06 Análisis y 07 Inventario | Conexión e importación autorizada, ejecución manual, resultados por ecosistema, comparación, catálogo y detalle. |
| 4. Revisión OSV y reportes | 04 Centro de revisión, 08 Vulnerabilidades y 12 Reportes | Cobertura, avisos priorizados, seguimiento, filtros e informes con procedencia y parcialidad. |
| 5. Documentación y Nexo | 10 Documentación y 11 Nexo | Biblioteca, versiones, búsqueda de texto, preparación de documentos, citas y copia de propuestas. |

## Acuerdos visuales compartidos

| Elemento | Oscuro | Claro |
| --- | --- | --- |
| Fondo de página | `#101722` | `#F2EEE6` |
| Navegación y encabezado | `#0E1520` | `#E8E2D7` |
| Superficie de panel | `#161F2E` | `#FBF8F1` |
| Borde | `#2D394B` | `#D8CFC1` |
| Texto principal | `#F5F2EB` | `#25281F` |
| Texto secundario | `#ABB7C8` | `#555C59` |
| Ámbar para énfasis y acción primaria | `#F2BC79` | `#795018` para texto y `#F2BC79` para el botón |
| Papel para el resumen prioritario | `#F1DDBF` | `#F1DDBF` |

- Usa Open Sans en la interfaz y una monoespaciada para versiones, commits, rutas y códigos. En escritorio, usa 222 px de navegación y márgenes de 30 px como punto de partida. El menú pasa a lateral por debajo de 980 px.
- Comparte los componentes de encabezado, botón, filtro, tabla, aviso, etiqueta, diálogo y panel de detalle. Reserva el ámbar para la acción principal o un dato pendiente que requiere atención.
- Añade etiquetas a los estados, foco visible por teclado y estados vacíos con una acción o explicación concreta. Conserva los filtros al volver de un detalle cuando el flujo lo permita. Revisa los tamaños de 1440 y 390 px.
- Define en la skill 1 los componentes compartidos antes de que las otras cuatro skills modifiquen navegación o estilos globales. La skill 2 aporta el contexto de autorización y la 3 aporta el contrato de detección y snapshots. Las skills 4 y 5 consumen esos contratos para sus pantallas.

## Instrucción para la skill 1 · Sistema visual y navegación

> Implementa la plantilla 03 y revisa las variantes 23, 24 y 25. Aplica el sistema visual a los componentes compartidos de Angular. El buscador abre secciones por nombre con teclado. La barra superior da acceso a idioma, tema, notificaciones, Nexo, perfil, configuración y sesión. El menú lateral debe ocultar opciones fuera del rol efectivo. Muestra el aviso de verificación cuando corresponda. Verifica escritorio, móvil, tema claro y oscuro y foco al abrir y cerrar el menú o la búsqueda.

## Instrucción para la skill 2 · Identidad y permisos

> Implementa las plantillas 01, 02, 09, 13 y 14 con sus variantes. Trata la verificación del correo, la admisión de plataforma y la aprobación de una invitación como decisiones independientes. Las invitaciones requieren el correo coincidente y verificado antes de aceptar. Los proveedores externos aparecen cuando están configurados y la vinculación requiere autenticación reciente. Respeta el rol efectivo para miembros, equipos y accesos por repositorio. Reserva Administración a su permiso global y exige un motivo registrado para las acciones sensibles. Verifica los estados pendiente, aprobado y rechazado, además de cuenta sin verificar, rol lector y proveedor desactivado.

## Instrucción para la skill 3 · Repositorios y análisis

> Implementa las plantillas 05, 06 y 07 y los detalles 18 y 19. La GitHub App concede acceso a las fuentes de la organización. Distingue importación local, sincronización, selección de repositorios y retiro. Separa progreso de carga de estado de análisis: solo muestra porcentaje cuando la carga lo informa. Mantén ejecución manual, historial Maven/npm, cobertura completa o parcial, error y sin manifiesto. Compara dos snapshots completados del mismo repositorio. El inventario separa directas y transitivas y permite consultar OSV de forma independiente. Presenta procedencia y exposición inconclusa cuando falte evidencia.

## Instrucción para la skill 4 · Revisión OSV y reportes

> Implementa las plantillas 04, 08 y 12. El Centro de revisión resume solo repositorios accesibles y conduce al inventario o al historial correspondiente. En Vulnerabilidades, conserva la relación de cada versión corregida con el paquete afectado, muestra referencias y fechas y guarda el seguimiento del equipo como un estado independiente. Resuelto significa que se cerró el seguimiento. La verificación técnica requiere evidencia posterior. El informe ejecutivo resume alcance y prioridades. El técnico muestra paquete, versión, repositorio y evidencia OSV. Conserva procedencia, filtros, cobertura, truncamiento si aplica y fecha en la vista previa y las exportaciones.

## Instrucción para la skill 5 · Documentación y Nexo

> Implementa las plantillas 10 y 11. La biblioteca permite subir, versionar, descargar, buscar en el texto y gestionar documentos según permisos. Separa la preparación del texto de la preparación para búsqueda de Nexo. Conserva el alcance de organización y repositorio. Cada cita debe llevar documento, archivo, página o sección y fragmento. Si falta evidencia autorizada, muestra el estado sin respuesta sustentada y conserva la opción de preparar documentos. El prompt propuesto se copia como texto para revisión humana. Nexo opera en modo de solo lectura.

## Revisión conjunta

| Recorrido | Resultado esperado |
| --- | --- |
| Acceso → cuenta → invitación | Correo, admisión y aprobación de invitación conservan estados propios. |
| GitHub App → repositorio → análisis → inventario | La fuente autorizada, el repositorio y la procedencia se mantienen al navegar. |
| Inventario → aviso → reporte | La versión, el estado OSV y el alcance coinciden en las tres pantallas. |
| Organización → equipo → repositorio | Las acciones disponibles siguen el rol y la autorización efectiva. |
| Documentación → Nexo → cita | La cita abre la fuente autorizada y conserva su localización. |
| Administración → acción con motivo → auditoría | La confirmación exige un motivo y la auditoría muestra el registro asociado. |
