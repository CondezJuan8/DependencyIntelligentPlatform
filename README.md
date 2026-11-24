# Dependency Intelligence

Base inicial de una plataforma de inventario de dependencias y vulnerabilidades. Ejecución local con Docker Compose; sin hosting ni despliegue público.

## Arranque local

Requiere Docker Engine y Docker Compose v2. No requiere Java, Maven ni Node instalados en el host.

```bash
cp -n .env.example .env
docker compose up --build -d
```

En PowerShell, crea `.env` sin reemplazar uno existente con `if (-not (Test-Path .env)) { Copy-Item .env.example .env }` y luego ejecuta `docker compose up --build -d`.

El archivo `.env` no se versiona; `cp -n` conserva una configuración local existente. Configurar `POSTGRES_PASSWORD` y `POSTGRES_APP_PASSWORD` distintas; esta última es obligatoria para el login runtime limitado. Para volúmenes existentes seguir el [aprovisionamiento y actualización](docs/database/README.md#arranque-y-actualización) antes de iniciar el backend.

Abrir http://localhost:8088 y comprobar http://localhost:8088/health. Para cambiar el puerto, editar `WEB_PORT` en `.env`.

En una base vacía, crea el primer administrador antes de registrar usuarios. El operador es interactivo, solo corre cuando se invoca y rechaza una base que ya contiene datos:

```bash
docker compose --profile bootstrap build platform-admin-bootstrap
docker compose --profile bootstrap run --rm -it platform-admin-bootstrap
```

Después inicia sesión con esa cuenta, crea una organización y entra en **Mis repositorios** para importar una carpeta local o conectar GitHub. Selecciona **Analizar** manualmente; el trabajo queda consultable y, al terminar, sus snapshots aparecen en **Inventario**. Las cuentas OWNER de plataforma no reciben acceso automático a otras organizaciones: para analizar se necesita membresía activa en la organización seleccionada y permiso `scan.create`. Las cuentas del registro normal esperan aprobación de un administrador de plataforma.

```bash
docker compose logs -f
docker compose down
```

La base persiste en el volumen `postgres_data`. `down` conserva los datos. Las imágenes y dependencias se descargan durante la primera construcción. Las integraciones futuras con GitHub y OSV necesitarán internet.

## Estructura

- `backend/`: Java 21, Spring Boot 3.5 y Maven; proyectos independientes **Domain**, **Application** e **Infrastructure**, con contextos DDD dentro de cada capa. Importar `backend/pom.xml` en el IDE.
- `frontend/`: Angular 22, TypeScript y Angular Material; `core`, `shared` y funcionalidades del MVP.
- `infra/`: límites y documentación de infraestructura local y scanner.
- `docs/`: requisitos, contexto, stack y convenciones de commits.

La paleta visual y los hitos para probar recorridos con el usuario están documentados en [diseño frontend](docs/DISENO_FRONTEND.md).

Domain contiene agregados, reglas y value objects en Java puro. Application coordina casos de uso mediante DTO y puertos de entrada/salida. Infrastructure implementa HTTP, JPA/JDBC, configuración y transacciones. Las dependencias Maven apuntan hacia Domain; las pruebas ArchUnit protegen los límites. Ver [arquitectura del backend](docs/ARQUITECTURA_BACKEND.md).

## Estado y decisiones

Consultar el [roadmap](docs/ROADMAP.md) para fases y estado; todavía no hay fecha total nueva comprometida.

Objetivo vigente: producto completo listo para usar por fases, primero local y después servidor. Alcance, decisiones y matriz de pendientes: [contexto maestro](docs/DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md). El plan anterior de demo/50 horas ya no limita la aceptación.

El progreso de cada bloque se registra en la [bitácora de trabajo](docs/BITACORA_TRABAJO.md). Referencias de datos: [nueva especificación](docs/CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md) y [estado ejecutable/pendientes](docs/database/implementation-report.md).

Incluye PostgreSQL/Flyway hasta V32, permisos por organización/equipo/repositorio y análisis manual durable. Análisis e Inventario trabajan con snapshots Maven/npm; la cola registra el estado y las etapas confirmadas y no inventa porcentajes. Acceso local, verificación/recuperación de correo y organizaciones están disponibles en Angular y API. El bootstrap, las reglas de admisión y el alcance de OWNER están descritos en [ACCESO_LOCAL](docs/ACCESO_LOCAL.md). Diccionario y diagramas en [database](docs/database/README.md).

El backend normaliza paquetes, versiones y apariciones de análisis tenant-scoped. El scanner procesa Maven y npm con cdxgen; el inventario consulta resultados OSV por versión exacta. Consulta el contexto maestro para el alcance y estado de las pantallas.

Los endpoints de repositorios/scans comprueban sesión, membresía y permisos de rol/equipo. Flyway y datasource usan credenciales separadas. Verificación y recuperación de correo disponibles con Mailpit local en `http://localhost:8025`, tokens de un uso y revocación de sesiones. Google/GitHub y vinculación explícita disponibles en backend/Angular, deshabilitados hasta configurar clientes propios: [activación y límites](docs/IDENTIDAD_GOOGLE_GITHUB.md). El consentimiento real sigue pendiente. Disponibles [invitaciones y gestión de miembros/equipos/permisos](docs/INVITACIONES_EQUIPOS_PERMISOS.md), con SMTP y autorización probados; falta revisión visual conjunta. Sesiones y cola de recuperación en memoria. Configuración SMTP en [verificación y recuperación](docs/VERIFICACION_Y_RECUPERACION.md).

Health y los contratos públicos de registro/login/CSRF permiten iniciar el acceso. Los POST exigen CSRF y las operaciones de organización/repositorio exigen sesión. OpenAPI permanece deshabilitado. PostgreSQL y backend se conectan por la red interna de Compose y Nginx publica en `127.0.0.1`. La revisión visual quedó pendiente por falta de acceso al navegador. Build Angular y pruebas HTTP correctos.

Material es la biblioteca visual inicial. cdxgen será un scanner aislado, no una dependencia de la API. GitHub y OSV podrán usar el cliente HTTP de Spring; no se agregan SDK sin necesidad. El modo local prioriza análisis manual: GitHub no puede enviar webhooks directamente a localhost. No se abre ningún túnel.

RAG, pgvector, Ollama, Gemini, Tika/PDFBox y OCR se implementarán en sus fases; no se agregan dependencias inactivas. Codex queda fuera del producto hasta nueva instrucción. No se incluye todavía pipeline de despliegue.

## Verificación

```bash
docker compose --env-file .env.example config --quiet
docker compose build
docker compose up -d
curl --fail http://localhost:8088/health
```

El build del backend ejecuta `mvn verify`; el frontend usa `npm ci` y build de producción. Para ejecutar las pruebas backend por separado con Java 21 y Maven: `cd backend && mvn verify`.

Para incluir las pruebas de PostgreSQL real: `mvn verify -Pdatabase-it` desde `backend/`. Testcontainers crea una base aislada por suite en PostgreSQL desechable. Si defines `TEST_DATABASE_URL`, `TEST_DATABASE_USER` y `TEST_DATABASE_PASSWORD`, el usuario necesita `CREATEDB`; las bases temporales se eliminan al cerrar Maven. Usa únicamente un servidor de pruebas desechable. El artefacto ejecutable se genera en `backend/infrastructure/target/infrastructure-0.1.0-SNAPSHOT.jar`.

Versiones compatibles contrastadas en [Spring Boot](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [springdoc](https://springdoc.org/) y [Angular](https://angular.dev/reference/versions). Versiones transitivas frontend fijadas en `package-lock.json`; backend gestionado por el BOM de Spring Boot.
