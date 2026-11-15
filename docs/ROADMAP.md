# Roadmap del producto completo

La fuente de alcance vigente es el [contexto maestro](DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md), junto con [arquitectura](ARQUITECTURA_BACKEND.md), [database](CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md) y [stack](STACK_TECNOLOGICO_DEPENDENCY_INTELLIGENCE.md).

El usuario prioriza un producto completo listo para usar, local primero y servidor después. El plan anterior de demo/50 horas y sus estimaciones dejan de ser criterios de aceptación. La disponibilidad declarada sigue siendo 25 horas semanales; no hay fecha total nueva comprometida.

| Fase propuesta | Resultado verificable | Estado |
| --- | --- | --- |
| Transición del modelo | V4–V11, casos de uso compatibles, datos conservados, diccionario y siete diagramas | Núcleo completado y probado; vector opcional pendiente |
| Acceso y aislamiento | Correo/contraseña, Google/GitHub, equipos, permisos por repositorio y RLS | RLS/roles SQL probados; identidad y autorización funcional pendientes: siguiente fase |
| Análisis real | GitHub, jobs, scanner Maven/npm, SBOM, OSV, inventario/grafo/historial y UI | Parcial: importador acotado existente |
| Cambios y primer piloto | Comparación/recomendaciones, pruebas aisladas y propuesta de PR aprobable | Pendiente; requisito del primer piloto |
| Documentación e IA | Originales privados, búsqueda/citas, embeddings Ollama y Gemini en la plataforma | Pendiente |
| Producto completo y servidor | Reportes, alertas in-app, auditoría, retención, operación, backup/restauración y recorridos completos | Pendiente |

Las dependencias de cada módulo se concretan en la matriz del contexto maestro; las funciones transversales se incorporan con cada entrega. Correo de notificaciones después de in-app; el correo de verificación/recuperación de cuentas es independiente.

Codex no se integra ni se agenda dentro del producto hasta nueva instrucción del usuario. Modelos/dimensión y límites de proveedores se fijan al implementar, sin prometer gratuidad.

Antes de estimar fecha total, descomponer cada fase en tareas y criterios verificables, incluida la transición de datos. Actualizar la [bitácora](BITACORA_TRABAJO.md) por bloque y ejecutar únicamente comprobaciones relevantes.
