# Normalización del esquema aplicado V13

Fuente en catálogo real, diccionario y migraciones V4–V17. V13 incorpora credential_version, atributo escalar determinado por el usuario, e índice de solicitudes de tokens. V17 normaliza los permisos directos por miembro/repositorio en una tabla de asociación con FK e índice. Los cambios de contraseña actualizan hash y versión dentro de la transacción. El inventario de claves mantiene su estructura. La revisión de 1NF–5NF analiza dependencias declaradas y excepciones técnicas. [Catálogo](schema.json), [diccionario](data-dictionary.md).

## Criterios y decisiones

- 1NF: relaciones canónicas como filas, valores escalares y claves. No hay columnas SQL array en el núcleo. JSONB solo conserva payloads/evidencia fuente, parámetros técnicos, configuración extensible o detalles de auditoría; no reemplaza membresías, permisos, aristas, fixes, citas ni listas canónicas consultables.
- 2NF/3NF: cada atributo describe el hecho de la fila y su clave completa. Se separan identidad global, membresía tenant y asignación de roles; paquete, versión y aparición; advisory, evaluación de severidad y hallazgo. Datos de proveedor/modelo no se copian en cada mensaje.
- BCNF: las identidades naturales declaradas determinan su fila en su ámbito. Se conservan claves técnicas redundantes para FK compuestas. No se certifica BCNF universal: algunas repeticiones deliberadas de tenant/procedencia y valores generados se justifican abajo. Un UNIQUE con NULL no siempre es una clave candidata relacional total.
- 4NF: hechos multivaluados independientes se separan: roles/permisos, miembros/equipos, identificadores/licencias/hashes, aliases/referencias, paquetes afectados/rangos/fixes, documentos/repositorios/versiones, evidencia/hallazgos, scope/reportes y suscripciones/entregas. Por ejemplo, los roles de una membresía no multiplican sus equipos en una única tabla.
- 5NF: los puentes binarios tienen claves compuestas y representan hechos independientes. Las relaciones de tres o más participantes (permisos por repositorio, citas tipadas, evidencia de hallazgo) conservan su significado completo; no se presupone una descomposición sin pérdida. No se conocen dependencias de reunión adicionales verificadas que permitan afirmar 5NF para todo el sistema.

## Matriz por módulo

| Módulo | DF e identidad relevante | Descomposición y control |
| --- | --- | --- |
| Identidad/acceso | usuario → correo normalizado/estado; (proveedor, subject) → usuario; (organización, usuario) → membresía | Credenciales/tokens separados; roles y permisos M:N; equipos y permisos con FK tenant. Invitación no es una membresía activa |
| Integraciones | conexión → proveedor/cuenta; (organización, proveedor, ID externo) → repositorio; (repositorio, SHA) → commit | Instalación GitHub como subtipo, ramas/commits/webhooks/sincronizaciones separados. Delivery idempotente según su clave SQL |
| Paquetes | (organización, ecosistema, namespace, nombre) → paquete; (paquete, versión textual) → versión | Identificadores alternativos separados: dos PURL con qualifiers pueden pertenecer a la misma versión. NULLS NOT DISTINCT conserva unicidad del namespace ausente |
| Snapshots/grafo | (scan, bom_ref) → aparición; arista → extremos del mismo scan | Snapshot no es paquete. Directa/transitiva permanece UNKNOWN sin evidencia; licencias observadas separadas de las de versión |
| Jobs/pipeline | job → límites/estado; (job, número) → intento; ejecución → etapa/intento | Eventos/logs/artefactos como filas; intentos no exceden máximo. Todos los jobs iniciales tienen organización; no se introduce cola global implícita |
| Vulnerabilidades | (fuente, advisory ID) → advisory; registro fuente → payload/hash/base; hallazgo → advisory/aparición | Fuentes/severidades/aliases/referencias y rangos/eventos/fixes separados. Un fix legado del hallazgo no se promueve a verdad pública sin evidencia |
| Documentos/RAG | documento → identidad/política; (documento, versión) → original; (versión, ordinal) → chunk | Asociaciones M:N tipadas. Modelo/revisión/dimensión separados de metadatos chunk/modelo. Original privado y temporal con expiración son hechos distintos |
| Asistente | conversación → membresía; (conversación, orden) → mensaje | Modelo/prompt versionados. Citas XOR a recursos con FK reales; no ID polimórfico sin referencia |
| Impacto | análisis → snapshot/acción/versiones; resultado → análisis/clasificación; test → simulación/resultado | Evidencia M:N sin CSV. VALIDATED requiere prueba terminada del mismo análisis. Propuesta de PR distinta de aprobación/publicación |
| Reportes | solicitud → definición/formato/job; artefacto → solicitud/hash/ruta | Scope repositorios/scans, destinatarios y programación separados; parámetros técnicos no sustituyen scope |
| Notificaciones | evento → hecho deduplicado; entrega → evento/membresía/canal | Suscripciones e intentos separados; lectura por destinatario. Catálogo no implementa correo |
| Auditoría/retención | evento → actor/acción/instante; política → tipo/organización; solicitud → recurso/estado | Cambios de atributos separados y append-only; eliminación tipada con FK. Sin periodos legales inventados |

## Excepciones técnicas y límites explícitos

`organization_id` se repite en hijos aunque un UUID padre globalmente único permita derivarlo: permite RLS local y FK (organización, recurso) para impedir cruces de tenant. Las claves UNIQUE adicionales que contienen la PK sirven de destino de esas FK; no son claves candidatas mínimas distintas. No se duplican nombres ni atributos del padre en esos hijos.

El proveedor se conserva en relaciones de integración/GitHub/repositorio para verificar el subtipo y coherencia por FK. La procedencia repositorio/commit de simulaciones y snapshots permite FK compuestas e inmutabilidad histórica. Son redundancias controladas por restricciones, no una afirmación de BCNF estricta de toda tabla. El correo normalizado generado depende del correo: es una proyección determinista para identidad, no un segundo hecho editable.

`scan_component_changes` es una proyección persistida de comparación; exige versionado/recalculado consistente por la aplicación. Snapshots, hashes, payloads y auditoría conservan observaciones históricas: no se sobrescriben al cambiar el catálogo actual. La base CANONICAL_JSON del payload legado distingue su hash de bytes HTTP originales, que no existían.

JSON técnico puede contener información sensible: el escritor debe sanitizar secretos. Los CHECK de claves/rutas no sustituyen autorización de almacenamiento ni sanitización de todo payload. Logs/eventos no se presentan como entidades de negocio alternativas.

Los catálogos globales son de referencia; usuarios/identidades globales son privados del subsistema de autenticación. El catálogo de paquetes queda privado por tenant para evitar exposición entre organizaciones. Roles personalizados, autores y maintainers no se inventan. La tabla vectorial física es opcional y no se incluye entre las 121 tablas aplicadas.

## Inventario de claves y dependencias por tabla

Listado obtenido del catálogo aplicado. Cada PK determina los atributos de su fila. UNIQUE no mínimo (por contener la PK) se marca como soporte técnico; UNIQUE con columnas anulables requiere considerar NULL y sus condiciones antes de llamarlo clave candidata. Para definiciones completas, checks, índices parciales y referencias consultar el diccionario. Este inventario SQL no pretende inferir dependencias semánticas no declaradas.

| Tabla | PK: determinante de fila | Otras restricciones UNIQUE y observación |
| --- | --- | --- |
| `assistant_conversations` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima |
| `assistant_message_evidences` | `(id)` → atributos de fila | `(organization_id, message_id, ordinal)` — identidad alternativa declarada |
| `assistant_messages` | `(id)` → atributos de fila | `(organization_id, conversation_id, ordinal)` — identidad alternativa declarada; `(organization_id, id)` — soporte FK; no mínima |
| `assistant_model_providers` | `(code)` → atributos de fila | — |
| `assistant_model_versions` | `(id)` → atributos de fila | `(provider, model_name, revision)` — identidad alternativa declarada |
| `assistant_prompt_versions` | `(id)` → atributos de fila | `(purpose, version)` — identidad alternativa declarada |
| `audit_event_changes` | `(id)` → atributos de fila | — |
| `audit_events` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima |
| `background_job_attempts` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima; `(organization_id, job_id, attempt_number)` — identidad alternativa declarada |
| `background_job_events` | `(id)` → atributos de fila | — |
| `background_jobs` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima |
| `change_pull_request_proposals` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima |
| `change_record_evidences` | `(organization_id, change_record_id, evidence_id)` → atributos de fila | — |
| `change_simulations` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima |
| `component_external_references` | `(id)` → atributos de fila | `(organization_id, component_version_id, reference_type, url)` — identidad alternativa declarada |
| `component_hashes` | `(organization_id, component_version_id, algorithm, digest)` → atributos de fila | — |
| `component_identifiers` | `(id)` → atributos de fila | `(organization_id, identifier_type, value)` — identidad alternativa declarada |
| `component_licenses` | `(organization_id, component_version_id, license_id)` → atributos de fila | — |
| `component_versions` | `(id)` → atributos de fila | `(organization_id, component_id, version)` — identidad alternativa declarada; `(organization_id, id)` — soporte FK; no mínima; `(organization_id, component_id, id)` — soporte FK; no mínima |
| `components` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima; `(organization_id, package_ecosystem_id, namespace, name)` — anulable; revisar semántica NULL |
| `data_deletion_requests` | `(id)` → atributos de fila | — |
| `data_retention_policies` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima; `(organization_id, resource_type)` — identidad alternativa declarada |
| `dependency_edges` | `(id)` → atributos de fila | `(organization_id, scan_id, source_scan_component_id, target_scan_component_id, relationship_type_id)` — identidad alternativa declarada |
| `dependency_relationship_types` | `(id)` → atributos de fila | `(code)` — identidad alternativa declarada |
| `dependency_scopes` | `(id)` → atributos de fila | `(code)` — identidad alternativa declarada |
| `document_chunk_embeddings` | `(id)` → atributos de fila | `(organization_id, document_chunk_id, embedding_model_id)` — identidad alternativa declarada; `(organization_id, id, embedding_model_id)` — soporte FK; no mínima; `(organization_id, id)` — soporte FK; no mínima |
| `document_chunks` | `(id)` → atributos de fila | `(organization_id, document_version_id, ordinal)` — identidad alternativa declarada; `(organization_id, id)` — soporte FK; no mínima |
| `document_component_versions` | `(organization_id, document_id, component_version_id)` → atributos de fila | — |
| `document_components` | `(organization_id, document_id, component_id)` → atributos de fila | — |
| `document_processing_artifacts` | `(id)` → atributos de fila | — |
| `document_processing_runs` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima |
| `document_repositories` | `(organization_id, document_id, repository_id)` → atributos de fila | — |
| `document_review_flags` | `(id)` → atributos de fila | `(organization_id, document_id, impact_finding_id)` — identidad alternativa declarada |
| `document_types` | `(code)` → atributos de fila | — |
| `document_versions` | `(id)` → atributos de fila | `(organization_id, document_id, version_number)` — identidad alternativa declarada; `(organization_id, id)` — soporte FK; no mínima |
| `documents` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima |
| `embedding_models` | `(id)` → atributos de fila | `(provider, model_name, revision)` — identidad alternativa declarada |
| `finding_evidence` | `(id)` → atributos de fila | — |
| `finding_fixed_versions` | `(organization_id, finding_id, version)` → atributos de fila | — |
| `github_installations` | `(id)` → atributos de fila | `(installation_external_id)` — identidad alternativa declarada; `(organization_id, id)` — soporte FK; no mínima; `(organization_id, integration_id)` — identidad alternativa declarada |
| `impact_analyses` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima; `(organization_id, repository_id, id, base_commit_id)` — soporte FK; no mínima |
| `impact_evidences` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima |
| `impact_finding_component_versions` | `(organization_id, finding_id, component_version_id)` → atributos de fila | — |
| `impact_finding_documents` | `(organization_id, finding_id, document_id)` → atributos de fila | — |
| `impact_finding_evidences` | `(organization_id, finding_id, evidence_id)` → atributos de fila | — |
| `impact_finding_repositories` | `(organization_id, finding_id, repository_id)` → atributos de fila | — |
| `impact_finding_vulnerabilities` | `(organization_id, finding_id, vulnerability_id)` → atributos de fila | — |
| `impact_findings` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima |
| `impact_recommendation_evidences` | `(organization_id, recommendation_id, evidence_id)` → atributos de fila | — |
| `impact_recommendations` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima |
| `impact_test_suggestions` | `(id)` → atributos de fila | — |
| `integration_providers` | `(code)` → atributos de fila | — |
| `integration_sync_runs` | `(id)` → atributos de fila | — |
| `integrations` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima; `(organization_id, provider, external_id)` — identidad alternativa declarada; `(organization_id, id, provider)` — soporte FK; no mínima |
| `invitation_roles` | `(organization_id, invitation_id, role_id)` → atributos de fila | — |
| `job_statuses` | `(code)` → atributos de fila | — |
| `job_types` | `(code)` → atributos de fila | — |
| `license_catalog` | `(id)` → atributos de fila | `(code)` — identidad alternativa declarada |
| `membership_invitations` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima; `(token_hash)` — identidad alternativa declarada |
| `membership_roles` | `(organization_id, membership_id, role_id)` → atributos de fila | — |
| `notification_channels` | `(code)` → atributos de fila | — |
| `notification_deliveries` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima; `(organization_id, notification_id, membership_id, channel)` — identidad alternativa declarada |
| `notification_delivery_attempts` | `(id)` → atributos de fila | `(organization_id, delivery_id, attempt_number)` — identidad alternativa declarada |
| `notification_rules` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima; `(organization_id, name)` — identidad alternativa declarada |
| `notification_subscriptions` | `(organization_id, rule_id, membership_id, channel)` → atributos de fila | — |
| `notifications` | `(id)` → atributos de fila | `(organization_id, event_type, deduplication_key)` — identidad alternativa declarada; `(organization_id, id)` — soporte FK; no mínima |
| `organization_memberships` | `(organization_id, user_id)` → atributos de fila | `(id)` — identidad alternativa declarada; `(organization_id, id)` — identidad alternativa declarada |
| `organization_teams` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima; `(organization_id, name)` — identidad alternativa declarada |
| `organizations` | `(id)` → atributos de fila | `(slug)` — identidad alternativa declarada |
| `package_ecosystems` | `(id)` → atributos de fila | `(code)` — identidad alternativa declarada |
| `permissions` | `(id)` → atributos de fila | `(code)` — identidad alternativa declarada |
| `report_artifacts` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima |
| `report_definitions` | `(id)` → atributos de fila | `(code)` — identidad alternativa declarada |
| `report_formats` | `(code)` → atributos de fila | — |
| `report_jobs` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima |
| `report_repositories` | `(organization_id, report_job_id, repository_id)` → atributos de fila | — |
| `report_scans` | `(organization_id, report_job_id, scan_id)` → atributos de fila | — |
| `report_schedule_recipients` | `(id)` → atributos de fila | `(organization_id, schedule_id, membership_id, external_email)` — anulable; revisar semántica NULL |
| `report_schedules` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima |
| `report_types` | `(code)` → atributos de fila | — |
| `repositories` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima; `(organization_id, provider, external_id)` — identidad alternativa declarada |
| `repository_branches` | `(id)` → atributos de fila | `(organization_id, repository_id, name)` — identidad alternativa declarada |
| `repository_commits` | `(id)` → atributos de fila | `(organization_id, repository_id, commit_sha)` — identidad alternativa declarada; `(organization_id, repository_id, id)` — soporte FK; no mínima |
| `repository_settings` | `(organization_id, repository_id)` → atributos de fila | — |
| `repository_team_access` | `(organization_id, repository_id, team_id, permission_id)` → atributos de fila | — |
| `role_permissions` | `(role_id, permission_id)` → atributos de fila | — |
| `roles` | `(id)` → atributos de fila | `(code)` — identidad alternativa declarada |
| `sbom_documents` | `(organization_id, scan_id)` → atributos de fila | `(organization_id, id)` — identidad alternativa declarada |
| `sbom_external_references` | `(id)` → atributos de fila | `(organization_id, sbom_document_id, reference_type, url)` — identidad alternativa declarada |
| `scan_artifacts` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima; `(organization_id, scan_id, id)` — soporte FK; no mínima |
| `scan_comparisons` | `(id)` → atributos de fila | `(organization_id, base_scan_id, target_scan_id, rules_version)` — identidad alternativa declarada; `(organization_id, id)` — soporte FK; no mínima |
| `scan_component_changes` | `(id)` → atributos de fila | `(organization_id, comparison_id, component_id)` — identidad alternativa declarada |
| `scan_component_licenses` | `(organization_id, scan_component_id, license_id)` → atributos de fila | — |
| `scan_components` | `(organization_id, scan_id, bom_ref)` → atributos de fila | `(organization_id, scan_id, id)` — identidad alternativa declarada; `(organization_id, id)` — identidad alternativa declarada |
| `scan_logs` | `(id)` → atributos de fila | — |
| `scan_profile_ecosystems` | `(organization_id, scan_profile_id, package_ecosystem_id)` → atributos de fila | — |
| `scan_profiles` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima; `(organization_id, name)` — identidad alternativa declarada |
| `scan_stage_executions` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima; `(organization_id, scan_id, stage_code, attempt_number)` — identidad alternativa declarada |
| `scan_stages` | `(code)` → atributos de fila | `(sequence_number)` — identidad alternativa declarada |
| `scan_vulnerability_findings` | `(id)` → atributos de fila | `(organization_id, id, vulnerability_id)` — soporte FK; no mínima; `(organization_id, scan_component_id, vulnerability_id)` — identidad alternativa declarada; `(organization_id, id)` — soporte FK; no mínima |
| `scanner_tools` | `(code)` → atributos de fila | — |
| `scans` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima; `(organization_id, repository_id, id, repository_commit_id)` — soporte FK; no mínima; `(organization_id, repository_id, id)` — soporte FK; no mínima |
| `severity_levels` | `(code)` → atributos de fila | `(sort_order)` — identidad alternativa declarada |
| `simulation_test_runs` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima |
| `team_memberships` | `(organization_id, team_id, membership_id)` → atributos de fila | — |
| `user_access_tokens` | `(id)` → atributos de fila | `(token_hash)` — identidad alternativa declarada |
| `user_identities` | `(id)` → atributos de fila | `(provider, provider_subject)` — identidad alternativa declarada; `(user_id, provider)` — identidad alternativa declarada |
| `user_password_credentials` | `(user_id)` → atributos de fila | — |
| `users` | `(id)` → atributos de fila | `(email_normalized)` — anulable; revisar semántica NULL |
| `version_change_records` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima |
| `vulnerabilities` | `(id)` → atributos de fila | `(source, external_id)` — identidad alternativa declarada |
| `vulnerability_affected_packages` | `(id)` → atributos de fila | `(vulnerability_id, package_ecosystem_id, namespace, name)` — anulable; revisar semántica NULL |
| `vulnerability_affected_ranges` | `(id)` → atributos de fila | — |
| `vulnerability_aliases` | `(id)` → atributos de fila | `(vulnerability_id, alias_type, alias_value)` — identidad alternativa declarada |
| `vulnerability_fixed_versions` | `(affected_package_id, version)` → atributos de fila | — |
| `vulnerability_range_events` | `(range_id, sequence_number)` → atributos de fila | — |
| `vulnerability_references` | `(id)` → atributos de fila | `(vulnerability_id, url)` — identidad alternativa declarada |
| `vulnerability_severities` | `(id)` → atributos de fila | `(vulnerability_id, source, method, observed_at)` — identidad alternativa declarada |
| `vulnerability_source_records` | `(id)` → atributos de fila | `(id, vulnerability_id)` — soporte FK; no mínima; `(vulnerability_id, source, fetched_at, sha256)` — identidad alternativa declarada |
| `vulnerability_sources` | `(code)` → atributos de fila | — |
| `webhook_events` | `(id)` → atributos de fila | `(organization_id, id)` — soporte FK; no mínima; `(provider, delivery_id)` — identidad alternativa declarada |
