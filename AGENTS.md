# Instrucciones de trabajo

- Antes de implementar, revisar `docs/DEPENDENCY_INTELLIGENCE_CONTEXTO_MAESTRO.md`, `docs/ARQUITECTURA_BACKEND.md`, `docs/CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md` y `docs/STACK_TECNOLOGICO_DEPENDENCY_INTELLIGENCE.md`. Priorizar decisiones vigentes del usuario. Núcleo database V1–V13, 121 tablas, acceso local, autorización y verificación/recuperación con SMTP implementados. Ver `docs/database/implementation-report.md`, `docs/ACCESO_LOCAL.md` y `docs/VERIFICACION_Y_RECUPERACION.md`. Continuar con Google/GitHub, vinculación segura e invitaciones según el maestro.

- Mantener `docs/BITACORA_TRABAJO.md` en cada bloque: petición, cambios concretos, herramientas/tecnologías utilizadas, verificaciones ejecutadas con resultado y siguiente paso. Distinguir herramientas usadas de dependencias nuevas implementadas. No inventar horas ni resultados.
- Mantener Domain y Application independientes de Spring/JPA; los adaptadores y la configuración pertenecen a Infrastructure.
- Ejecutar únicamente las pruebas necesarias para el cambio. Repetirlas si cambia el comportamiento cubierto, fallan o queda una duda concreta.
- Separar los cambios por funcionalidad, normalmente en bloques de 2–3 commits; cada commit debe ser coherente y llevar emoji obligatorio y formato Conventional Commits con descripción en español.
- Objetivo vigente: producto completo listo para usar, por fases, local primero y servidor después. El plan anterior de demo en dos semanas/50 horas ya no limita alcance o aceptación. Actualizar estados por evidencia.
- Acceso confirmado: correo/contraseña, Google y GitHub. Maven y npm. Primer piloto con equipos/permisos por repositorio y pruebas aisladas/propuesta de PR aprobable. Notificaciones in-app; correo de notificaciones después.
- IA del producto: Gemini; embeddings: Ollama. No integrar ni agendar Codex dentro del producto hasta que el usuario lo pida explícitamente. No inventar modelos, dimensiones, cuotas ni gratuidad.
