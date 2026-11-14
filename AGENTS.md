# Instrucciones de trabajo

- Mantener `docs/BITACORA_TRABAJO.md` en cada bloque: petición, cambios concretos, herramientas/tecnologías utilizadas, verificaciones ejecutadas con resultado y siguiente paso. Distinguir herramientas usadas de dependencias nuevas implementadas. No inventar horas ni resultados.
- Mantener Domain y Application independientes de Spring/JPA; los adaptadores y la configuración pertenecen a Infrastructure.
- Ejecutar únicamente las pruebas necesarias para el cambio. Repetirlas si cambia el comportamiento cubierto, fallan o queda una duda concreta.
- Separar los cambios por funcionalidad, normalmente en bloques de 2–3 commits; cada commit debe ser coherente y llevar emoji obligatorio y formato Conventional Commits con descripción en español.
- Priorizar el alcance de `docs/FUNCIONES_DEMO_DOS_SEMANAS.md`: demo local, 25 horas semanales y presupuesto total de 50 horas. Actualizar los estados por evidencia, sin marcar terminadas funciones que solo están planificadas.
