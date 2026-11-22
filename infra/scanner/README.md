## Resolución local Maven y comunicación de cobertura (22/11/2026)

Si cdxgen detecta un POM y entrega un SBOM sin componentes, `BoundedMavenArchiveReader` extrae únicamente bytes de POM con límites de entradas, tamaño y expansión. `MavenPomDependencyResolver` crea un modelo local que resuelve propiedades heredadas, POM padre por ruta relativa, versiones gestionadas y BOM de tipo `pom`/scope `import` incluidos en el mismo archivo fuente. Devuelve declaraciones directas confirmadas y su scope. No se inicia Maven ni se ejecutan wrappers, plugins, compilación o scripts; tampoco hay resolución de dependencias desde Internet.

El inventario presenta primero el estado por ecosistema y las dependencias recuperadas. Los elementos sin versión o vínculo verificable se distinguen de hallazgos de seguridad y la explicación técnica es expandible. OSV recibe solo coordenadas/versiones exactas guardadas por el scanner; su resultado permanece separado.

Verificado con fixtures unitarios de herencia local y BOM importado; OMEGA aún debe reanalizarse en una sesión de usuario para confirmar el conteo resultante en la instancia. No se afirma cobertura de perfiles activados por entorno, BOM/POM remotos ni transitivas Maven no incluidas en el SBOM.

# Scanner de dependencias
## Fallback Maven para SBOM vacío (22/11/2026)

La validación de la instancia detectó un scan `cdxgen-maven` real con un POM y ningún componente en CycloneDX. El reader Maven ahora vuelve a abrir el ZIP autorizado en una copia temporal (límite comprimido de 100 MiB) y, solo cuando el SBOM no aporta dependencias pero informa manifiestos, usa `BoundedMavenArchiveReader` para recuperar declaraciones de POM con versión exacta. Los paquetes recuperados se persisten y pueden pasar a la evaluación OSV por separado.

El fallback no resuelve versiones de BOM o POM padre remoto y no ejecuta Maven; tampoco atribuye relaciones transitivas. La cobertura permanece `PARTIAL`, con un mensaje que explica esos límites. Sin versión exacta, el paquete queda contado como no resuelto y no se consulta OSV con coordenadas inventadas.

## Flujo conectado

El botón **Analizar repositorio** llama a `POST /maven-analysis` y `POST /npm-analysis`. Ambos endpoints usan el `RepositoryAnalysisSource` autorizado para abrir el ZIP del commit actual; sus readers invocan cdxgen y guardan componentes, scopes, aristas, procedencia, hash y cobertura con `MavenAnalysisStore`. Después la interfaz solicita `POST /vulnerability-analysis`, que consulta OSV sobre las versiones exactas y persiste cada resultado. El `POST /scans` genérico conserva su función de solicitud; no sustituye estas acciones.

cdxgen corre como servicio separado `cdxgen-scanner`, imagen `ghcr.io/cdxgen/cdxgen-secure:13.8.0` fijada al digest OCI multi-arquitectura `sha256:3a59d95320b5d70f7b369af4ed4ad92c0fe931a426374e0f9df942129e23b8ad`. La imagen y el modo `CDXGEN_SECURE_MODE` mantienen desactivadas las instalaciones automáticas. En modo seguro, cdxgen analiza manifiestos y lockfiles sin invocar Maven ni npm; así no ejecuta scripts, wrappers, plugins ni herramientas de compilación provenientes del repositorio. La versión 13.8.0 es un release oficial que incluye endurecimiento de lectores y rutas. Referencias primarias: [Threat Model](https://github.com/CycloneDX/cdxgen/blob/v13.8.0/docs/THREAT_MODEL.md), [CLI](https://github.com/CycloneDX/cdxgen/blob/v13.8.0/docs/CLI.md), [release 13.8.0](https://github.com/CycloneDX/cdxgen/releases/tag/v13.8.0).

## Aislamiento y límites

- El backend prepara el ZIP en un volumen separado; comprueba rutas, duplicados, hasta 50.000 entradas y hasta 512 MiB descomprimidos, y elimina la carpeta al terminar.
- `scanner-work-init` asigna `/app/scanner-work` al usuario `app` antes de que inicien backend y scanner; esa ruta está permitida por los permisos Node de la imagen. Se conserva la lista de permisos de la imagen y se añade permiso de red para el listener HTTP; el contenedor solo está en una red Docker `internal`, sin salida a Internet ni rutas al host. El backend no sobrescribe los permisos de lectura/escritura temporales. El sidecar monta la fuente en solo lectura; los archivos se dejan en modo lectura para el análisis.
- El scanner ve el volumen en solo lectura, solo está en una red Docker interna, no publica puerto al host ni recibe el socket de Docker. El servidor cdxgen se enlaza al nombre DNS del servicio (`cdxgen-scanner`), no a `0.0.0.0`, que su modo seguro rechaza explícitamente. La lista de hosts Git usa `invalid.invalid`, por lo que no acepta clonar fuentes remotas; la API envía únicamente rutas locales creadas a partir del ZIP autorizado.
- La API envía únicamente una ruta generada por ella, el tipo `java`/`js` y CycloneDX 1.6. La configuración del proyecto no decide comandos ni ubicaciones.
- Contenedor del scanner: filesystem raíz de solo lectura, usuario no root definido por la imagen segura, sin capabilities, `no-new-privileges`, 2 GiB de memoria, 2 CPU, 256 procesos, salida temporal de 512 MiB y límite de ejecución de 5 minutos. La variable `SWIFT_SIGNING_KEY` heredada de la imagen se elimina al iniciar para que el audit seguro no reporte credenciales en un entorno que no usa Swift.
- El mapper limita la respuesta a 25 MiB y 50.000 componentes. La API no ejecuta procesos de contenedor ni necesita acceso a Docker.

La versión y el digest OCI multi-arquitectura quedan fijados para que Compose seleccione la plataforma compatible disponible.

## Cobertura que se informa

- **npm:** cdxgen lee `package-lock.json`, conserva nombres `@scope/paquete` y registra las relaciones disponibles; lockfiles múltiples se analizan juntos. Si faltan versiones exactas o relaciones verificables, se conserva el inventario parcial y se informa el conteo pendiente.
- **Maven:** cdxgen recorre módulos y POM dentro de la fuente, incluidas versiones obtenibles por propiedades, POM padre y `dependencyManagement`/BOM locales. El modo seguro evita ejecutar Maven; por eso no garantiza el árbol transitivo resuelto por Maven y cada análisis Maven se marca **parcial** con esa explicación. Un POM padre remoto o una versión no inferible queda en el conteo sin resolver. La resolución Maven transitiva segura sigue pendiente.
- **OSV:** el adaptador existente envía ecosistemas/nombres/versiones exactos, asocia cada fila batch por posición y sigue `next_page_token` por paquete (con tope defensivo de 100 páginas). Un fallo de consulta deja inventario intacto y queda `UNAVAILABLE`/`INCOMPLETE`; cada aviso conserva ID OSV aunque el detalle no tenga alias, severidad o no se descargue.
- No encontrar resultados es **sin avisos conocidos en OSV** para esa versión consultada. Es distinto de una versión no resuelta o de una consulta incompleta/fallida.

El importador manual CycloneDX acepta Maven y npm, mantiene `bom-ref`, scope y relaciones verificadas. No inventa aristas al faltar el componente intermedio.

## Verificación y límites pendientes

Se ejecutaron 18 pruebas focales de Infrastructure en una copia temporal del backend: mapper CycloneDX Maven/npm, extracción y request al sidecar, lockfile npm scoped, parser CycloneDX, y adaptador OSV con respuestas controladas. Pasaron las 18. También pasó el build de producción Angular.

Se aplicó V25 y se verificó el runtime del scanner 13.8.0 en Compose: `/health` responde y el backend espera su healthcheck. Con fixtures temporales reales, cdxgen encontró dos paquetes npm (scoped directo y dependencia transitiva) con las dos aristas observadas, y dos paquetes Maven entre tres POMs multimódulo con versiones gestionadas/heredadas pero sin aristas; ambos Maven quedan presentes como `UNKNOWN` y con cobertura parcial. No se ejecutó una petición autenticada desde el usuario en la UI.

Se ejecutaron 18 pruebas focales (18 pasaron), 5 `SbomImportIT` con PostgreSQL desechable (5 pasaron) y build Angular (pasó). La consulta OSV se validó con respuestas simuladas, incluyendo paginación, error del proveedor, asociación de resultados batch y vulnerabilidad con ID sin alias/severidad; no se consultó OSV en vivo.
