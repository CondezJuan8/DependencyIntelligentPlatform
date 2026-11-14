# Backlog de salida del MVP

Complementa el [roadmap y sus estimaciones](ROADMAP.md). Confirmado por el usuario: **25 horas semanales y demo local para presentar como primera salida**. Una fila se completa por su resultado verificable, no por crear paquetes o interfaces.

Estados: `Pendiente`, `En curso`, `Bloqueado`, `Terminado`. Mantener una sola entrega principal en curso por responsable.

## Primera salida: demo local

Estos elementos tienen prioridad sobre el piloto. Plazo solicitado: **dos semanas / 50 horas**, distribuidas en **40 h de trabajo y 10 h de reserva**. Consultar [las funciones F01–F09 y el calendario diario](FUNCIONES_DEMO_DOS_SEMANAS.md), que concretan este backlog.

| ID | Entrega | Depende de | Estado | Criterio de aceptación |
| --- | --- | --- | --- | --- |
| DEMO-01 | Escenario y SBOM de ejemplo — F01 | Verificar arranque completo en equipo de presentación | En curso | Perfil/bootstrap y ejemplos Maven CycloneDX 1.6 preparados y probados en PostgreSQL; falta validar Compose/navegador |
| DEMO-02 | Carga, validación y persistencia — F02–F04 | DEMO-01 | Pendiente | Carga manual desde la interfaz, snapshot y componentes reales; archivo inválido no deja datos parciales |
| DEMO-03 | Consulta local de inventario — F05 | DEMO-02 | Pendiente | Devuelve componentes del snapshot con búsqueda y paginación; no abre globalmente los endpoints de negocio |
| DEMO-04 | Pantalla, detalle y ensayo — F06–F09 | DEMO-03 | Pendiente | Muestra inventario, detalle/procedencia y estados vacío/error; guion y arranque reproducibles |

Avance de DEMO-02: **F03 (parser) implementada y validada de forma unitaria**. F02 (carga) y F04 (persistencia de la importación) siguen pendientes; por eso la entrega completa continúa pendiente.

No se bloquea la demo por login multiusuario, GitHub, scanner u OSV. No se mostrará como integración ejecutada algo que no se haya implementado. El parser y la persistencia deben poder reutilizarse en MVP-08/MVP-09.

## Después de la demo: piloto

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

Resolver DEMO-01 y avanzar con importación, consulta y pantalla. Commits propuestos por funcionalidad, cada uno con su validación necesaria:

1. `✨ feat(sbom): importa y persiste un snapshot CycloneDX local`
2. `✨ feat(inventory): consulta el inventario del snapshot de demostración`
3. `✨ feat(frontend): presenta el inventario importado en la demo local`

Al cerrar la demo, reestimar el piloto. Resolver MVP-01 a MVP-03 antes de habilitar el acceso de negocio para usuarios del piloto.

Las elecciones de proveedor y contrato se concretan antes de desarrollar cada integración. Este backlog no autoriza despliegues ni nuevas contrataciones.

## Registro semanal

| Semana | Horas efectivas | Entregas terminadas | Bloqueos | Horas restantes revisadas |
| --- | --- | --- | --- | --- |
| Inicial | Capacidad: 25 h/semana; consumidas: por registrar | Arquitectura y persistencia inicial | Elegir escenario/SBOM y fecha de inicio | Presupuesto de demo: 50 h en dos semanas; piloto se reestima después |

Registrar las horas reales al cerrar cada entrega. Si se consume la reserva o cambia el alcance, actualizar la fecha estimada y explicar la causa.
