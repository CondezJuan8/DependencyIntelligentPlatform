# Consulta de vulnerabilidades con OSV

## Alcance implementado

El inventario consulta OSV para las versiones exactas del último análisis completado de Maven y npm. Angular solicita la comprobación al ejecutar **Analizar repositorio** y carga la última evidencia al abrir un repositorio. La API valida primero el permiso de repositorio; la llamada externa ocurre después de completar esa autorización.

- `POST /api/organizations/{organizationId}/repositories/{repositoryId}/vulnerability-analysis`: consulta y guarda una nueva evaluación. Inventario envía `{ "scanIds": [...] }` con hasta dos análisis del repositorio. Las llamadas sin IDs usan los últimos scans por ecosistema.
- `GET /api/organizations/{organizationId}/repositories/{repositoryId}/vulnerability-analysis`: devuelve la evaluación más reciente y su estado de vigencia.
- Las consultas de paquetes usan `POST /v1/querybatch`; los avisos encontrados se completan con `GET /v1/vulns/{id}`.
- `maven` se traduce al ecosistema OSV `Maven` y su nombre `groupId:artifactId`; `npm` conserva nombres scoped como `@scope/paquete`.
- Los lotes se limitan por `OSV_BATCH_SIZE` (1–1000, por defecto 100); se aplican timeouts configurables y paginación acotada.

## Estados y lectura correcta

| Estado | Significado |
| --- | --- |
| `MATCHED` | OSV devolvió uno o más avisos para esa versión exacta. |
| `NO_KNOWN_ADVISORIES` | OSV no devolvió avisos conocidos en esta consulta; no es garantía de que la versión esté libre de vulnerabilidades. |
| `INCOMPLETE` | Quedó sin completar la paginación, una parte de la respuesta o el detalle de un aviso. Los identificadores disponibles se conservan. |
| `UNAVAILABLE` | La consulta no pudo completarse y no hay evidencia nueva utilizable. |
| `STALE` | La evidencia anterior superó `OSV_ASSESSMENT_MAX_AGE` (por defecto 24 horas) o un nuevo intento falló tras una evaluación anterior. Se conserva y muestra el resultado exitoso previo. |
| `NOT_ASSESSED` | Aún no se ha consultado OSV para la versión. |

Cada aviso presenta el identificador, resumen disponible, severidad disponible, vector CVSS y enlaces de fuente. Se guarda el JSON fuente para mantener evidencia verificable; el hash SHA-256 está basado en el JSON canónico de avisos recibidos. Los códigos de error son internos y no sustituyen el estado visible.

## Revisión de exposición en código

Desde el aviso del Inventario se puede pedir una revisión estática del mismo componente y análisis. El endpoint `GET /api/organizations/{organizationId}/repositories/{repositoryId}/vulnerability-analysis/{advisoryId}/code-evidence` requiere `scanId` y `scanComponentId`; consulta el resultado OSV guardado para esa relación antes de abrir archivos. No inicia otro scan ni otra consulta OSV.

La revisión comprueba que el ZIP disponible tenga exactamente el `source_sha256` del scan y, si proviene de GitHub, descarga el commit almacenado en ese scan. Los uploads locales solo se revisan si su ZIP retenido conserva el mismo hash. Un cambio de fuente produce `INCONCLUSIVE` y no se examina el código actual. El proceso solo lee un ZIP con límites; no ejecuta scripts/build/tests y no escribe al repositorio, crea ramas o PR.

`VERSION_AFFECTED` es evidencia OSV, no evidencia de uso del código. `USAGE_DETECTED` y `REACHABLE` requieren reglas estáticas conocidas; la segunda se limita a un parámetro HTTP pasado directamente en el mismo método a `ObjectMapper.readValue(..., Path.class)` para un aviso Jackson específico. `NO_PATH_FOUND` solo aplica a una regla conocida tras lectura completa y no demuestra ausencia de vulnerabilidad. Si no hay regla aplicable, el resultado es `INCONCLUSIVE`. La implementación actual cubre dos avisos `jackson-databind`; para npm puede mostrar importaciones, pero no afirma alcanzabilidad de una función vulnerable sin regla OSV→API. El alcance y pruebas están registrados en el [contexto maestro](DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md) y la [bitácora](BITACORA_TRABAJO.md).

## Persistencia y límites


OSV es una fuente externa susceptible a retrasos, cobertura desigual y fallos de red. Una consulta completa sin avisos indica “sin avisos conocidos según OSV a la hora indicada”. Maven y npm se evalúan por versión exacta. Para actualizar la evidencia de los scans guardados, pulsa **Consultar OSV** en Inventario. Esta acción usa las coordenadas persistidas. El rango propio y la comparación con una segunda fuente siguen pendientes.

## Configuración

Compose expone estas variables opcionales en el servicio backend:

```dotenv
OSV_API_BASE_URL=https://api.osv.dev
OSV_CONNECT_TIMEOUT=PT5S
OSV_READ_TIMEOUT=PT15S
OSV_ASSESSMENT_MAX_AGE=PT24H
OSV_BATCH_SIZE=100
```

La URL requiere HTTPS. No hay claves ni credenciales OSV. Ver también la especificación vigente del [contexto maestro](DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md) y el [informe de esquema](database/implementation-report.md).
