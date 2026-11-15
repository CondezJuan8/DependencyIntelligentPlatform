# Dependency Intelligence

Base inicial de una plataforma de inventario de dependencias y vulnerabilidades. Ejecución local con Docker Compose; sin hosting ni despliegue público.

## Arranque local

Requiere Docker Engine y Docker Compose v2. No requiere Java, Maven ni Node instalados en el host.

```bash
cp -n .env.example .env
docker compose up --build -d
```

El archivo `.env` no se versiona; `cp -n` conserva una configuración local existente. Configurar `POSTGRES_PASSWORD` y `POSTGRES_APP_PASSWORD` distintas; esta última es obligatoria para el login runtime limitado. Para volúmenes existentes seguir el [aprovisionamiento y actualización](docs/database/README.md#arranque-y-actualización) antes de iniciar el backend.

Abrir http://localhost:8088 y comprobar http://localhost:8088/health. Para cambiar el puerto, editar `WEB_PORT` en `.env`.

```bash
docker compose logs -f
docker compose down
```

La base persiste en el volumen `postgres_data`. `down` conserva los datos. Las imágenes y dependencias se descargan durante la primera construcción. Las integraciones futuras con GitHub y OSV necesitarán internet.

## Estructura

- `backend/`: Java 21, Spring Boot 3.5 y Maven; proyectos independientes **Domain**, **Application** e **Infrastructure**, con contextos DDD dentro de cada capa. Importar `backend/pom.xml` en el IDE.
- `frontend/`: Angular 22, TypeScript, Angular Material y Cytoscape.js; `core`, `shared` y funcionalidades del MVP.
- `infra/`: límites y documentación de infraestructura local y scanner.
- `docs/`: requisitos, contexto, stack y convenciones de commits.

Domain contiene agregados, reglas y value objects en Java puro. Application coordina casos de uso mediante DTO y puertos de entrada/salida. Infrastructure implementa HTTP, JPA/JDBC, configuración y transacciones. Las dependencias Maven apuntan hacia Domain; las pruebas ArchUnit protegen los límites. Ver [arquitectura del backend](docs/ARQUITECTURA_BACKEND.md).

## Estado y decisiones

Consultar el [roadmap](docs/ROADMAP.md) para fases y estado; todavía no hay fecha total nueva comprometida.

Objetivo vigente: producto completo listo para usar por fases, primero local y después servidor. Alcance, decisiones y matriz de pendientes: [contexto maestro](docs/DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md). El plan anterior de demo/50 horas ya no limita la aceptación.

El progreso de cada bloque se registra en la [bitácora de trabajo](docs/BITACORA_TRABAJO.md). Referencias de datos: [nueva especificación](docs/CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md) y [estado ejecutable/pendientes](docs/database/implementation-report.md).

Incluye PostgreSQL/Flyway hasta V12 con 121 tablas, 85 con RLS forzado y 208 FK. Registro/login/logout local, sesión y creación/consulta de organizaciones disponibles en Angular y API. Contratos, configuración y alcance de verificación en [ACCESO_LOCAL](docs/ACCESO_LOCAL.md). Diccionario y siete diagramas en [database](docs/database/README.md).

Están implementados registro/consulta de repositorios, solicitud de análisis e importación persistente de SBOM bajo contexto tenant transaccional. El parser CycloneDX 1.6/Maven acotado alimenta una importación atómica con catálogo normalizado paquete/versión/aparición. Faltan carga HTTP e inventario visual. Las solicitudes quedan `PENDING`; importaciones manuales crean snapshots `COMPLETED` con origen explícito, sin inventar commit ni scanner. No existen worker, GitHub, scanner, OSV ni pantallas de inventario/grafo. Se verificaron migraciones hasta V11, transición con datos, rollback y acceso con login runtime real limitado.

Los endpoints de repositorios/scans comprueban sesión, membresía y permisos de rol/equipo. Flyway y datasource usan credenciales separadas. El guard runtime verifica sus privilegios. Continúan verificación/recuperación de correo, Google/GitHub, invitaciones y gestión de equipos. La sesión actual vive en memoria del backend.

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

Para incluir las pruebas de PostgreSQL real: `mvn verify -Pdatabase-it` desde `backend/`. Requiere Docker para Testcontainers o una base desechable configurada mediante `TEST_DATABASE_URL`, `TEST_DATABASE_USER` y `TEST_DATABASE_PASSWORD`. El artefacto ejecutable se genera en `backend/infrastructure/target/infrastructure-0.1.0-SNAPSHOT.jar`.

Versiones compatibles contrastadas en [Spring Boot](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [springdoc](https://springdoc.org/) y [Angular](https://angular.dev/reference/versions). Versiones transitivas frontend fijadas en `package-lock.json`; backend gestionado por el BOM de Spring Boot.
