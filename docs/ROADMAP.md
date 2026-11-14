# Roadmap de Dependency Intelligence

Fecha de revisión: 14 de noviembre de 2026. Estimación inicial basada en el código actual; no es una fecha de entrega comprometida.

## Objetivo y supuesto de capacidad

Primera salida propuesta: **piloto privado para pocos usuarios** que puedan iniciar sesión, conectar un repositorio GitHub, solicitar un análisis manual y consultar inventario, vulnerabilidades e historial con aislamiento por organización.

Referencia provisional: una persona responsable del proyecto, asistida por Codex, con **25 horas efectivas por semana**. Las horas incluyen implementación, revisión, depuración, pruebas necesarias y documentación breve. El trabajo de Codex se considera asistencia dentro de esa capacidad; no se asume que elimina el tiempo de integración o validación. Confirmar disponibilidad semanal y tipo de primera salida antes de fijar una fecha.

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

## Estimación restante del piloto

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
| 25 h/semana — referencia provisional | 8–11 semanas |
| 40 h/semana | 5–7 semanas |

Semanas de trabajo sostenido, sin descontar festivos, vacaciones o interrupciones. Si se añaden personas, no se divide automáticamente el esfuerzo: hay integración y coordinación.

## Hitos que permiten mostrar avances

| Hito | Momento orientativo a 25 h/semana | Qué se puede demostrar |
| --- | --- | --- |
| Base técnica | Disponible | Separación de capas y persistencia inicial |
| Demo acotada | 2–3 semanas | Importar manualmente un SBOM conocido y consultar inventario local con una interfaz sencilla |
| Primer análisis real | 4–6 semanas | Repositorio → scanner → SBOM → inventario; escenario acotado y todavía sin garantías de piloto |
| Piloto privado | 8–11 semanas | Flujo autorizado completo con vulnerabilidades, historial y operación básica |

La demo acotada es una opción de entrega temprana. Si se toma como desvío independiente, reservar **40–60 h** y revisar su impacto en el piloto; no se promete que ejecutar ambas rutas mantenga la misma fecha. Reutilizar parser y consultas para evitar trabajo desechable. En dos semanas se puede perseguir una demo recortada, pero no se estima terminada toda la plataforma actual.

## Después del piloto

Estimaciones adicionales independientes; no están incluidas en las 180–275 h.

| Ampliación | Esfuerzo orientativo | Dependencia |
| --- | --- | --- |
| Grafo navegable, comparación básica entre snapshots y exportación CSV | 24–40 h | Snapshots consistentes e inventario real |
| Preparación de lanzamiento público | 60–100 h + 25 % de reserva | Piloto validado: operación, recuperación, observabilidad, cuotas y revisión de seguridad según alcance |
| Documentación e IA/RAG | Por estimar tras definir un caso de uso | Fuentes, permisos, recuperación y criterios de evaluación definidos |
| Simulación de cambios, SSO y capacidades empresariales | Fuera de esta versión | Validación del piloto y requisitos concretos |

Lanzamiento público con el mismo alcance funcional: **255–400 h en total desde el estado actual**, aproximadamente **11–16 semanas a 25 h/semana**, antes de considerar una ventana de uso y feedback del piloto. Incluye la reserva de ambas etapas; no incluye cobros, requisitos contractuales, cumplimiento específico, RAG ni ampliaciones del grafo. No se despliega ni se contrata infraestructura como parte de este documento.

## Decisiones que pueden mover la estimación

- Confirmar horas semanales y si la primera salida es demo, piloto o producto público.
- Elegir el mecanismo de autenticación antes de implementar login y sesiones.
- Propuesta para acotar el piloto: un ecosistema inicial y un conjunto pequeño de repositorios; elegir Maven o npm después de una prueba técnica. La elección todavía no está confirmada.
- Validar primero scanner, resolución de dependencias privadas y SBOM en repositorios reales. Es el principal riesgo técnico del camino crítico.
- Mantener análisis manual como primera versión; webhooks, monorepos complejos y muchos ecosistemas requieren estimación adicional.
- Los cambios de alcance y la espera de accesos, servicios o decisiones no están absorbidos ilimitadamente por la reserva.

## Seguimiento y pruebas

Actualizar semanalmente horas consumidas, entregas aceptadas, bloqueos y estimación restante. Reestimar tras el primer análisis real; esa evidencia vale más que el rango inicial.

Probar solo lo necesario para cada cambio: reglas de negocio modificadas, autorización y aislamiento cuando se toquen, integración real de persistencia o proveedor cuando corresponda y un recorrido completo antes del piloto. Repetir una verificación si cambia el código relevante, falla una prueba o queda una duda concreta.

Crear normalmente 2–3 commits por bloque que incluya varias funcionalidades, con al menos un commit coherente por funcionalidad y su validación relevante. Usar siempre emoji y Conventional Commits. No dividir artificialmente una función para alcanzar una cifra.
