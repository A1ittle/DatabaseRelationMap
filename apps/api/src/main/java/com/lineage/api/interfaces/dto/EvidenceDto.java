package com.lineage.api.interfaces.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class EvidenceDto {

	private final String id;
	private final String state;
	private final String sourceRef;
	private final Instant observedAt;
	private final String description;

	public EvidenceDto(String id, String state, String sourceRef, Instant observedAt, String description) {
		this.id = id;
		this.state = state;
		this.sourceRef = sourceRef;
		this.observedAt = observedAt;
		this.description = description;
	}

	public String getId() {
		return id;
	}

	public String getState() {
		return state;
	}

	public String getSourceRef() {
		return sourceRef;
	}

	public Instant getObservedAt() {
		return observedAt;
	}

	public String getDescription() {
		return description;
	}
}
