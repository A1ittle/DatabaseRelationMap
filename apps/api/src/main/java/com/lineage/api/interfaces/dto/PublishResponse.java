package com.lineage.api.interfaces.dto;

import java.time.Instant;

public final class PublishResponse {

	private final String snapshotId;
	private final Instant publishedAt;

	public PublishResponse(String snapshotId, Instant publishedAt) {
		this.snapshotId = snapshotId;
		this.publishedAt = publishedAt;
	}

	public String getSnapshotId() {
		return snapshotId;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}
}
