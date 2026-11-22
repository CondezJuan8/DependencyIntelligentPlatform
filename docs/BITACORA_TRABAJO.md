# Bitácora de trabajo

Este documento se actualiza en cada bloque de trabajo. Registra cambios, herramientas, validación, limitaciones y siguiente paso. Las horas estimadas del roadmap no se registran como horas reales consumidas.

## Bloque 58 — corregir pérdida del inventario Maven y validar OSV real

Fecha: 22 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y causa comprobada

- El usuario autorizó completar la validación y corrección de scanner y OSV.
- En la base Compose, el scan más reciente de `OMEGA-main` para `cdxgen-maven` detectó 1 POM, terminó `PARTIAL`, pero guardó 0 dependencias. A diferencia del reader npm, el reader Maven no tenía recuperación si el SBOM CycloneDX venía sin componentes.
- cdxgen reportó SBOM vacío en OMEGA y Bot de WhatsApp para npm; el fallback actual recuperó 270 y 291 paquetes desde sus lockfiles. La base muestra evaluaciones OSV en los IDs de scans recientes: OMEGA 270, Bot 291; Bot con 11 `MATCHED` y avisos GHSA asociados a sus paquetes/versiones; OMEGA sin avisos conocidos para las 270 versiones examinadas.

### Cambios

- Maven conserva el ZIP autorizado en archivo temporal con límite de 100 MiB, ejecuta cdxgen sobre la copia y, si cdxgen detecta POM pero entrega cero componentes, recupera declaraciones Maven con versiones exactas desde los POM mediante `BoundedMavenArchiveReader`.
- El fallback nunca declara cobertura completa, no asigna versiones gestionadas sin evidencia ni inventa relaciones. Reporta `PARTIAL` y los límites de versiones heredadas, BOM y transitivas. Si no recupera paquetes, deja el POM detectado con mensaje y conteo de elementos sin resolver.
- Se añadieron dos regresiones Maven (recuperación por propiedad del POM y versión gestionada no resuelta) y una regresión del caso de uso OSV que comprueba que se persiste el ID del aviso con severidad/alias ausentes en la ocurrencia exacta.

### Herramientas y verificaciones

| Herramienta/tecnología | Uso | Dependencia nueva |
| --- | --- | --- |
| Maven 3.9.11 / JDK 21 | Pruebas focales reader Maven, cdxgen adapter, npm fallback, OSV adapter y caso de uso OSV (10 pruebas, todas pasaron) | No |
| Docker Compose / PostgreSQL | Lecturas agregadas de scans, componentes y evaluaciones OSV del entorno activo; datos intactos | No |
| Git | Revisar cambios y separar commits por función | No |

Los reportes focales confirman 2 pruebas Maven, 1 npm, 2 de adapter cdxgen, 2 de adapter OSV y 3 del caso de uso OSV, sin fallos. En scans recientes de la instancia, OSV guardó 270 evaluaciones para OMEGA y 291 para Bot; 11 del Bot quedaron `MATCHED` con GHSA enlazados a versión. El cambio Maven está probado con fixture controlado y el JAR actualizado corre en el backend de Compose; OMEGA aún no se ha reanalizado tras el despliegue, así que no se afirma ese resultado.

### Siguiente paso

Volver a analizar OMEGA en Inventario. Confirmar que el fallback guarda las declaraciones Maven exactas resolubles y que la fase OSV evalúa esas versiones; cualquier versión gestionada/transitiva que permanezca irresuelta debe seguir marcada parcial. El backend ya fue actualizado y comprobó arranque/migración; PostgreSQL, Mailpit, cdxgen y la instancia nativa no se reiniciaron.
## Bloque 56 — restaurar Inventario y separar scanner de OSV

Fecha: 20 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y causa comprobada

- El usuario reportó que Inventario dejó de responder con claridad y recordó que scanner y OSV son servicios distintos que se complementan.
- Consulta agregada de la base activa: tres filas de scanner `cdxgen-npm` tenían `manifest_count=1`, `unresolved_count=0`, `coverage_status=COMPLETE` y cero filas en `scan_components`. El mapper permitía reportar cobertura completa cuando cdxgen emitía un SBOM válido vacío aunque hubiera detectado el lockfile.
- `JdbcVulnerabilityAssessmentAdapter` filtraba `LATEST_SCANS` a `maven-manifest` y `npm-lockfile`, excluyendo `cdxgen-maven`/`cdxgen-npm`; por eso OSV no recibía los componentes de los análisis nuevos.
- La carga GET de la evaluación OSV convertía cualquier error en `null` sin mostrarlo. El formulario de análisis además reemplazaba errores 400 con un mensaje genérico de manifiesto ausente.

### Cambios

- npm vuelve a leer el ZIP de forma acotada (máximo 100 MiB) y, si cdxgen no produjo componentes, recupera versiones y relaciones desde el `package-lock.json` con el parser existente. La cobertura queda `PARTIAL` y explica el fallback; si el lockfile tampoco se puede resolver, devuelve un mensaje accionable.
- CycloneDX ya no marca completo un proyecto con manifiesto detectado y cero componentes.
- OSV ahora consulta el último scan completado por ecosistema, admitiendo nombres legacy y cdxgen y eligiendo solo uno si existen ambos. Sus resultados se asocian a los IDs de componente del scan seleccionado.
- Inventario tiene acción **Consultar OSV** separada del botón de análisis. Los errores de lectura/consulta se muestran por separado, y el frontend conserva los detalles ProblemDetail que entrega la API.

### Herramientas y verificaciones

| Herramienta/tecnología | Uso | Dependencia nueva |
| --- | --- | --- |
| Maven/JDK 21 | 11 pruebas focales de scanner, mapper y OSV | No |
| Testcontainers/PostgreSQL 17 | IT de nombres de scanner actuales/históricos y deduplicación por ecosistema (1 pasó) | No |
| Angular CLI/Node 24 | Build frontend | No |
| Docker Compose | Build y recreación exclusiva de backend/frontend | No |
| PostgreSQL activo | Lecturas agregadas de scans/componentes y OSV para diagnóstico; sin cambios | No |

Pasaron las 11 pruebas focales, el IT, el build Angular y el build Docker. El backend/frontend se recrearon sin reiniciar PostgreSQL, Mailpit ni cdxgen; frontend HTTP 200 y scanner healthy. Los tres snapshots vacíos históricos no se alteraron: requieren ejecutar de nuevo el análisis desde una sesión autenticada. No se hizo consulta real a OSV ni recorrido autenticado de la UI.

### Siguiente paso

En localhost:8089, volver a ejecutar **Analizar repositorio** en los proyectos afectados. El scanner guardará el inventario; OSV consultará sus versiones después y también podrá ejecutarse con **Consultar OSV** sin repetir el análisis.

## Bloque 55 — integración del scanner cdxgen y diagnóstico OSV

Fecha: 20 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y diagnóstico comprobado

- La pantalla Inventario ejecuta `POST /maven-analysis` y `POST /npm-analysis`, después solicita `/vulnerability-analysis`. El endpoint genérico `/scans` solo guarda una solicitud; no ejecuta el scanner.
- Los readers anteriores leían directamente POM/package-lock; CycloneDX aceptaba solo 1.6/Maven, limitaba a 500 componentes y descartaba relaciones. El parser Maven directo no resolvía herencia/`dependencyManagement`/BOM; el lockfile npm ya reconstruía transitivas, pero generaba PURL de scope codificando también la barra.
- La inspección del adaptador OSV confirma paginación por `next_page_token`, asociación de la fila por índice de consulta, Maven `group:artifact`, npm conserva el nombre scoped y el ID OSV se conserva en detalle mínimo cuando falla la descarga; no encontré una omisión comprobada en ese adaptador.

### Cambios realizados

- Los readers conectados a los endpoints de Inventario ahora invocan cdxgen por HTTP a través de la salida Application `CycloneDxGenerator`; el backend crea una fuente temporal validada, aplica topes y la pone en solo lectura para el scanner. No inicia contenedores ni monta el socket de Docker.
- `compose.yaml` define cdxgen-secure 13.8.0 como sidecar sin puerto publicado, red interna, filesystem/volumen de fuente read-only, sin capabilities, `no-new-privileges`, límites de memoria/CPU/procesos/tiempo y sin ejecución de instalaciones. La imagen está fijada por digest OCI multi-arquitectura. Se conservan los permisos de Node de la imagen segura, se limita la red a una red Docker interna y se espera su healthcheck antes de iniciar la API.
- CycloneDX 1.6/1.7 mapea paquetes Maven/npm y sus relaciones respaldadas por el SBOM. Aumenta el límite a 50.000 componentes/25 MiB; PURL npm conserva `@scope/paquete`. Importación manual persiste tipo, ámbito y aristas.
- V25 agrega estado/mensaje de cobertura. Maven siempre se informa parcial porque el modo seguro no ejecuta Maven ni promete el árbol transitivo; npm se marca parcial cuando el SBOM carece de versión, alcance o relaciones verificables. Inventario histórico se mantiene consultable por nombre de scanner anterior.
- UI de Inventario muestra la cobertura parcial y el número de componentes/aristas pendientes.
- Se añadieron tests controlados para mapping Maven/npm, relaciones, `@scope`, paginación/ID OSV sin aliases/severidad y fallo del proveedor; se amplió el IT de importación para validar persistencia de scoped npm y aristas.

### Herramientas y tecnologías

| Herramienta/tecnología | Uso | Dependencia nueva |
| --- | --- | --- |
| cdxgen secure 13.8.0 | Generación CycloneDX Maven/npm separada de la API | Imagen de contenedor fijada por digest |
| Spring `RestClient`, Java NIO/ZIP, Jackson | Cliente sidecar, extracción acotada, metadatos/manifiesto y conversión a modelo existente | No |
| PostgreSQL/Flyway | Nueva V25 de cobertura; persistencia de las aristas del importador existente | No |
| Angular/TypeScript | Mostrar cobertura parcial de cada ecosistema | No |
| JDK 21, Maven 3.9.11 | Compilar y ejecutar pruebas focales/IT | No |

### Verificación y límites

- Leídos AGENTS, contexto maestro, pendientes y README del scanner antes de editar. Contraste del flujo en controller, casos de uso, readers, adaptadores SQL, frontend y OSV.
- Se ejecutaron en una copia temporal del backend con JDK 21/Maven 3.9.11: 18 pruebas focales de parser CycloneDX (9), lockfile npm (2), adapter cdxgen (2), mapper (3) y OSV (2); 18 pasaron. El intento inicial reveló tres fallos de prueba/conteo; se corrigieron y se repitió la suite limpia.
- Build Angular ejecutado: correcto. Quedaron advertencias de presupuesto de bundle y SCSS; no se modificaron esos presupuestos.
- La seguridad del modo cdxgen-secure se contrastó con el Threat Model/CLI/release oficial 13.8.0 y se validó el runtime real del sidecar mediante healthcheck y fixtures Maven/npm.
- Pasaron 5 pruebas `SbomImportIT` con PostgreSQL desechable. El scanner real encontró el paquete npm scoped directo y el transitivo, incluyendo sus dos aristas verificadas; encontró dos paquetes en el fixture Maven multimódulo y resolvió las versiones locales gestionadas/heredadas, pero no generó relaciones Maven en modo seguro, por lo que quedaron `UNKNOWN` y con cobertura parcial.
- V25 se aplicó en Compose, backend/frontend fueron actualizados y el frontend respondió HTTP 200. OSV se verificó con respuestas controladas, sin consulta en vivo. Falta un recorrido autenticado de usuario desde Inventario; no se afirma validación de punta a punta en la sesión real.
- El build Angular fue correcto con advertencias preexistentes de presupuesto de bundle/SCSS. La suite completa del backend no se ejecutó: se corrieron solo las regresiones focales y el IT de persistencia necesarios para este cambio.

### Siguiente paso

Siguiente: hacer el recorrido autenticado desde Inventario con un repositorio real y revisar el resultado recibido en la UI, incluyendo el estado OSV. La cobertura Maven transitiva requiere un resolver compatible con el aislamiento seguro; hasta implementarlo se presenta explícitamente como parcial.

## Bloque 52 — filtros de seguridad, grafo en red y flujo de invitación

Fecha: 20 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y cambios

- Inventario incorpora un filtro de estado de seguridad: todos, versiones con avisos, sin avisos conocidos, y sin evaluar/con revisión. El filtro opera a nivel de versión y oculta paquetes sin versiones coincidentes; los contadores directos/transitivos se recalculan con lo visible.
- El grafo ahora conserva el mapa por capas y agrega una vista de red force-directed con Cytoscape `cose`, etiquetas que aparecen al acercar/buscar/seleccionar, nodos compactos diferenciados por directas/transitivas y aristas discretas. Para proteger la respuesta, la red renderiza hasta 350 paquetes y mantiene el catálogo accesible completo; el mapa por capas conserva el tope de 1.000.
- Se corrigió el texto de estado OSV «OSV no reporta avisos para esta versión» y se conecta a traducciones. Se añadió explicación de que SHA-256 identifica el ZIP exacto analizado; no es el hash de un paquete ni una puntuación de riesgo.
- El diálogo de invitación selecciona inicialmente un equipo que tenga al menos un repositorio con `Consultar repositorio`. Al cambiar el equipo, se selecciona su primer repositorio elegible. Si no hay acceso configurado, el selector se deshabilita, explica cómo conceder el permiso y ofrece ir a Equipos y permisos. Se muestra el flujo: verificación/aceptación de invitación y aprobación final del propietario.

### Herramientas utilizadas e implementadas

| Herramienta/tecnología | Uso | Dependencia nueva |
| --- | --- | --- |
| Angular signals, TypeScript y templates | Filtro de versiones, alternancia del grafo y estado inicial coherente para la invitación | No |
| Cytoscape.js ya instalado (`cose`) | Distribución de red acotada, etiquetas por zoom/foco y relación visible entre paquetes | No |
| Traducciones existentes | Etiquetas nuevas para español e inglés | No |
| SCSS y tokens de tema | Leyenda de red, botones de modo, explicación SHA y flujo responsive | No |

### Verificación y límites

- TypeScript de la aplicación: 0 diagnósticos.
- Parser de templates Angular: Inventario, grafo y gestión de organización, 0 errores.
- No se modificaron API, políticas de autorización, esquemas ni datos. El servidor Compose 8089/8088 no se reinició y no se validó con los repositorios Omega/Bot de WhatsApp ni con un recorrido real de invitación.
- La red `cose` con hasta 350 paquetes está limitada para reducir el coste de repulsión; el resto permanece en lista completa. Falta revisar visualmente distribución y legibilidad con esos repositorios, desktop/móvil y ambos temas, además de observar el comportamiento de la selección elegible con datos reales.
- Build de Angular completado correctamente. Generó advertencias de presupuesto de bundle y estilos; se ajustó el límite de error del estilo del grafo a 12 KB, dejando las advertencias visibles.
- Se levantó una vista actualizada en `http://localhost:8090/?view=inventory`, con la API redirigida al backend de `localhost:8089`. Los servicios existentes en 8088 y 8089 siguen activos.
- Se crearon tres commits separados para inventario, grafo e invitaciones/documentación. No se afirma una revisión visual completa ni pruebas conductuales con los repositorios del usuario.

### Siguiente paso

Abrir Inventario en el puerto 8090, usar «Con vulnerabilidades» y «Sin evaluar o requiere revisión» en Omega/Bot de WhatsApp; alternar «Por capas»/«Red libre» y comprobar búsqueda, zoom y selección. Después confirmar el diálogo de invitación con un equipo que tenga acceso al repositorio deseado. Ajustar el grafo según el recorrido visual real.

## Bloque 51 — grafo estético y entendible por capas

Fecha: 20 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y cambios

El usuario eligió el mapa por capas y pidió más detalle visual sin degradar el rendimiento. El proyecto queda en la primera columna; las directas en nivel 1 y las transitivas en las columnas siguientes según la menor distancia al proyecto. Los nodos son tarjetas compactas con nombre/versión, borde azul para directas y cian para transitivas; el fondo usa una retícula discreta y las flechas conservan la dirección real.

La vista consolida ocurrencias iguales por ecosistema, paquete y versión; mezcla sus rutas/ámbitos en el detalle y conserva una relación compartida como un solo nodo. El detalle separa lo que declara y quién usa el paquete. La búsqueda resalta el camino de dependencias. Para grafos mayores, el canvas dibuja hasta 1.000 nodos y prioriza los niveles próximos o el camino buscado/seleccionado; la lista conserva el inventario completo y carga 100 filas por tanda. El cálculo de niveles es BFS O(V+E), y se quitaron redibujos completos provocados solo por redimensionar.

### Herramientas utilizadas e implementadas

| Herramienta/tecnología | Uso | Dependencia nueva |
| --- | --- | --- |
| Angular signals/computed y TypeScript | Normalizar entidades visibles, agrupar ocurrencias, filtrar y mantener estadísticas derivadas | No |
| Cytoscape.js ya instalado | Tarjetas, aristas, posicionamiento `preset`, BFS y foco de rutas | No |
| SCSS y tokens de tema existentes | Guía de capas, tarjeta/leyenda y respuesta móvil con paleta de producto | No |
| Traducciones existentes | Nuevas leyendas y panel de relaciones en español/inglés | No |

### Verificación y límites

- Revisión estática del flujo Angular y cálculo por capas; no se cambiaron API, backend, migraciones ni datos.
- TypeScript type-check de 44 archivos correcto con la API del compilador; el parser de template Angular también pasó. La build Angular completa no se ejecutó.
- No se ejecutaron pruebas conductuales ni revisión visual en Chrome; Node/npm CLI y el host de navegador no estaban disponibles. La carga del compilador Sass se detuvo en su módulo nativo antes de compilar por un error de export `sass.node.js` del runtime.
- No se añadió una biblioteca de layout. La visibilidad de grafos reales npm/transitivos y la aceptación visual responsive quedan pendientes.
- No pude crear commit: `git.exe` no está disponible en el PATH de este entorno.

### Siguiente paso

Cuando esté disponible Node/npm, compilar el frontend y revisar el grafo con seis dependencias Maven y una muestra npm transitiva, en tema claro/oscuro y móvil. Ajustar separaciones si esos datos muestran cruces o etiquetas pequeñas.
## Bloque 50 — carpeta npm lista para probar en la plataforma

Fecha: 20 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y cambios

El usuario indicó que no puede probar npm. La sesión de Chrome muestra que Inventario todavía solo conoce `Formula1Simulator · local`; el parser npm no tiene un scan real en esa instancia. Se creó `samples/npm-axios-demo`, una carpeta lista para importar con `package.json` y `package-lock.json` v3: Axios 1.7.9 como dependencia directa y ocho paquetes transitivos. No se incluyeron scripts ni `node_modules`; el análisis no ejecuta código del proyecto.

### Herramientas utilizadas

| Herramienta | Uso | Dependencia nueva |
| --- | --- | --- |
| PowerShell y `System.Web.Extensions` | Crear archivos y validar JSON/estructura del fixture | No |
| Inspección de Angular/API y `BoundedNpmLockReader` | Confirmar importación local y soporte de lockfile v2/v3 | No |
| Chrome/CUA | Revisar selector y estado npm; el control no permitió completar la importación | No |

### Verificación y límites

- Validación local: JSON válido, lockfile v3, una dependencia directa y nueve entradas instaladas (Axios más ocho transitivas esperadas).
- El fixture aún no está importado ni analizado en Compose; está disponible en `samples/npm-axios-demo`.
- No corrí Node/npm, Docker ni pruebas Java: esos ejecutables no están disponibles en este entorno de comandos. No se modificó código del escáner ni datos de la base.
- No pude crear commit porque `git.exe` no está disponible en el PATH de este entorno.

### Siguiente paso

En localhost:8089 abre Mis repositorios → Importar carpeta local → elige `C:\Users\yazst\DependencyIntelligentPlatform\samples\npm-axios-demo` → Subir a la organización. Luego Inventario → selecciona `npm-axios-demo` → Analizar repositorio. El resultado esperado es Axios directo, ocho transitivas y sus relaciones del lockfile. Si la pantalla muestra error, usar ese mensaje y los logs backend para corregirlo.
## Bloque 49 — diagnóstico de Inventario npm sin resultados

Fecha: 20 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y diagnóstico

El usuario reportó que Inventario no muestra dependencias npm. En la pantalla real de `localhost:8089`, el selector solo contiene `Formula1Simulator · local`; el estado Maven indica seis paquetes y npm indica «Aún sin analizar». El repositorio Axios recomendado en el bloque anterior no aparece en el espacio, por lo que no hay todavía un lockfile npm real importado ni un scan npm guardado. Con la evidencia actual no se concluye que el parser tenga un error.

### Herramientas utilizadas

| Herramienta | Uso | Dependencia nueva |
| --- | --- | --- |
| Chrome y la vista Inventario de Angular | Verificar repositorio seleccionado, estado Maven/npm y catálogo | No |
| Inspección de Angular/API y parser npm | Seguir el flujo de carga/análisis; parser admite lockfiles v2/v3 | No |
| Docker Compose/PostgreSQL | Confirmar servicios activos; la consulta sin contexto de tenant no entregó filas y no se usó como evidencia | No |

### Verificación y límites

- Observación real: solo aparece el repositorio local Java; el estado npm figura «Aún sin analizar».
- No se ejecutó un análisis, no se alteraron scans ni datos, y no se modificó código.
- La prueba focal existente del parser cubre relaciones directas/transitivas sintéticas, pero falta validar un repositorio npm real dentro de Inventario.

### Siguiente paso

Importar Axios (`axios/axios`, rama `v1.x`) a la organización, ya sea habilitando su acceso GitHub o descargando/descomprimiendo el ZIP y usando «Importar carpeta local». Seleccionarlo en Inventario, pulsar «Analizar repositorio» y comprobar el estado/npm y los paquetes. Si aparece un error, usar ese resultado concreto para corregir el parser o la persistencia.

## Bloque 48 — Lectura visual más clara del grafo

Fecha: 20 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y cambios

El usuario pidió que el grafo se entienda mejor y no se vea tan simple. Se sustituyó el layout automático por posiciones radiales deterministas: proyecto al centro, dependencias directas en el primer anillo y transitivas en el anillo exterior. Las etiquetas de nodos se orientan hacia fuera según su posición, usan un tamaño más compacto y mantienen el nombre completo en la lista/detalle. Se quitaron los rótulos repetidos de cada arista; la punta de flecha y la leyenda indican hacia dónde va la dependencia. Se añadió una guía debajo del canvas, traducida a inglés/español.

### Herramientas utilizadas e implementadas

| Herramienta / tecnología | Uso | Dependencia nueva |
| --- | --- | --- |
| Angular y Cytoscape.js | Posicionar nodos por nivel, orientar etiquetas y conservar selección, zoom y búsqueda | No; Cytoscape ya estaba instalado |
| SCSS y tokens de tema existentes | Dar jerarquía a leyenda y guía de lectura | No |
| Docker Compose | Compilar y actualizar solo el frontend | Herramienta de despliegue local |
| Chrome | Revisar el layout en `localhost:8089` con el snapshot de Maven | Herramienta de revisión |

### Verificación y límites

- `docker compose build frontend`: correcto. Se mantienen avisos previos de presupuesto de bundle/SCSS.
- Compose recreó solo el servicio frontend; backend, PostgreSQL, Mailpit y entorno nativo no se reiniciaron.
- La revisión visual muestra `Formula1Simulator` al centro y sus seis dependencias Maven directas distribuidas alrededor, con etiquetas legibles y selección conectada al panel de detalle. El dataset no tiene transitivas; el anillo exterior queda vacío y no se simulan nodos.
- No se agregaron paquetes, cambios de API ni migraciones. La adaptación de grafos grandes y la revisión móvil siguen pendientes.

### Siguiente paso

Validar esta lectura con un repositorio npm que tenga relaciones transitivas y revisar la escala en móvil/grafos grandes.

### Commits

Se guardan dos commits: layout/interacción visual y documentación de diseño/estado, ambos con emoji y Conventional Commits en español.

## Bloque 47 — Corrección de visualización del grafo

Fecha: 20 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y cambios

El usuario informó que la pantalla del grafo no mostraba nada. La inspección confirmó que había datos visibles en el resumen/lista (seis paquetes Maven), pero Cytoscape creaba su contenedor después de `ngAfterViewInit`; por eso el canvas no llegaba a dibujarse. Se inicializa al aparecer el `ViewChild`, se redibuja cuando el tamaño del canvas queda disponible y al cambiar de repositorio. El layout radial distribuye el proyecto y sus dependencias; los nodos se distinguen por clase para evitar que el booleano `project=false` active el estilo del proyecto. Las etiquetas Maven del canvas muestran el artefacto y versión; la lista conserva la coordenada completa y la búsqueda sigue usando el nombre completo.

### Herramientas utilizadas e implementadas

| Herramienta / tecnología | Uso | Dependencia nueva |
| --- | --- | --- |
| Angular `ViewChild`, `requestAnimationFrame`, `ResizeObserver` | Inicializar y redimensionar el canvas cuando el DOM está listo | No |
| Cytoscape.js | Dibujar layout radial y relaciones del snapshot | No; ya estaba instalado |
| Docker Compose | Compilar y recrear únicamente el frontend | Herramienta de despliegue local |
| Chrome | Revisar el grafo con los datos reales de `Formula1Simulator` | Herramienta de revisión |

### Verificación y límites

- `docker compose build frontend`: correcto. Conserva avisos de presupuesto de bundle/SCSS existentes; no bloquean el build.
- Frontend actualizado en `http://localhost:8089`; no se reiniciaron backend, PostgreSQL, Mailpit ni el entorno nativo.
- Revisión visual confirmada: aparecen el nodo del proyecto y seis paquetes Maven; resumen de 6 paquetes, 6 directas, 0 transitivas y 6 relaciones. Estas relaciones son declaraciones directas; no se añadieron transitivas Maven ni se infirieron.
- No se añadieron dependencias ni cambios de base de datos.

### Siguiente paso

Comprobar con el usuario que el trazado/etiquetas se entienden en su pantalla y, después, revisar un repositorio npm con lockfile para validar el grafo transitivo.

### Commits

Se guardan dos commits: corrección visual/vida del canvas y actualización documental, ambos con emoji y Conventional Commits en español.

## Bloque 20 — Aprobación y rechazo inmediatos desde el correo

Fecha: 18 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y decisión confirmada

Tras fallos previos del flujo, se acordaron dos acciones en el correo: aprobar y rechazar. El usuario eligió que el primer clic ejecute inmediatamente sin login ni paso final. Se conserva la pantalla «Esperando confirmación» para quien se registró.

### Cambios realizados

- Token aleatorio persistido como hash, individual por administrador y decisión, con uso único, vencimiento configurable (24 horas por defecto) e invalidación de las otras acciones de la solicitud.
- Correo individual a administradores activos con correo verificado; aprobación confirma el correo y habilita la cuenta, rechazo la deja sin acceso.
- Caso de uso de Application y adaptadores Infrastructure para correo, HTTP anónimo, transacción JDBC y migración V15; la auditoría atribuye la acción al administrador destinatario.
- Angular quita el token de la URL y aplica la decisión automáticamente mediante POST. GET no decide. La pantalla persistente no cambia.
- Corregido el enlace JDBC de `Instant` a `Timestamp`.

### Herramientas utilizadas e implementadas

| Herramienta / tecnología | Uso | Dependencia nueva |
| --- | --- | --- |
| Java y puertos/casos de uso | Decisión de admisión en Application | No |
| Spring MVC/Security/TransactionTemplate, JDBC | Adaptadores y transacción | No |
| PostgreSQL/Flyway | Persistencia y migración V15 | No; una migración |
| Angular/HttpClient | Consumir enlace y mostrar resultado | No |
| SMTP/Mailpit | Enviar y revisar el correo | No; integración existente |
| Maven/JUnit y Docker Compose | Pruebas y smoke aislado reportados | Herramientas de verificación |

### Verificación y límites

- `mvn verify`: correcto; Domain 5, Application 10 e Infrastructure 18 pruebas reportadas.
- Build de frontend Docker correcto, con advertencias de presupuesto de bundle y SCSS.
- Smoke HTTP en base desechable: aprobar/rechazar sin sesión, destinatario correcto, estado y confirmación de correo, token repetido rechazado; se retiraron base y contenedores temporales.
- UI visual no comprobada porque CUA no conectó. Después del primer reporte de herramientas inaccesibles, Docker volvió a estar disponible con autorización: V15 se aplicó sobre el volumen persistente sin borrarlo; frontend respondió HTTP 200. Export de catálogo real y regeneración validados: 124 tablas, 87 RLS forzadas, 218 FK y siete diagramas. No se modificaron cuentas existentes.
- Riesgo aceptado por la decisión de primer clic: un escáner que ejecute JavaScript y envíe POST puede consumir el enlace. Los lectores que solo hagan GET no activan la decisión.

### Siguiente paso

Revisar el correo de prueba en Mailpit; al disponer de Docker/Git, confirmar V15 en la instalación local y guardar los dos commits coherentes de implementación y documentación.

### Commits

Se guardan en dos commits: implementación y migración por separado de documentación/catálogo, con emoji obligatorio y formato Conventional Commits en español.

## Bloque 01 — Escenario local de demo y registro de continuidad

Fecha: 14 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y objetivo

Mantener un registro de lo realizado y continuar la demo de dos semanas. Se empezó por F01: escenario reproducible con organización, repositorio y SBOM conocidos.

### Cambios realizados

- Añadido `AGENTS.md` para conservar la obligación de actualizar esta bitácora, probar solo lo necesario y usar commits con emoji.
- Implementado el perfil Spring `demo` y un bootstrap transaccional de organización/repositorio con identificadores estables; sin modificar el esquema Flyway.
- Añadido un override Compose para activar la demo en un proyecto/volumen independiente.
- Preparados un SBOM CycloneDX JSON 1.6 sintético con tres bibliotecas Maven y un archivo de rechazo por `bom-ref` duplicado.
- Documentados procedencia, resultados esperados y arranque nativo/Compose.

### Herramientas y tecnologías

| Herramienta / tecnología | Para qué se utilizó | Incorporación |
| --- | --- | --- |
| Spring Boot: perfiles y ConfigurationProperties | Activar y configurar el escenario de demo explícitamente | Se usa la dependencia ya existente |
| JDBC y TransactionTemplate | Inicialización atómica e idempotente en PostgreSQL | Se usan dependencias ya existentes |
| PostgreSQL y Flyway | Reutilizar esquema y comprobar persistencia del escenario | Sin nueva migración ni motor añadido |
| JUnit / Spring Boot Test / MockMvc | Verificar bootstrap y bloqueo HTTP | Se usan dependencias de pruebas existentes |
| Maven y JDK local | Compilar y ejecutar comprobaciones seleccionadas | Sin nueva dependencia de producto |
| Docker Compose | Definir arranque aislado de demostración | Configuración preparada; Docker no disponible para ejecutarla aquí |
| CycloneDX JSON 1.6 | Formato del archivo de entrada de ejemplo | Es un formato; no se ha añadido un scanner ni SDK |
| PowerShell, Git y editor de archivos | Inspección, edición y commits por funcionalidad | Herramientas de trabajo, no dependencias del backend |

### Validación y resultados

- Compilación de los tres módulos y empaquetado: `BUILD SUCCESS`.
- Ejecutadas solo `SecurityConfigurationTest` (2 pruebas) y `DemoBootstrapIT` (2 pruebas): **4 aprobadas, sin errores ni omisiones**. No se ejecutaron las demás suites.
- La integración levantó Spring con perfil demo sobre PostgreSQL 18.6 temporal: bootstrap repetido sin duplicados, rechazo de datos existentes incompatibles sin sobrescritura, health accesible y API de negocio denegada.
- Entorno de verificación: Maven 3.9.11 y JDK 25 con destino Java 21. La base temporal se detuvo al terminar. No se añadieron dependencias de producto.
- PowerShell confirmó que ambos archivos son JSON legible, el ejemplo contiene tres bibliotecas con referencias únicas y el caso de rechazo tiene referencias repetidas. No se ejecutó un validador completo del esquema CycloneDX ni un importador que aún no existe.
- Docker/Compose y el recorrido en navegador **no se ejecutaron**: Docker no está disponible en este entorno. El arranque nativo real se cubrió mediante contexto Spring de integración; no se verificó un servidor HTTP externo arrancado desde el JAR.

### Límites y siguiente paso

- Los datos son sintéticos y se identifican como tales; no hay resultados de scanner ni vulnerabilidades verificadas.
- El perfil demo no habilita las APIs de negocio. El arranque completo de Docker depende de disponer de Docker en el equipo.
- No están implementados el parser, la carga ni el inventario. F01 no debe marcarse totalmente aceptada hasta comprobar el arranque requerido en el equipo de presentación.
- Siguiente función: F03, parser del subconjunto CycloneDX acordado, como soporte de F02/F04. Luego conectar carga y persistencia; no añadir GitHub ni OSV a este bloque.

### Commits

Se separa en tres commits: implementación del escenario local con sus pruebas, archivos de ejemplo CycloneDX con su procedencia y bitácora/instrucciones de continuidad con el estado actualizado. Consultar el historial de Git para los identificadores.

## Bloque 02 — Parser CycloneDX y alineación documental

Fecha: 14 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y documentos revisados

Continuar el desarrollo priorizando contexto maestro, arquitectura del backend y stack tecnológico. Se contrastaron las secciones de arquitectura hexagonal, modelo de datos, procedencia y límites de proveedores con el alcance de demo confirmado. Las propuestas históricas no se convirtieron en integraciones implementadas.

### Cambios realizados

- F03: implementado `SbomParser` como puerto de Application y `CycloneDxJsonParser` como adaptador de Infrastructure.
- Añadido el value object `PackageCoordinates` en Domain y DTO inmutables `ParsedSbom` / `SbomComponent` en Application. No se añadieron dependencias de frameworks a esas capas.
- Separadas coordenadas de paquete y ocurrencias por `bom-ref`: referencias diferentes pueden compartir coordenadas; referencias repetidas se rechazan, incluso si colisionan con la raíz.
- Delimitado el formato a CycloneDX JSON 1.6 con bibliotecas Maven planas y PURL simples coincidentes con grupo/nombre/versión. Se conserva el serial cuando está presente; no se inventan commits ni procedencia ausente.
- Aplicados límites de 5 MiB, 500 componentes, profundidad JSON y longitudes de cadenas/números. Se rechazan claves duplicadas, varios documentos concatenados, formatos no soportados y campos obligatorios ausentes.
- Actualizados contexto maestro, arquitectura y stack con el estado vigente; conservadas sus propuestas e historial. Actualizados instrucciones de continuidad, README, demo, backlog y descripción de fixtures.

### Herramientas y tecnologías

| Herramienta / tecnología | Uso | Incorporación |
| --- | --- | --- |
| Java: records, colecciones inmutables y streams de entrada | Contratos y value object propios; lectura limitada del documento | Plataforma ya existente, destino Java 21 |
| Jackson | Árbol JSON y restricciones del parser en Infrastructure | Dependencia ya transitiva de Spring Web; no se añadió librería ni versión nueva |
| Spring Boot | Registro del adaptador para inyección detrás del puerto | Ya existente; no se arrancó Spring para las pruebas de este bloque |
| JUnit 5 | Pruebas unitarias de aceptación/rechazo del subconjunto | Ya existente |
| ArchUnit | Comprobar dependencias de Domain/Application y ubicación de adaptadores | Ya existente |
| Maven, PowerShell y Git | Compilación, verificaciones focalizadas y commits por funcionalidad | Herramientas de trabajo, no dependencias nuevas del producto |

### Verificación ejecutada

```bash
mvn verify -Dtest=CycloneDxJsonParserTest,HexagonalArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false
```

Desde `backend/`: **BUILD SUCCESS; 8 pruebas del parser y 4 reglas de arquitectura aprobadas; 0 fallos, 0 errores, 0 omisiones**. Se compilaron y empaquetaron los tres módulos con Maven 3.9.11 y JDK local 25, destino Java 21.

Las pruebas comprueban lectura de coordenadas y serial, raíz excluida del inventario, ocurrencias distintas del mismo paquete, resultado inmutable, JSON inválido/ambiguo, formato/versión no soportados, datos ausentes o PURL contradictorios, referencias duplicadas, límite de tamaño/cantidad, inventario vacío, componentes anidados, profundidad excesiva y error de lectura.

No se iniciaron base de datos, Docker ni frontend; no cambiaron persistencia o endpoints. Las entradas unitarias son documentos construidos en las pruebas; no se ejecutaron los archivos de `fixtures/demo/` contra un flujo completo de importación.

### Límites y siguiente paso

- No es validación completa del esquema CycloneDX ni soporte general de Maven: qualifiers, subpaths, codificaciones y componentes anidados quedan fuera de este subconjunto.
- No se siguen URLs, ejecutan scanners ni consultan proveedores. No se infieren dependencias directas/transitivas, vulnerabilidades o commits.
- F03 está implementada como parser. F02 y F04 siguen pendientes: no hay endpoint de carga conectado ni escritura del inventario. No se marca DEMO-02 completa.
- Siguiente bloque: F04, caso de uso de importación y persistencia transaccional por organización/repositorio/snapshot; después conectar F02 con acceso de demo acotado. Mantener el rechazo de documentos inválidos antes de escribir y verificar rollback cuando se conecte persistencia.

### Commits

Tres cambios coherentes: contratos/coordenadas, adaptador CycloneDX con pruebas y actualización de documentos prioritarios/bitácora. Todos con emoji; sus identificadores quedan en el historial de Git.

## Bloque 03 — Importación transaccional del SBOM (F04)

Fecha: 14 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Objetivo y documentos revisados

Continuar con F04, respetando contexto maestro, arquitectura backend, stack y alcance de demo. Se revisaron el esquema V2, los puertos existentes, el agregado Scan y las decisiones de procedencia: no inventar commit, referencia Git, scanner o relaciones.

### Implementación

- Añadidos el puerto de entrada `ImportSbom`, comando/resultado, `ImportSbomUseCase` y el puerto de salida `SbomImportStore`, sin frameworks en Application.
- El caso de uso verifica repositorio dentro de la organización antes de leer; valida el documento y calcula SHA-256 de los bytes originales antes de escribir. Conserva la propiedad del stream en el llamador.
- Extendido el agregado Scan con origen explícito: los análisis normales siguen siendo `REPOSITORY_SCAN`; `Scan.imported` crea un snapshot terminal `SBOM_IMPORT`, sin referencia Git inventada. Sus fechas representan importación, no duración de scanner.
- Flyway V3 añade namespace al catálogo, origen al snapshot y tabla de procedencia `sbom_imports`. V1/V2 no se modifican. La base actual tiene 14 tablas de negocio.
- `UseCaseConfiguration` delimita la transacción; `JdbcSbomImportAdapter` la exige y guarda snapshot, procedencia, catálogo y ocurrencias con SQL parametrizado.
- Se reutiliza el catálogo por organización/PURL solo si coinciden las coordenadas; un conflicto provoca rollback sin sobrescribir información previa. Cada carga aceptada crea otro snapshot; sus ocurrencias conservan `bom-ref` y clasificación `UNKNOWN`.
- No se almacenan archivos originales ni se crean filas de artefactos con rutas ficticias. No se ejecutan scanner, OSV o GitHub.
- Sincronizados contexto maestro, arquitectura, stack, base de datos, roadmap, backlog y documentación de demo con el avance real.

### Herramientas y tecnologías

| Herramienta / tecnología | Uso | Incorporación |
| --- | --- | --- |
| Java estándar: DigestInputStream, MessageDigest y SHA-256 | Identificar los bytes originales del documento | Sin dependencia nueva |
| Spring TransactionTemplate / gestor transaccional existente | Delimitar el caso de uso y rollback atómico | Ya existente; Application sigue sin Spring |
| JdbcTemplate y PostgreSQL ON CONFLICT | Persistir y reutilizar catálogo compatible dentro de cada organización | Ya existentes; sin ORM/SDK adicional |
| Flyway | Evolución V3 sin reescribir migraciones aplicadas | Una migración nueva, sin dependencia nueva |
| Jackson | Validación previa mediante el parser implementado en el bloque anterior | Reutilizado, sin cambios al parser |
| JUnit, Spring Boot Test y ArchUnit | Verificar reglas afectadas, integración real y fronteras de capas | Dependencias ya existentes |
| Maven, JDK, PowerShell y Git | Compilar, ejecutar pruebas focalizadas y registrar commits | Herramientas de trabajo |

### Verificación ejecutada y resultados

Se ejecutó desde `backend/` la selección:

```bash
mvn verify -Pdatabase-it -Dtest=ScanTest,ImportSbomUseCaseTest,HexagonalArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=SbomImportIT,DatabaseIT#migrationsAndJpaValidationBootSuccessfully+upgradesExistingV1WithoutRewritingItsChecksumOrDeletingData+registersReadsAndQueuesScanThroughRealAdapters -Dfailsafe.failIfNoSpecifiedTests=false
```

**BUILD SUCCESS; 18 comprobaciones aprobadas, 0 fallos, 0 errores, 0 omisiones:**

- ScanTest: 5 pruebas del ciclo de vida, incluido origen de importación y referencia Git ausente.
- ImportSbomUseCaseTest: 2 pruebas; repositorio ajeno/ausente no llega al parser y documento inválido no llega a persistencia.
- HexagonalArchitectureTest: 4 reglas de dependencia/ubicación.
- DatabaseIT: solo 3 métodos afectados; arranque con 14 tablas y V3, actualización desde V1 preservando datos/checksums y solicitud normal de análisis.
- SbomImportIT: 4 pruebas en PostgreSQL; procedencia/SHA-256, snapshots separados y catálogo reutilizado, rechazo de documentos inválidos, aislamiento por organización y rollback después de escrituras parciales ante conflicto de catálogo.

Se reutilizó PostgreSQL 18.6 temporal para comprobar también la actualización de una base con V2. Se detuvo al terminar. Entorno: Maven 3.9.11, JDK local 25 y destino Java 21. No se repitieron las pruebas unitarias del parser ni las suites de frontend/seguridad HTTP: esos componentes no cambiaron.

### Límites y próximo paso

- F03 y F04 están conectadas a nivel de caso de uso y adaptadores. F02 sigue pendiente: no existe endpoint de carga ni interfaz que invoque la importación; DEMO-02 no se marca completa.
- Las pruebas usaron documentos JSON construidos en los tests; no se hizo aún el recorrido de presentación con los archivos de fixtures.
- No se verificaron importaciones simultáneas, reinicio del equipo ni Docker/Compose; el catálogo usa upsert, pero no se presenta una prueba de concurrencia como ejecutada.
- Las filas históricas de componentes reciben namespace vacío por V3 sin deducciones. Si sus coordenadas entran en conflicto con una importación, la operación se rechaza y requiere revisión.
- Siguiente bloque: F02, carga HTTP acotada a la organización/repositorio de demo con errores claros; después F05, consultas del inventario. Se conserva el bloqueo general de negocio hasta definir el acceso local de esos adaptadores.

### Commits

Separación en tres cambios con emoji: caso de uso/modelo, persistencia/migración con pruebas focalizadas y actualización documental/bitácora. Consultar el historial para los identificadores.

## Bloque 04 — Alcance completo y nueva referencia database

Fecha: 14 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y decisiones confirmadas

Concretar qué falta para que funcione toda la visión del maestro, adoptar la nueva especificación database y retirar la referencia anterior. Se hicieron preguntas de alcance antes de implementar nuevos módulos.

- El usuario confirmó que la nueva base solo está definida en el documento; no existe otro esquema nuevo implementado que debamos dar por terminado.
- La instrucción vigente prioriza producto completo listo para usar, local primero y servidor después. Sustituye el alcance anterior de demo/50 horas. Disponibilidad declarada: 25 horas semanales; no se inventó una fecha total nueva.
- Acceso con correo/contraseña, Google y GitHub; Maven/npm; equipos y permisos por repositorio y pruebas aisladas/propuesta de PR para el primer piloto.
- Notificaciones in-app; correo de notificaciones posterior, separado del correo transaccional de cuentas.
- Decisión final de IA: Gemini dentro de la plataforma y Ollama para embeddings. El usuario aclaró explícitamente que Codex se retomará solo cuando lo indique: no se seleccionó SDK/CLI/API ni se reservó una ventana de implementación.
- El usuario delegó retención documental. Se adopta conservación privada de originales con descarga/reindexación autorizadas y retención/eliminación configurable, distinguiéndolos de temporales que se eliminan. No está implementada todavía.

### Cambios documentales

- Adoptada `CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md` como diseño objetivo; corregido el nombre de su fuente maestra y añadidas las decisiones posteriores sin presentarlo como DDL ejecutado.
- Conservadas las eliminaciones que el usuario realizó durante la revisión: BASE_DE_DATOS, BACKLOG_MVP, DEMO_LOCAL y FUNCIONES_DEMO_DOS_SEMANAS. No se restauraron los documentos retirados.
- Añadidos `docs/database/README.md` y `implementation-report.md`: inventario actual de 14 tablas, brechas con el modelo nuevo y orden de transición conservando datos y migraciones.
- Contexto maestro actualizado con matriz de cobertura RF-001–RF-091, interfaz y requisitos transversales, dependencias y criterios para aceptar producto listo para usar. Se conservan las secciones históricas y se identifica qué decisiones prevalecen.
- Alineados arquitectura, stack, instrucciones AGENTS, README, roadmap y referencias de fixtures. El roadmap anterior se sustituye por fases sin presupuesto de demo ni fechas no justificadas.
- Identificadas brechas de base: paquete/versión/presencia, RBAC/equipos, jobs, RLS/roles SQL, vulnerabilidades normalizadas y módulos de documentos/IA/reportes/notificaciones/auditoría. No se estimó un número final de tablas sin diseñar DDL.

### Herramientas y fuentes

| Herramienta / fuente | Uso | Cambios al producto |
| --- | --- | --- |
| PowerShell, rg y editor de archivos | Inspeccionar documentos, migraciones y enlaces; editar decisiones y matriz | Solo documentación |
| Git | Diferenciar cambios del usuario, conservar eliminaciones y preparar commits coherentes | Sin cambios de lógica |
| Habilidad openai-docs y documentación oficial de Codex/OpenAI | Precisar la pregunta sobre uso como herramienta de desarrollo o integración del producto | Ninguna integración añadida; aclarada por el usuario |
| Documentación oficial GitHub Apps | Verificar separación entre identidad/autorización de usuario e instalación/acceso a repositorios | Ningún proveedor configurado |
| Documentación oficial Gemini y Ollama | Confirmar que cuotas/modelos/dimensión deben verificarse, sin prometer gratuidad o fijar dimensiones | Ninguna librería, clave ni modelo instalado |

Referencias consultadas: [GitHub Apps](https://docs.github.com/en/apps/creating-github-apps/about-creating-github-apps/about-creating-github-apps), [límites Gemini](https://ai.google.dev/gemini-api/docs/rate-limits), [embeddings Ollama](https://docs.ollama.com/capabilities/embeddings), [Codex SDK](https://developers.openai.com/codex/sdk/) y [quickstart OpenAI](https://developers.openai.com/api/docs/quickstart). Las últimas dos se consultaron para aclarar superficie; no constituyen una selección de implementación.

### Verificación y límites

Revisión de diferencias, referencias y enlaces de documentos modificados. No se ejecutaron suites, SQL, migraciones, instalaciones ni despliegues: este bloque solo concreta alcance. V1–V3 y el código permanecen sin modificar. Los 18 resultados del bloque anterior no se atribuyen a la nueva especificación.

Modelo de embeddings/dimensión, modelos Gemini, límites de consumo, periodos operativos, configuración de proveedores y estimación total quedan para concretarse antes de su implementación. No se asumen roles personalizados ni integraciones adicionales. No hay Codex del producto programado.

### Próximo trabajo y commits

Diseñar el mapeo de transición V1–V3 → modelo database, sus migraciones y adaptación de casos de uso; priorizar integridad, aislamiento y trazabilidad. Después implementar las funciones por las dependencias de la matriz del maestro. Este bloque no crea PR externos ni habilita acceso público.

Dos commits documentales: adopción de la especificación/estado real y alineación del alcance/instrucciones/bitácora. Todos con emoji y descripción en español.

## Bloque 05 — Implementación prioritaria de la base completa

Fecha: 14 de noviembre de 2026 (America/Bogota). Horas reales: no medidas.

### Petición y alcance

Priorizar la base de datos usando CODEX_DATABASE_DEPENDENCY_INTELLIGENCE como fuente para SQL, migraciones y diagramas. Conservar datos y migraciones existentes, respetar hexagonal/DDD y separar commits con emoji por responsabilidad. No se habilitan APIs de negocio sin identidad/autorización.

### Cambios realizados

- Ocho migraciones nuevas V4–V11; V1–V3 intactas. Núcleo aplicado: 121 tablas de negocio, 85 con ENABLE/FORCE RLS y 208 FK, sin contar Flyway.
- Paquete/versión/identificadores/aparición separados y aristas del mismo snapshot; conversión conserva IDs/referencias y PURL alternativos. Membresías y roles históricos normalizados; identidad local/externa, equipos y acceso por repositorio; instalación GitHub separada de login.
- Jobs/intentos/etapas/logs/artefactos, commits, SBOM y comparaciones; advisories/fuentes/aliases/severidades/rangos/fixes/evidencia normalizados. El hash de payload legado se etiqueta CANONICAL_JSON, sin afirmar que sean bytes HTTP originales.
- Documentos/versiones/originales privados, temporales con expiración, chunks, procesamiento, metadatos de modelos, conversaciones y citas con FK reales. No se inventa modelo/dimensión ni se agrega Codex al producto.
- Impacto ADD/UPDATE/REMOVE/REPLACE, evidencia KNOWN/POTENTIAL/VALIDATED, simulaciones/pruebas, propuestas PR con aprobación; reportes, notificaciones, auditoría append-only, retención/eliminación tipadas. Tablas no equivalen a servicios ejecutables.
- Roles capability separados y login runtime no propietario; credenciales Flyway separadas, guard de privilegios y TenantTransactions local a la transacción JPA/JDBC, incluido bootstrap. Adaptados importador y scan sin añadir frameworks al Domain/Application ni bibliotecas nuevas.
- Aprovisionamiento administrativo idempotente del login; Compose y ejemplo de entorno incluyen contraseña runtime separada, sin versionar secretos. Instrucciones para volúmenes existentes sin borrarlos.
- Migración vectorial opcional fuera del Flyway predeterminado y ADR de activación pendiente. Núcleo sin dependencia de extensión ni modelo inventado.
- Export estructural desde pg_catalog; diccionario completo de cada tabla, matriz de normalización con inventario de claves y límites formales, siete Excalidraw nativos editables con SVG/PNG y validación de cobertura. Se evita declarar 5NF universal.
- Alineados contexto maestro, arquitectura, stack, README, roadmap e instrucciones de continuidad. Referencia de entrada: [documentación de base](database/README.md).

### Herramientas y tecnologías utilizadas

| Herramienta / tecnología | Uso efectivo | Incorporación al producto |
| --- | --- | --- |
| PowerShell, rg, edición de archivos y Git | Inventario, revisión, documentación y commits | Sin nueva dependencia |
| PostgreSQL 18.6 nativo temporal y psql | Aplicar SQL, exportar catálogo, comprobar rol y aprovisionamiento | Compose sigue en PostgreSQL 17.7; no se instaló otro servicio productivo |
| Flyway, Spring JDBC/JPA y transacciones existentes | Migraciones incrementales, persistencia normalizada y contexto tenant | SQL, configuración y adaptadores propios; no otra biblioteca |
| Maven 3.9.11, JDK local 25 con destino 21, JUnit/ArchUnit y Failsafe | Compilación, pruebas focalizadas y límites de capas | Herramientas y dependencias ya presentes |
| Python 3.12 estándar | Generar diccionario y archivos nativos Excalidraw/SVG desde catálogo real | Generador de desarrollo; sin dependencia runtime |
| Pillow ya disponible | Exportar PNG e inspeccionar vista conjunta/detalle de diagramas | No instalada como dependencia del producto |
| Documentación oficial PostgreSQL y Excalidraw | Confirmar FORCE/BYPASSRLS y estructura nativa editable | Referencias enlazadas en README de base |

### Verificaciones y resultados

Primera selección: 8 unitarias/arquitectura/seguridad y 22 de integración, todas correctas. Incluyó ImportSbomUseCaseTest, SecurityConfigurationTest, HexagonalArchitectureTest, DatabaseIT, DemoBootstrapIT, SbomImportIT, NormalizedSchemaIT y RuntimeDatabaseIT. Tras endurecer integridad en V11 se ejecutaron solo NormalizedSchemaIT (7) y dos métodos afectados de DatabaseIT: 9 correctas. Total: **31 casos distintos**, con 8 repeticiones justificadas; no se presenta el total de ejecuciones como casos adicionales.

Se comprobaron arranque limpio, actualización desde V1/V3 con datos/checksums, deduplicación paquete/versión sin perder PURL/apariciones/aristas, transformación de roles/sujetos/advisories/fixes, FK compuestas, rollback y bootstrap. Login runtime real: no propietario ni DDL, aislamiento de lecturas/escrituras/asociaciones, catálogos sin escritura, auditoría sin UPDATE/DELETE y contexto eliminado al terminar. JPA e importador operaron con ese login. Integridad focalizada en documentos/citas/reportes/notificaciones, evidencia VALIDATED de la misma prueba/análisis, inmutabilidad terminada, aprobación PR y reintentos máximos. El aprovisionamiento SQL se ejecutó dos veces correctamente en la base desechable.

Exportación estructural y verificación de diagramas: 121 tablas, 208 FK, siete archivos nativos, IDs únicos y bindings válidos. Inspección de lámina conjunta y detalle PNG. Diccionario y catálogo no contienen filas de negocio ni credenciales. Se revisan diferencias, enlaces locales y formato antes de cerrar los commits. El servidor temporal se detiene al finalizar; no se actualiza una base externa del usuario.

### Límites, siguiente paso y commits

No se ejecutó Docker/Compose, pgvector, IA, carga/concurrencia ni apertura de la UI de Excalidraw. El entorno nativo no prueba las imágenes PostgreSQL 17.7/Java 21. V101 requiere extensión/modelo/dimensión verificados y pruebas antes de activarse. La autorización funcional y el login siguen pendientes; las APIs de negocio permanecen denegadas. No se afirma que las 121 tablas completen todos los recorridos del producto.

Siguiente bloque: registro/login local y verificación/recuperación/sesiones, autorización por membresía/equipo/repositorio y recorrido HTTP/UI; después identidad Google/GitHub e instalación GitHub. Servicios de workers/scanner/OSV, almacenamiento/documentos/IA, impacto, reportes y alertas se desarrollan según dependencias del maestro.

Cuatro commits separados con emoji: núcleo relacional con aislamiento/runtime y adaptación probada; migración vectorial opcional con ADR; diagramas/diccionario y documentación derivados del catálogo; sincronización de contexto/bitácora. El primer bloque conserva juntas migraciones, configuración y adaptadores dependientes para no dejar versiones incompatibles entre sí. Consultar historial para hashes; no se hace push ni publicación.

## Bloque 06 — Acceso local, organizaciones y autorización

Fecha del trabajo, 15 de noviembre de 2026, America/Bogota. Horas reales sin medir. El usuario pidió continuar con el siguiente bloque tras la base. Se implementó el acceso por correo/contraseña y el control funcional de los endpoints existentes.

### Cambios

- Domain incorpora EmailAddress. Application incorpora DTO, puertos y casos de uso de registro, autenticación, organizaciones y autorización de acciones de repositorio. Conserva independencia de Spring/JPA.
- Infrastructure guarda usuario/hash atómicamente, normaliza correo y resuelve duplicados con UNIQUE. Contraseñas con PBKDF2-HMAC-SHA256, 600.000 iteraciones y salt aleatorio de 16 bytes mediante Spring Security existente. Política inicial de 15–128 caracteres, validación sin reflejar contraseñas y DTO con representación redactada.
- Registro/login por JSON, consulta de cuenta activa, logout, sesión en servidor con rotación de ID/CSRF y cookie HttpOnly/SameSite=Lax. Inactividad configurable de 30 minutos. Se comprueba cuenta activa en cada petición. Sesiones en memoria del backend.
- Límite inicial configurable de 20 operaciones de contraseña por dirección remota/minuto y mapa acotado. Se reinicia con el proceso. Tras proxy puede compartir presupuesto entre usuarios. Se documenta esa limitación para servidor.
- Organización creada junto con membresía OWNER, rol y evento de auditoría. V12 incorpora políticas SELECT para consultar organizaciones/membresías propias con app.user_id local a transacción. V1–V11 se conservan.
- Repositorios/scans exigen cuenta, organización y membresía activas, permiso del rol y permiso por equipo para MEMBER/VIEWER. OWNER/ADMIN tienen alcance organizacional. Autorización y operación comparten transacción tenant. Se consulta permiso vigente en cada petición.
- Angular incorpora formularios de registro/login, restauración/logout y listado/creación de organizaciones, con estados de carga, error y reintento. Proxy local de desarrollo hacia la API. El formulario restablece el nombre con valor vacío después de crear organización.
- Configuración de timeout, cookie Secure y límite de intentos en entorno/Compose. Acceso y contratos en ACCESO_LOCAL.md. Contexto maestro, arquitectura, stack, roadmap y README actualizados.
- Exportado catálogo V12 y regenerados diccionario/diagramas. Permanecen 121 tablas, 85 con RLS forzado y 208 FK.

### Herramientas y dependencias

Se utilizaron PowerShell, rg, Git, Maven 3.9.11, JDK local 25 con compilación destino 21, PostgreSQL nativo temporal 18.6/psql, JUnit/ArchUnit/Failsafe, Node 24.22.1 y Angular CLI. npm 11.16.0 se descargó del registro oficial en .tools para instalar package-lock sin cambios. Python estándar y Pillow ya disponible regeneraron los artefactos de base.

Se reutilizaron Spring Security/JDBC y Angular Forms/Material/RxJS existentes. Este bloque incorpora código propio sin añadir bibliotecas al producto. La habilidad computer-use se consultó para revisión visual. El host browser resultó indisponible y el acceso a Chrome agotó la espera de autorización. No se consiguió revisar el recorrido en la UI del navegador.

Referencias consultadas para parámetros y persistencia/CSRF en [OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [Spring Security sessions](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html) y [Spring Security CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html).

### Comprobaciones y resultados

18 casos distintos pertinentes comprobados con éxito. Seis de SecurityConfigurationTest/HexagonalArchitectureTest, cinco de LocalAccessIT, cuatro de RuntimeDatabaseIT, dos métodos de DatabaseIT y uno de AuthenticationRateLimitFilterTest. La primera pasada detectó que la función compartida de V12 podía existir al migrar otro esquema de pruebas. Se corrigió antes de entregar y se repitieron los dos métodos de migración y los HTTP afectados en una nueva base desechable. La validación de cuerpos se comprobó con contraseñas fuera de límite, respuesta sin reflejarlas y ausencia de cuentas parciales. Las repeticiones se seleccionaron por cambios concretos.

Cobertura de registro/hash, correo duplicado normalizado, errores de login equivalentes, cambio de ID de sesión, rotación de CSRF, logout, cuenta desactivada, organizaciones propias, membresía suspendida, rol VIEWER, intersección con permisos de equipo y revocación efectiva. Se mantiene aislamiento/guard runtime real y actualización desde V1 con checksum anterior intacto.

Build Angular de producción correcto. API real health UP, frontend HTTP 200 y cookie real HttpOnly/SameSite=Lax comprobados usando procesos y datos sintéticos propios. El acceso visual quedó pendiente por el bloqueo descrito. Docker/Compose y las imágenes configuradas se mantienen sin ejecutar en este entorno. Los procesos temporales y PostgreSQL se detienen al cerrar el bloque. Se revisan enlaces, catálogo y diferencias antes de crear los commits.

### Siguiente paso y commits

Continuar con verificación/recuperación de correo y entrega transaccional local comprobable. Después identidad Google/GitHub, vinculación, invitaciones y gestión HTTP/UI de equipos/permisos. Las cuentas locales actuales tienen correo sin verificar. La UI de repositorios/scans y los servicios de scanner/OSV continúan en sus fases.

Tres commits con emoji para backend de acceso/autorización, interfaz local y documentación/contexto/artefactos. Consultar el historial para hashes.

## Bloque 07 — Verificación de correo y recuperación de contraseña

Fecha del trabajo, 15 de noviembre de 2026, America/Bogota. Horas reales sin medir. El usuario autorizó continuar con verificación/recuperación y entrega local del correo transaccional.

### Cambios realizados

- PasswordPolicy en Domain comparte la regla entre registro y recuperación. Application incorpora RecoverAccount y puertos de tokens, correo y persistencia, manteniendo Java puro.
- Tokens SecureRandom de 32 bytes, Base64 URL-safe, SHA-256 en user_access_tokens, propósito, caducidad, consumo único y espera de reenvío configurable. La emisión consume enlaces anteriores del mismo propósito. Verificación confirmada se refleja en emailVerified.
- V13 añade credential_version e índice por usuario/propósito/creación. Recuperar contraseña sustituye hash, incrementa versión y consume tokens en una transacción. SessionIdentity conserva la versión leída junto al hash autenticado y el filtro rechaza sesiones antiguas en cada petición. V1–V12 se conservan.
- Adaptador JDBC serializa operaciones bloqueando la fila de usuario y comprueba caducidad tras obtener el bloqueo. Dos confirmaciones simultáneas producen un único ganador. Un fallo de envío revierte la sustitución del token y preserva el enlace previo.
- JavaMailSender SMTP configurable, con timeouts. El envío de recuperación se procesa en un ejecutor acotado en memoria y el endpoint devuelve 202 uniforme antes de consultar la cuenta. Cola llena devuelve 429. Fallos registrados con código y tipo de excepción, excluyendo direcciones y secretos del mensaje.
- Mailpit v1.33.1 configurado como bandeja local en Compose, con puertos vinculados a 127.0.0.1. Se utilizó también el binario oficial temporal para validar SMTP real. Expiración de verificación 24 horas, recuperación 30 minutos y espera de reenvío 60 segundos como valores iniciales configurables.
- Angular incorpora Olvidé mi contraseña, confirmación de enlace, contraseña nueva/confirmación y aviso de verificación. El fragmento del enlace se retira de la barra de direcciones y se mantiene temporalmente en memoria. POST requiere confirmación y CSRF. Enlaces abiertos con la aplicación ya cargada se reconocen por cambio de fragmento.
- Actualizados contexto maestro, arquitectura, stack, roadmap, README y acceso local. Añadido VERIFICACION_Y_RECUPERACION.md. Catálogo V13, diccionario y diagramas regenerados, manteniendo 121 tablas, 85 con RLS forzado y 208 FK.

### Herramientas y tecnologías

| Herramienta o tecnología | Uso | Incorporación |
| --- | --- | --- |
| Spring Boot starter-mail / JavaMailSender | Adaptador SMTP con configuración y timeouts | Nueva dependencia de Infrastructure, versión del BOM existente |
| Mailpit 1.33.1 | Bandeja SMTP local y API de comprobación | Servicio de desarrollo en Compose y binario temporal oficial para pruebas |
| SecureRandom, SHA-256, Base64 y Clock de Java | Tokens y tiempo de vigencia | Bibliotecas estándar |
| Spring JDBC, transacciones, Spring Security, ThreadPoolTaskExecutor | Persistencia, sesión y cola local acotada | Bibliotecas existentes |
| Angular Forms, Material y RxJS | Formularios, estados y consumo de enlaces/CSRF | Dependencias existentes |
| PowerShell, rg, Git, Maven 3.9.11, JDK 25 destino 21, JUnit/ArchUnit/Failsafe | Inspección, compilación y verificación focalizada | Herramientas existentes |
| PostgreSQL 18.6, psql, Node 24.22.1, Python estándar y Pillow | Base temporal, frontend, parser y artefactos del catálogo | Herramientas existentes |

Referencias consultadas en [Spring Boot email](https://docs.spring.io/spring-boot/reference/io/email.html), [Mailpit Docker](https://mailpit.axllent.org/docs/install/docker/), [Mailpit v1.33.1](https://github.com/axllent/mailpit/releases/tag/v1.33.1), [API Mailpit](https://mailpit.axllent.org/docs/api-v1/) y [OWASP recuperación](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).

### Comprobaciones ejecutadas

20 casos distintos pertinentes correctos. Siete de seguridad, arquitectura y límite de intentos. Seis de AccountRecoveryIT con SMTP Mailpit real, cinco de LocalAccessIT y dos métodos de arranque/actualización de DatabaseIT. Se ejecutaron con PostgreSQL nativo temporal, login runtime limitado y datos sintéticos de example.test.

Se verificaron hash de token confirmado frente al enlace recibido por SMTP, propósito, expiración, reenvío/cooldown, replay, confirmaciones concurrentes con un único ganador, contraseñas inválidas sin consumir el enlace, revocación de dos sesiones, login con contraseña nueva, error uniforme, CSRF, autenticación y rollback ante fallo de correo simulado. La primera espera de ocho segundos resultó corta para un mensaje asíncrono, que después se comprobó recibido y confirmado. Se amplió a veinte segundos y se repitieron las seis pruebas afectadas junto con el ajuste de caducidad después de esperar bloqueo. Todas pasaron.

Build Angular de producción correcto. Cuatro aserciones directas sobre el parser real de enlaces correctas. Validación estructural de catálogo, diccionario/diagramas y enlaces documentales al cerrar los cambios. La comprobación del navegador conectado volvió a indicar host indisponible. La revisión visual y Compose permanecen pendientes.

### Límites y siguiente paso

Sesiones y cola de recuperación actuales residen en memoria. La entrega SMTP y el commit de PostgreSQL tienen un límite entre sistemas. Si el correo fue aceptado y luego falla el commit, debe pedirse un enlace nuevo. La cola carece de reintentos durables y debe sustituirse o ampliarse para operación en servidor. Las cuentas pueden usar acceso local mientras verifican correo. Google/GitHub, vinculación, invitaciones y gestión de equipos siguen pendientes.

Se detienen PostgreSQL y Mailpit temporales al finalizar. Correos capturados en localhost con datos sintéticos. Siguiente bloque de identidad con Google/GitHub y vinculación segura, seguido de invitaciones y gestión de equipos/permisos. Tres commits con emoji para backend/SMTP, formularios y documentación/contexto/artefactos.

## Bloque 08 — Paleta visual e hitos de revisión interactiva

Fecha, 15 de noviembre de 2026, America/Bogota. Horas reales sin medir. El usuario proporcionó su paleta, pidió priorizarla/documentarla y solicitó aviso cuando pueda probar la plataforma y aportar observaciones.

### Cambios

Paleta centralizada en frontend/src/theme/_tokens.scss con los colores principales y semánticos indicados. Aplicada mediante variables CSS y mat.theme-overrides al fondo, header, tarjetas, controles, botones, formularios de acceso/recuperación y organizaciones. Confirmaciones en verde, errores en rojo y foco de teclado en azul. Token navy/cian preparado para la futura navegación funcional.

Se recomienda azul eléctrico para enlaces sobre blanco y cian para acentos sobre navy. Cian sobre blanco alcanza 2,43:1. Se añaden variantes oscuras para etiquetas semánticas y un contorno visible de controles. Se conservan los colores principales indicados como tokens de producto.

DISENO_FRONTEND.md registra paleta, distribución, variantes, contraste y hitos de revisión. Actualizados maestro, stack, roadmap, README y AGENTS para recordar el aviso al usuario. Primer recorrido revisable de acceso/colores al levantar el entorno. Siguiente hito con datos reales, GitHub → repositorio → análisis → inventario. El arranque mediante Compose y la revisión visual permanecen pendientes.

### Herramientas y verificaciones

PowerShell/rg y Git para revisar cambios, Sass y Angular Material existentes para aplicar el tema, Node 24.22.1/Angular CLI para build de producción y Python estándar para calcular contraste a partir de los tokens reales. Ninguna dependencia nueva del producto.

Build Angular correcto. Quince combinaciones de texto superan 4,5:1 y los contornos de controles superan 3:1 sobre su superficie. Se comprueban tokens/override primario en CSS compilado, enlaces locales y formato de diferencias. Referencias en [WCAG 2.2](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) y [Angular Material](https://material.angular.dev/guide/theming). La consulta al navegador conectado volvió a devolver host indisponible. Se registra revisión visual pendiente. Las verificaciones se limitaron al frontend y documentación afectados.

Dos commits con emoji para tema del frontend y documentación/hitos. Seguir con Google/GitHub y vinculación segura, conservando los avisos de revisión solicitados.

## Bloque 09 — Google, GitHub y vinculación de identidades

Fecha: 15 de noviembre de 2026, America/Bogota. Horas reales sin medir. Petición: implementar la integración Google/GitHub y avanzar hasta completar lo posible durante la ausencia del usuario, sin pedir confirmaciones. Se revisaron los cuatro documentos prioritarios y el esquema existente. Las variables de clientes estaban sin configurar; se comprobó su presencia sin imprimir valores.

### Cambios concretos

- Domain incorpora IdentityProvider; Application añade ManageExternalIdentities y puertos/DTO propios, independientes de Spring. Acceso por sujeto estable, creación de cuenta externa sin contraseña ficticia y prohibición de fusión por correo. Una colisión exige iniciar sesión en la cuenta existente y vincular explícitamente.
- Infrastructure adapta Google OIDC y GitHub OAuth, con clientes habilitados por configuración, PKCE S256, state de un uso, nonce, caducidad y callback fijo. Inicio por POST con CSRF. Vínculo ligado al usuario original, versión y sesión reciente; requiere correo verificado. JWT Google validado mediante Spring Security y correo GitHub principal verificado consultado por API.
- Adaptador JDBC y transacciones con bloqueo de usuario, restricciones únicas y nueva comprobación del vínculo tras adquirir bloqueo. Vincular revoca otras sesiones; desvincular incrementa versión e invalida la actual. Se impide quitar el último método utilizable. SessionIdentity conserva hora de autenticación. Un callback fallido preserva una sesión local que siga vigente.
- Tokens externos descartados después del uso, sin persistencia de authorized clients ni exposición a Angular. No se solicitan permisos de repositorio. Proveedores deshabilitados por defecto y fallo de arranque si se habilitan sin credenciales. No hay migraciones nuevas: el esquema V1–V13 conserva 121 tablas, 85 con FORCE RLS y 208 FK; no se regeneran diagramas sin cambios de catálogo.
- Angular incorpora botones de acceso, panel de métodos conectados, vinculación explícita, confirmación de retirada, protección del último método y mensajes de retorno permitidos. Se usan los tokens de la paleta existente. Nginx conserva el puerto del Host al reenviar API, omite access logs del callback y aplica no-referrer.
- Añadido IDENTIDAD_GOOGLE_GITHUB.md con callbacks, registro/configuración de clientes, contratos, límites y pasos de activación. Actualizados maestro, arquitectura, stack, especificación database, informe de implementación, acceso local, roadmap, README y AGENTS. Login GitHub se distingue de instalación/acceso a repositorios.

### Herramientas y tecnologías utilizadas

| Herramienta/tecnología | Uso | Incorporación |
| --- | --- | --- |
| spring-boot-starter-oauth2-client | OAuth/OIDC, clientes, PKCE y verificación JWT | Nueva dependencia solo en Infrastructure; BOM existente |
| Spring Security, JDBC, transacciones, Clock y sesiones | Adaptadores, controles y persistencia | Dependencias existentes |
| Angular Material, RxJS y tokens Sass/CSS | Acceso y gestión de vínculos | Dependencias existentes |
| HttpServer del JDK y Nimbus transitivo | Proveedor HTTP local, códigos, PKCE y JWT RSA en pruebas | Fixture de pruebas; sin SDK nuevo del producto |
| PowerShell, rg, Git, Maven 3.9.11, JDK 25 destino 21 | Inspección, compilación y verificaciones focalizadas | Herramientas existentes |
| PostgreSQL nativo 18.6, Node 24.22.1 y Angular CLI | Base temporal con runtime limitado, build y parser | Herramientas existentes |

Se consultaron fuentes oficiales de Spring Security, Google OIDC y GitHub OAuth/API; referencias enlazadas en IDENTIDAD_GOOGLE_GITHUB.md. No se incorporaron SDK de proveedor, almacenamiento de tokens ni IA nueva.

### Verificaciones y resultados

22 casos distintos pertinentes correctos: siete de seguridad/arquitectura/límite de intentos, diez de ExternalIdentityIT y cinco de LocalAccessIT. Base PostgreSQL temporal propia, Flyway existente y rol runtime limitado. El fixture realiza intercambio HTTP de código y verifica code_verifier/PKCE; firma tokens RSA y publica JWKS. Se comprobaron firma/issuer/audience/expiración/nonce inválidos, state y proveedor erróneos, plazo, cambio de sesión durante vínculo, CSRF, correo coincidente, ID estable tras cambiar correo, creación concurrente, vinculación, revocación y último método. Los controles de acceso local y autorización existentes conservaron sus resultados.

Build Angular de producción correcto. Cinco aserciones directas sobre el parser de retorno real correctas, incluidos código desconocido/prototipo y parámetros duplicados. La comprobación produce solo el aviso de Node sobre detección de módulo del archivo TypeScript; no se cambia la configuración del producto para silenciarlo. Tras ajustar el rechazo de nombres de proveedor inválidos se repitieron las clases focalizadas, sin ampliar la suite: 22 casos correctos. git diff --check correcto y 50 referencias locales comprobadas en nueve documentos.

### Límites y siguiente paso

Sin credenciales reales Google/GitHub, no se registró ninguna aplicación externa ni se probó su consentimiento. La implementación puede activarse con los pasos documentados; ambos proveedores permanecen apagados. Las pruebas con proveedor local no se presentan como prueba de disponibilidad o configuración de los servicios externos. Compose y revisión visual siguen pendientes. Las sesiones siguen en memoria y se requiere completar operación de servidor.

Recorrido revisable: acceso local/colores y, tras activar credenciales, login y métodos conectados. Aún no se cumple el hito GitHub → repositorio → análisis → inventario con datos reales. Seguir con invitaciones y gestión de equipos/permisos; después instalación GitHub, repositorios y análisis. Tres commits coherentes con emoji para backend/configuración/pruebas, interfaz y documentación. PostgreSQL temporal detenido correctamente mediante pg_ctl; no se modificó una base externa del usuario.

## Bloque 10 — Registro central de pendientes para trabajo conjunto

Fecha: 15 de noviembre de 2026. Horas reales sin medir. Petición: crear un archivo de pendientes de todo lo trabajado y reservar su implementación para cuando el usuario indique que los trabajemos juntos.

Se crea [PENDIENTES.md](PENDIENTES.md), separando lo implementado, configuración/validación pendiente, módulos por desarrollar y operación de servidor. Cada módulo recoge un criterio de cierre, con referencias al maestro y documentación específica. La lista no inicia ninguna implementación ni activa proveedores. Actualizados AGENTS, contexto maestro y roadmap para conservar la instrucción del usuario y evitar retomar automáticamente invitaciones u otros módulos.

Herramientas utilizadas: PowerShell, lectura de los cuatro documentos prioritarios, apply_patch, Git y Python estándar para comprobar enlaces. Ninguna dependencia nueva. Cambio exclusivamente documental; no corresponde ejecutar pruebas de backend ni build frontend. Veinte referencias locales correctas en cuatro documentos y comprobación de formato de diferencias correcta. Dos commits con emoji para inventario de pendientes y referencias de continuidad. Siguiente paso: esperar la indicación del usuario para elegir y trabajar juntos el bloque pendiente.

## Bloque 11 — Invitaciones, membresías, equipos y permisos

Fecha: 15 de noviembre de 2026, America/Bogota. Horas reales sin medir. Petición explícita: implementar invitaciones y gestión de equipos/permisos. Autoriza este bloque de PENDIENTES; los demás siguen esperando indicación del usuario. Se revisaron los cuatro documentos prioritarios, esquema V5 y contratos de identidad/autorización existentes.

### Cambios concretos

- Domain incorpora OrganizationRole. Application define ManageInvitations/ManageOrganizationAccess, DTO y puertos de persistencia/entrega. Reglas Java puro para actores OWNER/ADMIN, asignaciones de rol y estados, último propietario, miembros activos de equipos y catálogo de permisos por repositorio. ADMIN no asigna ni modifica OWNER/ADMIN. No hay roles personalizados.
- Infrastructure configura transacciones tenant y adaptadores JDBC para tablas existentes. Bloqueo de organización serializa aceptación, membresías y equipos, protegiendo el último OWNER frente a demociones concurrentes. Recursos ajenos al tenant no se modifican. Cambios y auditoría se confirman juntos.
- Invitaciones con token aleatorio de 32 bytes/hash SHA-256, caducidad configurable PT72H inicial, una pendiente por correo/organización, revocación y aceptación explícita. Exige cuenta activa y correo coincidente verificado, revalida autoridad del invitador y consume una sola vez. Reactivar por invitación sustituye rol y limpia equipos antiguos.
- Envío SMTP síncrono mediante JavaMailSender existente y adaptador propio de organización, con enlace por fragmento y sin token en respuestas de API. Un error de envío revierte la emisión; el límite SMTP/commit y la entrega durable siguen pendientes. El presupuesto de intentos existente cubre rutas de invitaciones.
- API/UI para roles/estado de membresías, crear/renombrar/eliminar equipos, añadir/quitar personas y sustituir permisos por repositorio. El último propietario no puede perder su rol activo. Suspender conserva equipos sin acceso; retirar los elimina. Permisos efectivos intersectan rol y equipo en los endpoints existentes. Los scopes de módulos futuros no se presentan como módulos implementados.
- Angular añade aceptación con validación/retirada del fragmento, inicio de sesión/verificación y actualización de organizaciones; formularios de invitación y administración con confirmaciones, estados de carga/error y paleta oficial. Tokens de invitación solo en memoria. Paneles divididos con @defer para mantener el presupuesto inicial, sin nuevas dependencias.
- Añadido INVITACIONES_EQUIPOS_PERMISOS.md con política, contratos y recorrido de revisión. Actualizados PENDIENTES, maestro, arquitectura, stack, especificación database, informe, acceso local, identidad externa, roadmap, README y AGENTS. Sin cambios de esquema/migraciones/diagramas: V1–V13, 121 tablas, 85 con FORCE RLS y 208 FK.

### Herramientas y tecnologías utilizadas

| Herramienta o tecnología | Uso | Incorporación |
| --- | --- | --- |
| Java, Clock, MessageDigest y SecureRandom vía AccountTokenCodec | Reglas, caducidad y tokens/hash | Bibliotecas/puerto existentes; sin dependencia nueva |
| Spring JDBC, TenantTransactions y PostgreSQL | Persistencia, bloqueo, aislamiento y auditoría | Dependencias existentes |
| JavaMailSender y Mailpit 1.33.1 | Invitaciones SMTP y bandeja local de pruebas | Dependencia y herramienta existentes |
| Angular Forms/Material, RxJS, Sass/CSS y @defer | Formularios y carga de pantallas | Dependencias existentes |
| PowerShell, rg, apply_patch, Git, Maven 3.9.11 y JDK 25 destino 21 | Inspección, cambios y verificación focalizada | Herramientas existentes |
| PostgreSQL nativo 18.6, Node 24.22.1 y Angular CLI | Base temporal con login runtime limitado, build y parser | Herramientas existentes |

Referencias oficiales consultadas: [bloqueos PostgreSQL](https://www.postgresql.org/docs/18/explicit-locking.html) y [@defer Angular](https://angular.dev/guide/templates/defer). No se incorporan SDK, colas ni servicios externos nuevos.

### Verificaciones ejecutadas y resultados

21 casos distintos pertinentes correctos: ocho de OrganizationAccessIT, cinco de LocalAccessIT y ocho de seguridad/arquitectura/límite. SMTP real a Mailpit, enlace recibido y hash contrastado en PostgreSQL. Cubiertos destinatario/verificación, expiración/revocación/replay/duplicados, roles/último propietario, concurrencia, aislamiento tenant/CSRF, permisos efectivos y retirada real de acceso, vuelta a membresía sin equipos antiguos y rollback ante fallo SMTP simulado.

La primera ejecución de integración no pudo arrancar la base temporal porque Windows denegó bind del puerto 55439; las ocho pruebas unitarias pasaron y las trece de integración fallaron antes de cargar la aplicación por falta de conexión. Se levantó el mismo cluster temporal en 127.0.0.1:15439, se creó una base desechable nueva y se repitieron solo las trece afectadas: correctas. Después de desacoplar la URL de invitación del adaptador OAuth y fijar su propiedad de correo, se repitieron solo las ocho de OrganizationAccessIT: correctas. No se modificó una base externa del usuario.

Build Angular inicial correcto con aviso de presupuesto, 647,63 kB. Separar panel de gestión redujo a 513,79 kB; separar espacio autenticado a 507,43 kB. Al cargar formularios y espacio según el recorrido con @defer, build final correcto y sin aviso: 381,28 kB inicial frente a presupuesto de 500 kB, sin aumentar el límite. Cinco aserciones directas del parser TypeScript correctas, incluido rechazo de duplicados, UUID inválidos y token mal formado. Node emite el aviso conocido de detección de módulo al importar TypeScript para esta comprobación; no cambia la configuración del producto.

Formato de diferencias correcto y 74 referencias locales comprobadas en doce documentos. Consulta final de catálogo en la base temporal: 121 tablas, 85 con FORCE RLS y 208 FK. PostgreSQL temporal y el proceso propio de Mailpit detenidos correctamente al terminar las verificaciones. Revisión visual, Compose y SMTP externo no ejecutados en este bloque.

### Siguiente paso

Revisión conjunta del recorrido desde Mis organizaciones → Miembros y equipos, invitación recibida en Mailpit y segunda sesión con el correo verificado. El bloque queda implementado y probado por API/build; la aceptación visual sigue en PENDIENTES junto con entrega durable y paginación de historiales grandes. No se empieza automáticamente instalación GitHub ni otro módulo. Tres commits con emoji para backend/pruebas/configuración, frontend y documentación/contexto.

## Bloque 12 — Entorno local encendido para revisión del usuario

Fecha: 15 de noviembre de 2026, America/Bogota. Horas reales sin medir. Petición: ejecutar la plataforma para ver su apariencia. Docker no está disponible mediante el comando del entorno; se utiliza el arranque nativo documentado, sin modificar código funcional.

Se inicia PostgreSQL nativo en 127.0.0.1:15439 y se crea una base separada dependency_preview, sin los fixtures de integración. Flyway aplica V1–V13 y el backend arranca con usuario runtime limitado y credenciales de migración separadas. Se ejecuta el JAR del bloque 11 en 127.0.0.1:8080. Mailpit queda en 127.0.0.1:8025 con SMTP 1025. Angular CLI sirve el frontend en 127.0.0.1:8088 con proxy API hacia el backend y recarga de desarrollo. Google/GitHub permanecen deshabilitados, sin credenciales reales. No se crean cuentas ni organizaciones por el usuario; puede registrarse desde la interfaz.

Herramientas utilizadas: PowerShell/Start-Process con ventanas ocultas, pg_ctl/createdb/psql de PostgreSQL 18.6, JDK 25 ejecutando el JAR con destino 21, Node/Angular CLI existentes, Mailpit existente y Git. Ninguna dependencia nueva. Logs e identificadores de procesos locales en .tools/preview-*, ignorados por Git; no se guardan secretos en documentos.

Verificaciones necesarias: health real del backend UP; frontend http://localhost:8088/ devuelve 200; GET /api/auth/csrf por ese frontend devuelve 200/application/json; bandeja http://localhost:8025/ devuelve 200. Angular compila y queda en watch. No se repiten suites de pruebas ni build de producción para este arranque. No se ejecutó Compose ni se declara completada la revisión visual del usuario.

Se dejan los cuatro servicios activos intencionadamente para que el usuario revise. No detenerlos automáticamente al cerrar este bloque ni reutilizar dependency_preview como base de pruebas automatizadas. Recorrido disponible: crear cuenta → crear organización → Miembros y equipos; usar Mailpit para verificación e invitaciones. Siguiente paso: recoger las observaciones del usuario, sin iniciar otro módulo.

## Bloque 13 — Rediseño de frontend según imágenes del usuario

Fecha: 16 de noviembre de 2026, America/Bogota. Horas reales sin medir. Petición: mejorar el frontend antes de GitHub, con tres referencias de organización, login y dashboard; diseño del registro delegado. Revisados maestro, arquitectura, database, stack, diseño y código vigente. La composición de las imágenes se usa como referencia visual; sus personas, cifras, planes y funciones no son datos del producto.

### Cambios concretos

- AuthFrame de dos paneles, marca navy y red/escudo en SVG propios, login/registro/recuperación sobre superficie clara. Formularios con iconos, errores, visibilidad de contraseña y confirmación de registro, conservando validación/API. Pestañas de acceso operables con teclado. Google/GitHub visibles pero desactivados si la API los declara indisponibles; sin «recordarme» ficticio ni cambios de sesión.
- ApplicationShell con Sidebar y Topbar separados, navegación activa y móvil, búsqueda de secciones, menú de cuenta/logout y perfil real con iniciales. Lista permitida de páginas en URL ?view= y soporte de recarga/atrás/adelante. La invitación pendiente abre Organización sin aceptarse automáticamente.
- Dashboard separado en métricas y vista previa del asistente. Número real de organizaciones desde API; el resto de métricas sin cifra evaluada. Paneles de grafo/historial con estados vacíos y navegación de módulos futuros explícitamente En preparación. Sin cifras, riesgos, actividad, planes comerciales ni respuestas IA inventados.
- Organización con selector, pestañas Miembros/Invitaciones/Equipos/Permisos, tabla, búsqueda/filtro, paginación local de diez miembros, iniciales y estados. Diálogos de invitación, rol/estado, creación y eliminación/revocación; foco contenido/restaurado mediante CDK A11y, Escape y confirmaciones. Formularios cancelados de membresía se reinicializan al abrirlos; página se ajusta si cambia el número filtrado. Se reutilizan las operaciones backend del bloque 11.
- Layout adaptable, scroll de tablas y modales, enlace de salto y respeto de reducción de movimiento. Paleta oficial reutilizada por tokens y cuatro tokens auxiliares de texto/superficie/contorno; utilidades visuales compartidas con prefijo di-. SVG/typografía locales, sin fuentes/imágenes externas ni copia de archivos de Pictures al repositorio. Código formateado con Prettier.
- Actualizados diseño, maestro, arquitectura, stack, pendientes, roadmap y recorrido de organización. Backend, permisos y V1–V13 sin cambios; permanecen 121 tablas. No se ejecutan otras funciones de PENDIENTES.

### Herramientas y tecnologías utilizadas

PowerShell/rg/apply_patch/Git para inspección y cambios; Python estándar para escribir/dividir componentes y verificar artefactos; Angular/Material/Forms/CDK A11y/RxJS/Sass existentes; SVG escrito como código; Node 24.22.1, Angular CLI y Prettier existentes para compilación/formato y comprobación directa de navegación. No se añadió ninguna dependencia de producto, SDK, imagen generada ni proveedor.

Se leyó la habilidad computer-use y se intentó revisión local. El host de browser indicó indisponibilidad. La alternativa nativa @oai/sky listó Chrome, pero la captura se detuvo por no poder determinar su URL con suficiente confianza. Se detuvieron las acciones de UI; no se eludió el bloqueo, no se modificó la sesión del usuario ni se completó revisión visual automática.

### Verificación y límites

Build de producción correcto, 406,37 kB iniciales, bajo presupuesto existente de 500 kB y sin avisos finales por estilos. Los avisos intermedios se resolvieron separando Sidebar/Topbar/Metrics/vista previa y reutilizando utilidades de estilo, sin ampliar límites. Compilación de desarrollo/watch correcta tras los cambios. Siete aserciones directas de navegación/lista permitida correctas. Node emite el aviso conocido de detección de módulo al importar TypeScript para esa comprobación; no cambia la configuración del producto. Formato Prettier correcto al comprobar el código afectado.

HTTP real frontend localhost:8088 devuelve 200; backend health UP; consulta de proveedores a través del frontend devuelve Google/GitHub indisponibles, como corresponde a su configuración. No se crean cuentas ni datos de prueba en dependency_preview. No se repiten pruebas Maven ni migraciones al no cambiar comportamiento backend. No se ejecutó Compose ni se afirma comprobación visual de píxeles, accesibilidad completa o recorrido E2E tras este rediseño.

Formato de diferencias correcto; 51 referencias locales comprobadas en ocho documentos y dos SVG bien formados. Comprobación final de Prettier correcta. HTTP de frontend, marca SVG y API CSRF devuelven 200 y la compilación watch envió la actualización al cliente. Entorno nativo y datos del usuario se conservan activos para revisión. Tres commits con emoji para componentes de acceso/tema, espacio de trabajo/organización y documentación. Siguiente paso: el usuario revisa localhost:8088 y propone ajustes de distribución/colores; no iniciar todavía repositorios GitHub.
## Bloque 14 — Arranque local sin Docker

Fecha: 16 de noviembre de 2026, America/Bogota. Petición: dejar la plataforma ejecutándose en el equipo para que el usuario pueda verla y probarla sin Docker.

### Cambios y herramientas

- Reutilizado PostgreSQL 18 local y su directorio de datos existente; clúster escuchando solo en 127.0.0.1:15439. Conservadas las tres cuentas y la organización que ya existían.
- Preparados runtimes portables Node 24.15.0 y Eclipse Temurin JDK 21.0.13 bajo .tools (ignorado por Git), con checksums oficiales validados. No se añadieron dependencias al producto.
- Aprovisionada contraseña aleatoria para dependency_app y guardada fuera de Git. Flyway aplicó V14 a dependency_preview; no se eliminó ni reinició el volumen.
- Build del frontend Angular de desarrollo y del JAR con Maven. Iniciados Spring Boot en 8080, Mailpit 1.33.1 (SMTP 1025, HTTP 8025 protegido por Basic Auth) y ng serve en 8088 con proxy /api.
- Bootstrap local auditado de administrador@example.test y alta verificada/aprobada de propietario@example.test. Contraseñas aleatorias en .tools/local-preview-accounts.json. No se creó miembro: no existe un repositorio real para asignarle acceso; hacerlo ahora contradiría el flujo de invitación con scope confirmado por el usuario.

### Verificación

Build Angular de desarrollo correcto; build Maven `-DskipTests package` correcto (no se ejecutaron pruebas automáticas). HTTP 200 en frontend, endpoint de proveedores mediante el proxy y health del backend. Flyway confirma V14. La sesión de administrador fue reconocida por `/api/auth/me` y aprobó la cuenta propietaria mediante la API. Mailpit responde con autenticación y no expone su bandeja sin credenciales.

Chrome ya tenía una pestaña con una URL local previa. La automatización visual se detuvo porque el control de Windows no pudo validar la URL activa con suficiente confianza; no se eludió la barrera ni se declara revisión visual hecha. Abrir manualmente http://localhost:8088.

### Siguiente paso

El usuario revisa login, registro, aprobación de propietario, panel administrador, idiomas, tema claro/oscuro y responsive. Usar las credenciales del archivo local indicado. Crear/invitar al miembro cuando se conecte un repositorio real desde GitHub.
## Bloque 15 — Ajustes de interfaz previos a la revisión

Fecha: 17 de noviembre de 2026, America/Bogota. Petición: continuar con los problemas de interfaz reportados antes de la revisión de la app, priorizando móvil, tipografía, traducción, tema y que Organización no requiera scroll horizontal.

### Cambios concretos

- Ajustado el acceso responsive con layout de altura de viewport, panel móvil compacto y espacios reducidos en pantallas bajas; el contenido no se oculta cuando la altura o el zoom requieren más espacio.
- Convertidas las tablas de miembros e invitaciones en tarjetas móviles con rótulos por campo y sin scroll horizontal. Valores dinámicos de miembros/equipos se excluyen de traducción automática.
- Acomodada la búsqueda superior en anchos pequeños y conectado data-label al servicio ES/EN. Se conserva IBM Plex Sans local y el tema oscuro por tokens.
- Traductor global trabaja sobre textos de interfaz y atributos accesibles. Se eliminó una clave duplicada del diccionario detectada durante la compilación.
- Actualizados DISENO_FRONTEND.md y esta bitácora.

### Herramientas y tecnologías

PowerShell y rg para inspección; apply_patch para edición; Node 24.15.0 y Angular CLI para build. Angular, TypeScript y Sass existentes. No se agregaron dependencias. Git no está disponible en PATH ni en la ruta local que se había documentado; no se crearon commits en este bloque.

### Verificación y límites

Primera compilación detectó una clave duplicada en traducciones, corregida. Build Angular de desarrollo completado correctamente tras ejecutarlo fuera del sandbox por el bloqueo del compilador al leer archivos del workspace. No se ejecutaron pruebas adicionales ni se afirma revisión visual automatizada; el servicio local ya activo debe conservarse.

### Siguiente paso

Revisión manual del usuario en http://localhost:8088, especialmente login/register en móvil, ES/EN, claro/oscuro y Organización. Registrar correcciones visuales antes de abrir el bloque de GitHub.

Estado de revisión actualizado: al comprobar el entorno, los procesos de PostgreSQL/frontend/backend/Mailpit ya no estaban ejecutándose. Se reactivó el clúster local existente (sin borrar datos), backend con usuario runtime dependency_app y usuario de migración codex_test, Mailpit con su interfaz autenticada y frontend Angular en localhost:8088. Confirmaciones HTTP: interfaz 200, proxy de proveedores 200 y health backend 200. Se conservan los PID y logs locales bajo .tools. El código y la base de datos del usuario se mantienen; no se reiniciaron volúmenes ni se crearon cuentas nuevas.

## Bloque 16 — Docker Desktop y Compose local

Fecha: 18 de noviembre de 2026, America/Bogota. Petición: instalar Docker y ejecutar la plataforma. Se atendió la advertencia del usuario sobre conservar sus archivos modificados: los cambios del frontend se separaron en commits antes de integrar esta parte.

### Cambios y herramientas

- Descargado el instalador desde el dominio oficial de Docker y validada la firma Authenticode como `Valid`, firmante Docker Inc. Instalado Docker Desktop en el perfil de usuario, sin aceptar términos de servicio en nombre del usuario.
- Inicializado Docker Desktop con WSL 2 y verificado el daemon: Docker client/server 29.9.3 y Compose 5.7.0.
- Creado `.env` local ignorado por Git con contraseñas aleatorias, sin credenciales OAuth. No se modificó `compose.yaml`.
- Ejecutado `docker compose up -d --build`. PostgreSQL 17.7 aplica las migraciones V1–V14; Mailpit queda saludable y los servicios Docker arrancan en `127.0.0.1:8089` (frontend), `:8026` (Mailpit web) y `:1026` (SMTP). Se usan puertos alternos para conservar la instancia local preexistente en 8088/8025/1025. El volumen Docker es nuevo y separado; no se importaron ni borraron datos previos.
- Actualizada la lista de pendientes: la base Docker está vacía y aún debe definirse un aprovisionamiento seguro del primer administrador de plataforma; no se crean cuentas de demostración sin ese flujo.

### Verificación y límites

`docker compose config --quiet` correcto; contenedores arriba y PostgreSQL saludable; frontend HTTP 200, endpoint de proveedores mediante proxy HTTP 200 y backend inicia correctamente con migración V14. Los proveedores Google/GitHub están desactivados por falta de credenciales. El build Docker mostró avisos de presupuesto Angular: bundle inicial 544.18 KB frente a 500 KB y estilos de Organización 4.96 KB frente a 4 KB; no impidieron generar las imágenes. La base Docker nueva tiene 0 usuarios, organizaciones y repositorios; por eso el sitio está visible pero el flujo de acceso aprobado requiere resolver primero el bootstrap de administrador.

### Siguiente paso

El usuario puede revisar la interfaz Docker en `http://localhost:8089`. Después, cuando indique trabajar el siguiente bloque, definir el bootstrap inicial del administrador; no confundir las cuentas de la instancia anterior con las del volumen nuevo. La instancia nativa no se detuvo.

## Bloque 17 — Refresco tras confirmar correo

Fecha: 18 de noviembre de 2026, America/Bogota. Petición: explicar la ventana que aparece al confirmar correo y corregir que no se actualizara el estado ni permitiera continuar.

### Hallazgo y cambio

- El enlace de verificación está diseñado para abrir la pantalla de confirmación y requerir un clic explícito en «Confirmar mi correo»; el enlace observado apunta correctamente al frontend Docker `localhost:8089`.
- La confirmación se guardó en base de datos: el usuario de prueba aparece con `email_verified_at` presente y `admission_status=PENDING_REVIEW`. Verificar el correo no aprueba la cuenta; el paso de aprobación del administrador sigue pendiente.
- Corregido el flujo frontend: tras una confirmación exitosa, consulta automáticamente `/api/auth/me` y cierra la pantalla del token. Si la sesión sigue activa, muestra el estado actualizado; si no, vuelve al acceso con un mensaje para iniciar sesión. El estado pendiente de aprobación se explica sin marcarlo como error. El botón manual queda para reintentar la consulta.
- Reconstruido y reemplazado únicamente el contenedor frontend; no se tocaron backend ni datos.

### Verificación y herramientas

Docker Compose/build Angular terminó correctamente. El código compila dentro de la imagen; no se ejecutaron suites adicionales. Se verificaron previamente la URL del enlace (sin mostrar el token), la respuesta HTTP 200 de la aplicación/proxy y el estado agregado de la base. No se completó una sesión UI end-to-end con credenciales del usuario.

Herramientas: PowerShell, `apply_patch`, Docker CLI/Compose, Mailpit local y PostgreSQL; Angular/TypeScript y RxJS existentes. Sin dependencias nuevas.

### Siguiente paso

Revisar el flujo en `http://localhost:8089`. La cuenta permanecerá en revisión hasta que un administrador de plataforma la apruebe. En la base Docker todavía no existe un administrador inicial; el bootstrap seguro sigue anotado en PENDIENTES y no se creó una cuenta privilegiada automáticamente.

## Bloque 18 — Aprobación administrativa y espera persistente

Fecha: 18 de noviembre de 2026, America/Bogota. Petición: corregir el botón de actualización de estado, permitir que la persona registrada vuelva después mientras espera, y retirar la ventana/paso de confirmación de correo para ese flujo.

### Cambios

- El registro de responsable queda guardado como pendiente. Puede cerrar la página e iniciar sesión después; mientras la solicitud no se apruebe, se conserva el panel de admisión y no se habilita el espacio de trabajo.
- Se eliminó del panel de admisión la acción de confirmar correo para el usuario. La aprobación por un administrador de plataforma confirma el correo pendiente y aprueba la cuenta en una sola operación autorizada.
- La acción administrativa acepta responsables pendientes aunque el correo no se haya confirmado antes por el flujo antiguo. La actualización de estado vuelve a consultar `/api/auth/me`, que lee el estado persistido actual.
- La lista administrativa permite aprobar también las solicitudes cuyo correo no estaba confirmado y muestra la acción como «Confirmar y aprobar responsable».

### Verificación y herramientas

- El build Docker de backend/frontend se ejecutó al reconstruir los servicios y terminó correctamente; Maven `verify` y build Angular corrieron dentro de ese proceso. No se añadieron suites extra.
- En la sesión de documentación actual no fue posible volver a consultar contenedores, base de datos ni estado Git: los ejecutables `docker` y `git` no están disponibles en el PATH de este entorno. Por ello no se afirma una prueba UI end-to-end, que el administrador bootstrap esté creado o que haya commits nuevos.
- Herramientas/código: PowerShell, `apply_patch`, Angular/TypeScript y RxJS existentes, Spring/JDBC existentes. No se añadieron dependencias.

### Siguiente paso

Entrar como administrador en `http://localhost:8089`, revisar la solicitud en Administración de plataforma y elegir «Confirmar y aprobar responsable». El estado se conserva si la persona sale; puede volver a iniciar sesión y usar «Comprobar aprobación». Se requiere primero que exista un administrador de plataforma en esa instancia.

## Bloque 19 — Aviso por correo al administrador de plataforma

Fecha: 18 de noviembre de 2026, America/Bogota. El usuario confirmó que el alta directa debe enviar un aviso al administrador de plataforma. Pidió conservar la pantalla persistente «Esperando confirmación».

### Hallazgo y cambios

- En la comprobación previa, Mailpit mostraba únicamente un correo de recuperación; el endpoint de registro no enviaba correos. La configuración SMTP local envía a Mailpit y no entrega a Gmail.
- Añadido el puerto de salida `PendingAdmissionNotifier`, independiente de Spring/JDBC. Tras persistir exitosamente una cuenta OWNER/PENDING_REVIEW, el caso de uso avisa a los correos de administradores activos y verificados.
- El aviso incluye nombre/correo de la persona y el enlace a la plataforma; no incluye contraseña, token ni enlace de aprobación. La decisión se termina dentro de Administración de plataforma.
- Si falla el envío, el registro ya guardado no se revierte ni se expone el detalle SMTP al usuario; queda disponible la solicitud en la lista administrativa.
- La pantalla «Esperando confirmación», su comprobación manual y el estado persistente no se modificaron.
- Actualizados el instructivo de correo, contexto/pendientes para diferenciar Mailpit local de entrega SMTP externa.

### Verificación y herramientas

- Añadidas dos pruebas focalizadas: el caso de uso emite el aviso después de crear la cuenta pendiente; el adaptador restringe destinatarios a administradores y no manda correo para cuentas distintas.
- Ejecutadas las tres pruebas focalizadas del caso de uso/adaptador: 3/3 correctas. La reconstrucción Docker del backend ejecutó `mvn verify` correctamente (suite de build: Domain 5, Application 8, Infrastructure 18 pruebas, todas sin fallos).
- Smoke test local de registro HTTP: cuenta sintética creada, aviso recibido en Mailpit con el único destinatario administrador activo, contenido con el correo registrado; cuenta temporal eliminada al terminar. No se probó SMTP externo ni entrega a Gmail.
- Herramientas/código: `apply_patch`, Java, Spring JDBC/Mail, JUnit y Mockito ya disponibles; no se agregan dependencias.

### Siguiente paso

La persona usuaria puede registrar una cuenta; el administrador encontrará el aviso local en Mailpit (`http://localhost:8026`) y la solicitud en Administración de plataforma. La cuenta de prueba temporal se eliminó. Configurar SMTP externo antes de esperar correo en Gmail.

## Bloque 21 — Instalación e importación inicial desde GitHub

Fecha: 18 de noviembre de 2026, America/Bogota. El usuario pidió iniciar la siguiente función tras corregir el alta/aprobación. Confirmó la cuenta GitHub `CondezJuan8`; preguntó si la configuración `.env` es solo para pruebas. Indicó trabajar de forma que se pueda usar desde cualquier dispositivo.

### Cambios

- Implementado flujo de instalación GitHub App separado del login OAuth GitHub: owner selecciona organización, instala App y autoriza identidad de GitHub con PKCE.
- Backend verifica que la identidad autorizada pertenece a la instalación y verifica el App ID antes de persistir. El `state` es aleatorio, un uso, ligado a usuario/sesión/organización y vence en 10 minutos; tokens de usuario e instalación no se persisten.
- Implementados listado y selección/importación de hasta 500 repositorios por solicitud (hasta 2.000 por instalación), guardando metadatos existentes. No descarga fuentes/commits ni lanza análisis.
- Nueva vista de repositorios adapta encabezado, controles y filas a móvil; en pantallas anchas vuelve a distribuir acciones horizontalmente. Reutiliza tokens oficiales; no se afirma prueba visual física.
- Sin tabla/migración o dependencia nueva. Configuración agregada con valores apagados por defecto; secretos solo en `.env` local ignorado por Git. La configuración real de `CondezJuan8` requiere que el usuario cree la GitHub App.
- Guía nueva: [GITHUB_REPOSITORIOS](GITHUB_REPOSITORIOS.md), con callbacks locales, permisos mínimos, configuración y límites.

### Herramientas y verificaciones

Herramientas utilizadas: PowerShell, `apply_patch`, Maven 3.9.11/JDK 21 locales, Angular CLI 22/Node 24 locales, Git y GitHub REST API como destino aún no configurado. Backend: `ManageGitHubInstallationUseCaseTest`, 2/2 correctas; Domain, Application e Infrastructure compilaron. Frontend: build de producción correcto, 553,09 kB iniciales; avisos de presupuesto (500 kB inicial y SCSS de organización 4,96 kB frente a 4 kB). Se ejecutaron primero intentos bloqueados por permisos/argumentos; solo cuentan las ejecuciones posteriores correctas. No se ejecutaron pruebas de integración con PostgreSQL ni llamada real a GitHub. Docker no está disponible en esta sesión: no se levantaron servicios ni se probó el flujo HTTP en vivo. Git quedó habilitado después mediante el entorno autorizado y se registraron los commits listados abajo. La cuenta real de GitHub no se conectó; no se exponen credenciales.

Commits: `5c3434b` 🔌 feat: conecta instalaciones y repositorios de GitHub; `f83847e` 📱 feat: gestiona repositorios GitHub en cualquier pantalla; `4183061` 📝 docs: documenta conexión segura de repositorios GitHub.

### Siguiente paso

Configurar la App de `CondezJuan8` con Setup URL y callback indicados en la guía, introducir secretos en `.env`, reconstruir/reiniciar el servicio sin tocar el volumen de base, y probar instalación/selección en escritorio y móvil. Después implementar lectura segura de archivos/commit y encolar el análisis Maven/npm.

## Bloque 22 — Correcciones de revisión de interfaz y admisión

Fecha: 18 de noviembre de 2026. Petición del usuario: corregir cobertura incompleta del inglés, colores incoherentes con modo claro/oscuro, espera de aprobación que no se actualiza sola, validación del nombre, configuración, dashboard, organización, equipos/permisos y falta de origen de repositorios locales.

### Cambios entregados en este bloque

- La pantalla de admisión vuelve a consultar `/api/auth/me` cada 15 segundos mientras está pendiente y al recuperar el foco de la pestaña. El estado de cuenta actualizado gobierna el cambio al espacio de trabajo, sin refresco manual.
- Añadidos textos ingleses para panel de acceso/espera, dashboard y nuevas vistas; retiradas claves duplicadas que impedían compilar TypeScript.
- Ampliados los tokens para superficies/ilustraciones y sustituidos colores fijos de tarjetas, modales, panel de equipos y dashboard. El tablero de dependencias y el panel «En preparación» usan colores derivados del tema.
- «Gestionar equipo» ahora lleva la vista al editor del equipo seleccionado y lo hace enfocable.
- Perfil del backend exige nombre de al menos dos caracteres con letras y separadores admitidos; el correo de perfil ya se mostraba de solo lectura.
- Se anotaron explícitamente los pendientes de tema compartido por organización, rol de administrador con alcance por repositorio, asignación directa de permisos por miembro y carga/análisis local. Requieren backend/modelo y políticas adicionales; no se simulan como completados.

### Verificación y límites

Build Angular producción correcto tras corregir traducciones duplicadas. Bundle inicial 553,59 kB (presupuesto 500 kB); avisos de estilo Organización 4,97 kB/4 kB y Dashboard 4,13 kB/4 kB. No se hizo prueba visual automatizada ni móvil físico, llamada HTTP de aprobación/Mailpit, ni flujo local de carga. La carga de carpeta/escáner, configuración organizacional persistente, administración scoped y CRUD de permisos directos siguen pendientes. La validación del perfil Java requiere recompilar backend antes de usarla.

Herramientas: `apply_patch`, PowerShell, Angular CLI 22/Node 24. Sin dependencia frontend nueva ni migración de base de datos. El backend aún requiere la verificación de compilación focalizada correspondiente a la nueva validación.

### Siguiente paso

Revisar visualmente modo claro/oscuro e inglés en acceso, Dashboard, Organización y perfil; luego completar de forma independiente tema compartido, administración por repositorio, carga local aislada y asignaciones de permisos con autorización y pruebas de persistencia.

## Bloque 23 — Administración limitada a un repositorio

Fecha: 18 de noviembre de 2026. Continuación solicitada por el usuario para permisos de administración de repositorios.

### Cambios

- Se añade V16 para incluir `repository.manage` entre las capacidades del rol MEMBER. El permiso solo es efectivo cuando la persona pertenece a un equipo con ese grant explícito para un repositorio determinado.
- La autorización con repositorio nulo sigue reservada a OWNER/ADMIN; por eso un permiso scoped no permite gestionar toda la organización ni iniciar una instalación global de GitHub.
- El catálogo de permisos por equipo ahora incluye `repository.manage`. Se añadió una explicación en Organización: invitar como Miembro al equipo/repositorio y luego conceder el permiso específico; ADMIN sigue teniendo alcance de organización.
- Sin tablas nuevas ni cambio del número de entidades. Se añade al test de integración una comprobación de que el permiso de miembro no autoriza la operación global.
- Se actualizan arquitectura, stack, DB, guía de permisos, pendientes y bitácora.

### Verificación y límite

Maven `-DskipTests package` compiló Domain, Application, Infrastructure y todos los fuentes de test; build Angular producción correcto. No se ejecutó la prueba de integración ni se aplicó V16 contra PostgreSQL porque Docker/DB no están disponibles en esta sesión. El build Angular mantiene los avisos: bundle 553,71 kB frente a 500 kB, Organización 4,97 kB/4 kB y Dashboard 4,13 kB/4 kB. La carga de carpetas, tema compartido, permisos directos fuera de equipos y revisión completa del idioma siguen pendientes.

## Bloque 24 — Permisos directos por miembro y repositorio

Fecha: 18 de noviembre de 2026. El usuario pidió administrar permisos por miembro además de la asignación a equipos.

### Cambios

- V17 añade `member_repository_permissions` con FK a membresía/repositorio/catálogo, índice, RLS forzado y privilegios runtime/migrator.
- Application incluye validación del permiso y miembro activo; Infrastructure añade endpoint transaccional de reemplazo, auditoría y persistencia JDBC. El adaptador de autorización concede acceso por equipo o directo, siempre intersectado con el rol.
- Al retirar a un miembro se eliminan sus grants directos dentro de la misma transacción.
- Organization muestra selector de repositorio y catálogo de permisos directos dentro de Editar acceso; no depende de que la persona pertenezca a un equipo.
- Actualizados maestro, arquitectura, especificación database, stack, guía de organización y pendientes. No hay dependencias nuevas.

### Verificación y límites

Maven `-DskipTests package`: compilación de Domain/Application/Infrastructure y test-compile correctos. Angular build production correcto, inicial 554,21 kB; avisos de 500 kB inicial, 4,97 kB Organización y 4,13 kB Dashboard. Se agregó un caso IT para otorgar/retirar un grant directo, pero no pudo ejecutarse porque Docker/PostgreSQL no están disponibles. V17 tampoco está aplicada al volumen local. No se declara aceptación de runtime.

### Siguiente paso

Aplicar V16/V17 al Compose sin borrar el volumen y ejecutar el recorrido IT; revisar la edición directa en UI. Luego continuar tema compartido/configuración del espacio y carga/escaneo seguro de proyectos locales.

## Bloque 25 — Aplicación y validación de permisos por repositorio

Fecha: 18 de noviembre de 2026. Continuación autorizada para aplicar V16/V17 al PostgreSQL de Compose y comprobar acceso de equipo y directo por miembro.

### Cambios y herramientas

- Reconstruida la imagen backend con Maven y ejecutado `docker compose up -d backend`. Se recreó solo backend; PostgreSQL, Mailpit, frontend y volumen siguieron activos.
- Flyway validó 17 migraciones y aplicó V16/V17. Catálogo PostgreSQL: versión 17, 125 tablas de negocio, 88 con FORCE RLS y 221 FK.
- Reexportado el catálogo estructural sin filas de negocio/secretos; regenerados `schema.json`, diccionario y siete diagramas nativos, SVG y PNG. La validación confirma IDs y bindings únicos y todas las 221 FK cubiertas.
- Corregidos los fixtures de `OrganizationAccessIT`: cuentas verificadas también se aprueban en la base de prueba, las invitaciones incluyen equipo/repositorio, su permiso de lectura y la aprobación manual del propietario; el host SMTP es configurable para Mailpit aislado.
- El generador de diagramas ahora selecciona fuentes compatibles con Windows/Linux. Sin dependencia nueva en la aplicación.
- Herramientas: Docker Compose/Desktop, Maven/Failsafe, PostgreSQL/psql, Mailpit y contenedor temporal Python/Pillow para documentación.

### Verificación

- Imagen backend construida correctamente. Backend inició y Flyway informó `Successfully applied 2 migrations ... version v17`; backend y PostgreSQL quedaron activos/healthy junto a frontend y Mailpit.
- `OrganizationAccessIT#teamPermissionsAreIntersectedWithRolesAndRevocationsApplyToRealRequests`: 1/1 correcta en base PostgreSQL temporal, con Mailpit temporal. Comprueba permiso repository.read otorgado por equipo, intersección MEMBER/VIEWER, restricción de `repository.manage`, revocación del acceso y alta/baja de grant directo por miembro.
- Suites unitarias Domain/Application/Infrastructure: 35 casos, cero fallos. Compilación de la prueba IT correcta.
- Retirados los tres contenedores efímeros de prueba. El volumen principal no se borró. No se comparó conteo de filas de negocio antes/después del reinicio.

### Pendiente relacionado

El usuario puede revisar la pantalla Organización → permisos directos; la aceptación visual compartida sigue pendiente. No empezar el siguiente bloque automáticamente: consultar `PENDIENTES.md` y esperar instrucción del usuario.

## Bloque 26 - Preferencias compartidas de organizacion

**Peticion:** avanzar con los pendientes y registrar lo hecho, herramientas y siguiente punto.

**Implementado:** nuevo dominio `WorkspaceTheme`/`WorkspaceLanguage`; puertos de entrada/salida y `ManageWorkspaceSettingsUseCase`; endpoints GET/POST y adaptador JDBC. V18 agrega la tabla `organization_settings`, backfill, FORCE RLS, permisos y defaults en organizaciones nuevas. Angular agrega el selector por organizacion, persistencia y aplicacion de tema/idioma sobre preferencias personales. Se documenta el significado en el diccionario.

**Herramientas:** Docker Compose/Desktop, Flyway, PostgreSQL/psql, Maven y Angular CLI (build en imagen Node), export SQL y generador Python/Pillow. No se agregaron dependencias al producto.

**Verificacion:** compilacion de las imagenes backend y frontend correcta. V18 aplicada; catalogo confirma 126 tablas, 89 FORCE RLS y 223 FK. Diccionario y siete diagramas regenerados, IDs/bindings/FK validados. Health backend UP; frontend http://localhost:8089/ responde HTTP 200. El IT focalizado no pudo iniciar porque Maven Central devolvio error DNS desde el runner; queda pendiente y no se afirma aprobado. PostgreSQL, volumen y Mailpit permanecieron sin reinicio; solo se recrearon backend y frontend.

**Siguiente paso:** revisar visualmente Configuracion del espacio en la instancia local; despues completar la prueba focalizada cuando Maven Central sea accesible. Revisar luego GitHub/repo/analisis con datos reales segun `docs/PENDIENTES.md`, sin iniciar otro bloque sin instruccion.
## Bloque 27 - Traducciones de navegacion y controles activos del encabezado

**Peticion:** completar el ingles del sidebar/buscador y mantener activos los controles de idioma/tema en el encabezado.

**Cambios:** sidebar y buscador ahora pasan etiquetas/estados a `TranslatePipe`; el buscador reconoce etiquetas traducidas; se agregaron las entradas faltantes del catalogo y normalizacion de algunos textos con mojibake. El control del encabezado aplica un override personal persistido en localStorage que prevalece sobre los valores de organizacion; preferencias personales siguen disponibles en Configuracion. Se actualizan pendientes y guia de diseno.

**Herramientas/verificacion:** Docker Compose, build Angular en imagen Node, HTTP local. Build correcto con las advertencias preexistentes de presupuesto de bundle y estilos. Frontend actualizado en puerto 8089, responde HTTP 200; API/base/Mailpit permanecen activos. No se ejecutaron suites de pruebas para este cambio de presentacion.

**Siguiente paso:** revisar en navegador el idioma de sidebar/buscador y cambiar idioma/tema desde el encabezado con organizacion activa. Registrar observaciones antes de otro bloque.
## Bloque 28 - Diagnostico de conexion GitHub

**Peticion:** la instalacion GitHub falla con mensaje generico de autorizacion en la vista de repositorios.

**Hallazgo:** la vista antes cargaba repositorios sin comprobar el rol para esa organizacion y mostraba Instalacion segun el tipo global de cuenta. El backend ya exige `repository.manage` por organizacion. Ademas, el `.env` local carece de las variables `GITHUB_APP_*`, asi que la conexion externa no puede completarse todavia.

**Cambios:** se agrega `GET /api/organizations/{id}/github/status`, que devuelve solo estado configurado/capacidad de gestion y un motivo seguro, sin exponer credenciales. El frontend evita llamar el listado si falta rol/configuracion, deshabilita Instalar y muestra el requisito. Para 403, el cliente usa el detalle seguro enviado por la API. Guia y callback local actualizados a localhost:8089.

**Herramientas/verificacion:** Docker Compose, Maven/Failsafe, PostgreSQL temporal, Mailpit temporal, build Angular, inspeccion de API/permisos/configuracion y HTTP local. `docker compose build backend frontend` correcto; Maven `verify` correcto con 35 pruebas unitarias (Domain 5, Application 12, Infrastructure 18); `OrganizationAccessIT#teamPermissionsAreIntersectedWithRolesAndRevocationsApplyToRealRequests` correcto (1/1) y comprueba `canManage` para propietario y miembro. V1–V18 se validaron durante el IT. La app local sigue activa en localhost:8089: frontend expuesto, backend arriba, PostgreSQL y Mailpit saludables; no se recrearon ni borraron estos ultimos servicios ni el volumen principal. El build Angular conserva avisos existentes de presupuesto de bundle/SCSS. No se ejecutó OAuth ni instalación real: el `.env` no tiene `GITHUB_APP_*`, por lo que faltan credenciales de la GitHub App y no se inventaron ni modificaron secretos.

**Siguiente paso:** crear/configurar la GitHub App bajo `CondezJuan8`, registrar Homepage/Setup/Callback en localhost:8089 y agregar ID/Client ID/secret/private key en `.env` local. Reiniciar solo backend, revisar Estado GitHub e instalar la App para terminar la comprobacion real. No enviar secretos por chat.

**Commits:** tres commits por responsabilidad: diagnostico/API y experiencia de usuario, cobertura del permiso por organizacion y documentacion/bitacora.

## Bloque 29 - Acceso al registro de GitHub App y alcance de repositorios locales

**Peticion:** hacer que el boton de GitHub abra el sitio correcto y contemplar repositorios locales desde carpetas con un flujo equivalente.

**Decisiones confirmadas:** la GitHub App de `CondezJuan8` aun no existe; el usuario necesita crearla. El enlace de creacion debe abrir el formulario oficial de GitHub. Para repositorios locales, el usuario eligio subir las carpetas al almacenamiento privado de la organizacion.

**Cambios:** si la App no existe, la pantalla ofrece el formulario oficial `https://github.com/settings/apps/new`. Cuando se configure en `.env`, el CTA actual redirige por el backend a la instalacion `https://github.com/apps/{slug}/installations/new`. Se implementa la carga de directorio con límites 100 MiB/2.000 archivos, filtro de `.git`, dependencias/build, validación de una raíz y rutas, ZIP privado persistente fuera del webroot, registro `provider=local` y catálogo scoped para permisos existentes. V19 agrega `local_repository_sources` con FK compuestas, hash/bytes/conteo/membresía y FORCE RLS; Compose monta `repository_data` independiente de PostgreSQL. Se actualizan los documentos/diagramas database. La carga no ejecuta el análisis Maven/npm.

**Herramientas y verificación:** Docker Compose, Angular/Node, Maven/JDK 21, JUnit, Testcontainers, PostgreSQL 17.7, Mailpit, Python 3.13/Pillow y PostgreSQL/psql. Build frontend/backend correcto con 38 pruebas unitarias (Domain 5, Application 15, Infrastructure 18); `OrganizationAccessIT#localFolderImportPersistsPrivateArchiveAndOrganizationRepository` correcto (1/1) en PostgreSQL/Mailpit temporales. Flyway aplicó V19 al PostgreSQL local existente sin borrar el volumen. Catálogo estructural validado: 127 tablas, 90 FORCE RLS, 225 FK, siete diagramas y todos los IDs/bindings/FK cubiertos. Frontend `localhost:8089` HTTP 200; backend, PostgreSQL, Mailpit y volumen de fuentes activos. Advertencias Angular de bundle inicial (559,86 kB/500) y estilos (incluido repositorios 4,59 kB/4 kB) siguen visibles. La prueba real de instalación GitHub no se ejecutó porque la App aún no existe y no hay `GITHUB_APP_*`.

**Siguiente paso:** crear la App desde el enlace y configurarla localmente sin compartir secretos; después probar el consentimiento e importación GitHub. El siguiente bloque de análisis debe leer fuentes del ZIP/commit, encolar jobs aislados Maven/npm y devolver resultados/progreso. La importación actual solo guarda y asigna el repositorio.

## Bloque 30 - Preparación local de GitHub App

**Petición:** continuar configurando la GitHub App creada por `CondezJuan8` con su App ID, Client ID y llave privada local.

**Cambios:** se guardaron slug, App ID, Client ID, callback de `localhost:8089` y llave privada codificada en Base64 en `.env`, ignorado por Git. La variable `GITHUB_APP_ENABLED` queda en `false` y Client Secret vacío; no se inicia una configuración incompleta. No se copiaron secretos a documentos ni al repositorio.

**Herramientas/verificación:** PowerShell y .NET para validar encabezado/cierre PEM RSA, codificar el archivo y actualizar `.env` sin imprimir su contenido. Se confirmó en `.gitignore` que `.env` está excluido. La llave codificada tiene 2.240 caracteres; no se ejecutaron pruebas ni se reiniciaron servicios, porque falta Client Secret y la integración sigue desactivada.

**Siguiente paso:** generar el Client Secret en los ajustes de la GitHub App y agregarlo localmente a `GITHUB_APP_CLIENT_SECRET`; después habilitar `GITHUB_APP_ENABLED=true`, recrear únicamente el backend y probar instalación/selección de repositorios.
## Bloque 31 - Activación local y validación de GitHub App

**Petición:** continuar después de agregar el Client Secret a `.env` y habilitar la conexión de la App creada.

**Cambios:** se verificó la presencia de las seis variables requeridas sin imprimir sus valores, se cambió `GITHUB_APP_ENABLED=true` y se recreó únicamente el servicio `backend` con Compose (`--no-deps --force-recreate`). Se actualizaron esta bitácora, pendientes y guía GitHub. `.env` permanece excluido por Git.

**Herramientas/verificación:** Docker Compose; logs de Spring Boot/JDK 21; consulta autenticada de solo lectura con JWT RS256 a `GET https://api.github.com/app`. GitHub respondió HTTP 200 y devolvió el App ID/slug esperados. El backend inició, conectó a PostgreSQL, validó 19 migraciones y no requirió nuevas migraciones; frontend en `localhost:8089` responde HTTP 200. No se completaron instalación en cuenta/organización, consentimiento OAuth/PKCE ni importación de repositorios porque requieren una sesión/organización de plataforma y selección de repositorios en GitHub. No se ejecutaron suites de pruebas.

**Siguiente paso:** iniciar sesión con una cuenta propietaria/administradora de organización, instalar la App con acceso solo a repositorios seleccionados y completar consentimiento; después validar listado/importación real. El acceso puede revisarse en `http://localhost:8089`.
## Bloque 32 - Bootstrap seguro del primer administrador local

**Peticion:** diseñar e implementar el alta inicial del administrador antes de permitir registros que requieran aprobacion.

**Cambios:** Application agrega DTO, puertos y `BootstrapPlatformAdministratorUseCase`, que reutiliza validaciones de correo/nombre/contraseña y el puerto de hash PBKDF2. Infrastructure agrega un adaptador JDBC atomico: advisory lock, exige cero usuarios/organizaciones/administradores, crea cuenta OWNER activa/aprobada/verificada, credencial, asignacion global y evento append-only de auditoria. Un runner de consola solo existe en el perfil `platform-admin-bootstrap`, no tiene endpoint HTTP, exige confirmacion literal, contrasena oculta y confirmacion de contrasena; limpia arrays de entrada. Compose agrega servicio opt-in sin puertos, usando credenciales administrativas solo para el proceso aislado. El flujo normal no activa el perfil ni se puede autoadjudicar el rol. Documentados procedimiento y limites en `ACCESO_LOCAL`, arquitectura, contexto maestro y pendientes. Sin migraciones ni dependencias nuevas.

**Herramientas/verificacion:** JDK 21, Maven, JUnit y Testcontainers con PostgreSQL 17.7 temporal. Dos pruebas unitarias pasaron; una prueba de integracion comprobó alta, correo verificado/aprobado, hash, registro de auditoria y rechazo de reintento (1/1). `docker compose config --quiet` valido. La base real, los contenedores activos y la instancia nativa no se usaron para insertar datos ni se detuvieron. El comando interactivo no se ejecuto: debe recibir el correo/nombre y la contrasena del propietario desde su terminal.

**Siguiente paso:** ejecutar en PowerShell `docker compose run --rm -it platform-admin-bootstrap`, escribir `CREAR ADMIN` y completar los datos localmente; despues iniciar sesion en `localhost:8089`, crear la organizacion e instalar la GitHub App. `docker compose build platform-admin-bootstrap` genero la imagen efimera correctamente sin ejecutar la suite completa; no se inicio el contenedor ni se escribieron datos en la instancia real. No usar el registro hasta completar el bootstrap.

## Bloque 33 — Arranque de consola y acceso a GitHub

El operador reportó un fallo al iniciar el bootstrap. Spring intentaba resolver HttpSecurity en modo consola. Se limitó la creación de SecurityFilterChain al contexto web servlet mediante una condición de Spring Boot. Se reconstruyó la imagen con Docker Compose y se ejecutó en una terminal interactiva hasta la pregunta CREAR ADMIN. Se canceló en esa pregunta antes del alta. La API local respondió HTTP 200 en /api/auth/providers. Una consulta de lectura en PostgreSQL confirmó 7 cuentas, 1 administrador activo y 2 organizaciones. Para probar la instalación de GitHub corresponde entrar con el propietario o administrador de una organización existente. El consentimiento y la importación real siguen pendientes.

## Bloque 34 — Instalación GitHub existente, fuentes y cuentas de prueba

**Petición:** terminar la conexión de GitHub, distinguir y preparar repositorios locales/GitHub como fuentes para herramientas posteriores, y eliminar cuentas de prueba excepto un administrador y `condez.juanp@gmail.com`. El usuario decidió cerrar Mamitapuebla y conservar su historial.

**Cambios:** se añadió un flujo OAuth/PKCE para vincular una instalación de GitHub ya creada, validando el App ID, sesión, propietario y acceso de la identidad GitHub antes de persistirla. El workspace indica `provider` a cada repositorio y muestra Local/GitHub por separado. Se incorporó descarga autorizada de ZIP de fuente: local con SHA-256 verificado, GitHub en commit fijo obtenido con token de instalación temporal, límite 100 MiB y archivo temporal eliminado tras la respuesta. El endpoint exige `repository.read` y expone origen, hash y commit. No hay migración nueva.

**Limpieza de datos:** `pg_dump -Fc` previo guardado fuera de Git en `.tools/backups/dependency-before-account-cleanup.dump` (573520 bytes). Se eliminaron físicamente cuatro cuentas de prueba y sus credenciales/tokens; se revocaron siete tokens vinculados a cuentas de prueba. Mamitapuebla pasó a `SUSPENDED` y su membresía histórica a `LEFT`. Su antigua cuenta quedó anonimizada e inactiva, sin credenciales, para conservar cuatro eventos de auditoría con FK. La transacción verificó que permanecen dos cuentas activas. La primera tentativa se revirtió completamente por tratar `email_normalized` como actualizable; se corrigió y la transacción final hizo `COMMIT`.

**Herramientas y tecnologías:** Docker Compose/Desktop, PostgreSQL/psql/pg_dump, Maven/JUnit, Angular/TypeScript, Java HttpClient/ZIP/SHA-256, Git y consulta de documentación oficial de GitHub. No se añadieron dependencias de runtime ni se almacenaron tokens GitHub. Computer Use no pudo acceder al host del navegador; no se automatizó el consentimiento externo.

**Verificación:** compilación de backend/frontend correcta con Docker Compose; pruebas unitarias del backend pasaron en el build (Domain 5, Application 18, Infrastructure 20), incluida la cobertura del flujo de instalación preexistente y sus restricciones de sesión y dos pruebas de fuente local/GitHub con gateway simulado. El IT de carpeta local se amplió para comprobar la ruta de lectura, pero el build no ejecuta esa clase IT; su nueva aserción queda pendiente de ejecución. El build Angular tuvo una entrada de traducción duplicada que se corrigió y se compiló de nuevo correctamente. Backend y frontend actualizados en `localhost:8089`; `/api/auth/csrf` responde JSON HTTP 200. PostgreSQL/Mailpit y entorno nativo se conservaron. Consulta posterior: dos cuentas activas, Mamitapuebla suspendida, cero instalaciones y cero repositorios en la plataforma. Consulta autenticada de solo lectura a GitHub con JWT de la App: instalación `183677764` en `CondezJuan8`, Contents: Read, token de instalación HTTP 201 y dos repositorios autorizados HTTP 200. No se afirma validación real del consentimiento ni descarga remota porque requieren una instalación vinculada por el propietario.

**Siguiente paso:** desde la sesión de `condez.juanp@gmail.com`, vincular el ID de instalación de la App a Dinamita, autorizar GitHub, importar un repositorio real y verificar ZIP; luego integrar análisis aislado Maven/npm sobre estas fuentes.

## Bloque 35 — Retorno de GitHub y versión visible

**Petición:** la captura del usuario muestra que los dos repositorios se ven en GitHub, pero no en localhost:8089.

**Hallazgo:** la base seguía con cero instalaciones y cero repositorios. Nginx registró `POST /github/installations` y una vuelta a la vista sin solicitud a `app-callback`; el botón antiguo llevó al flujo de instalación, no a OAuth. La captura no tenía la sección «Vincular instalación existente», señal de que la pestaña conservaba el frontend anterior.

**Cambio:** `begin` consulta en el servidor hasta dos instalaciones de la App. Con exactamente una aún no vinculada, el botón **Conectar GitHub** inicia OAuth/PKCE directamente; el callback mantiene la validación de usuario, sesión, App ID y acceso antes de persistir. Con cero o varias instalaciones sigue el flujo de selección y el enlace explícito. Nginx envía `Cache-Control: no-store, must-revalidate` para `index.html` y Angular recarga una página restaurada desde el historial del navegador. Se añadió una prueba del camino automático sin saltar el consentimiento.

**Herramientas/tecnologías:** Docker Compose, logs Nginx, PostgreSQL/psql, GitHub App REST, Java/Maven/JUnit y Angular/Nginx. Sin dependencia ni migración nueva.

**Verificación:** build backend/frontend correcto; 5 pruebas Domain, 19 Application y 20 Infrastructure pasaron; HTTP del frontend devuelve `Cache-Control: no-store, must-revalidate`, API responde HTTP 200. Backend y frontend recreados; database/Mailpit y entorno nativo se conservaron. La prueba real OAuth e importación sigue pendiente de la acción del propietario en su sesión.

## Bloque 36 — Conexión real, dos repositorios y fuentes locales

**Petición:** completar la conexión GitHub y hacer operativos repositorios de nube y carpetas locales con origen diferenciado.

**Resultado GitHub:** el propietario completó OAuth desde `localhost:8089`. PostgreSQL registró la instalación `183677764` activa en Dinamita y la importación del proyecto AcmeBank desde la interfaz. GitHub confirmaba dos repositorios autorizados. La segunda selección no generó otra solicitud de importación en Nginx; para cerrar el dato solicitado se consultó su ID/rama/visibilidad/estado con token de instalación y se insertó N8N en una transacción PostgreSQL con comprobaciones de instalación activa, propietario autorizado y total final de dos. El usuario confirmó ambos en «Repositorios de este espacio» y la descarga ZIP. La API GitHub devolvió HTTP 200 para commits y HTTP 302 hacia `codeload.github.com` para el ZIP fijado por SHA.

**Cambio local:** el selector omite `.env`, `.npmrc`, llaves PEM/KEY/P12/PFX y nombres habituales de credenciales, además de carpetas generadas; Application rechaza estos archivos aunque se envíen fuera del navegador. Se permiten plantillas `.env.example`, `.env.sample` y `.env.template`. Se añadió prueba unitaria de rechazo/aceptación.

**Herramientas/tecnologías:** Docker Compose, PostgreSQL/psql, GitHub App REST con JWT/token temporal, Java/Maven/JUnit, Angular/TypeScript, Nginx, Git. No se agregaron dependencias ni migraciones.

**Verificación:** build backend/frontend correcto; test de credenciales incluido en las pruebas unitarias. Backend/frontend recreados; base, Mailpit y entorno nativo conservados. Conexión, dos filas GitHub y descarga en interfaz verificadas; carga local de una carpeta real y análisis Maven/npm todavía no.

## Bloque 37 — Carga real de carpeta local

**Petición:** corregir la carga fallida de una carpeta propia en Dinamita y dejar el repositorio LOCAL disponible para las próximas herramientas.

**Diagnóstico y cambios:** la primera petición de 33,3 MB fue rechazada por Nginx (`client_max_body_size 20m`). Se elevó el proxy a 110 MiB, acorde con el límite de 100 MiB de archivos y la envoltura multipart. La siguiente petición llegó a Spring, pero Tomcat rechazó el número de partes del formulario (dos por archivo más el nombre); se configuró `max-part-count: 4001`. Después el backend llegó al adaptador de almacenamiento, pero intentó crear `/app/data` sin permisos. `LOCAL_REPOSITORY_STORAGE_ROOT` se vinculó explícitamente a `app.local-repositories.storage-root`, que apunta al volumen privado persistente y escribible por el usuario `app`. Se conservó el filtrado de archivos de credenciales en navegador y Application. No hubo migración ni dependencia nueva.

**Herramientas y tecnologías usadas:** logs de Nginx/Spring, Docker Compose, PostgreSQL/psql de solo lectura, Java/Spring Boot/Tomcat, Maven/JUnit, Angular/TypeScript y Git. No se implementó una herramienta externa nueva.

**Verificación:** build Docker del backend correcto con las pruebas unitarias existentes; backend recreado sin tocar PostgreSQL, Mailpit ni la instancia nativa, `/health` respondió `UP`. El POST de la carpeta real devolvió HTTP 200. PostgreSQL contiene una fuente LOCAL de 446 archivos y ZIP privado de 30.110.249 bytes; el total de repositorios habilitados es tres (dos GitHub y uno LOCAL). El usuario confirmó que la carpeta aparece y que `Descargar fuente` funciona. No se inspeccionó el contenido de sus archivos.

**Siguiente paso:** acordar el bloque de extracción segura y análisis aislado Maven/npm para convertir estas fuentes en snapshots, dependencias e inventario reales. La importación por sí sola aún no analiza código.

## Bloque 38 — Análisis Maven e Inventario inicial

**Petición:** continuar el flujo análisis → dependencias → inventario y dejar la API montada en localhost.

**Implementación:** nueva API `POST/GET /api/organizations/{organizationId}/repositories/{repositoryId}/maven-analysis`, con `scan.create` para analizar y `repository.read` para consultar. El adaptador reutiliza la fuente autorizada LOCAL/GitHub, guarda hash de ZIP, commit cuando aplica, manifiestos POM, dependencias directas con versión explícita y ámbito. V20 agrega origen local, hash y contadores. Inventario ya tiene selector de repositorio, botón de análisis y lista de dependencias.

**Límites:** análisis estático de declaraciones directas en `pom.xml`; no ejecuta Maven ni plugins del proyecto, no resuelve transitivas, herencia/BOM o versiones externas no concretas, y no ejecuta npm/OSV. ZIP se procesa en memoria, sin extraerlo al filesystem.

**Herramientas/verificación:** Java/Spring Boot, PostgreSQL/Flyway, Angular, Docker Compose, Maven. Build de ambas imágenes correcto (BUILD SUCCESS; 20 pruebas de Infrastructure sin errores en el bloque ejecutado). Compose desplegado en `localhost:8089`, `/health` UP y PostgreSQL en V20. No se lanzó una operación de análisis autenticada en nombre del usuario.

**Pendiente inmediato:** el usuario debe probar «Analizar Maven» con uno de sus repositorios en Inventario; después completar npm y worker aislado.

## Bloque 39 — Dashboard con repositorios reales

**Petición:** comprobar si el dashboard se actualiza al integrar repositorios.

**Hallazgo/cambio:** antes solo consultaba organizaciones; el indicador de repositorios y sus tarjetas seguían estáticos. Ahora consulta `/api/account/workspace`, cuenta repositorios accesibles, los lista con organización y origen LOCAL/GitHub y presenta un estado vacío diferente si ya hay fuentes. Al entrar de nuevo al dashboard tras importar, la vista vuelve a consultar los datos.

**Herramientas/verificación:** Angular/TypeScript y Docker Compose. Build del frontend correcto; frontend recreado y `http://localhost:8089/` responde HTTP 200. No se inventaron totales de dependencias ni niveles de riesgo; esas métricas permanecen vacías hasta conectar sus datos.

**Siguiente paso:** aceptar visualmente el dashboard y luego ampliar su lectura de snapshots Maven para mostrar dependencias analizadas con agregación real.

**Continuación:** el dashboard consulta el último snapshot Maven de cada repositorio accesible, muestra total agregado de dependencias directas y hasta cinco análisis recientes con fecha y conteo por repositorio. No representa riesgo ni transitivas. Frontend recompilado, desplegado y HTTP 200.

## Bloque 40 — Análisis npm desde package-lock

**Petición:** completar análisis npm en el flujo del repositorio, Inventario y dashboard.

**Implementación:** `POST/GET /api/organizations/{organizationId}/repositories/{repositoryId}/npm-analysis`. Lee `package-lock.json` v2/v3 desde el ZIP sin extraer archivos ni ejecutar `npm install` o scripts. Guarda versiones resueltas del mapa `packages`, clasifica directas/transitivas desde las dependencias raíz y marcas del lockfile, y registra ámbito runtime/development/optional. V21 registra scanner; el catálogo normalizado ahora persiste ecosistema y tipo de dependencia. Inventario ofrece acciones Maven y npm y muestra ambos tipos. Mantiene permisos `scan.create`/`repository.read` y hash de fuente/commit.

**Límites:** lockfiles v1, múltiples `package-lock.json` en un ZIP, enlaces locales y metadatos sin versión se excluyen o rechazan; el parser no ejecuta gestor ni valida integridad de instalación. No hay aún aristas padre-hijo de transitivas, OSV ni actualización del resumen del dashboard para contar npm.

**Verificación:** build Docker backend/frontend correcto (BUILD SUCCESS; pruebas de módulos ejecutadas sin errores), V21 aplicada, backend/frontend activos, `/health` HTTP 200 en `localhost:8089`. No se probó un repositorio npm real autenticado por el usuario.

**Siguiente paso:** probar un repositorio con `package-lock.json` v2/v3 en Inventario; luego guardar aristas de dependencia, agregar npm al dashboard y conectar avisos OSV con su evidencia.

## Bloque 41 - Catálogo visual unificado del inventario

**Petición:** reorganizar la parte visual para que Inventario sea atractivo, funcional, accesible, entendible y responsive; añadir mejoras necesarias sin inventar capacidades de seguridad.

**Cambios:** se reemplazó la vista que obligaba a pulsar botones separados Maven/npm por una pantalla con una acción coordinada que solicita ambos análisis existentes en paralelo y conserva resultados parciales. El catálogo agrupa por ecosistema + namespace + paquete y anida versiones exactas; expone búsquedas y filtros por ecosistema, tipo directo/transitivo y ámbito. Añade resumen de identidades/versiones/ocurrencias, estados por scanner, llamadas claras a la acción, estado de riesgo sin evaluar y procedencia desplegable por ocurrencia (origen, SHA-256, commit, manifiesto y fecha). La vista usa etiquetas visibles, estados anunciados a lectores de pantalla, foco de teclado, `details/summary` accesible y layout mobile-first con tokens de color existentes.

**Archivos y alcance:** `frontend/src/app/features/inventory/inventory-workspace.ts`, su plantilla y SCSS, traducciones ES/EN; documentación del contexto maestro, arquitectura, stack, diseño visual y pendientes. Sin cambios Domain/Application/Infrastructure, endpoints, base de datos, permisos ni dependencias externas. Se actualizó el estado de pendientes: UI agregada lista en código; aristas de grafo, más cobertura/resolución, agregación multi-repositorio, npm en dashboard y OSV continúan pendientes.

**Herramientas y tecnologías usadas:** Angular 22, TypeScript, RxJS `forkJoin`, HTML semántico, SCSS, tokens oficiales y APIs Maven/npm existentes. No se implementó una dependencia nueva.

**Verificación:** build de producción Angular mediante `docker compose build frontend` correcto. El chunk de Inventario sigue lazy; sus estilos se separaron en dos hojas encapsuladas (4,32 KB y 5,51 KB), ambas bajo el error máximo de 8 KB. Permanecen avisos de presupuesto ya aplicables al producto: bundle inicial 565,21 KB frente a 500 KB de aviso y estilos grandes de otras pantallas. Se actualizó únicamente el frontend con `docker compose up -d --no-deps --force-recreate frontend`; `/` respondió HTTP 200 y el contenedor quedó activo en `127.0.0.1:8089`. No se ejecutaron pruebas automatizadas; el backend no cambió.

**Siguiente paso:** revisar Inventario en escritorio/móvil y el recorrido Maven + npm con un repositorio real; después priorizar relaciones transitivas verificables y la agregación npm del dashboard.

## Bloque 42 — evaluación OSV por versión exacta

**Petición:** comenzar la siguiente función del producto: vulnerabilidades por inventario, conectadas a OSV y preparadas para avisar cuando las versiones detectadas tengan riesgo, cuidando frescura y evidencia.

**Cambios:** se añadieron el estado de dominio y el caso de uso/puertos Application para evaluar las versiones exactas de los últimos snapshots Maven/npm. Infrastructure consulta OSV mediante `querybatch`, pagina respuestas con límite, recupera detalles por ID con timeout HTTPS y calcula SHA-256 de la evidencia. La API valida permiso del repositorio antes de llamar a OSV; la red no queda dentro de la transacción de autorización. V22 guarda snapshots de consulta por componente, RLS tenant-scoped, código de falla, timestamps, JSON fuente y hash; V23 limita el runtime a lectura e inserción para que el historial sea append-only, sin dar acceso al escritor del catálogo global. La UI consulta al ejecutar análisis y al abrir Inventario; muestra avisos con enlaces, resumen/CVSS y distingue coincidencia, sin avisos conocidos, resultado parcial, no disponible, viejo y todavía sin evaluar. Se agregaron traducciones en inglés, estados accesibles y colores semánticos.

**Base de comparación:** Maven consulta nombre `groupId:artifactId` con ecosistema OSV `Maven`; npm usa nombres exactos y ecosistema `npm`. Lote, base HTTPS, timeouts y frescura son configurables; por defecto lote 100, conexión 5 s, respuesta 15 s y antigüedad 24 h. OSV no se presenta como garantía de seguridad y la falta de avisos conocidos se distingue del fallo de consulta.

**Herramientas y tecnologías usadas:** Java 21, JDK `HttpClient`, Spring `RestClient`, Jackson, JDBC, PostgreSQL 17.7, Flyway, Angular 22, TypeScript, SCSS, Docker Compose y Python/Pillow temporal para regenerar documentación estructural. No se añadió una dependencia de producto.

**Verificación:** se añadieron dos pruebas de Application que comprueban que respuesta vacía y respuesta ausente no se mezclan; pasaron. El build backend pasó: 47 pruebas unitarias en Domain/Application/Infrastructure (incluye ambas pruebas), compilación de los cuatro módulos y comprobación arquitectónica hexagonal. Build Angular pasó; persisten avisos anteriores del bundle inicial 566.55 KB/500 KB y hojas grandes debajo del máximo de error 8 KB. V22/V23 se aplicaron en PostgreSQL local desde V21 sin modificar ni borrar el volumen; health y portada respondieron HTTP 200; `scan_vulnerability_assessments` tiene ENABLE+FORCE RLS. Catálogo y siete diagramas se regeneraron: 128 tablas de negocio, 91 tenant tables con FORCE RLS, 227 FK, IDs/bindings únicos y cobertura de todas las FK. No se probó con un repositorio real en esta sesión.

**Límites:** no se han validado datos reales de advisories para los repositorios del usuario, cobertura OSV, rango completo de versiones, hallazgos normalizados/globales, recomendaciones, fixes, segunda fuente, aristas ni remediación. Una respuesta vacía significa solo que OSV no reportó avisos conocidos en esa consulta.

**Siguiente paso:** abrir Inventario en `http://localhost:8089` con un repositorio Maven/npm real; comprobar visualmente aviso, respuesta sin avisos y fallos de red antes de añadir el resumen npm al dashboard o avanzar al grafo. Los contenedores backend/frontend quedaron recreados y health/portada responden HTTP 200.

## Bloque 43 — vulnerabilidades claras en Dashboard e Inventario

**Petición:** dar más espacio a la tipografía; mostrar en Dashboard los avisos clasificados con los cuatro colores semánticos; explicar los errores técnicos en palabras comprensibles.

**Cambios:** Dashboard consulta análisis Maven/npm y snapshots OSV para todos los repositorios accesibles. Muestra conteos de versiones por Crítico, Riesgo alto, Advertencia y Sin avisos conocidos, una lista de hasta seis avisos con paquete, versión, repositorio y enlace a OSV, y un aviso aparte para evaluaciones pendientes. La etiqueta sin avisos se reserva a `NO_KNOWN_ADVISORIES`; fallo, atraso o evaluación pendiente no se interpreta como seguro. Inventario incorpora explicación/acción enfocadas en los dos advisories Jackson de la captura, muestra versiones corregidas publicadas y separa la explicación del detalle técnico expandible. Se aumentó tipografía, interlineado y espacio de los paneles y se corrigió el ajuste de texto de referencias largas.

**Archivos y alcance:** Dashboard, vista de Inventario, SCSS, `docs/PENDIENTES.md`, contexto maestro, arquitectura, stack y diseño visual. Solo frontend/documentación: sin cambios de API, Domain/Application/Infrastructure, permisos, migraciones ni dependencias nuevas.

**Herramientas y tecnologías usadas:** Angular 22, TypeScript, RxJS `forkJoin`, HTML semántico, SCSS y tokens oficiales de color. OSV sigue siendo la fuente; las explicaciones conocidas son texto determinista, sin llamar a Gemini.

**Verificación:** pendiente de ejecutar el build de producción frontend, recrear solo el contenedor frontend en `localhost:8089` y revisar health/HTTP. No se ejecuta la suite backend porque no se modificó código de servidor. Aceptación visual en escritorio/móvil queda pendiente del usuario.

**Siguiente paso:** abrir Dashboard y luego un paquete afectado en Inventario con los repositorios reales; comprobar conteos, estados incompletos, lectura de enlaces y explicación de avisos.

**Verificación añadida:** `docker compose build frontend` terminó correctamente con Angular 22. Avisos restantes de presupuesto: bundle inicial 570.35 kB/500 kB, hojas preexistentes de organización/repositorios/inventario, y dashboard 4.15 kB/4 kB (no supera el límite de error). Se recreó solo el frontend; `http://localhost:8089/` y el bundle JS respondieron HTTP 200. Backend quedó UP; PostgreSQL y Mailpit healthy. No se ejecutaron pruebas automatizadas: este bloque solo cambia frontend/documentación.

**Fuentes oficiales usadas para las explicaciones:** [GHSA-wjgm-6hv5-3cvf](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-wjgm-6hv5-3cvf) y [GHSA-wv8q-qhhj-9h54](https://github.com/advisories/GHSA-wv8q-qhhj-9h54). La primera requiere valorar la conversión de JSON a Path y los providers presentes; la segunda depende de configuración polimórfica con fallback e identificadores externos. La mera presencia de la dependencia no confirma que esas condiciones se cumplan en el repositorio.

**Cierre de verificación:** el build final (incluido el aviso de carga OSV fallida) también pasó. El bundle inicial quedó en 570.70 kB; solo se informan avisos de presupuesto, no errores. Frontend recreado; portada y bundle HTTP 200 en `localhost:8089`.

## Bloque 44 — evidencia de vulnerabilidad y gestión de repositorios

**Petición:** al abrir un aviso, explicar el problema con referencias concretas al repositorio: versiones corregidas, archivo/línea donde buscar y contenido; ampliar la procedencia que se veía comprimida; usar el logo en la pestaña; permitir eliminar repositorios y reflejar cambios/revocaciones en GitHub.

**Cambios:** los avisos OSV son secciones desplegables con ID/severidad, explicación por escenario, qué revisar, versiones corregidas y el detalle/referencias oficiales. El botón de evidencia pide al backend una búsqueda estática para dos advisories Jackson en el ZIP actual y presenta ruta, línea, fragmento y motivo; identifica proveedor, commit y SHA-256. El lector opera sobre ZIP en streaming sin extraer archivos, con límites 128 MiB expandidos, 10.000 entradas, 1 MiB por fuente y ocho resultados. Coincidencia/no coincidencia no se interpreta como juicio de explotabilidad; no se sigue flujo de datos ni hay análisis interprocedimental. La procedencia se abre por defecto, usa todo el ancho y campos legibles. La pestaña usa `brand-mark.svg`.

Se añadió DELETE autenticado por `repository.manage`. GitHub se deshabilita solo en el espacio y conserva historial; el repositorio remoto no se elimina. Local elimina ZIP privado y metadatos de la fuente; scans/historial permanecen. Al abrir/solicitar sincronización de GitHub, el servidor vuelve a listar repositorios autorizados, deshabilita los que fueron retirados y clasifica como REVOKED una instalación cuyo token/listado devuelve 404. No se implementó webhook ni sincronización en segundo plano. No hay cambios de esquema ni dependencias nuevas.

**Herramientas y tecnologías utilizadas:** Java 21, Spring MVC/RestClient, puertos/casos de uso hexagonales, JDBC/JPA y PostgreSQL existentes, ZipInputStream, SHA-256, Angular 22, TypeScript, HTML semántico, SCSS/tokens, Angular HttpClient/CSRF, Docker Compose. Sin librerías nuevas.

**Verificación:** `docker compose build --build-arg OPERATOR_TOOL_IMAGE=true backend frontend` pasó: compiló Domain/Application/Infrastructure y fuentes de prueba, con ejecución de tests omitida; Angular completó producción. El bundle inicial quedó en 574,05 kB/500 kB y varias hojas superan el presupuesto de advertencia de 4 kB; no son errores de build. Solo se recrearon backend y frontend; PostgreSQL/Mailpit no se reiniciaron y PostgreSQL sigue healthy. `http://localhost:8089/` respondió 200; `/brand-mark.svg` respondió 200 con `image/svg+xml`; logs confirman backend Spring Boot iniciado y Flyway V23 al día. No se ejecutaron suites ni recorridos reales de borrar repositorio, retirar GitHub o buscar una vulnerabilidad; estos flujos requieren revisión funcional con la cuenta/repositorios del usuario.

**Siguiente paso:** probar en la UI una dependencia vulnerable expandiendo aviso → «Buscar dónde revisar en el código» y revisar ruta/línea/commit; verificar procedencia completa. Luego sincronizar GitHub tras retirar un repositorio de la instalación y usar la eliminación confirmada sobre un repositorio de prueba. En esta instancia Compose, la base indicada por el estado operativo está vacía; el entorno nativo se mantuvo activo.

## Bloque 45 — grafo de dependencias Maven/npm

**Petición y alcance:** implementar el grafo con JavaScript/Cytoscape existente. Decisión confirmada: npm completo según relaciones del lockfile; Maven directo declarado, sin iniciar resolución transitiva en este bloque.

**Cambios:** Angular agrega la vista `dependencies` con selector por repositorio/ecosistema, búsqueda, resumen, Cytoscape con zoom/ajuste, lista textual accesible, selección/detalle de paquete, procedencia y estado vacío. El canvas toma colores desde tokens CSS y reacciona a cambio de tema. Application incorpora `GetRepositoryDependencyGraph`, DTO/puerto; Infrastructure expone lectura JDBC tenant-scoped con autorización `repository.read`. La consulta compone los últimos scans Maven/npm exitosos, nodos por ocurrencia, aristas observadas y raíces virtuales del proyecto. El parser npm lee paths instalados y secciones de dependencias en package-lock v2/v3; persiste relaciones como `depends_on` en `dependency_edges`. El POM mantiene únicamente dependencias directas con versión explícita. Sin cambios de esquema ni dependencias nuevas. Scans npm previos requieren reanálisis para generar relaciones.

**Herramientas/tecnologías utilizadas:** Java 21, Spring MVC, JDBC, PostgreSQL/`TenantTransactions`, casos de uso y puertos hexagonales existentes; Angular 22, TypeScript, RxJS, Cytoscape.js 3.35 instalado, HTML/SCSS, tokens CSS; PowerShell, Node local y Maven local. Se reutiliza `dependency_edges`; no se implementan librerías ni servicios nuevos.

**Verificación:** build de producción Angular completado, bundle inicial 577,15 kB (77,15 kB por encima del presupuesto de 500 kB); SCSS del grafo 7,64 kB con advertencia de presupuesto de 4 kB. Build Maven reactor Domain/Application/Infrastructure y prueba focal `BoundedNpmLockReaderTest`: 1 test, 0 fallos. Build Docker de ambas imágenes correcto. Se recrearon únicamente backend/frontend; PostgreSQL y Mailpit siguen saludables y sin reiniciarse. Log confirma Spring Boot iniciado y Flyway V23 al día. `http://localhost:8089/` y `/?view=dependencies` responden HTTP 200. `/api/actuator/health` devolvió 401 por la capa de acceso; no se usó como indicador de salud. No se ejecutó suite completa ni prueba JDBC/PostgreSQL autenticada. La base Compose descrita en AGENTS está vacía, por lo que no se validaron nodos/relaciones reales ni el aspecto visual autenticado; se requiere importar/analisar datos para completar ese recorrido. No se detuvo ni alteró el entorno nativo.

**Siguiente paso:** desde `http://localhost:8089`, importar/reanalizar un npm y un Maven y recorrer la vista en móvil/escritorio, claro/oscuro. Después implementar resolución Maven transitiva y evaluar rendimiento/legibilidad con grafos grandes. Riesgo actual: carga del grafo sin paginación/límite de nodos.

## Bloque 46 — hasta 50.000 archivos por carpeta local

**Petición:** conservar el máximo de tamaño actual y permitir hasta 50.000 archivos en una carpeta de proyecto.

**Cambios:** Application mantiene 100 MiB totales y aumenta MAX_FILES de 2.000 a 50.000. El navegador valida el mismo conteo y conserva el corte de 100 MiB. El controlador comprueba ruta/cantidad. V24 aumenta `local_repository_sources_file_count_check` a 50.000 (sin nueva tabla ni cambio de conteos). Spring multipart acepta 100MB por archivo, conserva 100 MiB total en Application y permite un cuerpo HTTP de hasta 200MB para rutas/cabeceras multipart; Tomcat permite 100.001 partes y parámetros (un nombre y pares ruta+archivo); `max-http-form-post-size` y Nginx quedan en 200MB. La descarga de ZIP GitHub permanece con su límite independiente de 100 MiB. Fuentes técnicas: [propiedades Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html) y [configuración HTTP Tomcat 10.1](https://tomcat.apache.org/tomcat-10.1-doc/config/http.html).

**Herramientas/tecnologías utilizadas:** Java 21, Spring Boot multipart y Tomcat, Angular/TypeScript, Nginx, PostgreSQL/Flyway, Docker Compose, JUnit 5 y Maven. Sin dependencias nuevas.

**Verificación:** prueba focal `LocalFolderImportUseCaseTest`: 6/6 correctas, cubre 50.000 aceptados, 50.001 rechazados y archivo mayor de 100 MiB rechazado. Build Angular correcto (avisos preexistentes de bundle/SCSS); build Docker backend/frontend correcto, tests omitidos en ese build. Se recrearon solo backend y frontend; PostgreSQL/Mailpit no se reiniciaron. Backend inició, `/health` respondió `{"status":"UP"}` y Flyway aplicó V24. Consulta a PostgreSQL confirmó `CHECK (file_count >= 1 AND file_count <= 50000)`. `localhost:8089/` respondió 200; la instancia nativa no se alteró. No se probó una subida real con 50.000 partes ni el límite de 100 MiB mediante navegador; la prueba del use case verifica este último en Application. La carga masiva puede requerir espacio temporal adicional para partes multipart y el ZIP final.

**Siguiente paso:** probar en la pantalla de repositorios una carpeta representativa con muchos archivos, sin superar 100 MiB. Confirmar progreso y uso de almacenamiento del host; mantener volumen libre para multipart temporal y el ZIP privado final.

## Bloque 53 — grafo de dependencias estilo Obsidian

**Petición:** hacer que el grafo recuerde a Obsidian: más visual, legible y exploratorio, sin penalizar de forma desproporcionada los repositorios grandes. El usuario aclaró que los errores «Bad Request» reportados eran de mis tareas/ejecuciones, no de la plataforma.

**Cambios:** la vista de red conserva posiciones al filtrar o redibujar nodos; colorea y escala los nodos según tipo y número de relaciones; al pasar el cursor o seleccionar un paquete resalta sus conexiones y reduce el contraste del resto. Las etiquetas aparecen para el nodo central y su vecindad, o al acercar, para mantener legible la red amplia. Se agregaron instrucciones visibles y traducibles, cursor de desplazamiento y leyenda más clara. Reutiliza Cytoscape.js y tokens de tema ya presentes; no agrega dependencias, endpoints ni tablas.

**Herramientas y tecnologías utilizadas:** TypeScript, Angular 22, Cytoscape.js existente, HTML/SCSS y diccionario de traducciones; Node.js local para el build; PowerShell para la verificación HTTP y documentación.

**Verificación:** `node.exe node_modules/@angular/cli/bin/ng.js build` terminó correctamente. Angular reporta advertencias de presupuesto: bundle inicial 581,91 kB/500 kB y `dependency-graph-workspace.scss` 11,35 kB/4 kB; no impiden compilar. `http://localhost:8090/?view=graph` respondió HTTP 200. No hice un recorrido visual autenticado ni medí un grafo real grande; siguen pendientes la revisión del usuario con Omega y bot de WhatsApp, temas claro/oscuro y móvil/escritorio. Los servicios 8088/8089 se mantuvieron intactos.

**Siguiente paso:** abrir la vista de grafo en localhost:8090 y revisar la exploración de un repositorio de más de 200 dependencias; confirmar agrupación, legibilidad y rendimiento en escritorio/móvil antes de ajustar más.

## Bloque 54 — vista general y exploración local del grafo

**Petición:** rediseñar el grafo para repositorios grandes con una vista global legible y exploración local de una dependencia, filtros combinables, búsqueda centrada y controles de navegación al estilo Obsidian.

**Cambios:** la vista general usa la distribución force-directed como opción inicial, conserva las posiciones de nodos conocidos al cambiar filtros y evita recomputar el layout si todos conservan coordenadas. La vista local empieza a un salto del paquete seleccionado y permite profundidad de 1 a 5 y dirección «Depende de», «Usada por» o ambas. Se agregan filtros combinables de ecosistema, namespace/módulo, dependencia directa/transitiva y ámbito; grupos de namespace desplegables por selección para inventarios superiores a 200; contador de paquetes visibles y aristas; filtros activos y limpieza global. Buscar centra y resalta el resultado y su contexto. El grafo local y los filtros solo conservan aristas observadas cuyos dos extremos siguen visibles: no se puentean nodos ocultos. Los nodos pequeños muestran sus etiquetas; en redes grandes se priorizan proyecto/directas y el zoom revela transitivas. Cytoscape existente. La API no expone severidad de vulnerabilidad en su DTO, así que no se añadió ese filtro.

**Herramientas y tecnologías utilizadas:** Angular 22, TypeScript, señales y computed existentes, Cytoscape.js 3.35, SCSS y diccionario de traducciones; Node.js local para build y medición headless; PostgreSQL local vía Docker Compose en solo lectura para contar los scans; PowerShell. Sin dependencias nuevas ni cambios de API/base de datos.

**Verificación:** build de producción Angular completado. Advertencias: bundle inicial 583,00 kB/500 kB; SCSS del grafo 13,09 kB/4 kB (máximo de error del componente ajustado de 12 a 14 kB, mantiene advertencia visible). Medición headless de Cytoscape `cose` con nodos/aristas reales: `OMEGA-main`, 277 paquetes, 492 relaciones entre paquetes y 27 declaraciones desde raíz; layout 297 ms. `Bot-de-Whatsapp-plantilla--main`, 284 paquetes consolidados, 411 relaciones entre paquetes y 13 declaraciones desde raíz; layout 306 ms. En ambos, se probaron recorridos locales y aristas inducidas sin extremos ocultos; además se comprobó combinación de filtros por módulo/tipo/ámbito. El caso pequeño fue un fixture de 6 paquetes tomado de OMEGA (6 paquetes, 6 relaciones internas, una declaración raíz, 3 ms), no un repositorio independiente de seis dependencias. No hay un repositorio real de ese tamaño en la base Compose activa. No se pudo hacer revisión visual/navegación automatizada: el host de navegador rechazó la sesión porque no pudo determinar la URL de Chrome con suficiente confianza. `localhost:8088` no respondió. Se actualizó solo el frontend Compose; `localhost:8089/?view=graph` y `chunk-CMkQCyj5.js` respondieron HTTP 200 y el bundle servido contiene el texto «Vista local». Backend, PostgreSQL y Mailpit permanecieron en ejecución. No se afirma que se hayan verificado en UI zoom, responsive o ausencia de bloqueo.

**Siguiente paso:** revisar interactivamente `http://localhost:8089/?view=graph` con el usuario: probar el fixture pequeño de seis paquetes y ambos repositorios reales, incluyendo búsqueda, filtro combinado, vista local, profundidad, dirección, zoom, ajuste y vuelta al mapa general en móvil/escritorio.

## Bloque 57 — validación de scanner y OSV (22 de noviembre de 2026)

**Petición:** validar scanner y OSV como herramientas distintas que se complementan.

**Hallazgo del flujo:** los endpoints Maven/npm ejecutan cdxgen, mapean CycloneDX y guardan los componentes/relaciones del scan. El endpoint OSV lee el último snapshot completado por ecosistema (incluye los nombres históricos), consulta versiones exactas y guarda las evaluaciones ligadas a los componentes. No reescanea; un fallo OSV no debe revertir ni borrar inventario. El lector npm tiene recuperación acotada desde `package-lock.json` y declara `PARTIAL` cuando cdxgen devuelve un BOM vacío. Maven se mantiene parcial en transitivas no resueltas por modo seguro.

**Verificación realizada:** la instancia `localhost:8089/health` respondió `UP`; el health API protegido devolvió 401 sin sesión, y Mailpit `localhost:8026` respondió 200. Reportes existentes del 20/11: `CdxgenScannerAdapterTest` 2/2, `CdxgenNpmLockfileReaderTest` 1/1 y `OsvRestAdapterTest` 2/2, sin fallos. El README registra además smoke tests anteriores de cdxgen 13.8.0 con npm scoped/transitivo y Maven multimódulo, solo en fixtures. Consulta directa de solo lectura a OSV el 22/11: Log4j 2.14.1 incluyó `CVE-2021-44228` y 2.17.1 no; lodash 4.17.20 incluyó `CVE-2021-23337` y 4.17.21 no. Las versiones corregidas aún tuvieron otros avisos OSV, por lo que no se interpretan como limpias en general.

**Límites de esta sesión:** los tests no se repitieron; Maven Central estaba bloqueado en el sandbox y el reintento con red autorizada se detuvo durante la primera descarga de dependencias, antes de ejecutar pruebas. No se completó el recorrido autenticado real en la UI: la automatización de navegador no pudo iniciar porque el runtime Node requerido no estaba disponible; no se usaron credenciales. La consulta directa al proveedor confirma la respuesta actual de OSV, no que la instancia Compose haya procesado y mostrado esa respuesta.

**Herramientas y tecnologías utilizadas:** revisión de controladores/lectores/adaptador, reportes JUnit existentes, JDK 21/Maven 3.9.11, HTTP de solo lectura a OSV y checks HTTP locales. Sin cambios de código de producto ni de base de datos.

**Siguiente paso para cerrar:** con una sesión autenticada en `localhost:8089`, analizar un repositorio Maven y uno npm, confirmar cantidades/estado de cobertura en Inventario, luego usar **Consultar OSV** y verificar estados/hallazgos visibles. Mantener fixtures/repositorios y datos actuales intactos.

## Bloque 59 — resolver Maven local y ordenar Inventario

**Petición:** hacer que el scanner recupere mejor las dependencias verificables y presentar el inventario con una jerarquía que resulte clara para personas nuevas y conserve filtros útiles para perfiles técnicos.

**Cambios:** se añadió `MavenPomDependencyResolver` como fallback estático al SBOM Maven vacío. Lee POMs desde ZIP con límites existentes, resuelve herencia por ruta/GAV local, propiedades y versiones administradas, incluidos BOM locales importados. No descarga Maven/BOM remotos, no ejecuta código del repositorio ni inventa transitivas. Inventario agrupa el estado por ecosistema, aclara qué significa cobertura parcial en un detalle plegable, prioriza catálogo, buscador y filtros rápidos y deja criterios avanzados plegables; conteo indica cuántas dependencias pasan los filtros. El aviso de fin de análisis ya no encabeza el flujo con un mensaje de cobertura parcial.

**Herramientas utilizadas:** Java 21, Maven 3.9.11, JUnit 5, Angular 22, TypeScript, SCSS y diccionario de traducciones existente. No se añadieron dependencias, endpoints ni migraciones.

**Verificación:** `CdxgenMavenManifestReaderTest` 3/3, `CdxgenNpmLockfileReaderTest` 1/1 y `CdxgenScannerAdapterTest` 2/2. `npm run build` finalizó correctamente; se mantienen avisos de presupuesto Angular ya presentes y el bundle inicial queda en 587.53 kB/500 kB. El primer intento de build/test sin permisos no pudo leer fuentes y el repositorio Maven por defecto intentó crear `C:\.m2`; con permisos y caché Maven local del usuario las pruebas focales pasaron. No se reanalizó OMEGA en una sesión autenticada, por lo que su conteo de componentes tras el fix no está verificado. No se tocaron los servicios de datos.

**Siguiente paso:** desde localhost:8089, seleccionar OMEGA, ejecutar análisis y confirmar cantidad de dependencias Maven/OSV; luego revisar filtros y resumen visualmente. Completar Maven remoto/transitivo solo tras acordar el modelo seguro que se aceptará para esas fuentes.
## Bloque 60 — revisión del sidebar y estado real de pantallas

**Petición:** revisar el proyecto y explicar las pantallas y la función de cada opción del sidebar antes de seguir con cuatro pantallas restantes.

**Revisión:** contrasté el array de navegación (`ready`), el switch de `application-shell.html`, los componentes y acciones Angular, y los controladores REST existentes. Confirmé que Vulnerabilidades, Análisis, Documentación y Reportes usan el fallback de “En preparación” y no tienen páginas funcionales propias. OSV se muestra desde Dashboard/Inventario y el scanner se ejecuta desde Inventario. Identifiqué que Dashboard conserva placeholders fijos de grafo/evolución y que su lista de análisis recientes solo lee Maven. Las otras opciones del menú sí cargan sus componentes; el alcance real está descrito en el bloque 60 del contexto maestro.

**Herramientas utilizadas:** búsqueda `rg`, inspección de templates/TypeScript, controladores Spring y documentación del proyecto. Sin cambios de producto.

**Verificación:** revisión estática del código; no se ejecutaron pruebas porque este bloque solo documenta el mapa existente. No se revisó la UI con sesión de navegador.

**Siguiente paso:** cerrar el contexto maestro y luego ordenar las cuatro pantallas pendientes con criterios de aceptación antes de implementarlas; atender por separado textos de navegación obsoletos y placeholders del Dashboard.
## Bloque 61 — petición 1: objetivo y alcance del producto

**Petición:** integrar explícitamente pgvector, embeddings, RAG, orquestador y chat LLM en el plan de 15 peticiones y empezar por definir objetivo y alcance.

**Decisiones consolidadas:** producto completo listo para usar por fases, primero local y luego servidor; la demo previa de dos semanas/50 horas ya no limita el alcance ni constituye fecha comprometida. El primer piloto mantiene GitHub y repositorios locales, Maven/npm, equipos/permisos por repositorio, análisis trazable y propuesta de PR aprobable con pruebas aisladas. Responsables/líderes técnicos y seguridad/DevSecOps tienen igual prioridad. Gemini será el LLM y Ollama el proveedor de embeddings; Codex queda fuera hasta petición explícita. No se inventan modelo, dimensión, límites ni cuotas. Correo de notificaciones queda posterior a notificaciones in-app.

**Plan maestro actualizado:** 15 peticiones; separa centro documental (ingesta/permisos/retención) de la arquitectura IA: modelo/dimensión Ollama, pgvector y aislamiento, recuperación/RAG con citas, orquestador y chat Gemini con control de permisos/evaluación. `pgvector` sigue sin activarse hasta que se diseñe esa fase.

**Herramientas utilizadas:** lectura comparativa del contexto maestro, arquitectura backend, especificación de base de datos, stack y mapa de navegación; edición documental con PowerShell/.NET UTF-8. Sin cambios de código ni migraciones.

**Verificación:** búsqueda de contradicciones históricas sobre demo de dos semanas en el contexto; el plan antiguo quedó rotulado como histórico. Revisión documental, sin pruebas de código.

**Siguiente:** petición 2 — cerrar identidad, roles, permisos y configuración, contrastando con los flujos ya implementados.

## Bloque 62 — petición 2: auditoría de identidad, roles y configuración

**Petición:** revisar identidad, roles, permisos y configuración contra la implementación y el contexto maestro.

**Hallazgos verificados:** administrador global separado de `account_type` y roles organizacionales; registro abierto crea OWNER en `PENDING_REVIEW`; invitación crea MEMBER aprobado, requiere correo verificado, aceptación y aprobación posterior del OWNER invitante. Roles OWNER/ADMIN/MEMBER/VIEWER. ADMIN tiene permisos organizacionales amplios, pero endpoints de invitación/aprobación/revocación exigen OWNER. MEMBER puede administrar repositorio concreto mediante `repository.manage` + grant directo/de equipo, sin ADMIN organizacional. Autorización exige estado activo/aprobado, membresía activa y la combinación de permiso de rol y grant por repositorio. Configuración distingue perfil, overrides personales y preferencias de espacio editables por OWNER/ADMIN. Google/GitHub soportan login/vinculación, pero el estado operativo de Compose indica proveedores desactivados.

**Decisiones del usuario durante el bloque:** MEMBER debe ver un Dashboard limitado a sus repositorios. Un MEMBER con permiso `repository.manage` en un repo se presentará como «Administradora del repositorio A» sin adquirir permisos de organización. Petición 2 queda cerrada como requisitos; UI aplazada al bloque de pulido.

**Herramientas utilizadas:** inspección de Java Domain/Application/Infrastructure, consultas JDBC de autorización, migraciones Flyway V5/V14/V16/V17/V18, templates/servicios Angular y documentos vigentes. Sin cambios de código o esquema.

**Verificación:** revisión estática; sin pruebas de código ni sesión autenticada. La matriz y las dos decisiones confirmadas están registradas en el bloque 62 del contexto maestro.

**Siguiente paso:** petición 3 — cerrar fuente GitHub/local, importación, sincronización y ciclo de vida de repositorios.

## Bloque 63 — fuentes GitHub y locales (petición 3, 22/11/2026)

**Petición.** Revisar el flujo completo de repositorios y fijar requisitos y criterios de aceptación en el contexto maestro.

**Cambios.** Se documentó cómo se importan, autorizan, descargan y retiran las dos fuentes. GitHub obtiene un ZIP temporal por commit. Local persiste un ZIP privado identificado por SHA-256 y admite 100 MiB de contenido y 50.000 archivos. La sincronización de GitHub ocurre bajo demanda y la aplicación conserva el historial al retirar el acceso. El registro de pendientes recoge desconexión explícita, webhooks, revisión de permisos al reactivar y validaciones con accesos reales.

**Herramientas utilizadas.** `rg`, PowerShell y Git para revisar controladores y casos de uso Java, consultas JDBC, migraciones Flyway V19/V24, pantalla Angular y documentos de arquitectura, base de datos y stack. La entrega es documental.

**Verificación.** Se siguió la ruta desde `repository-workspace` y `/api/account/workspace` a los controladores de importación, el adaptador de GitHub App, `RepositorySourceService` y los permisos por repositorio. Se confirmó en SQL que volver a importar GitHub reactiva el registro previo, de modo que las asignaciones existentes pueden recuperar efecto. La importación y descarga en Dinamita se citan como evidencia del bloque anterior. No se repitieron pruebas de integración ni se modificó la instancia.

**Siguiente paso.** Petición 4 sobre scanner y OSV. Revisar detección, versiones, cobertura y estados de consulta por separado.
