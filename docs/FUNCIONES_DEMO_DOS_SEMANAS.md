# Funciones para presentar Dependency Intelligence en dos semanas

## Objetivo y límite de tiempo

Entregar una **demo local funcional en dos semanas**, con la capacidad confirmada de **25 horas semanales: 50 horas en total**. Presupuesto: **40 horas de implementación y verificación + 10 horas de reserva**. Este es un plan de alcance cerrado para la presentación, no una garantía de que toda la plataforma esté terminada.

Recorrido de la demo:

**Abrir la aplicación → cargar un SBOM de ejemplo → guardar el inventario → buscar dependencias → consultar el detalle y su procedencia.**

Un SBOM es el archivo que describe los componentes de un proyecto. Para esta entrega se importa un archivo CycloneDX JSON ya generado. Los resultados deben proceder del archivo importado; la demo no analizará automáticamente el código fuente.

## Base que ya se puede reutilizar

- Módulos Domain, Application e Infrastructure y estructura Angular.
- PostgreSQL, esquema inicial y migraciones Flyway.
- Registro/consulta de repositorios y solicitud de análisis parcialmente implementados.
- Reglas del agregado Scan y claves que relacionan datos por organización y snapshot.

Todavía faltan los casos de uso de importación y consulta de inventario, sus adaptadores y las pantallas. La estructura existente no significa que estas funciones ya estén terminadas.

## Funciones obligatorias

Todas las funciones siguientes son necesarias para el recorrido de presentación. El esfuerzo incluye su validación relevante; la reserva es independiente.

| ID | Función | Qué debe hacer | Se acepta cuando | Horas |
| --- | --- | --- | --- | ---: |
| F01 | Preparar el escenario local | Disponer de una organización y un repositorio de demostración, un SBOM conocido y una configuración local reproducible | La aplicación y la base arrancan; el ejemplo tiene procedencia y componentes esperados documentados | 3 |
| F02 | Cargar un archivo SBOM | Mostrar un selector de archivo y un botón Importar; recibir un único JSON, con límite de tamaño y resultado visible | El usuario puede elegir y enviar el archivo, ve la espera y recibe confirmación o error | 4 |
| F03 | Interpretar y validar CycloneDX | Leer el subconjunto acordado del documento y convertir componentes a objetos de aplicación/dominio | Extrae nombre, versión, ecosistema y PURL del ejemplo; rechaza JSON inválido, formato no soportado y referencias duplicadas | 6 |
| F04 | Guardar el snapshot y sus componentes | Crear la importación vinculada al repositorio de demo y guardar componentes/ocurrencias en una transacción | Una importación válida queda disponible después de reiniciar; una inválida no deja un snapshot parcial | 7 |
| F05 | Consultar y buscar en el inventario | Consultar un snapshot, buscar por nombre y paginar con orden estable | La respuesta contiene solo datos de la organización y del snapshot configurados para la demo | 5 |
| F06 | Mostrar la tabla de dependencias | Presentar nombre, versión, ecosistema y PURL; incluir búsqueda, paginación y total del snapshot | Los datos visibles corresponden al archivo importado y cambiar la búsqueda actualiza el listado | 6 |
| F07 | Mostrar detalle y procedencia | Abrir un panel sencillo con datos del componente, repositorio de ejemplo y fecha de importación | Se puede explicar de dónde viene la dependencia sin inventar commit, severidad ni clasificación directa/transitiva | 3 |
| F08 | Resolver estados y documentar el recorrido | Mostrar carga, inventario vacío, archivo rechazado y fallo del backend; escribir instrucciones y guion breve | El usuario entiende el resultado y puede repetir la presentación desde las instrucciones | 4 |
| F09 | Verificar y ensayar la entrega | Ejecutar las comprobaciones críticas y el recorrido completo en el equipo de presentación | Importación, búsqueda, detalle y rechazo de archivo inválido funcionan con el ejemplo elegido | 2 |
| | **Trabajo planificado** | | | **40** |
| | **Reserva para bloqueos y correcciones** | | | **10** |
| | **Capacidad total de dos semanas** | | | **50** |

## Decisiones para mantener el alcance

1. Usar una sola organización, un repositorio de ejemplo y un ecosistema en la presentación. Elegir el SBOM y fijar su versión soportada durante F01.
2. Soportar únicamente el subconjunto necesario de CycloneDX JSON. No prometer compatibilidad general con todos los SBOM, versiones o gestores.
3. Como propuesta para la demo, limitar el archivo a 5 MB y el ejemplo a 500 componentes. Verificar estos límites al cargar y al procesar; ajustarlos solo si el ejemplo lo exige y cabe en el presupuesto.
4. Cada carga confirmada crea un snapshot independiente; deshabilitar doble envío mientras se procesa. Reutilizar el catálogo por PURL dentro de la organización sin duplicar una misma ocurrencia dentro del snapshot.
5. Si faltan identificadores requeridos para persistir el ejemplo, rechazar el archivo con un mensaje claro. No fabricar versiones, PURL ni hashes de commit.
6. Acceso de demo en un perfil local explícito, con organización de ejemplo fijada por el servidor y publicación solo en loopback. No deshabilitar globalmente la seguridad ni aceptar una organización arbitraria del cliente. La configuración normal conserva el bloqueo de negocio hasta implementar autorización.
7. Conservar Domain y Application sin Spring/JPA. El parser JSON, HTTP, persistencia y configuración de demo pertenecen a Infrastructure; Application coordina los puertos y las transacciones se configuran desde Infrastructure.
8. Para la demo basta persistir los componentes y su presencia. Importar/recorrer aristas del grafo, conservar archivos originales y procesar en segundo plano se posponen. No presentar una referencia temporal al archivo como artefacto almacenado de forma permanente.

## Calendario de diez jornadas de cinco horas

Los días son jornadas de trabajo relativas al inicio acordado; no se han fijado fechas de calendario.

| Semana | Jornada | Trabajo | Horas |
| --- | --- | --- | ---: |
| 1 | Día 1 | F01: escenario y arranque (3 h); F02: contrato y comienzo de la carga (2 h) | 5 |
| 1 | Día 2 | F02: terminar carga (2 h); F03: comenzar parser (3 h) | 5 |
| 1 | Día 3 | F03: terminar validación (3 h); F04: comenzar persistencia (2 h) | 5 |
| 1 | Día 4 | F04: terminar transacción y snapshot | 5 |
| 1 | Día 5 | F05: consulta, búsqueda y paginación | 5 |
| 2 | Día 6 | F06: tabla conectada al backend | 5 |
| 2 | Día 7 | F06: terminar tabla (1 h); F07: detalle (3 h); F08: estados (1 h) | 5 |
| 2 | Día 8 | F08: terminar estados y guion (3 h); F09: comprobar y ensayar (2 h) | 5 |
| 2 | Día 9 | Reserva: corregir bloqueos y repetir solo las comprobaciones afectadas | 5 |
| 2 | Día 10 | Reserva: ajustes finales y ensayo de presentación | 5 |
| | | **Total** | **50** |

Hito de cierre de semana 1: un SBOM válido ya se importa, persiste y consulta desde backend. Hito de cierre de semana 2: ese flujo se presenta desde Angular y se puede repetir.

## Verificación mínima necesaria

- Parser: ejemplo válido y archivos inválidos relevantes, incluidos formato no soportado e identificadores duplicados.
- Persistencia: importación válida, rollback ante error y consulta acotada a la organización/snapshot.
- Acceso: perfil local aislado y política normal de negocio conservada.
- Presentación: una pasada completa de carga → inventario → búsqueda → detalle y un rechazo de archivo inválido; confirmar persistencia tras reiniciar.

Estas comprobaciones están incluidas en las funciones y en F09; no constituyen una campaña de pruebas adicional. No repetir suites completas por cambios de documentación o ajustes visuales menores. Repetir solo si cambia el comportamiento cubierto, aparece un fallo o queda una duda concreta.

## Qué queda después de la presentación

Login multiusuario y roles, integración GitHub, descarga/clonado de repositorios, scanner automático, worker de análisis, OSV y vulnerabilidades reales, grafo, comparación de snapshots, exportaciones, dashboards avanzados, RAG/IA y despliegue público.

No son requisitos para aceptar esta demo. Si alguno pasa a ser obligatorio, debe sustituir trabajo de esfuerzo equivalente o cambiar la fecha; no se añade encima de las 50 horas.

## Control del plazo y commits

Revisar el avance al terminar los días 3, 5 y 8. Si se consume la reserva antes de completar el recorrido, simplificar primero presentación visual y detalle integrado en la tabla, preservando importación real, persistencia, aislamiento local y consulta. Un bloqueo de arranque o parser se atiende en cuanto aparezca.

Agrupar el trabajo en bloques de 2–3 commits cuando haya varias funciones; mantener al menos un commit coherente por funcionalidad y añadir sus pruebas necesarias al mismo cambio. Ejemplos:

- `✨ feat(demo): prepara el escenario y acceso local de presentación`
- `✨ feat(sbom): valida e importa un snapshot CycloneDX`
- `✨ feat(inventory): consulta y filtra componentes del snapshot`
- `✨ feat(frontend): permite cargar y consultar el inventario local`
- `✨ feat(frontend): muestra detalle y procedencia de componentes`
- `📝 docs(demo): documenta arranque y guion de presentación`

## Lista de entrega

- [ ] F01–F09 terminadas y criterios de aceptación comprobados.
- [ ] Ejemplo, versión soportada y límites documentados.
- [ ] Arranque reproducible en el equipo de presentación.
- [ ] Inventario leído de datos importados y persistidos.
- [ ] Fallos visibles y sin escrituras parciales.
- [ ] Perfil local aislado; no se ha habilitado acceso público.
- [ ] Guion breve y dos archivos preparados: uno válido y otro inválido.
- [ ] Commits con emoji y repositorio sin artefactos ni secretos añadidos.
