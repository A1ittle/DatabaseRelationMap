-- Flyway V2: object_identity PK becomes (scope_id, object_id).
-- Do not edit V1 — already-migrated DBs have a stored Flyway checksum.
-- V2 is the source of truth for running apps; spec/v1/storage.sql documents greenfield.
-- Same natural object_id (e.g. root) may exist in more than one catalog_scope.

ALTER TABLE object_grant ADD COLUMN scope_id text;

UPDATE object_grant og
SET scope_id = oi.scope_id
FROM object_identity oi
WHERE oi.object_id = og.object_id;

ALTER TABLE object_grant ALTER COLUMN scope_id SET NOT NULL;

ALTER TABLE object_grant DROP CONSTRAINT object_grant_pkey;
ALTER TABLE object_grant DROP CONSTRAINT object_grant_object_id_fkey;

ALTER TABLE object_version DROP CONSTRAINT object_version_scope_id_object_id_fkey;

ALTER TABLE object_identity DROP CONSTRAINT object_identity_pkey;
ALTER TABLE object_identity DROP CONSTRAINT object_identity_scope_id_object_id_key;
ALTER TABLE object_identity ADD CONSTRAINT object_identity_pkey PRIMARY KEY (scope_id, object_id);

ALTER TABLE object_version
  ADD CONSTRAINT object_version_scope_id_object_id_fkey
  FOREIGN KEY (scope_id, object_id) REFERENCES object_identity (scope_id, object_id);

ALTER TABLE object_grant
  ADD CONSTRAINT object_grant_pkey PRIMARY KEY (scope_id, object_id, group_id);

ALTER TABLE object_grant
  ADD CONSTRAINT object_grant_scope_id_object_id_fkey
  FOREIGN KEY (scope_id, object_id) REFERENCES object_identity (scope_id, object_id);
