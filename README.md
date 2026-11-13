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

- `backend/`: Java 21, Spring Boot 3.5, Maven; monolito modular con paquetes `domain`, `application`, `infrastructure` y `api` por módulo.
- `frontend/`: Angular 22, TypeScript, Angular Material y Cytoscape.js; `core`, `shared` y funcionalidades del MVP.
- `infra/`: límites y documentación de infraestructura local y scanner.
- `docs/`: requisitos, contexto, stack y convenciones de commits.

El dominio no debe depender de Spring ni de formatos de proveedores. Application coordina casos de uso mediante puertos; Infrastructure implementa persistencia y adaptadores. Las APIs llaman a Application. Historial y relaciones se incorporarán en Scan y Component/Dependency Graph sin duplicar servicios.

## Estado y decisiones

Incluye compilación, conexión PostgreSQL, Flyway, Actuator y pantalla inicial. JUnit, Mockito, Spring Security Test y Testcontainers están preparados para pruebas. Las únicas pruebas iniciales comprueban la política de acceso HTTP.

No están implementados autenticación, aislamiento multi-tenant, esquema de negocio, GitHub, scanner, parseo CycloneDX, OSV, procesamiento asíncrono, inventario ni grafo. Los paquetes indican ubicación, no capacidades terminadas. La migración inicial valida Flyway sin definir entidades todavía. Antes de exponer datos deben implementarse identidad, autorización, filtros por organización y pruebas de acceso cruzado.

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

Versiones compatibles contrastadas en [Spring Boot](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [springdoc](https://springdoc.org/) y [Angular](https://angular.dev/reference/versions). Versiones transitivas frontend fijadas en `package-lock.json`; backend gestionado por el BOM de Spring Boot.
