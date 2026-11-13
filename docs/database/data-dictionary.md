# Diccionario de datos

Generado desde el catálogo PostgreSQL real por `tools/database/render_docs.py`. Incluye las fases core, future y vector; la base local de trabajo no activa vector.

Todas las FKs usan `ON DELETE RESTRICT`: no se propaga borrado de historia. Retención y eliminación requieren políticas explícitas y un worker; no se elimina contenido por añadir una fecha de expiración. `updated_at` debe actualizarlo el caso de uso.

Los índices FK facilitan joins y borrados controlados. Índices adicionales: historial por repositorio/fecha, cola pendiente por disponibilidad, inventario por versión y búsqueda textual documental. Las restricciones UNIQUE evitan duplicados; el índice parcial del root permite como máximo una raíz por scan.

## `assistant_conversations`

Conversaciones con propietario miembro de la organización.

Módulo: **documentation**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `owner_membership_id` | `uuid` | no | `—` |
| `created_at` | `timestamp with time zone` | no | `now()` |
| `title` | `text` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, owner_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `assistant_messages (organization_id,conversation_id)`.

Índices:

- `CREATE UNIQUE INDEX assistant_conversations_pkey ON public.assistant_conversations USING btree (id)`
- `CREATE UNIQUE INDEX assistant_conversations_organization_id_id_key ON public.assistant_conversations USING btree (organization_id, id)`
- `CREATE INDEX ix_assistant_conversations_fk1 ON public.assistant_conversations USING btree (organization_id, owner_membership_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `assistant_message_evidences`

Citas tipadas a chunk, aparición o finding; exactamente una referencia.

Módulo: **documentation**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `message_id` | `uuid` | no | `—` |
| `document_chunk_id` | `uuid` | sí | `—` |
| `scan_component_id` | `uuid` | sí | `—` |
| `finding_id` | `uuid` | sí | `—` |
| `citation_ordinal` | `integer` | no | `—` |
| `relevance` | `numeric(6,5)` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((citation_ordinal > 0))`
- `CHECK (((relevance >= (0)::numeric) AND (relevance <= (1)::numeric)))`
- `CHECK ((num_nonnulls(document_chunk_id, scan_component_id, finding_id) = 1))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, message_id, citation_ordinal)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, message_id) REFERENCES assistant_messages(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, document_chunk_id) REFERENCES document_chunks(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, scan_component_id) REFERENCES scan_components(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, finding_id) REFERENCES scan_vulnerability_findings(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX assistant_message_evidences_pkey ON public.assistant_message_evidences USING btree (id)`
- `CREATE UNIQUE INDEX assistant_message_evidences_organization_id_id_key ON public.assistant_message_evidences USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX assistant_message_evidences_organization_id_message_id_cita_key ON public.assistant_message_evidences USING btree (organization_id, message_id, citation_ordinal) NULLS NOT DISTINCT`
- `CREATE INDEX ix_assistant_message_evidences_fk2 ON public.assistant_message_evidences USING btree (organization_id, document_chunk_id)`
- `CREATE INDEX ix_assistant_message_evidences_fk3 ON public.assistant_message_evidences USING btree (organization_id, scan_component_id)`
- `CREATE INDEX ix_assistant_message_evidences_fk4 ON public.assistant_message_evidences USING btree (organization_id, finding_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `assistant_messages`

Mensajes ordenados, modelo/prompt y consumo opcional.

Módulo: **documentation**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `conversation_id` | `uuid` | no | `—` |
| `ordinal` | `integer` | no | `—` |
| `speaker` | `text` | no | `—` |
| `content` | `text` | no | `—` |
| `model_version_id` | `uuid` | sí | `—` |
| `prompt_version_id` | `uuid` | sí | `—` |
| `input_tokens` | `integer` | sí | `—` |
| `output_tokens` | `integer` | sí | `—` |
| `created_at` | `timestamp with time zone` | no | `now()` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((ordinal >= 0))`
- `CHECK ((speaker = ANY (ARRAY['USER'::text, 'ASSISTANT'::text, 'SYSTEM'::text])))`
- `CHECK ((input_tokens >= 0))`
- `CHECK ((output_tokens >= 0))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, conversation_id, ordinal)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, conversation_id) REFERENCES assistant_conversations(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (model_version_id) REFERENCES assistant_model_versions(id) ON DELETE RESTRICT`
- `FOREIGN KEY (prompt_version_id) REFERENCES assistant_prompt_versions(id) ON DELETE RESTRICT`

Relaciones entrantes: `assistant_message_evidences (organization_id,message_id)`.

Índices:

- `CREATE UNIQUE INDEX assistant_messages_pkey ON public.assistant_messages USING btree (id)`
- `CREATE UNIQUE INDEX assistant_messages_organization_id_id_key ON public.assistant_messages USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX assistant_messages_organization_id_conversation_id_ordinal_key ON public.assistant_messages USING btree (organization_id, conversation_id, ordinal) NULLS NOT DISTINCT`
- `CREATE INDEX ix_assistant_messages_fk2 ON public.assistant_messages USING btree (model_version_id)`
- `CREATE INDEX ix_assistant_messages_fk3 ON public.assistant_messages USING btree (prompt_version_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `assistant_model_providers`

Proveedores configurables; no se siembra uno por defecto.

Módulo: **documentation**. Fase: **future**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `assistant_model_versions (provider_id)`.

Índices:

- `CREATE UNIQUE INDEX assistant_model_providers_pkey ON public.assistant_model_providers USING btree (id)`
- `CREATE UNIQUE INDEX assistant_model_providers_code_key ON public.assistant_model_providers USING btree (code) NULLS NOT DISTINCT`

## `assistant_model_versions`

Versión de modelo y configuración técnica sin secretos.

Módulo: **documentation**. Fase: **future**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `provider_id` | `uuid` | no | `—` |
| `model_name` | `text` | no | `—` |
| `model_version` | `text` | no | `—` |
| `configuration` | `jsonb` | no | `'{}'::jsonb` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (provider_id, model_name, model_version)`
- `FOREIGN KEY (provider_id) REFERENCES assistant_model_providers(id) ON DELETE RESTRICT`

Relaciones entrantes: `assistant_messages (model_version_id)`; `embedding_models (model_version_id)`.

Índices:

- `CREATE UNIQUE INDEX assistant_model_versions_pkey ON public.assistant_model_versions USING btree (id)`
- `CREATE UNIQUE INDEX assistant_model_versions_provider_id_model_name_model_versi_key ON public.assistant_model_versions USING btree (provider_id, model_name, model_version) NULLS NOT DISTINCT`

## `assistant_prompt_versions`

Versión de las instrucciones usadas para reproducibilidad.

Módulo: **documentation**. Fase: **future**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `name` | `text` | no | `—` |
| `version` | `text` | no | `—` |
| `content` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (name, version)`

Relaciones entrantes: `assistant_messages (prompt_version_id)`.

Índices:

- `CREATE UNIQUE INDEX assistant_prompt_versions_pkey ON public.assistant_prompt_versions USING btree (id)`
- `CREATE UNIQUE INDEX assistant_prompt_versions_name_version_key ON public.assistant_prompt_versions USING btree (name, version) NULLS NOT DISTINCT`

## `audit_event_changes`

Cambios redactados de atributos, también append-only.

Módulo: **audit**. Fase: **migration**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `audit_event_id` | `uuid` | no | `—` |
| `attribute` | `text` | no | `—` |
| `old_value_redacted` | `text` | sí | `—` |
| `new_value_redacted` | `text` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, audit_event_id, attribute)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, audit_event_id) REFERENCES audit_events(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX audit_event_changes_pkey ON public.audit_event_changes USING btree (id)`
- `CREATE UNIQUE INDEX audit_event_changes_organization_id_id_key ON public.audit_event_changes USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX audit_event_changes_organization_id_audit_event_id_attribut_key ON public.audit_event_changes USING btree (organization_id, audit_event_id, attribute) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `audit_events`

Eventos append-only; referencia descriptiva, no FK polimórfica para datos de negocio.

Módulo: **audit**. Fase: **migration**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `actor_membership_id` | `uuid` | sí | `—` |
| `action` | `text` | no | `—` |
| `resource_type` | `text` | no | `—` |
| `resource_reference` | `text` | no | `—` |
| `result` | `text` | no | `—` |
| `correlation_id` | `uuid` | sí | `—` |
| `occurred_at` | `timestamp with time zone` | no | `now()` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((result = ANY (ARRAY['SUCCESS'::text, 'DENIED'::text, 'FAILURE'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, actor_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `audit_event_changes (organization_id,audit_event_id)`.

Índices:

- `CREATE UNIQUE INDEX audit_events_pkey ON public.audit_events USING btree (id)`
- `CREATE UNIQUE INDEX audit_events_organization_id_id_key ON public.audit_events USING btree (organization_id, id)`
- `CREATE INDEX ix_audit_events_fk1 ON public.audit_events USING btree (organization_id, actor_membership_id)`
- `CREATE INDEX ix_audit_timeline ON public.audit_events USING btree (organization_id, occurred_at DESC, id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `background_job_attempts`

Intentos numerados de ejecución de un trabajo.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `job_id` | `uuid` | no | `—` |
| `attempt_number` | `integer` | no | `—` |
| `worker` | `text` | no | `—` |
| `status` | `text` | no | `'PENDING'::text` |
| `created_at` | `timestamp with time zone` | no | `now()` |
| `started_at` | `timestamp with time zone` | sí | `—` |
| `finished_at` | `timestamp with time zone` | sí | `—` |
| `error_code` | `text` | sí | `—` |
| `error_message` | `text` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((attempt_number > 0))`
- `CHECK ((status = ANY (ARRAY['PENDING'::text, 'RUNNING'::text, 'COMPLETED'::text, 'FAILED'::text, 'CANCELLED'::text, 'RETRYING'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, job_id, attempt_number)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT`
- `CHECK (((finished_at IS NULL) OR ((started_at IS NOT NULL) AND (finished_at >= started_at))))`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX background_job_attempts_pkey ON public.background_job_attempts USING btree (id)`
- `CREATE UNIQUE INDEX background_job_attempts_organization_id_id_key ON public.background_job_attempts USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX background_job_attempts_organization_id_job_id_attempt_numb_key ON public.background_job_attempts USING btree (organization_id, job_id, attempt_number) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `background_job_events`

Transiciones y eventos de diagnóstico de un trabajo.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `job_id` | `uuid` | no | `—` |
| `event_type` | `text` | no | `—` |
| `occurred_at` | `timestamp with time zone` | no | `now()` |
| `details` | `text` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX background_job_events_pkey ON public.background_job_events USING btree (id)`
- `CREATE UNIQUE INDEX background_job_events_organization_id_id_key ON public.background_job_events USING btree (organization_id, id)`
- `CREATE INDEX ix_background_job_events_fk1 ON public.background_job_events USING btree (organization_id, job_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `background_jobs`

Cola persistente por tenant, reintentos limitados y deduplicación.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `job_type_id` | `uuid` | no | `—` |
| `status` | `text` | no | `'PENDING'::text` |
| `created_at` | `timestamp with time zone` | no | `now()` |
| `started_at` | `timestamp with time zone` | sí | `—` |
| `finished_at` | `timestamp with time zone` | sí | `—` |
| `deduplication_key` | `text` | no | `—` |
| `payload` | `jsonb` | no | `'{}'::jsonb` |
| `available_at` | `timestamp with time zone` | no | `now()` |
| `locked_at` | `timestamp with time zone` | sí | `—` |
| `locked_by` | `text` | sí | `—` |
| `attempt_count` | `integer` | no | `0` |
| `max_attempts` | `integer` | no | `3` |
| `last_error_code` | `text` | sí | `—` |
| `last_error_message` | `text` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((status = ANY (ARRAY['PENDING'::text, 'RUNNING'::text, 'COMPLETED'::text, 'FAILED'::text, 'CANCELLED'::text, 'RETRYING'::text])))`
- `CHECK ((attempt_count >= 0))`
- `CHECK ((max_attempts > 0))`
- `CHECK ((attempt_count <= max_attempts))`
- `CHECK ((jsonb_typeof(payload) = 'object'::text))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, deduplication_key)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (job_type_id) REFERENCES job_types(id) ON DELETE RESTRICT`
- `CHECK (((finished_at IS NULL) OR ((started_at IS NOT NULL) AND (finished_at >= started_at))))`

Relaciones entrantes: `background_job_attempts (organization_id,job_id)`; `background_job_events (organization_id,job_id)`; `document_processing_runs (organization_id,job_id)`; `report_jobs (organization_id,job_id)`; `scans (organization_id,job_id)`.

Índices:

- `CREATE UNIQUE INDEX background_jobs_pkey ON public.background_jobs USING btree (id)`
- `CREATE UNIQUE INDEX background_jobs_organization_id_id_key ON public.background_jobs USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX background_jobs_organization_id_deduplication_key_key ON public.background_jobs USING btree (organization_id, deduplication_key) NULLS NOT DISTINCT`
- `CREATE INDEX ix_background_jobs_fk1 ON public.background_jobs USING btree (job_type_id)`
- `CREATE INDEX ix_jobs_pending ON public.background_jobs USING btree (organization_id, available_at, id) WHERE (status = ANY (ARRAY['PENDING'::text, 'RETRYING'::text]))`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `change_record_evidences`

Extractos sanitizados de changelogs/release notes.

Módulo: **impact**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `change_record_id` | `uuid` | no | `—` |
| `excerpt_redacted` | `text` | no | `—` |
| `source_reference` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, change_record_id) REFERENCES version_change_records(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX change_record_evidences_pkey ON public.change_record_evidences USING btree (id)`
- `CREATE UNIQUE INDEX change_record_evidences_organization_id_id_key ON public.change_record_evidences USING btree (organization_id, id)`
- `CREATE INDEX ix_change_record_evidences_fk1 ON public.change_record_evidences USING btree (organization_id, change_record_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `component_external_references`

Referencias externas de una versión.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `component_version_id` | `uuid` | no | `—` |
| `reference_type` | `text` | no | `—` |
| `url` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, component_version_id, reference_type, url)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX component_external_references_pkey ON public.component_external_references USING btree (id)`
- `CREATE UNIQUE INDEX component_external_references_organization_id_id_key ON public.component_external_references USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX component_external_references_organization_id_component_ver_key ON public.component_external_references USING btree (organization_id, component_version_id, reference_type, url) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `component_hashes`

Digests independientes de artefactos de una versión.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `component_version_id` | `uuid` | no | `—` |
| `algorithm` | `text` | no | `—` |
| `digest` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, component_version_id, algorithm, digest)`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX component_hashes_pkey ON public.component_hashes USING btree (id)`
- `CREATE UNIQUE INDEX component_hashes_organization_id_id_key ON public.component_hashes USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX component_hashes_organization_id_component_version_id_algor_key ON public.component_hashes USING btree (organization_id, component_version_id, algorithm, digest) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `component_identifiers`

Identificadores alternativos de un paquete; PURL de versión se conserva como bom_ref cuando corresponda.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `component_id` | `uuid` | no | `—` |
| `identifier_type` | `text` | no | `—` |
| `value` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((identifier_type = ANY (ARRAY['PURL'::text, 'CPE'::text, 'SWID'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, identifier_type, value)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, component_id) REFERENCES components(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX component_identifiers_pkey ON public.component_identifiers USING btree (id)`
- `CREATE UNIQUE INDEX component_identifiers_organization_id_id_key ON public.component_identifiers USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX component_identifiers_organization_id_identifier_type_value_key ON public.component_identifiers USING btree (organization_id, identifier_type, value) NULLS NOT DISTINCT`
- `CREATE INDEX ix_component_identifiers_fk1 ON public.component_identifiers USING btree (organization_id, component_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `component_licenses`

Licencias declaradas por versión de catálogo.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `component_version_id` | `uuid` | no | `—` |
| `license_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `FOREIGN KEY (license_id) REFERENCES license_catalog(id) ON DELETE RESTRICT`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, component_version_id, license_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX component_licenses_pkey ON public.component_licenses USING btree (id)`
- `CREATE UNIQUE INDEX component_licenses_organization_id_id_key ON public.component_licenses USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX component_licenses_organization_id_component_version_id_lic_key ON public.component_licenses USING btree (organization_id, component_version_id, license_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_component_licenses_fk2 ON public.component_licenses USING btree (license_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `component_versions`

Versiones textuales de un paquete; no presupone SemVer.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `component_id` | `uuid` | no | `—` |
| `version` | `text` | no | `—` |
| `normalized_version` | `text` | sí | `—` |
| `published_at` | `timestamp with time zone` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, component_id, version)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, component_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, component_id) REFERENCES components(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `component_external_references (organization_id,component_version_id)`; `component_hashes (organization_id,component_version_id)`; `component_licenses (organization_id,component_version_id)`; `document_component_versions (organization_id,component_version_id)`; `impact_analyses (organization_id,target_component_version_id)`; `impact_finding_versions (organization_id,component_version_id)`; `scan_component_changes (organization_id,component_id,old_version_id)`; `scan_component_changes (organization_id,component_id,new_version_id)`; `scan_components (organization_id,component_version_id)`; `version_change_records (organization_id,component_id,to_version_id)`; `version_change_records (organization_id,component_id,from_version_id)`.

Índices:

- `CREATE UNIQUE INDEX component_versions_pkey ON public.component_versions USING btree (id)`
- `CREATE UNIQUE INDEX component_versions_organization_id_id_key ON public.component_versions USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX component_versions_organization_id_component_id_version_key ON public.component_versions USING btree (organization_id, component_id, version) NULLS NOT DISTINCT`
- `CREATE UNIQUE INDEX component_versions_organization_id_component_id_id_key ON public.component_versions USING btree (organization_id, component_id, id) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `components`

Catálogo lógico de paquetes privado por organización; no es una instalación.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `package_ecosystem_id` | `uuid` | no | `—` |
| `namespace` | `text` | no | `''::text` |
| `name` | `text` | no | `—` |
| `normalized_name` | `text` | no | `—` |
| `purl_type` | `text` | sí | `—` |
| `description` | `text` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `FOREIGN KEY (package_ecosystem_id) REFERENCES package_ecosystems(id) ON DELETE RESTRICT`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, package_ecosystem_id, namespace, normalized_name)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`

Relaciones entrantes: `component_identifiers (organization_id,component_id)`; `component_versions (organization_id,component_id)`; `document_components (organization_id,component_id)`; `impact_finding_components (organization_id,component_id)`; `scan_component_changes (organization_id,component_id)`; `version_change_records (organization_id,component_id)`.

Índices:

- `CREATE UNIQUE INDEX components_pkey ON public.components USING btree (id)`
- `CREATE UNIQUE INDEX components_organization_id_id_key ON public.components USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX components_organization_id_package_ecosystem_id_namespace_n_key ON public.components USING btree (organization_id, package_ecosystem_id, namespace, normalized_name) NULLS NOT DISTINCT`
- `CREATE INDEX ix_components_fk1 ON public.components USING btree (package_ecosystem_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `data_deletion_requests`

Solicitudes de eliminación trazables; ejecución a cargo de un worker autorizado.

Módulo: **audit**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `requested_by_membership_id` | `uuid` | no | `—` |
| `reason` | `text` | no | `—` |
| `status` | `text` | no | `'PENDING'::text` |
| `created_at` | `timestamp with time zone` | no | `now()` |
| `started_at` | `timestamp with time zone` | sí | `—` |
| `finished_at` | `timestamp with time zone` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((status = ANY (ARRAY['PENDING'::text, 'RUNNING'::text, 'COMPLETED'::text, 'FAILED'::text, 'CANCELLED'::text, 'RETRYING'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`
- `CHECK (((finished_at IS NULL) OR ((started_at IS NOT NULL) AND (finished_at >= started_at))))`

Relaciones entrantes: `deletion_request_documents (organization_id,deletion_request_id)`; `deletion_request_repositories (organization_id,deletion_request_id)`.

Índices:

- `CREATE UNIQUE INDEX data_deletion_requests_pkey ON public.data_deletion_requests USING btree (id)`
- `CREATE UNIQUE INDEX data_deletion_requests_organization_id_id_key ON public.data_deletion_requests USING btree (organization_id, id)`
- `CREATE INDEX ix_data_deletion_requests_fk1 ON public.data_deletion_requests USING btree (organization_id, requested_by_membership_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `data_retention_policies`

Política configurable por organización y tipo, desactivada hasta definirla.

Módulo: **audit**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `data_type` | `text` | no | `—` |
| `retention_days` | `integer` | sí | `—` |
| `action` | `text` | no | `—` |
| `legal_basis` | `text` | sí | `—` |
| `is_enabled` | `boolean` | no | `false` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((retention_days >= 0))`
- `CHECK ((action = ANY (ARRAY['DELETE'::text, 'ANONYMIZE'::text, 'KEEP'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, data_type)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`

Relaciones entrantes: `documents (organization_id,retention_policy_id)`.

Índices:

- `CREATE UNIQUE INDEX data_retention_policies_pkey ON public.data_retention_policies USING btree (id)`
- `CREATE UNIQUE INDEX data_retention_policies_organization_id_id_key ON public.data_retention_policies USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX data_retention_policies_organization_id_data_type_key ON public.data_retention_policies USING btree (organization_id, data_type) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `deletion_request_documents`

Documentos objetivo de una solicitud de eliminación.

Módulo: **documentation**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `deletion_request_id` | `uuid` | no | `—` |
| `document_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, deletion_request_id, document_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, deletion_request_id) REFERENCES data_deletion_requests(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX deletion_request_documents_pkey ON public.deletion_request_documents USING btree (id)`
- `CREATE UNIQUE INDEX deletion_request_documents_organization_id_id_key ON public.deletion_request_documents USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX deletion_request_documents_organization_id_deletion_request_key ON public.deletion_request_documents USING btree (organization_id, deletion_request_id, document_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_deletion_request_documents_fk2 ON public.deletion_request_documents USING btree (organization_id, document_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `deletion_request_repositories`

Objetivos de eliminación tipados mediante FK.

Módulo: **audit**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `deletion_request_id` | `uuid` | no | `—` |
| `repository_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `UNIQUE NULLS NOT DISTINCT (organization_id, deletion_request_id, repository_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, deletion_request_id) REFERENCES data_deletion_requests(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX deletion_request_repositories_pkey ON public.deletion_request_repositories USING btree (id)`
- `CREATE UNIQUE INDEX deletion_request_repositories_organization_id_id_key ON public.deletion_request_repositories USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX deletion_request_repositories_organization_id_deletion_requ_key ON public.deletion_request_repositories USING btree (organization_id, deletion_request_id, repository_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_deletion_request_repositories_fk2 ON public.deletion_request_repositories USING btree (organization_id, repository_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `dependency_edges`

Aristas dirigidas que solo unen apariciones del mismo scan.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `scan_id` | `uuid` | no | `—` |
| `source_scan_component_id` | `uuid` | no | `—` |
| `target_scan_component_id` | `uuid` | no | `—` |
| `relationship_type_id` | `uuid` | no | `—` |
| `scope_id` | `uuid` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((source_scan_component_id <> target_scan_component_id))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, scan_id, source_scan_component_id, target_scan_component_id, relationship_type_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, scan_id, source_scan_component_id) REFERENCES scan_components(organization_id, scan_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, scan_id, target_scan_component_id) REFERENCES scan_components(organization_id, scan_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (relationship_type_id) REFERENCES dependency_relationship_types(id) ON DELETE RESTRICT`
- `FOREIGN KEY (scope_id) REFERENCES dependency_scopes(id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX dependency_edges_pkey ON public.dependency_edges USING btree (id)`
- `CREATE UNIQUE INDEX dependency_edges_organization_id_id_key ON public.dependency_edges USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX dependency_edges_organization_id_scan_id_source_scan_compon_key ON public.dependency_edges USING btree (organization_id, scan_id, source_scan_component_id, target_scan_component_id, relationship_type_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_dependency_edges_fk2 ON public.dependency_edges USING btree (organization_id, scan_id, target_scan_component_id)`
- `CREATE INDEX ix_dependency_edges_fk3 ON public.dependency_edges USING btree (relationship_type_id)`
- `CREATE INDEX ix_dependency_edges_fk4 ON public.dependency_edges USING btree (scope_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `dependency_relationship_types`

Catálogo de referencia global, códigos estables; no acredita cobertura funcional del proveedor/ecosistema.

Módulo: **reference**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `dependency_edges (relationship_type_id)`.

Índices:

- `CREATE UNIQUE INDEX dependency_relationship_types_pkey ON public.dependency_relationship_types USING btree (id)`
- `CREATE UNIQUE INDEX dependency_relationship_types_code_key ON public.dependency_relationship_types USING btree (code) NULLS NOT DISTINCT`

## `dependency_scopes`

Catálogo de referencia global, códigos estables; no acredita cobertura funcional del proveedor/ecosistema.

Módulo: **reference**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `dependency_edges (scope_id)`; `scan_components (scope_id)`.

Índices:

- `CREATE UNIQUE INDEX dependency_scopes_pkey ON public.dependency_scopes USING btree (id)`
- `CREATE UNIQUE INDEX dependency_scopes_code_key ON public.dependency_scopes USING btree (code) NULLS NOT DISTINCT`

## `document_chunk_embeddings`

Vector por chunk/modelo; trigger verifica dimensión y RLS protege tenant.

Módulo: **documentation**. Fase: **vector**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `document_chunk_id` | `uuid` | no | `—` |
| `embedding_model_id` | `uuid` | no | `—` |
| `embedding` | `vector` | no | `—` |
| `created_at` | `timestamp with time zone` | no | `now()` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE (organization_id, document_chunk_id, embedding_model_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (embedding_model_id) REFERENCES embedding_models(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, document_chunk_id) REFERENCES document_chunks(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX document_chunk_embeddings_pkey ON public.document_chunk_embeddings USING btree (id)`
- `CREATE UNIQUE INDEX document_chunk_embeddings_organization_id_id_key ON public.document_chunk_embeddings USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX document_chunk_embeddings_organization_id_document_chunk_id_key ON public.document_chunk_embeddings USING btree (organization_id, document_chunk_id, embedding_model_id)`
- `CREATE INDEX ix_embeddings_model ON public.document_chunk_embeddings USING btree (embedding_model_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `document_chunks`

Texto extraído con ordinal, página, sección y hash para citas.

Módulo: **documentation**. Fase: **future**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `document_version_id` | `uuid` | no | `—` |
| `ordinal` | `integer` | no | `—` |
| `content` | `text` | no | `—` |
| `page_start` | `integer` | sí | `—` |
| `page_end` | `integer` | sí | `—` |
| `section_path` | `text` | sí | `—` |
| `token_count` | `integer` | sí | `—` |
| `content_hash` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((ordinal >= 0))`
- `CHECK ((page_start > 0))`
- `CHECK ((token_count >= 0))`
- `CHECK ((content_hash ~ '^[a-f0-9]{64}$'::text))`
- `CHECK (((page_end IS NULL) OR ((page_start IS NOT NULL) AND (page_end >= page_start))))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, document_version_id, ordinal)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, document_version_id) REFERENCES document_versions(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `assistant_message_evidences (organization_id,document_chunk_id)`; `document_chunk_embeddings (organization_id,document_chunk_id)`.

Índices:

- `CREATE UNIQUE INDEX document_chunks_pkey ON public.document_chunks USING btree (id)`
- `CREATE UNIQUE INDEX document_chunks_organization_id_id_key ON public.document_chunks USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX document_chunks_organization_id_document_version_id_ordinal_key ON public.document_chunks USING btree (organization_id, document_version_id, ordinal) NULLS NOT DISTINCT`
- `CREATE INDEX ix_document_chunks_search ON public.document_chunks USING gin (to_tsvector('simple'::regconfig, content))`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `document_component_versions`

Asociaciones de documentos con versiones concretas.

Módulo: **documentation**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `document_id` | `uuid` | no | `—` |
| `component_version_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, document_id, component_version_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX document_component_versions_pkey ON public.document_component_versions USING btree (id)`
- `CREATE UNIQUE INDEX document_component_versions_organization_id_id_key ON public.document_component_versions USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX document_component_versions_organization_id_document_id_com_key ON public.document_component_versions USING btree (organization_id, document_id, component_version_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_document_component_versions_fk2 ON public.document_component_versions USING btree (organization_id, component_version_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `document_components`

Asociaciones de documentos con paquetes lógicos.

Módulo: **documentation**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `document_id` | `uuid` | no | `—` |
| `component_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, document_id, component_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, component_id) REFERENCES components(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX document_components_pkey ON public.document_components USING btree (id)`
- `CREATE UNIQUE INDEX document_components_organization_id_id_key ON public.document_components USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX document_components_organization_id_document_id_component_i_key ON public.document_components USING btree (organization_id, document_id, component_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_document_components_fk2 ON public.document_components USING btree (organization_id, component_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `document_processing_artifacts`

Artefactos temporales de procesamiento con expiración.

Módulo: **documentation**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `processing_run_id` | `uuid` | no | `—` |
| `storage_reference` | `text` | no | `—` |
| `expires_at` | `timestamp with time zone` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, processing_run_id) REFERENCES document_processing_runs(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX document_processing_artifacts_pkey ON public.document_processing_artifacts USING btree (id)`
- `CREATE UNIQUE INDEX document_processing_artifacts_organization_id_id_key ON public.document_processing_artifacts USING btree (organization_id, id)`
- `CREATE INDEX ix_document_processing_artifacts_fk1 ON public.document_processing_artifacts USING btree (organization_id, processing_run_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `document_processing_runs`

Historial de extracción/OCR/indexación y versión de herramienta.

Módulo: **documentation**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `document_version_id` | `uuid` | no | `—` |
| `job_id` | `uuid` | no | `—` |
| `tool` | `text` | no | `—` |
| `tool_version` | `text` | no | `—` |
| `status` | `text` | no | `'PENDING'::text` |
| `created_at` | `timestamp with time zone` | no | `now()` |
| `started_at` | `timestamp with time zone` | sí | `—` |
| `finished_at` | `timestamp with time zone` | sí | `—` |
| `error_code` | `text` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `CHECK ((status = ANY (ARRAY['PENDING'::text, 'RUNNING'::text, 'COMPLETED'::text, 'FAILED'::text, 'CANCELLED'::text, 'RETRYING'::text])))`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, document_version_id) REFERENCES document_versions(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT`
- `CHECK (((finished_at IS NULL) OR ((started_at IS NOT NULL) AND (finished_at >= started_at))))`

Relaciones entrantes: `document_processing_artifacts (organization_id,processing_run_id)`.

Índices:

- `CREATE UNIQUE INDEX document_processing_runs_pkey ON public.document_processing_runs USING btree (id)`
- `CREATE UNIQUE INDEX document_processing_runs_organization_id_id_key ON public.document_processing_runs USING btree (organization_id, id)`
- `CREATE INDEX ix_document_processing_runs_fk1 ON public.document_processing_runs USING btree (organization_id, document_version_id)`
- `CREATE INDEX ix_document_processing_runs_fk2 ON public.document_processing_runs USING btree (organization_id, job_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `document_repositories`

Asociaciones independientes de documentos y repositorios.

Módulo: **documentation**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `document_id` | `uuid` | no | `—` |
| `repository_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, document_id, repository_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX document_repositories_pkey ON public.document_repositories USING btree (id)`
- `CREATE UNIQUE INDEX document_repositories_organization_id_id_key ON public.document_repositories USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX document_repositories_organization_id_document_id_repositor_key ON public.document_repositories USING btree (organization_id, document_id, repository_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_document_repositories_fk2 ON public.document_repositories USING btree (organization_id, repository_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `document_review_flags`

Documento potencialmente afectado por un hallazgo, sujeto a revisión.

Módulo: **documentation**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `document_id` | `uuid` | no | `—` |
| `impact_finding_id` | `uuid` | no | `—` |
| `reason` | `text` | no | `—` |
| `status` | `text` | no | `'PENDING'::text` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((status = ANY (ARRAY['PENDING'::text, 'REVIEWED'::text, 'DISMISSED'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, document_id, impact_finding_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX document_review_flags_pkey ON public.document_review_flags USING btree (id)`
- `CREATE UNIQUE INDEX document_review_flags_organization_id_id_key ON public.document_review_flags USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX document_review_flags_organization_id_document_id_impact_fi_key ON public.document_review_flags USING btree (organization_id, document_id, impact_finding_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_document_review_flags_fk2 ON public.document_review_flags USING btree (organization_id, impact_finding_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `document_types`

Catálogo de referencia global, códigos estables; no acredita cobertura funcional del proveedor/ecosistema.

Módulo: **reference**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `documents (document_type_id)`.

Índices:

- `CREATE UNIQUE INDEX document_types_pkey ON public.document_types USING btree (id)`
- `CREATE UNIQUE INDEX document_types_code_key ON public.document_types USING btree (code) NULLS NOT DISTINCT`

## `document_versions`

Edición con hash/MIME; original solo temporal con expiración obligatoria.

Módulo: **documentation**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `document_id` | `uuid` | no | `—` |
| `version_number` | `integer` | no | `—` |
| `sha256` | `text` | no | `—` |
| `size_bytes` | `bigint` | no | `—` |
| `validated_mime_type` | `text` | no | `—` |
| `extractor_version` | `text` | sí | `—` |
| `temporary_storage_reference` | `text` | sí | `—` |
| `temporary_expires_at` | `timestamp with time zone` | sí | `—` |
| `original_deleted_at` | `timestamp with time zone` | sí | `—` |
| `uploaded_at` | `timestamp with time zone` | no | `now()` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((version_number > 0))`
- `CHECK ((sha256 ~ '^[a-f0-9]{64}$'::text))`
- `CHECK ((size_bytes >= 0))`
- `CHECK (((temporary_storage_reference IS NULL) OR (temporary_expires_at IS NOT NULL)))`
- `CHECK (((original_deleted_at IS NULL) OR (temporary_storage_reference IS NULL)))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, document_id, version_number)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `document_chunks (organization_id,document_version_id)`; `document_processing_runs (organization_id,document_version_id)`.

Índices:

- `CREATE UNIQUE INDEX document_versions_pkey ON public.document_versions USING btree (id)`
- `CREATE UNIQUE INDEX document_versions_organization_id_id_key ON public.document_versions USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX document_versions_organization_id_document_id_version_numbe_key ON public.document_versions USING btree (organization_id, document_id, version_number) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `documents`

Documento lógico, tipo, origen, autor y política de retención opcional.

Módulo: **documentation**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `name` | `text` | no | `—` |
| `document_type_id` | `uuid` | no | `—` |
| `origin` | `text` | no | `—` |
| `status` | `text` | no | `'UPLOADED'::text` |
| `uploaded_by_membership_id` | `uuid` | no | `—` |
| `retention_policy_id` | `uuid` | sí | `—` |
| `created_at` | `timestamp with time zone` | no | `now()` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((status = ANY (ARRAY['UPLOADED'::text, 'PROCESSING'::text, 'INDEXED'::text, 'FAILED'::text, 'DELETED'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (document_type_id) REFERENCES document_types(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, uploaded_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, retention_policy_id) REFERENCES data_retention_policies(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `deletion_request_documents (organization_id,document_id)`; `document_component_versions (organization_id,document_id)`; `document_components (organization_id,document_id)`; `document_repositories (organization_id,document_id)`; `document_review_flags (organization_id,document_id)`; `document_versions (organization_id,document_id)`; `impact_finding_documents (organization_id,document_id)`.

Índices:

- `CREATE UNIQUE INDEX documents_pkey ON public.documents USING btree (id)`
- `CREATE UNIQUE INDEX documents_organization_id_id_key ON public.documents USING btree (organization_id, id)`
- `CREATE INDEX ix_documents_fk1 ON public.documents USING btree (document_type_id)`
- `CREATE INDEX ix_documents_fk2 ON public.documents USING btree (organization_id, uploaded_by_membership_id)`
- `CREATE INDEX ix_documents_fk3 ON public.documents USING btree (organization_id, retention_policy_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `embedding_models`

Dimensión declarada de un modelo, sin elegir proveedor.

Módulo: **documentation**. Fase: **future**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `model_version_id` | `uuid` | no | `—` |
| `dimensions` | `integer` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((dimensions > 0))`
- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (model_version_id)`
- `FOREIGN KEY (model_version_id) REFERENCES assistant_model_versions(id) ON DELETE RESTRICT`

Relaciones entrantes: `document_chunk_embeddings (embedding_model_id)`.

Índices:

- `CREATE UNIQUE INDEX embedding_models_pkey ON public.embedding_models USING btree (id)`
- `CREATE UNIQUE INDEX embedding_models_model_version_id_key ON public.embedding_models USING btree (model_version_id) NULLS NOT DISTINCT`

## `finding_evidence`

Versión e identificador observados, rango y método de matching.

Módulo: **vulnerability**. Fase: **migration**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `finding_id` | `uuid` | no | `—` |
| `matched_range_id` | `uuid` | sí | `—` |
| `observed_version` | `text` | no | `—` |
| `package_identifier` | `text` | no | `—` |
| `method_version` | `text` | no | `—` |
| `explanation` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, finding_id) REFERENCES scan_vulnerability_findings(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (matched_range_id) REFERENCES vulnerability_affected_ranges(id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX finding_evidence_pkey ON public.finding_evidence USING btree (id)`
- `CREATE UNIQUE INDEX finding_evidence_organization_id_id_key ON public.finding_evidence USING btree (organization_id, id)`
- `CREATE INDEX ix_finding_evidence_fk1 ON public.finding_evidence USING btree (organization_id, finding_id)`
- `CREATE INDEX ix_finding_evidence_fk2 ON public.finding_evidence USING btree (matched_range_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `github_installations`

Instalación de GitHub App asociada a una integración.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `integration_id` | `uuid` | no | `—` |
| `installation_id` | `bigint` | no | `—` |
| `app_id` | `bigint` | no | `—` |
| `suspended_at` | `timestamp with time zone` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((installation_id > 0))`
- `CHECK ((app_id > 0))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, integration_id)`
- `UNIQUE NULLS NOT DISTINCT (installation_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, integration_id) REFERENCES integrations(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX github_installations_pkey ON public.github_installations USING btree (id)`
- `CREATE UNIQUE INDEX github_installations_organization_id_id_key ON public.github_installations USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX github_installations_organization_id_integration_id_key ON public.github_installations USING btree (organization_id, integration_id) NULLS NOT DISTINCT`
- `CREATE UNIQUE INDEX github_installations_installation_id_key ON public.github_installations USING btree (installation_id) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `impact_analyses`

Operación propuesta sobre un scan/commit base; no modifica repositorios.

Módulo: **impact**. Fase: **future**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `repository_id` | `uuid` | no | `—` |
| `base_scan_id` | `uuid` | no | `—` |
| `current_scan_component_id` | `uuid` | sí | `—` |
| `target_component_version_id` | `uuid` | sí | `—` |
| `operation` | `text` | no | `—` |
| `requested_by_membership_id` | `uuid` | no | `—` |
| `method_version` | `text` | no | `—` |
| `status` | `text` | no | `'PENDING'::text` |
| `created_at` | `timestamp with time zone` | no | `now()` |
| `started_at` | `timestamp with time zone` | sí | `—` |
| `finished_at` | `timestamp with time zone` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((operation = ANY (ARRAY['ADD'::text, 'UPDATE'::text, 'REMOVE'::text, 'REPLACE'::text])))`
- `CHECK ((status = ANY (ARRAY['PENDING'::text, 'RUNNING'::text, 'COMPLETED'::text, 'FAILED'::text, 'CANCELLED'::text, 'RETRYING'::text])))`
- `CHECK ((((operation = 'ADD'::text) AND (current_scan_component_id IS NULL) AND (target_component_version_id IS NOT NULL)) OR ((operation = 'REMOVE'::text) AND (current_scan_component_id IS NOT NULL) AND (target_component_version_id IS NULL)) OR ((operation = ANY (ARRAY['UPDATE'::text, 'REPLACE'::text])) AND (current_scan_component_id IS NOT NULL) AND (target_component_version_id IS NOT NULL))))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, repository_id, base_scan_id) REFERENCES scans(organization_id, repository_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, base_scan_id, current_scan_component_id) REFERENCES scan_components(organization_id, scan_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, target_component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`
- `CHECK (((finished_at IS NULL) OR ((started_at IS NOT NULL) AND (finished_at >= started_at))))`

Relaciones entrantes: `impact_findings (organization_id,impact_analysis_id)`.

Índices:

- `CREATE UNIQUE INDEX impact_analyses_pkey ON public.impact_analyses USING btree (id)`
- `CREATE UNIQUE INDEX impact_analyses_organization_id_id_key ON public.impact_analyses USING btree (organization_id, id)`
- `CREATE INDEX ix_impact_analyses_fk1 ON public.impact_analyses USING btree (organization_id, repository_id, base_scan_id)`
- `CREATE INDEX ix_impact_analyses_fk2 ON public.impact_analyses USING btree (organization_id, base_scan_id, current_scan_component_id)`
- `CREATE INDEX ix_impact_analyses_fk3 ON public.impact_analyses USING btree (organization_id, target_component_version_id)`
- `CREATE INDEX ix_impact_analyses_fk4 ON public.impact_analyses USING btree (organization_id, requested_by_membership_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `impact_evidences`

Evidencia sanitizada con origen, fecha, ubicación y hash opcional.

Módulo: **impact**. Fase: **future**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `impact_finding_id` | `uuid` | no | `—` |
| `evidence_type` | `text` | no | `—` |
| `source_reference` | `text` | no | `—` |
| `excerpt_redacted` | `text` | sí | `—` |
| `page_number` | `integer` | sí | `—` |
| `observed_at` | `timestamp with time zone` | no | `—` |
| `sha256` | `text` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((page_number > 0))`
- `CHECK ((sha256 ~ '^[a-f0-9]{64}$'::text))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `impact_recommendation_evidences (organization_id,evidence_id)`.

Índices:

- `CREATE UNIQUE INDEX impact_evidences_pkey ON public.impact_evidences USING btree (id)`
- `CREATE UNIQUE INDEX impact_evidences_organization_id_id_key ON public.impact_evidences USING btree (organization_id, id)`
- `CREATE INDEX ix_impact_evidences_fk1 ON public.impact_evidences USING btree (organization_id, impact_finding_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `impact_finding_components`

Asociación tipada de un hallazgo de impacto; evita FKs polimórficas.

Módulo: **impact**. Fase: **future**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `impact_finding_id` | `uuid` | no | `—` |
| `component_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, impact_finding_id, component_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, component_id) REFERENCES components(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX impact_finding_components_pkey ON public.impact_finding_components USING btree (id)`
- `CREATE UNIQUE INDEX impact_finding_components_organization_id_id_key ON public.impact_finding_components USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX impact_finding_components_organization_id_impact_finding_id_key ON public.impact_finding_components USING btree (organization_id, impact_finding_id, component_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_impact_finding_components_fk2 ON public.impact_finding_components USING btree (organization_id, component_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `impact_finding_documents`

Asociación tipada de un hallazgo de impacto; evita FKs polimórficas.

Módulo: **impact**. Fase: **future**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `impact_finding_id` | `uuid` | no | `—` |
| `document_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, impact_finding_id, document_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX impact_finding_documents_pkey ON public.impact_finding_documents USING btree (id)`
- `CREATE UNIQUE INDEX impact_finding_documents_organization_id_id_key ON public.impact_finding_documents USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX impact_finding_documents_organization_id_impact_finding_id__key ON public.impact_finding_documents USING btree (organization_id, impact_finding_id, document_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_impact_finding_documents_fk2 ON public.impact_finding_documents USING btree (organization_id, document_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `impact_finding_repositories`

Asociación tipada de un hallazgo de impacto; evita FKs polimórficas.

Módulo: **impact**. Fase: **future**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `impact_finding_id` | `uuid` | no | `—` |
| `repository_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, impact_finding_id, repository_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX impact_finding_repositories_pkey ON public.impact_finding_repositories USING btree (id)`
- `CREATE UNIQUE INDEX impact_finding_repositories_organization_id_id_key ON public.impact_finding_repositories USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX impact_finding_repositories_organization_id_impact_finding__key ON public.impact_finding_repositories USING btree (organization_id, impact_finding_id, repository_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_impact_finding_repositories_fk2 ON public.impact_finding_repositories USING btree (organization_id, repository_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `impact_finding_versions`

Asociación tipada de un hallazgo de impacto; evita FKs polimórficas.

Módulo: **impact**. Fase: **future**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `impact_finding_id` | `uuid` | no | `—` |
| `component_version_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, impact_finding_id, component_version_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX impact_finding_versions_pkey ON public.impact_finding_versions USING btree (id)`
- `CREATE UNIQUE INDEX impact_finding_versions_organization_id_id_key ON public.impact_finding_versions USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX impact_finding_versions_organization_id_impact_finding_id_c_key ON public.impact_finding_versions USING btree (organization_id, impact_finding_id, component_version_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_impact_finding_versions_fk2 ON public.impact_finding_versions USING btree (organization_id, component_version_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `impact_finding_vulnerabilities`

Asociación tipada de un hallazgo de impacto; evita FKs polimórficas.

Módulo: **impact**. Fase: **future**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `impact_finding_id` | `uuid` | no | `—` |
| `vulnerability_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities(id) ON DELETE RESTRICT`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, impact_finding_id, vulnerability_id)`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX impact_finding_vulnerabilities_pkey ON public.impact_finding_vulnerabilities USING btree (id)`
- `CREATE UNIQUE INDEX impact_finding_vulnerabilities_organization_id_id_key ON public.impact_finding_vulnerabilities USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX impact_finding_vulnerabilitie_organization_id_impact_findin_key ON public.impact_finding_vulnerabilities USING btree (organization_id, impact_finding_id, vulnerability_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_impact_finding_vulnerabilities_fk2 ON public.impact_finding_vulnerabilities USING btree (vulnerability_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `impact_findings`

Hallazgos KNOWN/POTENTIAL/VALIDATED; confianza técnica entre 0 y 1.

Módulo: **impact**. Fase: **future**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `impact_analysis_id` | `uuid` | no | `—` |
| `impact_type` | `text` | no | `—` |
| `classification` | `text` | no | `—` |
| `severity_level_id` | `uuid` | sí | `—` |
| `confidence` | `numeric(6,5)` | sí | `—` |
| `summary` | `text` | no | `—` |
| `review_status` | `text` | no | `'PENDING'::text` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((classification = ANY (ARRAY['KNOWN'::text, 'POTENTIAL'::text, 'VALIDATED'::text])))`
- `CHECK (((confidence >= (0)::numeric) AND (confidence <= (1)::numeric)))`
- `CHECK ((review_status = ANY (ARRAY['PENDING'::text, 'ACCEPTED'::text, 'REJECTED'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, impact_analysis_id) REFERENCES impact_analyses(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (severity_level_id) REFERENCES severity_levels(id) ON DELETE RESTRICT`

Relaciones entrantes: `document_review_flags (organization_id,impact_finding_id)`; `impact_evidences (organization_id,impact_finding_id)`; `impact_finding_components (organization_id,impact_finding_id)`; `impact_finding_documents (organization_id,impact_finding_id)`; `impact_finding_repositories (organization_id,impact_finding_id)`; `impact_finding_versions (organization_id,impact_finding_id)`; `impact_finding_vulnerabilities (organization_id,impact_finding_id)`; `impact_recommendations (organization_id,impact_finding_id)`; `impact_test_suggestions (organization_id,impact_finding_id)`.

Índices:

- `CREATE UNIQUE INDEX impact_findings_pkey ON public.impact_findings USING btree (id)`
- `CREATE UNIQUE INDEX impact_findings_organization_id_id_key ON public.impact_findings USING btree (organization_id, id)`
- `CREATE INDEX ix_impact_findings_fk1 ON public.impact_findings USING btree (organization_id, impact_analysis_id)`
- `CREATE INDEX ix_impact_findings_fk2 ON public.impact_findings USING btree (severity_level_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `impact_recommendation_evidences`

Fundamento M:N de recomendaciones mediante evidencias.

Módulo: **impact**. Fase: **future**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `recommendation_id` | `uuid` | no | `—` |
| `evidence_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, recommendation_id, evidence_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, recommendation_id) REFERENCES impact_recommendations(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, evidence_id) REFERENCES impact_evidences(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX impact_recommendation_evidences_pkey ON public.impact_recommendation_evidences USING btree (id)`
- `CREATE UNIQUE INDEX impact_recommendation_evidences_organization_id_id_key ON public.impact_recommendation_evidences USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX impact_recommendation_evidenc_organization_id_recommendatio_key ON public.impact_recommendation_evidences USING btree (organization_id, recommendation_id, evidence_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_impact_recommendation_evidences_fk2 ON public.impact_recommendation_evidences USING btree (organization_id, evidence_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `impact_recommendations`

Acción sugerida y prioridad; requiere interpretación de aplicación.

Módulo: **impact**. Fase: **future**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `impact_finding_id` | `uuid` | no | `—` |
| `action` | `text` | no | `—` |
| `priority` | `integer` | no | `—` |
| `explanation` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((priority >= 0))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `impact_recommendation_evidences (organization_id,recommendation_id)`.

Índices:

- `CREATE UNIQUE INDEX impact_recommendations_pkey ON public.impact_recommendations USING btree (id)`
- `CREATE UNIQUE INDEX impact_recommendations_organization_id_id_key ON public.impact_recommendations USING btree (organization_id, id)`
- `CREATE INDEX ix_impact_recommendations_fk1 ON public.impact_recommendations USING btree (organization_id, impact_finding_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `impact_test_suggestions`

Pruebas sugeridas para comprobar un impacto.

Módulo: **impact**. Fase: **future**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `impact_finding_id` | `uuid` | no | `—` |
| `category` | `text` | no | `—` |
| `description` | `text` | no | `—` |
| `priority` | `integer` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((priority >= 0))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX impact_test_suggestions_pkey ON public.impact_test_suggestions USING btree (id)`
- `CREATE UNIQUE INDEX impact_test_suggestions_organization_id_id_key ON public.impact_test_suggestions USING btree (organization_id, id)`
- `CREATE INDEX ix_impact_test_suggestions_fk1 ON public.impact_test_suggestions USING btree (organization_id, impact_finding_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `integration_providers`

Catálogo de referencia global, códigos estables; no acredita cobertura funcional del proveedor/ecosistema.

Módulo: **reference**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `integrations (provider_id)`.

Índices:

- `CREATE UNIQUE INDEX integration_providers_pkey ON public.integration_providers USING btree (id)`
- `CREATE UNIQUE INDEX integration_providers_code_key ON public.integration_providers USING btree (code) NULLS NOT DISTINCT`

## `integration_sync_runs`

Historial de sincronización con estados de proceso.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `integration_id` | `uuid` | no | `—` |
| `status` | `text` | no | `'PENDING'::text` |
| `created_at` | `timestamp with time zone` | no | `now()` |
| `started_at` | `timestamp with time zone` | sí | `—` |
| `finished_at` | `timestamp with time zone` | sí | `—` |
| `error_code` | `text` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((status = ANY (ARRAY['PENDING'::text, 'RUNNING'::text, 'COMPLETED'::text, 'FAILED'::text, 'CANCELLED'::text, 'RETRYING'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, integration_id) REFERENCES integrations(organization_id, id) ON DELETE RESTRICT`
- `CHECK (((finished_at IS NULL) OR ((started_at IS NOT NULL) AND (finished_at >= started_at))))`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX integration_sync_runs_pkey ON public.integration_sync_runs USING btree (id)`
- `CREATE UNIQUE INDEX integration_sync_runs_organization_id_id_key ON public.integration_sync_runs USING btree (organization_id, id)`
- `CREATE INDEX ix_integration_sync_runs_fk1 ON public.integration_sync_runs USING btree (organization_id, integration_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `integrations`

Cuenta de proveedor conectada por tenant; solo referencias a credenciales externas.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `provider_id` | `uuid` | no | `—` |
| `external_account_id` | `text` | no | `—` |
| `external_account_login` | `text` | no | `—` |
| `status` | `text` | no | `'CONNECTED'::text` |
| `credential_reference` | `text` | sí | `—` |
| `connected_at` | `timestamp with time zone` | no | `now()` |
| `disconnected_at` | `timestamp with time zone` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((status = ANY (ARRAY['CONNECTED'::text, 'SUSPENDED'::text, 'DISCONNECTED'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, provider_id, external_account_id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, id, provider_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (provider_id) REFERENCES integration_providers(id) ON DELETE RESTRICT`

Relaciones entrantes: `github_installations (organization_id,integration_id)`; `integration_sync_runs (organization_id,integration_id)`; `repositories (organization_id,integration_id)`; `webhook_events (organization_id,integration_id,provider_id)`.

Índices:

- `CREATE UNIQUE INDEX integrations_pkey ON public.integrations USING btree (id)`
- `CREATE UNIQUE INDEX integrations_organization_id_id_key ON public.integrations USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX integrations_organization_id_provider_id_external_account_i_key ON public.integrations USING btree (organization_id, provider_id, external_account_id) NULLS NOT DISTINCT`
- `CREATE UNIQUE INDEX integrations_organization_id_id_provider_id_key ON public.integrations USING btree (organization_id, id, provider_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_integrations_fk1 ON public.integrations USING btree (provider_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `job_types`

Catálogo de referencia global, códigos estables; no acredita cobertura funcional del proveedor/ecosistema.

Módulo: **reference**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `background_jobs (job_type_id)`.

Índices:

- `CREATE UNIQUE INDEX job_types_pkey ON public.job_types USING btree (id)`
- `CREATE UNIQUE INDEX job_types_code_key ON public.job_types USING btree (code) NULLS NOT DISTINCT`

## `license_catalog`

Catálogo de referencia global, códigos estables; no acredita cobertura funcional del proveedor/ecosistema.

Módulo: **reference**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `component_licenses (license_id)`; `scan_component_licenses (license_id)`.

Índices:

- `CREATE UNIQUE INDEX license_catalog_pkey ON public.license_catalog USING btree (id)`
- `CREATE UNIQUE INDEX license_catalog_code_key ON public.license_catalog USING btree (code) NULLS NOT DISTINCT`

## `membership_invitations`

Invitaciones con token hasheado y expiración; nunca el token original.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `email_normalized` | `text` | no | `—` |
| `token_hash` | `text` | no | `—` |
| `expires_at` | `timestamp with time zone` | no | `—` |
| `inviter_membership_id` | `uuid` | no | `—` |
| `accepted_membership_id` | `uuid` | sí | `—` |
| `status` | `text` | no | `'PENDING'::text` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((status = ANY (ARRAY['PENDING'::text, 'ACCEPTED'::text, 'EXPIRED'::text, 'REVOKED'::text])))`
- `CHECK ((email_normalized = lower(btrim(email_normalized))))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (token_hash)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, inviter_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, accepted_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX membership_invitations_pkey ON public.membership_invitations USING btree (id)`
- `CREATE UNIQUE INDEX membership_invitations_organization_id_id_key ON public.membership_invitations USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX membership_invitations_token_hash_key ON public.membership_invitations USING btree (token_hash) NULLS NOT DISTINCT`
- `CREATE INDEX ix_membership_invitations_fk1 ON public.membership_invitations USING btree (organization_id, inviter_membership_id)`
- `CREATE INDEX ix_membership_invitations_fk2 ON public.membership_invitations USING btree (organization_id, accepted_membership_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `membership_roles`

Roles independientes asignados a una membresía.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `membership_id` | `uuid` | no | `—` |
| `role_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, membership_id, role_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX membership_roles_pkey ON public.membership_roles USING btree (id)`
- `CREATE UNIQUE INDEX membership_roles_organization_id_id_key ON public.membership_roles USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX membership_roles_organization_id_membership_id_role_id_key ON public.membership_roles USING btree (organization_id, membership_id, role_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_membership_roles_fk2 ON public.membership_roles USING btree (role_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `notification_channels`

Catálogo de referencia global, códigos estables; no acredita cobertura funcional del proveedor/ecosistema.

Módulo: **reference**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `notification_deliveries (channel_id)`.

Índices:

- `CREATE UNIQUE INDEX notification_channels_pkey ON public.notification_channels USING btree (id)`
- `CREATE UNIQUE INDEX notification_channels_code_key ON public.notification_channels USING btree (code) NULLS NOT DISTINCT`

## `notification_deliveries`

Entrega independiente por canal/destinatario, sin duplicados.

Módulo: **notification**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `notification_id` | `uuid` | no | `—` |
| `channel_id` | `uuid` | no | `—` |
| `recipient_membership_id` | `uuid` | no | `—` |
| `status` | `text` | no | `'PENDING'::text` |
| `created_at` | `timestamp with time zone` | no | `now()` |
| `started_at` | `timestamp with time zone` | sí | `—` |
| `finished_at` | `timestamp with time zone` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((status = ANY (ARRAY['PENDING'::text, 'RUNNING'::text, 'COMPLETED'::text, 'FAILED'::text, 'CANCELLED'::text, 'RETRYING'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, notification_id, channel_id, recipient_membership_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, notification_id) REFERENCES notifications(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (channel_id) REFERENCES notification_channels(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, recipient_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`
- `CHECK (((finished_at IS NULL) OR ((started_at IS NOT NULL) AND (finished_at >= started_at))))`

Relaciones entrantes: `notification_delivery_attempts (organization_id,delivery_id)`.

Índices:

- `CREATE UNIQUE INDEX notification_deliveries_pkey ON public.notification_deliveries USING btree (id)`
- `CREATE UNIQUE INDEX notification_deliveries_organization_id_id_key ON public.notification_deliveries USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX notification_deliveries_organization_id_notification_id_cha_key ON public.notification_deliveries USING btree (organization_id, notification_id, channel_id, recipient_membership_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_notification_deliveries_fk2 ON public.notification_deliveries USING btree (channel_id)`
- `CREATE INDEX ix_notification_deliveries_fk3 ON public.notification_deliveries USING btree (organization_id, recipient_membership_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `notification_delivery_attempts`

Intentos numerados de entrega.

Módulo: **notification**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `delivery_id` | `uuid` | no | `—` |
| `attempt_number` | `integer` | no | `—` |
| `error_code` | `text` | sí | `—` |
| `status` | `text` | no | `'PENDING'::text` |
| `created_at` | `timestamp with time zone` | no | `now()` |
| `started_at` | `timestamp with time zone` | sí | `—` |
| `finished_at` | `timestamp with time zone` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((attempt_number > 0))`
- `CHECK ((status = ANY (ARRAY['PENDING'::text, 'RUNNING'::text, 'COMPLETED'::text, 'FAILED'::text, 'CANCELLED'::text, 'RETRYING'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, delivery_id, attempt_number)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, delivery_id) REFERENCES notification_deliveries(organization_id, id) ON DELETE RESTRICT`
- `CHECK (((finished_at IS NULL) OR ((started_at IS NOT NULL) AND (finished_at >= started_at))))`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX notification_delivery_attempts_pkey ON public.notification_delivery_attempts USING btree (id)`
- `CREATE UNIQUE INDEX notification_delivery_attempts_organization_id_id_key ON public.notification_delivery_attempts USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX notification_delivery_attempt_organization_id_delivery_id_a_key ON public.notification_delivery_attempts USING btree (organization_id, delivery_id, attempt_number) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `notification_findings`

Findings relacionados con una notificación mediante FK real.

Módulo: **notification**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `notification_id` | `uuid` | no | `—` |
| `finding_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, notification_id, finding_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, notification_id) REFERENCES notifications(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, finding_id) REFERENCES scan_vulnerability_findings(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX notification_findings_pkey ON public.notification_findings USING btree (id)`
- `CREATE UNIQUE INDEX notification_findings_organization_id_id_key ON public.notification_findings USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX notification_findings_organization_id_notification_id_findi_key ON public.notification_findings USING btree (organization_id, notification_id, finding_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_notification_findings_fk2 ON public.notification_findings USING btree (organization_id, finding_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `notification_rules`

Reglas por evento, deshabilitadas por defecto.

Módulo: **notification**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `name` | `text` | no | `—` |
| `event_type` | `text` | no | `—` |
| `is_enabled` | `boolean` | no | `false` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, name)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`

Relaciones entrantes: `notification_subscriptions (organization_id,rule_id)`; `notifications (organization_id,rule_id)`.

Índices:

- `CREATE UNIQUE INDEX notification_rules_pkey ON public.notification_rules USING btree (id)`
- `CREATE UNIQUE INDEX notification_rules_organization_id_id_key ON public.notification_rules USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX notification_rules_organization_id_name_key ON public.notification_rules USING btree (organization_id, name) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `notification_scans`

Scans relacionados con una notificación mediante FK real.

Módulo: **notification**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `notification_id` | `uuid` | no | `—` |
| `scan_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, notification_id, scan_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, notification_id) REFERENCES notifications(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX notification_scans_pkey ON public.notification_scans USING btree (id)`
- `CREATE UNIQUE INDEX notification_scans_organization_id_id_key ON public.notification_scans USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX notification_scans_organization_id_notification_id_scan_id_key ON public.notification_scans USING btree (organization_id, notification_id, scan_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_notification_scans_fk2 ON public.notification_scans USING btree (organization_id, scan_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `notification_subscriptions`

Suscripciones independientes de miembros a reglas.

Módulo: **notification**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `rule_id` | `uuid` | no | `—` |
| `membership_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, rule_id, membership_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, rule_id) REFERENCES notification_rules(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX notification_subscriptions_pkey ON public.notification_subscriptions USING btree (id)`
- `CREATE UNIQUE INDEX notification_subscriptions_organization_id_id_key ON public.notification_subscriptions USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX notification_subscriptions_organization_id_rule_id_membersh_key ON public.notification_subscriptions USING btree (organization_id, rule_id, membership_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_notification_subscriptions_fk2 ON public.notification_subscriptions USING btree (organization_id, membership_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `notifications`

Evento lógico deduplicado; independiente de los canales de entrega.

Módulo: **notification**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `rule_id` | `uuid` | no | `—` |
| `deduplication_key` | `text` | no | `—` |
| `title` | `text` | no | `—` |
| `summary` | `text` | sí | `—` |
| `created_at` | `timestamp with time zone` | no | `now()` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, deduplication_key)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, rule_id) REFERENCES notification_rules(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `notification_deliveries (organization_id,notification_id)`; `notification_findings (organization_id,notification_id)`; `notification_scans (organization_id,notification_id)`.

Índices:

- `CREATE UNIQUE INDEX notifications_pkey ON public.notifications USING btree (id)`
- `CREATE UNIQUE INDEX notifications_organization_id_id_key ON public.notifications USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX notifications_organization_id_deduplication_key_key ON public.notifications USING btree (organization_id, deduplication_key) NULLS NOT DISTINCT`
- `CREATE INDEX ix_notifications_fk1 ON public.notifications USING btree (organization_id, rule_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `organization_memberships`

Pertenencia y estado de un usuario dentro de una organización.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `user_id` | `uuid` | no | `—` |
| `status` | `text` | no | `'ACTIVE'::text` |
| `joined_at` | `timestamp with time zone` | no | `now()` |
| `invited_by_user_id` | `uuid` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((status = ANY (ARRAY['INVITED'::text, 'ACTIVE'::text, 'SUSPENDED'::text, 'LEFT'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, user_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT`
- `FOREIGN KEY (invited_by_user_id) REFERENCES users(id) ON DELETE RESTRICT`

Relaciones entrantes: `assistant_conversations (organization_id,owner_membership_id)`; `audit_events (organization_id,actor_membership_id)`; `data_deletion_requests (organization_id,requested_by_membership_id)`; `documents (organization_id,uploaded_by_membership_id)`; `impact_analyses (organization_id,requested_by_membership_id)`; `membership_invitations (organization_id,inviter_membership_id)`; `membership_invitations (organization_id,accepted_membership_id)`; `membership_roles (organization_id,membership_id)`; `notification_deliveries (organization_id,recipient_membership_id)`; `notification_subscriptions (organization_id,membership_id)`; `report_jobs (organization_id,requested_by_membership_id)`; `report_schedule_recipients (organization_id,membership_id)`; `scans (organization_id,requested_by_membership_id)`; `team_memberships (organization_id,membership_id)`.

Índices:

- `CREATE UNIQUE INDEX organization_memberships_pkey ON public.organization_memberships USING btree (id)`
- `CREATE UNIQUE INDEX organization_memberships_organization_id_id_key ON public.organization_memberships USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX organization_memberships_organization_id_user_id_key ON public.organization_memberships USING btree (organization_id, user_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_organization_memberships_fk1 ON public.organization_memberships USING btree (user_id)`
- `CREATE INDEX ix_organization_memberships_fk2 ON public.organization_memberships USING btree (invited_by_user_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `organization_teams`

Agrupaciones internas de una organización.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, name)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`

Relaciones entrantes: `repository_team_access (organization_id,team_id)`; `team_memberships (organization_id,team_id)`.

Índices:

- `CREATE UNIQUE INDEX organization_teams_pkey ON public.organization_teams USING btree (id)`
- `CREATE UNIQUE INDEX organization_teams_organization_id_id_key ON public.organization_teams USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX organization_teams_organization_id_name_key ON public.organization_teams USING btree (organization_id, name) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `organizations`

Límite de propiedad y aislamiento del cliente.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant (identidad protegida por política específica)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `name` | `text` | no | `—` |
| `slug` | `text` | no | `—` |
| `legal_name` | `text` | sí | `—` |
| `status` | `text` | no | `'ACTIVE'::text` |
| `created_at` | `timestamp with time zone` | no | `now()` |
| `updated_at` | `timestamp with time zone` | no | `now()` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'CLOSED'::text])))`
- `CHECK ((slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'::text))`
- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (slug)`

Relaciones entrantes: `assistant_conversations (organization_id)`; `assistant_message_evidences (organization_id)`; `assistant_messages (organization_id)`; `audit_event_changes (organization_id)`; `audit_events (organization_id)`; `background_job_attempts (organization_id)`; `background_job_events (organization_id)`; `background_jobs (organization_id)`; `change_record_evidences (organization_id)`; `component_external_references (organization_id)`; `component_hashes (organization_id)`; `component_identifiers (organization_id)`; `component_licenses (organization_id)`; `component_versions (organization_id)`; `components (organization_id)`; `data_deletion_requests (organization_id)`; `data_retention_policies (organization_id)`; `deletion_request_documents (organization_id)`; `deletion_request_repositories (organization_id)`; `dependency_edges (organization_id)`; `document_chunk_embeddings (organization_id)`; `document_chunks (organization_id)`; `document_component_versions (organization_id)`; `document_components (organization_id)`; `document_processing_artifacts (organization_id)`; `document_processing_runs (organization_id)`; `document_repositories (organization_id)`; `document_review_flags (organization_id)`; `document_versions (organization_id)`; `documents (organization_id)`; `finding_evidence (organization_id)`; `github_installations (organization_id)`; `impact_analyses (organization_id)`; `impact_evidences (organization_id)`; `impact_finding_components (organization_id)`; `impact_finding_documents (organization_id)`; `impact_finding_repositories (organization_id)`; `impact_finding_versions (organization_id)`; `impact_finding_vulnerabilities (organization_id)`; `impact_findings (organization_id)`; `impact_recommendation_evidences (organization_id)`; `impact_recommendations (organization_id)`; `impact_test_suggestions (organization_id)`; `integration_sync_runs (organization_id)`; `integrations (organization_id)`; `membership_invitations (organization_id)`; `membership_roles (organization_id)`; `notification_deliveries (organization_id)`; `notification_delivery_attempts (organization_id)`; `notification_findings (organization_id)`; `notification_rules (organization_id)`; `notification_scans (organization_id)`; `notification_subscriptions (organization_id)`; `notifications (organization_id)`; `organization_memberships (organization_id)`; `organization_teams (organization_id)`; `report_artifacts (organization_id)`; `report_job_scans (organization_id)`; `report_jobs (organization_id)`; `report_schedule_recipients (organization_id)`; `report_schedules (organization_id)`; `repositories (organization_id)`; `repository_branches (organization_id)`; `repository_commits (organization_id)`; `repository_settings (organization_id)`; `repository_team_access (organization_id)`; `sbom_documents (organization_id)`; `sbom_external_references (organization_id)`; `scan_artifacts (organization_id)`; `scan_comparisons (organization_id)`; `scan_component_changes (organization_id)`; `scan_component_licenses (organization_id)`; `scan_components (organization_id)`; `scan_logs (organization_id)`; `scan_profile_ecosystems (organization_id)`; `scan_profiles (organization_id)`; `scan_stage_executions (organization_id)`; `scan_vulnerability_findings (organization_id)`; `scans (organization_id)`; `team_memberships (organization_id)`; `version_change_records (organization_id)`; `webhook_events (organization_id)`.

Índices:

- `CREATE UNIQUE INDEX organizations_pkey ON public.organizations USING btree (id)`
- `CREATE UNIQUE INDEX organizations_slug_key ON public.organizations USING btree (slug) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(id = app.current_organization_id())` WITH CHECK `(id = app.current_organization_id())`.

## `package_ecosystems`

Catálogo de referencia global, códigos estables; no acredita cobertura funcional del proveedor/ecosistema.

Módulo: **reference**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `components (package_ecosystem_id)`; `scan_profile_ecosystems (package_ecosystem_id)`; `vulnerability_affected_packages (package_ecosystem_id)`.

Índices:

- `CREATE UNIQUE INDEX package_ecosystems_pkey ON public.package_ecosystems USING btree (id)`
- `CREATE UNIQUE INDEX package_ecosystems_code_key ON public.package_ecosystems USING btree (code) NULLS NOT DISTINCT`

## `permissions`

Permisos atómicos de autorización.

Módulo: **identity**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `role_permissions (permission_id)`.

Índices:

- `CREATE UNIQUE INDEX permissions_pkey ON public.permissions USING btree (id)`
- `CREATE UNIQUE INDEX permissions_code_key ON public.permissions USING btree (code) NULLS NOT DISTINCT`

## `report_artifacts`

Archivo derivado con hash, ubicación y expiración.

Módulo: **reporting**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `report_job_id` | `uuid` | no | `—` |
| `sha256` | `text` | no | `—` |
| `size_bytes` | `bigint` | no | `—` |
| `content_type` | `text` | no | `—` |
| `storage_reference` | `text` | no | `—` |
| `generated_at` | `timestamp with time zone` | no | `now()` |
| `expires_at` | `timestamp with time zone` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((sha256 ~ '^[a-f0-9]{64}$'::text))`
- `CHECK ((size_bytes >= 0))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, report_job_id) REFERENCES report_jobs(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX report_artifacts_pkey ON public.report_artifacts USING btree (id)`
- `CREATE UNIQUE INDEX report_artifacts_organization_id_id_key ON public.report_artifacts USING btree (organization_id, id)`
- `CREATE INDEX ix_report_artifacts_fk1 ON public.report_artifacts USING btree (organization_id, report_job_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `report_definitions`

Definiciones globales disponibles de reportes.

Módulo: **reporting**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `report_type_id` | `uuid` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (name)`
- `FOREIGN KEY (report_type_id) REFERENCES report_types(id) ON DELETE RESTRICT`

Relaciones entrantes: `report_jobs (definition_id)`; `report_schedules (definition_id)`.

Índices:

- `CREATE UNIQUE INDEX report_definitions_pkey ON public.report_definitions USING btree (id)`
- `CREATE UNIQUE INDEX report_definitions_name_key ON public.report_definitions USING btree (name) NULLS NOT DISTINCT`
- `CREATE INDEX ix_report_definitions_fk0 ON public.report_definitions USING btree (report_type_id)`

## `report_formats`

Catálogo de referencia global, códigos estables; no acredita cobertura funcional del proveedor/ecosistema.

Módulo: **reference**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `report_jobs (format_id)`; `report_schedules (format_id)`.

Índices:

- `CREATE UNIQUE INDEX report_formats_pkey ON public.report_formats USING btree (id)`
- `CREATE UNIQUE INDEX report_formats_code_key ON public.report_formats USING btree (code) NULLS NOT DISTINCT`

## `report_job_scans`

Snapshots exactos que conforman el scope reproducible del reporte.

Módulo: **reporting**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `report_job_id` | `uuid` | no | `—` |
| `scan_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, report_job_id, scan_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, report_job_id) REFERENCES report_jobs(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX report_job_scans_pkey ON public.report_job_scans USING btree (id)`
- `CREATE UNIQUE INDEX report_job_scans_organization_id_id_key ON public.report_job_scans USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX report_job_scans_organization_id_report_job_id_scan_id_key ON public.report_job_scans USING btree (organization_id, report_job_id, scan_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_report_job_scans_fk2 ON public.report_job_scans USING btree (organization_id, scan_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `report_jobs`

Ejecución de reporte; parámetros técnicos, formato y solicitante.

Módulo: **reporting**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `definition_id` | `uuid` | no | `—` |
| `format_id` | `uuid` | no | `—` |
| `requested_by_membership_id` | `uuid` | no | `—` |
| `job_id` | `uuid` | no | `—` |
| `status` | `text` | no | `'PENDING'::text` |
| `created_at` | `timestamp with time zone` | no | `now()` |
| `started_at` | `timestamp with time zone` | sí | `—` |
| `finished_at` | `timestamp with time zone` | sí | `—` |
| `parameters` | `jsonb` | no | `'{}'::jsonb` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((status = ANY (ARRAY['PENDING'::text, 'RUNNING'::text, 'COMPLETED'::text, 'FAILED'::text, 'CANCELLED'::text, 'RETRYING'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, job_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (definition_id) REFERENCES report_definitions(id) ON DELETE RESTRICT`
- `FOREIGN KEY (format_id) REFERENCES report_formats(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT`
- `CHECK (((finished_at IS NULL) OR ((started_at IS NOT NULL) AND (finished_at >= started_at))))`

Relaciones entrantes: `report_artifacts (organization_id,report_job_id)`; `report_job_scans (organization_id,report_job_id)`.

Índices:

- `CREATE UNIQUE INDEX report_jobs_pkey ON public.report_jobs USING btree (id)`
- `CREATE UNIQUE INDEX report_jobs_organization_id_id_key ON public.report_jobs USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX report_jobs_organization_id_job_id_key ON public.report_jobs USING btree (organization_id, job_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_report_jobs_fk1 ON public.report_jobs USING btree (definition_id)`
- `CREATE INDEX ix_report_jobs_fk2 ON public.report_jobs USING btree (format_id)`
- `CREATE INDEX ix_report_jobs_fk3 ON public.report_jobs USING btree (organization_id, requested_by_membership_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `report_schedule_recipients`

Destinatarios miembros separados en filas.

Módulo: **reporting**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `schedule_id` | `uuid` | no | `—` |
| `membership_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, schedule_id, membership_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, schedule_id) REFERENCES report_schedules(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX report_schedule_recipients_pkey ON public.report_schedule_recipients USING btree (id)`
- `CREATE UNIQUE INDEX report_schedule_recipients_organization_id_id_key ON public.report_schedule_recipients USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX report_schedule_recipients_organization_id_schedule_id_memb_key ON public.report_schedule_recipients USING btree (organization_id, schedule_id, membership_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_report_schedule_recipients_fk2 ON public.report_schedule_recipients USING btree (organization_id, membership_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `report_schedules`

Programaciones deshabilitadas por defecto; cron y zona deben validarse en aplicación.

Módulo: **reporting**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `definition_id` | `uuid` | no | `—` |
| `format_id` | `uuid` | no | `—` |
| `cron_expression` | `text` | no | `—` |
| `time_zone` | `text` | no | `—` |
| `is_enabled` | `boolean` | no | `false` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (definition_id) REFERENCES report_definitions(id) ON DELETE RESTRICT`
- `FOREIGN KEY (format_id) REFERENCES report_formats(id) ON DELETE RESTRICT`

Relaciones entrantes: `report_schedule_recipients (organization_id,schedule_id)`.

Índices:

- `CREATE UNIQUE INDEX report_schedules_pkey ON public.report_schedules USING btree (id)`
- `CREATE UNIQUE INDEX report_schedules_organization_id_id_key ON public.report_schedules USING btree (organization_id, id)`
- `CREATE INDEX ix_report_schedules_fk1 ON public.report_schedules USING btree (definition_id)`
- `CREATE INDEX ix_report_schedules_fk2 ON public.report_schedules USING btree (format_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `report_types`

Catálogo de referencia global, códigos estables; no acredita cobertura funcional del proveedor/ecosistema.

Módulo: **reference**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `report_definitions (report_type_id)`.

Índices:

- `CREATE UNIQUE INDEX report_types_pkey ON public.report_types USING btree (id)`
- `CREATE UNIQUE INDEX report_types_code_key ON public.report_types USING btree (code) NULLS NOT DISTINCT`

## `repositories`

Repositorios autorizados, identidad externa y configuración observada.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `integration_id` | `uuid` | no | `—` |
| `external_repository_id` | `text` | no | `—` |
| `owner_login` | `text` | no | `—` |
| `name` | `text` | no | `—` |
| `html_url` | `text` | sí | `—` |
| `default_branch` | `text` | no | `'main'::text` |
| `visibility` | `text` | no | `—` |
| `is_archived` | `boolean` | no | `false` |
| `is_enabled` | `boolean` | no | `true` |
| `last_synced_at` | `timestamp with time zone` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((visibility = ANY (ARRAY['PUBLIC'::text, 'PRIVATE'::text, 'INTERNAL'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, integration_id, external_repository_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, integration_id) REFERENCES integrations(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `deletion_request_repositories (organization_id,repository_id)`; `document_repositories (organization_id,repository_id)`; `impact_finding_repositories (organization_id,repository_id)`; `repository_branches (organization_id,repository_id)`; `repository_commits (organization_id,repository_id)`; `repository_settings (organization_id,repository_id)`; `repository_team_access (organization_id,repository_id)`.

Índices:

- `CREATE UNIQUE INDEX repositories_pkey ON public.repositories USING btree (id)`
- `CREATE UNIQUE INDEX repositories_organization_id_id_key ON public.repositories USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX repositories_organization_id_integration_id_external_reposi_key ON public.repositories USING btree (organization_id, integration_id, external_repository_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_repositories_name ON public.repositories USING btree (organization_id, owner_login, name)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `repository_branches`

Referencia de rama y último commit observado en el mismo repositorio.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `repository_id` | `uuid` | no | `—` |
| `name` | `text` | no | `—` |
| `last_commit_id` | `uuid` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, repository_id, name)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, repository_id, last_commit_id) REFERENCES repository_commits(organization_id, repository_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX repository_branches_pkey ON public.repository_branches USING btree (id)`
- `CREATE UNIQUE INDEX repository_branches_organization_id_id_key ON public.repository_branches USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX repository_branches_organization_id_repository_id_name_key ON public.repository_branches USING btree (organization_id, repository_id, name) NULLS NOT DISTINCT`
- `CREATE INDEX ix_repository_branches_fk2 ON public.repository_branches USING btree (organization_id, repository_id, last_commit_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `repository_commits`

Commits observados; SHA limitado al repositorio y organización.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `repository_id` | `uuid` | no | `—` |
| `commit_sha` | `text` | no | `—` |
| `committed_at` | `timestamp with time zone` | sí | `—` |
| `message` | `text` | sí | `—` |
| `html_url` | `text` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((commit_sha ~ '^([a-f0-9]{40}|[a-f0-9]{64})$'::text))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, repository_id, commit_sha)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, repository_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `repository_branches (organization_id,repository_id,last_commit_id)`; `scans (organization_id,repository_id,commit_id)`.

Índices:

- `CREATE UNIQUE INDEX repository_commits_pkey ON public.repository_commits USING btree (id)`
- `CREATE UNIQUE INDEX repository_commits_organization_id_id_key ON public.repository_commits USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX repository_commits_organization_id_repository_id_commit_sha_key ON public.repository_commits USING btree (organization_id, repository_id, commit_sha) NULLS NOT DISTINCT`
- `CREATE UNIQUE INDEX repository_commits_organization_id_repository_id_id_key ON public.repository_commits USING btree (organization_id, repository_id, id) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `repository_settings`

Opciones escalares de ejecución por repositorio.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `repository_id` | `uuid` | no | `—` |
| `manual_scans_enabled` | `boolean` | no | `true` |
| `push_scans_enabled` | `boolean` | no | `false` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, repository_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX repository_settings_pkey ON public.repository_settings USING btree (id)`
- `CREATE UNIQUE INDEX repository_settings_organization_id_id_key ON public.repository_settings USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX repository_settings_organization_id_repository_id_key ON public.repository_settings USING btree (organization_id, repository_id) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `repository_team_access`

Rol concedido a un equipo sobre un repositorio concreto.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `repository_id` | `uuid` | no | `—` |
| `team_id` | `uuid` | no | `—` |
| `role_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, repository_id, team_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, team_id) REFERENCES organization_teams(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX repository_team_access_pkey ON public.repository_team_access USING btree (id)`
- `CREATE UNIQUE INDEX repository_team_access_organization_id_id_key ON public.repository_team_access USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX repository_team_access_organization_id_repository_id_team_i_key ON public.repository_team_access USING btree (organization_id, repository_id, team_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_repository_team_access_fk2 ON public.repository_team_access USING btree (organization_id, team_id)`
- `CREATE INDEX ix_repository_team_role ON public.repository_team_access USING btree (role_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `role_permissions`

Concesiones de permisos a roles globales.

Módulo: **identity**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `role_id` | `uuid` | no | `—` |
| `permission_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (role_id, permission_id)`
- `FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT`
- `FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX role_permissions_pkey ON public.role_permissions USING btree (id)`
- `CREATE UNIQUE INDEX role_permissions_role_id_permission_id_key ON public.role_permissions USING btree (role_id, permission_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_role_permissions_fk1 ON public.role_permissions USING btree (permission_id)`

## `roles`

Roles base globales. No presupone roles personalizados.

Módulo: **identity**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `membership_roles (role_id)`; `repository_team_access (role_id)`; `role_permissions (role_id)`.

Índices:

- `CREATE UNIQUE INDEX roles_code_key ON public.roles USING btree (code) NULLS NOT DISTINCT`
- `CREATE UNIQUE INDEX roles_pkey ON public.roles USING btree (id)`

## `sbom_documents`

Metadatos CycloneDX ligados a un artefacto del mismo scan.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `scan_id` | `uuid` | no | `—` |
| `artifact_id` | `uuid` | no | `—` |
| `serial_number` | `text` | sí | `—` |
| `spec_version` | `text` | no | `—` |
| `generated_at` | `timestamp with time zone` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, scan_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, scan_id, artifact_id) REFERENCES scan_artifacts(organization_id, scan_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `sbom_external_references (organization_id,sbom_document_id)`.

Índices:

- `CREATE UNIQUE INDEX sbom_documents_pkey ON public.sbom_documents USING btree (id)`
- `CREATE UNIQUE INDEX sbom_documents_organization_id_id_key ON public.sbom_documents USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX sbom_documents_organization_id_scan_id_key ON public.sbom_documents USING btree (organization_id, scan_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_sbom_documents_fk1 ON public.sbom_documents USING btree (organization_id, scan_id, artifact_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `sbom_external_references`

Referencias consultables del documento SBOM.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `sbom_document_id` | `uuid` | no | `—` |
| `reference_type` | `text` | no | `—` |
| `url` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, sbom_document_id, reference_type, url)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, sbom_document_id) REFERENCES sbom_documents(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX sbom_external_references_pkey ON public.sbom_external_references USING btree (id)`
- `CREATE UNIQUE INDEX sbom_external_references_organization_id_id_key ON public.sbom_external_references USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX sbom_external_references_organization_id_sbom_document_id_r_key ON public.sbom_external_references USING btree (organization_id, sbom_document_id, reference_type, url) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `scan_artifacts`

Ubicación, hash y tamaño de artefactos derivados de un scan.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `scan_id` | `uuid` | no | `—` |
| `kind` | `text` | no | `—` |
| `format` | `text` | no | `—` |
| `sha256` | `text` | no | `—` |
| `size_bytes` | `bigint` | no | `—` |
| `storage_reference` | `text` | no | `—` |
| `created_at` | `timestamp with time zone` | no | `now()` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((kind = ANY (ARRAY['SBOM'::text, 'LOG'::text, 'PROVIDER_RESULT'::text])))`
- `CHECK ((sha256 ~ '^[a-f0-9]{64}$'::text))`
- `CHECK ((size_bytes >= 0))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, scan_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `sbom_documents (organization_id,scan_id,artifact_id)`.

Índices:

- `CREATE UNIQUE INDEX scan_artifacts_pkey ON public.scan_artifacts USING btree (id)`
- `CREATE UNIQUE INDEX scan_artifacts_organization_id_id_key ON public.scan_artifacts USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX scan_artifacts_organization_id_scan_id_id_key ON public.scan_artifacts USING btree (organization_id, scan_id, id) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `scan_comparisons`

Comparación reproducible entre dos scans del mismo repositorio.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `repository_id` | `uuid` | no | `—` |
| `base_scan_id` | `uuid` | no | `—` |
| `target_scan_id` | `uuid` | no | `—` |
| `method_version` | `text` | no | `—` |
| `created_at` | `timestamp with time zone` | no | `now()` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((base_scan_id <> target_scan_id))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, base_scan_id, target_scan_id, method_version)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, repository_id, base_scan_id) REFERENCES scans(organization_id, repository_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, repository_id, target_scan_id) REFERENCES scans(organization_id, repository_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `scan_component_changes (organization_id,comparison_id)`.

Índices:

- `CREATE UNIQUE INDEX scan_comparisons_pkey ON public.scan_comparisons USING btree (id)`
- `CREATE UNIQUE INDEX scan_comparisons_organization_id_id_key ON public.scan_comparisons USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX scan_comparisons_organization_id_base_scan_id_target_scan_i_key ON public.scan_comparisons USING btree (organization_id, base_scan_id, target_scan_id, method_version) NULLS NOT DISTINCT`
- `CREATE INDEX ix_scan_comparisons_fk1 ON public.scan_comparisons USING btree (organization_id, repository_id, base_scan_id)`
- `CREATE INDEX ix_scan_comparisons_fk2 ON public.scan_comparisons USING btree (organization_id, repository_id, target_scan_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `scan_component_changes`

Proyección persistida del diff, con método en la comparación y evidencia.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: proyección histórica. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `comparison_id` | `uuid` | no | `—` |
| `component_id` | `uuid` | no | `—` |
| `old_version_id` | `uuid` | sí | `—` |
| `new_version_id` | `uuid` | sí | `—` |
| `change_type` | `text` | no | `—` |
| `evidence` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((change_type = ANY (ARRAY['ADDED'::text, 'REMOVED'::text, 'UPDATED'::text, 'UNCHANGED'::text])))`
- `CHECK ((((change_type = 'ADDED'::text) AND (old_version_id IS NULL) AND (new_version_id IS NOT NULL)) OR ((change_type = 'REMOVED'::text) AND (old_version_id IS NOT NULL) AND (new_version_id IS NULL)) OR ((change_type = 'UPDATED'::text) AND (old_version_id IS NOT NULL) AND (new_version_id IS NOT NULL) AND (old_version_id <> new_version_id)) OR ((change_type = 'UNCHANGED'::text) AND (old_version_id IS NOT NULL) AND (old_version_id = new_version_id))))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, comparison_id, component_id, old_version_id, new_version_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, comparison_id) REFERENCES scan_comparisons(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, component_id) REFERENCES components(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, component_id, old_version_id) REFERENCES component_versions(organization_id, component_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, component_id, new_version_id) REFERENCES component_versions(organization_id, component_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX scan_component_changes_pkey ON public.scan_component_changes USING btree (id)`
- `CREATE UNIQUE INDEX scan_component_changes_organization_id_id_key ON public.scan_component_changes USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX scan_component_changes_organization_id_comparison_id_compon_key ON public.scan_component_changes USING btree (organization_id, comparison_id, component_id, old_version_id, new_version_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_scan_component_changes_fk2 ON public.scan_component_changes USING btree (organization_id, component_id)`
- `CREATE INDEX ix_scan_component_changes_fk3 ON public.scan_component_changes USING btree (organization_id, component_id, old_version_id)`
- `CREATE INDEX ix_scan_component_changes_fk4 ON public.scan_component_changes USING btree (organization_id, component_id, new_version_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `scan_component_licenses`

Licencias observadas en la aparición concreta del SBOM.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `scan_component_id` | `uuid` | no | `—` |
| `license_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, scan_component_id, license_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, scan_component_id) REFERENCES scan_components(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (license_id) REFERENCES license_catalog(id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX scan_component_licenses_pkey ON public.scan_component_licenses USING btree (id)`
- `CREATE UNIQUE INDEX scan_component_licenses_organization_id_id_key ON public.scan_component_licenses USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX scan_component_licenses_organization_id_scan_component_id_l_key ON public.scan_component_licenses USING btree (organization_id, scan_component_id, license_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_scan_component_licenses_fk2 ON public.scan_component_licenses USING btree (license_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `scan_components`

Presencia de una versión en un snapshot, identificada por bom_ref.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `scan_id` | `uuid` | no | `—` |
| `component_version_id` | `uuid` | no | `—` |
| `bom_ref` | `text` | no | `—` |
| `scope_id` | `uuid` | sí | `—` |
| `is_root` | `boolean` | no | `false` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, scan_id, bom_ref)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, scan_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (scope_id) REFERENCES dependency_scopes(id) ON DELETE RESTRICT`

Relaciones entrantes: `assistant_message_evidences (organization_id,scan_component_id)`; `dependency_edges (organization_id,scan_id,source_scan_component_id)`; `dependency_edges (organization_id,scan_id,target_scan_component_id)`; `impact_analyses (organization_id,base_scan_id,current_scan_component_id)`; `scan_component_licenses (organization_id,scan_component_id)`; `scan_vulnerability_findings (organization_id,scan_component_id)`.

Índices:

- `CREATE UNIQUE INDEX scan_components_pkey ON public.scan_components USING btree (id)`
- `CREATE UNIQUE INDEX scan_components_organization_id_id_key ON public.scan_components USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX scan_components_organization_id_scan_id_bom_ref_key ON public.scan_components USING btree (organization_id, scan_id, bom_ref) NULLS NOT DISTINCT`
- `CREATE UNIQUE INDEX scan_components_organization_id_scan_id_id_key ON public.scan_components USING btree (organization_id, scan_id, id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_scan_components_fk2 ON public.scan_components USING btree (organization_id, component_version_id)`
- `CREATE INDEX ix_scan_components_fk3 ON public.scan_components USING btree (scope_id)`
- `CREATE INDEX ix_scan_components_inventory ON public.scan_components USING btree (organization_id, component_version_id, scan_id)`
- `CREATE UNIQUE INDEX uq_scan_root ON public.scan_components USING btree (organization_id, scan_id) WHERE is_root`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `scan_logs`

Diagnóstico por scan; sanitización y retención a cargo del productor/worker.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `scan_id` | `uuid` | no | `—` |
| `level` | `text` | no | `—` |
| `message` | `text` | no | `—` |
| `created_at` | `timestamp with time zone` | no | `now()` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((level = ANY (ARRAY['DEBUG'::text, 'INFO'::text, 'WARN'::text, 'ERROR'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX scan_logs_pkey ON public.scan_logs USING btree (id)`
- `CREATE UNIQUE INDEX scan_logs_organization_id_id_key ON public.scan_logs USING btree (organization_id, id)`
- `CREATE INDEX ix_scan_logs_fk1 ON public.scan_logs USING btree (organization_id, scan_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `scan_profile_ecosystems`

Ecosistemas habilitados en un perfil sin listas JSON.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `scan_profile_id` | `uuid` | no | `—` |
| `package_ecosystem_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, scan_profile_id, package_ecosystem_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, scan_profile_id) REFERENCES scan_profiles(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (package_ecosystem_id) REFERENCES package_ecosystems(id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX scan_profile_ecosystems_pkey ON public.scan_profile_ecosystems USING btree (id)`
- `CREATE UNIQUE INDEX scan_profile_ecosystems_organization_id_id_key ON public.scan_profile_ecosystems USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX scan_profile_ecosystems_organization_id_scan_profile_id_pac_key ON public.scan_profile_ecosystems USING btree (organization_id, scan_profile_id, package_ecosystem_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_scan_profile_ecosystems_fk2 ON public.scan_profile_ecosystems USING btree (package_ecosystem_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `scan_profiles`

Versión explícita de configuración de análisis; JSON solo para flags técnicos.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `name` | `text` | no | `—` |
| `configuration_version` | `text` | no | `—` |
| `settings` | `jsonb` | no | `'{}'::jsonb` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((jsonb_typeof(settings) = 'object'::text))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, name, configuration_version)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`

Relaciones entrantes: `scan_profile_ecosystems (organization_id,scan_profile_id)`; `scans (organization_id,scan_profile_id)`.

Índices:

- `CREATE UNIQUE INDEX scan_profiles_pkey ON public.scan_profiles USING btree (id)`
- `CREATE UNIQUE INDEX scan_profiles_organization_id_id_key ON public.scan_profiles USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX scan_profiles_organization_id_name_configuration_version_key ON public.scan_profiles USING btree (organization_id, name, configuration_version) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `scan_stage_executions`

Intentos de cada etapa, métricas y errores redactados.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `scan_id` | `uuid` | no | `—` |
| `stage_id` | `uuid` | no | `—` |
| `attempt_number` | `integer` | no | `1` |
| `status` | `text` | no | `'PENDING'::text` |
| `created_at` | `timestamp with time zone` | no | `now()` |
| `started_at` | `timestamp with time zone` | sí | `—` |
| `finished_at` | `timestamp with time zone` | sí | `—` |
| `error_code` | `text` | sí | `—` |
| `metrics` | `jsonb` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((attempt_number > 0))`
- `CHECK ((status = ANY (ARRAY['PENDING'::text, 'RUNNING'::text, 'COMPLETED'::text, 'FAILED'::text, 'CANCELLED'::text, 'RETRYING'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, scan_id, stage_id, attempt_number)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (stage_id) REFERENCES scan_stages(id) ON DELETE RESTRICT`
- `CHECK (((finished_at IS NULL) OR ((started_at IS NOT NULL) AND (finished_at >= started_at))))`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX scan_stage_executions_pkey ON public.scan_stage_executions USING btree (id)`
- `CREATE UNIQUE INDEX scan_stage_executions_organization_id_id_key ON public.scan_stage_executions USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX scan_stage_executions_organization_id_scan_id_stage_id_atte_key ON public.scan_stage_executions USING btree (organization_id, scan_id, stage_id, attempt_number) NULLS NOT DISTINCT`
- `CREATE INDEX ix_scan_stage_executions_fk2 ON public.scan_stage_executions USING btree (stage_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `scan_stages`

Catálogo global de etapas del pipeline.

Módulo: **scan**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `scan_stage_executions (stage_id)`.

Índices:

- `CREATE UNIQUE INDEX scan_stages_pkey ON public.scan_stages USING btree (id)`
- `CREATE UNIQUE INDEX scan_stages_code_key ON public.scan_stages USING btree (code) NULLS NOT DISTINCT`

## `scan_vulnerability_findings`

Coincidencia de advisory con una aparición concreta; conserva procedencia.

Módulo: **vulnerability**. Fase: **migration**. Alcance: **tenant**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `scan_component_id` | `uuid` | no | `—` |
| `vulnerability_id` | `uuid` | no | `—` |
| `source_record_id` | `uuid` | no | `—` |
| `severity_assessment_id` | `uuid` | sí | `—` |
| `match_status` | `text` | no | `—` |
| `resolution_status` | `text` | no | `'OPEN'::text` |
| `detected_at` | `timestamp with time zone` | no | `now()` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((match_status = ANY (ARRAY['MATCHED'::text, 'UNCERTAIN'::text, 'REJECTED'::text])))`
- `CHECK ((resolution_status = ANY (ARRAY['OPEN'::text, 'DISMISSED'::text, 'FIXED'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, scan_component_id, vulnerability_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, scan_component_id) REFERENCES scan_components(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities(id) ON DELETE RESTRICT`
- `FOREIGN KEY (vulnerability_id, severity_assessment_id) REFERENCES vulnerability_severities(vulnerability_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (source_record_id) REFERENCES vulnerability_source_records(id) ON DELETE RESTRICT`
- `FOREIGN KEY (vulnerability_id, source_record_id) REFERENCES vulnerability_source_records(vulnerability_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `assistant_message_evidences (organization_id,finding_id)`; `finding_evidence (organization_id,finding_id)`; `notification_findings (organization_id,finding_id)`.

Índices:

- `CREATE UNIQUE INDEX scan_vulnerability_findings_pkey ON public.scan_vulnerability_findings USING btree (id)`
- `CREATE UNIQUE INDEX scan_vulnerability_findings_organization_id_id_key ON public.scan_vulnerability_findings USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX scan_vulnerability_findings_organization_id_scan_component__key ON public.scan_vulnerability_findings USING btree (organization_id, scan_component_id, vulnerability_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_scan_vulnerability_findings_fk2 ON public.scan_vulnerability_findings USING btree (vulnerability_id)`
- `CREATE INDEX ix_scan_vulnerability_findings_fk3 ON public.scan_vulnerability_findings USING btree (vulnerability_id, severity_assessment_id)`
- `CREATE INDEX ix_scan_vulnerability_findings_fk4 ON public.scan_vulnerability_findings USING btree (source_record_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `scanner_tools`

Catálogo de referencia global, códigos estables; no acredita cobertura funcional del proveedor/ecosistema.

Módulo: **reference**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `scans (scanner_tool_id)`.

Índices:

- `CREATE UNIQUE INDEX scanner_tools_pkey ON public.scanner_tools USING btree (id)`
- `CREATE UNIQUE INDEX scanner_tools_code_key ON public.scanner_tools USING btree (code) NULLS NOT DISTINCT`

## `scans`

Snapshot trazable a commit, configuración, herramienta, solicitante y trabajo.

Módulo: **scan**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `repository_id` | `uuid` | no | `—` |
| `commit_id` | `uuid` | no | `—` |
| `scan_profile_id` | `uuid` | no | `—` |
| `job_id` | `uuid` | no | `—` |
| `requested_by_membership_id` | `uuid` | sí | `—` |
| `scanner_tool_id` | `uuid` | no | `—` |
| `scanner_version` | `text` | no | `—` |
| `trigger_type` | `text` | no | `—` |
| `request_key` | `text` | no | `—` |
| `source_ref` | `text` | sí | `—` |
| `status` | `text` | no | `'PENDING'::text` |
| `created_at` | `timestamp with time zone` | no | `now()` |
| `started_at` | `timestamp with time zone` | sí | `—` |
| `finished_at` | `timestamp with time zone` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((trigger_type = ANY (ARRAY['MANUAL'::text, 'WEBHOOK'::text, 'SCHEDULED'::text])))`
- `CHECK ((status = ANY (ARRAY['PENDING'::text, 'RUNNING'::text, 'COMPLETED'::text, 'FAILED'::text, 'CANCELLED'::text, 'RETRYING'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, repository_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, repository_id, request_key)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, job_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, repository_id, commit_id) REFERENCES repository_commits(organization_id, repository_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, scan_profile_id) REFERENCES scan_profiles(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (scanner_tool_id) REFERENCES scanner_tools(id) ON DELETE RESTRICT`
- `CHECK (((finished_at IS NULL) OR ((started_at IS NOT NULL) AND (finished_at >= started_at))))`

Relaciones entrantes: `impact_analyses (organization_id,repository_id,base_scan_id)`; `notification_scans (organization_id,scan_id)`; `report_job_scans (organization_id,scan_id)`; `scan_artifacts (organization_id,scan_id)`; `scan_comparisons (organization_id,repository_id,base_scan_id)`; `scan_comparisons (organization_id,repository_id,target_scan_id)`; `scan_components (organization_id,scan_id)`; `scan_logs (organization_id,scan_id)`; `scan_stage_executions (organization_id,scan_id)`.

Índices:

- `CREATE UNIQUE INDEX scans_pkey ON public.scans USING btree (id)`
- `CREATE UNIQUE INDEX scans_organization_id_id_key ON public.scans USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX scans_organization_id_repository_id_id_key ON public.scans USING btree (organization_id, repository_id, id) NULLS NOT DISTINCT`
- `CREATE UNIQUE INDEX scans_organization_id_repository_id_request_key_key ON public.scans USING btree (organization_id, repository_id, request_key) NULLS NOT DISTINCT`
- `CREATE UNIQUE INDEX scans_organization_id_job_id_key ON public.scans USING btree (organization_id, job_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_scans_fk1 ON public.scans USING btree (organization_id, repository_id, commit_id)`
- `CREATE INDEX ix_scans_fk2 ON public.scans USING btree (organization_id, scan_profile_id)`
- `CREATE INDEX ix_scans_fk4 ON public.scans USING btree (organization_id, requested_by_membership_id)`
- `CREATE INDEX ix_scans_fk5 ON public.scans USING btree (scanner_tool_id)`
- `CREATE INDEX ix_scans_history ON public.scans USING btree (organization_id, repository_id, created_at DESC, id)`
- `CREATE INDEX ix_scans_commit_profile ON public.scans USING btree (organization_id, commit_id, scan_profile_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `severity_levels`

Catálogo de referencia global, códigos estables; no acredita cobertura funcional del proveedor/ecosistema.

Módulo: **reference**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `rank` | `smallint` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK (((rank >= 0) AND (rank <= 4)))`
- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`
- `UNIQUE NULLS NOT DISTINCT (rank)`

Relaciones entrantes: `impact_findings (severity_level_id)`; `vulnerability_severities (severity_level_id)`.

Índices:

- `CREATE UNIQUE INDEX severity_levels_pkey ON public.severity_levels USING btree (id)`
- `CREATE UNIQUE INDEX severity_levels_code_key ON public.severity_levels USING btree (code) NULLS NOT DISTINCT`
- `CREATE UNIQUE INDEX severity_levels_rank_key ON public.severity_levels USING btree (rank) NULLS NOT DISTINCT`

## `team_memberships`

Miembros de cada equipo.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `team_id` | `uuid` | no | `—` |
| `membership_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (organization_id, team_id, membership_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, team_id) REFERENCES organization_teams(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX team_memberships_pkey ON public.team_memberships USING btree (id)`
- `CREATE UNIQUE INDEX team_memberships_organization_id_id_key ON public.team_memberships USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX team_memberships_organization_id_team_id_membership_id_key ON public.team_memberships USING btree (organization_id, team_id, membership_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_team_memberships_fk2 ON public.team_memberships USING btree (organization_id, membership_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `user_identities`

Identidades externas de autenticación; no accesibles al rol runtime.

Módulo: **identity**. Fase: **migration**. Alcance: **global (sin acceso runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `provider` | `text` | no | `—` |
| `provider_subject` | `text` | no | `—` |
| `user_id` | `uuid` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (provider, provider_subject)`
- `FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX user_identities_pkey ON public.user_identities USING btree (id)`
- `CREATE UNIQUE INDEX user_identities_provider_provider_subject_key ON public.user_identities USING btree (provider, provider_subject) NULLS NOT DISTINCT`
- `CREATE INDEX ix_user_identities_fk0 ON public.user_identities USING btree (user_id)`

## `users`

Identidad global, email normalizado y hash opcional; visible solo para membresías activas del tenant.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant (identidad protegida por política específica)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `email_normalized` | `text` | no | `—` |
| `email_verified_at` | `timestamp with time zone` | sí | `—` |
| `password_hash` | `text` | sí | `—` |
| `display_name` | `text` | no | `—` |
| `avatar_url` | `text` | sí | `—` |
| `is_active` | `boolean` | no | `true` |
| `created_at` | `timestamp with time zone` | no | `now()` |
| `updated_at` | `timestamp with time zone` | no | `now()` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK (((email_normalized = lower(btrim(email_normalized))) AND (POSITION(('@'::text) IN (email_normalized)) > 1)))`
- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (email_normalized)`

Relaciones entrantes: `organization_memberships (user_id)`; `organization_memberships (invited_by_user_id)`; `user_identities (user_id)`.

Índices:

- `CREATE UNIQUE INDEX users_pkey ON public.users USING btree (id)`
- `CREATE UNIQUE INDEX users_email_normalized_key ON public.users USING btree (email_normalized) NULLS NOT DISTINCT`

RLS (`FORCE` activado): `tenant_member_visibility` USING `(EXISTS ( SELECT 1
   FROM organization_memberships m
  WHERE ((m.user_id = users.id) AND (m.status = 'ACTIVE'::text))))` WITH CHECK `—`.

## `version_change_records`

Cambio entre versiones del mismo paquete con fuente consultada.

Módulo: **impact**. Fase: **future**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `component_id` | `uuid` | no | `—` |
| `from_version_id` | `uuid` | no | `—` |
| `to_version_id` | `uuid` | no | `—` |
| `source_url` | `text` | no | `—` |
| `retrieved_at` | `timestamp with time zone` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `FOREIGN KEY (organization_id, component_id, to_version_id) REFERENCES component_versions(organization_id, component_id, id) ON DELETE RESTRICT`
- `CHECK ((from_version_id <> to_version_id))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, component_id) REFERENCES components(organization_id, id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, component_id, from_version_id) REFERENCES component_versions(organization_id, component_id, id) ON DELETE RESTRICT`

Relaciones entrantes: `change_record_evidences (organization_id,change_record_id)`.

Índices:

- `CREATE UNIQUE INDEX version_change_records_pkey ON public.version_change_records USING btree (id)`
- `CREATE UNIQUE INDEX version_change_records_organization_id_id_key ON public.version_change_records USING btree (organization_id, id)`
- `CREATE INDEX ix_version_change_records_fk1 ON public.version_change_records USING btree (organization_id, component_id)`
- `CREATE INDEX ix_version_change_records_fk2 ON public.version_change_records USING btree (organization_id, component_id, from_version_id)`
- `CREATE INDEX ix_version_change_records_fk3 ON public.version_change_records USING btree (organization_id, component_id, to_version_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.

## `vulnerabilities`

Advisories públicos canónicos por fuente e identificador externo.

Módulo: **vulnerability**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `vulnerability_source_id` | `uuid` | no | `—` |
| `external_id` | `text` | no | `—` |
| `summary` | `text` | sí | `—` |
| `description` | `text` | sí | `—` |
| `published_at` | `timestamp with time zone` | sí | `—` |
| `modified_at` | `timestamp with time zone` | sí | `—` |
| `withdrawn_at` | `timestamp with time zone` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (vulnerability_source_id, external_id)`
- `FOREIGN KEY (vulnerability_source_id) REFERENCES vulnerability_sources(id) ON DELETE RESTRICT`

Relaciones entrantes: `impact_finding_vulnerabilities (vulnerability_id)`; `scan_vulnerability_findings (vulnerability_id)`; `vulnerability_affected_packages (vulnerability_id)`; `vulnerability_aliases (vulnerability_id)`; `vulnerability_references (vulnerability_id)`; `vulnerability_severities (vulnerability_id)`; `vulnerability_source_records (vulnerability_id)`.

Índices:

- `CREATE UNIQUE INDEX vulnerabilities_pkey ON public.vulnerabilities USING btree (id)`
- `CREATE UNIQUE INDEX vulnerabilities_vulnerability_source_id_external_id_key ON public.vulnerabilities USING btree (vulnerability_source_id, external_id) NULLS NOT DISTINCT`

## `vulnerability_affected_packages`

Identidad pública del paquete afectado, independiente del catálogo privado.

Módulo: **vulnerability**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `vulnerability_id` | `uuid` | no | `—` |
| `package_ecosystem_id` | `uuid` | no | `—` |
| `namespace` | `text` | no | `''::text` |
| `normalized_name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (vulnerability_id, package_ecosystem_id, namespace, normalized_name)`
- `FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities(id) ON DELETE RESTRICT`
- `FOREIGN KEY (package_ecosystem_id) REFERENCES package_ecosystems(id) ON DELETE RESTRICT`

Relaciones entrantes: `vulnerability_affected_ranges (affected_package_id)`; `vulnerability_fixed_versions (affected_package_id)`.

Índices:

- `CREATE UNIQUE INDEX vulnerability_affected_packages_pkey ON public.vulnerability_affected_packages USING btree (id)`
- `CREATE UNIQUE INDEX vulnerability_affected_packag_vulnerability_id_package_ecos_key ON public.vulnerability_affected_packages USING btree (vulnerability_id, package_ecosystem_id, namespace, normalized_name) NULLS NOT DISTINCT`
- `CREATE INDEX ix_vulnerability_affected_packages_fk1 ON public.vulnerability_affected_packages USING btree (package_ecosystem_id)`

## `vulnerability_affected_ranges`

Rango del advisory; interpretación determinista por ecosistema.

Módulo: **vulnerability**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `affected_package_id` | `uuid` | no | `—` |
| `range_type` | `text` | no | `—` |
| `repository_url` | `text` | sí | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((range_type = ANY (ARRAY['SEMVER'::text, 'ECOSYSTEM'::text, 'GIT'::text])))`
- `PRIMARY KEY (id)`
- `FOREIGN KEY (affected_package_id) REFERENCES vulnerability_affected_packages(id) ON DELETE RESTRICT`

Relaciones entrantes: `finding_evidence (matched_range_id)`; `vulnerability_range_events (affected_range_id)`.

Índices:

- `CREATE UNIQUE INDEX vulnerability_affected_ranges_pkey ON public.vulnerability_affected_ranges USING btree (id)`
- `CREATE INDEX ix_vulnerability_affected_ranges_fk0 ON public.vulnerability_affected_ranges USING btree (affected_package_id)`

## `vulnerability_aliases`

Identificadores alternativos, sin arrays ni equivalencia OSV/CVE implícita.

Módulo: **vulnerability**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `vulnerability_id` | `uuid` | no | `—` |
| `alias_type` | `text` | no | `—` |
| `value` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (vulnerability_id, alias_type, value)`
- `FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities(id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX vulnerability_aliases_pkey ON public.vulnerability_aliases USING btree (id)`
- `CREATE UNIQUE INDEX vulnerability_aliases_vulnerability_id_alias_type_value_key ON public.vulnerability_aliases USING btree (vulnerability_id, alias_type, value) NULLS NOT DISTINCT`

## `vulnerability_fixed_versions`

Versiones corregidas explicitadas por el proveedor.

Módulo: **vulnerability**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `affected_package_id` | `uuid` | no | `—` |
| `version` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (affected_package_id, version)`
- `FOREIGN KEY (affected_package_id) REFERENCES vulnerability_affected_packages(id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX vulnerability_fixed_versions_pkey ON public.vulnerability_fixed_versions USING btree (id)`
- `CREATE UNIQUE INDEX vulnerability_fixed_versions_affected_package_id_version_key ON public.vulnerability_fixed_versions USING btree (affected_package_id, version) NULLS NOT DISTINCT`

## `vulnerability_range_events`

Secuencia de introducción, fix, límite o última versión afectada.

Módulo: **vulnerability**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `affected_range_id` | `uuid` | no | `—` |
| `ordinal` | `integer` | no | `—` |
| `event_type` | `text` | no | `—` |
| `version` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((ordinal >= 0))`
- `CHECK ((event_type = ANY (ARRAY['INTRODUCED'::text, 'FIXED'::text, 'LAST_AFFECTED'::text, 'LIMIT'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (affected_range_id, ordinal)`
- `FOREIGN KEY (affected_range_id) REFERENCES vulnerability_affected_ranges(id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX vulnerability_range_events_pkey ON public.vulnerability_range_events USING btree (id)`
- `CREATE UNIQUE INDEX vulnerability_range_events_affected_range_id_ordinal_key ON public.vulnerability_range_events USING btree (affected_range_id, ordinal) NULLS NOT DISTINCT`

## `vulnerability_references`

Referencias públicas independientes del advisory.

Módulo: **vulnerability**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `vulnerability_id` | `uuid` | no | `—` |
| `reference_type` | `text` | no | `—` |
| `url` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (vulnerability_id, url)`
- `FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities(id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX vulnerability_references_pkey ON public.vulnerability_references USING btree (id)`
- `CREATE UNIQUE INDEX vulnerability_references_vulnerability_id_url_key ON public.vulnerability_references USING btree (vulnerability_id, url) NULLS NOT DISTINCT`

## `vulnerability_severities`

Evaluaciones por fuente/método, con score y vector CVSS opcionales.

Módulo: **vulnerability**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `vulnerability_id` | `uuid` | no | `—` |
| `source_id` | `uuid` | no | `—` |
| `severity_level_id` | `uuid` | no | `—` |
| `method` | `text` | no | `—` |
| `score` | `numeric(5,2)` | sí | `—` |
| `vector` | `text` | sí | `—` |
| `cvss_version` | `text` | sí | `—` |
| `assessed_at` | `timestamp with time zone` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK (((score >= (0)::numeric) AND (score <= (10)::numeric)))`
- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (vulnerability_id, source_id, method, vector)`
- `UNIQUE NULLS NOT DISTINCT (vulnerability_id, id)`
- `FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities(id) ON DELETE RESTRICT`
- `FOREIGN KEY (source_id) REFERENCES vulnerability_sources(id) ON DELETE RESTRICT`
- `FOREIGN KEY (severity_level_id) REFERENCES severity_levels(id) ON DELETE RESTRICT`

Relaciones entrantes: `scan_vulnerability_findings (vulnerability_id,severity_assessment_id)`.

Índices:

- `CREATE UNIQUE INDEX vulnerability_severities_pkey ON public.vulnerability_severities USING btree (id)`
- `CREATE UNIQUE INDEX vulnerability_severities_vulnerability_id_source_id_method__key ON public.vulnerability_severities USING btree (vulnerability_id, source_id, method, vector) NULLS NOT DISTINCT`
- `CREATE UNIQUE INDEX vulnerability_severities_vulnerability_id_id_key ON public.vulnerability_severities USING btree (vulnerability_id, id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_vulnerability_severities_fk1 ON public.vulnerability_severities USING btree (source_id)`
- `CREATE INDEX ix_vulnerability_severities_fk2 ON public.vulnerability_severities USING btree (severity_level_id)`

## `vulnerability_source_records`

Respuesta pública de proveedor hasheada, parser y fecha de consulta.

Módulo: **vulnerability**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `vulnerability_id` | `uuid` | no | `—` |
| `source_id` | `uuid` | no | `—` |
| `payload` | `jsonb` | no | `—` |
| `sha256` | `text` | no | `—` |
| `fetched_at` | `timestamp with time zone` | no | `—` |
| `parser_version` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((sha256 ~ '^[a-f0-9]{64}$'::text))`
- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (vulnerability_id, source_id, sha256)`
- `FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities(id) ON DELETE RESTRICT`
- `FOREIGN KEY (source_id) REFERENCES vulnerability_sources(id) ON DELETE RESTRICT`
- `UNIQUE (vulnerability_id, id)`

Relaciones entrantes: `scan_vulnerability_findings (source_record_id)`; `scan_vulnerability_findings (vulnerability_id,source_record_id)`.

Índices:

- `CREATE UNIQUE INDEX vulnerability_source_records_pkey ON public.vulnerability_source_records USING btree (id)`
- `CREATE UNIQUE INDEX vulnerability_source_records_vulnerability_id_source_id_sha_key ON public.vulnerability_source_records USING btree (vulnerability_id, source_id, sha256) NULLS NOT DISTINCT`
- `CREATE INDEX ix_vulnerability_source_records_fk1 ON public.vulnerability_source_records USING btree (source_id)`
- `CREATE UNIQUE INDEX vulnerability_source_records_vulnerability_id_id_key ON public.vulnerability_source_records USING btree (vulnerability_id, id)`

## `vulnerability_sources`

Catálogo de referencia global, códigos estables; no acredita cobertura funcional del proveedor/ecosistema.

Módulo: **reference**. Fase: **migration**. Alcance: **global (solo lectura runtime)**. Naturaleza: snapshot/evidencia. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `code` | `text` | no | `—` |
| `name` | `text` | no | `—` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `PRIMARY KEY (id)`
- `UNIQUE NULLS NOT DISTINCT (code)`

Relaciones entrantes: `vulnerabilities (vulnerability_source_id)`; `vulnerability_severities (source_id)`; `vulnerability_source_records (source_id)`.

Índices:

- `CREATE UNIQUE INDEX vulnerability_sources_pkey ON public.vulnerability_sources USING btree (id)`
- `CREATE UNIQUE INDEX vulnerability_sources_code_key ON public.vulnerability_sources USING btree (code) NULLS NOT DISTINCT`

## `webhook_events`

Entrega idempotente y hash del payload, persistida después de verificar firma.

Módulo: **identity**. Fase: **migration**. Alcance: **tenant**. Naturaleza: dato fuente/relación. Propietario SQL: `dependency_migrator`.

| Columna | Tipo | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | no | `gen_random_uuid()` |
| `organization_id` | `uuid` | no | `—` |
| `provider_id` | `uuid` | no | `—` |
| `integration_id` | `uuid` | no | `—` |
| `delivery_id` | `text` | no | `—` |
| `event_type` | `text` | no | `—` |
| `received_at` | `timestamp with time zone` | no | `now()` |
| `processed_at` | `timestamp with time zone` | sí | `—` |
| `payload_hash` | `text` | no | `—` |
| `status` | `text` | no | `'RECEIVED'::text` |

Restricciones (PK, UNIQUE, FK y CHECK):

- `CHECK ((status = ANY (ARRAY['RECEIVED'::text, 'PROCESSED'::text, 'FAILED'::text, 'IGNORED'::text])))`
- `PRIMARY KEY (id)`
- `UNIQUE (organization_id, id)`
- `UNIQUE NULLS NOT DISTINCT (provider_id, delivery_id)`
- `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`
- `FOREIGN KEY (organization_id, integration_id, provider_id) REFERENCES integrations(organization_id, id, provider_id) ON DELETE RESTRICT`

Relaciones entrantes: ninguna.

Índices:

- `CREATE UNIQUE INDEX webhook_events_pkey ON public.webhook_events USING btree (id)`
- `CREATE UNIQUE INDEX webhook_events_organization_id_id_key ON public.webhook_events USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX webhook_events_provider_id_delivery_id_key ON public.webhook_events USING btree (provider_id, delivery_id) NULLS NOT DISTINCT`
- `CREATE INDEX ix_webhook_events_fk1 ON public.webhook_events USING btree (organization_id, integration_id, provider_id)`

RLS (`FORCE` activado): `tenant_isolation` USING `(organization_id = app.current_organization_id())` WITH CHECK `(organization_id = app.current_organization_id())`.
