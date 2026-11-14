# Bitácora de trabajo

Este documento se actualiza en cada bloque de trabajo. Registra cambios, herramientas, validación, limitaciones y siguiente paso. Las horas estimadas del roadmap no se registran como horas reales consumidas.

## Bloque 01 — Escenario local de demo y registro de continuidad

Fecha: 14 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y objetivo

Mantener un registro de lo realizado y continuar la demo de dos semanas. Se empezó por F01: escenario reproducible con organización, repositorio y SBOM conocidos.

### Cambios realizados

- Añadido `AGENTS.md` para conservar la obligación de actualizar esta bitácora, probar solo lo necesario y usar commits con emoji.
- Implementado el perfil Spring `demo` y un bootstrap transaccional de organización/repositorio con identificadores estables; sin modificar el esquema Flyway.
- Añadido un override Compose para activar la demo en un proyecto/volumen independiente.
- Preparados un SBOM CycloneDX JSON 1.6 sintético con tres bibliotecas Maven y un archivo de rechazo por `bom-ref` duplicado.
- Documentados procedencia, resultados esperados y arranque nativo/Compose.

### Herramientas y tecnologías

| Herramienta / tecnología | Para qué se utilizó | Incorporación |
| --- | --- | --- |
| Spring Boot: perfiles y ConfigurationProperties | Activar y configurar el escenario de demo explícitamente | Se usa la dependencia ya existente |
| JDBC y TransactionTemplate | Inicialización atómica e idempotente en PostgreSQL | Se usan dependencias ya existentes |
| PostgreSQL y Flyway | Reutilizar esquema y comprobar persistencia del escenario | Sin nueva migración ni motor añadido |
| JUnit / Spring Boot Test / MockMvc | Verificar bootstrap y bloqueo HTTP | Se usan dependencias de pruebas existentes |
| Maven y JDK local | Compilar y ejecutar comprobaciones seleccionadas | Sin nueva dependencia de producto |
| Docker Compose | Definir arranque aislado de demostración | Configuración preparada; Docker no disponible para ejecutarla aquí |
| CycloneDX JSON 1.6 | Formato del archivo de entrada de ejemplo | Es un formato; no se ha añadido un scanner ni SDK |
| PowerShell, Git y editor de archivos | Inspección, edición y commits por funcionalidad | Herramientas de trabajo, no dependencias del backend |

### Validación y resultados

- Compilación de los tres módulos y empaquetado: `BUILD SUCCESS`.
- Ejecutadas solo `SecurityConfigurationTest` (2 pruebas) y `DemoBootstrapIT` (2 pruebas): **4 aprobadas, sin errores ni omisiones**. No se ejecutaron las demás suites.
- La integración levantó Spring con perfil demo sobre PostgreSQL 18.6 temporal: bootstrap repetido sin duplicados, rechazo de datos existentes incompatibles sin sobrescritura, health accesible y API de negocio denegada.
- Entorno de verificación: Maven 3.9.11 y JDK 25 con destino Java 21. La base temporal se detuvo al terminar. No se añadieron dependencias de producto.
- PowerShell confirmó que ambos archivos son JSON legible, el ejemplo contiene tres bibliotecas con referencias únicas y el caso de rechazo tiene referencias repetidas. No se ejecutó un validador completo del esquema CycloneDX ni un importador que aún no existe.
- Docker/Compose y el recorrido en navegador **no se ejecutaron**: Docker no está disponible en este entorno. El arranque nativo real se cubrió mediante contexto Spring de integración; no se verificó un servidor HTTP externo arrancado desde el JAR.

### Límites y siguiente paso

- Los datos son sintéticos y se identifican como tales; no hay resultados de scanner ni vulnerabilidades verificadas.
- El perfil demo no habilita las APIs de negocio. El arranque completo de Docker depende de disponer de Docker en el equipo.
- No están implementados el parser, la carga ni el inventario. F01 no debe marcarse totalmente aceptada hasta comprobar el arranque requerido en el equipo de presentación.
- Siguiente función: F03, parser del subconjunto CycloneDX acordado, como soporte de F02/F04. Luego conectar carga y persistencia; no añadir GitHub ni OSV a este bloque.

### Commits

Se separa en tres commits: implementación del escenario local con sus pruebas, archivos de ejemplo CycloneDX con su procedencia y bitácora/instrucciones de continuidad con el estado actualizado. Consultar el historial de Git para los identificadores.

## Bloque 02 — Parser CycloneDX y alineación documental

Fecha: 14 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y documentos revisados

Continuar el desarrollo priorizando contexto maestro, arquitectura del backend y stack tecnológico. Se contrastaron las secciones de arquitectura hexagonal, modelo de datos, procedencia y límites de proveedores con el alcance de demo confirmado. Las propuestas históricas no se convirtieron en integraciones implementadas.

### Cambios realizados

- F03: implementado `SbomParser` como puerto de Application y `CycloneDxJsonParser` como adaptador de Infrastructure.
- Añadido el value object `PackageCoordinates` en Domain y DTO inmutables `ParsedSbom` / `SbomComponent` en Application. No se añadieron dependencias de frameworks a esas capas.
- Separadas coordenadas de paquete y ocurrencias por `bom-ref`: referencias diferentes pueden compartir coordenadas; referencias repetidas se rechazan, incluso si colisionan con la raíz.
- Delimitado el formato a CycloneDX JSON 1.6 con bibliotecas Maven planas y PURL simples coincidentes con grupo/nombre/versión. Se conserva el serial cuando está presente; no se inventan commits ni procedencia ausente.
- Aplicados límites de 5 MiB, 500 componentes, profundidad JSON y longitudes de cadenas/números. Se rechazan claves duplicadas, varios documentos concatenados, formatos no soportados y campos obligatorios ausentes.
- Actualizados contexto maestro, arquitectura y stack con el estado vigente; conservadas sus propuestas e historial. Actualizados instrucciones de continuidad, README, demo, backlog y descripción de fixtures.

### Herramientas y tecnologías

| Herramienta / tecnología | Uso | Incorporación |
| --- | --- | --- |
| Java: records, colecciones inmutables y streams de entrada | Contratos y value object propios; lectura limitada del documento | Plataforma ya existente, destino Java 21 |
| Jackson | Árbol JSON y restricciones del parser en Infrastructure | Dependencia ya transitiva de Spring Web; no se añadió librería ni versión nueva |
| Spring Boot | Registro del adaptador para inyección detrás del puerto | Ya existente; no se arrancó Spring para las pruebas de este bloque |
| JUnit 5 | Pruebas unitarias de aceptación/rechazo del subconjunto | Ya existente |
| ArchUnit | Comprobar dependencias de Domain/Application y ubicación de adaptadores | Ya existente |
| Maven, PowerShell y Git | Compilación, verificaciones focalizadas y commits por funcionalidad | Herramientas de trabajo, no dependencias nuevas del producto |

### Verificación ejecutada

```bash
mvn verify -Dtest=CycloneDxJsonParserTest,HexagonalArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false
```

Desde `backend/`: **BUILD SUCCESS; 8 pruebas del parser y 4 reglas de arquitectura aprobadas; 0 fallos, 0 errores, 0 omisiones**. Se compilaron y empaquetaron los tres módulos con Maven 3.9.11 y JDK local 25, destino Java 21.

Las pruebas comprueban lectura de coordenadas y serial, raíz excluida del inventario, ocurrencias distintas del mismo paquete, resultado inmutable, JSON inválido/ambiguo, formato/versión no soportados, datos ausentes o PURL contradictorios, referencias duplicadas, límite de tamaño/cantidad, inventario vacío, componentes anidados, profundidad excesiva y error de lectura.

No se iniciaron base de datos, Docker ni frontend; no cambiaron persistencia o endpoints. Las entradas unitarias son documentos construidos en las pruebas; no se ejecutaron los archivos de `fixtures/demo/` contra un flujo completo de importación.

### Límites y siguiente paso

- No es validación completa del esquema CycloneDX ni soporte general de Maven: qualifiers, subpaths, codificaciones y componentes anidados quedan fuera de este subconjunto.
- No se siguen URLs, ejecutan scanners ni consultan proveedores. No se infieren dependencias directas/transitivas, vulnerabilidades o commits.
- F03 está implementada como parser. F02 y F04 siguen pendientes: no hay endpoint de carga conectado ni escritura del inventario. No se marca DEMO-02 completa.
- Siguiente bloque: F04, caso de uso de importación y persistencia transaccional por organización/repositorio/snapshot; después conectar F02 con acceso de demo acotado. Mantener el rechazo de documentos inválidos antes de escribir y verificar rollback cuando se conecte persistencia.

### Commits

Tres cambios coherentes: contratos/coordenadas, adaptador CycloneDX con pruebas y actualización de documentos prioritarios/bitácora. Todos con emoji; sus identificadores quedan en el historial de Git.
