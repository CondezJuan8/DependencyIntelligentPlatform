# Convenciones de commits

Formato: `<tipo>(<alcance>): <emoji> <descripción en español>`.

Ramas: crear `feature/<nombre>` desde `develop` para funcionalidad; `fix/<nombre>` para correcciones. No integrar ni publicar sin solicitud. Los dos commits iniciales se conservan como historial; los siguientes siguen esta convención.

Emojis: `feat` ✨, `fix` 🐛, `docs` 📝, `test` ✅, `refactor` ♻️, `chore` 🔧.
Ejemplo: `feat(database): ✨ implementa esquema y aislamiento por organización`.

- `docs`: documentación y decisiones de arquitectura.
- `chore`: estructura, dependencias y herramientas.
- `feat`: funcionalidad nueva.
- `fix`: corrección de errores.
- `test`: pruebas.
- `refactor`: cambios internos sin alterar comportamiento.

Usar mensajes en imperativo, con un alcance concreto. No incluir secretos ni artefactos generados.

## Arranque del repositorio

1. `docs: agrega requisitos, contexto y propuesta tecnológica`
2. `chore: crea estructura inicial para desarrollo local con Docker`
