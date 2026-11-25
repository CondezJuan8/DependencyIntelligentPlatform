# Dependency Intelligence — Observatorio de Evidencias

La guía «PORFAVOR» del paquete visual entregado el 25 de noviembre de 2026 define la referencia. Las 14 pantallas y 13 variantes muestran tareas para equipos de ingeniería y seguridad. Las cifras, personas y fuentes que aparecen en el material ilustran la composición. Angular presenta datos procedentes de la sesión y de sus repositorios autorizados.

## Temas

Los roles semánticos viven en `frontend/src/theme/_tokens.scss` y `frontend/src/theme/_appearance.scss`. El tema oscuro es la preferencia personal inicial. Se conservan las opciones claro, oscuro y del dispositivo, y la preferencia de la organización activa.

| Rol | Oscuro | Claro |
| --- | --- | --- |
| Fondo de página | `#101722` | `#F2EEE6` |
| Navegación y encabezado | `#0E1520` | `#E8E2D7` |
| Superficie de panel | `#161F2E` | `#FBF8F1` |
| Borde de superficie | `#2D394B` | `#D8CFC1` |
| Texto principal | `#F5F2EB` | `#25281F` |
| Texto secundario | `#ABB7C8` | `#555C59` |
| Énfasis y acción primaria | `#F2BC79` | `#795018` para texto, `#F2BC79` para botón |
| Resumen prioritario | `#F1DDBF` | `#F1DDBF` |

Open Sans se sirve desde los recursos locales con licencia SIL OFL. Versiones, commits, rutas y códigos usan una pila monoespaciada. Los estados llevan etiquetas con texto. El ámbar orienta a la acción primaria o a una decisión pendiente. La navegación usa 222 px en escritorio y pasa a un panel lateral por debajo de 980 px. La separación de contenido en escritorio parte de 30 px.

## Pantallas y recorridos

| Referencias | Implementación |
| --- | --- |
| 03, 23–25 | Shell Angular con encabezado, espacios y destinos según la cuenta, menú móvil, notificaciones y búsqueda modal por nombre, Ctrl/Cmd+K, flechas, Enter y Escape. |
| 01–02, 09, 13–17, 20–22, 27 | Acceso y variantes, estados separados de correo y admisión, invitaciones y permisos, perfil y preferencias, y administración global con motivo y auditoría. |
| 05–07, 18–19 | GitHub App y carga local con sus estados separados, inspección manual Maven/npm, historial, comparación y detalle de inventario en panel lateral con procedencia. |
| 04, 08, 12, 26 | Centro de revisión con resumen en papel, cobertura OSV, seguimiento humano de avisos y reportes con alcance, fecha y procedencia en vista previa y exportaciones. |
| 10–11 | Biblioteca con alcance de organización, versiones, descarga y búsqueda textual. Nexo permite acotar la consulta a un repositorio y muestra la versión citada para descarga autorizada. |

Los contenedores existentes comparten tokens de superficies, controles, foco, diálogo, estado y alerta. Revisa reflujo a 1440 y 390 px, interacciones por teclado y movimiento reducido. El registro de verificación y sus límites viven en la [bitácora](docs/BITACORA_TRABAJO.md) y en [pendientes](docs/PENDIENTES.md).
