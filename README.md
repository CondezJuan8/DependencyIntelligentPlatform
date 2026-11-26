# Dependency Intelligence

Plataforma local para conectar repositorios, analizar dependencias Maven/npm, consultar avisos OSV, documentar contexto y responder preguntas con Nexo.

> **Instancia comprobada el 26/11/2026:** interfaz en `http://localhost:8189`; backend directo en `http://localhost:8180`; Mailpit en `http://localhost:8126` y PostgreSQL Docker en `127.0.0.1:5433`. Los puertos directos se configuran en un override local ignorado por Git.

## Funciones

- Conectar GitHub o importar una carpeta local, con permisos por organización/equipo/repositorio.
- Análisis manual durable; scanner cdxgen genera SBOM CycloneDX para Inventario.
- Evaluación OSV de versiones exactas, revisión de vulnerabilidades, reportes ejecutivos/técnicos y PDF.
- Contexto maestro por repositorio, biblioteca documental y Nexo con citas.
- Sesiones, aprobación de cuentas, invitaciones, equipos, permisos y notificaciones.

## Dominios locales

| Dirección | Uso actual |
| --- | --- |
| `http://localhost:8189` | Frontend Angular; Nginx reenvía `/api` y `/health` al backend. |
| `http://localhost:8189/health` | Estado publicado detrás del proxy. |
| `http://localhost:8180/actuator/health` | Salud del backend directo. |
| `http://localhost:8180/swagger-ui/index.html` | Swagger UI del backend. También abre en `http://localhost:8189/swagger-ui/index.html`. |
| `http://localhost:8180/v3/api-docs` | Especificación OpenAPI JSON. |
| `http://localhost:8126` | Bandeja de Mailpit para correo de desarrollo. |
| `127.0.0.1:1126` | SMTP Mailpit desde el host; entre contenedores: `mailpit:1025`. |
| `127.0.0.1:5433` | PostgreSQL Docker para DBeaver; `5432` en el host pertenece a otra instalación. |

El backend y PostgreSQL siguen comunicándose dentro de Docker como `backend:8080` y `database:5432`; el override publica accesos adicionales solo en `127.0.0.1`. Ollama (`ollama:11434`) y cdxgen (`cdxgen-scanner:9090`) siguen internos. `8088` es el valor predeterminado de `.env.example` para otra instalación; `8089` corresponde a una instancia anterior. Gemini y OSV son HTTPS externos.

## Arquitectura

| Módulo | Función |
| --- | --- |
| `frontend/` | Angular 22, TypeScript y Angular Material. |
| `backend/domain/` | Entidades/reglas, Java independiente de Spring/JPA. |
| `backend/application/` | Casos de uso, DTO y puertos. |
| `backend/infrastructure/` | Spring Boot, REST, seguridad, SQL, configuración y adaptadores. |
| `compose.yaml` / `infra/` | Servicios, redes, volúmenes y bootstrap. |
| `backend/infrastructure/src/main/resources/db/migration/` | Migraciones Flyway; el checkout llega a V37. |

El **orquestador** son los casos de uso de Application, no un servicio aparte como Airflow o n8n. Coordina escáner, base de datos, OSV, recuperación documental y generación; los adaptadores concretos están en Infrastructure.

## Nexo, embeddings y RAG

- **Embeddings:** Ollama convierte texto en vectores numéricos para buscar similitud semántica. Modelo de este Compose: `embeddinggemma-2:latest`. Un vector no es resumen ni respuesta.
- **Persistencia:** PostgreSQL guarda documentos, fragmentos, metadatos, chats y citas; pgvector guarda los vectores asociados.
- **RAG híbrido:** combina similitud vectorial y búsqueda textual PostgreSQL. Reciprocal Rank Fusion (RRF) fusiona rankings y completa hasta seis evidencias en total; las fuentes estructuradas pueden ocupar parte de ese cupo.
- **Reranking:** la fusión RRF es la etapa actual; no es un modelo entrenado.
- **Fuentes:** Nexo suma evidencia autorizada de análisis, OSV e informes.
- **Generación:** Gemini recibe la pregunta y evidencia seleccionada para redactar con citas. Nexo es de solo lectura; sin evidencia debe abstenerse.
- **Indexación:** antes de enviar una pregunta, la interfaz intenta preparar automáticamente fragmentos pendientes. La acción manual permite reintentar.

Ver [guía técnica de RAG](docs/OLLAMA_PGVECTOR_RAG.md) y [manual](docs/MANUAL_USUARIO.md).

## Scanner y OSV

El contenedor aislado `cdxgen-secure:13.8.0` lee una instantánea autorizada y produce un SBOM CycloneDX. No instala las dependencias del proyecto ni accede al socket Docker. Los análisis son manuales, consultables y cancelables.

El backend consulta `https://api.osv.dev/v1/querybatch` con ecosistema, nombre y versión exacta. “Sin avisos conocidos” indica que OSV no encontró coincidencia para esa consulta; no prueba ausencia de todo riesgo. La cobertura depende de nombres/versiones detectables. Ver [OSV](docs/OSV_VULNERABILIDADES.md).

## Base de datos y seguridad

- PostgreSQL 17.7, pgvector 0.8.7 y Flyway.
- Rol runtime limitado; la aplicación fija el tenant por transacción y las tablas tenant-scoped aplican RLS.
- Volúmenes separados para PostgreSQL, repositorios, documentos, scanner y modelos de Ollama.
- `docker compose down` conserva volúmenes. `docker compose down -v` los elimina.
- No reescribas migraciones aplicadas; agrega una nueva.

Los catálogos/diagramas históricos pueden no incluir V37; verifica migraciones e historial Flyway antes de asumir que representan una instancia actual.

## API e integraciones

La API propia se publica detrás de Nginx bajo `/api`. Rutas representativas:

| Área | Rutas |
| --- | --- |
| Sesión | `GET /api/auth/csrf`, `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` |
| Organización | `/api/organizations` y `/api/organizations/{organizationId}/repositories` |
| Análisis | `POST .../repositories/{repositoryId}/dependency-analysis`, `GET /{jobId}`, `/active`, `DELETE /{jobId}` |
| Inventario/OSV | `.../inventory`, `.../inventory/occurrences`, `.../repositories/{repositoryId}/vulnerability-analysis` |
| Reportes | `.../reports/preview` y `.../reports/export-data` |
| Documentación | `.../documents`, `.../documents/search`, versiones/descarga |
| Nexo | `.../assistant/conversations`, `.../assistant/index`, `.../assistant/ask` |

`...` representa `/api/organizations/{organizationId}`. Cambios requieren sesión y CSRF; la UI gestiona el token. OpenAPI/Swagger está deshabilitado por defecto en la plantilla, pero la configuración local actual lo habilita.

Gemini usa `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`; Ollama usa `/api/tags` y `/api/embed` en su endpoint interno; OSV consulta `https://api.osv.dev/v1/querybatch`; GitHub REST usa `https://api.github.com`. SMTP envía correo configurado y Mailpit solo lo captura en desarrollo. PostgreSQL, Ollama y cdxgen son internos.

## Herramientas utilizadas

| Herramienta | Uso |
| --- | --- |
| Docker Engine / Compose v2 | Servicios locales |
| Java 21, Maven, Spring Boot 3.5.16 | Backend |
| Node.js 24, npm 11, Angular 22 | Frontend |
| PostgreSQL 17.7, pgvector 0.8.7, Flyway | Datos, vectores y migraciones |
| Ollama 0.40.17 | Embeddings locales |
| cdxgen Secure 13.8.0 | Scanner/SBOM aislado |
| Apache Tika 4.2.0, Tesseract | Extracción/OCR; PDF hasta 500 páginas y 50 OCR |
| OSV API | Avisos de versiones |
| Gemini 3.1 Flash Lite | Generación Nexo; costo/cuota dependen de cuenta |
| jsPDF | PDF de reportes |
| Mailpit | Correo local de desarrollo |

No se garantiza una cuota gratuita de Gemini.

## Arranque de una instalación nueva

Requiere Docker Compose. En PowerShell, crea `.env` si falta y cambia las contraseñas de ejemplo:

~~~powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# Edita .env; establece contraseñas locales distintas.
docker compose -p dependency-intelligence up --build -d database ollama cdxgen-scanner backend ollama-model-init
~~~

El overlay de UI/Mailpit requiere la red `dependency-intelligence_default` del backend:

~~~powershell
docker compose -p dependency-porfavor-check --profile review -f compose.yaml -f compose.review.yaml up --build -d --no-deps frontend mailpit
~~~

Publica 8189, 8126 y 1126. No lo ejecutes sobre una instancia activa en esos puertos. Revisa nombre del proyecto/red en otros entornos.

### Reiniciar el entorno de revisión actual

El archivo local `.local-secrets/compose.dbeaver.yaml` publica el backend en 8180 y PostgreSQL en 5433, ambos solo en loopback. Está ignorado por Git y no contiene contraseñas. Después de iniciar Docker Desktop, usa:

~~~powershell
docker compose -p dependency-intelligence -f compose.yaml -f .local-secrets/compose.dbeaver.yaml up -d --no-build
docker compose -p dependency-porfavor-check --profile review -f compose.yaml -f compose.review.yaml up -d --no-build --no-deps frontend mailpit
~~~

Los servicios `scanner-work-init` y `ollama-model-init` terminan con código 0 después de preparar sus recursos; no son procesos permanentes.

### Mostrar PostgreSQL en DBeaver

Crea una conexión **PostgreSQL** con host `127.0.0.1`, puerto `5433`, base `dependency_intelligence`, usuario `dependency` y la contraseña `POSTGRES_PASSWORD` del archivo local `.env`. En DBeaver pulsa **Test Connection**, luego abre **Databases → dependency_intelligence → Schemas → public → Tables**. No uses `5432` para esta base Docker: ese puerto lo ocupa PostgreSQL nativo del equipo. La conexión por `5433` se comprobó con contraseña, y la base tenía Flyway aplicado hasta V37 y pgvector 0.8.7.

En una base nueva y vacía, el bootstrap de administrador se ejecuta una sola vez:

~~~powershell
docker compose -p dependency-intelligence --profile bootstrap build platform-admin-bootstrap
docker compose -p dependency-intelligence --profile bootstrap run --rm -it platform-admin-bootstrap
~~~

Luego el administrador crea una organización; el registro normal puede requerir aprobación. Consulta [acceso local](docs/ACCESO_LOCAL.md).

`.env` y `.local-secrets/` están ignorados por Git. Gemini se lee desde `.local-secrets/gemini_api_key` y la clave privada GitHub App desde `.local-secrets/github_app_private_key.pem`; se montan de solo lectura. No pongas claves en frontend, commits, capturas ni chat. Login OAuth y acceso de repositorio GitHub App son flujos distintos.

## Desarrollo

~~~powershell
cd backend
mvn verify

cd ../frontend
npm ci
npm run build
~~~

`mvn verify -Pdatabase-it` usa una base desechable/configurada, nunca datos de usuario. **No se ejecutaron builds ni pruebas para este cambio documental.**

Referencias: [manual](docs/MANUAL_USUARIO.md), [arquitectura](docs/ARQUITECTURA_BACKEND.md), [RAG](docs/OLLAMA_PGVECTOR_RAG.md), [identidad](docs/IDENTIDAD_GOOGLE_GITHUB.md), [equipos/permisos](docs/INVITACIONES_EQUIPOS_PERMISOS.md), [contexto maestro](docs/DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md), [pendientes](docs/PENDIENTES.md).

## Verificación rápida

~~~powershell
docker ps
curl.exe -i http://localhost:8189/health
~~~

El 26/11/2026 frontend, backend, Swagger, OpenAPI, Mailpit y PostgreSQL respondieron; sesión y permisos se comprueban dentro de la aplicación.
