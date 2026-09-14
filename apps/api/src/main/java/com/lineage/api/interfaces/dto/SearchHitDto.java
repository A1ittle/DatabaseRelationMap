package com.lineage.api.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class SearchHitDto {

	private final ObjectDto object;
	private final String action;

	public SearchHitDto(ObjectDto object, String action) {
		this.object = object;
		this.action = action;
	}

	public ObjectDto getObject() {
		return object;
	}

	public String getAction() {
		return action;
	}
}
