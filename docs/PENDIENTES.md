## Estado visual vigente — Observatorio de Evidencias (25/11/2026)

- [x] Permitir descartar avisos del panel de notificaciones con papelera individual o «Borrar todo», guardado por cuenta en este navegador. Los avisos se derivan de OSV; la fuente no se borra. Pendiente revisión visual autenticada del ajuste compacto.
- [ ] Si se requiere sincronía entre dispositivos, persistir el descarte de avisos por usuario en backend y comprobarlo con cuentas reales.
- [x] Retirar las barras de atajos internos `page-jump` de seis ventanas; se conservaron acciones reales y navegación principal. Frontend 8189 actualizado; falta revisión visual autenticada.
- [x] Compactar la conexión GitHub y dar ancho completo a «Importar carpeta local». El selector de carpeta usa un control visual propio y las carpetas importadas tienen acciones más claras. Frontend 8189 recompilado; queda la revisión visual autenticada por el usuario.
- [ ] Identificar la petición API que muestra «Tu cuenta no tiene permiso para esta acción» en Dinamita. El estado GitHub permite mostrar el formulario, pero la captura no contiene método ni URL de la respuesta fallida; revisar Red/Network con la sesión autenticada antes de cambiar permisos.
- [x] Integrar los cinco bloques de la guía «PORFAVOR» en Angular. La paleta, el alcance de las vistas y las variantes están en [DESIGN.md](../DESIGN.md).
- [x] Recorrer las 11 vistas internas en escritorio (1440 px) y móvil (390 px) con autenticación real en Compose aislado. Probar el foco de búsqueda, menú y detalle de inventario, además de temas claro y oscuro.
- [x] Verificar registro, solicitud explícita del enlace desde el panel, verificación, aprobación administrativa e inicio de sesión; también rechazo/auditoría e invitaciones en Mailpit.
- [x] Probar carpeta local, ejecución npm, estado Maven sin manifiesto, comparación de snapshots, inventario, reportes y biblioteca en el entorno aislado.
- [ ] Actualizar en GitHub Developer Settings el Homepage URL, Setup URL y User authorization callback URL a `localhost:8189`; después volver a probar consentimiento e instalación. Google OAuth sigue desactivado.
- [ ] Validar Nexo con un proveedor configurado, recuperación de un documento preparado y apertura de su cita desde la respuesta. Se comprobó el rechazo de un repositorio ajeno y el estado sin evidencia.
- [ ] Continuar la revisión en `localhost:8189`, ahora conectada al backend y la base de datos anteriores. Falta actualizar Homepage/Setup/Callback en la GitHub App; Mailpit de desarrollo está en 8126.
- [ ] Reducir los avisos de presupuesto de Angular (objetivo de bundle inicial: 500 KB y de hojas Sass: 4 KB).

## Bloque 91 — prompt directo del usuario (24/11/2026; en curso)

Este es el bloque histórico del prompt de exposición y análisis. La restricción de no tocar Vulnerabilidades aplicaba a ese alcance funcional; el bloque 107 autorizó cambios visuales allí sin alterar funcionalidades.
**Decisión vigente:** se descarta la actualización upstream automática cada 12 horas. El análisis sigue iniciándose manualmente; no hay código que sincronice releases/npm/Maven en segundo plano.

- [ ] Exposición de código: enlazar declaraciones npm transitivas con `package-lock.json`, localizar referencias a paquetes en fuentes y conservar como inconclusa cualquier relación OSV→API sin regla validada. Falta reproducir con el snapshot real del usuario.
- [x] Comparar snapshots: nombres editables asociados a cada ejecución y numeración cronológica (`#1`, `#2`...) en historial, selectores, resumen y campos de edición directa para análisis base/objetivo. Persiste en base de datos; falta aplicar migración V33 al entorno para habilitarlo allí.
- [x] Análisis: ventana de carga con rueda personalizada, mensajes por etapas confirmadas (fuente, Maven, npm, resultados y OSV), cancelación y movimiento reducido; compilación Angular 0 diagnósticos. Pendiente revisión visual con análisis real en la instancia.
- [x] Inventario: misma ventana de carga al analizar repositorio, con progreso por lectura, detección de dependencias Maven/npm, resultados y evaluación OSV; fondo inerte, foco atrapado y cancelación. Frontend Compose reconstruido/activo en 8089; HTTP 200 y bundle contiene la ventana. Revisión visual pendiente porque el puente de navegador falló.
- [ ] Procedencia de Inventario/Comparar: repositorio visible, revisión/fecha e IDs cortos; explicar SHA-256 como huella del código analizado y ofrecer su valor completo para comparación.
- [ ] Explicar cobertura parcial, puntuación/severidad y límites sin afirmar cobertura que el scanner no demuestra.
- [ ] Ampliar Documentación a fuentes oficiales y foros de lenguajes/dependencias con origen verificable y privacidad clara.
- [x] OCR Tika/Tesseract activado en la JVM aislada: `AUTO`, inglés/español y hasta 50 páginas OCR. El texto extraído queda disponible para Nexo tras preparar los embeddings; la UI procesa lotes sucesivos automáticamente y avisa si quedan pendientes por fallos o por alcanzar su límite. Separada queda la verificación de un PDF escaneado real en runtime Compose.
- [ ] Validar de extremo a extremo con un PDF escaneado de prueba en Compose: texto reconocible en la biblioteca, embeddings completados y respuesta de Nexo con cita.
- [ ] Extender embeddings/RAG a datos autorizados de módulos, análisis, inventario, hallazgos, organización y reportes, conservando filtros tenant/repositorio y citas.
- [x] Dar nombre al asistente: Nexo. Pendiente vincularlo a evidencia legible sobre acciones, versiones, snapshots y responsables.
- [x] Reportes ejecutivos/técnicos limitados a hallazgos de severidad conocida: el ejecutivo explica prioridad y siguiente paso para cada paquete/version (incluidas severidades media y baja); el técnico incluye versión afectada, resumen y detalle OSV, versión corregida y referencia. JSON y CSV reflejan el alcance. TypeScript y compilador Angular sin emisión: 0 diagnósticos; no se pudo correr npm build ni comprobar el runtime en esta sesión. Ver [bitácora, bloque 93](BITACORA_TRABAJO.md).

Avance comprobado al registrar el bloque 92: exposición npm distingue declaración del lockfile y referencias reales; prueba focal 7/7. Backend empaquetado, Angular `tsc`/`ngc` y bundle pasan; el bundle presenta los avisos de presupuesto registrados. Los IDs visibles bajaron a 8 caracteres. No se actualizó la instancia en `localhost:8088` porque no fue accesible desde esta sesión, y no se reinició ningún servicio. Sigue pendiente probar el snapshot real, confirmar la UI servida y completar los demás puntos del prompt; ver [bitácora, bloque 92](BITACORA_TRABAJO.md). No se tocó Vulnerabilidades.

Actualización del bloque 101: el parser npm acepta calificadores/subrutas PURL válidos al verificar la versión; el fallback seguro del lockfile permanece. El mensaje de cobertura distingue SBOM vacío de componentes sin coordenadas verificables. Backend empaquetado correctamente con pruebas omitidas. Pendiente desplegar el backend en el entorno acordado y reanalizar el snapshot para confirmar si esta era la causa del caso reportado; aún no se afirma resuelto en runtime. El arreglo adicional del endpoint de etiqueta de snapshot elimina un error de compilación y mantiene el contrato 204. Ver [bitácora, bloque 101](BITACORA_TRABAJO.md).

### Estado vigente — OpenAI y harness de solo lectura (23/11/2026)

OpenAI reemplaza a Gemini para generación; Ollama conserva embeddings y pgvector la recuperación. El adaptador de OpenAI usa salida estructurada y no ofrece herramientas para cambiar repositorios. Solo devuelve diagnóstico y un prompt de reparación para revisión humana. La clave `OPENAI_API_KEY` está vacía en `.env` local; configura tu propia clave allí, nunca en el chat. Los siguientes registros de Gemini son históricos y quedaron sustituidos por el bloque 79. Ver [controles OWASP](ASISTENTE_IA_SEGURIDAD_OWASP.md).
## Bloque 84 — asistente y notificaciones en el encabezado

- [x] Mover el acceso y el panel fijo del asistente al shell global; retirar la tarjeta duplicada del Dashboard y conservar el movimiento izquierda/derecha.
- [x] Enviar pregunta con Enter y dejar Shift+Enter para salto de línea.
- [x] Consultar notificaciones contra repositorios accesibles y evaluaciones OSV guardadas; separar versiones afectadas, registros oficiales OSV modificados y evaluaciones que requieren revisión.
- [x] Informar fallos parciales y no mostrarlos como ausencia de avisos.
- [ ] Validar el recorrido visual y la lista con sesión autenticada y repositorios reales.
- [ ] Añadir comparación programada con releases vigentes de npm/Maven y persistencia de vistos/lecturas si se requiere notificación histórica.
- [ ] Integrar seguimiento de documentos oficiales externos, si se espera detección fuera de la base OSV ya consultada.

Los cambios y límites comprobados están registrados en el [contexto maestro, bloque 84](DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md) y en la bitácora.

## Estado runtime de la petición 10 (24/11/2026)

- [x] Imagen y contenedor Ollama `0.40.17` corriendo en Compose, aislados y sin puerto publicado; modelo guardado en `ollama_data`.
- [x] Desde el backend, `/api/tags` coincidió con el digest fijado y `/api/embed` respondió HTTP 200 con 768 valores finitos.
- [x] V29 aplicada sobre la base existente; pgvector 0.8.7, tabla vectorial con RLS forzado y permisos de `dependency_app` verificados. Respaldo previo en `%TEMP%\dependency-intelligence-pre-v29.dump`.
- [x] Frontend y backend activos en `localhost:8089`; health `UP`; ruta Asistente HTTP 200.
- [x] `GEMINI_API_KEY` configurada solo en `.env`; `gemini-3.5-flash-lite` respondió HTTP 200 con candidato desde el backend.
- [ ] Validar recuperación/citas con documentos autorizados cuando el usuario desee importarlos; sin documentos, el asistente se abstiene y los módulos siguen utilizables.
## Portabilidad de Ollama en Compose (23/11/2026)

- [x] Se retiro la dependencia de Ollama instalado en Windows y de reglas de Firewall por equipo.
- [x] Compose define servicio Ollama interno, volumen persistente para modelos y un descargador de modelo en el primer arranque.
- [x] La descarga de IA no bloquea el backend ni los modulos normales si hay Internet lento o ausente.
- [x] Descargar y verificar `ollama/ollama:0.40.17`; imagen de 8.52 GB extraída y modelo instalado en `ollama_data`.
- [x] Desde el backend, verificar `/api/tags`, digest, `/api/embed` y vector de 768 dimensiones.
## Bloque 75 — Petición 10: IA documental (23/11/2026; implementación inicial, no validada)

- [x] Contratos hexagonales y casos de uso para indexación y preguntas con recuperación de evidencia.
- [x] Adaptadores REST Gemini y Ollama; modelo/revisión vienen de configuración externa, la dimensión se detecta y se valida en runtime.
- [x] Migración V29 agrega pgvector, dimensión flexible y RLS en la tabla vectorial; Compose construye pgvector 0.8.7 sobre la misma base postgres:17.7-alpine.
- [x] API y pantalla de asistente, aislamiento por organización/repositorio, `assistant.query` + `document.read`, citas, abstención sin evidencia y error explícito de proveedor no disponible.
- [x] Instalar Ollama 0.40.17, descargar `embeddinggemma-2:latest` y comprobar `/api/tags` y `/api/embed`: digest `f4981a15b99858ad1f2e0729416e526be7b4f428291b40082b9e4f7b27b5f62f`, vector de 768 dimensiones con valores finitos. Nombre/digest guardados en `.env` local, ignorado por Git.
- [x] Despliegue portable en Compose verificado en runtime: Ollama aislado, modelo persistido en `ollama_data`, sin puerto ni Firewall del anfitrión.
- [x] Configurar `GEMINI_API_KEY` en `.env`; `gemini-3.5-flash-lite` probado con respuesta HTTP 200.
- [x] Aplicar V29 en Compose y comprobar pgvector/RLS/runtime grants; migración aplicada correctamente a la base existente.
- [ ] Ejecutar regresiones focalizadas: permisos tenant/repositorio, dimensión incorrecta, persistencia/citas, abstención y fallos Ollama/Gemini. La compilación Java/Angular pasó; aún no se escribieron ni ejecutaron pruebas nuevas.
- [ ] Indexación durable con progreso/reintentos, listado/recarga de conversaciones, evaluación de calidad y recorrido autenticado de extremo a extremo; validar UI móvil/desktop, tema e idioma.
- [x] Explicar la integración, el almacenamiento vectorial, el flujo RAG y los límites de red en [OLLAMA_PGVECTOR_RAG](OLLAMA_PGVECTOR_RAG.md).

La indexación HTTP actual procesa hasta 32 fragmentos por llamada. Ollama, pgvector y la generación Gemini están activos y comprobados. La importación de documentos es opcional; la recuperación con citas se valida cuando el usuario decida cargar documentos autorizados.

## Bloque 74 — petición 9: Centro de documentación (23/11/2026)

- [x] Biblioteca responsive por organización con selección de espacio y permisos en el backend.
- [x] Carga de archivos privados de hasta 50 MiB: PDF, DOCX, Markdown, TXT, README, OpenAPI, ADR y CHANGELOG.
- [x] Extracción aislada en proceso hijo con Apache Tika, versión fijada, límites de memoria/tiempo y máximo de páginas PDF.
- [x] Asociación a repositorios accesibles, historial de versiones, descarga con autorización, SHA-256, fragmentación e índice de búsqueda de texto completo.
- [x] Eliminación explícita del original privado, versiones, fragmentos y metadatos de embeddings/evidencia asociados; la solicitud permanece reintentable si el borrado físico falla.
- [x] Integración con `document.read`/`document.manage`, preferencias de idioma y tokens responsive/accesibles del tema.
- [ ] Asociar documentos con componentes/versiones y filtrar documentos por componente.
- [ ] Implementar configuración y ejecución automática de las políticas de retención ya modeladas en V9.
- [x] La biblioteca expone el estado de texto extraído; Tika OCR se configura explícitamente y Nexo puede recuperar ese texto tras indexarlo. Validación con PDF escaneado real en Compose pendiente.
- [ ] Implementar la fase semántica del siguiente bloque: definir modelo/dimensión de embeddings de Ollama, vector store, recuperación con citas y orquestación Gemini. No asumir modelo ni habilitar pgvector antes de fijar esa configuración.
- [ ] Recorrido autenticado de carga/búsqueda/versionado/descarga/eliminación en Compose después de bootstrap del administrador; la base actual está vacía y no permite login funcional.

Ver detalle, arquitectura y límites en el [contexto maestro, bloque 74](DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md) y en la [bitácora](BITACORA_TRABAJO.md).
## Bloque 73 — petición 8: Análisis (23 de noviembre de 2026)

- [x] Añadir pantalla propia a Análisis para seleccionar repositorio, ejecutar Maven/npm y ver resultados.
- [x] Consultar historial protegido por `scan.read`, con hasta 50 ejecuciones, commit, scanner, duración, cobertura, conteos, motivo de fallo y SHA-256.
- [x] Guardar como `FAILED` los errores de lectura de manifiestos sin reemplazar snapshots válidos; poder reintentar desde UI.
- [x] Pedir la evaluación OSV después del scanner, manteniéndola como etapa distinta.
- [x] Compilar backend/frontend y actualizar ambos servicios Compose sin tocar PostgreSQL, scanner, Mailpit o instancia nativa.
- [ ] Validar con sesión autorizada que los repositorios reales muestran el historial y que el análisis Maven/npm finaliza; Compose espera el bootstrap inicial del administrador.
- [ ] Worker durable, progreso por etapas, comparación de snapshots y persistencia trazable de errores al abrir la fuente.

El estado y límites verificados están descritos en el bloque 73 del [contexto maestro](DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md).

## Bloque 72 — unificar análisis recientes y cobertura del Dashboard (23 de noviembre de 2026)

- [x] Quitar el panel duplicado de cobertura y mostrar los contadores completo/parcial dentro de Análisis recientes.
- [x] Añadir elementos sin resolver a cada snapshot, conservando ecosistema, versiones, estado y fecha.
- [x] Reconstruir y actualizar solo el frontend de Compose; la vista servida responde HTTP 200.
- [ ] Revisión visual del usuario en localhost.

## Bloque 71 — limpiar restos del panel y publicar Dashboard en Compose (23 de noviembre de 2026)

- [x] Retirar del diccionario de idiomas los últimos textos del panel de tendencia eliminado.
- [x] Reconstruir y actualizar solo el contenedor frontend en `localhost:8089`; backend, PostgreSQL, cdxgen y Mailpit permanecieron activos.
- [x] Verificar HTTP 200 y el chunk Dashboard servido: sin grafo, sin panel de tendencia y con el contador de versiones de dependencias conservado.
- [ ] Revisión visual del usuario con su sesión y preferencias.

## Bloque 70 — Dashboard (23 de noviembre de 2026)

- [x] Completar métricas de repositorios, versiones únicas, cobertura y riesgo con datos reales del workspace, scanner y OSV.
- [x] Mostrar los repositorios autorizados en contexto de OWNER y MEMBER, con acceso directo al inventario del recurso seleccionado.
- [x] Distinguir Maven/npm y cobertura completa/parcial; quitar el panel de tendencia sin datos.
- [ ] Revisión interactiva por el usuario con sesión OWNER y MEMBER, y confirmación en modo oscuro, inglés y móvil.

La implementación está en el bloque 70 del [contexto maestro](DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md). No se afirma validación visual con una sesión real.

- [ ] Revisar la experiencia con la sesión real del usuario en OMEGA y Bot de WhatsApp. Los snapshots existentes se consultaron en solo lectura.
- [ ] Petición 12: historial y comparación de la presencia de un paquete entre análisis.

Actualización del bloque 65 (22 de noviembre de 2026). Las correcciones de la petición 4 ya usan una sola fuente por análisis y reproducen la evidencia OSV del scan mostrado. Se acordó un límite de 300 MiB y 50.000 archivos.

- [x] Fijar una fuente única por acción, asociar la evidencia OSV al `scanId` y filtrar las versiones corregidas por paquete y ecosistema.
- [x] Conservar el último inventario válido al fallar un reanálisis. Conciliar omisiones de CycloneDX con POM y lockfiles y distinguir ausencia de manifiesto, cobertura y fallo.
- [ ] Confirmar la presentación interactiva en los repositorios reales del usuario. Maven mantiene cobertura parcial para padres, BOM remotos y transitivas sin evidencia local.

Actualización del bloque 63 (22 de noviembre de 2026). **Petición 3 cerrada en requisitos.** El [contexto maestro](DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md) recoge importación, permisos, procedencia y retención de las dos fuentes. La descarga e importación confirmadas en Dinamita corresponden a la validación anterior.

- [ ] Completar la desconexión explícita de GitHub y procesar webhooks firmados e idempotentes. Mantener sincronización manual y mostrar cuándo no se pudo verificar el acceso remoto.
- [ ] Revisar permisos al volver a agregar un repositorio GitHub. Exponer las acciones por repositorio acordes a los permisos efectivos, también para cuentas `MEMBER` con `repository.manage` sobre el recurso.
- [ ] Validar retiro y revocación real, reconexión y accesos por equipo en ambas fuentes. Comprobar el flujo desde otro dispositivo y una carga cercana a 50.000 archivos. Consolidar plazos y respaldo del ZIP local en la petición 15.

Actualización del bloque 62 (22 de noviembre de 2026): auditoría de Identity/Organization y autorización. Confirmado: administrador global separado de roles de organización; cuenta OWNER creada por registro abierto con admisión pendiente; invitación crea MEMBER, requiere correo verificado, aceptación y aprobación manual del propietario invitante. Roles organizativos OWNER/ADMIN/MEMBER/VIEWER; MEMBER puede recibir `repository.manage` limitado por equipo/repositorio. ADMIN puede gestionar acceso general pero la API restringe invitaciones/aprobaciones al OWNER. Google/GitHub login y vinculación existen en código; Compose tiene credenciales deshabilitadas según el estado operativo.

Confirmado: cuentas MEMBER tendrán Dashboard limitado a repositorios asignados. Para MEMBER con `repository.manage` sobre un repo, la tabla mostrará «Administradora del repositorio A» sin cambiar su rol ni permisos de organización. **Petición 2 cerrada en requisitos; cambios de UI quedan para la fase de pulido.**

Actualización del bloque 61 (22 de noviembre de 2026): **petición 1 del plan de 15 cerrada**. Propósito del producto, objetivo del producto completo por fases (local primero, servidor después), alcance del primer piloto, exclusión de la antigua demo de dos semanas y criterio de “listo para usar” quedaron consolidados. El público prioritario son por igual responsables/líderes técnicos y seguridad/DevSecOps.

El plan ahora enumera las 15 peticiones y especifica la fase de IA documental: embeddings Ollama, dimensión/modelo por decidir, pgvector con aislamiento tenant, RAG con citas, orquestador y chat asistente Gemini con permisos/evaluación. No se han activado pgvector ni proveedores de IA.

Actualización del bloque 71 (23 de noviembre de 2026): Vulnerabilidades ahora tiene página propia, lista OSV global por repositorios accesibles, filtros combinables, referencias, versiones corregidas y estado de revisión persistente. Análisis, Documentación y Reportes siguen pendientes; falta revisión visual y funcional con los avisos reales del usuario; Compose está en V28.


Siguiente paso cuando se cierre la revisión documental: acordar el orden y los criterios de aceptación de las cuatro pantallas; no iniciar todavía una implementación por inferencia.

Actualización del bloque 59 (22 de noviembre de 2026): se amplió el fallback Maven a padres, propiedades, gestión de dependencias y BOM locales, y se reorganizó el inventario. El fallback pasó 3 pruebas; lockfile npm y adaptador cdxgen pasaron 1 y 2. El build frontend compila. Falta reanalizar OMEGA desde una sesión autenticada y revisar visualmente el flujo, sobre todo los estados Maven, antes de dar por confirmada la mejora con datos reales.

## Bloque 59 — resolver Maven local y aclarar Inventario

Implementado: lectura estática y acotada de POM padre, propiedades y BOM disponibles en la fuente del repositorio. La UI diferencia alcance de análisis y vulnerabilidades, ofrece filtros rápidos y avanzados, buscador y conteo de resultados. Mensaje neutral de finalización.

Pendiente de validar con usuario: reanalizar OMEGA y confirmar cuántas dependencias aparecen ahora; revisar claro/oscuro y móvil/escritorio. Límites confirmados: POM/BOM remotos, resolución transitiva Maven no incluida en SBOM y perfiles de construcción no resueltos.

# Pendientes de Dependency Intelligence

Actualizado: 22 de noviembre de 2026, bloque 58.

## Bloque 58 — Scanner Maven y OSV (fallback implementado; runtime pendiente)

**Comprobado en Compose:** OMEGA Maven tenía 1 POM detectado pero 0 componentes; npm conserva 270 componentes en OMEGA y 291 en Bot de WhatsApp. OSV guardó evaluaciones asociadas a esos scans; Bot tiene 11 resultados `MATCHED` con avisos ligados a paquetes y versiones, OMEGA no reporta avisos en las 270 versiones evaluadas.

**Implementado y probado:** si cdxgen devuelve SBOM Maven vacío, el backend recupera declaraciones con versión exacta desde POM, informa `PARTIAL` y conserva pendientes sin inventar versiones ni afirmar cobertura transitiva sin evidencia. Pasaron las pruebas focales del fallback y de asociación/persistencia OSV.

**Pendiente:** reanalizar OMEGA en Inventario y confirmar que el inventario Maven y su evaluación OSV aparecen en la UI. El backend Compose ya corre el JAR actualizado; PostgreSQL, Mailpit, cdxgen y el entorno nativo siguen activos. Maven transitivo, versiones gestionadas sin evidencia local, Maven v4 y ecosistemas adicionales siguen fuera de cobertura garantizada.
## Bloque 56 — restaurar Inventario y separar scanner de OSV (implementado; reanálisis autenticado pendiente)

La inspección agregada de la base activa encontró tres scans `cdxgen-npm` guardados como completos con un manifiesto y cero componentes. Además, la consulta OSV solo seleccionaba `maven-manifest`/`npm-lockfile`, por lo que dejaba fuera los scans actuales `cdxgen-maven`/`cdxgen-npm`. Se añadió recuperación npm acotada desde `package-lock.json` si cdxgen detecta el lockfile pero emite cero componentes; esa recuperación se marca parcial. Se cambió la consulta OSV para tomar el scan más reciente por ecosistema entre scanner actual e histórico, sin duplicarlos. La UI muestra errores del endpoint, informa si falta respuesta OSV y ofrece **Consultar OSV** sin volver a ejecutar cdxgen.

**Verificado:** 11 pruebas focales (scanner, mapper y OSV) pasan; el IT con PostgreSQL desechable confirma selección de scans legacy/nuevos y evita duplicados. Build Angular y build Docker backend/frontend correctos; `localhost:8089` responde HTTP 200 y cdxgen sigue healthy. Base de datos, Mailpit y volúmenes no se reiniciaron ni modificaron.

**Pendiente:** volver a ejecutar **Analizar repositorio** en los repositorios afectados para reemplazar los snapshots npm vacíos ya guardados. La sesión autenticada del usuario no se usó para lanzar análisis ni se consultó OSV en vivo. Scanner genera/guarda el inventario; OSV evalúa las versiones ya guardadas y puede volver a consultarse de forma independiente.

## Bloque 55 — scanner de dependencias y cobertura OSV (implementado; recorrido autenticado pendiente)



**Pendiente:** recorrido autenticado desde Inventario sobre repositorios reales; confirmar en la UI que se actualizan inventario, relaciones y vulnerabilidades después del análisis, y observar el comportamiento con un error real de OSV. Maven transitivo sigue limitado por el modo seguro: la cobertura se informa parcial y no se afirman relaciones no emitidas por el SBOM. Ver [scanner](../infra/scanner/README.md) y [bitácora](BITACORA_TRABAJO.md).

Ver [scanner](../infra/scanner/README.md) y [bitácora](BITACORA_TRABAJO.md).



Actualizado: 20 de noviembre de 2026, bloque 51 (mapa de dependencias por capas).

**Estado del entorno y datos:** no se consultó la base de datos activa durante el bloque 52, por lo que el contenido actual de repositorios/scans debe confirmarse en la instancia local al abrir la plataforma. La muestra de npm permanece en `samples/npm-axios-demo` (Axios directo + ocho paquetes transitivos en lockfile v3); no se afirma que Omega/Bot de WhatsApp estén importados o analizados desde esta sesión.


Bloque 35: el botón principal detecta una instalación única y lleva directamente a OAuth/PKCE; el navegador recibe `index.html` sin caché y recarga la pestaña restaurada al volver desde GitHub. El propietario completó el recorrido. La base registra la instalación y dos repositorios; la descarga ZIP funcionó en la vista del usuario.

**Instrucción vigente:** continuar por bloques funcionales aprobados y registrar cada entrega en esta lista y en la bitácora. El bootstrap seguro ya está implementado; sincronizar el estado de GitHub requiere pulsar «Sincronizar acceso con GitHub» o volver a cargar la vista de repositorios. No hay webhook ni sincronización en segundo plano en este bloque.

Este archivo reúne configuración pendiente, validaciones de lo entregado y funciones por implementar. El alcance y las decisiones técnicas se mantienen en el [contexto maestro](DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md), la [arquitectura](ARQUITECTURA_BACKEND.md), la [especificación database](CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md) y el [stack](STACK_TECNOLOGICO_DEPENDENCY_INTELLIGENCE.md). No hay fecha total nueva comprometida.

## Estado del bloque 26 - Configuracion de organizacion

Tema e idioma compartidos implementados de extremo a extremo: V18 crea `organization_settings` con valores por defecto, seguridad RLS y permisos runtime; el alta de organizaciones incluye la fila inicial. La API deja leer a miembros activos y solo OWNER/ADMIN pueden editar. Angular aplica la preferencia del espacio en la sesion, conserva preferencias personales para login/registro y ofrece controles en Configuracion. V18 ya esta en PostgreSQL local: 126 tablas, 89 con FORCE RLS y 223 FK. Build Angular y backend correctos; el IT focalizado queda pendiente porque Maven Central no resolvio DNS desde el contenedor de prueba. Servicios de revision activos en http://localhost:8089/.
## Lo que ya está implementado

- Bootstrap inicial seguro implementado: comando interactivo local sin endpoint HTTP, transaccion de un solo uso, restriccion a base sin usuarios/organizaciones/admins, hash PBKDF2, cuenta OWNER aprobada/verificada y auditoria. Compose ya cuenta con un administrador activo. Ver [ACCESO_LOCAL](ACCESO_LOCAL.md).

- Backend separado en Domain, Application e Infrastructure; database V1-V24 aplicada y verificada en PostgreSQL Compose: 128 tablas de negocio, 91 con FORCE RLS y 227 FK. V24 permite registrar hasta 50.000 archivos por fuente local, preservando el máximo de 100 MiB del contenido.
- Acceso local, sesiones/CSRF, creación/listado de organizaciones y autorización de repositorios/scans por rol/equipo.
- Verificación de correo y recuperación de contraseña con SMTP/Mailpit, tokens de un uso y revocación de sesiones.
- Admisión de responsables: aviso individual a los administradores verificados con aprobar/rechazar inmediato, tokens de un uso y caducidad. La pantalla persistente «Esperando confirmación» se mantiene. Verificar el recorrido en Mailpit.
- Google/GitHub y vinculación/desvinculación segura en backend y Angular. La GitHub App de repositorios ya tiene credenciales locales y consentimiento real en Dinamita; el login social de Google/GitHub aún requiere su propia configuración y comprobación real.
- Parser CycloneDX Maven acotado e importación persistente; todavía sin flujo de análisis real completo.
- Importación local de carpetas al almacenamiento privado y registro tenant-scoped del repositorio. El proceso no ejecuta todavía un escaneo automático.
- Paleta del usuario aplicada mediante tokens y Angular Material a las pantallas existentes.
- Invitaciones SMTP, aceptación, gestión de membresías/roles, equipos y permisos por repositorio en backend/Angular. Probados con PostgreSQL y Mailpit; revisión visual conjunta pendiente. [Recorrido y contratos](INVITACIONES_EQUIPOS_PERMISOS.md).
- Rediseño según las tres imágenes del usuario: acceso/registro, shell lateral, dashboard con estados vacíos y organización con tabla, filtros, pestañas y diálogos. Build correcto; falta aceptación visual del usuario. [Diseño](DISENO_FRONTEND.md).
- Integración de GitHub App: instalación y verificación de pertenencia mediante autorización OAuth/PKCE, selección e importación de repositorios, interfaz responsive móvil/escritorio. App instalada bajo `CondezJuan8` con dos repositorios autorizados; credenciales locales completas en `.env` ignorado por Git. Vinculación e importación reales completadas en Dinamita; falta sincronización automática ante cambios posteriores en GitHub.

El detalle y la evidencia por bloque están en la [bitácora](BITACORA_TRABAJO.md). Las tablas creadas y las pruebas con proveedores simulados no significan que el producto esté listo para usar en su totalidad.

## Configurar y validar juntos lo entregado

- [x] **Arranque nativo para revisión:** frontend http://localhost:8088/, API con health UP y proxy operativo, Mailpit http://localhost:8025/. Base separada dependency_preview y servicios activos al cerrar el bloque 12. Esto no completa revisión visual ni verificación de Compose.

- [ ] **Google:** registrar/configurar cliente propio, consentimiento, audiencia/cuentas de prueba y callback; guardar ID/secret fuera del repositorio y activar el proveedor. Probar login, cuenta existente, vínculo, cancelación y errores con Google real.
- [ ] **GitHub login:** elegir/configurar el registro de aplicación, credenciales, callback y permisos de identidad; probar consentimiento, correo principal verificado, login y vínculo con GitHub real. No equivale a autorizar repositorios.
- [ ] Continuar la revisión en `localhost:8189`, ahora conectada al backend y la base de datos anteriores. Falta actualizar Homepage/Setup/Callback en la GitHub App; Mailpit de desarrollo está en 8126.
- [x] **GitHub App real en escritorio local:** OAuth/PKCE completado en `localhost:8089`, instalación de `CondezJuan8` activa en Dinamita, dos repositorios importados y descarga ZIP confirmada por el usuario. Queda pendiente revisión en móvil/otro dispositivo y automatizar sincronización ante cambios de selección en GitHub.
- [x] **Arranque Compose y migraciones:** Flyway valida V1-V24; V24 aplicada. 128 tablas, 91 FORCE RLS, 227 FK. Health UP, PostgreSQL y Mailpit saludables; el volumen de base se conservó.
- [ ] **Servidor y correo:** configurar SMTP real para entregar avisos de nuevas solicitudes de acceso con aprobación/rechazo; comprobar entrega y comportamiento ante escáneres que ejecutan enlaces. Mailpit cubre el aviso local, no entrega externa.

Pasos y callbacks en [IDENTIDAD_GOOGLE_GITHUB](IDENTIDAD_GOOGLE_GITHUB.md), acceso en [ACCESO_LOCAL](ACCESO_LOCAL.md), correo en [VERIFICACION_Y_RECUPERACION](VERIFICACION_Y_RECUPERACION.md) y revisión de colores en [DISENO_FRONTEND](DISENO_FRONTEND.md). Nunca escribir secretos en este archivo ni enviarlos por chat.

## Funciones por implementar

- [x] Configuracion compartida de organizacion: tema SYSTEM/LIGHT/DARK e idioma ES/EN persistidos en backend con autorizacion OWNER/ADMIN y lectura para miembros activos; V18 aplicada y verificada en PostgreSQL local. Parametros adicionales quedan pendientes.
- [x] Importación desde carpeta: selector web, exclusión de `.git`/dependencias/artefactos de build/credenciales conocidas, límite de 100 MiB/50.000 archivos, ZIP privado persistente, repositorio local tenant-scoped y autorización mediante permisos existentes. La carga histórica de 446 archivos se confirmó antes de V24; el nuevo borde de 50.000 está cubierto por test focal y queda pendiente validación de carga real.

Orden de referencia según las dependencias del maestro; se elige el bloque concreto con el usuario al retomar.

| Área | Pendiente | Criterio de cierre |
| --- | --- | --- |
| Arranque local Docker | Arranque interactivo verificado. La instancia de revisión tiene 7 cuentas, 1 administrador activo y 2 organizaciones | Para conectar GitHub, entrar con una membresía OWNER o ADMIN de la organización elegida |
| Identidad y organización | Backend/UI de invitaciones y gestión entregados en bloque 11; pendiente revisión conjunta y paginación de historiales de invitaciones grandes | Completar aceptación visual/operativa sin considerar pendiente el código ya probado |
| Integración de repositorios GitHub | Instalación/OAuth/PKCE, listado, importación, descarga y sincronización bajo demanda; deshabilita repositorios retirados y detecta instalación revocada al abrir/actualizar el espacio | Falta webhook o sincronización periódica inmediata, y revisión del usuario retirando/revocando acceso real; las fuentes remotas viven mientras GitHub las autorice |
| Repositorios y análisis | V32 y job manual durable implementados: `202 Accepted`, estado/etapas consultables, permisos SQL mínimos del worker, reintentos acotados, idempotencia y cancelación cooperativa. PostgreSQL verifica importación local Maven/npm, autorización OWNER, trabajo, resultados por scan, Inventario y persistencia de commit remoto antes de cerrar el scan. `mvn verify -Pdatabase-it`: 61/61 IT y 77 unitarias/arquitectura; backend Compose reconstruido, `/health` y `/api/auth/csrf` responden 200 | La reproducción del 403 de la cuenta real requiere su sesión autenticada; el backend verifica `scan.create` por membresía activa en la organización/repositorio seleccionados. Quedan cancelación en cdxgen y recuperación tras reinicio. Manual y sin calendario; consulta upstream todavía no implementada |
| Vulnerabilidades | Pantalla propia con agrupación OSV, búsqueda/filtros por severidad/ecosistema/repositorio/estado, priorización, versiones corregidas, alias y fuentes; estados OPEN/IN_REVIEW/RISK_ACCEPTED/RESOLVED persistidos por hallazgo/repositorio | La implementación local está completa. V28 aplicada; catálogo real regenerado (127 tablas, 91 FORCE RLS, 226 FK). Falta revisión visual/funcional con hallazgos reales del usuario y confirmar estados con sus permisos |
| Historial y comparación | Consultas por commit y comparación de snapshots del mismo repositorio | Identificar añadidos, eliminados y actualizados con procedencia |
| Recomendaciones | Versiones candidatas/corregidas, riesgo y evidencia documental | Proponer cambios explicables y pruebas pertinentes |
| Impacto y primer piloto | ADD/UPDATE/REMOVE/REPLACE, estados KNOWN/POTENTIAL/VALIDATED, pruebas aisladas y propuesta de PR aprobable | Ejecutar validación aislada y presentar una propuesta revisable; acciones sobre repositorios sujetas a aprobación en el producto |
| Artefactos y documentos | Retención privada de SBOM/originales, versiones, descarga/exportación autorizada, reindexación y limpieza de temporales | Conservar y consultar originales con permisos y política de retención/eliminación |
| Búsqueda e IA | Extracción/OCR, búsqueda híbrida, embeddings Ollama y asistente Gemini con citas/permisos | Responder con evidencia autorizada; fijar modelos/dimensión y activar pgvector solo cuando se implemente esa fase |
| Alertas | Eventos, deduplicación, preferencias y entrega in-app | Consultar y gestionar notificaciones dentro de la plataforma; correo de notificaciones después |
| Reportes | Informes técnicos/ejecutivos y exportación PDF/CSV/JSON/SBOM; programación posterior | Exportaciones trazables con permisos sobre los datos |
| Interfaz general | Navegación, pantallas de módulos, estados de carga/error y accesibilidad | Recorridos completos con datos reales y revisión del usuario |

## Operación y requisitos transversales

- [ ] Definir y completar persistencia/recuperación de sesiones para el despliegue; actualmente viven en memoria.
- [ ] Entrega durable y reintentos de recuperación/verificación/invitaciones; resolver el límite entre aceptación SMTP y commit de base. Recuperación usa cola en memoria e invitaciones envío síncrono transaccional.
- [ ] Ampliar auditoría a los nuevos casos de uso y completar logs/métricas útiles, sin secretos.
- [ ] Concretar límites coordinados y proxy de confianza; el límite de intentos actual es por proceso.
- [ ] Configurar HTTPS, cookies seguras, origen público y callbacks exactos al pasar a servidor.
- [ ] Definir retención, limpieza, almacenamiento y gestión de secretos según el entorno.
- [ ] Comprobar backup/restauración, migraciones y despliegue reproducible en el entorno de destino.
- [ ] Mantener migraciones incrementales y actualizar diagramas/diccionario únicamente si cambia el esquema.
- [ ] Verificar cada bloque con las pruebas necesarias y evidencia real, sin repetir suites por rutina.

## Cómo retomaremos

1. El usuario indica cuándo y qué bloque trabajamos juntos.
2. Revisamos estado y dependencias; identificamos configuración o decisiones necesarias, sin pedir permisos ya concedidos para ese bloque.
3. Implementamos y verificamos lo acordado. Actualizamos esta lista, maestro y bitácora, con herramientas usadas y límites comprobados.
4. Marcamos como terminado solo lo que tenga evidencia y guardamos commits coherentes con emoji y descripción en español.

El primer recorrido pendiente de revisión es acceso y colores. El siguiente hito funcional es **GitHub → repositorio → análisis → inventario con datos reales**; todavía no está completado. Codex queda fuera de la IA del producto hasta petición explícita del usuario. No se presupone una cuota, modelo ni gratuidad de proveedores.
## Estado del bloque 27 - Sidebar, busqueda y preferencias de encabezado

Se tradujeron las etiquetas de navegacion y resultados del buscador; ahora puede buscarse usando el nombre mostrado en ingles. El selector de idioma y tema del encabezado vuelve a estar activo con una organizacion. El ajuste compartido queda como valor predeterminado y cada persona puede aplicar un override local desde el encabezado; se conserva en este navegador. Frontend actualizado en http://localhost:8089/ y responde HTTP 200. Falta tu revision visual.
## Estado del bloque 28 - Diagnostico GitHub

La pantalla ahora comprueba si la cuenta puede administrar GitHub en la organizacion y si la GitHub App esta configurada. Explica el requisito y no deja pulsar Instalar cuando falta configuracion o rol. En el `.env` local faltan las variables `GITHUB_APP_*`, por lo que aun hace falta registrar/configurar la App y guardar sus secretos localmente antes de probar OAuth e importacion. Callback vigente: `http://localhost:8089/api/integrations/github/app-callback`.

### Actualización del bloque 43

El tablero de vulnerabilidades y las explicaciones de advisories ya están implementados en frontend. Build y despliegue de `localhost:8089` se verificarán en este bloque; la revisión visual conjunta con repositorios reales todavía está pendiente.

Estado final del bloque 43: frontend compilado, desplegado y disponible en `http://localhost:8089`; portada y bundle HTTP 200. La revisión visual con datos reales queda para el usuario.
## IA documental: migración a OpenAI

- [x] Reemplazar Gemini por adaptador OpenAI Responses API; conservar Ollama y pgvector.
- [x] Limitar el harness MVP a diagnóstico y prompt revisable, sin herramientas ni mutaciones del repositorio.
- [x] Añadir salida estructurada, validación de citas contra evidencia, autorización previa y UI de copiar propuesta.
- [ ] El usuario configura `OPENAI_API_KEY` en `.env` local; no compartirla por chat.
- [ ] Reconstruir/reiniciar backend para aplicar V30 y validar una solicitud autenticada con documentos autorizados, citas y prompt de reparación.
- [ ] Revisar controles de datos/retención de OpenAI para los documentos que se envían como contexto.
## Estado de las peticiones 11-13 del contexto maestro (23/11/2026)

El plan vigente es el de 14 peticiones en `DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md`; las listas antiguas de 15 son historicas.

- [x] Peticion 11: comparacion de snapshots completados del mismo repositorio, cambios de paquetes/versiones y diferencias OSV con evidencia.
- [~] Peticion 12: filtros, vista ejecutiva/tecnica, vista previa y exportacion CSV/JSON/impresion PDF hasta 5.000 paquetes. Pendientes destinatarios, artefactos persistidos, SBOM/SPDX y programacion/envio.
- [x] CSV incluye metadatos del reporte y declara si las filas están completas o limitadas por el tope de 5.000 paquetes.
- [x] Build de la imagen `frontend` completado tras el cambio; no se ejecutó suite frontend porque no existe script de test.
- [ ] Peticion 13: sandbox desechable para impacto y pruebas, con aprobacion humana; no habilitar ejecucion del repositorio antes de verificarlo.

### Revisión de exposición de avisos OSV en el código (24/11/2026)

- [x] API y pantalla exigen el aviso del componente del mismo `scanId` y `scanComponentId`.
- [x] Snapshot GitHub fijado al commit del análisis; snapshot local rechazado si cambió su SHA-256.
- [x] Estados explícitos y salida de solo lectura; ninguna modificación de repositorio, rama o pull request.
- [x] Límites de ZIP, expansión, entradas, archivo y duración documentados en el contexto maestro.
- [ ] Ampliar reglas validadas para símbolos/APIs de avisos Java y npm; hoy solo hay dos reglas Jackson y la detección de imports npm no demuestra uso de función vulnerable.
- [ ] Explorar análisis de flujo entre archivos en un sandbox separado; no atribuir alcanzabilidad/explotabilidad sin evidencia validada.
- [ ] Recorrido visual autenticado con un aviso real. La base Compose contiene análisis y evaluaciones OSV; falta completar el clic autenticado desde Inventario con la sesión del usuario.

### Exposicion de vulnerabilidades y claridad de Inventario/Analisis

- [x] Los scans sin SHA permiten validar el vinculo OSV exacto; al faltar snapshot, el resultado es inconcluso y explica la causa en lugar de lanzar «aviso no pertenece».
- [x] Las pantallas explican el recorrido Scanner -> OSV -> evidencia del codigo y el alcance de «sin avisos» e «inconcluso».
- [ ] Confirmar el recorrido visual autenticado y revisar si persiste otra causa de excepcion en el entorno del usuario.
- [x] Se reconstruyeron backend/frontend en Compose; se mantuvieron base, scanner, Mailpit y Ollama sin reinicio. localhost:8089 y sus chunks nuevos responden 200.

## Estado del frontend rediseñado — bloque 102 (24/11/2026)

La propuesta visual quedó aplicada y documentada en [DESIGN.md](../DESIGN.md), con oscuro como tema inicial y audiencia de ingeniería y seguridad por igual. TypeScript (51 archivos), compilador Angular con templates y Sass (27 hojas) pasaron sin diagnósticos. El build de Compose pasó, y el frontend se actualizó en `localhost:8089`; HTTP 200 y capturas Impeccable de 1440×900 y 390×844 confirmaron el acceso nuevo. Queda pendiente la revisión autenticada: la base sigue vacía y falta el bootstrap seguro del administrador. No se usaron mocks ni se alteraron backend, datos o contratos; ver [bitácora, bloque 102](BITACORA_TRABAJO.md).

### Segunda pasada de claridad UX — bloque 103 (24/11/2026)

- [x] Mantener el sidebar y el diálogo de análisis que el usuario indicó que le gustan.
- [x] Normalizar gutters duplicados entre el shell y las vistas de repositorios, inventario, asistente, documentos y reportes.
- [x] Añadir navegación por secciones reales en dashboard, repositorios, análisis, inventario, avisos, documentación y reportes; mostrar guía contextual según la pestaña activa de organización.
- [x] Hacer los enlaces accesibles por teclado y visibles al navegar, con objetivos táctiles de 44 px; traducir las etiquetas nuevas.
- [x] Build Docker de frontend correcto y actualizado en `localhost:8089`; HTTP 200. Capturas de Impeccable revisadas a 1440×900 y 390×844.
- [ ] Recorrer las vistas internas con sesión, organización y repositorios reales después del bootstrap seguro del administrador. No usar mocks ni afirmar validación autenticada antes de ese paso.

Ver [bitácora, bloque 103](BITACORA_TRABAJO.md). La auditoría detectora de Impeccable no reportó hallazgos; el build conserva avisos de presupuesto Angular para el bundle inicial y estilos.
