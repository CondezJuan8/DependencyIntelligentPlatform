# Normalización y dependencias

Objetivo: hechos atómicos y descomposiciones sin pérdida hasta 5NF cuando sus dependencias lo permitan. No se afirma una demostración formal universal: reglas aún no definidas de negocio pueden introducir dependencias adicionales. El diccionario enumera las claves y restricciones verificadas en PostgreSQL para cada tabla.

## Criterios

- **1NF:** cada celda representa un valor. Relaciones de roles, permisos, ecosistemas, aliases, licencias, destinatarios y asociaciones documentales tienen filas propias.
- **2NF:** los atributos de una asociación dependen de toda su clave candidata. Ejemplo: el rol del acceso de un equipo depende de `(organization_id, repository_id, team_id)`, no del equipo aislado.
- **3NF:** no copiar nombre de ecosistema, rol, severidad o canal a tablas que ya tienen FK hacia ese catálogo.
- **BCNF:** una clave ID determina los atributos; las claves naturales alternativas declaradas deben determinar la misma fila. No declarar BCNF si aparecen dependencias no clave aún no modeladas.
- **4NF:** hechos multivaluados independientes se separan: alias y referencias de advisory; licencias y hashes por versión; documentos/repositorios y documentos/componentes.
- **5NF:** separar asociaciones independientes en puentes binarios solo si la descomposición conserva significado. No convertir una relación genuinamente ternaria en pares que produzcan combinaciones falsas.

## Matriz por módulo

| Módulo | Claves candidatas y dependencias principales | Evaluación / excepción |
|---|---|---|
| Identidad | `users.id → email_normalized, display_name, ...`; `email_normalized → id`. `organizations.slug → id`. | 3NF/BCNF bajo identidad única por email; email puede cambiar como atributo de usuario. |
| Membresías/RBAC | `(organization_id,user_id) → membership.id,status`. `(membership_id,role_id) → asignación`. `(role_id,permission_id) → concesión`. | Relaciones independientes de roles y permisos, compatibles con 4NF/5NF; no hay roles CSV. |
| Equipos | `(organization_id,team_id,membership_id) → pertenencia`; `(organization_id,repository_id,team_id) → role_id`. | El acceso equipo/repositorio/rol es un hecho conjunto. Separarlo en repositorio-equipo y equipo-rol permitiría roles incorrectos por repositorio. |
| Integraciones | `(organization_id,provider_id,external_account_id) → integración`. `installation_id → instalación`. | No se copian tokens; `credential_reference` es un escalar externo, no el secreto. |
| Repositorios | `(organization_id,integration_id,external_repository_id) → repositorio`. `(organization_id,repository_id,commit_sha) → commit`. | SHA no es identidad global. Owner/name son metadatos observados, no clave estable del proveedor. |
| Trabajos | `(organization_id,deduplication_key) → job`; `(organization_id,job_id,attempt_number) → intento`. | Intentos/eventos separados; transiciones validadas por trigger. Payload técnico no guarda relaciones canónicas. |
| Scans/SBOM | `(organization_id,repository_id,request_key) → scan`. `(organization_id,scan_id) → sbom_document`. | `repository_id` también se deriva del commit: se conserva como discriminador con FK compuesta, para impedir comparación entre repositorios. No hay SHA duplicado en scans. |
| Componentes | `(organization_id,ecosystem,namespace,normalized_name) → component`; `(organization_id,component_id,version) → version.id`. | Versiones no son atributos multivaluados del paquete ni presuponen SemVer. Catálogo privado evita filtrar dependencias internas. |
| Presencia/grafo | `(organization_id,scan_id,bom_ref) → scan_component`; `(organization_id,scan_id,source,target,relationship_type) → edge`. | El grafo es un hecho snapshot-scoped. No descomponer una arista en relaciones binarias sin scan: produciría rutas históricas falsas. Directa/transitiva y rutas se derivan del grafo. |
| Licencias/hashes | `(component_version,license)`, `(scan_component,license)`, `(component_version,algorithm,digest)` determinan hechos independientes. | 4NF/5NF para asociaciones independientes; licencia de catálogo y observación SBOM son fuentes distintas. |
| Vulnerabilidades | `(source,external_id) → advisory`; `(advisory,type,value) → alias`; `(advisory,url) → referencia`. | No guardar arrays de aliases/references. Rangos/eventos conservan orden. El paquete público afectado no duplica el catálogo privado: son hechos de fuentes con visibilidad distinta. |
| Hallazgos | `(organization_id,scan_component_id,vulnerability_id) → finding`. Fuente y severidad referencian el mismo advisory mediante FKs; el trigger comprueba rango de evidencia. | Snapshot de detección, no prueba de explotación. No derivar severidad canónica del texto de un LLM. |
| Comparaciones | `(organization_id,base_scan,target_scan,method_version) → comparación`. | `scan_component_changes` es una **proyección histórica persistida**, no fuente del inventario. Recalcular con un método nuevo crea otra comparación; no sobreescribir evidencia previa. |
| Documentos | `(organization_id,document_id,version_number) → versión`; `(organization_id,document_version_id,ordinal) → chunk`. | Documento-repositorio, documento-paquete y documento-versión son asociaciones independientes; puentes separados evitan producto cartesiano artificial. |
| Modelos/embeddings | `(provider,model_name,model_version) → modelo`; `(chunk,embedding_model) → vector` dentro del tenant. | Dimensión depende del modelo y no se copia a cada vector. Trigger valida dimensión e impide mutar el modelo. No se guarda un vector por inventario. |
| Conversaciones | `(organization_id,conversation_id,ordinal) → mensaje`; `(organization_id,message_id,citation_ordinal) → evidencia`. | Fuente tipada: CHECK exige exactamente una FK (chunk, aparición o finding). No hay entity_type/entity_id sin integridad. Permisos por usuario se validarán en aplicación. |
| Impacto | `impact_analysis.id → operación,base_scan,método`; `finding.id → clasificación,confianza,resumen`. | Evidencias y recomendaciones independientes mediante puente M:N; no inferir rotura confirmada desde POTENTIAL. Confianza es señal del método [0,1], no probabilidad calibrada. |
| Reportes | `(organization_id,job_id) → report_job`; `(report_job,scan) → scope`. | Filtros estructurales se guardan en puentes. Parámetros técnicos JSON no reemplazan el scope. Artefactos son resultados derivados. |
| Notificaciones | `(organization_id,deduplication_key) → notification`; `(notification,channel,recipient_membership) → entrega`. | Separar evento, suscripciones y entregas evita repetición de contenido y listas de destinatarios. Intentos son hechos numerados distintos. |
| Auditoría/retención | `audit_event.id → actor,acción,recurso,fecha`; `(audit_event,attribute) → cambio redactado`. `(organization_id,data_type) → política`. | Eventos append-only. La referencia textual de recurso describe historia, no es una asociación crítica usada como FK de negocio. Retención no está inventada ni ejecutada por el DDL. |

Las PK sustitutas no demuestran por sí solas normalización: cada UNIQUE natural se documenta en el diccionario. El `organization_id` repetido en filas dependientes y los discriminadores `scan_id`/`repository_id` son redundancia deliberada **verificada por FK compuesta**, necesaria para aislamiento e integridad. No se presenta como ausencia absoluta de redundancia ni como prueba formal de BCNF en todas las tablas.

## JSONB y atributos derivados

- `background_jobs.payload`: instrucciones técnicas mínimas, sin tokens ni código fuente. IDs y relaciones consultables deben usar columnas/puentes.
- `scan_profiles.settings`: flags de herramienta; ecosistemas separados en `scan_profile_ecosystems`.
- `scan_stage_executions.metrics`: métricas de diagnóstico, no entidades de dominio.
- `vulnerability_source_records.payload`: respuesta original pública para reproducibilidad, con hash/parser/fecha; matching consulta el modelo normalizado.
- `report_jobs.parameters`: presentación y parámetros no estructurales. Snapshots en `report_job_scans`.
- `assistant_model_versions.configuration`: opciones técnicas no secretas.

No se materializa inventario ni todas las rutas. El inventario se consulta desde scans/component_versions y las aristas. `scan_components.is_root` representa el rol de esa aparición en el SBOM, no clasificación directa/transitiva duplicada. Duración se calcula desde timestamps.

## Anomalías evitadas y límites

Actualizar el nombre de un rol o severidad no exige cambiar findings. Añadir una referencia de advisory no duplica sus aliases. Eliminar una asociación documental no elimina el documento. Cambiar el último scan no borra snapshots previos. FKs RESTRICT preservan historia y fuerzan un plan explícito de borrado.

No hay un validador universal de SemVer, comparación de rangos OSV, cron, zona IANA o asociación automática de paquetes: requieren casos de uso y validación por ecosistema. Los flags y fechas de expiración no ejecutan trabajos por sí solos. No se afirma que un usuario autenticado tenga permisos de recurso solo por compartir tenant.
