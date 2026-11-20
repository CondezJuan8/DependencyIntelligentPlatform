# Dependency Intelligence — Stack tecnológico oficial propuesto

### Bloque 51 — mapa de dependencias por capas

El grafo de Inventario ahora distribuye proyecto, directas y transitivas de izquierda a derecha por profundidad. Angular consolida ocurrencias con la misma identidad de paquete, versión y ecosistema en una tarjeta y conserva rutas/ámbitos para el detalle; las relaciones compartidas convergen en ese nodo. Las tarjetas muestran nombre/versión, los bordes identifican tipo, y búsqueda resalta coincidencias con su ruta de padres. El panel separa dependencias declaradas y paquetes que utilizan el nodo. El listado accesible mantiene todas las entradas; el canvas limita a 1.000 nodos por respuesta, prioriza niveles cercanos y enfoca búsquedas/selecciones grandes. Cytoscape usa layout preset y cálculo BFS O(V+E); no se añadió biblioteca ni se cambió API/base de datos. La revisión de tipos de TypeScript (44 archivos) y el parseo del template pasan; build completa y revisión visual en Compose quedan pendientes porque Node/npm CLI y el host de navegador no están disponibles. Ver [diseño](DISENO_FRONTEND.md), [stack](STACK_TECNOLOGICO_DEPENDENCY_INTELLIGENCE.md), [pendientes](PENDIENTES.md) y [bitácora](BITACORA_TRABAJO.md).
### Bloque 48 — distribución y lectura del grafo

Cytoscape.js 3.35 usa posiciones radiales controladas por Angular: raíz del repositorio al centro; directas y transitivas en anillos por nivel. Los nombres del canvas se orientan hacia fuera y se reducen para evitar cruces; detalle/lista conservan la identidad completa. No se agrega paquete. Build y revisión visual en Compose: ver [bitácora](BITACORA_TRABAJO.md) y [diseño frontend](DISENO_FRONTEND.md).

### Bloque 45 — grafo inicial

Se reutiliza Cytoscape.js 3.35 ya presente en Angular para navegar nodos/relaciones; no se añade paquete npm. El backend usa Java 21, Application ports/use case y lectura JDBC de PostgreSQL. `package-lock` npm v2/v3 aporta paths instalados y relaciones resolubles; el parser POM existente solo respalda dependencias directas con versión explícita. La tabla `dependency_edges` guarda las aristas observadas del scan. El grafo se consulta con autorización `repository.read`, organiza una fuente por cada último scan completado y deja visibles SHA-256/commit. Los colores del canvas se adaptan a los tokens CSS de tema. Compilación frontend y backend y prueba focal del parser registradas en la [bitácora](BITACORA_TRABAJO.md). Límite pendiente: resolver Maven transitivo sin inferir paquetes.

**Versión:** 1.0  

### Bloque 44 — evidencia, sincronización de GitHub y favicon

Se reutilizan ZIP/`ZipInputStream`, límites de lectura, SHA-256 y snapshots local/GitHub existentes en Java 21. Los puertos y el caso de uso viven en Application; Infrastructure adapta almacenamiento, GitHub REST y HTTP. Angular/TypeScript/SCSS muestra coincidencias por advisory. No se añadieron librerías, migraciones ni paquetes npm. La gestión usa HTTP DELETE/CSRF y los endpoints de GitHub App existentes; la sincronización se ejecuta al cargar o solicitarla, sin webhook. `/brand-mark.svg` reemplaza el favicon anterior.

### Bloque 43 — Dashboard de seguridad y legibilidad

El frontend Angular existente consulta snapshots OSV por repositorio junto con los snapshots Maven y npm; presenta categorías en SCSS reutilizando los tokens semánticos del tema. Inventario muestra guías de explicación deterministas para dos advisories Jackson conocidos, sus versiones corregidas y el texto original en secciones desplegables. RxJS y TypeScript existentes; ninguna dependencia nueva, llamada a Gemini ni servicio backend adicional. Build y disponibilidad del frontend se registran en la bitácora.

Actualización del bloque 34 (19/11/2026): la fuente local/GitHub usa Java 21 `HttpClient`, ZIP, SHA-256 y almacenamiento privado temporal, junto con GitHub App REST, Spring MVC/JDBC, PostgreSQL y Angular existentes. No se agregó dependencia de producto ni migración. La App tiene dos repositorios autorizados en GitHub; consentimiento OAuth e importación a la plataforma siguen pendientes. El ZIP todavía no se extrae ni analiza.

Actualización del bloque 20 (18/11/2026): la aprobación/rechazo inmediato reutiliza Java/Application, Spring MVC/Security/TransactionTemplate, JDBC/PostgreSQL/Flyway V15, Angular/HttpClient y SMTP/Mailpit. No se añadió dependencia de producto. Ver [bitácora](BITACORA_TRABAJO.md) y [flujo de correo](VERIFICACION_Y_RECUPERACION.md).
**Fecha:** 13 de noviembre de 2026  
**Estado:** Propuesta consolidada para aprobación del equipo  

### Actualización del bloque 42 — consulta OSV

Spring `RestClient` con JDK `HttpClient`, `POST /v1/querybatch`, lectura de avisos OSV y `ObjectMapper`; Java `MessageDigest` SHA-256; Application/Domain Java puros; persistencia JDBC/PostgreSQL con Flyway V22; Angular 22/TypeScript/SCSS para resultados. La configuración OSV no requiere credenciales y los timeouts, lote y antigüedad máxima son configurables. No se agregó dependencia de producto. Resultado del build, pruebas mínimas y estado de migración en la [bitácora](BITACORA_TRABAJO.md) y [documento OSV](OSV_VULNERABILIDADES.md).

### Actualización del bloque 41 - Inventario visual unificado

Angular 22, TypeScript, señales, RxJS `forkJoin`, HTML semántico y SCSS con los tokens existentes. La UI reutiliza las APIs Maven/npm, agrupa resultados por ecosistema/nombre/versión y muestra procedencia de los snapshots. No se añadió paquete npm, API nueva ni migración. La verificación de build queda registrada en la bitácora.
**Base documental:** `DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md` y `Requerimientos.md`

---

## 1. Propósito

Este documento consolida una propuesta de stack tecnológico para Dependency Intelligence, integrando:

- La arquitectura general del producto SaaS B2B.
- Los requisitos funcionales y no funcionales recopilados.
- El flujo de análisis de repositorios, generación de SBOM y detección de vulnerabilidades.
- El módulo de documentación técnica e inteligencia artificial basado en RAG.
- Las restricciones del MVP y la evolución hacia una plataforma empresarial.

El objetivo es definir tecnologías coherentes entre sí, evitar dependencias incompatibles y separar lo necesario para el MVP de las capacidades que pueden incorporarse posteriormente.

> **Estado de decisión:** este documento es una recomendación técnica derivada de los dos archivos. No implica que las tecnologías estén instaladas, implementadas o probadas. Las decisiones marcadas como pendientes deben aprobarse antes de considerarlas definitivas.

### Actualización de implementación — 14 de noviembre de 2026

Bloque 24, 18 de noviembre de 2026. Permisos directos por miembro/repositorio mediante migración V17, JDBC, FORCE RLS y Spring MVC existente; UI Angular muestra y guarda grants desde la edición de miembro. Autorización suma grants de equipo o directos y siempre respeta el rol. Sin dependencias nuevas; build/test-compile correctos, falta IT en PostgreSQL.

Bloque 23, 18 de noviembre de 2026. V16 expone gestión de repositorio scoped a miembros vía rol base y grant de equipo/repositorio, validada en el adaptador de autorización por recurso. No añade dependencia; se verifica con PostgreSQL antes de activar en Compose.

Bloque GitHub App, 18 de noviembre de 2026. Integración de repositorios con Spring `RestClient`, JCA para JWT/RSA y PKCE, Angular/RxJS existentes, JDBC y estado efímero en memoria. No se añade dependencia ni migración. Permisos de repositorio Contents/Metadata read-only. Instalación/listado/importación de metadatos implementados; faltan credenciales/consentimiento real, descarga de archivos/commits, escáner, webhooks y revisión física en dispositivos. [Guía operativa](GITHUB_REPOSITORIOS.md).

Bloque 13, 16 de noviembre de 2026. Rediseño con Angular/Material/Forms/CDK A11y/RxJS existentes, SVG nativo, Sass/tokens y @defer. Sin nueva dependencia ni fuente/imagen externa. AuthFrame, shell, sidebar/topbar, dashboard y métricas separados; tabla/pestañas/diálogos de organización. Siete comprobaciones directas de navegación y build de producción correctos, 406,37 kB iniciales sin ampliar presupuesto. Revisión visual conjunta pendiente; [criterios de diseño](DISENO_FRONTEND.md).

Bloque 11, 15 de noviembre de 2026. Invitaciones, membresías/roles, equipos y permisos por repositorio con puertos/casos de uso Java puro y adaptadores Spring JDBC/JavaMailSender existentes. Ninguna dependencia nueva. SMTP Mailpit real y PostgreSQL temporal con rol runtime limitado: 21 casos distintos pertinentes correctos. Angular usa Forms/Material/RxJS existentes y @defer para separar pantallas; build inicial 381,28 kB, bajo presupuesto de 500 kB. No cambia el esquema V1–V13. Configuración/contratos y límites de servidor en [INVITACIONES_EQUIPOS_PERMISOS](INVITACIONES_EQUIPOS_PERMISOS.md).

Bloque 09, 15 de noviembre de 2026. Nueva dependencia `spring-boot-starter-oauth2-client`, versión gestionada por el BOM existente, solo en Infrastructure. Google OIDC/GitHub OAuth con PKCE S256 y reglas de vinculación en Application Java puro. Angular Material/RxJS existentes para acceso y métodos conectados. Proveedor HTTP/JWT local de pruebas con HttpServer del JDK y Nimbus transitivo, sin SDK externo nuevo. Configuración real de clientes pendiente; ambos deshabilitados por defecto. [Identidad externa](IDENTIDAD_GOOGLE_GITHUB.md).

Bloque 08, 15 de noviembre de 2026. Paleta del usuario centralizada mediante tokens CSS/Sass y overrides oficiales de Angular Material. Aplicada a pantallas actuales con variantes de contraste para texto y estados. Se documentan hitos de revisión interactiva con el usuario. [Diseño frontend](DISENO_FRONTEND.md).

Bloque 07, 15 de noviembre de 2026. Incorporado `spring-boot-starter-mail` en Infrastructure con versión gestionada por Spring Boot. Mailpit v1.33.1 como bandeja SMTP local, verificada mediante binario oficial temporal y configurada en Compose. Tokens con SecureRandom/SHA-256 de Java estándar. V13 añade versión de credenciales e índice de solicitudes. Angular permite solicitar y confirmar enlaces. La cola de recuperación y las sesiones actuales residen en memoria. Resultados y límites en [VERIFICACION_Y_RECUPERACION](VERIFICACION_Y_RECUPERACION.md).

Actualización del bloque 06, 15 de noviembre de 2026. Acceso local en Spring Security y Angular con registro/login/logout, cookie HttpOnly/SameSite=Lax, CSRF enmascarado y sesión de servidor en memoria. PBKDF2-HMAC-SHA256 con 600.000 iteraciones y salt aleatorio mediante la biblioteca existente. Roles/equipos autorizan repositorios/scans en transacción tenant. V12 añade descubrimiento de organizaciones propias. Detalles operativos y límites de verificación visual en [ACCESO_LOCAL](ACCESO_LOCAL.md). Verificación/recuperación y proveedores externos son el siguiente trabajo.

Esta nota complementa la propuesta histórica sin convertir todas sus opciones en decisiones aprobadas. El objetivo vigente es el producto completo listo para usar, por fases, primero local y después servidor. El plan anterior de demo/50 horas deja de limitar el alcance o la aceptación. Decisiones y pendientes: `DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md`; diseño database vigente: `CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md`.

- Acceso confirmado: correo/contraseña, Google y GitHub; equipos y permisos por repositorio en el primer piloto. La identidad GitHub no sustituye la autorización/instalación para acceder a repositorios.
- Ecosistemas de análisis activos en el bloque 42: Java/Maven explícito y JavaScript/npm package-lock v2/v3; sus versiones exactas se evalúan en OSV. Se mantiene pendiente ampliar la resolución y cobertura.
- IA de la plataforma: Gemini. Embeddings: Ollama. Modelo, versión, dimensión y límites concretos se verifican al implementar los adaptadores; no se incorporan librerías por anticipado.
- Codex no se implementa ni se agenda como proveedor del producto hasta nueva instrucción explícita del usuario. No se asume uso de SDK, CLI, API ni suscripción como integración futura.
- Originales documentales en almacenamiento privado, descargables/reindexables con permisos y retención/eliminación configurables; temporales eliminados al terminar o expirar. No se contrató almacenamiento.
- Pruebas de cambios aisladas y propuesta de PR aprobable en el primer piloto. Notificaciones iniciales in-app; correo de notificaciones después, separado del correo de verificación/recuperación.
- Núcleo database V1-V24 con 128 tablas, 91 con RLS forzado y 227 FK. V18 configura tema/idioma; V19 guarda repositorios locales; V22/V23 conservan evaluaciones OSV tenant-scoped append-only; V24 permite metadatos de hasta 50.000 archivos locales. Catálogo y diagramas no cambian de relaciones en V24.
- Importación local usa `MultipartFile`, Java ZIP/I/O y volumen Docker privado `repository_data`; 100 MiB totales/50.000 archivos, con límites multipart/proxy alineados (hasta 200 MiB de cuerpo para overhead), sin nueva dependencia. Maven/npm se analizan en petición; workers aislados con progreso/reintentos siguen pendientes.

- Backend actual: Java con destino 21, Spring Boot 3.5.16 y módulos Maven Domain/Application/Infrastructure. Dominio y aplicación permanecen sin frameworks.
- Lectura de SBOM: adaptador Jackson sobre el puerto propio `SbomParser`. Jackson ya llega transitivamente con Spring Web; no se incorporó una dependencia nueva. El subconjunto inicial es CycloneDX JSON 1.6, bibliotecas Maven planas y coordenadas/PURL explícitos coincidentes, con límites de 5 MiB y 500 componentes.
- Verificación del parser: JUnit 5 y ArchUnit. El bloque F04 añade comprobaciones focalizadas de importación y migraciones en PostgreSQL real; detalle en la bitácora. Se compila con JDK local 25 y destino Java 21.
- F04 usa JDBC parametrizado y `TenantTransactions` con el gestor existente para importar snapshot, procedencia y ocurrencias atómicamente bajo contexto tenant local. V4–V11 separan paquete/versión/identificadores y renombran la procedencia a `sbom_documents`; V1–V3 permanecen intactas. SHA-256 usa Java estándar; el importador aún no almacena el original. No se añadió una biblioteca nueva.

- Flyway tiene credenciales de migración separadas del datasource `dependency_app`, no propietario. Roles capability, grants mínimos, FORCE RLS y guard de privilegios en Infrastructure. Verificados con PostgreSQL nativo temporal 18.6, Maven 3.9.11 y JDK local 25 con destino 21: 31 pruebas distintas pertinentes. Docker/PostgreSQL 17.7 no ejecutados en este bloque. Python estándar genera diccionario/Excalidraw/SVG desde catálogo; Pillow existente genera PNG, sin dependencia de runtime del producto.
- No se han incorporado microservicios, RabbitMQ, pgvector, RAG ni proveedores de IA.
- cdxgen, instalación/acceso a repositorios GitHub y OSV siguen pendientes. El login GitHub no completa esas funciones. El parser implementado no demuestra cobertura del scanner ni consulta de vulnerabilidades. El soporte de Maven para este ejemplo no aprueba por sí solo la cobertura de todos los ecosistemas del piloto.

El detalle vigente de responsabilidades está en `ARQUITECTURA_BACKEND.md`; los resultados y límites de cada bloque están en `BITACORA_TRABAJO.md`.

---

## 2. Criterios para seleccionar el stack

El stack debe cumplir estos criterios:

1. **Coherencia tecnológica:** las tecnologías deben integrarse de forma natural, especialmente en el ecosistema Java.
2. **Viabilidad del MVP:** evitar infraestructura que retrase la demostración del flujo principal.
3. **Evolución empresarial:** permitir multi-tenancy, auditoría, seguridad, escalabilidad y trazabilidad.
4. **Separación de responsabilidades:** el dominio no debe depender directamente de GitHub, OSV, cdxgen, Gemini/Ollama ni de detalles de persistencia.
5. **Evidencia y reproducibilidad:** cada análisis debe poder relacionarse con un repositorio, commit, versión de scanner, fecha y fuentes utilizadas.
6. **Seguridad:** el contenido de repositorios y documentos debe tratarse como no confiable.
7. **Uso correcto de IA:** los datos operativos se consultan de forma determinista; los embeddings se reservan para recuperación semántica documental.

---

## 3. Stack tecnológico propuesto

### 3.1 Resumen general

| Capa | Tecnología | Responsabilidad | Prioridad |
|---|---|---|---|
| Frontend | Angular + TypeScript | Aplicación web SaaS | MVP |
| UI | Angular Material o PrimeNG | Componentes de interfaz y tablas | MVP |
| Visualización de grafos | Cytoscape.js | Relaciones entre repositorios, paquetes y versiones | MVP |
| Backend | Java + Spring Boot | API, reglas de negocio e integraciones | MVP |
| Seguridad backend | Spring Security | Autenticación, autorización y protección de endpoints | MVP |
| Persistencia | Spring Data JPA + Hibernate | Persistencia relacional | MVP |
| Base de datos | PostgreSQL | Datos operativos, históricos y metadatos | MVP |
| Migraciones | Flyway | Versionamiento del esquema | MVP |
| Vector search | pgvector | Embeddings y recuperación semántica | Fase 2 |
| Acceso vectorial | Spring AI o integración JDBC compatible con pgvector | Consultas vectoriales desde Java | Fase 2 |
| Integración de código | GitHub App + GitHub REST API | Acceso autorizado a repositorios y eventos | MVP |
| Generación SBOM | cdxgen | Detección de componentes | MVP |
| Formato SBOM | CycloneDX | Intercambio normalizado de inventarios | MVP |
| Vulnerabilidades | OSV API | Consulta de vulnerabilidades | MVP |
| Procesamiento documental | Apache Tika / Apache PDFBox | Extracción de texto y metadatos | Fase 2 |
| OCR | Tesseract OCR | Extracción de texto de páginas escaneadas | Fase 2 |
| Embeddings | Ollama, modelo por fijar | Representación vectorial de fragmentos | Fase documental |
| LLM | Gemini API, modelo por fijar | Generación de respuestas fundamentadas | Fase documental |
| Contenedores | Docker | Empaquetado y aislamiento | MVP |
| Proxy / servidor web | Nginx | Terminación HTTPS y publicación del frontend/API | Despliegue |
| CI/CD | GitHub Actions | Build, pruebas y despliegue automatizado | MVP |
| Pruebas backend | JUnit 5, Mockito y Testcontainers | Pruebas unitarias e integración | MVP |
| Contratos API | OpenAPI + springdoc-openapi | Documentación y contrato REST | MVP |
| Observabilidad | Spring Boot Actuator + Micrometer | Health checks y métricas | MVP básico |

### 3.2 Frontend

**Tecnologías:**
- Angular.
- TypeScript.
- Angular Material o PrimeNG.
- Cytoscape.js para el grafo.
- Cliente HTTP de Angular para consumir la API REST.

**Responsabilidades:**
- Autenticación y navegación.
- Dashboard organizacional.
- Gestión de repositorios.
- Inventario de dependencias.
- Consulta de vulnerabilidades.
- Historial de análisis.
- Visualización del grafo.
- Centro documental y asistente de IA en una fase posterior.
- Reportes y exportaciones.

**Recomendación:** escoger una sola biblioteca principal de componentes visuales. Angular Material y PrimeNG son alternativas; no se recomienda introducir ambas sin una necesidad concreta. La selección final debe considerar la calidad de tablas, filtros, formularios, accesibilidad y personalización visual.

Cytoscape.js debe encargarse de la representación y exploración del grafo. La API del backend debe entregar nodos y relaciones; el frontend no debe inventar relaciones ni inferir impactos que el backend no haya identificado.

### 3.3 Backend

**Tecnologías:**
- Java.
- Spring Boot.
- Spring Web.
- Spring Security.
- Spring Data JPA.
- Hibernate.
- Bean Validation.
- Flyway.
- springdoc-openapi.

**Responsabilidades:**
- Exponer la API REST.
- Aplicar reglas de negocio.
- Gestionar usuarios, organizaciones, roles y permisos.
- Coordinar integraciones.
- Crear y consultar análisis.
- Persistir SBOM, componentes, relaciones y vulnerabilidades.
- Aplicar aislamiento multi-tenant.
- Gestionar documentación y consultas RAG cuando se incorpore ese módulo.
- Generar reportes y auditoría.

**Versión de Java:** utilizar una versión LTS compatible con la versión de Spring Boot elegida. Java 21 es una base conservadora razonable; confirmar compatibilidad y soporte al fijar las versiones exactas del proyecto.

**Estilo arquitectónico:** monolito modular con arquitectura hexagonal y principios DDD. No iniciar con microservicios: la complejidad operativa no se justifica para el MVP.

### 3.4 Base de datos relacional

**Tecnología: PostgreSQL.**

Será la fuente principal de verdad para los datos operativos.

Almacenará, entre otros:

- Organizaciones.
- Usuarios y membresías.
- Roles y permisos.
- Integraciones.
- Repositorios.
- Análisis y snapshots.
- Artefactos SBOM y referencias a ellos.
- Componentes y versiones.
- Relaciones entre dependencias.
- Vulnerabilidades y asociaciones.
- Documentos y metadatos.
- Fragmentos documentales.
- Resultados de análisis de impacto.
- Reportes.
- Notificaciones.
- Eventos de auditoría.

**Acceso:** Spring Data JPA + Hibernate para persistencia relacional. Para consultas especializadas, agregaciones o rendimiento, se podrán utilizar consultas SQL específicas o JDBC.

**Migraciones:** Flyway. Los cambios de esquema deben versionarse; no depender de la generación automática de tablas en entornos compartidos o productivos.

**Regla de diseño:** diferenciar el catálogo lógico de componentes de la presencia concreta de una versión en un repositorio, commit y snapshot.

### 3.5 Búsqueda vectorial y RAG

**Tecnología: PostgreSQL + pgvector.**

No se propone una base vectorial separada para el MVP. pgvector permite mantener datos relacionales y vectores en el mismo sistema, con filtros de organización y permisos.

Se utilizará para:
- Indexar documentación técnica.
- Recuperar fragmentos semánticamente relevantes.
- Consultar README, ADR, manuales, PDF, DOCX, Markdown, TXT y documentación compatible.
- Proporcionar contexto documental al asistente de IA.

**Integración Java:** utilizar Spring AI con su integración compatible con pgvector, si la versión elegida satisface los requisitos; alternativamente, usar JDBC y SQL nativo con pgvector. No utilizar `Pgvector.EntityFrameworkCore`: pertenece al ecosistema .NET/Entity Framework Core y no corresponde a un backend Java.

**Búsqueda híbrida:**
1. Recuperación vectorial con pgvector.
2. Búsqueda textual con las capacidades de full-text search de PostgreSQL.
3. Combinación y reordenamiento de resultados.
4. Aplicación obligatoria de filtros de tenant, permisos y asociaciones documentales.

Los embeddings no reemplazan las consultas relacionales para determinar versiones, inventarios, vulnerabilidades o relaciones exactas.

### 3.6 Integración con GitHub

**Tecnología: GitHub App + GitHub REST API.**

Responsabilidades:
- Autorizar la instalación en una cuenta u organización.
- Consultar repositorios permitidos.
- Recuperar metadatos, commits y archivos requeridos.
- Recibir webhooks.
- Iniciar análisis ante eventos configurados.
- Permitir revocar la integración.

**Seguridad:**
- Solicitar permisos mínimos.
- Validar firmas de webhooks.
- Proteger installation tokens y secretos.
- No almacenar tokens en texto plano.
- Respetar rate limits.
- No entregar credenciales innecesarias al scanner.

Se recomienda GitHub App frente a tokens personales de larga duración para la integración organizacional.

### 3.7 Análisis de dependencias y SBOM

**Tecnologías:**
- cdxgen como herramienta inicial de generación de SBOM.
- CycloneDX como formato inicial de intercambio.
- PURL cuando corresponda para identificar paquetes.

Flujo:
1. Obtener el commit exacto.
2. Recuperar los archivos necesarios.
3. Preparar el entorno de análisis.
4. Ejecutar cdxgen en un contenedor aislado.
5. Generar el SBOM CycloneDX.
6. Parsear y validar el documento.
7. Normalizar componentes, versiones y relaciones.
8. Persistir los resultados asociados al snapshot.

La cobertura debe comprobarse por ecosistema y tipo de proyecto. No prometer detección completa de transitivas si no se dispone de lockfiles o de información suficiente del gestor de paquetes.

**Ecosistemas iniciales candidatos:** Maven y npm. Esta selección aparece como recomendación en el contexto maestro, pero debe aprobarse y probarse con repositorios reales.

### 3.8 Inteligencia de vulnerabilidades

**Tecnología: OSV API.**

Responsabilidades:
- Consultar vulnerabilidades por paquete, ecosistema y versión.
- Asociar hallazgos con componentes y repositorios.
- Conservar identificadores, rangos afectados, severidad y versiones corregidas cuando la fuente los proporcione.
- Normalizar resultados.
- Evitar duplicados.
- Conservar la procedencia y fecha de consulta.

La integración debe estar detrás de un adaptador. El dominio no debe depender del formato JSON de OSV.

### 3.9 Procesamiento documental

Para la fase de Documentation Intelligence:

| Función | Tecnología propuesta |
|---|---|
| Extracción de contenido y metadatos | Apache Tika |
| Lectura y operaciones específicas de PDF | Apache PDFBox |
| OCR de páginas escaneadas | Tesseract OCR |
| Segmentación | Servicio Java de chunking |
| Embeddings | Ollama mediante adaptador propio |
| Persistencia vectorial | PostgreSQL + pgvector |
| Recuperación | Búsqueda híbrida |
| Generación | Gemini API mediante una capa de proveedor |

Tika y PDFBox pueden complementarse, pero no es necesario incorporar ambos indiscriminadamente: seleccionar el flujo según los formatos y necesidades de extracción probados.

El OCR solo debe ejecutarse cuando el documento lo requiera. El procesamiento debe ser asíncrono y conservar metadatos de página, sección y orden para permitir citas.

### 3.10 Infraestructura y despliegue

**Tecnologías propuestas:**
- Docker para empaquetar backend y servicios.
- Docker Compose para el entorno inicial.
- Nginx como reverse proxy.
- HTTPS.
- GitHub Actions para CI/CD.
- PostgreSQL como servicio persistente.

Para el scanner, utilizar un contenedor separado y restringido. No montar el Docker socket dentro del contenedor del scanner, no ejecutar con privilegios elevados y limitar CPU, memoria, red, almacenamiento y duración.

Para producción, los archivos temporales y documentos deben tener una política explícita de almacenamiento, cifrado, acceso y eliminación. El almacenamiento de objetos compatible con S3 puede evaluarse cuando se necesite; no es requisito introducirlo en el primer MVP.

---

## 4. Arquitectura lógica propuesta

```text
┌─────────────────────────────────────────────────────────────┐
│                     Angular + TypeScript                    │
│ Dashboard | Repositories | Inventory | Vulnerabilities      │
│ Graph | History | Documentation | AI Assistant | Reports    │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTPS / REST
┌──────────────────────────────▼──────────────────────────────┐
│                       Spring Boot API                       │
│                                                              │
│ API / Presentation                                            │
│ Application / Use Cases                                       │
│ Domain                                                        │
│ Infrastructure / Adapters                                    │
│                                                              │
│ Security | Multi-tenancy | Audit | Integrations               │
└──────────────┬───────────────────────┬───────────────────────┘
               │                       │
       ┌───────▼────────┐      ┌───────▼─────────────────────┐
       │ PostgreSQL     │      │ Asynchronous Processing    │
       │                │      │                             │
       │ Relational     │      │ Scan orchestration          │
       │ Data           │      │ Document indexing           │
       │ Scan History   │      │ Vulnerability lookups       │
       │ Audit          │      │ Report generation           │
       │ pgvector       │      └──────────────┬──────────────┘
       └────────────────┘                     │
                                  ┌────────────▼─────────────┐
                                  │ Isolated Scanner         │
                                  │ Docker + cdxgen          │
                                  │ CycloneDX SBOM           │
                                  └────────────┬─────────────┘
                                               │
                  ┌────────────────────────────┼────────────────────┐
                  │                            │                    │
          ┌───────▼────────┐          ┌─────────▼──────┐    ┌────────▼────────┐
          │ GitHub App/API │          │ OSV API        │    │ Gemini API      │
          │ Repositories   │          │ Vulnerabilities│    │ Embeddings/LLM  │
          │ Commits/Webhooks│         │                │    │ (Phase 2)       │
          └────────────────┘          └────────────────┘    └─────────────────┘
```

El diagrama representa límites lógicos, no obliga a desplegar cada módulo como un servicio independiente.

---

## 5. Arquitectura interna del backend

Se recomienda organizar el backend por módulos de negocio y mantener separación hexagonal dentro de cada módulo.

### 5.1 Capas

| Capa | Responsabilidad | Puede depender de |
|---|---|---|
| Domain | Entidades, value objects, reglas y políticas | No depende de frameworks ni proveedores |
| Application | Casos de uso, puertos y coordinación | Domain |
| Infrastructure | Persistencia, GitHub, OSV, cdxgen, Gemini/Ollama y filesystem | Application y Domain mediante contratos |
| API / Presentation | Controladores REST, DTOs y validación de entrada | Application |
| Workers | Procesos asíncronos y consumidores de trabajos | Application |

### 5.2 Módulos sugeridos

- Identity & Organization
- Access Control
- GitHub Integration
- Repository Management
- Scan Management
- SBOM & Components
- Dependency Relationships
- Vulnerability Intelligence
- Dependency Graph
- Scan History
- Documentation
- AI Assistant
- Change Impact
- Reporting
- Notifications
- Audit

No es necesario implementar todos los módulos en el MVP. La separación debe facilitar la evolución sin generar abstracciones vacías.

### 5.3 Adaptadores externos

Definir interfaces propias para proveedores externos, por ejemplo:

- `SourceCodeProvider`
- `VulnerabilityProvider`
- `SbomGenerator`
- `DocumentTextExtractor`
- `EmbeddingProvider`
- `LanguageModelProvider`
- `ReportStorage`

Las implementaciones concretas se ubican en Infrastructure. Esto evita acoplar el dominio a GitHub, OSV, cdxgen ni Gemini/Ollama.

---

## 6. Procesamiento asíncrono

Los análisis de repositorios y la indexación documental no deben ejecutarse dentro de una petición HTTP larga.

### Propuesta para el MVP

- Crear un registro de trabajo persistente en PostgreSQL.
- Devolver inmediatamente un identificador de análisis.
- Procesar el trabajo en un worker separado o proceso de aplicación controlado.
- Actualizar estados y resultados en la base de datos.
- Implementar reintentos limitados, idempotencia y manejo de errores.

Esta opción reduce infraestructura adicional durante las primeras fases del producto.

### Evolución

Si crece el volumen o se requiere mayor desacoplamiento, evaluar RabbitMQ como broker de mensajes. No es obligatorio añadirlo antes de demostrar la necesidad.

Los trabajos deben tener estados explícitos, por ejemplo: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` y, si aplica, `CANCELLED`.

---

## 7. Integración RAG con Dependency Intelligence

El segundo documento describe una arquitectura donde los PDF originales permanecen en el equipo del cliente y el servidor conserva fragmentos, embeddings y metadatos.

Esta propuesta puede integrarse como una modalidad de gestión documental, pero requiere explicar sus límites.

### 7.1 Flujo de indexación propuesto

1. El usuario selecciona un PDF desde el navegador.
2. El archivo se transmite por HTTPS.
3. El backend valida tamaño, tipo, permisos y seguridad del archivo.
4. El archivo se almacena temporalmente en el servidor.
5. Un trabajo asíncrono extrae texto y metadatos.
6. Se aplica OCR a las páginas escaneadas cuando sea necesario.
7. El texto se divide en fragmentos con tamaño y solapamiento configurables.
8. Se conservan página, sección, orden, documento y organización.
9. Se generan embeddings mediante el modelo Ollama configurado y verificado.
10. Se guardan texto, vectores y metadatos en PostgreSQL + pgvector.
11. Se verifica la indexación.
12. Se elimina la copia temporal según la política de retención acordada.

### 7.2 Flujo de consulta

1. El usuario formula una pregunta.
2. Se valida identidad, organización y permisos.
3. Se genera el embedding de la pregunta con el mismo modelo del índice correspondiente.
4. PostgreSQL recupera fragmentos relevantes con filtros de tenant y autorización.
5. Se combina búsqueda vectorial y textual cuando convenga.
6. Se seleccionan evidencias.
7. El LLM genera una respuesta basada en los fragmentos recuperados.
8. La respuesta presenta referencias al documento y página cuando los metadatos lo permitan.

### 7.3 Originales privados, temporales y retención

- El servidor puede consultar los fragmentos indexados aunque el computador del usuario esté apagado.
- El original retenido permite descarga autorizada y reindexación. Si se elimina según política, no se puede recuperar a partir del hash.
- Las citas por página dependen de la calidad de extracción y de los metadatos guardados.
- Tablas, imágenes, firmas y sellos pueden requerir el documento original.
- Un cambio en OCR/extracción permite reindexar el original retenido; si se eliminó, puede requerir nueva carga.
- Guardar un hash permite identificar el archivo, pero no sustituye su contenido.

**Decisión vigente delegada:** conservar originales en almacenamiento privado y configurar su retención/eliminación por organización. Eliminar temporales al terminar/expirar el trabajo. Quedan por concretar periodos y excepciones, no la distinción entre original y copia temporal.

---

## 8. Seguridad y aislamiento

La seguridad debe formar parte de la arquitectura desde el MVP.

### 8.1 Aplicación y multi-tenancy

- Aplicar autorización en cada recurso.
- Asociar los registros operativos a una organización.
- Filtrar consultas por tenant.
- Evitar que identificadores manipulados permitan consultar datos de otra organización.
- Validar permisos antes de recuperar documentos para RAG.
- Registrar acciones relevantes.
- No registrar tokens, credenciales ni secretos en logs.

### 8.2 Scanner

- Ejecutar cdxgen en contenedor aislado.
- Fijar y controlar las imágenes utilizadas.
- No montar Docker socket.
- Evitar privilegios elevados.
- Limitar CPU, memoria, almacenamiento y tiempo.
- Restringir red saliente.
- Montar el repositorio como solo lectura cuando sea viable.
- No entregar credenciales innecesarias.
- Eliminar archivos temporales.
- Validar rutas y tamaños.
- Registrar herramienta y versión.

### 8.3 Documentos e IA

- Validar archivos y límites de tamaño.
- Procesar documentos de forma aislada y controlada.
- Tratar el texto recuperado como contenido no confiable.
- Protegerse contra prompt injection en documentos.
- No permitir que el LLM ejecute SQL libre.
- Utilizar consultas parametrizadas, servicios de aplicación o herramientas permitidas.
- Aplicar permisos antes de recuperar fragmentos.
- Evitar filtraciones entre organizaciones.
- Mostrar fuentes y distinguir hechos de inferencias.

---

## 9. Stack por fases

### Fase 1 — MVP

**Objetivo:** demostrar el flujo completo desde un repositorio conectado hasta un inventario de dependencias y vulnerabilidades.

Incluye:

- Angular + TypeScript.
- Spring Boot + Java.
- Spring Security.
- PostgreSQL.
- Spring Data JPA + Hibernate.
- Flyway.
- GitHub App.
- cdxgen.
- CycloneDX.
- OSV API.
- Docker para scanner aislado.
- Cytoscape.js para grafo inicial.
- OpenAPI / Swagger.
- JUnit y pruebas de integración.
- CI/CD básico.

Flujo vertical:

**Conectar GitHub → seleccionar repositorio → analizar commit → generar SBOM → persistir componentes y relaciones → consultar OSV → mostrar inventario y vulnerabilidades.**

El procesamiento documental avanzado, RAG y el asistente de IA no deben bloquear la entrega de este flujo.

### Fase 2 — Documentation Intelligence

- Centro documental.
- Apache Tika / PDFBox.
- Tesseract OCR.
- Procesamiento asíncrono.
- Embeddings mediante Ollama.
- PostgreSQL + pgvector.
- Búsqueda híbrida.
- RAG con citas y referencias.
- Asistente contextual.
- Gestión de documentos potencialmente desactualizados.

### Fase 3 — Change Intelligence

- Comparación de versiones.
- Análisis de breaking changes documentados.
- Change Impact Analysis.
- Evidencias y nivel de confianza.
- Pruebas sugeridas.
- Alertas de cambios.
- Simulación controlada, inicialmente sin modificar repositorios.

### Fase 4 — Enterprise Intelligence

- Reportes programados.
- Auditoría avanzada.
- Integraciones adicionales.
- Políticas organizacionales.
- SSO mediante OIDC/SAML según demanda.
- GitHub Enterprise Server si se valida su necesidad.
- Retención y residencia de datos.
- Escalado de workers y broker de mensajes.
- PR asistido con aprobación humana.

---

## 10. Tecnologías que no se deben introducir sin justificación

| Tecnología / enfoque | Recomendación |
|---|---|
| `Pgvector.EntityFrameworkCore` | No usar: corresponde a .NET/EF Core, no a Java |
| Base vectorial independiente | No necesaria inicialmente; evaluar solo si pgvector deja de cubrir las necesidades |
| Microservicios | No para el MVP; comenzar con monolito modular |
| Kubernetes | No necesario para la primera entrega; evaluar cuando la operación lo justifique |
| RabbitMQ | Evolución posible; no obligatorio para el primer flujo |
| Entrenamiento de modelo propio | No recomendado inicialmente |
| LLM como fuente de vulnerabilidades | No; usar fuentes y lógica determinista |
| Embeddings para inventario/versiones | No; utilizar PostgreSQL relacional |
| Ejecución de scripts del repositorio | No sin controles y aislamiento explícitos |
| Simulación que modifica repositorios | Fuera del MVP inicial |

---

## 11. Decisiones pendientes de aprobación

Antes de congelar el stack, el equipo debe confirmar:

1. **Java:** versión LTS y versión compatible de Spring Boot.
2. **Frontend UI:** Angular Material o PrimeNG.
3. **Ecosistemas:** Maven y npm confirmados; validar cobertura real y resolver extensiones posteriores.
4. **Scanner:** cdxgen como herramienta inicial, validada con repositorios de prueba.
5. **Vulnerabilidades:** OSV API como fuente inicial.
6. **Procesamiento asíncrono:** tabla de trabajos y worker inicial; evaluar RabbitMQ posteriormente.
7. **Autenticación:** métodos confirmados, correo/contraseña y Google/GitHub; concretar configuración y vinculación segura.
8. **RAG:** confirmar si se implementará en una segunda fase y qué documentos podrán conservarse temporalmente.
9. **PDF original:** confirmar política de eliminación, retención y posibilidad de reindexación.
10. **Modelos de IA:** proveedores confirmados, Ollama para embeddings y Gemini para generación; faltan modelo/versiones, dimensión y límites operativos.
11. **Despliegue:** definir VPS, almacenamiento, backups, HTTPS y ambientes.
12. **Observabilidad:** definir métricas, logs, alertas y retención.

---

## 12. Riesgos técnicos que deben validarse con pruebas

| Riesgo | Validación necesaria |
|---|---|
| Cobertura incompleta de cdxgen | Probar Maven/npm, con y sin lockfiles |
| Dependencias transitivas no detectadas | Comparar SBOM con árboles resueltos por los gestores |
| Límites de OSV | Probar paquetes, ecosistemas, versiones, aliases y respuestas parciales |
| Rate limits de GitHub | Probar instalación, lectura, webhooks y reintentos |
| Aislamiento del scanner | Ejecutar repositorios de prueba malformados y potencialmente maliciosos |
| Aislamiento multi-tenant | Pruebas negativas de acceso cruzado |
| Calidad de extracción PDF | Probar PDFs digitales, escaneados, tablas y documentos mixtos |
| Calidad de RAG | Evaluar recuperación, citas, permisos y respuestas sin evidencia |
| Costo de IA | Medir tokens, documentos, fragmentos y consultas |
| Rendimiento de pgvector | Medir latencia con datos representativos y filtros por tenant |

---

## 13. Conclusión y recomendación

El stack recomendado para Dependency Intelligence es:

**Angular + TypeScript + Java + Spring Boot + PostgreSQL + Docker**, complementado con **GitHub App, cdxgen, CycloneDX, OSV y Cytoscape.js**.

Para la evolución documental:

**PostgreSQL + pgvector + adaptadores compatibles + Ollama embeddings + Gemini API + Apache Tika/PDFBox + Tesseract OCR**. Evaluar bibliotecas auxiliares al verificar compatibilidad, sin añadirlas por anticipado.

La decisión arquitectónica central es mantener un **monolito modular con arquitectura hexagonal**, una base relacional como fuente de verdad y procesos asíncronos aislados para análisis e indexación.

El MVP debe priorizar el flujo GitHub → SBOM → PostgreSQL → OSV → visualización. RAG, IA documental y análisis avanzado de cambios deben incorporarse después de estabilizar ese flujo.

Este enfoque permite demostrar valor temprano sin cerrar la puerta a una evolución empresarial.

---

## 14. Trazabilidad con los documentos de entrada

### Del contexto maestro

Se conservan como base:
- Angular + TypeScript.
- Java + Spring Boot.
- PostgreSQL.
- pgvector como evolución documental.
- GitHub App / GitHub API.
- cdxgen.
- CycloneDX.
- OSV API.
- Docker.
- Cytoscape.js.
- Arquitectura hexagonal, DDD y monolito modular.
- Separación entre MVP y fases posteriores.
- Requisitos de seguridad, multi-tenancy, trazabilidad y evidencia.

### Del documento RAG

Se incorporan:
- PDF original conservado en el equipo del cliente bajo la modalidad propuesta.
- Carga temporal por HTTPS.
- Procesamiento asíncrono.
- Extracción de texto y OCR.
- Fragmentación con metadatos de página y sección.
- La fuente original proponía OpenAI Embeddings; la decisión vigente del usuario es Ollama para embeddings y Gemini para generación.
- PostgreSQL + pgvector.
- Búsqueda híbrida.
- Recuperación filtrada por tenant y autorización.
- Respuestas con citas.
- Eliminación de la copia temporal según política.
- Limitaciones de no conservar el original.

### Ajustes y aclaraciones introducidos en esta consolidación

- Se corrige la incompatibilidad de `Pgvector.EntityFrameworkCore` con Java.
- Se propone Spring AI o JDBC compatible con pgvector.
- Se separa el stack del MVP del stack de fases futuras.
- Se recomienda un worker con trabajos persistidos antes de introducir un broker adicional.
- Se especifica que la búsqueda vectorial no sustituye la base relacional.
- Se mantiene el LLM fuera de las decisiones deterministas de vulnerabilidades.
- Se dejan explícitas las decisiones que requieren aprobación y validación práctica.


## 15. Ajuste de ejecución local

Por indicación del usuario, la estructura inicial se ejecuta exclusivamente en local con Docker Compose. No se configura hosting, despliegue público ni publicación automatizada. Nginx sirve Angular y redirige `/api` al backend dentro de la red de Compose. Los puertos se vinculan a `127.0.0.1`; PostgreSQL no se publica al host.

GitHub, OSV y la descarga inicial de imágenes y dependencias necesitan internet. Los webhooks de GitHub no son accesibles desde GitHub sin conectividad entrante: el flujo inicial deberá usar análisis manual. No se configura ningún túnel público.

La base técnica es Java 21, Spring Boot 3.5, Angular 22 y Angular Material. Las integraciones y el alcance funcional permanecen pendientes de implementación y validación. RAG, OCR, pgvector y proveedores de IA se reservan para fase 2.

## Estado al bloque 26 (18 de noviembre de 2026)

Se implementaron preferencias compartidas de tema e idioma por organizacion mediante V18, API y Angular. Base local en V18: 126 tablas de negocio, 89 con FORCE RLS y 223 FK. Ver `docs/database/implementation-report.md` y `docs/PENDIENTES.md`. Build frontend/backend correcto; la prueba focalizada de integracion sigue pendiente tras fallo DNS de Maven Central en el contenedor aislado.
