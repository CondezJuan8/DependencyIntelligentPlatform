# Backlog de salida del MVP

Complementa el [roadmap y sus estimaciones](ROADMAP.md). Prioridades propuestas al 14 de noviembre de 2026; el alcance y las horas semanales siguen pendientes de confirmación. Una fila se completa por su resultado verificable, no por crear paquetes o interfaces.

Estados: `Pendiente`, `En curso`, `Bloqueado`, `Terminado`. Mantener una sola entrega principal en curso por responsable.

| ID | Entrega | Prioridad | Depende de | Estado | Criterio de aceptación |
| --- | --- | --- | --- | --- | --- |
| MVP-01 | Elegir y conectar identidad | P0 | Decidir mecanismo de autenticación | Pendiente | Login y cierre de sesión funcionan; credenciales inválidas se rechazan |
| MVP-02 | Organización inicial y membresías | P0 | MVP-01 | Pendiente | Usuario accede a su organización y tiene un rol explícito |
| MVP-03 | Autorizar repositorios y scans | P0 | MVP-02 | Pendiente | Lecturas y escrituras ajenas se rechazan, incluso con IDs conocidos |
| MVP-04 | Conectar GitHub | P0 | MVP-03, accesos del proveedor | Pendiente | Conexión y desconexión controladas; permisos mínimos documentados; secretos protegidos |
| MVP-05 | Seleccionar repositorios | P0 | MVP-04 | Pendiente | Listado real y registro sin duplicados; estados vacío/error visibles |
| MVP-06 | Ejecutar scanner aislado | P0 | MVP-05, prueba técnica del ecosistema | Pendiente | Analiza un commit real con recursos y duración limitados; no ejecuta scripts arbitrarios del proyecto |
| MVP-07 | Gestionar ciclo y recuperación del scan | P0 | MVP-06 | Pendiente | Un trabajo llega a estado final y no queda RUNNING indefinidamente tras un fallo |
| MVP-08 | Importar CycloneDX | P0 | Artefacto de MVP-06 | Pendiente | SBOM válido se transforma y persiste; malformado se rechaza sin dejar un snapshot parcial |
| MVP-09 | Consultar inventario por snapshot | P0 | MVP-08 | Pendiente | Filtros/paginación y presencia por repositorio funcionan con datos reales y organización validada |
| MVP-10 | Consultar OSV y guardar hallazgos | P0 | MVP-08 | Pendiente | Evidencia por paquete/versión y fuente; errores externos se distinguen de un resultado sin hallazgos |
| MVP-11 | UI de identidad y repositorios | P0 | MVP-01 a MVP-05 | Pendiente | Usuario puede entrar, seleccionar organización y conectar/seleccionar repositorios |
| MVP-12 | UI de análisis y resultados | P0 | MVP-07, MVP-09, MVP-10 | Pendiente | Usuario solicita un análisis y ve su estado, inventario, hallazgos e historial |
| MVP-13 | Operación y aceptación del piloto | P0 | MVP-03 a MVP-12 | Pendiente | Flujo real aceptado, Docker verificado y backup/restore comprobado en un entorno privado |
| MVP-14 | Grafo navegable | P1 | MVP-09 | Pendiente | Representa relaciones del snapshot sin confundirlas con comunicación entre servicios |
| MVP-15 | Comparación de snapshots | P1 | MVP-09 | Pendiente | Identifica componentes añadidos, retirados y actualizados entre dos análisis |
| MVP-16 | Exportación CSV | P1 | MVP-09, MVP-10 | Pendiente | Exporta datos autorizados y protege frente a fórmulas interpretadas por hojas de cálculo |

P0 bloquea la salida del piloto propuesto. P1 amplía el producto después del piloto y tiene esfuerzo adicional en el roadmap. Las pruebas necesarias y los estados de carga/error de la UI forman parte de cada entrega, no son una fase opcional posterior.

## Próximo bloque de implementación

Resolver MVP-01 a MVP-03 antes de habilitar los endpoints de negocio. Separar por funcionalidad:

1. `✨ feat(identity): implementa autenticación e identidad del usuario`
2. `✨ feat(organization): crea organización inicial y membresías`
3. `✨ feat(accesscontrol): autoriza repositorios y análisis por organización`

Las elecciones de proveedor y contrato se concretan antes de desarrollar cada integración. Este backlog no autoriza despliegues ni nuevas contrataciones.

## Registro semanal

| Semana | Horas efectivas | Entregas terminadas | Bloqueos | Horas restantes revisadas |
| --- | --- | --- | --- | --- |
| Inicial | Por confirmar | Arquitectura y persistencia inicial | Identidad, scanner y capacidad semanal por concretar | 180–275 h para el piloto propuesto |

Registrar las horas reales al cerrar cada entrega. Si se consume la reserva o cambia el alcance, actualizar la fecha estimada y explicar la causa.
