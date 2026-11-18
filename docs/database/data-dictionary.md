# Diccionario de datos del esquema aplicado

Generado desde `schema.json`, exportado de PostgreSQL tras Flyway V17. No contiene filas de negocio ni secretos. Los nombres, columnas, restricciones e índices son del catálogo real.

PK/U: clave primaria/única, posiblemente compuesta. FK: referencia. NO ACTION/RESTRICT preservan historia; no se aplican cascadas indiscriminadas.

Las tablas globales de identidad se acceden desde el backend de autenticación; no son catálogos públicos ni autorizan acceso a organizaciones. Los catálogos públicos de advisories no contienen apariciones privadas. Las tablas tenant-scoped usan RLS y FKs de organización.

## assistant_conversations

Conversación de una membresía dentro de una organización.

Módulo: **documentation**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `created_by_membership_id` | `uuid` | No | — | FK |
| `title` | `text` | Sí | — |  |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |

Restricciones:

- `assistant_conversations_organization_id_created_by_members_fkey`: `FOREIGN KEY (organization_id, created_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `assistant_conversations_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `assistant_conversations_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: `assistant_messages` (organization_id, conversation_id), `data_deletion_requests` (organization_id, conversation_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX assistant_conversations_organization_id_id_key ON public.assistant_conversations USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX assistant_conversations_pkey ON public.assistant_conversations USING btree (id)`
- `CREATE INDEX ix_fk_assistant_conversations_a1426daf803b ON public.assistant_conversations USING btree (organization_id, created_by_membership_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## assistant_message_evidences

Citas tipadas con FK real a chunk, aparición o hallazgo autorizado.

Módulo: **documentation**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK, U |
| `message_id` | `uuid` | No | — | FK, U |
| `ordinal` | `integer` | No | — | U |
| `document_chunk_id` | `uuid` | Sí | — | FK |
| `scan_component_id` | `uuid` | Sí | — | FK |
| `finding_id` | `uuid` | Sí | — | FK |
| `citation_label` | `text` | No | — |  |

Restricciones:

- `assistant_message_evidences_check`: `CHECK ((num_nonnulls(document_chunk_id, scan_component_id, finding_id) = 1))`. Validada: True.
- `assistant_message_evidences_ordinal_check`: `CHECK ((ordinal > 0))`. Validada: True.
- `assistant_message_evidences_organization_id_document_chunk_fkey`: `FOREIGN KEY (organization_id, document_chunk_id) REFERENCES document_chunks(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `assistant_message_evidences_organization_id_finding_id_fkey`: `FOREIGN KEY (organization_id, finding_id) REFERENCES scan_vulnerability_findings(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `assistant_message_evidences_organization_id_message_id_fkey`: `FOREIGN KEY (organization_id, message_id) REFERENCES assistant_messages(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `assistant_message_evidences_organization_id_message_id_ordi_key`: `UNIQUE (organization_id, message_id, ordinal)`. Validada: True.
- `assistant_message_evidences_organization_id_scan_component_fkey`: `FOREIGN KEY (organization_id, scan_component_id) REFERENCES scan_components(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `assistant_message_evidences_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX assistant_message_evidences_organization_id_message_id_ordi_key ON public.assistant_message_evidences USING btree (organization_id, message_id, ordinal)`
- `CREATE UNIQUE INDEX assistant_message_evidences_pkey ON public.assistant_message_evidences USING btree (id)`
- `CREATE INDEX ix_fk_assistant_message_evidences_157839da7b27 ON public.assistant_message_evidences USING btree (organization_id, document_chunk_id)`
- `CREATE INDEX ix_fk_assistant_message_evidences_5f32a658af26 ON public.assistant_message_evidences USING btree (organization_id, finding_id)`
- `CREATE INDEX ix_fk_assistant_message_evidences_d20075151e64 ON public.assistant_message_evidences USING btree (organization_id, scan_component_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## assistant_messages

Mensajes ordenados, modelo/prompt y consumo observado.

Módulo: **documentation**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `conversation_id` | `uuid` | No | — | FK, U |
| `ordinal` | `integer` | No | — | U |
| `role` | `text` | No | — |  |
| `content` | `text` | No | — |  |
| `model_version_id` | `uuid` | Sí | — | FK |
| `prompt_version_id` | `uuid` | Sí | — | FK |
| `input_tokens` | `integer` | Sí | — |  |
| `output_tokens` | `integer` | Sí | — |  |
| `status` | `text` | No | 'COMPLETED'::text |  |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |

Restricciones:

- `assistant_messages_input_tokens_check`: `CHECK ((input_tokens >= 0))`. Validada: True.
- `assistant_messages_model_version_id_fkey`: `FOREIGN KEY (model_version_id) REFERENCES assistant_model_versions(id) ON DELETE RESTRICT`. Validada: True.
- `assistant_messages_ordinal_check`: `CHECK ((ordinal > 0))`. Validada: True.
- `assistant_messages_organization_id_conversation_id_fkey`: `FOREIGN KEY (organization_id, conversation_id) REFERENCES assistant_conversations(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `assistant_messages_organization_id_conversation_id_ordinal_key`: `UNIQUE (organization_id, conversation_id, ordinal)`. Validada: True.
- `assistant_messages_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `assistant_messages_output_tokens_check`: `CHECK ((output_tokens >= 0))`. Validada: True.
- `assistant_messages_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `assistant_messages_prompt_version_id_fkey`: `FOREIGN KEY (prompt_version_id) REFERENCES assistant_prompt_versions(id) ON DELETE RESTRICT`. Validada: True.
- `assistant_messages_role_check`: `CHECK ((role = ANY (ARRAY['USER'::text, 'ASSISTANT'::text, 'SYSTEM'::text])))`. Validada: True.
- `assistant_messages_status_check`: `CHECK ((status = ANY (ARRAY['PENDING'::text, 'COMPLETED'::text, 'FAILED'::text])))`. Validada: True.

Referencias entrantes: `assistant_message_evidences` (organization_id, message_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX assistant_messages_organization_id_conversation_id_ordinal_key ON public.assistant_messages USING btree (organization_id, conversation_id, ordinal)`
- `CREATE UNIQUE INDEX assistant_messages_organization_id_id_key ON public.assistant_messages USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX assistant_messages_pkey ON public.assistant_messages USING btree (id)`
- `CREATE INDEX ix_fk_assistant_messages_08824ba07b70 ON public.assistant_messages USING btree (model_version_id)`
- `CREATE INDEX ix_fk_assistant_messages_25f716824dd8 ON public.assistant_messages USING btree (prompt_version_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## assistant_model_providers

Proveedores confirmados Gemini/Ollama, sin claves ni modelos sembrados.

Módulo: **documentation**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `code` | `text` | No | — | PK |
| `name` | `text` | No | — |  |

Restricciones:

- `assistant_model_providers_pkey`: `PRIMARY KEY (code)`. Validada: True.

Referencias entrantes: `assistant_model_versions` (provider), `embedding_models` (provider).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX assistant_model_providers_pkey ON public.assistant_model_providers USING btree (code)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## assistant_model_versions

Modelo/revisión de generación configurado, sin presumir disponibilidad o gratuidad.

Módulo: **documentation**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `provider` | `text` | No | — | FK, U |
| `model_name` | `text` | No | — | U |
| `revision` | `text` | No | — | U |

Restricciones:

- `assistant_model_versions_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `assistant_model_versions_provider_fkey`: `FOREIGN KEY (provider) REFERENCES assistant_model_providers(code) ON DELETE RESTRICT`. Validada: True.
- `assistant_model_versions_provider_model_name_revision_key`: `UNIQUE (provider, model_name, revision)`. Validada: True.

Referencias entrantes: `assistant_messages` (model_version_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX assistant_model_versions_pkey ON public.assistant_model_versions USING btree (id)`
- `CREATE UNIQUE INDEX assistant_model_versions_provider_model_name_revision_key ON public.assistant_model_versions USING btree (provider, model_name, revision)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## assistant_prompt_versions

Instrucciones versionadas para reproducibilidad, sin secretos.

Módulo: **documentation**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `purpose` | `text` | No | — | U |
| `version` | `text` | No | — | U |
| `instructions` | `text` | No | — |  |

Restricciones:

- `assistant_prompt_versions_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `assistant_prompt_versions_purpose_version_key`: `UNIQUE (purpose, version)`. Validada: True.

Referencias entrantes: `assistant_messages` (prompt_version_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX assistant_prompt_versions_pkey ON public.assistant_prompt_versions USING btree (id)`
- `CREATE UNIQUE INDEX assistant_prompt_versions_purpose_version_key ON public.assistant_prompt_versions USING btree (purpose, version)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## audit_event_changes

Cambios auditables separados, con exclusión básica de atributos secretos.

Módulo: **audit**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK |
| `audit_event_id` | `uuid` | No | — | FK |
| `attribute` | `text` | No | — |  |
| `before_value` | `jsonb` | Sí | — |  |
| `after_value` | `jsonb` | Sí | — |  |

Restricciones:

- `audit_event_changes_attribute_check`: `CHECK ((lower(attribute) !~ '(password|token|secret|private_key)'::text))`. Validada: True.
- `audit_event_changes_organization_id_audit_event_id_fkey`: `FOREIGN KEY (organization_id, audit_event_id) REFERENCES audit_events(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `audit_event_changes_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX audit_event_changes_pkey ON public.audit_event_changes USING btree (id)`
- `CREATE INDEX ix_fk_audit_event_changes_f5eb1396e20f ON public.audit_event_changes USING btree (organization_id, audit_event_id)`

Retención/borrado: append-only; excepción administrativa explícita.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.
- Trigger: `CREATE TRIGGER audit_change_append_only BEFORE DELETE OR UPDATE ON public.audit_event_changes FOR EACH ROW EXECUTE FUNCTION app_private.reject_audit_mutation()`.

## audit_events

Eventos append-only; mantenimiento excepcional exige rol dedicado.

Módulo: **audit**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | — | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `actor_id` | `uuid` | Sí | — | FK |
| `action` | `character varying(100)` | No | — |  |
| `resource_type` | `character varying(100)` | No | — |  |
| `resource_id` | `uuid` | Sí | — |  |
| `occurred_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `metadata` | `jsonb` | No | '{}'::jsonb |  |
| `result` | `text` | No | 'UNKNOWN'::text |  |
| `correlation_id` | `uuid` | Sí | — |  |

Restricciones:

- `audit_events_action_check`: `CHECK ((btrim((action)::text) <> ''::text))`. Validada: True.
- `audit_events_actor_id_fkey`: `FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT`. Validada: True.
- `audit_events_organization_id_fkey`: `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`. Validada: True.
- `audit_events_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `audit_events_resource_type_check`: `CHECK ((btrim((resource_type)::text) <> ''::text))`. Validada: True.
- `audit_events_result_check`: `CHECK ((result = ANY (ARRAY['SUCCESS'::text, 'FAILURE'::text, 'UNKNOWN'::text])))`. Validada: True.
- `uq_audit_event_id`: `UNIQUE (organization_id, id)`. Validada: True.

Referencias entrantes: `audit_event_changes` (organization_id, audit_event_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX audit_events_pkey ON public.audit_events USING btree (id)`
- `CREATE INDEX ix_audit_history ON public.audit_events USING btree (organization_id, occurred_at DESC)`
- `CREATE INDEX ix_fk_audit_events_7c7a37207988 ON public.audit_events USING btree (actor_id)`
- `CREATE UNIQUE INDEX uq_audit_event_id ON public.audit_events USING btree (organization_id, id)`

Retención/borrado: append-only; excepción administrativa explícita.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.
- Trigger: `CREATE TRIGGER audit_append_only BEFORE DELETE OR UPDATE ON public.audit_events FOR EACH ROW EXECUTE FUNCTION app_private.reject_audit_mutation()`.

## background_job_attempts

Intentos numerados limitados por la configuración del job.

Módulo: **scan**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `job_id` | `uuid` | No | — | FK, U |
| `attempt_number` | `integer` | No | — | U |
| `worker` | `text` | No | — |  |
| `started_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `finished_at` | `timestamp with time zone` | Sí | — |  |
| `status` | `text` | No | — | FK |
| `error_code` | `text` | Sí | — |  |
| `error_message` | `text` | Sí | — |  |

Restricciones:

- `background_job_attempts_attempt_number_check`: `CHECK ((attempt_number > 0))`. Validada: True.
- `background_job_attempts_check`: `CHECK (((finished_at IS NULL) OR (finished_at >= started_at)))`. Validada: True.
- `background_job_attempts_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `background_job_attempts_organization_id_job_id_attempt_numb_key`: `UNIQUE (organization_id, job_id, attempt_number)`. Validada: True.
- `background_job_attempts_organization_id_job_id_fkey`: `FOREIGN KEY (organization_id, job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `background_job_attempts_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `background_job_attempts_status_fkey`: `FOREIGN KEY (status) REFERENCES job_statuses(code) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX background_job_attempts_organization_id_id_key ON public.background_job_attempts USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX background_job_attempts_organization_id_job_id_attempt_numb_key ON public.background_job_attempts USING btree (organization_id, job_id, attempt_number)`
- `CREATE UNIQUE INDEX background_job_attempts_pkey ON public.background_job_attempts USING btree (id)`
- `CREATE INDEX ix_fk_background_job_attempts_e2ba64935a19 ON public.background_job_attempts USING btree (status)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.
- Trigger: `CREATE TRIGGER ck_job_attempt_limit BEFORE INSERT OR UPDATE ON public.background_job_attempts FOR EACH ROW EXECUTE FUNCTION app_private.enforce_attempt_limit()`.

## background_job_events

Eventos de ejecución y metadatos técnicos sanitizados por la aplicación.

Módulo: **scan**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK |
| `job_id` | `uuid` | No | — | FK |
| `occurred_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `event_type` | `text` | No | — |  |
| `sanitized_metadata` | `jsonb` | No | '{}'::jsonb |  |

Restricciones:

- `background_job_events_organization_id_job_id_fkey`: `FOREIGN KEY (organization_id, job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `background_job_events_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX background_job_events_pkey ON public.background_job_events USING btree (id)`
- `CREATE INDEX ix_job_events ON public.background_job_events USING btree (organization_id, job_id, occurred_at)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## background_jobs

Cola persistente por organización, locks, deduplicación y límite de intentos.

Módulo: **scan**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `job_type` | `text` | No | — | FK |
| `status` | `text` | No | 'PENDING'::text | FK |
| `payload` | `jsonb` | No | '{}'::jsonb |  |
| `deduplication_key` | `text` | Sí | — |  |
| `available_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `locked_at` | `timestamp with time zone` | Sí | — |  |
| `locked_by` | `text` | Sí | — |  |
| `max_attempts` | `integer` | No | 3 |  |
| `last_error_code` | `text` | Sí | — |  |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |

Restricciones:

- `background_jobs_check`: `CHECK (((locked_at IS NULL) = (locked_by IS NULL)))`. Validada: True.
- `background_jobs_job_type_fkey`: `FOREIGN KEY (job_type) REFERENCES job_types(code) ON DELETE RESTRICT`. Validada: True.
- `background_jobs_max_attempts_check`: `CHECK (((max_attempts >= 1) AND (max_attempts <= 100)))`. Validada: True.
- `background_jobs_organization_id_fkey`: `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`. Validada: True.
- `background_jobs_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `background_jobs_payload_check`: `CHECK ((jsonb_typeof(payload) = 'object'::text))`. Validada: True.
- `background_jobs_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `background_jobs_status_fkey`: `FOREIGN KEY (status) REFERENCES job_statuses(code) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: `background_job_attempts` (organization_id, job_id), `background_job_events` (organization_id, job_id), `change_simulations` (organization_id, background_job_id), `document_processing_runs` (organization_id, background_job_id), `report_jobs` (organization_id, background_job_id), `scans` (organization_id, background_job_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX background_jobs_organization_id_id_key ON public.background_jobs USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX background_jobs_pkey ON public.background_jobs USING btree (id)`
- `CREATE INDEX ix_fk_background_jobs_5a1e07982ad2 ON public.background_jobs USING btree (job_type)`
- `CREATE INDEX ix_fk_background_jobs_b2c24bd9fb74 ON public.background_jobs USING btree (status)`
- `CREATE INDEX ix_job_queue ON public.background_jobs USING btree (organization_id, available_at) WHERE (status = ANY (ARRAY['PENDING'::text, 'RETRYING'::text]))`
- `CREATE UNIQUE INDEX uq_job_deduplication ON public.background_jobs USING btree (organization_id, job_type, deduplication_key) WHERE (deduplication_key IS NOT NULL)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## change_pull_request_proposals

Propuesta de PR; publicación requiere aprobación y referencia externa.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `simulation_id` | `uuid` | No | — | FK |
| `title` | `text` | No | — |  |
| `description` | `text` | No | — |  |
| `proposed_patch_storage_key` | `text` | No | — |  |
| `status` | `text` | No | 'DRAFT'::text |  |
| `approved_by_membership_id` | `uuid` | Sí | — | FK |
| `approved_at` | `timestamp with time zone` | Sí | — |  |
| `published_at` | `timestamp with time zone` | Sí | — |  |
| `external_url` | `text` | Sí | — |  |
| `external_number` | `bigint` | Sí | — |  |

Restricciones:

- `change_pull_request_proposals_check`: `CHECK (((status <> ALL (ARRAY['APPROVED'::text, 'PUBLISHED'::text])) OR ((approved_by_membership_id IS NOT NULL) AND (approved_at IS NOT NULL))))`. Validada: True.
- `change_pull_request_proposals_check1`: `CHECK (((status <> 'PUBLISHED'::text) OR ((published_at IS NOT NULL) AND (external_url IS NOT NULL) AND (external_number IS NOT NULL))))`. Validada: True.
- `change_pull_request_proposals_external_number_check`: `CHECK (((external_number IS NULL) OR (external_number > 0)))`. Validada: True.
- `change_pull_request_proposals_organization_id_approved_by__fkey`: `FOREIGN KEY (organization_id, approved_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `change_pull_request_proposals_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `change_pull_request_proposals_organization_id_simulation_i_fkey`: `FOREIGN KEY (organization_id, simulation_id) REFERENCES change_simulations(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `change_pull_request_proposals_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `change_pull_request_proposals_status_check`: `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'APPROVED'::text, 'PUBLISHED'::text, 'REJECTED'::text])))`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX change_pull_request_proposals_organization_id_id_key ON public.change_pull_request_proposals USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX change_pull_request_proposals_pkey ON public.change_pull_request_proposals USING btree (id)`
- `CREATE INDEX ix_fk_change_pull_request_proposals_2800fa237ee0 ON public.change_pull_request_proposals USING btree (organization_id, simulation_id)`
- `CREATE INDEX ix_fk_change_pull_request_proposals_3985f7f590af ON public.change_pull_request_proposals USING btree (organization_id, approved_by_membership_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## change_record_evidences

Relación M:N cambio/evidencia.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `change_record_id` | `uuid` | No | — | FK, PK |
| `evidence_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `change_record_evidences_organization_id_change_record_id_fkey`: `FOREIGN KEY (organization_id, change_record_id) REFERENCES version_change_records(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `change_record_evidences_organization_id_evidence_id_fkey`: `FOREIGN KEY (organization_id, evidence_id) REFERENCES impact_evidences(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `change_record_evidences_pkey`: `PRIMARY KEY (organization_id, change_record_id, evidence_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX change_record_evidences_pkey ON public.change_record_evidences USING btree (organization_id, change_record_id, evidence_id)`
- `CREATE INDEX ix_fk_change_record_evidences_c4f9e8cbf133 ON public.change_record_evidences USING btree (organization_id, evidence_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## change_simulations

Solicitud aislada ligada a un commit real del análisis y configuración inmutable.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `repository_id` | `uuid` | No | — | FK |
| `impact_analysis_id` | `uuid` | No | — | FK |
| `base_commit_id` | `uuid` | No | — | FK |
| `background_job_id` | `uuid` | Sí | — | FK |
| `requested_by_membership_id` | `uuid` | Sí | — | FK |
| `environment_reference` | `text` | No | — |  |
| `configuration_version` | `text` | No | — |  |
| `timeout_seconds` | `integer` | No | — |  |
| `status` | `text` | No | 'PENDING'::text | FK |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |

Restricciones:

- `change_simulations_organization_id_background_job_id_fkey`: `FOREIGN KEY (organization_id, background_job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `change_simulations_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `change_simulations_organization_id_repository_id_impact_an_fkey`: `FOREIGN KEY (organization_id, repository_id, impact_analysis_id, base_commit_id) REFERENCES impact_analyses(organization_id, repository_id, id, base_commit_id) ON DELETE RESTRICT`. Validada: True.
- `change_simulations_organization_id_requested_by_membership_fkey`: `FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `change_simulations_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `change_simulations_status_fkey`: `FOREIGN KEY (status) REFERENCES job_statuses(code) ON DELETE RESTRICT`. Validada: True.
- `change_simulations_timeout_seconds_check`: `CHECK ((timeout_seconds > 0))`. Validada: True.

Referencias entrantes: `change_pull_request_proposals` (organization_id, simulation_id), `simulation_test_runs` (organization_id, simulation_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX change_simulations_organization_id_id_key ON public.change_simulations USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX change_simulations_pkey ON public.change_simulations USING btree (id)`
- `CREATE INDEX ix_fk_change_simulations_7bda4004949e ON public.change_simulations USING btree (organization_id, background_job_id)`
- `CREATE INDEX ix_fk_change_simulations_8ae65300126c ON public.change_simulations USING btree (status)`
- `CREATE INDEX ix_fk_change_simulations_9e9df2d16a6f ON public.change_simulations USING btree (organization_id, requested_by_membership_id)`
- `CREATE INDEX ix_fk_change_simulations_b78861486b1e ON public.change_simulations USING btree (organization_id, repository_id, impact_analysis_id, base_commit_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.
- Trigger: `CREATE TRIGGER ck_simulation_context BEFORE UPDATE ON public.change_simulations FOR EACH ROW EXECUTE FUNCTION app_private.protect_simulation_context()`.

## component_external_references

Referencias externas de versión, normalizadas como filas.

Módulo: **component**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK, U |
| `component_version_id` | `uuid` | No | — | FK, U |
| `reference_type` | `text` | No | — | U |
| `url` | `text` | No | — | U |

Restricciones:

- `component_external_references_organization_id_component_ve_fkey`: `FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `component_external_references_organization_id_component_ver_key`: `UNIQUE (organization_id, component_version_id, reference_type, url)`. Validada: True.
- `component_external_references_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX component_external_references_organization_id_component_ver_key ON public.component_external_references USING btree (organization_id, component_version_id, reference_type, url)`
- `CREATE UNIQUE INDEX component_external_references_pkey ON public.component_external_references USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## component_hashes

Hashes observados por algoritmo y versión.

Módulo: **component**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `component_version_id` | `uuid` | No | — | FK, PK |
| `algorithm` | `text` | No | — | PK |
| `digest` | `text` | No | — | PK |

Restricciones:

- `component_hashes_algorithm_check`: `CHECK ((algorithm = ANY (ARRAY['MD5'::text, 'SHA-1'::text, 'SHA-256'::text, 'SHA-512'::text])))`. Validada: True.
- `component_hashes_check`: `CHECK ((length(digest) =
CASE algorithm
    WHEN 'MD5'::text THEN 32
    WHEN 'SHA-1'::text THEN 40
    WHEN 'SHA-256'::text THEN 64
    WHEN 'SHA-512'::text THEN 128
    ELSE NULL::integer
END))`. Validada: True.
- `component_hashes_digest_check`: `CHECK ((digest ~ '^[a-f0-9]+$'::text))`. Validada: True.
- `component_hashes_organization_id_component_version_id_fkey`: `FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `component_hashes_pkey`: `PRIMARY KEY (organization_id, component_version_id, algorithm, digest)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX component_hashes_pkey ON public.component_hashes USING btree (organization_id, component_version_id, algorithm, digest)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## component_identifiers

Identificadores alternativos de una versión, incluidos PURL con distintos qualifiers.

Módulo: **component**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK, U |
| `component_version_id` | `uuid` | No | — | FK |
| `identifier_type` | `text` | No | — | U |
| `value` | `text` | No | — | U |

Restricciones:

- `component_identifiers_identifier_type_check`: `CHECK ((identifier_type = ANY (ARRAY['PURL'::text, 'CPE'::text, 'SWID'::text])))`. Validada: True.
- `component_identifiers_organization_id_component_version_id_fkey`: `FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `component_identifiers_organization_id_identifier_type_value_key`: `UNIQUE (organization_id, identifier_type, value)`. Validada: True.
- `component_identifiers_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `component_identifiers_value_check`: `CHECK ((btrim(value) <> ''::text))`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX component_identifiers_organization_id_identifier_type_value_key ON public.component_identifiers USING btree (organization_id, identifier_type, value)`
- `CREATE UNIQUE INDEX component_identifiers_pkey ON public.component_identifiers USING btree (id)`
- `CREATE INDEX ix_fk_component_identifiers_2c26d5da4575 ON public.component_identifiers USING btree (organization_id, component_version_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## component_licenses

Licencias declaradas a nivel de versión de paquete.

Módulo: **component**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `component_version_id` | `uuid` | No | — | FK, PK |
| `license_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `component_licenses_license_id_fkey`: `FOREIGN KEY (license_id) REFERENCES license_catalog(id) ON DELETE RESTRICT`. Validada: True.
- `component_licenses_organization_id_component_version_id_fkey`: `FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `component_licenses_pkey`: `PRIMARY KEY (organization_id, component_version_id, license_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX component_licenses_pkey ON public.component_licenses USING btree (organization_id, component_version_id, license_id)`
- `CREATE INDEX ix_fk_component_licenses_c86ff8412c69 ON public.component_licenses USING btree (license_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## component_versions

Versión exacta textual; no asume SemVer universal.

Módulo: **component**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `component_id` | `uuid` | No | — | FK, U |
| `version` | `text` | No | — | U |
| `published_at` | `timestamp with time zone` | Sí | — |  |

Restricciones:

- `component_versions_organization_id_component_id_fkey`: `FOREIGN KEY (organization_id, component_id) REFERENCES components(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `component_versions_organization_id_component_id_version_key`: `UNIQUE (organization_id, component_id, version)`. Validada: True.
- `component_versions_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `component_versions_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `component_versions_version_check`: `CHECK ((btrim(version) <> ''::text))`. Validada: True.
- `uq_component_version_package`: `UNIQUE (organization_id, component_id, id)`. Validada: True.

Referencias entrantes: `component_external_references` (organization_id, component_version_id), `component_hashes` (organization_id, component_version_id), `component_identifiers` (organization_id, component_version_id), `component_licenses` (organization_id, component_version_id), `document_component_versions` (organization_id, component_version_id), `impact_analyses` (organization_id, component_id, current_version_id), `impact_analyses` (organization_id, component_id, target_version_id), `impact_analyses` (organization_id, replacement_version_id), `impact_finding_component_versions` (organization_id, component_version_id), `scan_component_changes` (organization_id, component_id, after_version_id), `scan_component_changes` (organization_id, component_id, before_version_id), `scan_components` (organization_id, component_version_id), `version_change_records` (organization_id, component_version_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX component_versions_organization_id_component_id_version_key ON public.component_versions USING btree (organization_id, component_id, version)`
- `CREATE UNIQUE INDEX component_versions_organization_id_id_key ON public.component_versions USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX component_versions_pkey ON public.component_versions USING btree (id)`
- `CREATE UNIQUE INDEX uq_component_version_package ON public.component_versions USING btree (organization_id, component_id, id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## components

Identidad lógica del paquete, privada por organización.

Módulo: **component**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `package_ecosystem_id` | `uuid` | No | — | FK, U |
| `namespace` | `text` | Sí | — | U |
| `name` | `text` | No | — | U |
| `description` | `text` | Sí | — |  |

Restricciones:

- `components_name_check1`: `CHECK ((btrim(name) <> ''::text))`. Validada: True.
- `components_namespace_check`: `CHECK (((namespace IS NULL) OR (btrim(namespace) <> ''::text)))`. Validada: True.
- `components_organization_id_fkey1`: `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`. Validada: True.
- `components_organization_id_id_key1`: `UNIQUE (organization_id, id)`. Validada: True.
- `components_package_ecosystem_id_fkey`: `FOREIGN KEY (package_ecosystem_id) REFERENCES package_ecosystems(id) ON DELETE RESTRICT`. Validada: True.
- `components_pkey1`: `PRIMARY KEY (id)`. Validada: True.
- `uq_component_identity`: `UNIQUE NULLS NOT DISTINCT (organization_id, package_ecosystem_id, namespace, name)`. Validada: True.

Referencias entrantes: `component_versions` (organization_id, component_id), `document_components` (organization_id, component_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX components_organization_id_id_key1 ON public.components USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX components_pkey1 ON public.components USING btree (id)`
- `CREATE INDEX ix_components_lookup ON public.components USING btree (organization_id, package_ecosystem_id, name)`
- `CREATE INDEX ix_fk_components_fd8dd6ce06ec ON public.components USING btree (package_ecosystem_id)`
- `CREATE UNIQUE INDEX uq_component_identity ON public.components USING btree (organization_id, package_ecosystem_id, namespace, name) NULLS NOT DISTINCT`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## data_deletion_requests

Solicitud tipada de eliminación y estado; no borra historia en cascada.

Módulo: **audit**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK |
| `requested_by_membership_id` | `uuid` | Sí | — | FK |
| `document_id` | `uuid` | Sí | — | FK |
| `integration_id` | `uuid` | Sí | — | FK |
| `scan_id` | `uuid` | Sí | — | FK |
| `report_job_id` | `uuid` | Sí | — | FK |
| `conversation_id` | `uuid` | Sí | — | FK |
| `status` | `text` | No | 'PENDING'::text | FK |
| `requested_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `completed_at` | `timestamp with time zone` | Sí | — |  |

Restricciones:

- `data_deletion_requests_check`: `CHECK ((num_nonnulls(document_id, integration_id, scan_id, report_job_id, conversation_id) = 1))`. Validada: True.
- `data_deletion_requests_organization_id_conversation_id_fkey`: `FOREIGN KEY (organization_id, conversation_id) REFERENCES assistant_conversations(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `data_deletion_requests_organization_id_document_id_fkey`: `FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `data_deletion_requests_organization_id_integration_id_fkey`: `FOREIGN KEY (organization_id, integration_id) REFERENCES integrations(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `data_deletion_requests_organization_id_report_job_id_fkey`: `FOREIGN KEY (organization_id, report_job_id) REFERENCES report_jobs(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `data_deletion_requests_organization_id_requested_by_member_fkey`: `FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `data_deletion_requests_organization_id_scan_id_fkey`: `FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `data_deletion_requests_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `data_deletion_requests_status_fkey`: `FOREIGN KEY (status) REFERENCES job_statuses(code) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX data_deletion_requests_pkey ON public.data_deletion_requests USING btree (id)`
- `CREATE INDEX ix_fk_data_deletion_requests_02b65a58e156 ON public.data_deletion_requests USING btree (organization_id, integration_id)`
- `CREATE INDEX ix_fk_data_deletion_requests_2ca733425449 ON public.data_deletion_requests USING btree (organization_id, requested_by_membership_id)`
- `CREATE INDEX ix_fk_data_deletion_requests_2f36d2fc813c ON public.data_deletion_requests USING btree (status)`
- `CREATE INDEX ix_fk_data_deletion_requests_2f798a8b08ee ON public.data_deletion_requests USING btree (organization_id, scan_id)`
- `CREATE INDEX ix_fk_data_deletion_requests_56fd44497575 ON public.data_deletion_requests USING btree (organization_id, report_job_id)`
- `CREATE INDEX ix_fk_data_deletion_requests_e28e06b5ce45 ON public.data_deletion_requests USING btree (organization_id, document_id)`
- `CREATE INDEX ix_fk_data_deletion_requests_f808717e8451 ON public.data_deletion_requests USING btree (organization_id, conversation_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## data_retention_policies

Retención/eliminación por organización y tipo, sin inventar periodos legales.

Módulo: **audit**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `resource_type` | `text` | No | — | U |
| `action` | `text` | No | — |  |
| `retention_days` | `integer` | Sí | — |  |
| `is_enabled` | `boolean` | No | true |  |

Restricciones:

- `data_retention_policies_action_check`: `CHECK ((action = ANY (ARRAY['KEEP'::text, 'DELETE'::text, 'ANONYMIZE'::text, 'ARCHIVE'::text])))`. Validada: True.
- `data_retention_policies_check`: `CHECK ((((action = 'KEEP'::text) AND (retention_days IS NULL)) OR ((action <> 'KEEP'::text) AND (retention_days IS NOT NULL) AND (retention_days > 0))))`. Validada: True.
- `data_retention_policies_organization_id_fkey`: `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`. Validada: True.
- `data_retention_policies_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `data_retention_policies_organization_id_resource_type_key`: `UNIQUE (organization_id, resource_type)`. Validada: True.
- `data_retention_policies_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `data_retention_policies_resource_type_check`: `CHECK ((resource_type = ANY (ARRAY['DOCUMENT_ORIGINAL'::text, 'DOCUMENT_TEMP'::text, 'SBOM'::text, 'SCAN_LOG'::text, 'AUDIT'::text, 'ADVISORY_PAYLOAD'::text, 'ASSISTANT_MESSAGE'::text, 'REPORT'::text])))`. Validada: True.

Referencias entrantes: `documents` (organization_id, retention_policy_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX data_retention_policies_organization_id_id_key ON public.data_retention_policies USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX data_retention_policies_organization_id_resource_type_key ON public.data_retention_policies USING btree (organization_id, resource_type)`
- `CREATE UNIQUE INDEX data_retention_policies_pkey ON public.data_retention_policies USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## dependency_edges

Arista entre dos apariciones del mismo snapshot; no demuestra comunicación runtime.

Módulo: **scan**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, U |
| `scan_id` | `uuid` | No | — | FK, U |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `source_scan_component_id` | `uuid` | No | — | FK, U |
| `target_scan_component_id` | `uuid` | No | — | FK, U |
| `relationship_type_id` | `uuid` | No | — | FK, U |

Restricciones:

- `ck_dependency_edge_not_self`: `CHECK ((source_scan_component_id <> target_scan_component_id))`. Validada: True.
- `dependency_edges_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `dependency_edges_relationship_type_id_fkey`: `FOREIGN KEY (relationship_type_id) REFERENCES dependency_relationship_types(id) ON DELETE RESTRICT`. Validada: True.
- `fk_edge_source`: `FOREIGN KEY (organization_id, scan_id, source_scan_component_id) REFERENCES scan_components(organization_id, scan_id, id) ON DELETE RESTRICT`. Validada: True.
- `fk_edge_target`: `FOREIGN KEY (organization_id, scan_id, target_scan_component_id) REFERENCES scan_components(organization_id, scan_id, id) ON DELETE RESTRICT`. Validada: True.
- `uq_dependency_edge`: `UNIQUE (organization_id, scan_id, source_scan_component_id, target_scan_component_id, relationship_type_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX dependency_edges_pkey ON public.dependency_edges USING btree (id)`
- `CREATE INDEX ix_dependency_edge_target ON public.dependency_edges USING btree (organization_id, scan_id, target_scan_component_id)`
- `CREATE INDEX ix_fk_dependency_edges_e6cd24f84dab ON public.dependency_edges USING btree (relationship_type_id)`
- `CREATE UNIQUE INDEX uq_dependency_edge ON public.dependency_edges USING btree (organization_id, scan_id, source_scan_component_id, target_scan_component_id, relationship_type_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## dependency_relationship_types

Tipos semánticos de aristas.

Módulo: **component**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `code` | `text` | No | — | U |

Restricciones:

- `dependency_relationship_types_code_key`: `UNIQUE (code)`. Validada: True.
- `dependency_relationship_types_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: `dependency_edges` (relationship_type_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX dependency_relationship_types_code_key ON public.dependency_relationship_types USING btree (code)`
- `CREATE UNIQUE INDEX dependency_relationship_types_pkey ON public.dependency_relationship_types USING btree (id)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## dependency_scopes

Scopes conocidos de dependencias.

Módulo: **component**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `code` | `text` | No | — | U |

Restricciones:

- `dependency_scopes_code_key`: `UNIQUE (code)`. Validada: True.
- `dependency_scopes_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: `scan_components` (dependency_scope_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX dependency_scopes_code_key ON public.dependency_scopes USING btree (code)`
- `CREATE UNIQUE INDEX dependency_scopes_pkey ON public.dependency_scopes USING btree (id)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## document_chunk_embeddings

Metadatos de chunk/modelo; vector físico requiere migración opcional.

Módulo: **documentation**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `document_chunk_id` | `uuid` | No | — | FK, U |
| `embedding_model_id` | `uuid` | No | — | FK, U |
| `status` | `text` | No | 'PENDING'::text |  |
| `vector_reference` | `text` | Sí | — |  |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |

Restricciones:

- `document_chunk_embeddings_check`: `CHECK (((status = 'READY'::text) = (vector_reference IS NOT NULL)))`. Validada: True.
- `document_chunk_embeddings_embedding_model_id_fkey`: `FOREIGN KEY (embedding_model_id) REFERENCES embedding_models(id) ON DELETE RESTRICT`. Validada: True.
- `document_chunk_embeddings_organization_id_document_chunk_i_fkey`: `FOREIGN KEY (organization_id, document_chunk_id) REFERENCES document_chunks(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `document_chunk_embeddings_organization_id_document_chunk_id_key`: `UNIQUE (organization_id, document_chunk_id, embedding_model_id)`. Validada: True.
- `document_chunk_embeddings_organization_id_id_embedding_mode_key`: `UNIQUE (organization_id, id, embedding_model_id)`. Validada: True.
- `document_chunk_embeddings_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `document_chunk_embeddings_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `document_chunk_embeddings_status_check`: `CHECK ((status = ANY (ARRAY['PENDING'::text, 'FAILED'::text, 'READY'::text])))`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX document_chunk_embeddings_organization_id_document_chunk_id_key ON public.document_chunk_embeddings USING btree (organization_id, document_chunk_id, embedding_model_id)`
- `CREATE UNIQUE INDEX document_chunk_embeddings_organization_id_id_embedding_mode_key ON public.document_chunk_embeddings USING btree (organization_id, id, embedding_model_id)`
- `CREATE UNIQUE INDEX document_chunk_embeddings_organization_id_id_key ON public.document_chunk_embeddings USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX document_chunk_embeddings_pkey ON public.document_chunk_embeddings USING btree (id)`
- `CREATE INDEX ix_fk_document_chunk_embeddings_8a978aee4c75 ON public.document_chunk_embeddings USING btree (embedding_model_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## document_chunks

Texto extraído con orden, páginas/sección y hash; índice textual simple.

Módulo: **documentation**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `document_version_id` | `uuid` | No | — | FK, U |
| `ordinal` | `integer` | No | — | U |
| `content` | `text` | No | — |  |
| `page_start` | `integer` | Sí | — |  |
| `page_end` | `integer` | Sí | — |  |
| `section_path` | `text` | Sí | — |  |
| `token_count` | `integer` | Sí | — |  |
| `content_hash` | `text` | No | — |  |

Restricciones:

- `document_chunks_check`: `CHECK ((((page_start IS NULL) AND (page_end IS NULL)) OR ((page_start IS NOT NULL) AND (page_end IS NOT NULL) AND (page_start > 0) AND (page_end >= page_start))))`. Validada: True.
- `document_chunks_content_check`: `CHECK ((btrim(content) <> ''::text))`. Validada: True.
- `document_chunks_content_hash_check`: `CHECK ((content_hash ~ '^[a-f0-9]{64}$'::text))`. Validada: True.
- `document_chunks_ordinal_check`: `CHECK ((ordinal > 0))`. Validada: True.
- `document_chunks_organization_id_document_version_id_fkey`: `FOREIGN KEY (organization_id, document_version_id) REFERENCES document_versions(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `document_chunks_organization_id_document_version_id_ordinal_key`: `UNIQUE (organization_id, document_version_id, ordinal)`. Validada: True.
- `document_chunks_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `document_chunks_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `document_chunks_token_count_check`: `CHECK ((token_count >= 0))`. Validada: True.

Referencias entrantes: `assistant_message_evidences` (organization_id, document_chunk_id), `document_chunk_embeddings` (organization_id, document_chunk_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX document_chunks_organization_id_document_version_id_ordinal_key ON public.document_chunks USING btree (organization_id, document_version_id, ordinal)`
- `CREATE UNIQUE INDEX document_chunks_organization_id_id_key ON public.document_chunks USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX document_chunks_pkey ON public.document_chunks USING btree (id)`
- `CREATE INDEX ix_document_chunks_text ON public.document_chunks USING gin (to_tsvector('simple'::regconfig, content))`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## document_component_versions

Asociación M:N documento/versión concreta.

Módulo: **documentation**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `document_id` | `uuid` | No | — | FK, PK |
| `component_version_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `document_component_versions_organization_id_component_vers_fkey`: `FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `document_component_versions_organization_id_document_id_fkey`: `FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `document_component_versions_pkey`: `PRIMARY KEY (organization_id, document_id, component_version_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX document_component_versions_pkey ON public.document_component_versions USING btree (organization_id, document_id, component_version_id)`
- `CREATE INDEX ix_fk_document_component_versions_1048a521a6f6 ON public.document_component_versions USING btree (organization_id, component_version_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## document_components

Asociación M:N documento/paquete.

Módulo: **documentation**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `document_id` | `uuid` | No | — | FK, PK |
| `component_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `document_components_organization_id_component_id_fkey`: `FOREIGN KEY (organization_id, component_id) REFERENCES components(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `document_components_organization_id_document_id_fkey`: `FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `document_components_pkey`: `PRIMARY KEY (organization_id, document_id, component_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX document_components_pkey ON public.document_components USING btree (organization_id, document_id, component_id)`
- `CREATE INDEX ix_fk_document_components_6a6510cbe6ac ON public.document_components USING btree (organization_id, component_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## document_processing_artifacts

Artefactos temporales del procesamiento y su expiración.

Módulo: **documentation**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK |
| `processing_run_id` | `uuid` | No | — | FK |
| `artifact_type` | `text` | No | — |  |
| `storage_key` | `text` | No | — |  |
| `sha256` | `text` | No | — |  |
| `expires_at` | `timestamp with time zone` | No | — |  |
| `deleted_at` | `timestamp with time zone` | Sí | — |  |

Restricciones:

- `document_processing_artifacts_organization_id_processing_r_fkey`: `FOREIGN KEY (organization_id, processing_run_id) REFERENCES document_processing_runs(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `document_processing_artifacts_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `document_processing_artifacts_sha256_check`: `CHECK ((sha256 ~ '^[a-f0-9]{64}$'::text))`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX document_processing_artifacts_pkey ON public.document_processing_artifacts USING btree (id)`
- `CREATE INDEX ix_fk_document_processing_artifacts_6ba8c62228f8 ON public.document_processing_artifacts USING btree (organization_id, processing_run_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## document_processing_runs

Extracción/OCR/chunking/embedding/indexación con herramienta/versión.

Módulo: **documentation**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `document_version_id` | `uuid` | No | — | FK |
| `background_job_id` | `uuid` | Sí | — | FK |
| `stage` | `text` | No | — |  |
| `tool_name` | `text` | No | — |  |
| `tool_version` | `text` | Sí | — |  |
| `status` | `text` | No | — | FK |
| `started_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `finished_at` | `timestamp with time zone` | Sí | — |  |
| `error_code` | `text` | Sí | — |  |

Restricciones:

- `document_processing_runs_check`: `CHECK (((finished_at IS NULL) OR (finished_at >= started_at)))`. Validada: True.
- `document_processing_runs_organization_id_background_job_id_fkey`: `FOREIGN KEY (organization_id, background_job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `document_processing_runs_organization_id_document_version__fkey`: `FOREIGN KEY (organization_id, document_version_id) REFERENCES document_versions(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `document_processing_runs_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `document_processing_runs_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `document_processing_runs_stage_check`: `CHECK ((stage = ANY (ARRAY['EXTRACT'::text, 'OCR'::text, 'CHUNK'::text, 'EMBED'::text, 'INDEX'::text])))`. Validada: True.
- `document_processing_runs_status_fkey`: `FOREIGN KEY (status) REFERENCES job_statuses(code) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: `document_processing_artifacts` (organization_id, processing_run_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX document_processing_runs_organization_id_id_key ON public.document_processing_runs USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX document_processing_runs_pkey ON public.document_processing_runs USING btree (id)`
- `CREATE INDEX ix_fk_document_processing_runs_0614fe53e318 ON public.document_processing_runs USING btree (status)`
- `CREATE INDEX ix_fk_document_processing_runs_0687e92348ee ON public.document_processing_runs USING btree (organization_id, document_version_id)`
- `CREATE INDEX ix_fk_document_processing_runs_700feddaea30 ON public.document_processing_runs USING btree (organization_id, background_job_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## document_repositories

Asociación M:N documento/repositorio del mismo tenant.

Módulo: **documentation**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `document_id` | `uuid` | No | — | FK, PK |
| `repository_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `document_repositories_organization_id_document_id_fkey`: `FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `document_repositories_organization_id_repository_id_fkey`: `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `document_repositories_pkey`: `PRIMARY KEY (organization_id, document_id, repository_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX document_repositories_pkey ON public.document_repositories USING btree (organization_id, document_id, repository_id)`
- `CREATE INDEX ix_document_repository_reverse ON public.document_repositories USING btree (organization_id, repository_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## document_review_flags

Documentos potencialmente desactualizados; no afirma que sean incorrectos.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK, U |
| `document_id` | `uuid` | No | — | FK, U |
| `impact_finding_id` | `uuid` | No | — | FK, U |
| `reason` | `text` | No | — |  |
| `status` | `text` | No | 'PENDING'::text |  |

Restricciones:

- `document_review_flags_organization_id_document_id_fkey`: `FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `document_review_flags_organization_id_document_id_impact_fi_key`: `UNIQUE (organization_id, document_id, impact_finding_id)`. Validada: True.
- `document_review_flags_organization_id_impact_finding_id_fkey`: `FOREIGN KEY (organization_id, impact_finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `document_review_flags_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `document_review_flags_status_check`: `CHECK ((status = ANY (ARRAY['PENDING'::text, 'REVIEWED'::text, 'DISMISSED'::text])))`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX document_review_flags_organization_id_document_id_impact_fi_key ON public.document_review_flags USING btree (organization_id, document_id, impact_finding_id)`
- `CREATE UNIQUE INDEX document_review_flags_pkey ON public.document_review_flags USING btree (id)`
- `CREATE INDEX ix_fk_document_review_flags_fc57bb8047ed ON public.document_review_flags USING btree (organization_id, impact_finding_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## document_types

Tipos documentales conocidos.

Módulo: **documentation**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `code` | `text` | No | — | PK |
| `description` | `text` | No | — |  |

Restricciones:

- `document_types_pkey`: `PRIMARY KEY (code)`. Validada: True.

Referencias entrantes: `documents` (document_type).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX document_types_pkey ON public.document_types USING btree (code)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## document_versions

Original privado retenido o eliminado, hash, MIME/tamaño y temporal con expiración.

Módulo: **documentation**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `document_id` | `uuid` | No | — | FK, U |
| `version_number` | `integer` | No | — | U |
| `sha256` | `text` | No | — |  |
| `size_bytes` | `bigint` | No | — |  |
| `mime_type` | `text` | No | — |  |
| `original_filename` | `text` | No | — |  |
| `storage_backend` | `text` | No | — |  |
| `original_status` | `text` | No | 'RETAINED'::text |  |
| `storage_key` | `text` | Sí | — |  |
| `temporary_storage_key` | `text` | Sí | — |  |
| `temporary_expires_at` | `timestamp with time zone` | Sí | — |  |
| `deleted_at` | `timestamp with time zone` | Sí | — |  |
| `indexing_status` | `text` | No | 'PENDING'::text | FK |
| `uploaded_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |

Restricciones:

- `document_versions_check`: `CHECK ((((original_status = 'RETAINED'::text) AND (storage_key IS NOT NULL) AND (deleted_at IS NULL)) OR ((original_status = 'DELETED'::text) AND (storage_key IS NULL) AND (temporary_storage_key IS NULL) AND (deleted_at IS NOT NULL))))`. Validada: True.
- `document_versions_check1`: `CHECK (((temporary_storage_key IS NULL) = (temporary_expires_at IS NULL)))`. Validada: True.
- `document_versions_indexing_status_fkey`: `FOREIGN KEY (indexing_status) REFERENCES job_statuses(code) ON DELETE RESTRICT`. Validada: True.
- `document_versions_organization_id_document_id_fkey`: `FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `document_versions_organization_id_document_id_version_numbe_key`: `UNIQUE (organization_id, document_id, version_number)`. Validada: True.
- `document_versions_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `document_versions_original_status_check`: `CHECK ((original_status = ANY (ARRAY['RETAINED'::text, 'DELETED'::text])))`. Validada: True.
- `document_versions_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `document_versions_sha256_check`: `CHECK ((sha256 ~ '^[a-f0-9]{64}$'::text))`. Validada: True.
- `document_versions_size_bytes_check`: `CHECK ((size_bytes > 0))`. Validada: True.
- `document_versions_storage_backend_check`: `CHECK ((storage_backend = ANY (ARRAY['LOCAL'::text, 'OBJECT'::text])))`. Validada: True.
- `document_versions_storage_key_check`: `CHECK (((storage_key IS NULL) OR ((storage_key ~ '^[A-Za-z0-9][A-Za-z0-9/_.-]*$'::text) AND (storage_key !~ '(^|/)\.\.(/|$)'::text))))`. Validada: True.
- `document_versions_temporary_storage_key_check`: `CHECK (((temporary_storage_key IS NULL) OR ((temporary_storage_key ~ '^[A-Za-z0-9][A-Za-z0-9/_.-]*$'::text) AND (temporary_storage_key !~ '(^|/)\.\.(/|$)'::text))))`. Validada: True.
- `document_versions_version_number_check`: `CHECK ((version_number > 0))`. Validada: True.

Referencias entrantes: `document_chunks` (organization_id, document_version_id), `document_processing_runs` (organization_id, document_version_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX document_versions_organization_id_document_id_version_numbe_key ON public.document_versions USING btree (organization_id, document_id, version_number)`
- `CREATE UNIQUE INDEX document_versions_organization_id_id_key ON public.document_versions USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX document_versions_pkey ON public.document_versions USING btree (id)`
- `CREATE INDEX ix_fk_document_versions_b0f5be7e368b ON public.document_versions USING btree (indexing_status)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## documents

Documento lógico y política de retención de la organización.

Módulo: **documentation**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `name` | `text` | No | — |  |
| `document_type` | `text` | No | — | FK |
| `origin` | `text` | No | — |  |
| `description` | `text` | Sí | — |  |
| `status` | `text` | No | 'ACTIVE'::text |  |
| `uploaded_by_membership_id` | `uuid` | Sí | — | FK |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `retention_policy_id` | `uuid` | Sí | — | FK |

Restricciones:

- `documents_document_type_fkey`: `FOREIGN KEY (document_type) REFERENCES document_types(code) ON DELETE RESTRICT`. Validada: True.
- `documents_name_check`: `CHECK ((btrim(name) <> ''::text))`. Validada: True.
- `documents_organization_id_fkey`: `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`. Validada: True.
- `documents_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `documents_organization_id_uploaded_by_membership_id_fkey`: `FOREIGN KEY (organization_id, uploaded_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `documents_origin_check`: `CHECK ((origin = ANY (ARRAY['UPLOAD'::text, 'REPOSITORY'::text, 'INTEGRATION'::text])))`. Validada: True.
- `documents_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `documents_status_check`: `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'ARCHIVED'::text, 'DELETION_PENDING'::text])))`. Validada: True.
- `fk_document_retention`: `FOREIGN KEY (organization_id, retention_policy_id) REFERENCES data_retention_policies(organization_id, id) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: `data_deletion_requests` (organization_id, document_id), `document_component_versions` (organization_id, document_id), `document_components` (organization_id, document_id), `document_repositories` (organization_id, document_id), `document_review_flags` (organization_id, document_id), `document_versions` (organization_id, document_id), `impact_finding_documents` (organization_id, document_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX documents_organization_id_id_key ON public.documents USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX documents_pkey ON public.documents USING btree (id)`
- `CREATE INDEX ix_fk_documents_421921caa418 ON public.documents USING btree (organization_id, uploaded_by_membership_id)`
- `CREATE INDEX ix_fk_documents_b22d0253a2bd ON public.documents USING btree (organization_id, retention_policy_id)`
- `CREATE INDEX ix_fk_documents_f24b0d1fc7b4 ON public.documents USING btree (document_type)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## embedding_models

Modelo/revisión/dimensión verificados antes de usar vectores.

Módulo: **documentation**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `provider` | `text` | No | — | FK, U |
| `model_name` | `text` | No | — | U |
| `revision` | `text` | No | — | U |
| `dimension` | `integer` | No | — |  |

Restricciones:

- `embedding_models_dimension_check`: `CHECK ((dimension > 0))`. Validada: True.
- `embedding_models_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `embedding_models_provider_fkey`: `FOREIGN KEY (provider) REFERENCES assistant_model_providers(code) ON DELETE RESTRICT`. Validada: True.
- `embedding_models_provider_model_name_revision_key`: `UNIQUE (provider, model_name, revision)`. Validada: True.

Referencias entrantes: `document_chunk_embeddings` (embedding_model_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX embedding_models_pkey ON public.embedding_models USING btree (id)`
- `CREATE UNIQUE INDEX embedding_models_provider_model_name_revision_key ON public.embedding_models USING btree (provider, model_name, revision)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## finding_evidence

Explicación y registro fuente del mismo advisory que el hallazgo.

Módulo: **vulnerability**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK |
| `finding_id` | `uuid` | No | — | FK |
| `vulnerability_id` | `uuid` | No | — | FK |
| `source_record_id` | `uuid` | Sí | — | FK |
| `evidence_type` | `text` | No | — |  |
| `observed_version` | `text` | Sí | — |  |
| `explanation` | `text` | No | — |  |

Restricciones:

- `finding_evidence_organization_id_finding_id_vulnerability__fkey`: `FOREIGN KEY (organization_id, finding_id, vulnerability_id) REFERENCES scan_vulnerability_findings(organization_id, id, vulnerability_id) ON DELETE RESTRICT`. Validada: True.
- `finding_evidence_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `finding_evidence_source_record_id_vulnerability_id_fkey`: `FOREIGN KEY (source_record_id, vulnerability_id) REFERENCES vulnerability_source_records(id, vulnerability_id) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX finding_evidence_pkey ON public.finding_evidence USING btree (id)`
- `CREATE INDEX ix_finding_evidence ON public.finding_evidence USING btree (organization_id, finding_id)`
- `CREATE INDEX ix_fk_finding_evidence_3490f02b3f6b ON public.finding_evidence USING btree (source_record_id, vulnerability_id)`
- `CREATE INDEX ix_fk_finding_evidence_e65c45176051 ON public.finding_evidence USING btree (organization_id, finding_id, vulnerability_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## finding_fixed_versions

Fixes observados en el contexto de un hallazgo; no se promueven a verdad global.

Módulo: **vulnerability**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `finding_id` | `uuid` | No | — | FK, PK |
| `version` | `text` | No | — | PK |

Restricciones:

- `finding_fixed_versions_organization_id_finding_id_fkey`: `FOREIGN KEY (organization_id, finding_id) REFERENCES scan_vulnerability_findings(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `finding_fixed_versions_pkey`: `PRIMARY KEY (organization_id, finding_id, version)`. Validada: True.
- `finding_fixed_versions_version_check`: `CHECK ((btrim(version) <> ''::text))`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX finding_fixed_versions_pkey ON public.finding_fixed_versions USING btree (organization_id, finding_id, version)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## github_installations

Subtipo GitHub: instalación y cuenta conectadas a una integración GitHub.

Módulo: **integration**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `integration_id` | `uuid` | No | — | FK, U |
| `installation_external_id` | `bigint` | No | — | U |
| `account_type` | `text` | No | — |  |
| `provider` | `text` | No | 'github'::text | FK |

Restricciones:

- `fk_github_provider`: `FOREIGN KEY (organization_id, integration_id, provider) REFERENCES integrations(organization_id, id, provider) ON DELETE RESTRICT`. Validada: True.
- `github_installations_account_type_check`: `CHECK ((account_type = ANY (ARRAY['USER'::text, 'ORGANIZATION'::text])))`. Validada: True.
- `github_installations_installation_external_id_check`: `CHECK ((installation_external_id > 0))`. Validada: True.
- `github_installations_installation_external_id_key`: `UNIQUE (installation_external_id)`. Validada: True.
- `github_installations_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `github_installations_organization_id_integration_id_fkey`: `FOREIGN KEY (organization_id, integration_id) REFERENCES integrations(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `github_installations_organization_id_integration_id_key`: `UNIQUE (organization_id, integration_id)`. Validada: True.
- `github_installations_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `github_installations_provider_check`: `CHECK ((provider = 'github'::text))`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX github_installations_installation_external_id_key ON public.github_installations USING btree (installation_external_id)`
- `CREATE UNIQUE INDEX github_installations_organization_id_id_key ON public.github_installations USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX github_installations_organization_id_integration_id_key ON public.github_installations USING btree (organization_id, integration_id)`
- `CREATE UNIQUE INDEX github_installations_pkey ON public.github_installations USING btree (id)`
- `CREATE INDEX ix_fk_github_installations_468d48d4e1a0 ON public.github_installations USING btree (organization_id, integration_id, provider)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## impact_analyses

Solicitud ADD/UPDATE/REMOVE/REPLACE ligada a snapshot/commit y versiones.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `repository_id` | `uuid` | No | — | FK, U |
| `base_scan_id` | `uuid` | No | — | FK |
| `base_commit_id` | `uuid` | Sí | — | FK, U |
| `component_id` | `uuid` | No | — | FK |
| `current_version_id` | `uuid` | Sí | — | FK |
| `target_version_id` | `uuid` | Sí | — | FK |
| `replacement_version_id` | `uuid` | Sí | — | FK |
| `operation` | `text` | No | — |  |
| `status` | `text` | No | 'PENDING'::text | FK |
| `method_version` | `text` | No | — |  |
| `requested_by_membership_id` | `uuid` | Sí | — | FK |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |

Restricciones:

- `impact_analyses_check`: `CHECK ((((operation = 'ADD'::text) AND (current_version_id IS NULL) AND (target_version_id IS NOT NULL) AND (replacement_version_id IS NULL)) OR ((operation = 'UPDATE'::text) AND (current_version_id IS NOT NULL) AND (target_version_id IS NOT NULL) AND (current_version_id <> target_version_id) AND (replacement_version_id IS NULL)) OR ((operation = 'REMOVE'::text) AND (current_version_id IS NOT NULL) AND (target_version_id IS NULL) AND (replacement_version_id IS NULL)) OR ((operation = 'REPLACE'::text) AND (current_version_id IS NOT NULL) AND (target_version_id IS NULL) AND (replacement_version_id IS NOT NULL) AND (replacement_version_id <> current_version_id))))`. Validada: True.
- `impact_analyses_operation_check`: `CHECK ((operation = ANY (ARRAY['ADD'::text, 'UPDATE'::text, 'REMOVE'::text, 'REPLACE'::text])))`. Validada: True.
- `impact_analyses_organization_id_component_id_current_versi_fkey`: `FOREIGN KEY (organization_id, component_id, current_version_id) REFERENCES component_versions(organization_id, component_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_analyses_organization_id_component_id_target_versio_fkey`: `FOREIGN KEY (organization_id, component_id, target_version_id) REFERENCES component_versions(organization_id, component_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_analyses_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `impact_analyses_organization_id_replacement_version_id_fkey`: `FOREIGN KEY (organization_id, replacement_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_analyses_organization_id_repository_id_base_scan_i_fkey1`: `FOREIGN KEY (organization_id, repository_id, base_scan_id, base_commit_id) REFERENCES scans(organization_id, repository_id, id, repository_commit_id) ON DELETE RESTRICT`. Validada: True.
- `impact_analyses_organization_id_repository_id_base_scan_id_fkey`: `FOREIGN KEY (organization_id, repository_id, base_scan_id) REFERENCES scans(organization_id, repository_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_analyses_organization_id_repository_id_id_base_commi_key`: `UNIQUE (organization_id, repository_id, id, base_commit_id)`. Validada: True.
- `impact_analyses_organization_id_requested_by_membership_id_fkey`: `FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_analyses_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `impact_analyses_status_fkey`: `FOREIGN KEY (status) REFERENCES job_statuses(code) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: `change_simulations` (organization_id, repository_id, impact_analysis_id, base_commit_id), `impact_evidences` (organization_id, impact_analysis_id), `impact_findings` (organization_id, impact_analysis_id), `impact_recommendations` (organization_id, impact_analysis_id), `impact_test_suggestions` (organization_id, impact_analysis_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX impact_analyses_organization_id_id_key ON public.impact_analyses USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX impact_analyses_organization_id_repository_id_id_base_commi_key ON public.impact_analyses USING btree (organization_id, repository_id, id, base_commit_id)`
- `CREATE UNIQUE INDEX impact_analyses_pkey ON public.impact_analyses USING btree (id)`
- `CREATE INDEX ix_fk_impact_analyses_2eb939bcdc33 ON public.impact_analyses USING btree (organization_id, replacement_version_id)`
- `CREATE INDEX ix_fk_impact_analyses_7a3e13d387e5 ON public.impact_analyses USING btree (organization_id, component_id, current_version_id)`
- `CREATE INDEX ix_fk_impact_analyses_cc076871ee31 ON public.impact_analyses USING btree (organization_id, component_id, target_version_id)`
- `CREATE INDEX ix_fk_impact_analyses_dc7598238c8c ON public.impact_analyses USING btree (organization_id, repository_id, base_scan_id, base_commit_id)`
- `CREATE INDEX ix_fk_impact_analyses_ecb421aad706 ON public.impact_analyses USING btree (organization_id, requested_by_membership_id)`
- `CREATE INDEX ix_fk_impact_analyses_fcefd3146cd4 ON public.impact_analyses USING btree (status)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## impact_evidences

Evidencia observada con origen/extracto/página/fecha/hash.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `impact_analysis_id` | `uuid` | No | — | FK |
| `evidence_type` | `text` | No | — |  |
| `source_url` | `text` | Sí | — |  |
| `excerpt` | `text` | No | — |  |
| `page_number` | `integer` | Sí | — |  |
| `observed_at` | `timestamp with time zone` | No | — |  |
| `content_hash` | `text` | No | — |  |

Restricciones:

- `impact_evidences_content_hash_check`: `CHECK ((content_hash ~ '^[a-f0-9]{64}$'::text))`. Validada: True.
- `impact_evidences_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `impact_evidences_organization_id_impact_analysis_id_fkey`: `FOREIGN KEY (organization_id, impact_analysis_id) REFERENCES impact_analyses(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_evidences_page_number_check`: `CHECK ((page_number > 0))`. Validada: True.
- `impact_evidences_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: `change_record_evidences` (organization_id, evidence_id), `impact_finding_evidences` (organization_id, evidence_id), `impact_recommendation_evidences` (organization_id, evidence_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX impact_evidences_organization_id_id_key ON public.impact_evidences USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX impact_evidences_pkey ON public.impact_evidences USING btree (id)`
- `CREATE INDEX ix_fk_impact_evidences_6e4bd890d4a1 ON public.impact_evidences USING btree (organization_id, impact_analysis_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## impact_finding_component_versions

Versiones relacionadas con hallazgos.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `finding_id` | `uuid` | No | — | FK, PK |
| `component_version_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `impact_finding_component_vers_organization_id_component_ve_fkey`: `FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_finding_component_versio_organization_id_finding_id_fkey`: `FOREIGN KEY (organization_id, finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_finding_component_versions_pkey`: `PRIMARY KEY (organization_id, finding_id, component_version_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX impact_finding_component_versions_pkey ON public.impact_finding_component_versions USING btree (organization_id, finding_id, component_version_id)`
- `CREATE INDEX ix_fk_impact_finding_component_versions_72eda88c78fb ON public.impact_finding_component_versions USING btree (organization_id, component_version_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## impact_finding_documents

Documentos relacionados con hallazgos.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `finding_id` | `uuid` | No | — | FK, PK |
| `document_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `impact_finding_documents_organization_id_document_id_fkey`: `FOREIGN KEY (organization_id, document_id) REFERENCES documents(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_finding_documents_organization_id_finding_id_fkey`: `FOREIGN KEY (organization_id, finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_finding_documents_pkey`: `PRIMARY KEY (organization_id, finding_id, document_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX impact_finding_documents_pkey ON public.impact_finding_documents USING btree (organization_id, finding_id, document_id)`
- `CREATE INDEX ix_fk_impact_finding_documents_b086d8daa498 ON public.impact_finding_documents USING btree (organization_id, document_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## impact_finding_evidences

Asociación hallazgo/evidencia del mismo análisis.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `finding_id` | `uuid` | No | — | FK, PK |
| `evidence_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `impact_finding_evidences_organization_id_evidence_id_fkey`: `FOREIGN KEY (organization_id, evidence_id) REFERENCES impact_evidences(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_finding_evidences_organization_id_finding_id_fkey`: `FOREIGN KEY (organization_id, finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_finding_evidences_pkey`: `PRIMARY KEY (organization_id, finding_id, evidence_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX impact_finding_evidences_pkey ON public.impact_finding_evidences USING btree (organization_id, finding_id, evidence_id)`
- `CREATE INDEX ix_fk_impact_finding_evidences_3fee878c8c89 ON public.impact_finding_evidences USING btree (organization_id, evidence_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.
- Trigger: `CREATE CONSTRAINT TRIGGER ck_finding_evidence_analysis AFTER INSERT OR UPDATE ON public.impact_finding_evidences DEFERRABLE INITIALLY IMMEDIATE FOR EACH ROW EXECUTE FUNCTION app_private.validate_impact_evidence_link()`.

## impact_finding_repositories

Repositorios relacionados con hallazgos, con FK real.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `finding_id` | `uuid` | No | — | FK, PK |
| `repository_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `impact_finding_repositories_organization_id_finding_id_fkey`: `FOREIGN KEY (organization_id, finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_finding_repositories_organization_id_repository_id_fkey`: `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_finding_repositories_pkey`: `PRIMARY KEY (organization_id, finding_id, repository_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX impact_finding_repositories_pkey ON public.impact_finding_repositories USING btree (organization_id, finding_id, repository_id)`
- `CREATE INDEX ix_fk_impact_finding_repositories_9576a99eb12e ON public.impact_finding_repositories USING btree (organization_id, repository_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## impact_finding_vulnerabilities

Advisories públicos relacionados con hallazgos tenant-scoped.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `finding_id` | `uuid` | No | — | FK, PK |
| `vulnerability_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `impact_finding_vulnerabilities_organization_id_finding_id_fkey`: `FOREIGN KEY (organization_id, finding_id) REFERENCES impact_findings(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_finding_vulnerabilities_pkey`: `PRIMARY KEY (organization_id, finding_id, vulnerability_id)`. Validada: True.
- `impact_finding_vulnerabilities_vulnerability_id_fkey`: `FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities(id) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX impact_finding_vulnerabilities_pkey ON public.impact_finding_vulnerabilities USING btree (organization_id, finding_id, vulnerability_id)`
- `CREATE INDEX ix_fk_impact_finding_vulnerabilities_f2bea8abb50c ON public.impact_finding_vulnerabilities USING btree (vulnerability_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## impact_findings

Clasificación KNOWN/POTENTIAL/VALIDATED; VALIDATED exige prueba terminada del mismo análisis.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `impact_analysis_id` | `uuid` | No | — | FK |
| `classification` | `text` | No | — |  |
| `summary` | `text` | No | — |  |
| `severity_code` | `text` | Sí | — | FK |
| `review_status` | `text` | No | 'PENDING'::text |  |
| `validation_test_run_id` | `uuid` | Sí | — | FK |

Restricciones:

- `impact_findings_check`: `CHECK (((classification = 'VALIDATED'::text) = (validation_test_run_id IS NOT NULL)))`. Validada: True.
- `impact_findings_classification_check`: `CHECK ((classification = ANY (ARRAY['KNOWN'::text, 'POTENTIAL'::text, 'VALIDATED'::text])))`. Validada: True.
- `impact_findings_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `impact_findings_organization_id_impact_analysis_id_fkey`: `FOREIGN KEY (organization_id, impact_analysis_id) REFERENCES impact_analyses(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_findings_organization_id_validation_test_run_id_fkey`: `FOREIGN KEY (organization_id, validation_test_run_id) REFERENCES simulation_test_runs(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_findings_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `impact_findings_review_status_check`: `CHECK ((review_status = ANY (ARRAY['PENDING'::text, 'ACCEPTED'::text, 'REJECTED'::text])))`. Validada: True.
- `impact_findings_severity_code_fkey`: `FOREIGN KEY (severity_code) REFERENCES severity_levels(code) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: `document_review_flags` (organization_id, impact_finding_id), `impact_finding_component_versions` (organization_id, finding_id), `impact_finding_documents` (organization_id, finding_id), `impact_finding_evidences` (organization_id, finding_id), `impact_finding_repositories` (organization_id, finding_id), `impact_finding_vulnerabilities` (organization_id, finding_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX impact_findings_organization_id_id_key ON public.impact_findings USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX impact_findings_pkey ON public.impact_findings USING btree (id)`
- `CREATE INDEX ix_fk_impact_findings_55b2da7130b4 ON public.impact_findings USING btree (organization_id, validation_test_run_id)`
- `CREATE INDEX ix_fk_impact_findings_8f5b9c41f58e ON public.impact_findings USING btree (severity_code)`
- `CREATE INDEX ix_fk_impact_findings_d345fa60ddb7 ON public.impact_findings USING btree (organization_id, impact_analysis_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.
- Trigger: `CREATE CONSTRAINT TRIGGER ck_impact_validation AFTER INSERT OR UPDATE ON public.impact_findings DEFERRABLE INITIALLY IMMEDIATE FOR EACH ROW EXECUTE FUNCTION app_private.validate_impact_validation()`.

## impact_recommendation_evidences

Relación M:N recomendación/evidencia del mismo análisis.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `recommendation_id` | `uuid` | No | — | FK, PK |
| `evidence_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `impact_recommendation_evidenc_organization_id_recommendati_fkey`: `FOREIGN KEY (organization_id, recommendation_id) REFERENCES impact_recommendations(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_recommendation_evidence_organization_id_evidence_id_fkey`: `FOREIGN KEY (organization_id, evidence_id) REFERENCES impact_evidences(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_recommendation_evidences_pkey`: `PRIMARY KEY (organization_id, recommendation_id, evidence_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX impact_recommendation_evidences_pkey ON public.impact_recommendation_evidences USING btree (organization_id, recommendation_id, evidence_id)`
- `CREATE INDEX ix_fk_impact_recommendation_evidences_8910d601cba5 ON public.impact_recommendation_evidences USING btree (organization_id, evidence_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.
- Trigger: `CREATE CONSTRAINT TRIGGER ck_recommendation_evidence_analysis AFTER INSERT OR UPDATE ON public.impact_recommendation_evidences DEFERRABLE INITIALLY IMMEDIATE FOR EACH ROW EXECUTE FUNCTION app_private.validate_impact_evidence_link()`.

## impact_recommendations

Acción, prioridad y fundamento del análisis.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `impact_analysis_id` | `uuid` | No | — | FK |
| `action` | `text` | No | — |  |
| `priority` | `integer` | No | — |  |
| `explanation` | `text` | No | — |  |

Restricciones:

- `impact_recommendations_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `impact_recommendations_organization_id_impact_analysis_id_fkey`: `FOREIGN KEY (organization_id, impact_analysis_id) REFERENCES impact_analyses(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_recommendations_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `impact_recommendations_priority_check`: `CHECK ((priority > 0))`. Validada: True.

Referencias entrantes: `impact_recommendation_evidences` (organization_id, recommendation_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX impact_recommendations_organization_id_id_key ON public.impact_recommendations USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX impact_recommendations_pkey ON public.impact_recommendations USING btree (id)`
- `CREATE INDEX ix_fk_impact_recommendations_0a283fef5d32 ON public.impact_recommendations USING btree (organization_id, impact_analysis_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## impact_test_suggestions

Pruebas sugeridas; no implica que hayan sido ejecutadas.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK |
| `impact_analysis_id` | `uuid` | No | — | FK |
| `category` | `text` | No | — |  |
| `priority` | `integer` | No | — |  |
| `description` | `text` | No | — |  |

Restricciones:

- `impact_test_suggestions_organization_id_impact_analysis_id_fkey`: `FOREIGN KEY (organization_id, impact_analysis_id) REFERENCES impact_analyses(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `impact_test_suggestions_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `impact_test_suggestions_priority_check`: `CHECK ((priority > 0))`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX impact_test_suggestions_pkey ON public.impact_test_suggestions USING btree (id)`
- `CREATE INDEX ix_fk_impact_test_suggestions_4e1d4dd1962d ON public.impact_test_suggestions USING btree (organization_id, impact_analysis_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## integration_providers

Códigos de proveedor; un catálogo no implementa la integración.

Módulo: **integration**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `code` | `text` | No | — | PK |
| `name` | `text` | No | — |  |

Restricciones:

- `integration_providers_code_check`: `CHECK ((btrim(code) <> ''::text))`. Validada: True.
- `integration_providers_pkey`: `PRIMARY KEY (code)`. Validada: True.

Referencias entrantes: `integrations` (provider), `repositories` (provider), `webhook_events` (provider).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX integration_providers_pkey ON public.integration_providers USING btree (code)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## integration_sync_runs

Historial y estado de sincronización.

Módulo: **integration**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK |
| `integration_id` | `uuid` | No | — | FK |
| `started_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `finished_at` | `timestamp with time zone` | Sí | — |  |
| `status` | `text` | No | — |  |
| `error_code` | `text` | Sí | — |  |

Restricciones:

- `integration_sync_runs_check`: `CHECK (((finished_at IS NULL) OR (finished_at >= started_at)))`. Validada: True.
- `integration_sync_runs_organization_id_integration_id_fkey`: `FOREIGN KEY (organization_id, integration_id) REFERENCES integrations(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `integration_sync_runs_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `integration_sync_runs_status_check`: `CHECK ((status = ANY (ARRAY['RUNNING'::text, 'COMPLETED'::text, 'FAILED'::text])))`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX integration_sync_runs_pkey ON public.integration_sync_runs USING btree (id)`
- `CREATE INDEX ix_fk_integration_sync_runs_1eaa3dd5a8c1 ON public.integration_sync_runs USING btree (organization_id, integration_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## integrations

Conexión de una organización a una cuenta externa, sin secretos en claro.

Módulo: **integration**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | — | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `provider` | `character varying(40)` | No | — | FK, U |
| `external_id` | `character varying(255)` | No | — | U |
| `status` | `character varying(20)` | No | — |  |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `external_account_login` | `text` | Sí | — |  |
| `credential_reference` | `text` | Sí | — |  |
| `disconnected_at` | `timestamp with time zone` | Sí | — |  |

Restricciones:

- `fk_integration_provider`: `FOREIGN KEY (provider) REFERENCES integration_providers(code) ON DELETE RESTRICT`. Validada: True.
- `integrations_external_id_check`: `CHECK ((btrim((external_id)::text) <> ''::text))`. Validada: True.
- `integrations_organization_id_fkey`: `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`. Validada: True.
- `integrations_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `integrations_organization_id_provider_external_id_key`: `UNIQUE (organization_id, provider, external_id)`. Validada: True.
- `integrations_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `integrations_provider_check`: `CHECK (((btrim((provider)::text) <> ''::text) AND ((provider)::text = lower((provider)::text))))`. Validada: True.
- `integrations_status_check`: `CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying, 'REVOKED'::character varying])::text[])))`. Validada: True.
- `uq_integration_provider_identity`: `UNIQUE (organization_id, id, provider)`. Validada: True.

Referencias entrantes: `data_deletion_requests` (organization_id, integration_id), `github_installations` (organization_id, integration_id, provider), `github_installations` (organization_id, integration_id), `integration_sync_runs` (organization_id, integration_id), `repositories` (organization_id, integration_id, provider), `repositories` (organization_id, integration_id), `webhook_events` (organization_id, integration_id, provider), `webhook_events` (organization_id, integration_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX integrations_organization_id_id_key ON public.integrations USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX integrations_organization_id_provider_external_id_key ON public.integrations USING btree (organization_id, provider, external_id)`
- `CREATE UNIQUE INDEX integrations_pkey ON public.integrations USING btree (id)`
- `CREATE INDEX ix_fk_integrations_2658eaf3ca9b ON public.integrations USING btree (provider)`
- `CREATE UNIQUE INDEX uq_integration_provider_identity ON public.integrations USING btree (organization_id, id, provider)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## invitation_roles

Roles propuestos para cada invitación.

Módulo: **identity**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `invitation_id` | `uuid` | No | — | FK, PK |
| `role_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `invitation_roles_organization_id_invitation_id_fkey`: `FOREIGN KEY (organization_id, invitation_id) REFERENCES membership_invitations(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `invitation_roles_pkey`: `PRIMARY KEY (organization_id, invitation_id, role_id)`. Validada: True.
- `invitation_roles_role_id_fkey`: `FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX invitation_roles_pkey ON public.invitation_roles USING btree (organization_id, invitation_id, role_id)`
- `CREATE INDEX ix_fk_invitation_roles_24263cc89f54 ON public.invitation_roles USING btree (role_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `platform_administrator_read`, roles public: USING `app_private.is_platform_administrator()`; CHECK `None`.
- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## job_statuses

Estados de procesamiento; no ejecuta workers por sí solo.

Módulo: **scan**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `code` | `text` | No | — | PK |

Restricciones:

- `job_statuses_pkey`: `PRIMARY KEY (code)`. Validada: True.

Referencias entrantes: `background_job_attempts` (status), `background_jobs` (status), `change_simulations` (status), `data_deletion_requests` (status), `document_processing_runs` (status), `document_versions` (indexing_status), `impact_analyses` (status), `report_jobs` (status), `scan_comparisons` (status), `scan_stage_executions` (status), `simulation_test_runs` (status).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX job_statuses_pkey ON public.job_statuses USING btree (code)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## job_types

Tipos de trabajos tenant-scoped soportados por el modelo.

Módulo: **scan**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `code` | `text` | No | — | PK |
| `description` | `text` | No | — |  |

Restricciones:

- `job_types_pkey`: `PRIMARY KEY (code)`. Validada: True.

Referencias entrantes: `background_jobs` (job_type).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX job_types_pkey ON public.job_types USING btree (code)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## license_catalog

Licencias referenciables por código, sin sembrar detecciones ficticias.

Módulo: **component**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `code` | `text` | No | — | U |
| `name` | `text` | No | — |  |
| `url` | `text` | Sí | — |  |

Restricciones:

- `license_catalog_code_key`: `UNIQUE (code)`. Validada: True.
- `license_catalog_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: `component_licenses` (license_id), `scan_component_licenses` (license_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX license_catalog_code_key ON public.license_catalog USING btree (code)`
- `CREATE UNIQUE INDEX license_catalog_pkey ON public.license_catalog USING btree (id)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## member_repository_assignments

Permisos directos de una membresía en un repositorio, delimitados por organización.

Módulo: **identity**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `membership_id` | `uuid` | No | — | FK, PK |
| `team_id` | `uuid` | No | — | FK, PK |
| `repository_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `member_repository_assignments_organization_id_membership_i_fkey`: `FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `member_repository_assignments_organization_id_repository_i_fkey`: `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `member_repository_assignments_organization_id_team_id_fkey`: `FOREIGN KEY (organization_id, team_id) REFERENCES organization_teams(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `member_repository_assignments_pkey`: `PRIMARY KEY (organization_id, membership_id, team_id, repository_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX member_repository_assignments_pkey ON public.member_repository_assignments USING btree (organization_id, membership_id, team_id, repository_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `platform_administrator_read`, roles public: USING `app_private.is_platform_administrator()`; CHECK `None`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## member_repository_permissions

Direct permission grants for a membership and repository, intersected with its effective role.

Módulo: **identity**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `membership_id` | `uuid` | No | — | FK, PK |
| `repository_id` | `uuid` | No | — | FK, PK |
| `permission_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `member_repository_permissions_organization_id_membership_i_fkey`: `FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `member_repository_permissions_organization_id_repository_i_fkey`: `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `member_repository_permissions_permission_id_fkey`: `FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE RESTRICT`. Validada: True.
- `member_repository_permissions_pkey`: `PRIMARY KEY (organization_id, membership_id, repository_id, permission_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX member_repository_permissions_lookup_idx ON public.member_repository_permissions USING btree (organization_id, membership_id, repository_id)`
- `CREATE UNIQUE INDEX member_repository_permissions_pkey ON public.member_repository_permissions USING btree (organization_id, membership_id, repository_id, permission_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `platform_administrator_read`, roles public: USING `app_private.is_platform_administrator()`; CHECK `None`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## membership_invitations

Invitación con token hash, estado y expiración.

Módulo: **identity**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `email_normalized` | `text` | No | — |  |
| `token_hash` | `text` | No | — | U |
| `invited_by_membership_id` | `uuid` | No | — | FK |
| `accepted_by_user_id` | `uuid` | Sí | — | FK |
| `status` | `text` | No | 'PENDING'::text |  |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `expires_at` | `timestamp with time zone` | No | — |  |
| `target_team_id` | `uuid` | Sí | — | FK |
| `repository_id` | `uuid` | Sí | — | FK |
| `accepted_at` | `timestamp with time zone` | Sí | — |  |
| `approved_at` | `timestamp with time zone` | Sí | — |  |
| `approved_by_user_id` | `uuid` | Sí | — | FK |

Restricciones:

- `fk_invitation_repository`: `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `fk_invitation_target_team`: `FOREIGN KEY (organization_id, target_team_id) REFERENCES organization_teams(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `membership_invitations_accepted_by_user_id_fkey`: `FOREIGN KEY (accepted_by_user_id) REFERENCES users(id) ON DELETE RESTRICT`. Validada: True.
- `membership_invitations_approved_by_user_id_fkey`: `FOREIGN KEY (approved_by_user_id) REFERENCES users(id) ON DELETE RESTRICT`. Validada: True.
- `membership_invitations_check`: `CHECK ((expires_at > created_at))`. Validada: True.
- `membership_invitations_email_normalized_check`: `CHECK (((email_normalized = lower(btrim(email_normalized))) AND (email_normalized <> ''::text)))`. Validada: True.
- `membership_invitations_organization_id_fkey`: `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`. Validada: True.
- `membership_invitations_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `membership_invitations_organization_id_invited_by_membersh_fkey`: `FOREIGN KEY (organization_id, invited_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `membership_invitations_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `membership_invitations_status_check`: `CHECK ((status = ANY (ARRAY['PENDING'::text, 'AWAITING_OWNER'::text, 'ACCEPTED'::text, 'EXPIRED'::text, 'REVOKED'::text])))`. Validada: True.
- `membership_invitations_token_hash_check`: `CHECK ((token_hash ~ '^[a-f0-9]{64}$'::text))`. Validada: True.
- `membership_invitations_token_hash_key`: `UNIQUE (token_hash)`. Validada: True.

Referencias entrantes: `invitation_roles` (organization_id, invitation_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_membership_invitations_27dea051f2b9 ON public.membership_invitations USING btree (organization_id, invited_by_membership_id)`
- `CREATE INDEX ix_fk_membership_invitations_91f2b95bca59 ON public.membership_invitations USING btree (accepted_by_user_id)`
- `CREATE UNIQUE INDEX membership_invitations_organization_id_id_key ON public.membership_invitations USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX membership_invitations_pkey ON public.membership_invitations USING btree (id)`
- `CREATE UNIQUE INDEX membership_invitations_token_hash_key ON public.membership_invitations USING btree (token_hash)`
- `CREATE UNIQUE INDEX uq_pending_invitation ON public.membership_invitations USING btree (organization_id, email_normalized) WHERE (status = ANY (ARRAY['PENDING'::text, 'AWAITING_OWNER'::text]))`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `platform_administrator_read`, roles public: USING `app_private.is_platform_administrator()`; CHECK `None`.
- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## membership_roles

Varios roles por membresía sin arrays ni CSV.

Módulo: **identity**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `membership_id` | `uuid` | No | — | FK, PK |
| `role_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `membership_roles_organization_id_membership_id_fkey`: `FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `membership_roles_pkey`: `PRIMARY KEY (organization_id, membership_id, role_id)`. Validada: True.
- `membership_roles_role_id_fkey`: `FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_membership_roles_bde84f90ac86 ON public.membership_roles USING btree (role_id)`
- `CREATE UNIQUE INDEX membership_roles_pkey ON public.membership_roles USING btree (organization_id, membership_id, role_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `platform_administrator_read`, roles public: USING `app_private.is_platform_administrator()`; CHECK `None`.
- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## notification_channels

Canales conocidos; solo in-app está priorizado inicialmente.

Módulo: **notifications**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `code` | `text` | No | — | PK |

Restricciones:

- `notification_channels_pkey`: `PRIMARY KEY (code)`. Validada: True.

Referencias entrantes: `notification_deliveries` (channel), `notification_subscriptions` (channel).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX notification_channels_pkey ON public.notification_channels USING btree (code)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## notification_deliveries

Entrega y lectura por membresía/canal, separada del evento.

Módulo: **notifications**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `notification_id` | `uuid` | No | — | FK, U |
| `membership_id` | `uuid` | No | — | FK, U |
| `channel` | `text` | No | — | FK, U |
| `status` | `text` | No | 'PENDING'::text |  |
| `delivered_at` | `timestamp with time zone` | Sí | — |  |
| `read_at` | `timestamp with time zone` | Sí | — |  |

Restricciones:

- `notification_deliveries_channel_fkey`: `FOREIGN KEY (channel) REFERENCES notification_channels(code) ON DELETE RESTRICT`. Validada: True.
- `notification_deliveries_check`: `CHECK (((read_at IS NULL) OR ((channel = 'IN_APP'::text) AND (delivered_at IS NOT NULL) AND (read_at >= delivered_at))))`. Validada: True.
- `notification_deliveries_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `notification_deliveries_organization_id_membership_id_fkey`: `FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `notification_deliveries_organization_id_notification_id_fkey`: `FOREIGN KEY (organization_id, notification_id) REFERENCES notifications(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `notification_deliveries_organization_id_notification_id_mem_key`: `UNIQUE (organization_id, notification_id, membership_id, channel)`. Validada: True.
- `notification_deliveries_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `notification_deliveries_status_check`: `CHECK ((status = ANY (ARRAY['PENDING'::text, 'DELIVERED'::text, 'FAILED'::text, 'READ'::text])))`. Validada: True.

Referencias entrantes: `notification_delivery_attempts` (organization_id, delivery_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_notification_deliveries_337ce841db05 ON public.notification_deliveries USING btree (channel)`
- `CREATE INDEX ix_fk_notification_deliveries_c0ad177ad6ac ON public.notification_deliveries USING btree (organization_id, membership_id)`
- `CREATE UNIQUE INDEX notification_deliveries_organization_id_id_key ON public.notification_deliveries USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX notification_deliveries_organization_id_notification_id_mem_key ON public.notification_deliveries USING btree (organization_id, notification_id, membership_id, channel)`
- `CREATE UNIQUE INDEX notification_deliveries_pkey ON public.notification_deliveries USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## notification_delivery_attempts

Intentos de entrega, resultado y referencia de proveedor sin secretos.

Módulo: **notifications**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK, U |
| `delivery_id` | `uuid` | No | — | FK, U |
| `attempt_number` | `integer` | No | — | U |
| `attempted_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `status` | `text` | No | — |  |
| `error_code` | `text` | Sí | — |  |
| `provider_reference` | `text` | Sí | — |  |

Restricciones:

- `notification_delivery_attempt_organization_id_delivery_id_a_key`: `UNIQUE (organization_id, delivery_id, attempt_number)`. Validada: True.
- `notification_delivery_attempts_attempt_number_check`: `CHECK ((attempt_number > 0))`. Validada: True.
- `notification_delivery_attempts_organization_id_delivery_id_fkey`: `FOREIGN KEY (organization_id, delivery_id) REFERENCES notification_deliveries(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `notification_delivery_attempts_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `notification_delivery_attempts_status_check`: `CHECK ((status = ANY (ARRAY['DELIVERED'::text, 'FAILED'::text])))`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX notification_delivery_attempt_organization_id_delivery_id_a_key ON public.notification_delivery_attempts USING btree (organization_id, delivery_id, attempt_number)`
- `CREATE UNIQUE INDEX notification_delivery_attempts_pkey ON public.notification_delivery_attempts USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## notification_rules

Reglas de eventos por organización.

Módulo: **notifications**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `event_type` | `text` | No | — |  |
| `name` | `text` | No | — | U |
| `is_enabled` | `boolean` | No | true |  |

Restricciones:

- `notification_rules_event_type_check`: `CHECK ((event_type = ANY (ARRAY['NEW_VULNERABILITY'::text, 'SEVERITY_CHANGED'::text, 'COMPONENT_ADDED'::text, 'COMPONENT_REMOVED'::text, 'COMPONENT_UPDATED'::text, 'SCAN_FAILED'::text])))`. Validada: True.
- `notification_rules_organization_id_fkey`: `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`. Validada: True.
- `notification_rules_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `notification_rules_organization_id_name_key`: `UNIQUE (organization_id, name)`. Validada: True.
- `notification_rules_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: `notification_subscriptions` (organization_id, rule_id), `notifications` (organization_id, rule_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX notification_rules_organization_id_id_key ON public.notification_rules USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX notification_rules_organization_id_name_key ON public.notification_rules USING btree (organization_id, name)`
- `CREATE UNIQUE INDEX notification_rules_pkey ON public.notification_rules USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## notification_subscriptions

Suscripción independiente regla/membresía/canal.

Módulo: **notifications**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `rule_id` | `uuid` | No | — | FK, PK |
| `membership_id` | `uuid` | No | — | FK, PK |
| `channel` | `text` | No | — | FK, PK |

Restricciones:

- `notification_subscriptions_channel_fkey`: `FOREIGN KEY (channel) REFERENCES notification_channels(code) ON DELETE RESTRICT`. Validada: True.
- `notification_subscriptions_organization_id_membership_id_fkey`: `FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `notification_subscriptions_organization_id_rule_id_fkey`: `FOREIGN KEY (organization_id, rule_id) REFERENCES notification_rules(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `notification_subscriptions_pkey`: `PRIMARY KEY (organization_id, rule_id, membership_id, channel)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_notification_subscriptions_3899e0ed4b38 ON public.notification_subscriptions USING btree (organization_id, membership_id)`
- `CREATE INDEX ix_fk_notification_subscriptions_9a988c43cf3a ON public.notification_subscriptions USING btree (channel)`
- `CREATE UNIQUE INDEX notification_subscriptions_pkey ON public.notification_subscriptions USING btree (organization_id, rule_id, membership_id, channel)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## notifications

Evento lógico deduplicado con referencias tipadas a sus recursos.

Módulo: **notifications**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `rule_id` | `uuid` | Sí | — | FK |
| `event_type` | `text` | No | — | U |
| `deduplication_key` | `text` | No | — | U |
| `title` | `text` | No | — |  |
| `summary` | `text` | No | — |  |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `scan_id` | `uuid` | Sí | — | FK |
| `finding_id` | `uuid` | Sí | — | FK |
| `comparison_id` | `uuid` | Sí | — | FK |

Restricciones:

- `notifications_organization_id_comparison_id_fkey`: `FOREIGN KEY (organization_id, comparison_id) REFERENCES scan_comparisons(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `notifications_organization_id_event_type_deduplication_key_key`: `UNIQUE (organization_id, event_type, deduplication_key)`. Validada: True.
- `notifications_organization_id_finding_id_fkey`: `FOREIGN KEY (organization_id, finding_id) REFERENCES scan_vulnerability_findings(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `notifications_organization_id_fkey`: `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`. Validada: True.
- `notifications_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `notifications_organization_id_rule_id_fkey`: `FOREIGN KEY (organization_id, rule_id) REFERENCES notification_rules(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `notifications_organization_id_scan_id_fkey`: `FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `notifications_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: `notification_deliveries` (organization_id, notification_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_notifications_68e5d198033f ON public.notifications USING btree (organization_id, rule_id)`
- `CREATE INDEX ix_fk_notifications_cb6dcd8ef2d4 ON public.notifications USING btree (organization_id, finding_id)`
- `CREATE INDEX ix_fk_notifications_d7ba458f2714 ON public.notifications USING btree (organization_id, scan_id)`
- `CREATE INDEX ix_fk_notifications_f2954a865311 ON public.notifications USING btree (organization_id, comparison_id)`
- `CREATE UNIQUE INDEX notifications_organization_id_event_type_deduplication_key_key ON public.notifications USING btree (organization_id, event_type, deduplication_key)`
- `CREATE UNIQUE INDEX notifications_organization_id_id_key ON public.notifications USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX notifications_pkey ON public.notifications USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## organization_memberships

Participación de un usuario en una organización; una por par.

Módulo: **identity**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK, U |
| `user_id` | `uuid` | No | — | FK, PK |
| `joined_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `id` | `uuid` | No | gen_random_uuid() | U |
| `status` | `text` | No | 'ACTIVE'::text |  |
| `invited_by_user_id` | `uuid` | Sí | — | FK |
| `repository_scope` | `text` | No | 'TEAM'::text |  |

Restricciones:

- `memberships_organization_id_fkey`: `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`. Validada: True.
- `memberships_pkey`: `PRIMARY KEY (organization_id, user_id)`. Validada: True.
- `memberships_user_id_fkey`: `FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT`. Validada: True.
- `organization_memberships_invited_by_user_id_fkey`: `FOREIGN KEY (invited_by_user_id) REFERENCES users(id) ON DELETE RESTRICT`. Validada: True.
- `organization_memberships_repository_scope_check`: `CHECK ((repository_scope = ANY (ARRAY['TEAM'::text, 'ASSIGNED'::text])))`. Validada: True.
- `organization_memberships_status_check`: `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'LEFT'::text])))`. Validada: True.
- `uq_membership_id`: `UNIQUE (id)`. Validada: True.
- `uq_membership_tenant_id`: `UNIQUE (organization_id, id)`. Validada: True.

Referencias entrantes: `assistant_conversations` (organization_id, created_by_membership_id), `change_pull_request_proposals` (organization_id, approved_by_membership_id), `change_simulations` (organization_id, requested_by_membership_id), `data_deletion_requests` (organization_id, requested_by_membership_id), `documents` (organization_id, uploaded_by_membership_id), `impact_analyses` (organization_id, requested_by_membership_id), `member_repository_assignments` (organization_id, membership_id), `member_repository_permissions` (organization_id, membership_id), `membership_invitations` (organization_id, invited_by_membership_id), `membership_roles` (organization_id, membership_id), `notification_deliveries` (organization_id, membership_id), `notification_subscriptions` (organization_id, membership_id), `report_jobs` (organization_id, requested_by_membership_id), `report_schedule_recipients` (organization_id, membership_id), `scans` (organization_id, requested_by_membership_id), `team_memberships` (organization_id, membership_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_organization_memberships_920a6e994a0d ON public.organization_memberships USING btree (invited_by_user_id)`
- `CREATE INDEX ix_memberships_user ON public.organization_memberships USING btree (user_id)`
- `CREATE UNIQUE INDEX memberships_pkey ON public.organization_memberships USING btree (organization_id, user_id)`
- `CREATE UNIQUE INDEX uq_membership_id ON public.organization_memberships USING btree (id)`
- `CREATE UNIQUE INDEX uq_membership_tenant_id ON public.organization_memberships USING btree (organization_id, id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `membership_self_discovery`, roles dependency_runtime: USING `((app_private.current_organization_id() IS NULL) AND (user_id = app_private.current_user_id()) AND (status = 'ACTIVE'::text))`; CHECK `None`.
- Política `platform_administrator_read`, roles public: USING `app_private.is_platform_administrator()`; CHECK `None`.
- Política `platform_administrator_update`, roles public: USING `app_private.is_platform_administrator()`; CHECK `app_private.is_platform_administrator()`.
- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## organization_teams

Equipos de una organización.

Módulo: **identity**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `name` | `text` | No | — | U |
| `is_enabled` | `boolean` | No | true |  |

Restricciones:

- `organization_teams_name_check`: `CHECK ((btrim(name) <> ''::text))`. Validada: True.
- `organization_teams_organization_id_fkey`: `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`. Validada: True.
- `organization_teams_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `organization_teams_organization_id_name_key`: `UNIQUE (organization_id, name)`. Validada: True.
- `organization_teams_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: `member_repository_assignments` (organization_id, team_id), `membership_invitations` (organization_id, target_team_id), `repository_team_access` (organization_id, team_id), `team_memberships` (organization_id, team_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX organization_teams_organization_id_id_key ON public.organization_teams USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX organization_teams_organization_id_name_key ON public.organization_teams USING btree (organization_id, name)`
- `CREATE UNIQUE INDEX organization_teams_pkey ON public.organization_teams USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `platform_administrator_read`, roles public: USING `app_private.is_platform_administrator()`; CHECK `None`.
- Política `platform_administrator_update`, roles public: USING `app_private.is_platform_administrator()`; CHECK `app_private.is_platform_administrator()`.
- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## organizations

Organización propietaria y límite de tenant.

Módulo: **identity**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | — | PK |
| `name` | `character varying(200)` | No | — |  |
| `slug` | `character varying(100)` | No | — | U |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `legal_name` | `text` | Sí | — |  |
| `status` | `text` | No | 'ACTIVE'::text |  |
| `updated_at` | `timestamp with time zone` | Sí | — |  |

Restricciones:

- `organizations_name_check`: `CHECK ((btrim((name)::text) <> ''::text))`. Validada: True.
- `organizations_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `organizations_slug_check`: `CHECK (((slug)::text ~ '^[a-z0-9]+(-[a-z0-9]+)*$'::text))`. Validada: True.
- `organizations_slug_key`: `UNIQUE (slug)`. Validada: True.
- `organizations_status_check`: `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'CLOSED'::text])))`. Validada: True.

Referencias entrantes: `audit_events` (organization_id), `background_jobs` (organization_id), `components` (organization_id), `data_retention_policies` (organization_id), `documents` (organization_id), `integrations` (organization_id), `membership_invitations` (organization_id), `notification_rules` (organization_id), `notifications` (organization_id), `organization_memberships` (organization_id), `organization_teams` (organization_id), `report_schedules` (organization_id), `repositories` (organization_id), `scan_profiles` (organization_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX organizations_pkey ON public.organizations USING btree (id)`
- `CREATE UNIQUE INDEX organizations_slug_key ON public.organizations USING btree (slug)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `organization_self_discovery`, roles dependency_runtime: USING `((app_private.current_organization_id() IS NULL) AND (status = 'ACTIVE'::text) AND (EXISTS ( SELECT 1
   FROM (organization_memberships membership
     JOIN users account ON (((account.id = membership.user_id) AND account.is_active)))
  WHERE ((membership.organization_id = organizations.id) AND (membership.user_id = app_private.current_user_id()) AND (membership.status = 'ACTIVE'::text)))))`; CHECK `None`.
- Política `platform_administrator_read`, roles public: USING `app_private.is_platform_administrator()`; CHECK `None`.
- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(id = app_private.current_organization_id())`; CHECK `(id = app_private.current_organization_id())`.

## package_ecosystems

Ecosistemas conocidos; solo Maven/npm están confirmados como cobertura objetivo.

Módulo: **component**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `code` | `text` | No | — | U |
| `name` | `text` | No | — |  |

Restricciones:

- `package_ecosystems_code_check`: `CHECK ((btrim(code) <> ''::text))`. Validada: True.
- `package_ecosystems_code_key`: `UNIQUE (code)`. Validada: True.
- `package_ecosystems_name_check`: `CHECK ((btrim(name) <> ''::text))`. Validada: True.
- `package_ecosystems_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: `components` (package_ecosystem_id), `scan_profile_ecosystems` (package_ecosystem_id), `vulnerability_affected_packages` (package_ecosystem_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX package_ecosystems_code_key ON public.package_ecosystems USING btree (code)`
- `CREATE UNIQUE INDEX package_ecosystems_pkey ON public.package_ecosystems USING btree (id)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## permissions

Permisos atómicos de aplicación.

Módulo: **identity**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `code` | `text` | No | — | U |
| `description` | `text` | No | — |  |

Restricciones:

- `permissions_code_key`: `UNIQUE (code)`. Validada: True.
- `permissions_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: `member_repository_permissions` (permission_id), `repository_team_access` (permission_id), `role_permissions` (permission_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX permissions_code_key ON public.permissions USING btree (code)`
- `CREATE UNIQUE INDEX permissions_pkey ON public.permissions USING btree (id)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## platform_administrators

Asignación explícita de cuentas con privilegios administrativos globales de plataforma.

Módulo: **identity**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `user_id` | `uuid` | No | — | FK, PK |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |

Restricciones:

- `platform_administrators_pkey`: `PRIMARY KEY (user_id)`. Validada: True.
- `platform_administrators_user_id_fkey`: `FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX platform_administrators_pkey ON public.platform_administrators USING btree (user_id)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## platform_audit_events

Registro append-only de acciones administrativas globales con actor y objetivo.

Módulo: **audit**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `actor_id` | `uuid` | No | — | FK |
| `action` | `text` | No | — |  |
| `resource_type` | `text` | No | — |  |
| `resource_id` | `uuid` | No | — |  |
| `reason` | `text` | No | — |  |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |

Restricciones:

- `platform_audit_events_actor_id_fkey`: `FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT`. Validada: True.
- `platform_audit_events_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `platform_audit_events_reason_check`: `CHECK (((length(btrim(reason)) >= 3) AND (length(btrim(reason)) <= 500)))`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX platform_audit_events_pkey ON public.platform_audit_events USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `administrator_access`, roles public: USING `app_private.is_platform_administrator()`; CHECK `(app_private.is_platform_administrator() AND (actor_id = app_private.current_user_id()))`.
- Trigger: `CREATE TRIGGER platform_audit_append_only BEFORE DELETE OR UPDATE ON public.platform_audit_events FOR EACH ROW EXECUTE FUNCTION app_private.reject_audit_mutation()`.

## report_artifacts

Archivo generado privado, hash, tamaño, MIME y expiración.

Módulo: **reporting**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `report_job_id` | `uuid` | No | — | FK |
| `storage_key` | `text` | No | — |  |
| `sha256` | `text` | No | — |  |
| `size_bytes` | `bigint` | No | — |  |
| `media_type` | `text` | No | — |  |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `expires_at` | `timestamp with time zone` | Sí | — |  |

Restricciones:

- `report_artifacts_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `report_artifacts_organization_id_report_job_id_fkey`: `FOREIGN KEY (organization_id, report_job_id) REFERENCES report_jobs(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `report_artifacts_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `report_artifacts_sha256_check`: `CHECK ((sha256 ~ '^[a-f0-9]{64}$'::text))`. Validada: True.
- `report_artifacts_size_bytes_check`: `CHECK ((size_bytes > 0))`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_report_artifacts_6c307a905bdd ON public.report_artifacts USING btree (organization_id, report_job_id)`
- `CREATE UNIQUE INDEX report_artifacts_organization_id_id_key ON public.report_artifacts USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX report_artifacts_pkey ON public.report_artifacts USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## report_definitions

Definiciones de informes.

Módulo: **reporting**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `code` | `text` | No | — | U |
| `report_type` | `text` | No | — | FK |
| `name` | `text` | No | — |  |

Restricciones:

- `report_definitions_code_key`: `UNIQUE (code)`. Validada: True.
- `report_definitions_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `report_definitions_report_type_fkey`: `FOREIGN KEY (report_type) REFERENCES report_types(code) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: `report_jobs` (definition_id), `report_schedules` (definition_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_report_definitions_7c3003583080 ON public.report_definitions USING btree (report_type)`
- `CREATE UNIQUE INDEX report_definitions_code_key ON public.report_definitions USING btree (code)`
- `CREATE UNIQUE INDEX report_definitions_pkey ON public.report_definitions USING btree (id)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## report_formats

Formatos de exportación; catálogo no significa exportador implementado.

Módulo: **reporting**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `code` | `text` | No | — | PK |

Restricciones:

- `report_formats_pkey`: `PRIMARY KEY (code)`. Validada: True.

Referencias entrantes: `report_jobs` (format), `report_schedules` (format).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX report_formats_pkey ON public.report_formats USING btree (code)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## report_jobs

Solicitud de generación, formato, scope separado y parámetros técnicos.

Módulo: **reporting**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `definition_id` | `uuid` | No | — | FK |
| `format` | `text` | No | — | FK |
| `requested_by_membership_id` | `uuid` | Sí | — | FK |
| `background_job_id` | `uuid` | Sí | — | FK |
| `status` | `text` | No | 'PENDING'::text | FK |
| `execution_parameters` | `jsonb` | No | '{}'::jsonb |  |
| `requested_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |

Restricciones:

- `report_jobs_definition_id_fkey`: `FOREIGN KEY (definition_id) REFERENCES report_definitions(id) ON DELETE RESTRICT`. Validada: True.
- `report_jobs_execution_parameters_check`: `CHECK ((jsonb_typeof(execution_parameters) = 'object'::text))`. Validada: True.
- `report_jobs_format_fkey`: `FOREIGN KEY (format) REFERENCES report_formats(code) ON DELETE RESTRICT`. Validada: True.
- `report_jobs_organization_id_background_job_id_fkey`: `FOREIGN KEY (organization_id, background_job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `report_jobs_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `report_jobs_organization_id_requested_by_membership_id_fkey`: `FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `report_jobs_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `report_jobs_status_fkey`: `FOREIGN KEY (status) REFERENCES job_statuses(code) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: `data_deletion_requests` (organization_id, report_job_id), `report_artifacts` (organization_id, report_job_id), `report_repositories` (organization_id, report_job_id), `report_scans` (organization_id, report_job_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_report_jobs_4a5157a1415a ON public.report_jobs USING btree (format)`
- `CREATE INDEX ix_fk_report_jobs_5650daac5dc6 ON public.report_jobs USING btree (organization_id, requested_by_membership_id)`
- `CREATE INDEX ix_fk_report_jobs_583adc29addf ON public.report_jobs USING btree (organization_id, background_job_id)`
- `CREATE INDEX ix_fk_report_jobs_993c24d13c62 ON public.report_jobs USING btree (status)`
- `CREATE INDEX ix_fk_report_jobs_ad5f133225aa ON public.report_jobs USING btree (definition_id)`
- `CREATE UNIQUE INDEX report_jobs_organization_id_id_key ON public.report_jobs USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX report_jobs_pkey ON public.report_jobs USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## report_repositories

Repositorios seleccionados para un informe sin arrays.

Módulo: **reporting**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `report_job_id` | `uuid` | No | — | FK, PK |
| `repository_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `report_repositories_organization_id_report_job_id_fkey`: `FOREIGN KEY (organization_id, report_job_id) REFERENCES report_jobs(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `report_repositories_organization_id_repository_id_fkey`: `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `report_repositories_pkey`: `PRIMARY KEY (organization_id, report_job_id, repository_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_report_repositories_a44e6467942b ON public.report_repositories USING btree (organization_id, repository_id)`
- `CREATE UNIQUE INDEX report_repositories_pkey ON public.report_repositories USING btree (organization_id, report_job_id, repository_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## report_scans

Snapshots seleccionados para un informe sin arrays.

Módulo: **reporting**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `report_job_id` | `uuid` | No | — | FK, PK |
| `scan_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `report_scans_organization_id_report_job_id_fkey`: `FOREIGN KEY (organization_id, report_job_id) REFERENCES report_jobs(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `report_scans_organization_id_scan_id_fkey`: `FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `report_scans_pkey`: `PRIMARY KEY (organization_id, report_job_id, scan_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_report_scans_48e43036bdc3 ON public.report_scans USING btree (organization_id, scan_id)`
- `CREATE UNIQUE INDEX report_scans_pkey ON public.report_scans USING btree (organization_id, report_job_id, scan_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## report_schedule_recipients

Un destinatario por fila: membresía o correo externo, no ambos.

Módulo: **reporting**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK, U |
| `schedule_id` | `uuid` | No | — | FK, U |
| `membership_id` | `uuid` | Sí | — | FK, U |
| `external_email` | `text` | Sí | — | U |

Restricciones:

- `report_schedule_recipients_check`: `CHECK ((num_nonnulls(membership_id, external_email) = 1))`. Validada: True.
- `report_schedule_recipients_organization_id_membership_id_fkey`: `FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `report_schedule_recipients_organization_id_schedule_id_fkey`: `FOREIGN KEY (organization_id, schedule_id) REFERENCES report_schedules(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `report_schedule_recipients_organization_id_schedule_id_memb_key`: `UNIQUE NULLS NOT DISTINCT (organization_id, schedule_id, membership_id, external_email)`. Validada: True.
- `report_schedule_recipients_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_report_schedule_recipients_8bbb3295a71d ON public.report_schedule_recipients USING btree (organization_id, membership_id)`
- `CREATE UNIQUE INDEX report_schedule_recipients_organization_id_schedule_id_memb_key ON public.report_schedule_recipients USING btree (organization_id, schedule_id, membership_id, external_email) NULLS NOT DISTINCT`
- `CREATE UNIQUE INDEX report_schedule_recipients_pkey ON public.report_schedule_recipients USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## report_schedules

Programación futura por zona horaria, deshabilitada por defecto.

Módulo: **reporting**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `definition_id` | `uuid` | No | — | FK |
| `format` | `text` | No | — | FK |
| `cron_expression` | `text` | No | — |  |
| `time_zone` | `text` | No | — |  |
| `is_enabled` | `boolean` | No | false |  |

Restricciones:

- `report_schedules_definition_id_fkey`: `FOREIGN KEY (definition_id) REFERENCES report_definitions(id) ON DELETE RESTRICT`. Validada: True.
- `report_schedules_format_fkey`: `FOREIGN KEY (format) REFERENCES report_formats(code) ON DELETE RESTRICT`. Validada: True.
- `report_schedules_organization_id_fkey`: `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`. Validada: True.
- `report_schedules_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `report_schedules_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: `report_schedule_recipients` (organization_id, schedule_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_report_schedules_288d20c38667 ON public.report_schedules USING btree (definition_id)`
- `CREATE INDEX ix_fk_report_schedules_549015267a2a ON public.report_schedules USING btree (format)`
- `CREATE UNIQUE INDEX report_schedules_organization_id_id_key ON public.report_schedules USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX report_schedules_pkey ON public.report_schedules USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## report_types

Tipos de informe.

Módulo: **reporting**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `code` | `text` | No | — | PK |

Restricciones:

- `report_types_pkey`: `PRIMARY KEY (code)`. Validada: True.

Referencias entrantes: `report_definitions` (report_type).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX report_types_pkey ON public.report_types USING btree (code)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## repositories

Repositorio identificado por proveedor e ID externo dentro del tenant.

Módulo: **integration**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | — | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `integration_id` | `uuid` | Sí | — | FK |
| `provider` | `character varying(40)` | No | — | FK, U |
| `external_id` | `character varying(255)` | No | — | U |
| `full_name` | `character varying(512)` | No | — |  |
| `default_branch` | `character varying(255)` | No | — |  |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `html_url` | `text` | Sí | — |  |
| `visibility` | `text` | No | 'UNKNOWN'::text |  |
| `is_archived` | `boolean` | No | false |  |
| `is_enabled` | `boolean` | No | true |  |
| `last_synced_at` | `timestamp with time zone` | Sí | — |  |

Restricciones:

- `fk_repository_integration_provider`: `FOREIGN KEY (organization_id, integration_id, provider) REFERENCES integrations(organization_id, id, provider) ON DELETE RESTRICT`. Validada: True.
- `fk_repository_provider`: `FOREIGN KEY (provider) REFERENCES integration_providers(code) ON DELETE RESTRICT`. Validada: True.
- `repositories_default_branch_check`: `CHECK ((btrim((default_branch)::text) <> ''::text))`. Validada: True.
- `repositories_external_id_check`: `CHECK ((btrim((external_id)::text) <> ''::text))`. Validada: True.
- `repositories_full_name_check`: `CHECK ((btrim((full_name)::text) <> ''::text))`. Validada: True.
- `repositories_organization_id_fkey`: `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`. Validada: True.
- `repositories_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `repositories_organization_id_integration_id_fkey`: `FOREIGN KEY (organization_id, integration_id) REFERENCES integrations(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `repositories_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `repositories_provider_check`: `CHECK (((btrim((provider)::text) <> ''::text) AND ((provider)::text = lower((provider)::text))))`. Validada: True.
- `repositories_visibility_check`: `CHECK ((visibility = ANY (ARRAY['PUBLIC'::text, 'PRIVATE'::text, 'INTERNAL'::text, 'UNKNOWN'::text])))`. Validada: True.
- `uq_repository_provider_identity`: `UNIQUE (organization_id, provider, external_id)`. Validada: True.

Referencias entrantes: `document_repositories` (organization_id, repository_id), `impact_finding_repositories` (organization_id, repository_id), `member_repository_assignments` (organization_id, repository_id), `member_repository_permissions` (organization_id, repository_id), `membership_invitations` (organization_id, repository_id), `report_repositories` (organization_id, repository_id), `repository_branches` (organization_id, repository_id), `repository_commits` (organization_id, repository_id), `repository_settings` (organization_id, repository_id), `repository_team_access` (organization_id, repository_id), `scans` (organization_id, repository_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_repositories_d5398b0f4192 ON public.repositories USING btree (provider)`
- `CREATE INDEX ix_fk_repositories_f9e7b0f06a7b ON public.repositories USING btree (organization_id, integration_id, provider)`
- `CREATE INDEX ix_repositories_integration ON public.repositories USING btree (organization_id, integration_id)`
- `CREATE INDEX ix_repositories_name ON public.repositories USING btree (organization_id, full_name)`
- `CREATE UNIQUE INDEX repositories_organization_id_id_key ON public.repositories USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX repositories_pkey ON public.repositories USING btree (id)`
- `CREATE UNIQUE INDEX uq_repository_provider_identity ON public.repositories USING btree (organization_id, provider, external_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `platform_administrator_read`, roles public: USING `app_private.is_platform_administrator()`; CHECK `None`.
- Política `platform_administrator_update`, roles public: USING `app_private.is_platform_administrator()`; CHECK `app_private.is_platform_administrator()`.
- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## repository_branches

Ramas observadas y commit más reciente, si existe.

Módulo: **integration**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK, U |
| `repository_id` | `uuid` | No | — | FK, U |
| `name` | `text` | No | — | U |
| `last_commit_id` | `uuid` | Sí | — | FK |

Restricciones:

- `repository_branches_name_check`: `CHECK ((btrim(name) <> ''::text))`. Validada: True.
- `repository_branches_organization_id_repository_id_fkey`: `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `repository_branches_organization_id_repository_id_last_com_fkey`: `FOREIGN KEY (organization_id, repository_id, last_commit_id) REFERENCES repository_commits(organization_id, repository_id, id) ON DELETE RESTRICT`. Validada: True.
- `repository_branches_organization_id_repository_id_name_key`: `UNIQUE (organization_id, repository_id, name)`. Validada: True.
- `repository_branches_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_repository_branches_87efe4031e57 ON public.repository_branches USING btree (organization_id, repository_id, last_commit_id)`
- `CREATE UNIQUE INDEX repository_branches_organization_id_repository_id_name_key ON public.repository_branches USING btree (organization_id, repository_id, name)`
- `CREATE UNIQUE INDEX repository_branches_pkey ON public.repository_branches USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## repository_commits

Identidad de commit dentro de un repositorio; no es un catálogo global de SHA.

Módulo: **integration**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `repository_id` | `uuid` | No | — | FK, U |
| `commit_sha` | `character varying(64)` | No | — | U |
| `authored_at` | `timestamp with time zone` | Sí | — |  |
| `message` | `text` | Sí | — |  |
| `html_url` | `text` | Sí | — |  |

Restricciones:

- `repository_commits_commit_sha_check`: `CHECK (((commit_sha)::text ~ '^[a-fA-F0-9]{40}([a-fA-F0-9]{24})?$'::text))`. Validada: True.
- `repository_commits_organization_id_repository_id_commit_sha_key`: `UNIQUE (organization_id, repository_id, commit_sha)`. Validada: True.
- `repository_commits_organization_id_repository_id_fkey`: `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `repository_commits_organization_id_repository_id_id_key`: `UNIQUE (organization_id, repository_id, id)`. Validada: True.
- `repository_commits_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: `repository_branches` (organization_id, repository_id, last_commit_id), `scans` (organization_id, repository_id, repository_commit_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX repository_commits_organization_id_repository_id_commit_sha_key ON public.repository_commits USING btree (organization_id, repository_id, commit_sha)`
- `CREATE UNIQUE INDEX repository_commits_organization_id_repository_id_id_key ON public.repository_commits USING btree (organization_id, repository_id, id)`
- `CREATE UNIQUE INDEX repository_commits_pkey ON public.repository_commits USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## repository_settings

Configuración escalar de análisis por repositorio.

Módulo: **integration**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `repository_id` | `uuid` | No | — | FK, PK |
| `scan_on_push` | `boolean` | No | false |  |
| `max_scan_seconds` | `integer` | No | 600 |  |

Restricciones:

- `repository_settings_max_scan_seconds_check`: `CHECK (((max_scan_seconds >= 1) AND (max_scan_seconds <= 86400)))`. Validada: True.
- `repository_settings_organization_id_repository_id_fkey`: `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `repository_settings_pkey`: `PRIMARY KEY (organization_id, repository_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX repository_settings_pkey ON public.repository_settings USING btree (organization_id, repository_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## repository_team_access

Permisos por equipo y repositorio del mismo tenant.

Módulo: **identity**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `repository_id` | `uuid` | No | — | FK, PK |
| `team_id` | `uuid` | No | — | FK, PK |
| `permission_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `repository_team_access_organization_id_repository_id_fkey`: `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `repository_team_access_organization_id_team_id_fkey`: `FOREIGN KEY (organization_id, team_id) REFERENCES organization_teams(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `repository_team_access_permission_id_fkey`: `FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE RESTRICT`. Validada: True.
- `repository_team_access_pkey`: `PRIMARY KEY (organization_id, repository_id, team_id, permission_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_repository_team_access_37ffeb3034bb ON public.repository_team_access USING btree (permission_id)`
- `CREATE INDEX ix_repository_team_access_team ON public.repository_team_access USING btree (organization_id, team_id)`
- `CREATE UNIQUE INDEX repository_team_access_pkey ON public.repository_team_access USING btree (organization_id, repository_id, team_id, permission_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `platform_administrator_read`, roles public: USING `app_private.is_platform_administrator()`; CHECK `None`.
- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## role_permissions

Asignación independiente M:N de permisos a roles.

Módulo: **identity**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `role_id` | `uuid` | No | — | FK, PK |
| `permission_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `role_permissions_permission_id_fkey`: `FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE RESTRICT`. Validada: True.
- `role_permissions_pkey`: `PRIMARY KEY (role_id, permission_id)`. Validada: True.
- `role_permissions_role_id_fkey`: `FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_role_permissions_7557ca12b2f6 ON public.role_permissions USING btree (permission_id)`
- `CREATE UNIQUE INDEX role_permissions_pkey ON public.role_permissions USING btree (role_id, permission_id)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## roles

Roles globales base; no configura roles personalizados por inferencia.

Módulo: **identity**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `code` | `text` | No | — | U |
| `name` | `text` | No | — |  |

Restricciones:

- `roles_code_key`: `UNIQUE (code)`. Validada: True.
- `roles_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: `invitation_roles` (role_id), `membership_roles` (role_id), `role_permissions` (role_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX roles_code_key ON public.roles USING btree (code)`
- `CREATE UNIQUE INDEX roles_pkey ON public.roles USING btree (id)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## sbom_documents

Procedencia de SBOM, antes sbom_imports; archivo retenido opcional, nunca inventado.

Módulo: **scan**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK, U |
| `scan_id` | `uuid` | No | — | FK, PK |
| `format` | `character varying(40)` | No | — |  |
| `specification_version` | `character varying(20)` | No | — |  |
| `serial_number` | `character varying(64)` | Sí | — |  |
| `sha256` | `character varying(64)` | No | — |  |
| `id` | `uuid` | No | gen_random_uuid() | U |
| `artifact_id` | `uuid` | Sí | — | FK |
| `generated_at` | `timestamp with time zone` | Sí | — |  |
| `document_version` | `integer` | No | 1 |  |

Restricciones:

- `fk_sbom_artifact`: `FOREIGN KEY (organization_id, scan_id, artifact_id) REFERENCES scan_artifacts(organization_id, scan_id, id) ON DELETE RESTRICT`. Validada: True.
- `sbom_documents_document_version_check`: `CHECK ((document_version > 0))`. Validada: True.
- `sbom_imports_format_check`: `CHECK ((btrim((format)::text) <> ''::text))`. Validada: True.
- `sbom_imports_organization_id_scan_id_fkey`: `FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `sbom_imports_pkey`: `PRIMARY KEY (organization_id, scan_id)`. Validada: True.
- `sbom_imports_sha256_check`: `CHECK (((sha256)::text ~ '^[a-f0-9]{64}$'::text))`. Validada: True.
- `sbom_imports_specification_version_check`: `CHECK ((btrim((specification_version)::text) <> ''::text))`. Validada: True.
- `uq_sbom_document_id`: `UNIQUE (organization_id, id)`. Validada: True.

Referencias entrantes: `sbom_external_references` (organization_id, sbom_document_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_sbom_documents_cbdf47fbe1e8 ON public.sbom_documents USING btree (organization_id, scan_id, artifact_id)`
- `CREATE UNIQUE INDEX sbom_imports_pkey ON public.sbom_documents USING btree (organization_id, scan_id)`
- `CREATE UNIQUE INDEX uq_sbom_document_id ON public.sbom_documents USING btree (organization_id, id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## sbom_external_references

Referencias externas asociadas al documento SBOM.

Módulo: **scan**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK, U |
| `sbom_document_id` | `uuid` | No | — | FK, U |
| `reference_type` | `text` | No | — | U |
| `url` | `text` | No | — | U |

Restricciones:

- `sbom_external_references_organization_id_sbom_document_id_fkey`: `FOREIGN KEY (organization_id, sbom_document_id) REFERENCES sbom_documents(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `sbom_external_references_organization_id_sbom_document_id_r_key`: `UNIQUE (organization_id, sbom_document_id, reference_type, url)`. Validada: True.
- `sbom_external_references_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX sbom_external_references_organization_id_sbom_document_id_r_key ON public.sbom_external_references USING btree (organization_id, sbom_document_id, reference_type, url)`
- `CREATE UNIQUE INDEX sbom_external_references_pkey ON public.sbom_external_references USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## scan_artifacts

Referencias privadas a artefactos realmente almacenados con hash/tamaño.

Módulo: **scan**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | — | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `scan_id` | `uuid` | No | — | FK, U |
| `format` | `character varying(40)` | No | — |  |
| `storage_key` | `text` | No | — |  |
| `sha256` | `character varying(64)` | No | — |  |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `size_bytes` | `bigint` | Sí | — |  |
| `media_type` | `text` | Sí | — |  |
| `expires_at` | `timestamp with time zone` | Sí | — |  |

Restricciones:

- `scan_artifacts_format_check`: `CHECK ((btrim((format)::text) <> ''::text))`. Validada: True.
- `scan_artifacts_organization_id_scan_id_fkey`: `FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `scan_artifacts_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `scan_artifacts_sha256_check`: `CHECK (((sha256)::text ~ '^[a-f0-9]{64}$'::text))`. Validada: True.
- `scan_artifacts_size_bytes_check`: `CHECK ((size_bytes >= 0))`. Validada: True.
- `scan_artifacts_storage_key_check`: `CHECK ((btrim(storage_key) <> ''::text))`. Validada: True.
- `uq_scan_artifact_id`: `UNIQUE (organization_id, id)`. Validada: True.
- `uq_scan_artifact_snapshot`: `UNIQUE (organization_id, scan_id, id)`. Validada: True.

Referencias entrantes: `sbom_documents` (organization_id, scan_id, artifact_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_artifacts_scan ON public.scan_artifacts USING btree (organization_id, scan_id)`
- `CREATE UNIQUE INDEX scan_artifacts_pkey ON public.scan_artifacts USING btree (id)`
- `CREATE UNIQUE INDEX uq_scan_artifact_id ON public.scan_artifacts USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX uq_scan_artifact_snapshot ON public.scan_artifacts USING btree (organization_id, scan_id, id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## scan_comparisons

Comparación reproducible entre snapshots distintos del mismo repositorio.

Módulo: **scan**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `repository_id` | `uuid` | No | — | FK |
| `base_scan_id` | `uuid` | No | — | FK, U |
| `target_scan_id` | `uuid` | No | — | FK, U |
| `rules_version` | `text` | No | — | U |
| `status` | `text` | No | 'PENDING'::text | FK |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |

Restricciones:

- `scan_comparisons_check`: `CHECK ((base_scan_id <> target_scan_id))`. Validada: True.
- `scan_comparisons_organization_id_base_scan_id_target_scan_i_key`: `UNIQUE (organization_id, base_scan_id, target_scan_id, rules_version)`. Validada: True.
- `scan_comparisons_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `scan_comparisons_organization_id_repository_id_base_scan_i_fkey`: `FOREIGN KEY (organization_id, repository_id, base_scan_id) REFERENCES scans(organization_id, repository_id, id) ON DELETE RESTRICT`. Validada: True.
- `scan_comparisons_organization_id_repository_id_target_scan_fkey`: `FOREIGN KEY (organization_id, repository_id, target_scan_id) REFERENCES scans(organization_id, repository_id, id) ON DELETE RESTRICT`. Validada: True.
- `scan_comparisons_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `scan_comparisons_status_fkey`: `FOREIGN KEY (status) REFERENCES job_statuses(code) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: `notifications` (organization_id, comparison_id), `scan_component_changes` (organization_id, comparison_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_scan_comparisons_1b3d0b904a44 ON public.scan_comparisons USING btree (organization_id, repository_id, target_scan_id)`
- `CREATE INDEX ix_fk_scan_comparisons_a7172a6c3851 ON public.scan_comparisons USING btree (status)`
- `CREATE INDEX ix_fk_scan_comparisons_deb5c203e28f ON public.scan_comparisons USING btree (organization_id, repository_id, base_scan_id)`
- `CREATE UNIQUE INDEX scan_comparisons_organization_id_base_scan_id_target_scan_i_key ON public.scan_comparisons USING btree (organization_id, base_scan_id, target_scan_id, rules_version)`
- `CREATE UNIQUE INDEX scan_comparisons_organization_id_id_key ON public.scan_comparisons USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX scan_comparisons_pkey ON public.scan_comparisons USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## scan_component_changes

Resultado derivado/persistido de comparación por paquete y versiones.

Módulo: **scan**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK, U |
| `comparison_id` | `uuid` | No | — | FK, U |
| `component_id` | `uuid` | No | — | FK, U |
| `before_version_id` | `uuid` | Sí | — | FK |
| `after_version_id` | `uuid` | Sí | — | FK |
| `change_type` | `text` | No | — |  |

Restricciones:

- `scan_component_changes_change_type_check`: `CHECK ((change_type = ANY (ARRAY['ADDED'::text, 'REMOVED'::text, 'UPDATED'::text, 'UNCHANGED'::text])))`. Validada: True.
- `scan_component_changes_check`: `CHECK ((((change_type = 'ADDED'::text) AND (before_version_id IS NULL) AND (after_version_id IS NOT NULL)) OR ((change_type = 'REMOVED'::text) AND (before_version_id IS NOT NULL) AND (after_version_id IS NULL)) OR ((change_type = 'UPDATED'::text) AND (before_version_id IS NOT NULL) AND (after_version_id IS NOT NULL) AND (before_version_id <> after_version_id)) OR ((change_type = 'UNCHANGED'::text) AND (before_version_id IS NOT NULL) AND (after_version_id IS NOT NULL) AND (before_version_id = after_version_id))))`. Validada: True.
- `scan_component_changes_organization_id_comparison_id_compon_key`: `UNIQUE (organization_id, comparison_id, component_id)`. Validada: True.
- `scan_component_changes_organization_id_comparison_id_fkey`: `FOREIGN KEY (organization_id, comparison_id) REFERENCES scan_comparisons(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `scan_component_changes_organization_id_component_id_after__fkey`: `FOREIGN KEY (organization_id, component_id, after_version_id) REFERENCES component_versions(organization_id, component_id, id) ON DELETE RESTRICT`. Validada: True.
- `scan_component_changes_organization_id_component_id_before_fkey`: `FOREIGN KEY (organization_id, component_id, before_version_id) REFERENCES component_versions(organization_id, component_id, id) ON DELETE RESTRICT`. Validada: True.
- `scan_component_changes_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_scan_component_changes_8f6fccad6fd8 ON public.scan_component_changes USING btree (organization_id, component_id, after_version_id)`
- `CREATE INDEX ix_fk_scan_component_changes_be008703d31e ON public.scan_component_changes USING btree (organization_id, component_id, before_version_id)`
- `CREATE UNIQUE INDEX scan_component_changes_organization_id_comparison_id_compon_key ON public.scan_component_changes USING btree (organization_id, comparison_id, component_id)`
- `CREATE UNIQUE INDEX scan_component_changes_pkey ON public.scan_component_changes USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## scan_component_licenses

Licencias observadas en una aparición concreta del SBOM.

Módulo: **component**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `scan_component_id` | `uuid` | No | — | FK, PK |
| `license_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `scan_component_licenses_license_id_fkey`: `FOREIGN KEY (license_id) REFERENCES license_catalog(id) ON DELETE RESTRICT`. Validada: True.
- `scan_component_licenses_organization_id_scan_component_id_fkey`: `FOREIGN KEY (organization_id, scan_component_id) REFERENCES scan_components(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `scan_component_licenses_pkey`: `PRIMARY KEY (organization_id, scan_component_id, license_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_scan_component_licenses_0b137bbca36b ON public.scan_component_licenses USING btree (license_id)`
- `CREATE UNIQUE INDEX scan_component_licenses_pkey ON public.scan_component_licenses USING btree (organization_id, scan_component_id, license_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## scan_components

Aparición de una versión dentro de un snapshot, con bom_ref local.

Módulo: **scan**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK, U |
| `scan_id` | `uuid` | No | — | FK, PK, U |
| `bom_ref` | `character varying(512)` | No | — | PK |
| `dependency_type` | `character varying(20)` | No | — |  |
| `id` | `uuid` | No | gen_random_uuid() | U |
| `component_version_id` | `uuid` | No | — | FK |
| `dependency_scope_id` | `uuid` | Sí | — | FK |
| `is_root` | `boolean` | No | false |  |

Restricciones:

- `fk_scan_component_version`: `FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `scan_components_bom_ref_check`: `CHECK ((btrim((bom_ref)::text) <> ''::text))`. Validada: True.
- `scan_components_dependency_scope_id_fkey`: `FOREIGN KEY (dependency_scope_id) REFERENCES dependency_scopes(id) ON DELETE RESTRICT`. Validada: True.
- `scan_components_dependency_type_check`: `CHECK (((dependency_type)::text = ANY ((ARRAY['DIRECT'::character varying, 'TRANSITIVE'::character varying, 'UNKNOWN'::character varying])::text[])))`. Validada: True.
- `scan_components_organization_id_scan_id_fkey`: `FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `scan_components_pkey`: `PRIMARY KEY (organization_id, scan_id, bom_ref)`. Validada: True.
- `uq_scan_component_id`: `UNIQUE (organization_id, scan_id, id)`. Validada: True.
- `uq_scan_component_tenant_id`: `UNIQUE (organization_id, id)`. Validada: True.

Referencias entrantes: `assistant_message_evidences` (organization_id, scan_component_id), `dependency_edges` (organization_id, scan_id, source_scan_component_id), `dependency_edges` (organization_id, scan_id, target_scan_component_id), `scan_component_licenses` (organization_id, scan_component_id), `scan_vulnerability_findings` (organization_id, scan_id, scan_component_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_scan_components_a4a31a9ebf3c ON public.scan_components USING btree (dependency_scope_id)`
- `CREATE INDEX ix_scan_component_versions ON public.scan_components USING btree (organization_id, component_version_id)`
- `CREATE UNIQUE INDEX scan_components_pkey ON public.scan_components USING btree (organization_id, scan_id, bom_ref)`
- `CREATE UNIQUE INDEX uq_scan_component_id ON public.scan_components USING btree (organization_id, scan_id, id)`
- `CREATE UNIQUE INDEX uq_scan_component_tenant_id ON public.scan_components USING btree (organization_id, id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## scan_logs

Diagnóstico estructurado; la redacción de secretos corresponde al escritor.

Módulo: **scan**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `organization_id` | `uuid` | No | — | FK |
| `scan_id` | `uuid` | No | — | FK |
| `occurred_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `level` | `text` | No | — |  |
| `message` | `text` | No | — |  |
| `sanitized_metadata` | `jsonb` | No | '{}'::jsonb |  |

Restricciones:

- `scan_logs_level_check`: `CHECK ((level = ANY (ARRAY['DEBUG'::text, 'INFO'::text, 'WARN'::text, 'ERROR'::text])))`. Validada: True.
- `scan_logs_organization_id_scan_id_fkey`: `FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `scan_logs_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_scan_logs_history ON public.scan_logs USING btree (organization_id, scan_id, occurred_at)`
- `CREATE UNIQUE INDEX scan_logs_pkey ON public.scan_logs USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## scan_profile_ecosystems

Ecosistemas habilitados por perfil como relación M:N.

Módulo: **scan**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `scan_profile_id` | `uuid` | No | — | FK, PK |
| `package_ecosystem_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `scan_profile_ecosystems_organization_id_scan_profile_id_fkey`: `FOREIGN KEY (organization_id, scan_profile_id) REFERENCES scan_profiles(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `scan_profile_ecosystems_package_ecosystem_id_fkey`: `FOREIGN KEY (package_ecosystem_id) REFERENCES package_ecosystems(id) ON DELETE RESTRICT`. Validada: True.
- `scan_profile_ecosystems_pkey`: `PRIMARY KEY (organization_id, scan_profile_id, package_ecosystem_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_scan_profile_ecosystems_09782f223459 ON public.scan_profile_ecosystems USING btree (package_ecosystem_id)`
- `CREATE UNIQUE INDEX scan_profile_ecosystems_pkey ON public.scan_profile_ecosystems USING btree (organization_id, scan_profile_id, package_ecosystem_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## scan_profiles

Configuración reutilizable de duración y ecosistemas.

Módulo: **scan**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `name` | `text` | No | — | U |
| `max_duration_seconds` | `integer` | No | — |  |

Restricciones:

- `scan_profiles_max_duration_seconds_check`: `CHECK ((max_duration_seconds > 0))`. Validada: True.
- `scan_profiles_name_check`: `CHECK ((btrim(name) <> ''::text))`. Validada: True.
- `scan_profiles_organization_id_fkey`: `FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT`. Validada: True.
- `scan_profiles_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `scan_profiles_organization_id_name_key`: `UNIQUE (organization_id, name)`. Validada: True.
- `scan_profiles_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: `scan_profile_ecosystems` (organization_id, scan_profile_id), `scans` (organization_id, scan_profile_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX scan_profiles_organization_id_id_key ON public.scan_profiles USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX scan_profiles_organization_id_name_key ON public.scan_profiles USING btree (organization_id, name)`
- `CREATE UNIQUE INDEX scan_profiles_pkey ON public.scan_profiles USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## scan_stage_executions

Historial de ejecución de una etapa e intento.

Módulo: **scan**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `scan_id` | `uuid` | No | — | FK, U |
| `stage_code` | `text` | No | — | FK, U |
| `attempt_number` | `integer` | No | 1 | U |
| `status` | `text` | No | — | FK |
| `started_at` | `timestamp with time zone` | Sí | — |  |
| `finished_at` | `timestamp with time zone` | Sí | — |  |
| `error_code` | `text` | Sí | — |  |

Restricciones:

- `scan_stage_executions_attempt_number_check`: `CHECK ((attempt_number > 0))`. Validada: True.
- `scan_stage_executions_check`: `CHECK (((finished_at IS NULL) OR ((started_at IS NOT NULL) AND (finished_at >= started_at))))`. Validada: True.
- `scan_stage_executions_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `scan_stage_executions_organization_id_scan_id_fkey`: `FOREIGN KEY (organization_id, scan_id) REFERENCES scans(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `scan_stage_executions_organization_id_scan_id_stage_code_at_key`: `UNIQUE (organization_id, scan_id, stage_code, attempt_number)`. Validada: True.
- `scan_stage_executions_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `scan_stage_executions_stage_code_fkey`: `FOREIGN KEY (stage_code) REFERENCES scan_stages(code) ON DELETE RESTRICT`. Validada: True.
- `scan_stage_executions_status_fkey`: `FOREIGN KEY (status) REFERENCES job_statuses(code) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_scan_stage_executions_03a4a98b9edc ON public.scan_stage_executions USING btree (stage_code)`
- `CREATE INDEX ix_fk_scan_stage_executions_995e9dcf2ad0 ON public.scan_stage_executions USING btree (status)`
- `CREATE UNIQUE INDEX scan_stage_executions_organization_id_id_key ON public.scan_stage_executions USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX scan_stage_executions_organization_id_scan_id_stage_code_at_key ON public.scan_stage_executions USING btree (organization_id, scan_id, stage_code, attempt_number)`
- `CREATE UNIQUE INDEX scan_stage_executions_pkey ON public.scan_stage_executions USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## scan_stages

Etapas ordenadas del pipeline.

Módulo: **scan**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `code` | `text` | No | — | PK |
| `sequence_number` | `integer` | No | — | U |

Restricciones:

- `scan_stages_pkey`: `PRIMARY KEY (code)`. Validada: True.
- `scan_stages_sequence_number_check`: `CHECK ((sequence_number > 0))`. Validada: True.
- `scan_stages_sequence_number_key`: `UNIQUE (sequence_number)`. Validada: True.

Referencias entrantes: `scan_stage_executions` (stage_code).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX scan_stages_pkey ON public.scan_stages USING btree (code)`
- `CREATE UNIQUE INDEX scan_stages_sequence_number_key ON public.scan_stages USING btree (sequence_number)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## scan_vulnerability_findings

Hallazgo de advisory en una aparición de un snapshot.

Módulo: **vulnerability**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, U |
| `scan_id` | `uuid` | No | — | FK |
| `vulnerability_id` | `uuid` | No | — | FK, U |
| `detected_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `scan_component_id` | `uuid` | No | — | FK, U |
| `match_status` | `text` | No | 'MATCHED'::text |  |
| `resolution_status` | `text` | No | 'OPEN'::text |  |
| `applied_severity` | `text` | Sí | — | FK |

Restricciones:

- `fk_finding_occurrence`: `FOREIGN KEY (organization_id, scan_id, scan_component_id) REFERENCES scan_components(organization_id, scan_id, id) ON DELETE RESTRICT`. Validada: True.
- `scan_vulnerability_findings_applied_severity_fkey`: `FOREIGN KEY (applied_severity) REFERENCES severity_levels(code) ON DELETE RESTRICT`. Validada: True.
- `scan_vulnerability_findings_match_status_check`: `CHECK ((match_status = ANY (ARRAY['MATCHED'::text, 'UNDETERMINED'::text])))`. Validada: True.
- `scan_vulnerability_findings_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `scan_vulnerability_findings_resolution_status_check`: `CHECK ((resolution_status = ANY (ARRAY['OPEN'::text, 'DISMISSED'::text, 'RESOLVED'::text])))`. Validada: True.
- `uq_finding_advisory`: `UNIQUE (organization_id, id, vulnerability_id)`. Validada: True.
- `uq_finding_occurrence_advisory`: `UNIQUE (organization_id, scan_component_id, vulnerability_id)`. Validada: True.
- `uq_finding_tenant_id`: `UNIQUE (organization_id, id)`. Validada: True.
- `vulnerability_findings_vulnerability_id_fkey`: `FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities(id) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: `assistant_message_evidences` (organization_id, finding_id), `finding_evidence` (organization_id, finding_id, vulnerability_id), `finding_fixed_versions` (organization_id, finding_id), `notifications` (organization_id, finding_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_findings_vulnerability ON public.scan_vulnerability_findings USING btree (vulnerability_id)`
- `CREATE INDEX ix_fk_scan_vulnerability_findings_58ec0c855ac6 ON public.scan_vulnerability_findings USING btree (applied_severity)`
- `CREATE INDEX ix_fk_scan_vulnerability_findings_fb532e3e54de ON public.scan_vulnerability_findings USING btree (organization_id, scan_id, scan_component_id)`
- `CREATE UNIQUE INDEX scan_vulnerability_findings_pkey ON public.scan_vulnerability_findings USING btree (id)`
- `CREATE UNIQUE INDEX uq_finding_advisory ON public.scan_vulnerability_findings USING btree (organization_id, id, vulnerability_id)`
- `CREATE UNIQUE INDEX uq_finding_occurrence_advisory ON public.scan_vulnerability_findings USING btree (organization_id, scan_component_id, vulnerability_id)`
- `CREATE UNIQUE INDEX uq_finding_tenant_id ON public.scan_vulnerability_findings USING btree (organization_id, id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## scanner_tools

Herramientas de scanner conocidas; no instala ni ejecuta scanners.

Módulo: **scan**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `code` | `text` | No | — | PK |
| `name` | `text` | No | — |  |

Restricciones:

- `scanner_tools_code_check`: `CHECK ((btrim(code) <> ''::text))`. Validada: True.
- `scanner_tools_pkey`: `PRIMARY KEY (code)`. Validada: True.

Referencias entrantes: `scans` (scanner_name).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX scanner_tools_pkey ON public.scanner_tools USING btree (code)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## scans

Snapshot con origen, estado, repositorio y commit cuando existe; importación no inventa scanner.

Módulo: **scan**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | — | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `repository_id` | `uuid` | No | — | FK, U |
| `git_reference` | `character varying(255)` | Sí | — |  |
| `status` | `character varying(20)` | No | — |  |
| `scanner_name` | `character varying(100)` | Sí | — | FK |
| `scanner_version` | `character varying(100)` | Sí | — |  |
| `requested_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `started_at` | `timestamp with time zone` | Sí | — |  |
| `finished_at` | `timestamp with time zone` | Sí | — |  |
| `failure_reason` | `text` | Sí | — |  |
| `origin` | `character varying(20)` | No | 'REPOSITORY_SCAN'::character varying |  |
| `scan_profile_id` | `uuid` | Sí | — | FK |
| `background_job_id` | `uuid` | Sí | — | FK |
| `requested_by_membership_id` | `uuid` | Sí | — | FK |
| `trigger_type` | `text` | No | 'MANUAL'::text |  |
| `repository_commit_id` | `uuid` | Sí | — | FK, U |

Restricciones:

- `ck_scan_origin_reference`: `CHECK (((((origin)::text = 'REPOSITORY_SCAN'::text) AND (git_reference IS NOT NULL)) OR (((origin)::text = 'SBOM_IMPORT'::text) AND (git_reference IS NULL) AND (repository_commit_id IS NULL) AND (scanner_name IS NULL) AND (scanner_version IS NULL) AND ((status)::text = 'COMPLETED'::text) AND (trigger_type = 'IMPORT'::text))))`. Validada: True.
- `fk_scan_commit`: `FOREIGN KEY (organization_id, repository_id, repository_commit_id) REFERENCES repository_commits(organization_id, repository_id, id) ON DELETE RESTRICT`. Validada: True.
- `fk_scan_job`: `FOREIGN KEY (organization_id, background_job_id) REFERENCES background_jobs(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `fk_scan_profile`: `FOREIGN KEY (organization_id, scan_profile_id) REFERENCES scan_profiles(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `fk_scan_requester`: `FOREIGN KEY (organization_id, requested_by_membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `fk_scan_tool`: `FOREIGN KEY (scanner_name) REFERENCES scanner_tools(code) ON DELETE RESTRICT`. Validada: True.
- `scans_check`: `CHECK (((started_at IS NULL) OR (started_at >= requested_at)))`. Validada: True.
- `scans_check1`: `CHECK (((finished_at IS NULL) OR (finished_at >= started_at)))`. Validada: True.
- `scans_check2`: `CHECK (((((status)::text = 'PENDING'::text) AND (started_at IS NULL) AND (finished_at IS NULL) AND (failure_reason IS NULL)) OR (((status)::text = 'RUNNING'::text) AND (started_at IS NOT NULL) AND (finished_at IS NULL) AND (failure_reason IS NULL)) OR (((status)::text = 'COMPLETED'::text) AND (started_at IS NOT NULL) AND (finished_at IS NOT NULL) AND (failure_reason IS NULL)) OR (((status)::text = 'FAILED'::text) AND (started_at IS NOT NULL) AND (finished_at IS NOT NULL) AND (failure_reason IS NOT NULL) AND (btrim(failure_reason) <> ''::text))))`. Validada: True.
- `scans_git_reference_check`: `CHECK ((btrim((git_reference)::text) <> ''::text))`. Validada: True.
- `scans_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `scans_organization_id_repository_id_fkey`: `FOREIGN KEY (organization_id, repository_id) REFERENCES repositories(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `scans_origin_check`: `CHECK (((origin)::text = ANY ((ARRAY['REPOSITORY_SCAN'::character varying, 'SBOM_IMPORT'::character varying])::text[])))`. Validada: True.
- `scans_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `scans_status_check`: `CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RUNNING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))`. Validada: True.
- `scans_trigger_type_check`: `CHECK ((trigger_type = ANY (ARRAY['MANUAL'::text, 'WEBHOOK'::text, 'SCHEDULED'::text, 'IMPORT'::text])))`. Validada: True.
- `uq_scan_commit_context`: `UNIQUE (organization_id, repository_id, id, repository_commit_id)`. Validada: True.
- `uq_scan_repository_id`: `UNIQUE (organization_id, repository_id, id)`. Validada: True.

Referencias entrantes: `data_deletion_requests` (organization_id, scan_id), `impact_analyses` (organization_id, repository_id, base_scan_id, base_commit_id), `impact_analyses` (organization_id, repository_id, base_scan_id), `notifications` (organization_id, scan_id), `report_scans` (organization_id, scan_id), `sbom_documents` (organization_id, scan_id), `scan_artifacts` (organization_id, scan_id), `scan_comparisons` (organization_id, repository_id, base_scan_id), `scan_comparisons` (organization_id, repository_id, target_scan_id), `scan_components` (organization_id, scan_id), `scan_logs` (organization_id, scan_id), `scan_stage_executions` (organization_id, scan_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_scans_21873767fd3a ON public.scans USING btree (organization_id, background_job_id)`
- `CREATE INDEX ix_fk_scans_3a8a7e90cf0e ON public.scans USING btree (organization_id, scan_profile_id)`
- `CREATE INDEX ix_fk_scans_438244757505 ON public.scans USING btree (organization_id, repository_id, repository_commit_id)`
- `CREATE INDEX ix_fk_scans_bd15a8868ad5 ON public.scans USING btree (scanner_name)`
- `CREATE INDEX ix_fk_scans_ff6fe266116a ON public.scans USING btree (organization_id, requested_by_membership_id)`
- `CREATE INDEX ix_scans_history ON public.scans USING btree (organization_id, repository_id, requested_at DESC)`
- `CREATE INDEX ix_scans_pending ON public.scans USING btree (requested_at) WHERE ((status)::text = 'PENDING'::text)`
- `CREATE UNIQUE INDEX scans_organization_id_id_key ON public.scans USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX scans_pkey ON public.scans USING btree (id)`
- `CREATE UNIQUE INDEX uq_scan_commit_context ON public.scans USING btree (organization_id, repository_id, id, repository_commit_id)`
- `CREATE UNIQUE INDEX uq_scan_repository_id ON public.scans USING btree (organization_id, repository_id, id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.
- Trigger: `CREATE TRIGGER ck_scan_transition BEFORE UPDATE ON public.scans FOR EACH ROW EXECUTE FUNCTION app_private.protect_terminal_scan()`.

## severity_levels

Severidades normalizadas y orden; UNKNOWN conserva incertidumbre.

Módulo: **vulnerability**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `code` | `text` | No | — | PK |
| `sort_order` | `integer` | No | — | U |

Restricciones:

- `severity_levels_pkey`: `PRIMARY KEY (code)`. Validada: True.
- `severity_levels_sort_order_key`: `UNIQUE (sort_order)`. Validada: True.

Referencias entrantes: `impact_findings` (severity_code), `scan_vulnerability_findings` (applied_severity), `vulnerability_severities` (severity_code).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX severity_levels_pkey ON public.severity_levels USING btree (code)`
- `CREATE UNIQUE INDEX severity_levels_sort_order_key ON public.severity_levels USING btree (sort_order)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## simulation_test_runs

Resultados de pruebas; evidencia terminada inmutable, sin ejecutar comandos desde SQL.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `simulation_id` | `uuid` | No | — | FK |
| `tool_name` | `text` | No | — |  |
| `tool_version` | `text` | Sí | — |  |
| `command_summary` | `text` | No | — |  |
| `status` | `text` | No | — | FK |
| `exit_code` | `integer` | Sí | — |  |
| `started_at` | `timestamp with time zone` | Sí | — |  |
| `finished_at` | `timestamp with time zone` | Sí | — |  |
| `sanitized_summary` | `text` | Sí | — |  |

Restricciones:

- `simulation_test_runs_check`: `CHECK (((finished_at IS NULL) OR ((started_at IS NOT NULL) AND (finished_at >= started_at))))`. Validada: True.
- `simulation_test_runs_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `simulation_test_runs_organization_id_simulation_id_fkey`: `FOREIGN KEY (organization_id, simulation_id) REFERENCES change_simulations(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `simulation_test_runs_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `simulation_test_runs_status_fkey`: `FOREIGN KEY (status) REFERENCES job_statuses(code) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: `impact_findings` (organization_id, validation_test_run_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_simulation_test_runs_20e626e7cf40 ON public.simulation_test_runs USING btree (status)`
- `CREATE INDEX ix_fk_simulation_test_runs_5c601984e40f ON public.simulation_test_runs USING btree (organization_id, simulation_id)`
- `CREATE UNIQUE INDEX simulation_test_runs_organization_id_id_key ON public.simulation_test_runs USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX simulation_test_runs_pkey ON public.simulation_test_runs USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.
- Trigger: `CREATE TRIGGER ck_finished_test_immutable BEFORE UPDATE ON public.simulation_test_runs FOR EACH ROW EXECUTE FUNCTION app_private.protect_finished_test()`.

## team_memberships

Membresías pertenecientes a equipos del mismo tenant.

Módulo: **identity**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `organization_id` | `uuid` | No | — | FK, PK |
| `team_id` | `uuid` | No | — | FK, PK |
| `membership_id` | `uuid` | No | — | FK, PK |

Restricciones:

- `team_memberships_organization_id_membership_id_fkey`: `FOREIGN KEY (organization_id, membership_id) REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `team_memberships_organization_id_team_id_fkey`: `FOREIGN KEY (organization_id, team_id) REFERENCES organization_teams(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `team_memberships_pkey`: `PRIMARY KEY (organization_id, team_id, membership_id)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_team_membership_member ON public.team_memberships USING btree (organization_id, membership_id)`
- `CREATE UNIQUE INDEX team_memberships_pkey ON public.team_memberships USING btree (organization_id, team_id, membership_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `platform_administrator_read`, roles public: USING `app_private.is_platform_administrator()`; CHECK `None`.
- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## user_access_tokens

Hashes de tokens de verificación, recuperación, sesión y decisiones de admisión, de un solo uso y con expiración.

Módulo: **identity**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `user_id` | `uuid` | No | — | FK |
| `purpose` | `text` | No | — |  |
| `token_hash` | `text` | No | — | U |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `expires_at` | `timestamp with time zone` | No | — |  |
| `consumed_at` | `timestamp with time zone` | Sí | — |  |
| `target_user_id` | `uuid` | Sí | — | FK |
| `admission_decision` | `text` | Sí | — |  |

Restricciones:

- `user_access_tokens_admission_scope_check`: `CHECK ((((purpose = 'PLATFORM_ADMISSION_DECISION'::text) AND (target_user_id IS NOT NULL) AND (admission_decision IS NOT NULL) AND (admission_decision = ANY (ARRAY['APPROVE'::text, 'REJECT'::text]))) OR ((purpose <> 'PLATFORM_ADMISSION_DECISION'::text) AND (target_user_id IS NULL) AND (admission_decision IS NULL))))`. Validada: True.
- `user_access_tokens_check`: `CHECK ((expires_at > created_at))`. Validada: True.
- `user_access_tokens_check1`: `CHECK (((consumed_at IS NULL) OR (consumed_at >= created_at)))`. Validada: True.
- `user_access_tokens_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `user_access_tokens_purpose_check`: `CHECK ((purpose = ANY (ARRAY['VERIFY_EMAIL'::text, 'RESET_PASSWORD'::text, 'REFRESH_SESSION'::text, 'PLATFORM_ADMISSION_DECISION'::text])))`. Validada: True.
- `user_access_tokens_target_user_id_fkey`: `FOREIGN KEY (target_user_id) REFERENCES users(id) ON DELETE RESTRICT`. Validada: True.
- `user_access_tokens_token_hash_check`: `CHECK ((token_hash ~ '^[a-f0-9]{64}$'::text))`. Validada: True.
- `user_access_tokens_token_hash_key`: `UNIQUE (token_hash)`. Validada: True.
- `user_access_tokens_user_id_fkey`: `FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_account_token_requests ON public.user_access_tokens USING btree (user_id, purpose, created_at DESC)`
- `CREATE INDEX ix_admission_decision_tokens_pending ON public.user_access_tokens USING btree (target_user_id, created_at DESC) WHERE ((purpose = 'PLATFORM_ADMISSION_DECISION'::text) AND (consumed_at IS NULL))`
- `CREATE INDEX ix_user_access_tokens_user ON public.user_access_tokens USING btree (user_id, purpose)`
- `CREATE UNIQUE INDEX user_access_tokens_pkey ON public.user_access_tokens USING btree (id)`
- `CREATE UNIQUE INDEX user_access_tokens_token_hash_key ON public.user_access_tokens USING btree (token_hash)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## user_identities

Vínculos verificados a identidades externas Google/GitHub y sujetos históricos.

Módulo: **identity**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `user_id` | `uuid` | No | — | FK, U |
| `provider` | `text` | No | — | U |
| `provider_subject` | `text` | No | — | U |
| `linked_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |

Restricciones:

- `user_identities_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `user_identities_provider_check`: `CHECK ((provider = ANY (ARRAY['google'::text, 'github'::text, 'legacy'::text])))`. Validada: True.
- `user_identities_provider_provider_subject_key`: `UNIQUE (provider, provider_subject)`. Validada: True.
- `user_identities_provider_subject_check`: `CHECK ((btrim(provider_subject) <> ''::text))`. Validada: True.
- `user_identities_user_id_fkey`: `FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT`. Validada: True.
- `user_identities_user_id_provider_key`: `UNIQUE (user_id, provider)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_user_identities_user ON public.user_identities USING btree (user_id)`
- `CREATE UNIQUE INDEX user_identities_pkey ON public.user_identities USING btree (id)`
- `CREATE UNIQUE INDEX user_identities_provider_provider_subject_key ON public.user_identities USING btree (provider, provider_subject)`
- `CREATE UNIQUE INDEX user_identities_user_id_provider_key ON public.user_identities USING btree (user_id, provider)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## user_password_credentials

Hash de contraseña local, separado de identidades externas.

Módulo: **identity**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `user_id` | `uuid` | No | — | FK, PK |
| `password_hash` | `text` | No | — |  |
| `changed_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |

Restricciones:

- `user_password_credentials_password_hash_check`: `CHECK ((btrim(password_hash) <> ''::text))`. Validada: True.
- `user_password_credentials_pkey`: `PRIMARY KEY (user_id)`. Validada: True.
- `user_password_credentials_user_id_fkey`: `FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX user_password_credentials_pkey ON public.user_password_credentials USING btree (user_id)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## users

Identidad global con verificación de correo y versión de credenciales para revocar sesiones.

Módulo: **identity**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | — | PK |
| `email` | `character varying(320)` | No | — |  |
| `display_name` | `character varying(200)` | No | — |  |
| `created_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `email_normalized` | `text` | Sí | lower(btrim((email)::text)) | U |
| `email_verified_at` | `timestamp with time zone` | Sí | — |  |
| `avatar_url` | `text` | Sí | — |  |
| `is_active` | `boolean` | No | true |  |
| `updated_at` | `timestamp with time zone` | Sí | — |  |
| `credential_version` | `bigint` | No | 0 |  |
| `account_type` | `text` | No | 'OWNER'::text |  |
| `admission_status` | `text` | No | 'APPROVED'::text |  |
| `reviewed_by_user_id` | `uuid` | Sí | — | FK |
| `reviewed_at` | `timestamp with time zone` | Sí | — |  |

Restricciones:

- `uq_user_email_normalized`: `UNIQUE (email_normalized)`. Validada: True.
- `users_account_type_check`: `CHECK ((account_type = ANY (ARRAY['OWNER'::text, 'MEMBER'::text])))`. Validada: True.
- `users_admission_status_check`: `CHECK ((admission_status = ANY (ARRAY['PENDING_REVIEW'::text, 'APPROVED'::text, 'REJECTED'::text])))`. Validada: True.
- `users_credential_version_check`: `CHECK ((credential_version >= 0))`. Validada: True.
- `users_display_name_check`: `CHECK ((btrim((display_name)::text) <> ''::text))`. Validada: True.
- `users_email_check`: `CHECK ((btrim((email)::text) <> ''::text))`. Validada: True.
- `users_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `users_reviewed_by_user_id_fkey`: `FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: `audit_events` (actor_id), `membership_invitations` (accepted_by_user_id), `membership_invitations` (approved_by_user_id), `organization_memberships` (user_id), `organization_memberships` (invited_by_user_id), `platform_administrators` (user_id), `platform_audit_events` (actor_id), `user_access_tokens` (target_user_id), `user_access_tokens` (user_id), `user_identities` (user_id), `user_password_credentials` (user_id), `users` (reviewed_by_user_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX uq_user_email_normalized ON public.users USING btree (email_normalized)`
- `CREATE UNIQUE INDEX users_pkey ON public.users USING btree (id)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## version_change_records

Cambios documentados con fuente, versión y fecha de consulta.

Módulo: **impact**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `component_version_id` | `uuid` | No | — | FK |
| `source_url` | `text` | No | — |  |
| `published_at` | `timestamp with time zone` | Sí | — |  |
| `fetched_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `content` | `text` | No | — |  |

Restricciones:

- `version_change_records_organization_id_component_version_i_fkey`: `FOREIGN KEY (organization_id, component_version_id) REFERENCES component_versions(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `version_change_records_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `version_change_records_pkey`: `PRIMARY KEY (id)`. Validada: True.

Referencias entrantes: `change_record_evidences` (organization_id, change_record_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_version_change_records_fd989628ab55 ON public.version_change_records USING btree (organization_id, component_version_id)`
- `CREATE UNIQUE INDEX version_change_records_organization_id_id_key ON public.version_change_records USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX version_change_records_pkey ON public.version_change_records USING btree (id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.

## vulnerabilities

Advisory público canónico por fuente/identificador.

Módulo: **vulnerability**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | — | PK |
| `source` | `character varying(40)` | No | — | FK, U |
| `external_id` | `character varying(255)` | No | — | U |
| `summary` | `text` | Sí | — |  |
| `published_at` | `timestamp with time zone` | Sí | — |  |
| `modified_at` | `timestamp with time zone` | Sí | — |  |
| `status` | `text` | No | 'ACTIVE'::text |  |

Restricciones:

- `fk_vulnerability_source`: `FOREIGN KEY (source) REFERENCES vulnerability_sources(code) ON DELETE RESTRICT`. Validada: True.
- `vulnerabilities_external_id_check`: `CHECK ((btrim((external_id)::text) <> ''::text))`. Validada: True.
- `vulnerabilities_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `vulnerabilities_source_check`: `CHECK ((btrim((source)::text) <> ''::text))`. Validada: True.
- `vulnerabilities_source_external_id_key`: `UNIQUE (source, external_id)`. Validada: True.
- `vulnerabilities_status_check`: `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'WITHDRAWN'::text])))`. Validada: True.

Referencias entrantes: `impact_finding_vulnerabilities` (vulnerability_id), `scan_vulnerability_findings` (vulnerability_id), `vulnerability_affected_packages` (vulnerability_id), `vulnerability_aliases` (vulnerability_id), `vulnerability_references` (vulnerability_id), `vulnerability_severities` (vulnerability_id), `vulnerability_source_records` (vulnerability_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX vulnerabilities_pkey ON public.vulnerabilities USING btree (id)`
- `CREATE UNIQUE INDEX vulnerabilities_source_external_id_key ON public.vulnerabilities USING btree (source, external_id)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## vulnerability_affected_packages

Paquetes públicos afectados según advisory, separados del catálogo privado.

Módulo: **vulnerability**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `vulnerability_id` | `uuid` | No | — | FK, U |
| `package_ecosystem_id` | `uuid` | No | — | FK, U |
| `namespace` | `text` | Sí | — | U |
| `name` | `text` | No | — | U |

Restricciones:

- `vulnerability_affected_packag_vulnerability_id_package_ecos_key`: `UNIQUE NULLS NOT DISTINCT (vulnerability_id, package_ecosystem_id, namespace, name)`. Validada: True.
- `vulnerability_affected_packages_name_check`: `CHECK ((btrim(name) <> ''::text))`. Validada: True.
- `vulnerability_affected_packages_package_ecosystem_id_fkey`: `FOREIGN KEY (package_ecosystem_id) REFERENCES package_ecosystems(id) ON DELETE RESTRICT`. Validada: True.
- `vulnerability_affected_packages_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `vulnerability_affected_packages_vulnerability_id_fkey`: `FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities(id) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: `vulnerability_affected_ranges` (affected_package_id), `vulnerability_fixed_versions` (affected_package_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_vulnerability_affected_packages_6271e294c42a ON public.vulnerability_affected_packages USING btree (package_ecosystem_id)`
- `CREATE UNIQUE INDEX vulnerability_affected_packag_vulnerability_id_package_ecos_key ON public.vulnerability_affected_packages USING btree (vulnerability_id, package_ecosystem_id, namespace, name) NULLS NOT DISTINCT`
- `CREATE UNIQUE INDEX vulnerability_affected_packages_pkey ON public.vulnerability_affected_packages USING btree (id)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## vulnerability_affected_ranges

Rangos de versión por paquete afectado y tipo.

Módulo: **vulnerability**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `affected_package_id` | `uuid` | No | — | FK |
| `range_type` | `text` | No | — |  |
| `repository_url` | `text` | Sí | — |  |

Restricciones:

- `vulnerability_affected_ranges_affected_package_id_fkey`: `FOREIGN KEY (affected_package_id) REFERENCES vulnerability_affected_packages(id) ON DELETE RESTRICT`. Validada: True.
- `vulnerability_affected_ranges_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `vulnerability_affected_ranges_range_type_check`: `CHECK ((range_type = ANY (ARRAY['SEMVER'::text, 'ECOSYSTEM'::text, 'GIT'::text])))`. Validada: True.

Referencias entrantes: `vulnerability_range_events` (range_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_affected_ranges_package ON public.vulnerability_affected_ranges USING btree (affected_package_id)`
- `CREATE UNIQUE INDEX vulnerability_affected_ranges_pkey ON public.vulnerability_affected_ranges USING btree (id)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## vulnerability_aliases

Aliases independientes como CVE o GHSA por advisory.

Módulo: **vulnerability**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `vulnerability_id` | `uuid` | No | — | FK, U |
| `alias_type` | `text` | No | — | U |
| `alias_value` | `text` | No | — | U |

Restricciones:

- `vulnerability_aliases_alias_value_check`: `CHECK ((btrim(alias_value) <> ''::text))`. Validada: True.
- `vulnerability_aliases_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `vulnerability_aliases_vulnerability_id_alias_type_alias_val_key`: `UNIQUE (vulnerability_id, alias_type, alias_value)`. Validada: True.
- `vulnerability_aliases_vulnerability_id_fkey`: `FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities(id) ON DELETE RESTRICT`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_vulnerability_alias_lookup ON public.vulnerability_aliases USING btree (alias_type, alias_value)`
- `CREATE UNIQUE INDEX vulnerability_aliases_pkey ON public.vulnerability_aliases USING btree (id)`
- `CREATE UNIQUE INDEX vulnerability_aliases_vulnerability_id_alias_type_alias_val_key ON public.vulnerability_aliases USING btree (vulnerability_id, alias_type, alias_value)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## vulnerability_fixed_versions

Versiones corregidas publicadas por la fuente para un paquete afectado.

Módulo: **vulnerability**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `affected_package_id` | `uuid` | No | — | FK, PK |
| `version` | `text` | No | — | PK |

Restricciones:

- `vulnerability_fixed_versions_affected_package_id_fkey`: `FOREIGN KEY (affected_package_id) REFERENCES vulnerability_affected_packages(id) ON DELETE RESTRICT`. Validada: True.
- `vulnerability_fixed_versions_pkey`: `PRIMARY KEY (affected_package_id, version)`. Validada: True.
- `vulnerability_fixed_versions_version_check`: `CHECK ((btrim(version) <> ''::text))`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX vulnerability_fixed_versions_pkey ON public.vulnerability_fixed_versions USING btree (affected_package_id, version)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## vulnerability_range_events

Eventos ordenados de introducción/fix/límites del rango.

Módulo: **vulnerability**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `range_id` | `uuid` | No | — | FK, PK |
| `sequence_number` | `integer` | No | — | PK |
| `event_type` | `text` | No | — |  |
| `version_value` | `text` | No | — |  |

Restricciones:

- `vulnerability_range_events_event_type_check`: `CHECK ((event_type = ANY (ARRAY['INTRODUCED'::text, 'FIXED'::text, 'LAST_AFFECTED'::text, 'LIMIT'::text])))`. Validada: True.
- `vulnerability_range_events_pkey`: `PRIMARY KEY (range_id, sequence_number)`. Validada: True.
- `vulnerability_range_events_range_id_fkey`: `FOREIGN KEY (range_id) REFERENCES vulnerability_affected_ranges(id) ON DELETE RESTRICT`. Validada: True.
- `vulnerability_range_events_sequence_number_check`: `CHECK ((sequence_number > 0))`. Validada: True.
- `vulnerability_range_events_version_value_check`: `CHECK ((btrim(version_value) <> ''::text))`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX vulnerability_range_events_pkey ON public.vulnerability_range_events USING btree (range_id, sequence_number)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## vulnerability_references

Referencias/URLs de advisories como filas separadas.

Módulo: **vulnerability**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `vulnerability_id` | `uuid` | No | — | FK, U |
| `reference_type` | `text` | No | — |  |
| `url` | `text` | No | — | U |

Restricciones:

- `vulnerability_references_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `vulnerability_references_url_check`: `CHECK ((btrim(url) <> ''::text))`. Validada: True.
- `vulnerability_references_vulnerability_id_fkey`: `FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities(id) ON DELETE RESTRICT`. Validada: True.
- `vulnerability_references_vulnerability_id_url_key`: `UNIQUE (vulnerability_id, url)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX vulnerability_references_pkey ON public.vulnerability_references USING btree (id)`
- `CREATE UNIQUE INDEX vulnerability_references_vulnerability_id_url_key ON public.vulnerability_references USING btree (vulnerability_id, url)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## vulnerability_severities

Evaluaciones por fuente, método, fecha, score/vector y versión CVSS.

Módulo: **vulnerability**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK |
| `vulnerability_id` | `uuid` | No | — | FK, U |
| `source` | `text` | No | — | FK, U |
| `method` | `text` | No | — | U |
| `severity_code` | `text` | No | — | FK |
| `score` | `numeric(4,1)` | Sí | — |  |
| `vector` | `text` | Sí | — |  |
| `cvss_version` | `text` | Sí | — |  |
| `observed_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP | U |

Restricciones:

- `vulnerability_severities_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `vulnerability_severities_score_check`: `CHECK (((score >= (0)::numeric) AND (score <= (10)::numeric)))`. Validada: True.
- `vulnerability_severities_severity_code_fkey`: `FOREIGN KEY (severity_code) REFERENCES severity_levels(code) ON DELETE RESTRICT`. Validada: True.
- `vulnerability_severities_source_fkey`: `FOREIGN KEY (source) REFERENCES vulnerability_sources(code) ON DELETE RESTRICT`. Validada: True.
- `vulnerability_severities_vulnerability_id_fkey`: `FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities(id) ON DELETE RESTRICT`. Validada: True.
- `vulnerability_severities_vulnerability_id_source_method_obs_key`: `UNIQUE (vulnerability_id, source, method, observed_at)`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_vulnerability_severities_17acf6e5d0f7 ON public.vulnerability_severities USING btree (source)`
- `CREATE INDEX ix_fk_vulnerability_severities_b8f82c60f873 ON public.vulnerability_severities USING btree (severity_code)`
- `CREATE UNIQUE INDEX vulnerability_severities_pkey ON public.vulnerability_severities USING btree (id)`
- `CREATE UNIQUE INDEX vulnerability_severities_vulnerability_id_source_method_obs_key ON public.vulnerability_severities USING btree (vulnerability_id, source, method, observed_at)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## vulnerability_source_records

Payload fuente y hash con base RAW_BYTES o CANONICAL_JSON explícita.

Módulo: **vulnerability**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `vulnerability_id` | `uuid` | No | — | FK, U |
| `source` | `text` | No | — | FK, U |
| `fetched_at` | `timestamp with time zone` | No | — | U |
| `parser_version` | `text` | No | — |  |
| `payload` | `jsonb` | No | — |  |
| `sha256` | `text` | No | — | U |
| `hash_basis` | `text` | No | — |  |

Restricciones:

- `vulnerability_source_records_hash_basis_check`: `CHECK ((hash_basis = ANY (ARRAY['RAW_BYTES'::text, 'CANONICAL_JSON'::text])))`. Validada: True.
- `vulnerability_source_records_id_vulnerability_id_key`: `UNIQUE (id, vulnerability_id)`. Validada: True.
- `vulnerability_source_records_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `vulnerability_source_records_sha256_check`: `CHECK ((sha256 ~ '^[a-f0-9]{64}$'::text))`. Validada: True.
- `vulnerability_source_records_source_fkey`: `FOREIGN KEY (source) REFERENCES vulnerability_sources(code) ON DELETE RESTRICT`. Validada: True.
- `vulnerability_source_records_vulnerability_id_fkey`: `FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities(id) ON DELETE RESTRICT`. Validada: True.
- `vulnerability_source_records_vulnerability_id_source_fetche_key`: `UNIQUE (vulnerability_id, source, fetched_at, sha256)`. Validada: True.

Referencias entrantes: `finding_evidence` (source_record_id, vulnerability_id).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_vulnerability_source_records_d5ec48162714 ON public.vulnerability_source_records USING btree (source)`
- `CREATE UNIQUE INDEX vulnerability_source_records_id_vulnerability_id_key ON public.vulnerability_source_records USING btree (id, vulnerability_id)`
- `CREATE UNIQUE INDEX vulnerability_source_records_pkey ON public.vulnerability_source_records USING btree (id)`
- `CREATE UNIQUE INDEX vulnerability_source_records_vulnerability_id_source_fetche_key ON public.vulnerability_source_records USING btree (vulnerability_id, source, fetched_at, sha256)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## vulnerability_sources

Fuentes públicas de vulnerabilidades.

Módulo: **vulnerability**. Ámbito: **global**. RLS: False; FORCE: False.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `code` | `text` | No | — | PK |
| `name` | `text` | No | — |  |
| `base_url` | `text` | Sí | — |  |

Restricciones:

- `vulnerability_sources_pkey`: `PRIMARY KEY (code)`. Validada: True.

Referencias entrantes: `vulnerabilities` (source), `vulnerability_severities` (source), `vulnerability_source_records` (source).

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE UNIQUE INDEX vulnerability_sources_pkey ON public.vulnerability_sources USING btree (code)`

Retención/borrado: catálogo/identidad referenciada; RESTRICT evita borrar valores todavía utilizados.


## webhook_events

Recepciones idempotentes por proveedor/delivery; firma se verifica en el adaptador futuro.

Módulo: **integration**. Ámbito: **tenant**. RLS: True; FORCE: True.

| Columna | Tipo PostgreSQL | NULL | Default / generado | Claves |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | No | gen_random_uuid() | PK, U |
| `organization_id` | `uuid` | No | — | FK, U |
| `integration_id` | `uuid` | No | — | FK |
| `provider` | `text` | No | — | FK, U |
| `delivery_id` | `text` | No | — | U |
| `event_type` | `text` | No | — |  |
| `received_at` | `timestamp with time zone` | No | CURRENT_TIMESTAMP |  |
| `processed_at` | `timestamp with time zone` | Sí | — |  |
| `status` | `text` | No | 'PENDING'::text |  |
| `payload_hash` | `text` | No | — |  |
| `sanitized_payload` | `jsonb` | Sí | — |  |

Restricciones:

- `fk_webhook_provider`: `FOREIGN KEY (organization_id, integration_id, provider) REFERENCES integrations(organization_id, id, provider) ON DELETE RESTRICT`. Validada: True.
- `webhook_events_organization_id_id_key`: `UNIQUE (organization_id, id)`. Validada: True.
- `webhook_events_organization_id_integration_id_fkey`: `FOREIGN KEY (organization_id, integration_id) REFERENCES integrations(organization_id, id) ON DELETE RESTRICT`. Validada: True.
- `webhook_events_payload_hash_check`: `CHECK ((payload_hash ~ '^[a-f0-9]{64}$'::text))`. Validada: True.
- `webhook_events_pkey`: `PRIMARY KEY (id)`. Validada: True.
- `webhook_events_provider_delivery_id_key`: `UNIQUE (provider, delivery_id)`. Validada: True.
- `webhook_events_provider_fkey`: `FOREIGN KEY (provider) REFERENCES integration_providers(code) ON DELETE RESTRICT`. Validada: True.
- `webhook_events_status_check`: `CHECK ((status = ANY (ARRAY['PENDING'::text, 'PROCESSED'::text, 'FAILED'::text, 'IGNORED'::text])))`. Validada: True.

Referencias entrantes: ninguna.

Índices — UNIQUE/PK protegen identidad; los índices FK cubren búsquedas de referencias/prefijos y no duplican índices completos existentes:

- `CREATE INDEX ix_fk_webhook_events_276fbd77205d ON public.webhook_events USING btree (organization_id, integration_id, provider)`
- `CREATE INDEX ix_webhook_pending ON public.webhook_events USING btree (organization_id, received_at) WHERE (status = 'PENDING'::text)`
- `CREATE UNIQUE INDEX webhook_events_organization_id_id_key ON public.webhook_events USING btree (organization_id, id)`
- `CREATE UNIQUE INDEX webhook_events_pkey ON public.webhook_events USING btree (id)`
- `CREATE UNIQUE INDEX webhook_events_provider_delivery_id_key ON public.webhook_events USING btree (provider, delivery_id)`

Retención/borrado: sin borrado automático; aplicar políticas del tenant y resolver referencias antes de eliminar historia.

- Política `schema_migration_access`, roles dependency_migrator: USING `true`; CHECK `true`.
- Política `tenant_isolation`, roles public: USING `(organization_id = app_private.current_organization_id())`; CHECK `(organization_id = app_private.current_organization_id())`.
