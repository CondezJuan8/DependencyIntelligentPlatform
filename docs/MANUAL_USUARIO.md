# Manual de usuario

La interfaz de revisión activa está en `http://localhost:8189`. Lo que puedes hacer depende de tu organización, repo y permisos.

## Inicio de sesión

1. Abre la interfaz e inicia sesión con una cuenta habilitada.
2. Un registro nuevo puede requerir verificación de correo y aprobación de administrador de plataforma.
3. Elige la organización donde tienes membresía. Ser propietario de plataforma no otorga automáticamente acceso a organizaciones.
4. Ante un error de permisos, pide a OWNER/ADMIN que revise tu membresía, equipo y permisos de ese repositorio.

En una base nueva y vacía se crea primero el administrador mediante bootstrap, una sola vez. No ejecutes el bootstrap sobre una base con datos. Consulta [Acceso local](ACCESO_LOCAL.md).

## Conectar repositorio

### GitHub

1. Ve a **Mis repositorios**, selecciona organización y pulsa **Conectar GitHub**.
2. Autoriza GitHub App; si ya está instalada, introduce el número de instalación del final de la URL de GitHub.
3. Instala la App en la cuenta/organización dueña del repo y concede acceso a los repos que analizarás.
4. Vuelve a la app y selecciona un repo autorizado.

**Login GitHub** identifica al usuario; **GitHub App instalada** autoriza repositorios. OAuth puede estar desactivado aunque la App funcione. GitHub no puede llamar webhooks en localhost; el análisis se inicia manualmente.

### Carpeta local

1. En **Mis repositorios**, pulsa **Importar carpeta local** y selecciona el proyecto.
2. Se omiten elementos excluidos, como dependencias instaladas, compilaciones y credenciales.
3. Espera a que aparezca en carpetas importadas.

La fuente queda en un volumen Docker privado. Importarla no instala dependencias ni ejecuta el código.

## Analizar

1. Desde **Análisis** o el repo, pulsa **Analizar**.
2. La aplicación crea un trabajo consultable; al volver o recargar puede recuperar el trabajo activo. Se puede cancelar entre etapas.
3. El scanner aislado lee la instantánea y crea SBOM CycloneDX.
4. Cuando termine, abre **Inventario** para revisar paquete, versión y clasificación directa/transitiva.

El análisis es manual. Manifiestos incompatibles o versiones no reconocidas pueden dejar cobertura parcial. Un inventario vacío no prueba que no existan dependencias.

## Vulnerabilidades y reportes

En **Vulnerabilidades**, filtra por severidad, repo, paquete o estado. OSV se consulta con el ecosistema y versión exacta.

- **Sin avisos conocidos** significa que OSV no encontró coincidencia en esa consulta.
- No prueba que el paquete esté libre de todo riesgo.
- La cobertura depende de identificar nombre, ecosistema y versión.

En **Reportes**, selecciona repo y tipo. Incluye avisos de severidad media a crítica, resumen ejecutivo no técnico y detalle técnico. **Descargar PDF** crea un archivo independiente de la página. Actualiza análisis y OSV antes si esos datos no han terminado.

## Documentación

En **Documentación** guarda contexto maestro por repo (propósito, arquitectura, reglas, restricciones, decisiones) o referencias generales de vulnerabilidad sin repo asociado. Puedes administrar versiones según tus permisos.

Límite: 50 MiB por archivo, PDF de hasta 500 páginas y hasta 50 páginas OCR. Tika extrae texto y Tesseract procesa PDF cuando hace falta, configurado para inglés/español. El resultado depende de legibilidad.

Al preguntar, Nexo intenta indexar automáticamente los fragmentos nuevos/pendientes. **Preparar documentos para búsqueda** permite reintentar si quedaron pendientes o fallidos.

## Nexo

1. Abre **Nexo**, elige organización y opcionalmente limita por repositorio.
2. Pregunta algo concreto, por ejemplo: **¿Qué avisos OSV tiene este repo y qué paquete los introduce?**
3. Lee la respuesta y revisa las citas/fuentes.
4. **Nuevo chat** separa temas; puedes abrir conversaciones previas o eliminar un chat propio con confirmación.

Nexo recupera documentos y datos autorizados de análisis, OSV e informes. Combina texto y similitud semántica con RRF. Ollama crea embeddings; Gemini redacta con las fuentes seleccionadas. Se envía a Gemini la pregunta y los pasajes recuperados, no el repo entero por defecto. La conversación y citas quedan guardadas.

Nexo es de solo lectura: no cambia archivos ni crea PR. Puede abstenerse si faltan evidencia o permisos. Verifica las fuentes antes de actuar.

- **Embedding:** vector numérico para similitud semántica.
- **RAG:** recuperar evidencia antes de generar respuesta.
- **pgvector:** extensión PostgreSQL para almacenar/comparar vectores.
- **Orquestador:** casos de uso del backend que coordinan etapas; no es servicio externo.

Más en [guía técnica Nexo](OLLAMA_PGVECTOR_RAG.md).

## Organización y permisos

OWNER/ADMIN administra miembros, invitaciones y equipos. El rol limita qué permisos se pueden otorgar. Algunos permisos:

- `repository.read`: consultar repo.
- `scan.create` / `scan.read`: solicitar/leer análisis.
- `document.read` / `document.manage`: leer/gestionar documentos.
- `assistant.query`: usar Nexo.
- `report.read`: ver reportes.

El acceso combina rol, equipo y permiso por repositorio. Ver **Organización** y [guía de permisos](INVITACIONES_EQUIPOS_PERMISOS.md).

## Direcciones y servicios

| URL/host | Uso |
| --- | --- |
| `http://localhost:8189` | UI y API de la aplicación bajo `/api`. |
| `http://localhost:8189/health` | Estado a través del proxy. |
| `http://localhost:8180/swagger-ui/index.html` | Swagger del backend, habilitado en la configuración local. |
| `http://localhost:8180/v3/api-docs` | Especificación OpenAPI JSON. |
| `http://localhost:8126` | Bandeja Mailpit. |
| `127.0.0.1:1126` | SMTP desde host; desde Docker: `mailpit:1025`. |
| `127.0.0.1:5433` | Base PostgreSQL Docker para DBeaver. |

Ollama y cdxgen son internos y no tienen puerto publicado al host. PostgreSQL también está en `database:5432` dentro de Docker, además del acceso local `127.0.0.1:5433`. No uses `localhost:11434` ni `localhost:9090` desde el host.

Para mostrar tablas en DBeaver, crea una conexión PostgreSQL a `127.0.0.1:5433`, base `dependency_intelligence`, usuario `dependency` y contraseña `POSTGRES_PASSWORD` tomada de `.env`. Abre **Schemas → public → Tables**. El puerto `5432` del equipo corresponde a otro PostgreSQL.

Gemini redacta texto (cuota/costo según cuenta); Ollama hace embeddings localmente y necesita descarga inicial; OSV se consulta en `https://api.osv.dev`; GitHub App tiene credenciales propias y callback no es webhook. Mailpit captura correo de desarrollo, no lo entrega a destinatarios reales.

## Problemas frecuentes

| Síntoma | Revisa |
| --- | --- |
| No abre la app | Frontend/puerto 8189; esta instancia no usa 8089. |
| 403 | Organización, membresía, rol, equipo y permisos; para GitHub, instalación/repos autorizados. |
| Nexo no responde | Salud del backend, Ollama/modelo, archivo local Gemini y acceso a fuentes. |
| No encuentra documentos | Documento activo, alcance correcto, indexación; reintenta preparar. |
| Falta un aviso | Análisis finalizado y coordenadas/versión detectadas. |
| No llegó correo | Mailpit en 8126; SMTP interno `mailpit:1025`, no 1126. |

Al reconstruir o reiniciar backend puede ser necesario volver a iniciar sesión porque las sesiones viven en memoria. No compartas claves, tokens ni datos privados.
