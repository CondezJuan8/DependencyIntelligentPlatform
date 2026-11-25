# Auditoría de frontend — 25/11/2026

## Alcance y evidencia

Se revisaron el shell Angular, el mapa de navegación, las plantillas y hojas Sass de acceso, dashboard, repositorios, análisis, inventario, vulnerabilidades, documentación, Nexo, reportes, organización y administración. La revisión de vistas internas fue estática: la base local Compose no tiene cuentas, organizaciones ni repositorios. La app del puerto 8089 y el endpoint público `/api/auth/csrf` respondieron HTTP 200. El puerto 8088 rechazó conexión. El pipe CUA no conectó y Playwright no estaba disponible; por eso no se atribuyen interacciones autenticadas como verificadas.

## Puntuación técnica

| Dimensión | Puntuación | Evidencia |
| --- | ---: | --- |
| Accesibilidad | 3/4 | Labels, estados ARIA, foco y movimiento reducido presentes en componentes inspeccionados; no se ejecutó lector de pantalla ni recorrido de teclado real. |
| Rendimiento | 2/4 | Build exitoso; bundle inicial 646.59 KB excede el presupuesto de 500 KB y varias hojas Sass exceden 4 KB. |
| Temas | 3/4 | Tokens oficiales para oscuro/claro; no fue posible conmutar y revisar el tema claro en navegador. |
| Responsive | 2/4 | Capturas de acceso en 1440, 1024, 768, 390 y 375 px; los módulos autenticados tienen breakpoints en código, sin captura real. |
| Integridad visual | 3/4 | Navegación y composiciones por flujo conservan datos/acciones; Impeccable `detect --json --changed` devolvió `[]`. |
| **Total** | **13/20** | **Aceptable; la revisión visual autenticada sigue abierta.** |

## Hallazgos

- **P1 — revisión de recorridos internos sin completar.** No se pudo probar navegación autenticada, formularios, filtros, tablas ni persistencia con backend real debido a la base vacía y la falta de bootstrap seguro. Este es un bloqueo de evidencia, no una afirmación de defecto funcional.
- **P2 — presupuesto de carga.** Bundle inicial 646.59 KB frente a 500 KB; el build también reporta hojas Sass sobre el máximo de 4 KB. Reducir tamaño sin eliminar funciones queda pendiente.
- **P2 — tema claro sin verificación visual.** Los tokens existen; hacen falta capturas y recorrido con el tema claro, foco y movimiento reducido en las rutas internas.

## Decisiones y correcciones de esta pasada

Se mantuvieron composiciones propias por tarea en vez de imponer un grid uniforme o una plantilla externa. Análisis e inventario usan los iconos SVG locales en estados y vacíos; se corrigió el texto de carga de Documentación al idioma principal; la vista de acceso en móvil centra el bloque solo cuando hay altura suficiente y adelanta el mensaje de marca en escritorio. No se cambiaron contratos, lógica backend, API, base de datos, autenticación, autorización o integraciones.

## Resultado de validación

`docker compose build frontend`: correcto. Se actualizó únicamente el servicio `frontend`. App y endpoint público de sesión: HTTP 200. Impeccable: cinco capturas responsive de acceso y detector sin observaciones. Sin mocks ni dependencias nuevas. La validación de las rutas internas requiere bootstrap administrativo y datos autorizados.