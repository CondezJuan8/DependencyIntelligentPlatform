# Reporte de implementación — estado guardado

El trabajo quedó guardado por petición del usuario antes de completar la revisión final. El estado detallado y los pasos pendientes están en [CONTINUAR.md](CONTINUAR.md).

| Área | Resultado comprobado |
|---|---|
| Migraciones | 12 archivos en total: V1 existente, V2–V7 core, V101–V104 future, V201 vector opcional |
| Tablas | 76 core / 115 future / 116 con vector |
| Integridad | 246 FKs en esquema completo; tenant, snapshot y repositorio controlados por claves compuestas |
| Seguridad | RLS FORCE, runtime no propietario sin DDL/BYPASSRLS, migrador separado, auditoría append-only |
| Build | Correcto; 2 pruebas JUnit satisfactorias |
| SQL local / instalación limpia | 41 comprobaciones satisfactorias en cada escenario |
| SQL vector opcional | 47 comprobaciones satisfactorias en pila temporal eliminada |
| Diagramas | 7 Excalidraw en `diagramas/`; validados estructuralmente, revisión visual pendiente |
| Cierre operativo | Comprobación final de salud e inventario local interrumpida; pendiente |

La base de trabajo usa PostgreSQL 17.7 y no requiere extensiones adicionales. pgvector solo se activa explícitamente y se probó en una pila aislada. No se eligió proveedor ni dimensión de embeddings de producción. Retención, proveedor de identidad y políticas de priorización siguen abiertos.

Los seeds solo contienen referencias; no usuarios u organizaciones reales. Las APIs, workers y adaptadores funcionales no están implementados. El [diccionario](data-dictionary.md) detalla tablas, columnas, claves, índices y políticas. La [guía local](README.md) explica credenciales, perfiles, arranque y pruebas reproducibles.
