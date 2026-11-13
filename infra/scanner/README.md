# Scanner (pendiente)

cdxgen se integrará como proceso aislado para generar CycloneDX. No se instala en la API ni se monta Docker socket. Antes de habilitarlo: fijar imagen por digest, desactivar instalación/ejecución de scripts, usar usuario sin privilegios, entrada de solo lectura, salida limitada, red restringida y timeout. Registrar commit y versión del scanner. No hay análisis implementado en este arranque.
