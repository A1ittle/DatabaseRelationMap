package com.lineage.api.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class SearchHitDto {

	private final ObjectDto object;
	private final String action;
	private final String scopeId;
	private final String snapshotId;

	public SearchHitDto(ObjectDto object, String action, String scopeId, String snapshotId) {
		this.object = object;
		this.action = action;
		this.scopeId = scopeId;
		this.snapshotId = snapshotId;
	}

	public ObjectDto getObject() {
		return object;
	}

	public String getAction() {
		return action;
	}

	public String getScopeId() {
		return scopeId;
	}

	public String getSnapshotId() {
		return snapshotId;
	}
}
