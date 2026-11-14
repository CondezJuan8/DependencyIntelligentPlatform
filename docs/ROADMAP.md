# Roadmap de Dependency Intelligence

Fecha de revisión: 14 de noviembre de 2026. Estimación inicial basada en el código actual; no es una fecha de entrega comprometida.

## Objetivo y capacidad confirmados

Primera salida confirmada por el usuario: **demo local para presentar**. Flujo de la presentación: importar manualmente un SBOM CycloneDX conocido → guardar el snapshot → consultar su inventario en una interfaz sencilla. Se trabaja con un repositorio de ejemplo y un ecosistema para acotar el alcance.

Capacidad confirmada: **25 horas efectivas por semana**, una persona responsable del proyecto asistida por Codex. Las horas incluyen implementación, revisión, depuración, pruebas necesarias y documentación breve. El trabajo de Codex se considera asistencia dentro de esa capacidad; no se asume que elimina el tiempo de integración o validación. El inicio de ejecución todavía debe fijarse para convertir las semanas relativas en fechas de calendario.

## Primera entrega: demo local en dos semanas

Plazo solicitado por el usuario: **dos semanas con 25 horas semanales, 50 horas totales**. Se fija un presupuesto de **40 horas de trabajo y 10 horas de reserva**. La estimación previa de 40–60 horas / 2–3 semanas queda sustituida para esta demo por un alcance cerrado.

El documento de ejecución es [Funciones de la demo en dos semanas](FUNCIONES_DEMO_DOS_SEMANAS.md): funciones F01–F09, criterios de aceptación, calendario de diez jornadas y comprobaciones necesarias.

Flujo obligatorio: cargar manualmente un SBOM CycloneDX JSON de ejemplo → validar → persistir snapshot y componentes → consultar y buscar inventario → mostrar detalle/procedencia. Se trabaja con una organización y un repositorio locales; el acceso de demo no abre globalmente los endpoints de negocio.

Semana 1: escenario, carga, parser, persistencia y consulta de backend (25 h). Semana 2: tabla Angular, detalle, errores, documentación y verificación (15 h), más reserva (10 h). La reserva absorbe correcciones; no se utiliza para añadir funcionalidades.

Quedan fuera GitHub, scanner automático, login multiusuario, OSV, grafo, comparación, IA y despliegue público. Si se añade una función obligatoria, sustituir esfuerzo equivalente o revisar el plazo. Es un objetivo de entrega con alcance acotado, no una garantía del proyecto completo.

## Estado comprobado

| Área | Estado | Evidencia / límite |
| --- | --- | --- |
| Arquitectura | Base implementada | Tres módulos Maven: Domain, Application e Infrastructure |
| Persistencia | Esquema inicial implementado | 13 tablas, Flyway V1/V2, integridad por organización y snapshot; no equivale a tener todos los casos de uso |
| Repositorios | Parcial | Registro y consulta con persistencia; endpoints bloqueados hasta implementar autorización |
| Análisis | Parcial | Solicitudes persistidas en PENDING y reglas del agregado; falta ejecutor |
| Autenticación y permisos | Pendiente | No hay identidad ni validación operativa de membresías |
| GitHub, scanner, SBOM y OSV | Pendiente | Falta implementar y validar las integraciones |
| Frontend | Estructura inicial | Pantalla de presentación; rutas de funcionalidades todavía vacías |
| Despliegue | Solo configuración local | Compose definido; construcción Docker todavía sin validar en el entorno utilizado |

No se asigna un porcentaje global: contar carpetas, tablas o pruebas como funcionalidades completas daría una impresión incorrecta del avance.

## Etapa posterior: referencia de esfuerzo del piloto

El piloto privado ya no es la primera entrega. Su objetivo posterior es permitir login, conexión GitHub, análisis manual con scanner, inventario, vulnerabilidades e historial por organización. La siguiente estimación conserva la línea base anterior a la demo: se recalculará al terminarla, descontando trabajo reutilizable y añadiendo lo que aparezca. No debe sumarse automáticamente el total de la demo al del piloto.

| Orden | Entrega | Esfuerzo | Condición de finalización |
| --- | --- | --- | --- |
| 1 | Identidad, organizaciones y permisos | 24–36 h | Usuario autenticado, organización inicial y acceso según membresía; acceso ajeno rechazado |
| 2 | GitHub y selección de repositorios | 16–24 h | Conectar, listar y seleccionar repositorios con credenciales protegidas y errores controlados |
| 3 | Ejecución manual de análisis | 24–36 h | Worker lleva PENDING a RUNNING y a un estado final; aislamiento del scanner, límites de tiempo y recuperación de trabajos interrumpidos |
| 4 | Importación de SBOM e inventario | 16–24 h | Componentes y relaciones guardados por snapshot con commit y procedencia; errores de formato controlados |
| 5 | Vulnerabilidades con OSV | 12–20 h | Hallazgos trazables por versión; timeout o ausencia de cobertura no se presenta como ausencia de riesgo |
| 6 | Interfaz utilizable | 32–48 h | Login, repositorios, solicitud/estado de análisis, inventario, vulnerabilidades e historial conectados a datos reales |
| 7 | Preparación del piloto | 20–32 h | Flujo completo probado, Docker validado, entorno privado configurado, secretos externos al repo, backup y restauración comprobados |
| | **Subtotal** | **144–220 h** | |
| | **Reserva del 25 %** | **36–55 h** | Integraciones, defectos e incertidumbre |
| | **Total de planificación** | **180–275 h** | Alcance del piloto descrito arriba |

La tabla se suma de forma conservadora: no descuenta trabajo paralelo. La interfaz de cada función debe avanzarse junto con su backend cuando el contrato esté definido; el orden expresa dependencias, no una obligación de dejar toda la UI al final.

| Dedicación efectiva | Piloto estimado, incluida la reserva |
| --- | --- |
| 10 h/semana | 18–28 semanas |
| 25 h/semana — capacidad confirmada | 8–11 semanas |
| 40 h/semana | 5–7 semanas |

Semanas de trabajo sostenido, sin descontar festivos, vacaciones o interrupciones. Si se añaden personas, no se divide automáticamente el esfuerzo: hay integración y coordinación.

## Hitos que permiten mostrar avances

| Hito | Momento orientativo a 25 h/semana | Qué se puede demostrar |
| --- | --- | --- |
| Base técnica | Disponible | Separación de capas y persistencia inicial |
| Demo local de presentación | 2 semanas / 50 h | Importar manualmente un SBOM conocido y consultar inventario local con una interfaz sencilla |
| Primer análisis con scanner | Referencia anterior: 4–6 semanas; reestimar tras la demo | Repositorio → scanner → SBOM → inventario |
| Piloto privado | Referencia anterior: 8–11 semanas; reestimar tras la demo | Flujo autorizado completo con vulnerabilidades, historial y operación básica |

La demo es ahora la primera entrega elegida. Las fechas del piloto y del lanzamiento público se revisarán con las horas reales consumidas y el trabajo reutilizado; no se mantiene una promesa de fecha para esas etapas.

## Después del piloto

Estimaciones adicionales independientes; no están incluidas en las 180–275 h.

| Ampliación | Esfuerzo orientativo | Dependencia |
| --- | --- | --- |
| Grafo navegable, comparación básica entre snapshots y exportación CSV | 24–40 h | Snapshots consistentes e inventario real |
| Preparación de lanzamiento público | 60–100 h + 25 % de reserva | Piloto validado: operación, recuperación, observabilidad, cuotas y revisión de seguridad según alcance |
| Documentación e IA/RAG | Por estimar tras definir un caso de uso | Fuentes, permisos, recuperación y criterios de evaluación definidos |
| Simulación de cambios, SSO y capacidades empresariales | Fuera de esta versión | Validación del piloto y requisitos concretos |

Referencia anterior para lanzamiento público con el alcance funcional del piloto: **255–400 h**, aproximadamente **11–16 semanas a 25 h/semana**, antes de considerar una ventana de uso y feedback. Reestimar después de la demo. Incluye la reserva de las etapas de piloto y lanzamiento; no incluye cobros, requisitos contractuales, cumplimiento específico, RAG ni ampliaciones del grafo. No se despliega ni se contrata infraestructura como parte de este documento.

## Decisiones que pueden mover la estimación

- Confirmados: 25 h/semana y demo local como primera salida. Pendientes: fecha de inicio y escenario/SBOM de presentación.
- Elegir el mecanismo de autenticación para el piloto; no bloquea el parser de la demo.
- Propuesta para acotar el piloto: un ecosistema inicial y un conjunto pequeño de repositorios; elegir Maven o npm después de una prueba técnica. La elección todavía no está confirmada.
- Validar primero scanner, resolución de dependencias privadas y SBOM en repositorios reales. Es el principal riesgo técnico del camino crítico.
- Mantener análisis manual como primera versión; webhooks, monorepos complejos y muchos ecosistemas requieren estimación adicional.
- Los cambios de alcance y la espera de accesos, servicios o decisiones no están absorbidos ilimitadamente por la reserva.

## Seguimiento y pruebas

Actualizar semanalmente horas consumidas, entregas aceptadas, bloqueos y estimación restante. Reestimar al terminar la demo y después del primer análisis con scanner; esa evidencia vale más que el rango inicial.

Probar solo lo necesario para cada cambio: reglas de negocio modificadas, autorización y aislamiento cuando se toquen, integración real de persistencia o proveedor cuando corresponda y un recorrido completo antes del piloto. Repetir una verificación si cambia el código relevante, falla una prueba o queda una duda concreta.

Crear normalmente 2–3 commits por bloque que incluya varias funcionalidades, con al menos un commit coherente por funcionalidad y su validación relevante. Usar siempre emoji y Conventional Commits. No dividir artificialmente una función para alcanzar una cifra.
