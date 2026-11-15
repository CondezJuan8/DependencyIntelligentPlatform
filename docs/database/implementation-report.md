# Estado real de implementación de la base de datos

## Referencia y alcance

Diseño objetivo: [CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md](../CODEX_DATABASE_DEPENDENCY_INTELLIGENCE.md). Este informe describe lo implementado; no sustituye esa especificación. El usuario confirmó que la base nueva existe solo como documento.

## Esquema ejecutable actual

V1 inicializa Flyway, V2 crea el esquema inicial y V3 soporta importaciones manuales. Son **14 tablas de negocio**, más `flyway_schema_history` al aplicar Flyway. No se consultó una base del usuario en ejecución en este bloque.

| Tabla | Función / situación actual |
| --- | --- |
| organizations | Organización propietaria; bootstrap y validaciones existentes |
| users | Identidad inicial por subject/correo; no implementa login |
| memberships | Usuario/organización y rol textual; falta normalizar RBAC/equipos/permisos |
| integrations | Estructura inicial de conexiones; falta instalación GitHub funcional |
| repositories | Registro y consulta internos; falta integración GitHub |
| scans | Solicitudes y snapshots manuales; sin worker/scanner |
| scan_artifacts | Referencias/checksum; sin almacenamiento conectado |
| components | Paquete y versión juntos; requiere separar ambos conceptos |
| scan_components | Ocurrencias por snapshot/bom-ref; importación funcional con UNKNOWN |
| dependency_relationships | Estructura de aristas; falta importación/consulta del grafo |
| vulnerabilities | Advisory/payload inicial; faltan OSV, aliases, rangos y severidades normalizados |
| vulnerability_findings | Asociación inicial advisory/ocurrencia; sin generación de hallazgos |
| audit_events | Estructura inicial; falta emisión y comportamiento append-only |
| sbom_imports | Procedencia/hash funcionales; no almacena archivo original |

Configuración actual: PostgreSQL 17.7 en Compose, destino Java 21 y Spring Boot 3.5.16. Las validaciones anteriores usaron PostgreSQL 18.6 temporal y JDK local 25 con destino 21; no prueban Docker.

## Brechas respecto del diseño objetivo

- Identidad local/Google/GitHub, invitaciones, membresías normalizadas, roles/permisos, equipos y acceso por repositorio.
- RLS, rol runtime no propietario, rol de migración separado y contexto tenant transaccional seguro en el pool.
- Separación paquete/versión/presencia; migración de IDs/FK y decisión documentada de scope para catálogos privados sin exponer paquetes entre organizaciones.
- Instalaciones GitHub, commits, webhooks idempotentes, jobs/intentos/etapas, SBOM/artefactos y aristas por snapshot.
- Normalización de vulnerabilidades, aliases/referencias, rangos/eventos, versiones corregidas y evidencia de hallazgos; eliminar asociaciones canónicas multivaluadas en JSONB.
- Comparaciones, impacto, pruebas aisladas y propuestas de PR aprobables con trazabilidad.
- Documentos/originales privados, versiones, asociaciones, chunks, embeddings Ollama, citas y conversaciones Gemini.
- Reportes, artefactos, notificaciones in-app, auditoría append-only, retención y solicitudes de eliminación.
- Diccionario completo, matriz de normalización y siete diagramas Excalidraw derivados del SQL implementado.

No se fija el número final de tablas antes del DDL: la especificación contiene alternativas y entidades opcionales. No se declara la base actual conforme a 5NF ni a RLS.

## Transición requerida

1. Inventariar datos, claves, puertos y consultas afectados; mapear tablas actuales y objetivo.
2. Diseñar migraciones nuevas posteriores a V3 y adaptar los casos de uso en el mismo bloque. No borrar bases/volúmenes ni reescribir migraciones para evitar la transición.
3. Validar arranque limpio, actualización conservando datos, integridad y comportamiento de los adaptadores existentes.
4. Introducir roles SQL/contexto/RLS con pruebas negativas específicas; no activar pgvector hasta fijar y comprobar modelo/dimensión.
5. Derivar documentación y diagramas del DDL real, e implementar funciones completas por fases.

Este bloque solo concreta alcance y referencias. **No añadió SQL ni ejecutó migraciones o suites.** Resultados históricos de importación: [bitácora](../BITACORA_TRABAJO.md), bloque 03. No se atribuyen a la nueva base pendiente.
