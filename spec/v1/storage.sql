-- PostgreSQL 17 schema design (greenfield / post-V2).
-- Running apps apply Flyway V1 then V2; do not rewrite V1 checksums.
-- V2 is the source of truth for object_identity / object_grant on running databases.
CREATE TABLE catalog_scope (
  scope_id text PRIMARY KEY,
  display_name text NOT NULL,
  active_snapshot_id text,
  revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0)
);
CREATE TABLE ingest_run (
  run_id text PRIMARY KEY,
  scope_id text NOT NULL REFERENCES catalog_scope(scope_id),
  batch_key text NOT NULL,
  payload_sha256 char(64) NOT NULL,
  status text NOT NULL CHECK (status IN ('received','validating','ready','failed','published')),
  source_version text NOT NULL,
  captured_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  error_count integer NOT NULL DEFAULT 0 CHECK (error_count >= 0),
  warning_count integer NOT NULL DEFAULT 0 CHECK (warning_count >= 0),
  UNIQUE(scope_id,batch_key)
);
CREATE TABLE snapshot (
  snapshot_id text PRIMARY KEY,
  scope_id text NOT NULL REFERENCES catalog_scope(scope_id),
  run_id text NOT NULL UNIQUE REFERENCES ingest_run(run_id),
  coverage text NOT NULL CHECK (coverage IN ('complete','partial','unknown')),
  published_at timestamptz,
  UNIQUE(scope_id,snapshot_id)
);
ALTER TABLE catalog_scope ADD CONSTRAINT active_snapshot_in_scope
  FOREIGN KEY (scope_id,active_snapshot_id) REFERENCES snapshot(scope_id,snapshot_id);
CREATE TABLE object_identity (
  scope_id text NOT NULL REFERENCES catalog_scope(scope_id),
  object_id text NOT NULL,
  source_identity text NOT NULL,
  PRIMARY KEY(scope_id,object_id),
  UNIQUE(scope_id,source_identity)
);
CREATE TABLE object_version (
  snapshot_id text NOT NULL,
  object_id text NOT NULL,
  scope_id text NOT NULL,
  object_type text NOT NULL CHECK(object_type IN ('table','view','procedure','java')),
  namespace text NOT NULL,
  technical_name text NOT NULL,
  display_name text NOT NULL,
  system_name text NOT NULL,
  owner_ref text,
  java_kind text CHECK(java_kind IN ('class','job','service')),
  PRIMARY KEY(snapshot_id,object_id),
  FOREIGN KEY(scope_id,snapshot_id) REFERENCES snapshot(scope_id,snapshot_id),
  FOREIGN KEY(scope_id,object_id) REFERENCES object_identity(scope_id,object_id),
  CHECK ((object_type='java' AND java_kind IS NOT NULL) OR (object_type<>'java' AND java_kind IS NULL))
);
CREATE TABLE evidence (
  snapshot_id text NOT NULL REFERENCES snapshot(snapshot_id),
  evidence_id text NOT NULL,
  evidence_state text NOT NULL CHECK(evidence_state IN ('observed','parsed','inferred','confirmed','unknown')),
  source_ref text NOT NULL,
  observed_at timestamptz NOT NULL,
  description text NOT NULL,
  PRIMARY KEY(snapshot_id,evidence_id)
);
CREATE TABLE relation_version (
  snapshot_id text NOT NULL REFERENCES snapshot(snapshot_id),
  relation_id text NOT NULL,
  source_id text NOT NULL,
  target_id text NOT NULL,
  relation_type text NOT NULL CHECK(relation_type IN ('reads','writes','calls','derives')),
  source_order integer NOT NULL CHECK(source_order>=0),
  PRIMARY KEY(snapshot_id,relation_id),
  UNIQUE(snapshot_id,source_id,target_id,relation_type),
  FOREIGN KEY(snapshot_id,source_id) REFERENCES object_version(snapshot_id,object_id),
  FOREIGN KEY(snapshot_id,target_id) REFERENCES object_version(snapshot_id,object_id)
);
CREATE TABLE relation_evidence (
  snapshot_id text NOT NULL,
  relation_id text NOT NULL,
  evidence_id text NOT NULL,
  PRIMARY KEY(snapshot_id,relation_id,evidence_id),
  FOREIGN KEY(snapshot_id,relation_id) REFERENCES relation_version(snapshot_id,relation_id),
  FOREIGN KEY(snapshot_id,evidence_id) REFERENCES evidence(snapshot_id,evidence_id)
);
CREATE TABLE quality_issue (
  issue_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  run_id text NOT NULL REFERENCES ingest_run(run_id),
  code text NOT NULL,
  severity text NOT NULL CHECK(severity IN ('error','warning')),
  source_path text NOT NULL,
  message text NOT NULL
);
-- Authorized groups come from the server session; never trust request headers containing group IDs.
CREATE TABLE scope_grant (
  scope_id text NOT NULL REFERENCES catalog_scope(scope_id),
  group_id text NOT NULL,
  permission text NOT NULL CHECK(permission IN ('view','ingest')),
  PRIMARY KEY(scope_id,group_id,permission)
);
CREATE TABLE object_grant (
  scope_id text NOT NULL,
  object_id text NOT NULL,
  group_id text NOT NULL,
  effect text NOT NULL CHECK(effect IN ('allow','deny')),
  PRIMARY KEY(scope_id,object_id,group_id),
  FOREIGN KEY(scope_id,object_id) REFERENCES object_identity(scope_id,object_id)
);
CREATE TABLE policy_revision (
  singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),
  revision bigint NOT NULL CHECK(revision>=0)
);
INSERT INTO policy_revision(singleton,revision) VALUES(true,0);
CREATE INDEX relation_out ON relation_version(snapshot_id,source_id,relation_id);
CREATE INDEX relation_in ON relation_version(snapshot_id,target_id,relation_id);
CREATE INDEX object_catalog ON object_version(snapshot_id,object_type,system_name,object_id);
CREATE INDEX object_name ON object_version(snapshot_id,lower(technical_name));
CREATE INDEX issue_run ON quality_issue(run_id,issue_id);
