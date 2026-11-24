# Prompt maestro para corregir Dependency Intelligence

Usa este prompt en la raíz del repositorio cuando se decida iniciar la siguiente fase. Este documento define el encargo; no afirma que las correcciones descritas ya estén implementadas.

## Prompt

Actúa como arquitecto de software senior y desarrollador principal de este repositorio. Tu objetivo es convertir los problemas enumerados abajo en mejoras implementadas, verificables y trazables, sin fingir cobertura ni romper las funciones existentes.

### 1. Antes de cambiar código

1. Lee `AGENTS.md` y, antes de implementar, `docs/DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md`, `docs/ARQUITECTURA_BACKEND.md`, `docs/CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md`, `docs/STACK_TECNOLOGICO_DEPENDENCY_INTELLIGENCE.md`, `docs/PENDIENTES.md` y `docs/BITACORA_TRABAJO.md`.
2. Contrasta cada síntoma con el código, API, base/migraciones, interfaz, configuración Compose y evidencia de ejecución disponible. Distingue: implementado, parcial, pendiente, bloqueado por configuración/datos y no comprobado.
3. Revisa las preguntas de decisión al final. Hazlas al usuario por módulo y trabaja en paralelo solo en tareas independientes que no dependan de respuestas. No vuelvas a preguntar decisiones respondidas en el contexto o en el repositorio. Si una respuesta cambia arquitectura, privacidad, proveedor, frecuencia/costo, alcance del reporte o riesgo de seguridad, espera esa respuesta antes de implementar esa parte. Para lo demás, elige el cambio mínimo coherente y documéntalo.
4. Prepara un plan por bloques pequeños, con dependencias entre módulos y criterios de aceptación observables. La petición completa no autoriza a marcar como terminados módulos enteros sin evidencia. No mezcles una migración de esquema, cambio de scanner, rediseño de reportes y comportamiento de IA en un único bloque.

### 2. Decisiones y límites vigentes que no debes romper

- Respeta arquitectura hexagonal: Domain y Application no dependen de Spring/JPA; adaptadores y configuración viven en Infrastructure.
- Conserva tenant isolation, permisos por repositorio, RLS, trazabilidad y retención de evidencia. No muestres contenido de repositorios/documentos fuera de los permisos del usuario.
- **No modifiques el módulo Vulnerabilidades**: ni su comportamiento, consultas, estados, UI, datos, API o esquema. Solo lectura/auditoría para comprobar integraciones; si otro módulo requiere un cambio incompatible, detente y presenta la decisión sin tocarlo.
- El asistente de producto usa OpenAI para generación; Ollama para embeddings; pgvector para recuperación. No uses Gemini ni integres Codex al producto. Nunca inventes modelos, dimensiones, cuotas, gratuidad o resultados.
- El asistente actual es de solo lectura y depende de evidencia autorizada. No agregues herramientas de escritura, ejecución de código, actualización de repositorios o PR automático.
- No inventes datos del dashboard, cobertura, severidades, nombres, versiones, citas, vulnerabilidades, progreso ni resultados. Un dato desconocido/incompleto debe decirlo claramente.
- Un lockfile permite reconstruir versiones resueltas del snapshot analizado; no demuestra cuáles son las versiones publicadas más recientes hoy. No sustituyas el snapshot reproducible por versiones remotas no fijadas ni cambies archivos del usuario durante un análisis.
- Un análisis periódico y la consulta de versiones recientes son trabajos distintos. No ejecutes `npm install`, Maven con plugins/scripts, ni código del repositorio en el servidor para “actualizar” dependencias sin sandbox, alcance, aislamiento, aprobación y evidencia definidos.
- No detengas ni reinicies servicios nativos o Compose que no sean necesarios. No uses `dependency_preview`. Nunca imprimas secretos ni los incluyas en el repositorio.
- Usa paleta/tokens de `docs/DISENO_FRONTEND.md` y `frontend/src/theme/_tokens.scss`. Mantén los estados parciales honestos; no fuerces una etiqueta de “completo” para complacer.
- Ejecuta solo verificaciones necesarias para el cambio y reporta comandos y resultados exactos. Mantén `docs/PENDIENTES.md`, el contexto maestro y la bitácora con evidencia; separa herramientas usadas de dependencias nuevas. No añadas dependencias sin justificación.
- No hagas commits salvo que el usuario lo solicite en esa fase. Si se solicitan, cada commit coherente usa emoji obligatorio y Conventional Commits con descripción en español.

### 3. Problemas que debes resolver por bloques

#### A. Exposición de código de hallazgos OSV

Investiga el recorrido completo `snapshot → scan → componente → evaluación OSV → aviso → snapshot local/GitHub → búsqueda de evidencia fuente → API → UI`. Usa como caso de regresión el caso informado por el usuario: scan `c3ef190e-d589-4207-9761-60923dc67b` (confirma el ID en la instancia; no lo hardcodees), origen local, `node_modules/axios`, hash esperado/observado iguales, mensaje “no hay líneas asociadas” y explicación de que aún no hay regla validada para vincular aviso y API vulnerable.

No confundas coincidencia de nombre/import con uso de una API vulnerable ni con explotabilidad. Amplía cobertura solo con reglas versionadas, específicas por ecosistema/paquete/aviso y probadas con fixtures positivos y negativos. Devuelve estados legibles: evidencia encontrada; no se encontró patrón con reglas disponibles; regla no soportada; fuente/hash/commit no verificable; límite alcanzado; error. Si no hay evidencia, muestra qué se buscó y por qué no se puede concluir. Para npm contempla símbolos y llamadas a API cuando una regla fiable exista; para Java, símbolos/clases/métodos comprobables. No inventes números de línea ni resultado de flujo de datos. Trata análisis interprocedural como fase aparte y aislada.

#### B. Inventario, origen, identificadores y puntaje

Haz legibles procedencia, repositorio, rama/commit cuando exista, fuente local/GitHub, fecha y método del análisis. Presenta un nombre humano estable del repositorio y un identificador corto de scan como apoyo; conserva el UUID completo en detalle/copia para soporte y trazabilidad. No elimines identificadores persistidos ni relaciones.

Explica SHA-256 en lenguaje cotidiano como huella de los bytes de la fuente revisada: esperado frente al verificado, coincidencia/no coincidencia, qué protege y qué no significa. Muestra una forma corta para lectura y conserva el valor completo desplegable/copiar. No llames “seguro” a una coincidencia.

Localiza la fórmula y el significado actual de “puntuación técnica”. Si no está basada en una fórmula defendible y datos explicables, no presentes un número opaco: deriva una explicación en lenguaje simple con factores visibles, límites y acción recomendada; no inventes puntuaciones ni cambies semántica sin actualizar contrato/API y documentación.

#### C. Análisis, scanner, cobertura y actualidad

Audita configuración, entrada/salida, tiempo y logs de cdxgen; explica por qué devuelve cero componentes en el caso real. Mantén recuperación acotada desde manifests/lockfiles como respaldo explícito, con procedencia y estado parcial, nunca como si fuera un SBOM completo. Diferencia archivo ausente, proyecto sin dependencias, scanner vacío, versión no resuelta, error y límite. Conserva el último inventario válido si falla un nuevo análisis.

Diseña y entrega análisis asíncrono durable (estado, progreso solo si es real, reintentos acotados, cancelación segura, errores, idempotencia, exclusión de duplicados y recuperación tras reinicio) acorde con los puertos/tablas de jobs existentes. La petición web debe devolver un identificador consultable y no mantener la conexión durante el trabajo. No declares porcentaje/etapas que el backend no conoce.

Separa: (1) repetir escaneo del commit/snapshot fijado y (2) comprobar disponibilidad de nuevas versiones upstream. Propón sincronización cada 12 h únicamente para la segunda cuando haya fuentes oficiales, política, caché, límites de tasa, configuración, observabilidad y consentimiento definidos. Si el usuario inicia análisis manual en cualquier momento, debe poder hacerlo sin mutar repo/lockfile. Una respuesta de registry es metadata remota y debe quedar fechada/originada; nunca la presentes como la versión ya usada en el snapshot.

#### D. Comparar snapshots

Permite identificar snapshots por nombre del repo, fecha, rama/commit o etiqueta de origen; presenta selector legible. Compara solo snapshots del mismo repositorio y ecosistema compatibles; muestra añadidos, retirados, cambios de versión, estado de evaluación y diferencias de cobertura. Los hashes/IDs completos quedan disponibles para auditoría. Un snapshot local con contenido cambiado no se debe presentar como el mismo contenido.

#### E. Documentación y OCR

Habilita OCR real para PDF escaneado con motor y paquetes fijados, extracción aislada, límite de páginas/tamaño/tiempo/memoria, detección de idioma configurable si está disponible, estado de OCR y calidad/confianza interpretables. Mantén extracción de texto normal antes de OCR y no dupliques texto. Valida PDFs digitales, escaneados, mixtos, vacíos, malformados y sobre el límite. No afirmes OCR disponible hasta confirmar dependencias en la imagen desplegada y recorrer un documento escaneado.

La consulta de documentación oficial debe ser explícitamente una capacidad distinta de los documentos privados subidos: define dominios oficiales permitidos por ecosistema/proveedor, URLs, versión/fecha, atribución, caché, actualización, límites de red y licencias/robots aplicables. No raspes foros no oficiales ni envíes código/documentos privados a terceros. Si no se implementa navegador externo, di que la respuesta usa solo documentos indexados y ofrece fuentes oficiales verificables.

#### F. Asistente global, embeddings y RAG

El asistente debe conocer únicamente datos consultados por APIs/servicios del producto con el permiso correspondiente en las secciones Repositorios, Dashboard, Inventario, Vulnerabilidades, Análisis, Documentación, Reportes, Organización y Configuración. RAG documental no reemplaza consultas SQL/API para información viva como snapshot actual, versiones, estados, autorías o reportes. Integra herramientas de lectura tipadas y autorizadas en backend; no des permisos por confiar en el modelo. Cada respuesta debe distinguir hecho consultado, cita documental, inferencia y falta de datos. Muestra organización/repositorio/versión/fecha/fuente en contexto. Para “quién hizo qué”, usa auditoría verificable; si no hay evento registrado, abstente.

Conserva embeddings Ollama y retrieval pgvector con aislamiento tenant/repositorio. Define reindexación, versión/digest del modelo, dimensión medida, estado, idempotencia y eliminación consistente. Maneja indexación larga como trabajo durable si la evidencia confirma que hace falta. Incluye documentos y código solo cuando tienen permiso y una fuente indexada; no prometas comprensión del código ausente. Pon nombre configurable al asistente y solicita al usuario la marca/nombre antes de fijarlo si no existe ya en preferencias.

#### G. Reportes ejecutivos y técnicos

Audita backend, frontend, contenido, diseño de impresión/PDF, paginación y filtros. Reporte ejecutivo: resumen para dirección, impacto de negocio en términos no técnicos, conteos por severidad sustentados, repositorios/áreas afectadas, tendencia solo si hay snapshots comparables, acciones priorizadas/responsables si están disponibles, cobertura/limitaciones y fecha/fuente. Diseño jerárquico, limpio, accesible y listo para imprimir/PDF; sin tarjetas vacías, métricas inventadas ni jerga sin explicar.

Reporte técnico: incluir solo hallazgos con severidad relevante según filtros seleccionados (no catálogo completo de paquetes sin riesgo), con nombre/coordenada, versión detectada, severidad, aviso, explicación clara, rango afectado/corregido solo si la fuente lo confirma, evidencia, impacto, remediación, repositorio/archivo, snapshot y cobertura. Excluir paquetes sin hallazgos pertinentes. Los no evaluados deben aparecer en un bloque de cobertura/limitaciones aparte, nunca mezclados con vulnerabilidades ni como seguros. Asegura que filtro, vista previa y exportación describan el mismo conjunto de datos; no trunques silenciosamente al límite de 5.000: pagina/explica/exporta de forma consistente.

#### H. Organización, configuración y dashboard

Organización y Configuración deben conservar roles, permisos y aislamiento existentes. Añade solo información útil para los nuevos flujos (por ejemplo, política de frecuencia, acceso a proveedor documental o nombre del asistente) si hay autorización, defaults seguros, auditoría y ayuda comprensible. Dashboard debe explicar con lenguaje cotidiano y datos reales; enlazar al detalle y diferenciar nunca evaluado, vencido, fallo y sin avisos conocidos.

### 4. Criterios transversales de aceptación

- La explicación de un hallazgo es reproducible desde los bytes/commit del snapshot y la misma pareja scan/componente/advisory; errores de hash/commit/permisos no devuelven contenido.
- Los cambios de análisis/importación no alteran el repositorio ni su lockfile. Scan manual repetido y job programado se deduplican correctamente.
- Los estados de cobertura reflejan exactamente la fuente del resultado y persisten tras recargar. Ningún error se transforma en “sin vulnerabilidades”.
- Accesos de otro tenant, organización o repositorio denegado, incluso para herramientas de IA, documentos, exportaciones y URLs directas.
- Todos los resultados tienen fecha/fuente/alcance y la UI diferencia UUID/hash completo de etiqueta legible.
- Los informes ejecutivo y técnico coinciden con API/filtros y no ocultan truncamiento, fallas ni registros pendientes.
- OCR y RAG solo se marcan disponibles tras comprobar el servicio/runtime desplegado y una prueba representativa de extremo a extremo.
- Mantén pruebas focalizadas para reglas de código, scanner/async, filtros/reportes, OCR y permisos RAG según el bloque. No ejecutes suites ajenas al cambio.
- Actualiza documentos y bitácora con petición, cambios concretos, tecnologías realmente usadas, verificaciones y resultado observado, limitaciones y siguiente paso. No inventes horas, resultados ni cierre.

### 5. Preguntas que debes hacer al usuario (mínimo cinco por herramienta)

Agrupa la conversación por herramienta, permite responder “usa criterio técnico” en cada punto y evita bloquear módulos no dependientes de la decisión. No hagas estas preguntas si la respuesta ya está explícita en la sesión.

#### Mis repositorios

1. ¿Qué campos deben identificar el origen en pantalla: nombre del repo, organización, rama, commit, etiqueta y fecha?
2. Para repositorios locales, ¿qué nombre visible prefieres y quién puede cambiarlo?
3. Si una fuente GitHub se revoca o desaparece, ¿quieres conservar snapshots ya guardados con su etiqueta de origen?
4. ¿Qué eventos esperas ver en el historial de actividad del repositorio y quién los realizó?
5. ¿La frecuencia automática debe configurarse por repositorio o aplicarse como valor por organización?

#### Dashboard

1. ¿Qué tres preguntas ejecutivas quieres responder al entrar al Dashboard?
2. ¿Qué periodo debe usar para tendencias: desde el último snapshot, 30 días u otro?
3. ¿Qué estados deben quedar visibles arriba: hallazgos, cobertura, análisis fallidos o tareas pendientes?
4. ¿Qué roles pueden ver conteos agregados de toda la organización?
5. ¿Prefieres una vista inicial resumida o un panel configurable por usuario?

#### Inventario, scanner, evidencia de código y comparación

1. ¿Qué lenguajes/ecosistemas son prioritarios después de Java/Maven y JavaScript/npm?
2. ¿Qué entiendes por “encontrar código”: import, llamada a API, flujo de datos o ruta alcanzable? (Recomiendo mostrar por separado evidencia e inferencia.)
3. ¿Qué alias corto prefieres para un scan: fecha-hora, contador por repo o combinación repo+fecha?
4. ¿Quieres comparar automáticamente el snapshot nuevo con el anterior o iniciar la comparación manualmente?
5. ¿Qué campos deben aparecer primero en cada dependencia y cuáles pueden quedar en detalle?
6. ¿Qué fórmula actual o decisión de negocio debe explicar la puntuación técnica? ¿Debe reemplazarse si no es explicable?
7. ¿Qué etiquetas de cobertura te resultan claras para “sin evaluación”, “parcial”, “fallida” y “completa”?

#### Vulnerabilidades (zona protegida)

1. Confirmo: ¿el módulo queda completamente congelado, incluida su UI, API, filtros, estados y esquema?
2. ¿Puedo solo leerlo para validar que Inventario y reportes consumen sus datos, sin cambiarlo?
3. Si una corrección de Inventario necesita contrato nuevo, ¿prefieres que se detenga y te proponga la alternativa?
4. ¿Los reportes pueden leer las severidades actuales de Vulnerabilidades sin cambiar su presentación dentro del módulo?
5. ¿Hay algún enlace o integración externa desde Vulnerabilidades que deba considerarse también fuera de alcance?

#### Análisis y actualización de dependencias

1. ¿Las 12 horas aplican a escanear de nuevo el commit fijado, buscar versiones upstream nuevas o ambos como trabajos separados?
2. ¿Qué repositorios/rama pueden entrar al calendario automático y en qué ventana horaria?
3. ¿Qué proveedores de registry oficiales autorizas para consultar metadata y versiones disponibles?
4. ¿Qué límite de solicitudes/tiempo/costo y política de reintento quieres?
5. ¿Un fallo automático debe reintentar, esperar al siguiente ciclo o avisar a un administrador?
6. ¿Qué nivel de progreso quieres exponer si el sistema solo puede confirmar etapas terminadas?

#### Documentación y OCR

1. ¿Qué idiomas deben procesar los PDF escaneados?
2. ¿Cuál es el tamaño máximo de PDF, páginas y tiempo que aceptas para OCR?
3. ¿OCR debe ser enteramente local o autorizas una API externa? (Por privacidad, asumir local hasta respuesta.)
4. ¿Qué ecosistemas/lenguajes y dominios oficiales deben tener fuentes de documentación, por ejemplo npm, Maven, Python, Rust o Go?
5. ¿Quieres indexar solo documentación oficial versionada o también foros oficiales con fecha y autoría?
6. ¿Con qué frecuencia se actualizan fuentes externas y cuánto tiempo se conserva la copia indexada?

#### Asistente global, embeddings y RAG

1. ¿Qué nombre y tono debe usar el asistente?
2. ¿Debe contestar sobre todos los repositorios que puedes ver o pedir selección explícita de organización/repositorio en cada pregunta?
3. ¿Qué eventos de auditoría son necesarios para contestar “quién hizo qué” y cuánto historial debe consultarse?
4. ¿Qué fuentes autorizas para respuestas: datos estructurados, documentación cargada, repositorios indexados y documentación oficial online?
5. ¿Cuándo debe citar fuentes y qué formato de cita prefieres?
6. ¿Autorizas enviar pregunta y fragmentos documentales permitidos a OpenAI según la configuración vigente, o requieres un modo local sin generación externa?
7. ¿Qué retención deseas para conversaciones, embeddings y fragmentos derivados al eliminar un documento/repositorio?

#### Reportes ejecutivos

1. ¿Quién es el lector principal: dirección, cliente, seguridad o responsable de ingeniería?
2. ¿Qué extensión máxima prefieres: una página, resumen más anexos o informe extenso?
3. ¿Qué severidades entran por defecto y quién puede cambiar ese filtro?
4. ¿Qué acciones/KPIs le importan a dirección (riesgo, tendencia, repositorios afectados, progreso de remediación)?
5. ¿Qué identidad visual debe incluirse: logo/nombre de organización, colores, fecha, clasificación de confidencialidad?

#### Reportes técnicos

1. ¿Qué severidades se consideran relevantes por defecto y cómo tratar severidad desconocida?
2. ¿Quieres un hallazgo por paquete+versión+aviso o agrupar ocurrencias repetidas entre repositorios?
3. ¿Qué evidencia mínima debe incluir cada hallazgo: archivo/línea, advisory, rango afectado, versión corregida y fuente?
4. ¿Qué formatos son prioritarios: impresión/PDF, CSV o JSON?
5. ¿Los hallazgos sin versión corregida deben incluir alternativa de mitigación si hay evidencia oficial?
6. ¿El límite de 5.000 filas debe configurarse, paginarse o bloquear la exportación completa?

#### Organización

1. ¿Qué roles pueden cambiar frecuencia de análisis, fuentes oficiales y alcance del asistente?
2. ¿La configuración de estos módulos es global por organización o admite excepciones por repositorio?
3. ¿Qué eventos nuevos deben aparecer en auditoría para administradores?
4. ¿Los miembros con acceso a un repositorio pueden generar reportes sobre ese repo aunque no administren la organización?
5. ¿Quién debe recibir avisos in-app de fallos de análisis u OCR?

#### Configuración

1. ¿Qué ajustes deben ser personales, cuáles por organización y cuáles por repositorio?
2. ¿Quieres habilitar/deshabilitar el calendario automático desde la interfaz?
3. ¿Qué valor por defecto debe tener la frecuencia si el administrador no configura nada?
4. ¿Qué opciones de privacidad deben mostrarse para OpenAI, Ollama y fuentes oficiales online?
5. ¿Qué nombre/branding del asistente y del reporte debe poder configurar una persona administradora?

### 6. Informe final de cada bloque

Al terminar un bloque, informa: causa raíz encontrada; archivos/API/esquema afectados; cambios concretos; preguntas respondidas o decisiones asumidas; comandos/verificaciones ejecutados y resultados exactos; funcionalidades probadas en runtime; brechas restantes; documentos actualizados; y siguiente bloque sugerido. No digas “el repositorio funciona correctamente” de forma global: limita la afirmación a los criterios demostrados.
