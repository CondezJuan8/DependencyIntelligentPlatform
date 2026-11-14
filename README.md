# Dependency Intelligence

Base inicial de una plataforma de inventario de dependencias y vulnerabilidades. Ejecución local con Docker Compose; sin hosting ni despliegue público.

## Arranque local

Requiere Docker Engine y Docker Compose v2. No requiere Java, Maven ni Node instalados en el host.

```bash
cp -n .env.example .env
docker compose up --build -d
```

El archivo `.env` no se versiona; `cp -n` conserva una configuración local existente.

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

Consultar el [roadmap de salida](docs/ROADMAP.md) para alcance, esfuerzo y escenarios según horas semanales.

La primera entrega se planifica en dos semanas: ver [funciones de la demo local y calendario de 50 horas](docs/FUNCIONES_DEMO_DOS_SEMANAS.md).

El progreso de cada bloque se registra en la [bitácora de trabajo](docs/BITACORA_TRABAJO.md). Para preparar el escenario, consultar [demo local](docs/DEMO_LOCAL.md).

Incluye conexión PostgreSQL, Flyway, Actuator y pantalla inicial. El esquema del MVP contiene organizaciones, usuarios, membresías, integraciones, repositorios, análisis, artefactos, componentes, ocurrencias, relaciones, vulnerabilidades, hallazgos y auditoría. V2 amplía V1 sin reescribirla. Las claves compuestas impiden relaciones entre organizaciones y snapshots distintos; ver [base de datos](docs/BASE_DE_DATOS.md).

Están implementados los casos de uso de registro/consulta de repositorios y solicitud de análisis, con persistencia real y consultas por organización. También existe un parser acotado de CycloneDX JSON 1.6/Maven, todavía sin conectar a carga y persistencia del inventario. La solicitud queda `PENDING`: no existen worker, GitHub, scanner, OSV ni las pantallas de inventario y grafo. Los demás contextos conservan paquetes de extensión. Las pruebas cubren reglas, parser, límites de arquitectura, seguridad HTTP, persistencia, restricciones y actualización V1 → V2.

Faltan autenticación y autorización por membresía. La integridad referencial y los filtros de organización no sustituyen esos controles; no hay RLS. Los endpoints de negocio permanecen bloqueados.

Solo `/actuator/health` es público en la API; el resto se deniega. OpenAPI está instalado pero deshabilitado hasta configurar acceso. PostgreSQL y backend solo son accesibles desde la red interna de Compose. Nginx publica exclusivamente en `127.0.0.1`.

Material es la biblioteca visual inicial. cdxgen será un scanner aislado, no una dependencia de la API. GitHub y OSV podrán usar el cliente HTTP de Spring; no se agregan SDK sin necesidad. El modo local prioriza análisis manual: GitHub no puede enviar webhooks directamente a localhost. No se abre ningún túnel.

RAG, pgvector, OpenAI, Tika/PDFBox y OCR pertenecen a fase 2; no se agregan dependencias inactivas. No se incluye pipeline de despliegue.

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
