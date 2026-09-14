package com.lineage.api.infrastructure;

public final class CatalogScopeRow {

	private final String scopeId;
	private final String activeSnapshotId;
	private final long revision;

	public CatalogScopeRow(String scopeId, String activeSnapshotId, long revision) {
		this.scopeId = scopeId;
		this.activeSnapshotId = activeSnapshotId;
		this.revision = revision;
	}

	public String getScopeId() {
		return scopeId;
	}

	public String getActiveSnapshotId() {
		return activeSnapshotId;
	}

	public long getRevision() {
		return revision;
	}
}
