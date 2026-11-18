# CONTEXTO MAESTRO — DEPENDENCY INTELLIGENCE
## Documento de continuidad para migrar de cuenta de ChatGPT

**Fecha de elaboración:** 12 de noviembre de 2026  
**Propósito:** conservar el contexto, decisiones, ideas, requisitos, arquitectura, pantallas, investigación pendiente y próximos pasos conversados sobre el proyecto Dependency Intelligence, para continuar el trabajo desde otra cuenta de ChatGPT.

> Este documento consolida el contexto disponible de las conversaciones. Distingue lo que ya se propuso de lo que está decidido. Las propuestas no deben tratarse como decisiones aprobadas si el usuario todavía no las confirmó. Tampoco se deben interpretar ejemplos, datos de mockups o estimaciones como resultados reales.

## Decisiones vigentes y estado de continuidad — 15 de noviembre de 2026

Las secciones numeradas posteriores conservan el contexto histórico. Las decisiones de este apartado, confirmadas por el usuario, prevalecen sobre propuestas pendientes o límites de entregas anteriores. Referencias prioritarias: este contexto, `ARQUITECTURA_BACKEND.md`, `CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md` y `STACK_TECNOLOGICO_DEPENDENCY_INTELLIGENCE.md`.

Instrucción posterior al bloque 09: pendientes consolidados en [PENDIENTES.md](PENDIENTES.md), para implementarlos y validarlos juntos cuando el usuario lo indique. El orden propuesto más abajo queda como referencia; no inicia automáticamente el siguiente bloque.

Decisión vigente del flujo de admisión (18 de noviembre de 2026): las cuentas de responsable creadas por registro quedan guardadas y pueden volver a iniciar sesión mientras esperan revisión. El administrador de plataforma confirma el correo y aprueba la cuenta en una sola acción; hasta entonces el usuario permanece fuera del espacio de trabajo. Salir/cerrar la página no descarta la solicitud. La espera debe poder consultarse tras volver a iniciar sesión.

Bloque 19 (18 de noviembre de 2026): un alta directa pendiente envía un correo de aviso únicamente a los administradores activos con correo confirmado. El aviso incluye nombre/correo del solicitante y la URL de la plataforma, sin contraseña ni enlace de aprobación. La interfaz conserva «Esperando confirmación». En desarrollo, Mailpit recibe el mensaje; correo externo requiere SMTP configurado.

Bloque 13, 16 de noviembre de 2026: antes de GitHub, el usuario priorizó rediseñar frontend y proporcionó imágenes de login, dashboard y organización; registro delegado. Entregados acceso de dos paneles, navegación lateral/topbar, dashboard con estados honestos y organización con tabla, filtros y pestañas. APIs y esquema permanecen iguales. Entorno local activo para revisión, build correcto y validación visual automática bloqueada por herramienta. Detalle y recorrido en [DISENO_FRONTEND](DISENO_FRONTEND.md). Siguiente acción: recoger observaciones del usuario, sin iniciar otra integración.

### Decisiones cerradas

- Objetivo: todo el producto listo para usar, por fases; primero local y después servidor. Disponibilidad declarada: 25 horas semanales. El plan de demo/50 horas ya no limita alcance o aceptación; no hay nueva fecha total comprometida.
- Nueva base implementada mediante V4–V13 con 121 tablas, 85 con RLS forzado y 208 FK. V13 incorpora versión de credenciales e índice de solicitudes de tokens. Las funciones entregadas se registran en la matriz. Pgvector sigue opcional.
- Acceso: correo/contraseña, Google y GitHub, con recuperación/verificación y vinculación segura de identidades. Login GitHub y acceso a repositorios mediante instalación/autorización son funciones distintas.
- Ecosistemas: Java/Maven y JavaScript/npm.
- Paleta visual confirmada, navy #101827, azul #2563EB, cian #06B6D4, slate #1E293B, fondo #F8FAFC, blanco #FFFFFF y semánticos verde/amarillo/naranja/rojo. Enlaces sobre blanco en azul para contraste. Distribución, tokens y variantes legibles en `DISENO_FRONTEND.md`.
- El usuario quiere probar la plataforma y revisar colores/errores durante el desarrollo. Avisar al cerrar hitos interactivos. Primero acceso y colores al levantar el entorno local, después GitHub → repositorio → análisis → inventario con datos reales. Indicar alcance y comprobaciones pendientes en cada aviso.
- Primer piloto con equipos y permisos por repositorio; pruebas de cambios aisladas y propuesta de PR sujeta a aprobación. Roles personalizados no se consideran confirmados.
- IA de la plataforma: Gemini. Embeddings: Ollama. No integrar ni planificar Codex como proveedor del producto hasta nueva instrucción explícita del usuario. Su uso como herramienta de desarrollo no equivale a una integración del producto.
- Notificaciones iniciales dentro de la aplicación; correo de notificaciones después. El correo de recuperación/verificación de cuentas se trata por separado.
- Política documental delegada: conservar originales en almacenamiento privado para descarga autorizada y reindexación; retención/eliminación configurable por organización y borrado de temporales. Sustituye la propuesta histórica de originales solo en el equipo del cliente. Su implementación está pendiente.
- Modelos concretos, dimensión de embeddings, límites de proveedores y periodos operativos se verifican/configuran al implementar, sin inventarlos. No se promete gratuidad de Gemini ni disponibilidad ilimitada.

### Matriz de funciones por completar

Estado a partir del repositorio, no del número de tablas o carpetas. Una función se acepta con backend, acceso autorizado, interfaz aplicable y resultado verificable; una tabla creada no equivale a una función terminada.

| Área / requisitos del maestro | Estado real | Qué falta para aceptarla | Dependencias |
| --- | --- | --- | --- |
| Identidad, organizaciones y permisos — RF-001–006 | Acceso local, organización, autorización, recuperación/verificación SMTP y proveedores externos con fixture. Bloque 11 entrega invitaciones SMTP, aceptación y gestión HTTP/UI de membresías, roles, equipos y permisos por repositorio | Configurar clientes y probar consentimiento real Google/GitHub; entrega durable, paginación de historiales grandes y revisión visual conjunta | Credenciales externas y operación de correo/sesiones |
| GitHub — RF-007–014 | Tabla de conexión y repositorios manuales | Instalar/revocar App, seleccionar repositorios reales, obtener commits/archivos y procesar webhooks idempotentes | Identidad, permisos, credenciales |
| Repositorios y análisis — RF-015–021 | Registro/consulta y solicitud de scan por API con autorización de rol/equipo y RLS | Interfaz de repositorios/scans, worker, estados, errores, historial y commit real | Jobs, GitHub, scanner |
| SBOM/componentes — RF-022–032 | Parser Maven acotado, importación atómica y paquete/versión/presencia normalizados | Carga HTTP, scanner aislado, Maven/npm reales y producción de aristas con evidencia | Worker, jobs, artefactos |
| Vulnerabilidades — RF-033–040 | Esquema normalizado de fuentes/rangos/hallazgos; sin OSV funcional | Consulta OSV, coincidencia por versión, ingesta de evidencia y generación de hallazgos | Adaptadores, jobs, snapshots |
| Inventario — RF-041–046 | Escritura de ocurrencias, sin consulta de inventario ni UI | Búsqueda/filtros/paginación, versiones, repositorios y directa/transitiva solo con evidencia | SBOM/grafo y permisos |
| Grafo — RF-047–052 | Aristas normalizadas con extremos del mismo snapshot | Importar/consultar aristas y navegar rutas/riesgos en UI | Parser/scanner, OSV, permisos |
| Historial/comparación — RF-053–059 | Snapshots importados con procedencia | Consulta por commit y comparación añadidos/eliminados/actualizados del mismo repositorio | Nuevo modelo, inventario |
| Recomendaciones — RF-060–065 | Pendiente | Versiones candidatas/corregidas, cambios documentados, riesgos, evidencia y pruebas sugeridas | OSV, releases/documentos, comparación |
| Alertas — RF-066–070 | Pendiente | Eventos, deduplicación, suscripción, estado y entrega in-app; correo después | Jobs, hallazgos/cambios |
| SBOM/artefactos — RF-071–074 | Solo hash/procedencia; original no retenido | Conservar/consultar/exportar SBOM y comparar snapshots; formatos y trazabilidad | Almacenamiento privado, permisos |
| Documentos/IA — RF-075–080 | Pendiente | Originales privados, versiones, asociaciones, extracción/OCR, búsqueda híbrida, Ollama, Gemini y respuestas citadas con permisos | Jobs, almacenamiento, modelos/configuración, pgvector aislado |
| Impacto — RF-081–085 y RF-091 | Pendiente | ADD/UPDATE/REMOVE/REPLACE con evidencia KNOWN/POTENTIAL/VALIDATED, documentos por revisar, pruebas aisladas y propuesta de PR aprobable | Grafo, versiones, documentos y ejecutor aislado |
| Reportes — RF-086–090 | Pendiente | Informes técnicos/ejecutivos, PDF/CSV/JSON/SBOM, artefactos trazables y programación posterior | Inventario, hallazgos, permisos, jobs |
| Auditoría y requisitos no funcionales | Health, FK/RLS/roles SQL, append-only, evento al crear organización, CSRF, hash de contraseña y límite local de intentos | Ampliar auditoría a casos de uso, logs/métricas, límites coordinados, retención, backup/restauración y servidor | Transversal a cada fase |
| Interfaz — pantallas del apartado 6 | Bloque 13: acceso/registro, navegación lateral/topbar, dashboard con organizaciones reales/estados vacíos y organización con pestañas/tabla/filtros/diálogos | Aceptación visual en escritorio/móvil, correcciones del usuario y pantallas con datos de los módulos aún pendientes | Contratos y funciones por módulo |

### Estado de la base y del código existente

- Tres módulos Maven Domain/Application/Infrastructure; Domain y Application sin Spring/JPA.
- Flyway V1–V13 con 121 tablas, 85 con RLS forzado y 208 FK. Diccionario y siete diagramas actualizados. V12 permite consultar organizaciones propias y V13 soporta revocación de sesiones por versión de credenciales.
- F03 y F04 funcionan como parser e importación persistente acotados, con validación previa, SHA-256, procedencia y rollback. Falta entrada HTTP e inventario visual. Importaciones manuales no inventan commit/scanner ni relaciones.
- Registro/login/logout, creación/listado de organizaciones y restauración de sesión disponibles en Angular. APIs de repositorios/scans autorizadas con rol y equipo. Las sesiones viven en memoria del backend y vencen por inactividad. Docker y el recorrido visual completo siguen pendientes. Ver `ACCESO_LOCAL.md`.
- Verificación y recuperación operativas con tokens aleatorios, hash, caducidad y uso único. Mailpit 1.33.1 recibe los correos locales. Cola de recuperación acotada en memoria, con reenvío manual ante fallo/reinicio. SMTP y PostgreSQL requieren reenvío si una aceptación de correo precede a un commit fallido. Configuración y pruebas en `VERIFICACION_Y_RECUPERACION.md`.
- Bloque 09: Google OIDC y GitHub OAuth con PKCE, state/nonce, sesión propia y vínculo explícito. No se fusionan correos coincidentes; se protege el último método disponible y se revocan otras sesiones al vincular. Sin secretos configurados, ambos proveedores están deshabilitados. Contratos, activación y límites en [IDENTIDAD_GOOGLE_GITHUB](IDENTIDAD_GOOGLE_GITHUB.md). No implica acceso a repositorios GitHub.
- Bloque 11 autorizado por el usuario: invitaciones con token hash/SMTP, aceptación por correo coincidente verificado, gestión de roles/estados, equipos y permisos por repositorio. Conserva el último OWNER y restringe a ADMIN ante OWNER/ADMIN. Pruebas con PostgreSQL/Mailpit y build correctos; revisión visual pendiente. [Recorrido y contratos](INVITACIONES_EQUIPOS_PERMISOS.md). No cambia el esquema V1–V13.
- La transición separa paquete/versión/presencia y normaliza los módulos. Casos de uso con tenant transaccional y credenciales runtime/migración separadas. Autorización consulta cuenta, organización, membresía, rol y equipo activos en cada petición. Los workers y servicios restantes se desarrollan por la matriz.

### Orden de implementación propuesto

1. Base y contratos hasta V13, con artefactos derivados del catálogo aplicado. Mantener migraciones incrementales y verificar modelo/dimensión al activar vectores.
2. Revisar con el usuario invitaciones y gestión de equipos/permisos entregadas en bloque 11. Acceso local en bloque 06, correo en bloque 07 y proveedores/vinculación en bloque 09. Configurar clientes reales Google/GitHub y verificar consentimiento cuando estén disponibles; completar entrega durable y operación para servidor. Elegir el siguiente bloque con el usuario según PENDIENTES.
3. GitHub, jobs, scanner Maven/npm, SBOM, OSV, inventario/grafo/historial y UI.
4. Comparación, recomendaciones e impacto; pruebas aisladas y propuesta de PR con aprobación para cerrar el primer piloto.
5. Documentos privados, extracción/búsqueda, embeddings Ollama y asistente Gemini; reportes y alertas in-app según sus dependencias.
6. Completar visión empresarial, automatizaciones/reportes programados y operación en servidor. Correo de notificaciones después de in-app. Codex queda fuera hasta nueva instrucción.

Estos pasos no autorizan contrataciones, publicación ni PR sobre repositorios externos en este bloque. No se presume una fecha del piloto: equipos/permisos y pruebas aisladas/PR amplían el trabajo y exigen reestimación. Mantener bitácora por bloque, pruebas relevantes y commits coherentes con emoji.

### Criterio de producto listo para usar

Arranque reproducible; usuarios y organización operativos; permisos efectivos; recorridos reales sin fixtures presentados como producción; trabajos recuperables; evidencias/citas y estados de error; configuración/secretos externos al código; backup y restauración comprobados; documentación y pruebas de aceptación de los módulos entregados. Las pantallas o tablas vacías no satisfacen este criterio.

---

# 1. Resumen ejecutivo del proyecto

**Dependency Intelligence** es una propuesta de plataforma SaaS B2B para empresas de software. Su objetivo es ayudar a equipos de ingeniería, arquitectura, DevSecOps y seguridad a entender qué dependencias utilizan sus aplicaciones, qué riesgos presentan, dónde se propagan y qué podría ocurrir si se actualizan, eliminan o sustituyen.

La visión no es crear simplemente otro escáner de vulnerabilidades. La propuesta busca combinar:

1. **Dependency Inventory:** inventario centralizado de dependencias directas y transitivas de los repositorios.
2. **Vulnerability Intelligence:** identificación y seguimiento de vulnerabilidades asociadas a paquetes y versiones.
3. **Dependency Graph / Impact Graph:** visualización de relaciones y propagación de dependencias entre proyectos.
4. **Change Impact Analysis:** análisis de consecuencias potenciales de modificar una dependencia.
5. **Documentation Intelligence:** documentación técnica centralizada y conectada con repositorios, componentes y versiones.
6. **AI Assistant:** asistente contextual que consulta los datos estructurados y la documentación de la organización, responde con evidencia y explica riesgos.
7. **Reporting Center:** reportes técnicos y ejecutivos, exportaciones y seguimiento.
8. **Enterprise capabilities:** integraciones, auditoría, permisos, multi-tenancy y capacidades avanzadas para organizaciones.

Posicionamiento conceptual propuesto:

**“Understand. Predict. Protect.”**

La plataforma pretende que una organización pueda entender su ecosistema de dependencias, anticipar el impacto de cambios técnicos y gestionar riesgos con evidencia.

## 1.1 Problema que busca resolver

En empresas con varios repositorios, servicios y equipos, la información de dependencias suele estar fragmentada. Es difícil responder rápidamente preguntas como:

- ¿Qué librerías y versiones utiliza realmente la organización?
- ¿En cuáles repositorios aparece una dependencia específica?
- ¿Una dependencia es directa o transitiva?
- ¿Qué vulnerabilidades afectan a qué proyectos?
- ¿Qué versión está corregida y qué evidencia lo respalda?
- Si actualizamos o eliminamos un paquete, ¿qué otros componentes podrían verse afectados?
- ¿Qué servicios, proyectos o documentos dependen de esa versión?
- ¿Qué cambios son urgentes y cuáles pueden planificarse?
- ¿Qué documentación interna explica cómo se utiliza una librería?
- ¿Cómo se puede presentar el estado de dependencias y riesgos a dirección, arquitectura o seguridad?

La propuesta consiste en convertir datos dispersos de repositorios, SBOM, vulnerabilidades, versiones y documentación en una visión navegable y trazable.

## 1.2 Usuarios potenciales

Perfiles que podrían utilizar o comprar el producto:

- CTO / VP of Engineering.
- Engineering Manager.
- Software Architect.
- DevSecOps Engineer.
- Application Security Engineer.
- Product Security / Vulnerability Management.
- Líderes de equipos de desarrollo.
- Equipos de cumplimiento y auditoría técnica.

El usuario final puede ser un desarrollador o arquitecto; el comprador podría ser un responsable de ingeniería, seguridad o tecnología. Esta hipótesis comercial todavía debe validarse.

## 1.3 Mercado objetivo

La intención conversada es construir algo que pueda ofrecerse a empresas ya establecidas, con especial interés en el mercado de Estados Unidos. No se ha validado todavía un segmento exacto, tamaño de empresa, industria ni comprador económico.

---

# 2. Origen y contexto de decisión

Dependency Intelligence es uno de los dos finalistas de una evaluación de oportunidades SaaS B2B:

- **Enterprise Commission Management**
- **Dependency Intelligence**

El usuario solicitó extraer la información de ambos finalistas para compartirla en otro chat. Después, el trabajo se concentró en Dependency Intelligence.

La motivación es construir un producto tecnológicamente interesante, no una aplicación CRUD sencilla. El proyecto debe tener una propuesta empresarial clara, funcionalidades defendibles y potencial de venta a compañías de Estados Unidos.

El usuario también preguntó por tecnologías como bases de datos vectoriales y embeddings. La orientación aplicada al proyecto fue:

- Los datos operativos y estructurados —repositorios, paquetes, versiones, vulnerabilidades, relaciones, análisis, usuarios— deben almacenarse principalmente en una base relacional.
- Una base vectorial o `pgvector` puede aportar valor para buscar y consultar documentación técnica mediante embeddings.
- No se debe usar una base vectorial como sustituto de consultas relacionales para inventarios, versiones o vulnerabilidades exactas.
- La IA no debe ser la fuente de verdad para determinar si una dependencia es vulnerable o si una actualización es segura.

---

# 3. Alcance funcional y módulos del producto

## 3.1 Dependency Inventory

Inventario consolidado de los componentes detectados en los repositorios conectados.

Debe permitir:

- Ver todas las dependencias detectadas.
- Distinguir dependencias directas y transitivas.
- Identificar nombre, ecosistema, versión y paquete.
- Saber en qué repositorios aparece cada dependencia.
- Consultar las distintas versiones de un mismo paquete presentes en la organización.
- Buscar y filtrar por nombre, ecosistema, versión, repositorio y otros atributos.
- Navegar desde una dependencia hacia sus repositorios y relaciones.
- Consultar el historial de presencia y cambios de dependencias.
- Conservar la procedencia de los datos y el commit analizado.

## 3.2 Vulnerability Intelligence

Identificación de vulnerabilidades que afecten a las dependencias detectadas.

Debe permitir:

- Consultar vulnerabilidades por paquete, ecosistema y versión.
- Asociar vulnerabilidades con dependencias y repositorios.
- Mostrar identificadores como OSV ID y, cuando exista, CVE.
- Mostrar severidad cuando la fuente la proporcione.
- Mostrar versiones afectadas y versiones corregidas cuando estén disponibles.
- Evitar duplicados al normalizar resultados de diferentes fuentes.
- Mostrar el estado de actualización de los datos.
- Identificar en qué repositorios aparece una dependencia vulnerable.
- Mantener trazabilidad de la fuente de cada hallazgo.

La fuente inicial propuesta es **OSV (Open Source Vulnerabilities)**. No se debe asumir que OSV cubre todos los ecosistemas, todos los tipos de vulnerabilidad ni todos los metadatos. La cobertura y el tratamiento de severidad/versiones deben investigarse y probarse.

## 3.3 Dependency Graph / Impact Graph

Representación visual de las relaciones entre repositorios, dependencias y versiones.

Primera versión conceptual:

- Repositorio.
- Dependencia.
- Versión.
- Relación directa o transitiva.
- Rutas que explican cómo una dependencia aparece dentro de un proyecto.

El grafo debe permitir explorar dónde se utiliza un componente y qué otros componentes dependen de él.

**Distinción fundamental:** una relación de dependencia de paquetes no demuestra que dos microservicios se comuniquen en tiempo de ejecución. No debe presentarse una conexión de SBOM como prueba de comunicación entre servicios. Para inferir comunicación real se necesitarían otras fuentes, por ejemplo telemetría, trazas, service mesh, configuración o documentación de arquitectura.

En el MVP, el grafo recomendado es sencillo: **repositorio → dependencia → versión**, con indicación de dependencia directa/transitiva y, si el SBOM lo permite, las rutas entre componentes.

## 3.4 Scan History

Historial de análisis por repositorio y organización.

Debe registrar:

- Fecha y hora.
- Repositorio.
- Commit o referencia analizada.
- Estado del análisis.
- Herramienta y versión del scanner.
- Componentes detectados.
- Vulnerabilidades detectadas.
- Errores o advertencias.
- Duración del análisis.
- Diferencias frente al análisis anterior.

Debe permitir comparar snapshots y detectar componentes agregados, eliminados o actualizados.

## 3.5 Change Impact Analysis

Módulo para evaluar qué podría ocurrir al agregar, actualizar, eliminar o sustituir una dependencia.

La idea conversada es que el usuario pueda seleccionar:

- Repositorio.
- Paquete.
- Versión actual.
- Versión objetivo.
- Tipo de operación: agregar, actualizar, eliminar o sustituir.

La plataforma produciría un análisis sin modificar inicialmente el repositorio.

Resultados potenciales:

- Repositorios afectados.
- Componentes que dependen del paquete.
- Dependencias transitivas relacionadas.
- Cambios documentados entre versiones.
- Breaking changes identificados en changelogs o release notes.
- Vulnerabilidades que se corrigen o introducen según la información disponible.
- Posibles incompatibilidades.
- Documentos que podrían requerir revisión.
- Pruebas sugeridas.
- Evidencia y nivel de confianza.

### Clasificación obligatoria de resultados

La plataforma debe distinguir claramente:

1. **Impacto conocido:** existe una relación demostrada por SBOM, manifest, lockfile, metadatos o evidencia verificable.
2. **Impacto potencial:** existe una ruta o cambio que merece revisión, pero no se ha demostrado que produzca un fallo.
3. **Impacto validado:** una prueba, análisis estático o ejecución controlada confirmó el efecto.

No afirmar que un servicio se romperá simplemente porque aparece una relación en el grafo.

### Evolución futura: Simulate Change

Una capacidad avanzada podría:

1. Crear una rama temporal.
2. Modificar el manifest o lockfile.
3. Instalar/resolver la nueva versión.
4. Ejecutar pruebas.
5. Comparar resultados.
6. Generar un informe.
7. Proponer un Pull Request sujeto a aprobación humana.

Esta capacidad no forma parte del MVP de dos semanas.

## 3.6 Documentation Center

Centro para cargar, organizar, relacionar y consultar documentación técnica.

Tipos de documentos contemplados:

- PDF.
- DOCX.
- Markdown.
- TXT.
- README.
- OpenAPI.
- ADR (Architecture Decision Records).
- Manuales técnicos.
- Changelogs y release notes.
- Documentación interna de librerías, servicios y procesos.

Funciones propuestas:

- Cargar documentos.
- Asociar documentos con una organización.
- Asociar documentos con repositorios, microservicios, librerías, componentes o versiones.
- Indexar texto para búsqueda.
- Buscar semánticamente.
- Consultar documentación desde el asistente de IA.
- Mostrar fuentes y fragmentos que respaldan una respuesta.
- Detectar documentos que podrían quedar desactualizados cuando cambia una dependencia.
- Marcar documentos para revisión, sin declarar automáticamente que están incorrectos.

Integraciones futuras mencionadas: Notion, Confluence y documentación almacenada en GitHub.

## 3.7 AI Assistant / Documentation Intelligence

Se propuso una IA contextual, no un chatbot genérico.

Debe poder responder preguntas relacionadas con los datos reales de la organización, por ejemplo:

- ¿Dónde utilizamos esta dependencia?
- ¿Qué repositorios están afectados por esta vulnerabilidad?
- Explícame esta vulnerabilidad.
- ¿Qué versión corregida existe según la fuente?
- ¿Qué dependencias transitivas llegan a este paquete?
- ¿Qué podría verse afectado si actualizamos esta librería?
- ¿Qué documentación interna explica el uso de este componente?
- ¿Qué riesgos de dependencias tiene esta organización?
- ¿Qué diferencias hay entre dos versiones?
- ¿Qué pruebas deberíamos ejecutar antes de actualizar?

### Arquitectura de IA propuesta

No entrenar un modelo propio inicialmente. Usar un patrón RAG (Retrieval-Augmented Generation):

1. **PostgreSQL relacional:** datos operativos y estructurados.
2. **pgvector:** embeddings de documentos, fragmentos, manuales, ADR, README, OpenAPI y release notes.
3. **LLM:** interpreta la pregunta, consulta fuentes autorizadas y redacta una explicación.
4. **Capa de recuperación:** busca documentos y datos relevantes.
5. **Control de permisos:** el asistente solo puede consultar información a la que el usuario tiene acceso.
6. **Citas/evidencias:** las respuestas deben mostrar de dónde sale la información.

El asistente debe distinguir entre:
- Evidencia documentada.
- Relación demostrada.
- Inferencia.
- Hipótesis o riesgo potencial.

No debe afirmar que una actualización es segura sin evidencia. No debe decidir por sí mismo si una vulnerabilidad existe; eso corresponde a fuentes de vulnerabilidades y lógica determinista.

### Límites de seguridad de IA

- No dar acceso libre del LLM a SQL.
- Usar consultas parametrizadas, funciones permitidas o servicios de dominio.
- Validar permisos antes de recuperar datos.
- Evitar que documentos maliciosos o instrucciones dentro de ellos controlen el sistema.
- No exponer secretos o datos de otros tenants.
- Registrar procedencia de las respuestas.
- Controlar consumo, rate limits y abuso.

## 3.8 Reporting Center

Sistema de informes técnicos y ejecutivos.

Tipos de reportes propuestos:

- **Executive Report:** resumen del estado general de dependencias y riesgos.
- **Dependency Report:** inventario de paquetes, versiones, ecosistemas y repositorios.
- **Vulnerability Report:** vulnerabilidades, severidad, paquetes afectados y correcciones conocidas.
- **Impact Report:** posibles consecuencias de cambios.
- **Repository Report:** estado de cada repositorio.
- **Change Report:** diferencias entre análisis o versiones.
- **SBOM Export:** exportación de inventarios.

Formatos contemplados:

- PDF.
- CSV.
- JSON.
- CycloneDX.
- SPDX.

Evolución posterior:
- Reportes semanales o mensuales.
- Envíos programados.
- Reportes por organización, equipo, repositorio o proyecto.
- Reportes para auditoría y dirección.

---

# 4. Requisitos funcionales recopilados

Los siguientes requisitos son el borrador funcional que se propuso en la conversación. Los identificadores son una organización de trabajo, no una especificación validada formalmente. Se debe conservar la trazabilidad y revisar duplicados o cambios de prioridad al convertirlos en backlog.

## 4.1 Usuarios, organizaciones y permisos

- **RF-001:** permitir registro/inicio de sesión de usuarios.
- **RF-002:** permitir crear o pertenecer a una organización.
- **RF-003:** asociar usuarios con organizaciones.
- **RF-004:** gestionar roles.
- **RF-005:** controlar permisos por rol.
- **RF-006:** aislar los datos de cada organización (multi-tenancy).

## 4.2 Integración con GitHub

- **RF-007:** conectar una cuenta u organización de GitHub mediante GitHub App.
- **RF-008:** permitir seleccionar repositorios autorizados.
- **RF-009:** almacenar la información necesaria de la instalación, sin exponer secretos.
- **RF-010:** consultar metadatos del repositorio.
- **RF-011:** recuperar información de archivos/manifests necesaria para el análisis.
- **RF-012:** recibir webhooks de GitHub.
- **RF-013:** iniciar análisis ante eventos configurados, por ejemplo push.
- **RF-014:** permitir desconectar/revocar la integración.

## 4.3 Gestión de repositorios y análisis

- **RF-015:** listar repositorios conectados.
- **RF-016:** mostrar estado del repositorio.
- **RF-017:** iniciar un análisis manual.
- **RF-018:** consultar el estado de un análisis.
- **RF-019:** consultar el historial de análisis.
- **RF-020:** asociar cada análisis con un commit o referencia concreta.
- **RF-021:** mostrar errores y advertencias del análisis.

## 4.4 Detección de dependencias y SBOM

- **RF-022:** detectar dependencias desde los archivos compatibles.
- **RF-023:** soportar inicialmente ecosistemas seleccionados.
- **RF-024:** detectar dependencias directas.
- **RF-025:** detectar dependencias transitivas cuando la herramienta y los archivos disponibles lo permitan.
- **RF-026:** identificar nombre del paquete.
- **RF-027:** identificar ecosistema.
- **RF-028:** identificar versión.
- **RF-029:** generar un SBOM.
- **RF-030:** soportar CycloneDX como formato inicial.
- **RF-031:** almacenar los componentes detectados.
- **RF-032:** almacenar las relaciones entre componentes.

## 4.5 Vulnerabilidades

- **RF-033:** consultar vulnerabilidades de las dependencias.
- **RF-034:** asociar hallazgos con paquete, ecosistema y versión.
- **RF-035:** mostrar identificador de vulnerabilidad.
- **RF-036:** mostrar severidad cuando esté disponible.
- **RF-037:** mostrar rangos de versiones afectadas.
- **RF-038:** mostrar versión corregida cuando la fuente la proporcione.
- **RF-039:** asociar vulnerabilidades con repositorios.
- **RF-040:** normalizar y evitar duplicados.

## 4.6 Inventario global

- **RF-041:** mostrar inventario consolidado de dependencias.
- **RF-042:** buscar dependencias.
- **RF-043:** filtrar por ecosistema, versión y repositorio.
- **RF-044:** distinguir directas y transitivas.
- **RF-045:** mostrar repositorios donde aparece una dependencia.
- **RF-046:** mostrar múltiples versiones del mismo paquete.

## 4.7 Grafo de dependencias

- **RF-047:** visualizar relaciones de dependencias.
- **RF-048:** mostrar nodos de repositorios.
- **RF-049:** mostrar nodos de paquetes/componentes.
- **RF-050:** mostrar relaciones y rutas de dependencia.
- **RF-051:** identificar dependencias transitivas.
- **RF-052:** mostrar información de riesgo asociada a los nodos cuando exista.

## 4.8 Historial y comparación

- **RF-053:** conservar snapshots de análisis.
- **RF-054:** consultar un análisis por commit.
- **RF-055:** comparar dos análisis.
- **RF-056:** detectar dependencias agregadas.
- **RF-057:** detectar dependencias eliminadas.
- **RF-058:** detectar dependencias actualizadas.
- **RF-059:** conservar fecha, fuente y procedencia de cada snapshot.

## 4.9 Recomendaciones de actualización

- **RF-060:** sugerir versiones candidatas de actualización cuando exista información fiable.
- **RF-061:** identificar versiones corregidas.
- **RF-062:** mostrar riesgos asociados a la actualización.
- **RF-063:** mostrar breaking changes documentados cuando se disponga de evidencia.
- **RF-064:** mostrar fuentes y evidencia de la recomendación.
- **RF-065:** sugerir pruebas que deberían ejecutarse antes de actualizar.

## 4.10 Alertas y seguimiento

- **RF-066:** detectar cambios relevantes en dependencias.
- **RF-067:** detectar nuevas vulnerabilidades.
- **RF-068:** notificar cambios de severidad o estado.
- **RF-069:** permitir consultar el estado de una alerta.
- **RF-070:** evitar notificaciones duplicadas.

## 4.11 SBOM y exportación

- **RF-071:** conservar SBOM generados.
- **RF-072:** consultar SBOM por repositorio y análisis.
- **RF-073:** exportar SBOM.
- **RF-074:** comparar SBOM/snapshots.

## 4.12 Documentación, IA, análisis de cambios y reportes

- **RF-075:** permitir cargar documentación técnica.
- **RF-076:** asociar documentos con organizaciones, repositorios y componentes.
- **RF-077:** indexar documentos para búsqueda semántica.
- **RF-078:** permitir consultas sobre documentación mediante IA.
- **RF-079:** mostrar referencias y evidencias de las respuestas.
- **RF-080:** permitir consultas en lenguaje natural sobre el inventario y los riesgos, respetando permisos.
- **RF-081:** analizar el impacto potencial de actualizar una dependencia.
- **RF-082:** analizar el impacto potencial de eliminar una dependencia.
- **RF-083:** mostrar repositorios y componentes relacionados/afectados.
- **RF-084:** identificar breaking changes documentados.
- **RF-085:** simular un cambio sin modificar inicialmente el repositorio.
- **RF-086:** generar reportes técnicos.
- **RF-087:** generar reportes ejecutivos.
- **RF-088:** exportar reportes a PDF, CSV y JSON.
- **RF-089:** exportar SBOM en formatos soportados.
- **RF-090:** permitir programar reportes en una fase posterior.
- **RF-091:** detectar documentación potencialmente desactualizada y marcarla para revisión.

---

# 5. Requisitos no funcionales recopilados

Los siguientes requisitos se plantearon como categorías y criterios iniciales. Los valores concretos de rendimiento, disponibilidad y retención todavía deben definirse.

## 5.1 Seguridad

- Comunicación HTTPS.
- Gestión segura de tokens y secretos.
- Validación de firma de webhooks.
- Autorización en cada recurso.
- Aislamiento de datos por organización.
- Ejecución aislada del scanner.
- No ejecutar scripts arbitrarios del repositorio sin controles.
- No montar el Docker socket en el contenedor del scanner.
- No entregar secretos innecesarios al proceso de análisis.
- Límites de CPU, memoria y tiempo.
- Auditoría de acciones relevantes.
- No registrar tokens, credenciales ni secretos en logs.
- Protección contra acceso cruzado entre tenants.
- Protección de documentos y resultados recuperados por IA.

## 5.2 Rendimiento

- Ejecutar análisis de forma asíncrona.
- No mantener una petición HTTP abierta durante todo el escaneo.
- Procesar dependencias en lotes cuando sea conveniente.
- Establecer límites de duración.
- Medir duración de cada etapa.
- Definir objetivos medibles de respuesta para dashboard, búsqueda y consulta de resultados.

## 5.3 Disponibilidad y confiabilidad

- Un fallo temporal de GitHub u OSV no debe destruir análisis previos.
- Implementar reintentos controlados.
- Procesar webhooks de forma idempotente.
- Mantener estados claros del análisis.
- Asociar los resultados con commit y fecha.
- Manejar errores parciales.
- Tener estrategia de backup y recuperación.
- Evitar duplicar análisis ante eventos repetidos.

## 5.4 Escalabilidad

- Soportar múltiples organizaciones.
- Soportar varios repositorios por organización.
- Limitar y controlar análisis paralelos.
- Usar índices adecuados.
- Separar trabajos pesados de peticiones web.
- Diseñar el almacenamiento para crecer en snapshots, componentes y vulnerabilidades.

## 5.5 Mantenibilidad

- Separar dominio, aplicación e infraestructura.
- Mantener integraciones intercambiables.
- Implementar pruebas unitarias y de integración.
- Documentar APIs.
- Gestionar cambios de esquema mediante migraciones.
- Evitar que el dominio dependa directamente de OSV, GitHub o cdxgen.

## 5.6 Interoperabilidad

- Utilizar CycloneDX como formato inicial de SBOM.
- Considerar SPDX.
- Utilizar identificadores normalizados como PURL cuando corresponda.
- Soportar varios ecosistemas de paquetes a futuro.
- Mantener adaptadores para proveedores externos.

## 5.7 Privacidad y retención

- Minimizar los datos obtenidos de repositorios.
- Definir qué contenido se almacena y por cuánto tiempo.
- Eliminar archivos temporales al terminar el análisis.
- Permitir revocar la integración con GitHub.
- No conservar secretos detectados accidentalmente.
- Definir políticas de retención y eliminación de documentos y análisis.

## 5.8 Usabilidad y accesibilidad

- Interfaz consistente.
- Navegación clara entre repositorios, dependencias y vulnerabilidades.
- Resultados técnicos explicados de forma comprensible.
- Grafo navegable y legible.
- Diseño responsive.
- Estados vacíos, de carga y error bien definidos.

## 5.9 Observabilidad

- Logs estructurados.
- Métricas de duración y resultado de análisis.
- Métricas de llamadas a proveedores.
- Seguimiento de trabajos/colas.
- Registro de errores.
- Procedencia de resultados.
- Trazabilidad entre webhook, job, scan, SBOM y vulnerabilidades.

## 5.10 Requisitos no funcionales añadidos para IA, documentación e informes

- **RNF-050:** la IA debe respetar permisos y aislamiento multi-tenant.
- **RNF-051:** las respuestas de IA deben presentar evidencias o referencias cuando sea posible.
- **RNF-052:** documentos cargados deben procesarse de forma segura.
- **RNF-053:** la IA debe diferenciar hechos, inferencias e incertidumbres.
- **RNF-054:** los resultados de impacto deben estar ligados a versiones, commits y fuentes.
- **RNF-055:** los reportes deben ser trazables y reproducibles.
- **RNF-056:** el uso de IA debe tener límites de consumo, rate limiting y controles contra abuso.

---

# 6. Pantallas y navegación propuestas

Se planteó una interfaz SaaS B2B con navegación lateral.

## 6.1 Pantallas principales

1. **Login / Access**
   - Inicio de sesión.
   - Recuperación de acceso.
   - Selección de organización si el usuario pertenece a varias.

2. **Onboarding / GitHub Integration**
   - Explicar conexión.
   - Instalar GitHub App.
   - Seleccionar organización y repositorios.
   - Mostrar permisos solicitados.
   - Confirmar conexión.

3. **Overview / Dashboard**
   - Resumen de repositorios.
   - Dependencias detectadas.
   - Vulnerabilidades por severidad.
   - Cambios recientes.
   - Estado de análisis.
   - Riesgos destacados.
   - Accesos rápidos.

4. **Repositories**
   - Lista de repositorios conectados.
   - Lenguaje/ecosistema detectado.
   - Último análisis.
   - Estado.
   - Número de dependencias y vulnerabilidades.
   - Acción de análisis manual.

5. **Repository Detail**
   - Overview.
   - Dependencies.
   - Vulnerabilities.
   - Dependency Graph.
   - Scan History.
   - Commit analizado y fecha.

6. **Dependencies / Inventory**
   - Inventario global.
   - Nombre, ecosistema, versión.
   - Directa/transitiva.
   - Repositorios donde aparece.
   - Filtros y búsqueda.

7. **Dependency Detail**
   - Información del paquete.
   - Versiones detectadas.
   - Repositorios relacionados.
   - Vulnerabilidades.
   - Dependencias que lo incluyen.
   - Historial de cambios.
   - Documentación asociada.
   - Acceso a análisis de impacto.

8. **Vulnerabilities**
   - Listado de vulnerabilidades.
   - Severidad.
   - Paquete/versión afectada.
   - Repositorios afectados.
   - Estado de corrección.
   - Fuente.

9. **Impact Graph**
   - Grafo de repositorios, paquetes y versiones.
   - Filtros por repositorio, paquete y riesgo.
   - Navegación por rutas.

10. **Scan History**
    - Historial de análisis.
    - Estado, commit, fecha y duración.
    - Comparación entre snapshots.

11. **Integrations**
    - GitHub.
    - Proveedores de vulnerabilidades.
    - Integraciones futuras.

12. **Settings / Organization Management**
    - Organización.
    - Usuarios.
    - Roles.
    - Preferencias.
    - Seguridad.

## 6.2 Pantallas nuevas propuestas

13. **AI Assistant**
    - Consultas contextuales.
    - Historial de preguntas.
    - Respuestas con evidencia.
    - Acceso contextual desde otras pantallas.

14. **Documentation Center**
    - Carga y listado de documentos.
    - Asociación a repositorios/componentes.
    - Búsqueda.
    - Estado de indexación.
    - Documentos por revisar.

15. **Change Impact Analysis**
    - Selección de paquete y versión.
    - Simulación.
    - Repositorios y rutas afectadas.
    - Breaking changes documentados.
    - Riesgos, pruebas y evidencia.

16. **Reporting Center**
    - Catálogo de reportes.
    - Filtros.
    - Vista previa.
    - Exportación.
    - Programación futura.

## 6.3 Navegación lateral sugerida

- Overview
- Repositories
- Dependencies
- Vulnerabilities
- Impact Graph
- Documentation
- Change Impact
- AI Assistant
- Reports
- Scan History
- Integrations
- Settings

No es necesario construir toda la navegación en la primera entrega. Las pantallas de IA, documentación avanzada, Change Impact y reportes programados pueden quedar para fases posteriores.

## 6.4 Pantallas con mayor valor visual y funcional

- Dashboard: permite entender el estado general.
- Dependency Detail: concentra el contexto de un paquete.
- Impact Graph: muestra visualmente las relaciones.
- Change Impact Analysis: puede convertirse en diferenciador si se implementa con evidencia real.

Las imágenes o mockups previos eran ilustrativos; no representan datos reales ni un diseño final aprobado.

---

# 7. Arquitectura técnica propuesta

## 7.1 Stack candidato

| Capa | Tecnología propuesta | Propósito |
|---|---|---|
| Frontend | Angular + TypeScript | Aplicación web SaaS |
| Backend | Java + Spring Boot | API, lógica de negocio e integraciones |
| Base de datos | PostgreSQL | Datos operativos y relacionales |
| Vector search | pgvector, fase posterior | Embeddings y búsqueda semántica documental |
| Repositorios | GitHub App / GitHub API | Conexión y lectura autorizada |
| Scanner | cdxgen | Generación de SBOM |
| Formato SBOM | CycloneDX | Inventario interoperable |
| Vulnerabilidades | OSV API | Consulta de vulnerabilidades |
| Contenedores | Docker | Aislamiento de ejecución |
| Grafo visual | Cytoscape.js | Visualización de dependencias |
| Fuente opcional | deps.dev | Metadatos adicionales, sujeto a validación |
| Scanner opcional | OSV-Scanner | Alternativa o complemento, sujeto a evaluación |
| PostgreSQL alojado opcional | Neon Free | Prototipo remoto, sujeto a límites del plan |

El usuario trabaja con Java/Spring Boot, Angular/TypeScript y PostgreSQL, por lo que el stack aprovecha tecnologías que ya conoce.

## 7.2 Flujo principal de análisis

1. El usuario instala/autoriza la GitHub App.
2. La aplicación recibe la instalación y los repositorios autorizados.
3. El backend registra la integración y los repositorios.
4. El usuario solicita un análisis o llega un webhook configurado.
5. El backend crea un trabajo asíncrono.
6. Se obtiene el commit o referencia exacta.
7. Se recuperan los archivos requeridos del repositorio.
8. Se ejecuta cdxgen en un entorno aislado y limitado.
9. Se genera un SBOM CycloneDX.
10. El backend parsea el SBOM.
11. Se normalizan componentes, versiones, PURL y relaciones.
12. Se persisten el análisis, componentes y relaciones.
13. Se consultan vulnerabilidades en OSV por lotes.
14. Se normalizan vulnerabilidades y se asocian con componentes.
15. Se actualiza el estado del análisis.
16. El frontend muestra inventario, vulnerabilidades, grafo e historial.

## 7.3 Integración OSV

Endpoints de referencia que se mencionaron:

- `POST https://api.osv.dev/v1/querybatch`
- `GET https://api.osv.dev/v1/vulns/{vulnerability_id}`

La consulta requiere datos adecuados de paquete, ecosistema y versión. Para el MVP conviene agrupar consultas con `querybatch`, controlar lotes, manejar respuestas parciales y evitar consultas redundantes.

La respuesta de OSV debe mapearse a un modelo interno. No se debe acoplar el dominio al JSON externo.

## 7.4 SBOM y cdxgen

- cdxgen se propuso como herramienta inicial para generar SBOM.
- CycloneDX es el formato inicial recomendado.
- Se debe comprobar la calidad de detección por ecosistema.
- La detección de transitivas depende del ecosistema, archivos disponibles, lockfiles y comportamiento de la herramienta.
- Se deben probar repositorios con y sin lockfiles.
- Se deben evaluar monorepos y proyectos multi-módulo.
- No se debe prometer cobertura total a partir de un solo escaneo.

## 7.5 GitHub App

La GitHub App es preferible a pedir tokens personales de larga duración.

La investigación debe confirmar:

- Permisos mínimos requeridos.
- Acceso a repositorios privados.
- Instalación a nivel de organización.
- Obtención y renovación de installation access tokens.
- Webhooks y validación de firma.
- Rate limits.
- Revocación/desinstalación.
- Compatibilidad futura con GitHub Enterprise Server.
- Qué datos se leen y almacenan.

## 7.6 Arquitectura interna

Se recomendó separar:

- **Domain:** entidades y reglas del negocio.
- **Application:** casos de uso y orquestación.
- **Infrastructure:** PostgreSQL, GitHub, OSV, cdxgen, almacenamiento y proveedores.
- **API / Presentation:** controladores REST y DTOs.
- **Workers:** ejecución asíncrona de análisis y tareas pesadas.

Posibles módulos de dominio:

- Organization / Identity.
- Repository.
- Integration.
- Scan.
- SBOM / Component.
- Dependency Relationship.
- Vulnerability.
- Documentation.
- Impact Analysis.
- Reporting / Notification.

La arquitectura puede seguir principios de arquitectura hexagonal y DDD, sin sobredimensionar el diseño para el MVP. No se debe crear complejidad de microservicios sin necesidad; un monolito modular con workers puede ser más viable al inicio.

---

# 8. Modelo de datos conceptual

No se ha aprobado un esquema SQL definitivo. Las siguientes entidades son una base para investigar y diseñar.

## 8.1 Entidades principales

- **Organization:** organización cliente.
- **User:** usuario.
- **Membership:** relación usuario-organización y rol.
- **Integration:** integración externa instalada.
- **GitHubInstallation:** identificador y estado de instalación.
- **Repository:** repositorio conectado.
- **Scan:** ejecución de análisis.
- **ScanArtifact:** SBOM u otros artefactos generados.
- **Component:** paquete/componente normalizado.
- **ScanComponent:** presencia de un componente en un análisis.
- **DependencyRelationship:** relación entre componentes.
- **Vulnerability:** vulnerabilidad normalizada.
- **ComponentVulnerability:** asociación entre componente y vulnerabilidad.
- **Document:** documento cargado o sincronizado.
- **DocumentChunk:** fragmentos indexados.
- **DocumentAssociation:** asociación de documento con repositorio/componente/organización.
- **ImpactAnalysis:** análisis de cambio.
- **ImpactFinding:** hallazgo de impacto con tipo, evidencia y confianza.
- **Report:** reporte generado.
- **Notification:** alerta o notificación.
- **AuditEvent:** registro de acciones relevantes.

## 8.2 Consideraciones de diseño

- Los componentes deben poder identificarse de forma estable por ecosistema y paquete, preferiblemente con PURL cuando aplique.
- La versión debe ser parte del contexto de presencia del componente en un análisis.
- No confundir el catálogo de componentes con la aparición concreta de un componente en un repositorio y commit.
- Las relaciones deben asociarse al snapshot/scan que las detectó.
- La vulnerabilidad debe guardar identificador y procedencia externa.
- Mantener datos originales o referencias suficientes para auditoría y reprocesamiento.
- La asociación documento-componente debe permitir relaciones de muchos a muchos.
- Los embeddings deben vincularse con el documento, fragmento, organización y permisos.
- Los resultados de impacto deben guardar versión actual, versión objetivo, commit base, fuentes y método utilizado.

---

# 9. API REST conceptual

No se han cerrado endpoints definitivos. Los siguientes son ejemplos de organización para convertir en contrato OpenAPI.

## 9.1 Autenticación y organizaciones

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/me`
- `GET /api/organizations`
- `POST /api/organizations`
- `GET /api/organizations/{organizationId}`
- `GET /api/organizations/{organizationId}/members`
- `POST /api/organizations/{organizationId}/members`

## 9.2 GitHub e integraciones

- `GET /api/integrations`
- `POST /api/integrations/github/install`
- `GET /api/integrations/github/callback`
- `GET /api/integrations/github/repositories`
- `POST /api/integrations/github/repositories`
- `DELETE /api/integrations/{integrationId}`
- `POST /api/webhooks/github`

## 9.3 Repositorios y análisis

- `GET /api/repositories`
- `GET /api/repositories/{repositoryId}`
- `POST /api/repositories/{repositoryId}/scans`
- `GET /api/repositories/{repositoryId}/scans`
- `GET /api/scans/{scanId}`
- `GET /api/scans/{scanId}/components`
- `GET /api/scans/{scanId}/sbom`
- `GET /api/scans/{scanId}/diff`

## 9.4 Dependencias y vulnerabilidades

- `GET /api/dependencies`
- `GET /api/dependencies/{dependencyId}`
- `GET /api/dependencies/{dependencyId}/repositories`
- `GET /api/dependencies/{dependencyId}/vulnerabilities`
- `GET /api/vulnerabilities`
- `GET /api/vulnerabilities/{vulnerabilityId}`
- `GET /api/repositories/{repositoryId}/vulnerabilities`

## 9.5 Grafo

- `GET /api/graph`
- `GET /api/repositories/{repositoryId}/graph`
- `GET /api/dependencies/{dependencyId}/graph`

## 9.6 Documentación e IA

- `POST /api/documents`
- `GET /api/documents`
- `GET /api/documents/{documentId}`
- `DELETE /api/documents/{documentId}`
- `POST /api/documents/{documentId}/index`
- `GET /api/documents/search`
- `POST /api/assistant/query`

## 9.7 Análisis de impacto

- `POST /api/impact-analyses`
- `GET /api/impact-analyses`
- `GET /api/impact-analyses/{analysisId}`
- `POST /api/impact-analyses/{analysisId}/simulate`

## 9.8 Reportes

- `GET /api/reports`
- `POST /api/reports`
- `GET /api/reports/{reportId}`
- `GET /api/reports/{reportId}/download`
- `POST /api/reports/schedules` (fase posterior)

Estos endpoints son propuestas, no contratos implementados ni validados.

---

# 10. Seguridad del scanner y de los repositorios

Una advertencia importante de la conversación: el repositorio conectado es contenido no confiable.

El scanner no debe ejecutar arbitrariamente scripts del proyecto.

Controles recomendados:

- Ejecutar cdxgen en contenedor aislado.
- Usar una imagen controlada y versiones fijadas.
- No montar el Docker socket.
- No usar privilegios elevados.
- Limitar CPU, memoria, almacenamiento y duración.
- No pasar credenciales de GitHub al contenedor si no son necesarias.
- Evitar acceso de red saliente salvo lo indispensable.
- Montar el contenido del repositorio como solo lectura cuando sea posible.
- Eliminar archivos temporales.
- Limitar tamaño de repositorio y artefactos.
- Validar rutas para evitar path traversal.
- Registrar scanner y versión.
- Manejar fallos y timeouts.
- Probar comportamiento con proyectos malformados o maliciosos.

---

# 11. Plan de MVP en dos semanas

El usuario expresó una restricción temporal importante: quiere sacar Dependency Intelligence en aproximadamente dos semanas. Por tanto, no se debe intentar construir toda la visión empresarial en ese plazo.

## 11.1 MVP recomendado

**Objetivo:** demostrar el flujo completo desde GitHub hasta un inventario de dependencias y vulnerabilidades visualizable.

Alcance mínimo:

1. Conectar GitHub.
2. Seleccionar repositorios.
3. Iniciar análisis manual.
4. Ejecutar cdxgen de forma aislada.
5. Generar CycloneDX SBOM.
6. Parsear componentes y relaciones.
7. Guardar resultados en PostgreSQL.
8. Consultar OSV.
9. Mostrar inventario.
10. Mostrar vulnerabilidades.
11. Mostrar detalle de repositorio.
12. Mostrar grafo sencillo.
13. Guardar historial de análisis.
14. Comparar snapshots de forma básica.
15. Generar un reporte/exportación básica.

## 11.2 No incluir en las dos semanas

Salvo que el MVP ya esté terminado y exista capacidad real:

- RAG completo.
- Chatbot con acceso amplio a toda la plataforma.
- Integración con Notion/Confluence.
- Simulación que modifica repositorios.
- Creación automática de Pull Requests.
- Análisis real de compatibilidad entre versiones.
- Breaking changes inferidos sin fuente.
- Reportes programados.
- SSO empresarial.
- GitHub Enterprise Server.
- Multitud de ecosistemas.
- Microservicios distribuidos.
- Motor avanzado de políticas.

## 11.3 Priorización sugerida por fases

### Fase 1 — MVP, primeros 14 días

- GitHub App.
- Repositorios.
- Scanner.
- SBOM.
- Inventario.
- OSV.
- Vulnerabilidades.
- Dashboard.
- Primer Impact Graph.
- Historial.
- Reporte básico.

### Fase 2 — Documentation Intelligence

- Documentation Center.
- Carga y extracción de documentos.
- Asociación con repositorios y componentes.
- Indexación.
- pgvector.
- RAG acotado.
- AI Assistant con referencias.

### Fase 3 — Change Intelligence

- Simulación de cambios.
- Comparación de versiones.
- Breaking changes con fuentes.
- Análisis de impacto.
- Documentación potencialmente afectada.
- Notificaciones.

### Fase 4 — Enterprise Intelligence

- Reportes programados.
- Integraciones adicionales.
- Auditoría avanzada.
- Políticas.
- Análisis más avanzado.
- PR asistido y remediación con aprobación humana.
- SSO y capacidades empresariales según demanda validada.

---

# 12. Decisiones del MVP que quedaron pendientes

En una conversación se propusieron cinco decisiones para cerrar el MVP. El usuario todavía no confirmó expresamente las opciones:

1. **Ecosistemas:** Java/Maven + JavaScript/npm (recomendación inicial).
2. **Scanner:** cdxgen + OSV API.
3. **Alcance:** inventario + vulnerabilidades + historial.
4. **Grafo:** repositorio → dependencia → versión.
5. **Ejecución:** análisis manual + webhook push.

No presentar estas opciones como decisiones definitivas. Retomarlas y confirmarlas cuando se cierre el alcance.

---

# 13. Investigación pendiente

Estas son las áreas que se identificaron para investigar antes de afirmar capacidades o cerrar requisitos.

## 13.1 GitHub App

- Permisos mínimos.
- Acceso a repositorios públicos y privados.
- Instalación por organización.
- Tokens de instalación y duración.
- Webhooks y eventos útiles.
- Validación de firmas.
- Rate limits.
- Revocación.
- GitHub Enterprise.
- Qué archivos se necesitan realmente para el scanner.

## 13.2 Scanner y ecosistemas

- Comparar cdxgen y OSV-Scanner.
- Cobertura real de Maven y npm.
- Directas y transitivas.
- Lockfiles obligatorios u opcionales.
- Comportamiento sin lockfiles.
- Monorepos.
- Maven multi-module.
- Gradle.
- npm workspaces.
- Versiones dinámicas.
- Dependencias privadas.
- Dependencias descargadas desde registries privados.
- Diferencias entre SBOM generado y árbol resuelto por el gestor de paquetes.
- Reproducibilidad del análisis.

## 13.3 Vulnerabilidades

- Cobertura de OSV por ecosistema.
- Diferencia entre OSV ID y CVE.
- Severidad y CVSS.
- Rangos de versiones afectadas.
- Versiones fixed.
- Vulnerabilidades sin severidad.
- Duplicados y alias.
- Frecuencia de actualización.
- CISA KEV como fuente complementaria futura.
- Cómo mostrar incertidumbre o datos incompletos.

## 13.4 Impact Graph

- Qué relaciones incluye realmente CycloneDX.
- Cómo representar rutas transitivas.
- Cómo diferenciar dependencia directa y transitiva.
- Qué significa “impacto” y qué no.
- Cómo evitar confundir dependencias de paquetes con comunicación real entre microservicios.
- Qué otras fuentes harían falta para analizar arquitectura de ejecución.

## 13.5 Seguridad

- Aislamiento de scanner.
- Contenedores.
- Límites de recursos.
- Tokens y secretos.
- Archivos temporales.
- Datos almacenados.
- Aislamiento multi-tenant.
- Seguridad de webhooks.
- Prompt injection en documentos y repositorios.
- Seguridad de archivos PDF/DOCX.
- Permisos de IA.

## 13.6 Compatibilidad y cambios

- Changelogs.
- Release notes.
- SemVer y sus límites.
- Breaking changes documentados.
- Comparación de APIs.
- Compatibilidad binaria.
- Pruebas automatizadas.
- Diferencia entre una actualización con versión mayor y un breaking change real.
- Cómo justificar una recomendación con evidencia.

## 13.7 Enterprise readiness

- SSO.
- SAML/OIDC.
- GitHub Enterprise Server.
- Auditoría.
- Retención.
- Residencia de datos.
- Controles de acceso.
- Requisitos de vendor security.
- Exportación y cumplimiento.

## 13.8 Validación comercial

- Quién es el comprador.
- Quién usa el producto.
- Qué problema es suficientemente costoso para pagar.
- Diferenciación frente a Dependabot, Snyk, Sonatype y otras plataformas.
- Qué herramientas ya tiene el cliente.
- Por qué compraría un producto adicional.
- Precio y modelo de cobro.
- Segmento de empresa inicial.
- Proceso de venta y prueba piloto.

---

# 14. Competencia y diferenciación: hipótesis a validar

Se mencionaron como referencias competitivas o alternativas del mercado:

- GitHub Dependabot.
- Snyk.
- Sonatype.
- Otras herramientas de SCA, SBOM y gestión de vulnerabilidades.

No se realizó en este documento una investigación competitiva actualizada ni se deben atribuir capacidades específicas a esos productos sin comprobar sus fuentes oficiales.

La hipótesis de diferenciación de Dependency Intelligence no debe limitarse a “también detectamos vulnerabilidades”. El valor potencial estaría en conectar:

- Inventario organizacional.
- Relaciones y rutas de dependencias.
- Vulnerabilidades.
- Historial.
- Documentación interna.
- Análisis de cambios.
- Explicaciones y reportes con evidencia.

Hay que demostrar que esa combinación resuelve un problema que las herramientas existentes no resuelven bien para el segmento elegido.

---

# 15. Principios de producto que no se deben perder

1. **La evidencia es más importante que una respuesta convincente.**
2. No presentar una inferencia como hecho.
3. No declarar que un servicio se rompe sin pruebas.
4. No declarar una actualización segura sin evidencia.
5. Las vulnerabilidades deben proceder de fuentes identificables.
6. Mantener commit, versión, fecha y procedencia.
7. Diferenciar inventario actual de snapshots históricos.
8. Separar datos estructurados de documentación semántica.
9. La IA debe explicar y ayudar a explorar, no sustituir el motor determinista.
10. Respetar permisos y aislamiento de organización.
11. Diseñar el MVP para demostrar valor de extremo a extremo.
12. Evitar construir funciones empresariales avanzadas antes de validar el problema comercial.
13. No ejecutar código no confiable del repositorio sin aislamiento y controles.
14. No confundir SBOM con un mapa de comunicación en tiempo de ejecución.
15. Cada recomendación debe incluir fuente, evidencia, incertidumbre y limitaciones.

---

# 16. Contexto del desarrollador y restricciones de ejecución

Este contexto ayuda a adaptar futuras explicaciones y planes técnicos:

- Tecnologías conocidas/practicadas: Java, Spring Boot, PostgreSQL, Angular y TypeScript.
- Ha trabajado conceptos de arquitectura hexagonal, DDD, APIs REST, Git Flow y organización por capas.
- Busca profundizar en arquitectura empresarial, no solo construir pantallas.
- Prefiere respuestas concretas, técnicas y objetivas.
- Le sirven entregables como:
  - SRS.
  - Historias de usuario.
  - Criterios de aceptación.
  - Backlog priorizado.
  - Diagramas de arquitectura.
  - Modelo entidad-relación.
  - Endpoints REST.
  - Contratos OpenAPI.
  - Plan de implementación.
  - Estrategia de pruebas.
  - Decisiones técnicas justificadas.
- No quiere propuestas genéricas ni funcionalidades simples sin diferenciación.
- Tiene un plazo aproximado de dos semanas para producir un primer MVP.
- Busca potencial de comercialización B2B, especialmente en Estados Unidos.

---

# 17. Estado actual del trabajo

## Ya conversado/propuesto

- Concepto y posicionamiento de Dependency Intelligence.
- Problema que busca resolver.
- Módulos funcionales principales.
- Borrador de requisitos funcionales RF-001 a RF-091.
- Categorías de requisitos no funcionales.
- Pantallas y navegación.
- Integración GitHub.
- cdxgen y CycloneDX.
- OSV API.
- PostgreSQL.
- Cytoscape.js.
- Arquitectura modular/hexagonal.
- Inventario y vulnerabilidades.
- Dependency Graph / Impact Graph.
- Historial y snapshots.
- Documentación técnica.
- IA contextual con RAG.
- Change Impact Analysis.
- Reporting Center.
- Roadmap por cuatro fases.
- Advertencias de seguridad y límites de la IA.

## No está confirmado o implementado según el contexto disponible

- No se han confirmado las cinco decisiones del MVP.
- No existe aquí un SRS formal aprobado.
- No existe un esquema SQL definitivo.
- No existe un contrato OpenAPI definitivo.
- No se ha demostrado una integración GitHub funcional.
- No se ha ejecutado cdxgen contra repositorios reales en el contexto disponible.
- No se ha validado la cobertura real de OSV para los ecosistemas objetivo.
- No se ha implementado el grafo.
- No se ha implementado el asistente IA.
- No se ha implementado el centro documental.
- No se ha implementado Change Impact.
- No se ha validado comercialmente el producto.
- No se ha completado un análisis competitivo actualizado.
- Los mockups/imágenes vistos anteriormente eran ilustrativos, no interfaces finales ni resultados reales.

---

# 18. Próximos pasos recomendados para continuar en la nueva cuenta

Orden sugerido para no perder tiempo:

## Paso 1 — Cerrar el MVP

Confirmar:
- Ecosistemas iniciales.
- Herramienta de SBOM.
- Fuente de vulnerabilidades.
- Tipo de análisis.
- Alcance del grafo.
- Qué queda fuera de las dos semanas.

## Paso 2 — Investigar las integraciones críticas

Prioridad:
1. GitHub App.
2. cdxgen para Maven/npm.
3. CycloneDX.
4. OSV API.

Probar cada integración con repositorios reales de prueba, no diseñar únicamente desde documentación.

## Paso 3 — Crear el SRS v1

Convertir los requisitos en:
- Identificador.
- Descripción.
- Prioridad (Must/Should/Could/Won't).
- Historia de usuario.
- Criterios de aceptación.
- Dependencias.
- Riesgos.
- Estado (por investigar, definido, implementado, probado).

## Paso 4 — Diseñar el modelo de datos

Definir entidades, relaciones, restricciones, índices y estrategia multi-tenant. Mantener los snapshots de análisis como unidades trazables.

## Paso 5 — Definir contratos API

Crear OpenAPI para:
- GitHub.
- Repositorios.
- Scans.
- SBOM.
- Componentes.
- Vulnerabilidades.
- Grafo.
- Historial.

## Paso 6 — Construir el flujo vertical

Priorizar un flujo que funcione de principio a fin:

**Conectar repositorio → analizar commit → generar SBOM → persistir dependencias → consultar OSV → mostrar resultados.**

## Paso 7 — Añadir visualización y reporte

Dashboard, detalle de repositorio, inventario, vulnerabilidades, grafo simple e informe exportable.

## Paso 8 — Validar diferenciación y mercado

Comparar con alternativas existentes y entrevistar potenciales usuarios/compradores antes de construir IA avanzada o funciones empresariales complejas.

---

# 19. Instrucciones para el siguiente ChatGPT

Al continuar este proyecto, utiliza este archivo como contexto maestro.

1. No vuelvas a empezar la ideación desde cero.
2. No vuelvas a preguntar qué es Dependency Intelligence: ya está definido en este documento.
3. Mantén la distinción entre MVP de dos semanas y visión completa.
4. No asumas que las propuestas pendientes ya fueron aprobadas.
5. Si se necesitan datos actuales de GitHub, OSV, cdxgen, CycloneDX, deps.dev, competidores o precios, investiga fuentes oficiales actualizadas.
6. No inventes cobertura, límites, endpoints ni capacidades de proveedores.
7. No recomiendes construir un chatbot genérico como prioridad.
8. La IA debe trabajar sobre datos y documentos recuperados con permisos, y responder con evidencia.
9. No uses embeddings para reemplazar el modelo relacional de dependencias y vulnerabilidades.
10. No afirmes impacto real cuando solo hay una relación potencial.
11. Prioriza entregables implementables: SRS, backlog, modelo de datos, endpoints, diagramas y plan de desarrollo.
12. Mantén el lenguaje técnico claro y explica las decisiones arquitectónicas.
13. Cuando el usuario pida avanzar, utiliza el contexto ya disponible y evita hacerle repetir decisiones o antecedentes.

**Punto de continuación recomendado:** cerrar el alcance exacto del MVP y convertir los requisitos RF/RNF en un backlog priorizado con historias de usuario y criterios de aceptación. Después, diseñar el modelo de datos y los contratos API del flujo vertical de análisis.
