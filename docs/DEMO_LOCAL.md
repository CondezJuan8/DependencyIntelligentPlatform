# Escenario local de la demo

## Estado

F01 dispone de perfil local, bootstrap idempotente y archivos de ejemplo. El importador, las consultas de inventario y sus pantallas siguen pendientes. La pantalla inicial actual no permite todavía cargar SBOM.

El perfil `demo` crea al arrancar, después de Flyway:

| Recurso | Identificador | Valor |
| --- | --- | --- |
| Organización | 11111111-1111-4111-8111-111111111111 | Dependency Intelligence Demo / local-demo |
| Repositorio | 22222222-2222-4222-8222-222222222222 | example/inventory-demo, proveedor demo, referencia main |

El repositorio es un registro sintético. No se clona ni se conecta GitHub. El bootstrap usa JDBC dentro de una transacción: repeticiones no duplican filas y un ID ocupado por otros datos hace fallar el arranque sin sobrescribirlos. Un conflicto de slug o identidad del proveedor también falla; no se corrige borrando datos.

## Arranque completo con Docker

Requiere Docker/Compose y `.env` local configurado a partir de `.env.example`, sin sobrescribir un `.env` existente. Desde la raíz:

```bash
docker compose -p dependency-intelligence-demo -f compose.yaml -f compose.demo.yaml --env-file .env up --build -d
docker compose -p dependency-intelligence-demo -f compose.yaml -f compose.demo.yaml logs backend
curl --fail http://localhost:8088/health
```

El nombre de proyecto crea un volumen de base de datos separado del Compose normal. No ejecutar simultáneamente ambos frontends en el mismo puerto; configurar otro `WEB_PORT` si hace falta. La publicación sigue limitada a `127.0.0.1`; backend y PostgreSQL no publican puertos. El override permite escuchar al backend dentro de la red de contenedores para que Nginx lo alcance.

```bash
docker compose -p dependency-intelligence-demo -f compose.yaml -f compose.demo.yaml down
```

`down` conserva el volumen. No se necesita borrar ni modificar volúmenes existentes para usar la demo.

## Backend con PostgreSQL local

Con Java 21, Maven y una base PostgreSQL dedicada a demostración, configurar `DB_HOST`, `DB_PORT`, `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD`. Desde `backend/`:

```bash
mvn package -DskipTests
java -jar infrastructure/target/infrastructure-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo
```

Este arranque nativo escucha en `127.0.0.1:8080`; health está en `/actuator/health`. No modifica ni crea el frontend. Usar una base dedicada: el perfil crea datos en la base configurada, no crea automáticamente otra base.

## Seguridad y verificación

El perfil normal no registra el bootstrap. En el perfil demo se conserva la política que deniega endpoints de negocio. No se ha implementado todavía un acceso especial de carga/consulta para la demo; se incorporará al desarrollar esas funciones, acotado al escenario local.

Para comprobar específicamente este bloque con PostgreSQL real:

```bash
mvn verify -Pdatabase-it -Dtest=SecurityConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=DemoBootstrapIT -Dfailsafe.failIfNoSpecifiedTests=false
```

Ejecutar desde `backend/`. Requiere Docker para Testcontainers o una base desechable indicada mediante `TEST_DATABASE_URL`, `TEST_DATABASE_USER` y `TEST_DATABASE_PASSWORD`. No apuntar estas pruebas a datos de trabajo.

Consultar la [bitácora](BITACORA_TRABAJO.md) para conocer qué arranque y pruebas se ejecutaron realmente y sus límites.
