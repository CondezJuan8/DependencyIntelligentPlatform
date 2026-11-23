-- Package inventory and vulnerability evidence do not need persisted package-to-package links.
DROP TABLE IF EXISTS dependency_edges;
DROP TABLE IF EXISTS dependency_relationship_types;
ALTER TABLE scan_components DROP COLUMN IF EXISTS is_root;
