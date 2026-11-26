# Reportes de vulnerabilidades

La vista usa las evaluaciones OSV guardadas del repositorio autorizado. Solo muestra avisos de severidad media, alta o crítica y elimina registros repetidos por paquete, versión y aviso.

- Ejecutivo: efecto en lenguaje no técnico y motivo para cambiar la versión, derivados del texto publicado por OSV. Cuando el aviso no permite precisar el efecto, se indica esa limitación.
- Técnico: dependencia, paquete, versión, mecanismo resumido y descripción completa de OSV sin marcadores de formato.
- PDF: se genera como archivo A4 independiente mediante jsPDF; CSV y JSON usan los mismos hallazgos filtrados. La exportación vuelve a comprobar el permiso del repositorio.

El informe documenta avisos conocidos; no confirma por sí solo que el código vulnerable se ejecute.
