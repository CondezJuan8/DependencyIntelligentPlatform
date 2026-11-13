\set ON_ERROR_STOP on
SELECT to_regclass('public.documents') IS NOT NULL AS future_tests,
       to_regclass('public.document_chunk_embeddings') IS NOT NULL AS vector_tests \gset
CREATE FUNCTION pg_temp.assert_true(ok boolean, label text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN IF NOT coalesce(ok,false) THEN RAISE EXCEPTION 'FAIL: %',label; END IF; RAISE NOTICE 'PASS: %',label; END $$;
CREATE FUNCTION pg_temp.expect_failure(statement text, expected_code text, label text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE actual_code text;
BEGIN
 BEGIN EXECUTE statement; EXCEPTION WHEN OTHERS THEN
  GET STACKED DIAGNOSTICS actual_code=RETURNED_SQLSTATE;
  IF actual_code<>expected_code THEN RAISE EXCEPTION 'FAIL: %, expected %, got %',label,expected_code,actual_code; END IF;
  RAISE NOTICE 'PASS: %',label; RETURN;
 END;
 RAISE EXCEPTION 'FAIL: % (statement unexpectedly succeeded)',label;
END $$;
\if :{?future_tests}
\else
\set future_tests false
\endif
BEGIN;
INSERT INTO organizations (id,name,slug) VALUES ('10e4c11d-6888-5d03-a540-766b38437f71','Fixture A','fixture-a');
INSERT INTO users (id,email_normalized,display_name) VALUES ('448f3fb1-c2a7-505d-97d3-d858000b53da','fixture-a@example.invalid','A');
INSERT INTO organization_memberships (id,organization_id,user_id) VALUES ('634382e7-88e8-564a-905a-1fcab89a0e96','10e4c11d-6888-5d03-a540-766b38437f71','448f3fb1-c2a7-505d-97d3-d858000b53da');
INSERT INTO integrations (id,organization_id,provider_id,external_account_id,external_account_login) VALUES ('ac75ad82-a8f8-5a0b-b6e3-a09e425cc266','10e4c11d-6888-5d03-a540-766b38437f71',(SELECT id FROM integration_providers WHERE code='github'),'A','A');
INSERT INTO github_installations (id,organization_id,integration_id,installation_id,app_id) VALUES ('87612217-11ca-5563-9c93-48107465ff36','10e4c11d-6888-5d03-a540-766b38437f71','ac75ad82-a8f8-5a0b-b6e3-a09e425cc266',900065,123);
INSERT INTO repositories (id,organization_id,integration_id,external_repository_id,owner_login,name,visibility) VALUES ('424be974-6f4e-50da-b310-5925639f54c2','10e4c11d-6888-5d03-a540-766b38437f71','ac75ad82-a8f8-5a0b-b6e3-a09e425cc266','A','A','repo','PRIVATE');
INSERT INTO scan_profiles (id,organization_id,name,configuration_version) VALUES ('901e9e8c-d3e5-555f-8f02-a8d718373e43','10e4c11d-6888-5d03-a540-766b38437f71','fixture','1');
INSERT INTO components (id,organization_id,package_ecosystem_id,name,normalized_name) VALUES ('06722d69-a67f-5497-bc31-7766af03f5e8','10e4c11d-6888-5d03-a540-766b38437f71',(SELECT id FROM package_ecosystems WHERE code='npm'),'example','example');
INSERT INTO repository_commits (id,organization_id,repository_id,commit_sha) VALUES ('f33e174d-121a-5ae4-ab54-35d762512404','10e4c11d-6888-5d03-a540-766b38437f71','424be974-6f4e-50da-b310-5925639f54c2','1111111111111111111111111111111111111111');
INSERT INTO background_jobs (id,organization_id,job_type_id,deduplication_key) VALUES ('7801bf99-05f2-5eb3-8aac-1c021ba69de1','10e4c11d-6888-5d03-a540-766b38437f71',(SELECT id FROM job_types WHERE code='SCAN'),'A1');
INSERT INTO scans (id,organization_id,repository_id,commit_id,scan_profile_id,job_id,scanner_tool_id,scanner_version,trigger_type,request_key) VALUES ('03ba3027-623d-56d3-95b9-9fb2548be83f','10e4c11d-6888-5d03-a540-766b38437f71','424be974-6f4e-50da-b310-5925639f54c2','f33e174d-121a-5ae4-ab54-35d762512404','901e9e8c-d3e5-555f-8f02-a8d718373e43','7801bf99-05f2-5eb3-8aac-1c021ba69de1',(SELECT id FROM scanner_tools WHERE code='cdxgen'),'fixture-v1','MANUAL','A1');
INSERT INTO component_versions (id,organization_id,component_id,version) VALUES ('79e2f547-66f7-562e-9837-534c5a0f562b','10e4c11d-6888-5d03-a540-766b38437f71','06722d69-a67f-5497-bc31-7766af03f5e8','1.0.0');
INSERT INTO scan_components (id,organization_id,scan_id,component_version_id,bom_ref) VALUES ('6fc86cf9-fa91-507f-8fce-6b8e0c3a8269','10e4c11d-6888-5d03-a540-766b38437f71','03ba3027-623d-56d3-95b9-9fb2548be83f','79e2f547-66f7-562e-9837-534c5a0f562b','pkg-A1');
INSERT INTO repository_commits (id,organization_id,repository_id,commit_sha) VALUES ('5d4811d5-64bc-548f-811a-2a4b2be4d12f','10e4c11d-6888-5d03-a540-766b38437f71','424be974-6f4e-50da-b310-5925639f54c2','2222222222222222222222222222222222222222');
INSERT INTO background_jobs (id,organization_id,job_type_id,deduplication_key) VALUES ('b8817ef6-b7b3-5393-8d0c-d62c6ed9fedc','10e4c11d-6888-5d03-a540-766b38437f71',(SELECT id FROM job_types WHERE code='SCAN'),'A2');
INSERT INTO scans (id,organization_id,repository_id,commit_id,scan_profile_id,job_id,scanner_tool_id,scanner_version,trigger_type,request_key) VALUES ('aec69e0c-5fe8-5c21-83d5-cd1ddf3ca7bb','10e4c11d-6888-5d03-a540-766b38437f71','424be974-6f4e-50da-b310-5925639f54c2','5d4811d5-64bc-548f-811a-2a4b2be4d12f','901e9e8c-d3e5-555f-8f02-a8d718373e43','b8817ef6-b7b3-5393-8d0c-d62c6ed9fedc',(SELECT id FROM scanner_tools WHERE code='cdxgen'),'fixture-v1','MANUAL','A2');
INSERT INTO component_versions (id,organization_id,component_id,version) VALUES ('71c28abe-298f-5b00-8fb8-59f64e18ec83','10e4c11d-6888-5d03-a540-766b38437f71','06722d69-a67f-5497-bc31-7766af03f5e8','2.0.0');
INSERT INTO scan_components (id,organization_id,scan_id,component_version_id,bom_ref) VALUES ('79f00ea1-0d6b-5726-a87b-516594c94989','10e4c11d-6888-5d03-a540-766b38437f71','aec69e0c-5fe8-5c21-83d5-cd1ddf3ca7bb','71c28abe-298f-5b00-8fb8-59f64e18ec83','pkg-A2');
INSERT INTO scan_components (id,organization_id,scan_id,component_version_id,bom_ref) VALUES ('767a8d60-c61f-5211-a382-4832d8c9941f','10e4c11d-6888-5d03-a540-766b38437f71','03ba3027-623d-56d3-95b9-9fb2548be83f','71c28abe-298f-5b00-8fb8-59f64e18ec83','extra-A');
INSERT INTO dependency_edges (id,organization_id,scan_id,source_scan_component_id,target_scan_component_id,relationship_type_id) VALUES ('c0d8eda2-3476-5d94-9dd7-8e7b6e3b22d8','10e4c11d-6888-5d03-a540-766b38437f71','03ba3027-623d-56d3-95b9-9fb2548be83f','6fc86cf9-fa91-507f-8fce-6b8e0c3a8269','767a8d60-c61f-5211-a382-4832d8c9941f',(SELECT id FROM dependency_relationship_types WHERE code='depends_on'));
INSERT INTO organizations (id,name,slug) VALUES ('4f06f825-f5a3-53e8-822d-698d9a0405db','Fixture B','fixture-b');
INSERT INTO users (id,email_normalized,display_name) VALUES ('6d797909-35e3-5932-9337-ceaf1a144d02','fixture-b@example.invalid','B');
INSERT INTO organization_memberships (id,organization_id,user_id) VALUES ('95d1876c-e639-546f-9b20-d03f25a249cc','4f06f825-f5a3-53e8-822d-698d9a0405db','6d797909-35e3-5932-9337-ceaf1a144d02');
INSERT INTO integrations (id,organization_id,provider_id,external_account_id,external_account_login) VALUES ('aac0834c-9209-5342-9542-21320c11d764','4f06f825-f5a3-53e8-822d-698d9a0405db',(SELECT id FROM integration_providers WHERE code='github'),'B','B');
INSERT INTO github_installations (id,organization_id,integration_id,installation_id,app_id) VALUES ('602d6e48-84e7-5f10-92c9-fd99ae2e12e2','4f06f825-f5a3-53e8-822d-698d9a0405db','aac0834c-9209-5342-9542-21320c11d764',900066,123);
INSERT INTO repositories (id,organization_id,integration_id,external_repository_id,owner_login,name,visibility) VALUES ('dfa0355a-5fce-53b2-931b-16e3e0e4f60b','4f06f825-f5a3-53e8-822d-698d9a0405db','aac0834c-9209-5342-9542-21320c11d764','B','B','repo','PRIVATE');
INSERT INTO scan_profiles (id,organization_id,name,configuration_version) VALUES ('534a4363-d20f-5e2f-bff9-d42dfe8d9f94','4f06f825-f5a3-53e8-822d-698d9a0405db','fixture','1');
INSERT INTO components (id,organization_id,package_ecosystem_id,name,normalized_name) VALUES ('6f4a569b-4445-5e4c-b1b2-8972136302d8','4f06f825-f5a3-53e8-822d-698d9a0405db',(SELECT id FROM package_ecosystems WHERE code='npm'),'example','example');
INSERT INTO repository_commits (id,organization_id,repository_id,commit_sha) VALUES ('e0057647-e673-5f9d-b230-e3dbdf7f2b0c','4f06f825-f5a3-53e8-822d-698d9a0405db','dfa0355a-5fce-53b2-931b-16e3e0e4f60b','1111111111111111111111111111111111111111');
INSERT INTO background_jobs (id,organization_id,job_type_id,deduplication_key) VALUES ('fdbee935-b9a1-5e6e-b9bc-08ace343edab','4f06f825-f5a3-53e8-822d-698d9a0405db',(SELECT id FROM job_types WHERE code='SCAN'),'B1');
INSERT INTO scans (id,organization_id,repository_id,commit_id,scan_profile_id,job_id,scanner_tool_id,scanner_version,trigger_type,request_key) VALUES ('ad17ab2b-432b-5eb6-81aa-72fae56fd64c','4f06f825-f5a3-53e8-822d-698d9a0405db','dfa0355a-5fce-53b2-931b-16e3e0e4f60b','e0057647-e673-5f9d-b230-e3dbdf7f2b0c','534a4363-d20f-5e2f-bff9-d42dfe8d9f94','fdbee935-b9a1-5e6e-b9bc-08ace343edab',(SELECT id FROM scanner_tools WHERE code='cdxgen'),'fixture-v1','MANUAL','B1');
INSERT INTO component_versions (id,organization_id,component_id,version) VALUES ('3e2f78d9-f9c1-5097-83e1-8b7a078a99c5','4f06f825-f5a3-53e8-822d-698d9a0405db','6f4a569b-4445-5e4c-b1b2-8972136302d8','1.0.0');
INSERT INTO scan_components (id,organization_id,scan_id,component_version_id,bom_ref) VALUES ('0a5757f5-6348-5296-af79-c933034e1e0b','4f06f825-f5a3-53e8-822d-698d9a0405db','ad17ab2b-432b-5eb6-81aa-72fae56fd64c','3e2f78d9-f9c1-5097-83e1-8b7a078a99c5','pkg-B1');
INSERT INTO repository_commits (id,organization_id,repository_id,commit_sha) VALUES ('3eb6c1f7-b9c7-56a3-80ee-473f87999a9f','4f06f825-f5a3-53e8-822d-698d9a0405db','dfa0355a-5fce-53b2-931b-16e3e0e4f60b','2222222222222222222222222222222222222222');
INSERT INTO background_jobs (id,organization_id,job_type_id,deduplication_key) VALUES ('63c3452f-e154-5ec3-89bb-79b5cc964ff6','4f06f825-f5a3-53e8-822d-698d9a0405db',(SELECT id FROM job_types WHERE code='SCAN'),'B2');
INSERT INTO scans (id,organization_id,repository_id,commit_id,scan_profile_id,job_id,scanner_tool_id,scanner_version,trigger_type,request_key) VALUES ('b1d30ff4-1bd7-5406-92c7-9e0fe5ab0e19','4f06f825-f5a3-53e8-822d-698d9a0405db','dfa0355a-5fce-53b2-931b-16e3e0e4f60b','3eb6c1f7-b9c7-56a3-80ee-473f87999a9f','534a4363-d20f-5e2f-bff9-d42dfe8d9f94','63c3452f-e154-5ec3-89bb-79b5cc964ff6',(SELECT id FROM scanner_tools WHERE code='cdxgen'),'fixture-v1','MANUAL','B2');
INSERT INTO component_versions (id,organization_id,component_id,version) VALUES ('f9845df3-a1e0-5599-8c7d-0d87581361c6','4f06f825-f5a3-53e8-822d-698d9a0405db','6f4a569b-4445-5e4c-b1b2-8972136302d8','2.0.0');
INSERT INTO scan_components (id,organization_id,scan_id,component_version_id,bom_ref) VALUES ('1259d96b-d9b3-58b0-9316-da18ccc7f03f','4f06f825-f5a3-53e8-822d-698d9a0405db','b1d30ff4-1bd7-5406-92c7-9e0fe5ab0e19','f9845df3-a1e0-5599-8c7d-0d87581361c6','pkg-B2');
INSERT INTO scan_components (id,organization_id,scan_id,component_version_id,bom_ref) VALUES ('5c0354c3-b3d3-5eed-8a32-a54ac3c1c2a2','4f06f825-f5a3-53e8-822d-698d9a0405db','ad17ab2b-432b-5eb6-81aa-72fae56fd64c','f9845df3-a1e0-5599-8c7d-0d87581361c6','extra-B');
INSERT INTO dependency_edges (id,organization_id,scan_id,source_scan_component_id,target_scan_component_id,relationship_type_id) VALUES ('294a3a20-eb7a-59e2-a7f2-968bef13462e','4f06f825-f5a3-53e8-822d-698d9a0405db','ad17ab2b-432b-5eb6-81aa-72fae56fd64c','0a5757f5-6348-5296-af79-c933034e1e0b','5c0354c3-b3d3-5eed-8a32-a54ac3c1c2a2',(SELECT id FROM dependency_relationship_types WHERE code='depends_on'));
INSERT INTO organization_memberships (id,organization_id,user_id) VALUES ('fa8dbbaf-7fb5-55ea-a1cd-f38924482c72','4f06f825-f5a3-53e8-822d-698d9a0405db','448f3fb1-c2a7-505d-97d3-d858000b53da');
INSERT INTO repositories (id,organization_id,integration_id,external_repository_id,owner_login,name,visibility) VALUES ('5c8df0c5-e80e-53e9-bc36-63b624ec609a','10e4c11d-6888-5d03-a540-766b38437f71','ac75ad82-a8f8-5a0b-b6e3-a09e425cc266','Aother','A','other','PRIVATE');
INSERT INTO vulnerabilities (id,vulnerability_source_id,external_id) VALUES ('e47ca149-6f2c-5ab8-a5a6-48cd0a5b76a8',(SELECT id FROM vulnerability_sources WHERE code='OSV'),'FIXTURE-ADVISORY');
INSERT INTO vulnerability_aliases (id,vulnerability_id,alias_type,value) VALUES ('dc944962-dbf1-5cec-9e99-cf8b93b1ed4e','e47ca149-6f2c-5ab8-a5a6-48cd0a5b76a8','FIXTURE','CVE-FIXTURE');
INSERT INTO vulnerability_aliases (id,vulnerability_id,alias_type,value) VALUES ('ac8cfb4f-b0e1-5777-a9cb-47cfe433bb2d','e47ca149-6f2c-5ab8-a5a6-48cd0a5b76a8','FIXTURE','GHSA-FIXTURE');
INSERT INTO vulnerability_references (id,vulnerability_id,reference_type,url) VALUES ('a1860b5b-acb6-5b44-9b62-15ffece6c39c','e47ca149-6f2c-5ab8-a5a6-48cd0a5b76a8','ADVISORY','https://example.invalid/advisory');
INSERT INTO vulnerability_affected_packages (id,vulnerability_id,package_ecosystem_id,normalized_name) VALUES ('ccdcf67e-f68f-553f-8bb1-01f65fb85973','e47ca149-6f2c-5ab8-a5a6-48cd0a5b76a8',(SELECT id FROM package_ecosystems WHERE code='npm'),'example');
INSERT INTO vulnerability_affected_ranges (id,affected_package_id,range_type) VALUES ('6b30631b-e87d-50ea-83ca-c778cb7a1716','ccdcf67e-f68f-553f-8bb1-01f65fb85973','SEMVER');
INSERT INTO vulnerability_range_events (id,affected_range_id,ordinal,event_type,version) VALUES ('f9544b75-07b0-53cc-899b-94857dabac58','6b30631b-e87d-50ea-83ca-c778cb7a1716',0,'INTRODUCED','0');
INSERT INTO vulnerability_range_events (id,affected_range_id,ordinal,event_type,version) VALUES ('b83c1d07-789c-5174-8e02-dd467c420fd8','6b30631b-e87d-50ea-83ca-c778cb7a1716',1,'FIXED','2.0.0');
INSERT INTO vulnerability_fixed_versions (id,affected_package_id,version) VALUES ('9f7ffeec-76e7-581d-a3fe-daa624709572','ccdcf67e-f68f-553f-8bb1-01f65fb85973','2.0.0');
INSERT INTO vulnerability_source_records (id,vulnerability_id,source_id,payload,sha256,fetched_at,parser_version) VALUES ('006c4525-4a6e-5fe3-9a85-1ba9250d9c43','e47ca149-6f2c-5ab8-a5a6-48cd0a5b76a8',(SELECT id FROM vulnerability_sources WHERE code='OSV'),'{}'::jsonb,'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',now(),'fixture-v1');
INSERT INTO scan_vulnerability_findings (id,organization_id,scan_component_id,vulnerability_id,source_record_id,match_status) VALUES ('b32acf8c-ca3a-5379-ab56-4a905fddfcb1','10e4c11d-6888-5d03-a540-766b38437f71','6fc86cf9-fa91-507f-8fce-6b8e0c3a8269','e47ca149-6f2c-5ab8-a5a6-48cd0a5b76a8','006c4525-4a6e-5fe3-9a85-1ba9250d9c43','MATCHED');
INSERT INTO finding_evidence (id,organization_id,finding_id,matched_range_id,observed_version,package_identifier,method_version,explanation) VALUES ('e88b9689-6501-51aa-8dbe-b42d4237ab31','10e4c11d-6888-5d03-a540-766b38437f71','b32acf8c-ca3a-5379-ab56-4a905fddfcb1','6b30631b-e87d-50ea-83ca-c778cb7a1716','1.0.0','pkg:npm/example','fixture-v1','Fixture only');
INSERT INTO scan_vulnerability_findings (id,organization_id,scan_component_id,vulnerability_id,source_record_id,match_status) VALUES ('a72d9e73-4e62-5e98-9f86-79f904dcd57a','4f06f825-f5a3-53e8-822d-698d9a0405db','0a5757f5-6348-5296-af79-c933034e1e0b','e47ca149-6f2c-5ab8-a5a6-48cd0a5b76a8','006c4525-4a6e-5fe3-9a85-1ba9250d9c43','MATCHED');
INSERT INTO finding_evidence (id,organization_id,finding_id,matched_range_id,observed_version,package_identifier,method_version,explanation) VALUES ('80ae1341-31a0-571f-b2cb-1bfad5c18666','4f06f825-f5a3-53e8-822d-698d9a0405db','a72d9e73-4e62-5e98-9f86-79f904dcd57a','6b30631b-e87d-50ea-83ca-c778cb7a1716','1.0.0','pkg:npm/example','fixture-v1','Fixture only');
INSERT INTO audit_events (id,organization_id,actor_membership_id,action,resource_type,resource_reference,result) VALUES ('86541b65-c416-5afd-a42a-ec22b3c86770','10e4c11d-6888-5d03-a540-766b38437f71','634382e7-88e8-564a-905a-1fcab89a0e96','fixture','repository','424be974-6f4e-50da-b310-5925639f54c2','SUCCESS');
INSERT INTO report_definitions (id,report_type_id,name) VALUES ('e9a65a09-60c3-540c-b564-da50c27b79b9',(SELECT id FROM report_types WHERE code='SBOM'),'Fixture SBOM');
INSERT INTO background_jobs (id,organization_id,job_type_id,deduplication_key) VALUES ('b3123a5f-7770-52f0-993c-ea6815b4adaa','10e4c11d-6888-5d03-a540-766b38437f71',(SELECT id FROM job_types WHERE code='REPORT'),'reportfixture');
INSERT INTO report_jobs (id,organization_id,definition_id,format_id,requested_by_membership_id,job_id) VALUES ('7fd73eb8-a4f2-5f4a-9a5f-27055acd325d','10e4c11d-6888-5d03-a540-766b38437f71','e9a65a09-60c3-540c-b564-da50c27b79b9',(SELECT id FROM report_formats WHERE code='JSON'),'634382e7-88e8-564a-905a-1fcab89a0e96','b3123a5f-7770-52f0-993c-ea6815b4adaa');
INSERT INTO report_job_scans (id,organization_id,report_job_id,scan_id) VALUES ('b125ab9e-5f38-522b-87e9-a3a73999168f','10e4c11d-6888-5d03-a540-766b38437f71','7fd73eb8-a4f2-5f4a-9a5f-27055acd325d','03ba3027-623d-56d3-95b9-9fb2548be83f');
INSERT INTO report_artifacts (id,organization_id,report_job_id,sha256,size_bytes,content_type,storage_reference) VALUES ('87f5aed8-6fd1-5588-947f-626be2991ea7','10e4c11d-6888-5d03-a540-766b38437f71','7fd73eb8-a4f2-5f4a-9a5f-27055acd325d','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',1,'application/json','fixture://report');
\if :future_tests
\ir verify-future.sql
\endif
SET LOCAL ROLE dependency_app;
SELECT pg_temp.assert_true((SELECT NOT rolsuper AND NOT rolbypassrls AND NOT rolcreaterole FROM pg_roles WHERE rolname=current_user),'runtime role has no administrative bypass');
SELECT pg_temp.assert_true((SELECT count(*)=0 FROM repositories),'missing context fails closed');
SELECT set_config('app.organization_id','10e4c11d-6888-5d03-a540-766b38437f71',true);
SELECT pg_temp.assert_true((SELECT count(*)=2 FROM repositories),'A reads only its two repositories');
SELECT pg_temp.assert_true((SELECT count(*)=1 FROM users),'global identity reads restricted to active tenant membership');
SELECT pg_temp.assert_true((SELECT count(*)=0 FROM repositories WHERE id='dfa0355a-5fce-53b2-931b-16e3e0e4f60b'),'A cannot read B');
UPDATE repositories SET name='forbidden' WHERE id='dfa0355a-5fce-53b2-931b-16e3e0e4f60b';
DELETE FROM repositories WHERE id='dfa0355a-5fce-53b2-931b-16e3e0e4f60b';
SELECT pg_temp.expect_failure('INSERT INTO repositories (id,organization_id,integration_id,external_repository_id,owner_login,name,visibility) VALUES (''4753a8c6-675e-52ed-ab98-94922de245c8'',''4f06f825-f5a3-53e8-822d-698d9a0405db'',''aac0834c-9209-5342-9542-21320c11d764'',''bad'',''B'',''bad'',''PRIVATE'')','42501','A cannot insert rows belonging to B');
SELECT pg_temp.expect_failure('INSERT INTO repositories (id,organization_id,integration_id,external_repository_id,owner_login,name,visibility) VALUES (''f8941400-f6f3-5200-95f0-1bf0a79ea0bd'',''10e4c11d-6888-5d03-a540-766b38437f71'',''aac0834c-9209-5342-9542-21320c11d764'',''bad'',''A'',''bad'',''PRIVATE'')','23503','composite FK blocks foreign integration');
SELECT pg_temp.expect_failure('INSERT INTO organization_memberships (id,organization_id,user_id) VALUES (''f2ef911c-761f-53ba-83fb-05a6d768491f'',''10e4c11d-6888-5d03-a540-766b38437f71'',''448f3fb1-c2a7-505d-97d3-d858000b53da'')','23505','duplicate membership rejected');
INSERT INTO membership_roles (id,organization_id,membership_id,role_id) VALUES ('cef20084-0f51-59d9-9821-ffe22277fffc','10e4c11d-6888-5d03-a540-766b38437f71','634382e7-88e8-564a-905a-1fcab89a0e96',(SELECT id FROM roles WHERE code='OWNER'));
SELECT pg_temp.expect_failure('INSERT INTO membership_roles (id,organization_id,membership_id,role_id) VALUES (''ae822083-8343-5fe6-af39-9e4c94e48102'',''10e4c11d-6888-5d03-a540-766b38437f71'',''634382e7-88e8-564a-905a-1fcab89a0e96'',(SELECT id FROM roles WHERE code=''OWNER''))','23505','duplicate membership role rejected');
INSERT INTO background_jobs (id,organization_id,job_type_id,deduplication_key) VALUES ('95761c21-846d-5fb5-82c0-ebf4894e6d87','10e4c11d-6888-5d03-a540-766b38437f71',(SELECT id FROM job_types WHERE code='SCAN'),'crossjob');
SELECT pg_temp.expect_failure('INSERT INTO scans (id,organization_id,repository_id,commit_id,scan_profile_id,job_id,scanner_tool_id,scanner_version,trigger_type,request_key) VALUES (''ec32108a-086e-5a78-9fd0-6b56a189a6bb'',''10e4c11d-6888-5d03-a540-766b38437f71'',''dfa0355a-5fce-53b2-931b-16e3e0e4f60b'',''e0057647-e673-5f9d-b230-e3dbdf7f2b0c'',''901e9e8c-d3e5-555f-8f02-a8d718373e43'',''95761c21-846d-5fb5-82c0-ebf4894e6d87'',(SELECT id FROM scanner_tools WHERE code=''cdxgen''),''1'',''MANUAL'',''bad'')','23503','scan cannot reference another tenant repository');
SELECT pg_temp.expect_failure('INSERT INTO dependency_edges (id,organization_id,scan_id,source_scan_component_id,target_scan_component_id,relationship_type_id) VALUES (''5e252615-b143-5a97-8237-e2ce794f7076'',''10e4c11d-6888-5d03-a540-766b38437f71'',''03ba3027-623d-56d3-95b9-9fb2548be83f'',''6fc86cf9-fa91-507f-8fce-6b8e0c3a8269'',''79f00ea1-0d6b-5726-a87b-516594c94989'',(SELECT id FROM dependency_relationship_types WHERE code=''depends_on''))','23503','edge cannot join two scans');
SELECT pg_temp.expect_failure('INSERT INTO dependency_edges (id,organization_id,scan_id,source_scan_component_id,target_scan_component_id,relationship_type_id) VALUES (''017a7093-c336-53fb-9e65-15683cbdebbc'',''10e4c11d-6888-5d03-a540-766b38437f71'',''03ba3027-623d-56d3-95b9-9fb2548be83f'',''6fc86cf9-fa91-507f-8fce-6b8e0c3a8269'',''0a5757f5-6348-5296-af79-c933034e1e0b'',(SELECT id FROM dependency_relationship_types WHERE code=''depends_on''))','23503','edge cannot join tenants');
SELECT pg_temp.expect_failure('INSERT INTO scan_vulnerability_findings (id,organization_id,scan_component_id,vulnerability_id,source_record_id,match_status) VALUES (''7ce1a382-a402-5c51-8c07-06da1d1f6039'',''10e4c11d-6888-5d03-a540-766b38437f71'',''0a5757f5-6348-5296-af79-c933034e1e0b'',''e47ca149-6f2c-5ab8-a5a6-48cd0a5b76a8'',''006c4525-4a6e-5fe3-9a85-1ba9250d9c43'',''MATCHED'')','23503','finding cannot refer to B occurrence');
INSERT INTO scan_comparisons (id,organization_id,repository_id,base_scan_id,target_scan_id,method_version) VALUES ('f20e789e-1715-5877-a348-e680834cf419','10e4c11d-6888-5d03-a540-766b38437f71','424be974-6f4e-50da-b310-5925639f54c2','03ba3027-623d-56d3-95b9-9fb2548be83f','aec69e0c-5fe8-5c21-83d5-cd1ddf3ca7bb','fixture-v1');
SELECT pg_temp.expect_failure('INSERT INTO scan_comparisons (id,organization_id,repository_id,base_scan_id,target_scan_id,method_version) VALUES (''00e8c06a-d1cf-5a69-9158-cdd89228c06c'',''10e4c11d-6888-5d03-a540-766b38437f71'',''5c8df0c5-e80e-53e9-bc36-63b624ec609a'',''03ba3027-623d-56d3-95b9-9fb2548be83f'',''aec69e0c-5fe8-5c21-83d5-cd1ddf3ca7bb'',''bad-method'')','23503','comparison cannot cross repositories');
SELECT pg_temp.expect_failure('INSERT INTO scan_comparisons (id,organization_id,repository_id,base_scan_id,target_scan_id,method_version) VALUES (''2b4c1013-0e4a-53e3-bc6d-0b92b900402b'',''10e4c11d-6888-5d03-a540-766b38437f71'',''424be974-6f4e-50da-b310-5925639f54c2'',''03ba3027-623d-56d3-95b9-9fb2548be83f'',''03ba3027-623d-56d3-95b9-9fb2548be83f'',''fixture-v1'')','23514','comparison cannot compare a scan to itself');
INSERT INTO scan_component_changes (id,organization_id,comparison_id,component_id,old_version_id,new_version_id,change_type,evidence) VALUES ('e593639f-6537-5796-9217-327acaba7d22','10e4c11d-6888-5d03-a540-766b38437f71','f20e789e-1715-5877-a348-e680834cf419','06722d69-a67f-5497-bc31-7766af03f5e8','79e2f547-66f7-562e-9837-534c5a0f562b','71c28abe-298f-5b00-8fb8-59f64e18ec83','UPDATED','Fixture diff');
SELECT pg_temp.expect_failure('UPDATE audit_events SET action=''mutated'' WHERE id=''86541b65-c416-5afd-a42a-ec22b3c86770''','42501','runtime audit is append-only');
SELECT pg_temp.expect_failure('CREATE TABLE public.forbidden_runtime_ddl (id integer)','42501','runtime cannot create schema objects');
SELECT pg_temp.expect_failure('UPDATE scans SET status=''COMPLETED'' WHERE id=''03ba3027-623d-56d3-95b9-9fb2548be83f''','23514','invalid scan state transition rejected');
UPDATE scans SET status='RUNNING',started_at=now() WHERE id='03ba3027-623d-56d3-95b9-9fb2548be83f';
UPDATE scans SET status='COMPLETED',finished_at=now() WHERE id='03ba3027-623d-56d3-95b9-9fb2548be83f';
SELECT set_config('app.organization_id','4f06f825-f5a3-53e8-822d-698d9a0405db',true);
SELECT pg_temp.assert_true((SELECT name='repo' FROM repositories WHERE id='dfa0355a-5fce-53b2-931b-16e3e0e4f60b'),'A updates/deletes did not modify B');
SELECT pg_temp.assert_true((SELECT count(*)=2 FROM organization_memberships),'one user can belong to A and B');
RESET ROLE;
SAVEPOINT tenant_scope;
SELECT set_config('app.organization_id','10e4c11d-6888-5d03-a540-766b38437f71',true);
ROLLBACK TO SAVEPOINT tenant_scope;
SELECT pg_temp.assert_true((SELECT app.current_organization_id()='4f06f825-f5a3-53e8-822d-698d9a0405db'::uuid),'nested rollback restores tenant context');
ROLLBACK;
SET ROLE dependency_app;
SELECT pg_temp.assert_true((SELECT app.current_organization_id() IS NULL),'transaction completion clears tenant context on same connection');
SELECT pg_temp.assert_true((SELECT count(*)=0 FROM repositories),'connection reuse without context cannot read previous tenant');
SELECT pg_temp.expect_failure('INSERT INTO organizations(name,slug) VALUES (''No context'',''missing-context'')','42501','missing context cannot create a tenant');
RESET ROLE;

SELECT pg_temp.assert_true((SELECT count(*)=0 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relkind='r' AND EXISTS (SELECT FROM pg_attribute a WHERE a.attrelid=c.oid AND a.attname='organization_id')
 AND (NOT c.relrowsecurity OR NOT c.relforcerowsecurity)), 'every tenant table has FORCE RLS');
SELECT pg_temp.assert_true((SELECT count(*)=0 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relkind='r' AND pg_get_userbyid(c.relowner)='dependency_app'), 'runtime owns no business table');
SELECT pg_temp.assert_true(NOT pg_has_role('dependency_app','dependency_migrator','MEMBER'), 'runtime cannot assume migrator role');
SELECT pg_temp.assert_true((SELECT count(*)=0 FROM pg_constraint fk
 JOIN pg_attribute child ON child.attrelid=fk.conrelid AND child.attname='organization_id'
 JOIN pg_attribute parent ON parent.attrelid=fk.confrelid AND parent.attname='organization_id'
 WHERE fk.contype='f' AND NOT child.attnum=ANY(fk.conkey)), 'all tenant-to-tenant FKs include organization_id');
SELECT pg_temp.assert_true((SELECT count(*)=0 FROM pg_constraint fk JOIN pg_namespace n ON n.oid=fk.connamespace
 WHERE n.nspname='public' AND fk.contype='f' AND fk.confdeltype<>'r'), 'historical foreign keys use explicit RESTRICT');
