# Convenciones de commits

Formato: `<emoji> <tipo>(<alcance>): <descripción en español>`. El emoji es obligatorio.

- `docs`: documentación y decisiones de arquitectura.
- `chore`: estructura, dependencias y herramientas.
- `feat`: funcionalidad nueva.
- `fix`: corrección de errores.
- `test`: pruebas.
- `refactor`: cambios internos sin alterar comportamiento.

Usar mensajes en imperativo, con un alcance concreto. No incluir secretos ni artefactos generados.

Ejemplos:

- `✨ feat(backend): agrega registro de repositorios y persistencia`
- `♻️ refactor(backend): separa Domain, Application e Infrastructure`
- `🐛 fix(database): corrige relaciones entre snapshots`
- `🧪 test(backend): verifica aislamiento por organización`
- `📝 docs(architecture): documenta los límites de las capas`

Se conserva `feat` como tipo Conventional Commits para funcionalidades nuevas.

## Arranque del repositorio (histórico anterior a la regla del emoji)

1. `docs: agrega requisitos, contexto y propuesta tecnológica`
2. `chore: crea estructura inicial para desarrollo local con Docker`
