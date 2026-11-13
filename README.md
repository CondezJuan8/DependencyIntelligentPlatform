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

La base ya tiene migraciones, referencias, índices, FKs compuestas y RLS con FORCE. Flyway usa un rol de migración independiente; la API usa `dependency_app`, sin DDL ni BYPASSRLS. Consultar [la guía de base de datos](docs/database/README.md), el [reporte de validación](docs/database/implementation-report.md) y los [diagramas editables](diagramas/README.md).

Perfil `core`: 76 tablas. Perfil `future`: 115 tablas e incluye documentación, impacto y notificaciones sin activar servicios externos. El vector opcional añade una tabla y requiere pgvector; no está habilitado en la base local. Si vienes del arranque anterior, añadir `APP_DB_PASSWORD` y `MIGRATION_DB_PASSWORD` a `.env`. El bootstrap funciona también sobre el volumen existente y conserva V1.

El esquema prepara el producto; todavía faltan autenticación, autorización por recurso, adaptadores GitHub/OSV/cdxgen, workers e interfaces funcionales. Los endpoints de negocio siguen denegados. `/health` comprueba la conexión; PostgreSQL y backend solo son accesibles en la red interna de Compose. Nginx publica en `127.0.0.1`.

Material es la biblioteca visual inicial. cdxgen se ejecutará aislado, sin Docker socket ni credenciales innecesarias. El modo local prioriza análisis manual: GitHub no puede enviar webhooks directamente a localhost. RAG y OCR siguen en fase 2; no se añaden SDK o proveedores de IA por anticipado. No hay despliegue público.

## Verificación

```bash
docker compose --env-file .env.example config --quiet
docker compose build
docker compose up -d
curl --fail http://localhost:8088/health
```

El build del backend ejecuta `mvn verify`; el frontend usa `npm ci` y build de producción. Para ejecutar las pruebas backend por separado con Java 21 y Maven: `cd backend && mvn verify`.

Versiones compatibles contrastadas en [Spring Boot](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [springdoc](https://springdoc.org/) y [Angular](https://angular.dev/reference/versions). Versiones transitivas frontend fijadas en `package-lock.json`; backend gestionado por el BOM de Spring Boot.
