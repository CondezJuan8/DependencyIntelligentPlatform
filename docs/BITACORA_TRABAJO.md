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
