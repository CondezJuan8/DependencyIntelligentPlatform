SELECT coalesce(jsonb_agg(jsonb_build_object(
 'name',c.relname,'rls',c.relrowsecurity,'force_rls',c.relforcerowsecurity,
 'owner',pg_get_userbyid(c.relowner),
 'columns',(SELECT jsonb_agg(jsonb_build_object('name',a.attname,'type',format_type(a.atttypid,a.atttypmod),'nullable',NOT a.attnotnull,'default',pg_get_expr(d.adbin,d.adrelid)) ORDER BY a.attnum)
  FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped),
 'constraints',(SELECT coalesce(jsonb_agg(jsonb_build_object('name',x.conname,'type',x.contype,'definition',pg_get_constraintdef(x.oid),'target',t.relname,'columns',
 (SELECT jsonb_agg(a.attname ORDER BY z.ordinality) FROM unnest(x.conkey) WITH ORDINALITY z(num,ordinality) JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum=z.num))), '[]'::jsonb) FROM pg_constraint x LEFT JOIN pg_class t ON t.oid=x.confrelid WHERE x.conrelid=c.oid),
 'indexes',(SELECT coalesce(jsonb_agg(indexdef),'[]'::jsonb) FROM pg_indexes i WHERE i.schemaname='public' AND i.tablename=c.relname),
 'policies',(SELECT coalesce(jsonb_agg(jsonb_build_object('name',p.policyname,'using',p.qual,'check',p.with_check)),'[]'::jsonb) FROM pg_policies p WHERE p.schemaname='public' AND p.tablename=c.relname)
) ORDER BY c.relname),'[]'::jsonb)
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind='r' AND c.relname<>'flyway_schema_history';
