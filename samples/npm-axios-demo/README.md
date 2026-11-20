# Muestra npm para Dependency Intelligence

Esta carpeta es un fixture pequeño y seguro para probar el lector `package-lock.json` v3 en la plataforma. Declara Axios directamente y conserva relaciones transitivas representativas (`form-data` → `combined-stream` → `delayed-stream`, entre otras).

No contiene `node_modules`, scripts de instalación ni código de producción. Para probarla, impórtala como carpeta local en la organización y luego ejecuta **Analizar repositorio** desde Inventario.
