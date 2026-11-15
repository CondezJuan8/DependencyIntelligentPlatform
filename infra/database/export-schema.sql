-- Structural metadata only. No business rows, passwords, URLs with credentials, or database names are exported.
SELECT jsonb_pretty(jsonb_build_object(
 'format_version', 1,
 'schema', 'public',
 'latest_migration', (SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1),
 'tables', (
  SELECT jsonb_agg(table_data ORDER BY table_data->>'name') FROM (
   SELECT jsonb_build_object(
    'name', relation.relname, 'comment', obj_description(relation.oid, 'pg_class'),
    'rls', relation.relrowsecurity, 'force_rls', relation.relforcerowsecurity,
    'columns', (SELECT jsonb_agg(jsonb_build_object('name', attribute.attname,
        'type', format_type(attribute.atttypid, attribute.atttypmod), 'nullable', NOT attribute.attnotnull,
        'default', pg_get_expr(definition.adbin, definition.adrelid), 'generated', attribute.attgenerated <> '') ORDER BY attribute.attnum)
       FROM pg_attribute attribute LEFT JOIN pg_attrdef definition ON definition.adrelid=attribute.attrelid AND definition.adnum=attribute.attnum
       WHERE attribute.attrelid=relation.oid AND attribute.attnum>0 AND NOT attribute.attisdropped),
    'constraints', (SELECT coalesce(jsonb_agg(jsonb_build_object('name', key.conname, 'kind', key.contype,
        'definition', pg_get_constraintdef(key.oid), 'validated', key.convalidated,
        'columns', (SELECT jsonb_agg(attribute.attname ORDER BY key_column.ordinal) FROM unnest(key.conkey) WITH ORDINALITY key_column(number,ordinal)
          JOIN pg_attribute attribute ON attribute.attrelid=key.conrelid AND attribute.attnum=key_column.number),
        'referenced_table', parent.relname,
        'referenced_columns', (SELECT jsonb_agg(attribute.attname ORDER BY key_column.ordinal) FROM unnest(key.confkey) WITH ORDINALITY key_column(number,ordinal)
          JOIN pg_attribute attribute ON attribute.attrelid=key.confrelid AND attribute.attnum=key_column.number),
        'on_delete', CASE key.confdeltype WHEN 'r' THEN 'RESTRICT' WHEN 'a' THEN 'NO ACTION' WHEN 'c' THEN 'CASCADE' WHEN 'n' THEN 'SET NULL' WHEN 'd' THEN 'SET DEFAULT' ELSE NULL END)
        ORDER BY key.conname), '[]') FROM pg_constraint key LEFT JOIN pg_class parent ON parent.oid=key.confrelid
       WHERE key.conrelid=relation.oid AND key.contype IN ('p','u','f','c')),
    'indexes', (SELECT coalesce(jsonb_agg(jsonb_build_object('name', indexname, 'definition', indexdef) ORDER BY indexname), '[]')
       FROM pg_indexes WHERE schemaname='public' AND tablename=relation.relname),
    'policies', (SELECT coalesce(jsonb_agg(jsonb_build_object('name', policyname, 'roles', roles, 'command', cmd, 'using', qual, 'check', with_check) ORDER BY policyname), '[]')
       FROM pg_policies WHERE schemaname='public' AND tablename=relation.relname),
    'triggers', (SELECT coalesce(jsonb_agg(pg_get_triggerdef(trigger.oid) ORDER BY trigger.tgname), '[]')
       FROM pg_trigger trigger WHERE trigger.tgrelid=relation.oid AND NOT trigger.tgisinternal)
   ) AS table_data
   FROM pg_class relation JOIN pg_namespace namespace ON namespace.oid=relation.relnamespace
   WHERE namespace.nspname='public' AND relation.relkind='r' AND relation.relname <> 'flyway_schema_history'
  ) tables
 ),
 'capability_roles', (SELECT jsonb_agg(jsonb_build_object('name', rolname, 'login', rolcanlogin, 'superuser', rolsuper, 'bypass_rls', rolbypassrls, 'create_db', rolcreatedb, 'create_role', rolcreaterole) ORDER BY rolname)
    FROM pg_roles WHERE rolname IN ('dependency_runtime','dependency_migrator','dependency_catalog_writer','dependency_audit_admin','dependency_app'))
));
