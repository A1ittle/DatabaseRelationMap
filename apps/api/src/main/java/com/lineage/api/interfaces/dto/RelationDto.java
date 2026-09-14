package com.lineage.api.interfaces.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class RelationDto {

	private final String id;
	private final String source;
	private final String target;
	private final String rel;
	private final int sourceOrder;
	private final List<String> evidenceIds;

	public RelationDto(String id, String source, String target, String rel, int sourceOrder,
			List<String> evidenceIds) {
		this.id = id;
		this.source = source;
		this.target = target;
		this.rel = rel;
		this.sourceOrder = sourceOrder;
		this.evidenceIds = evidenceIds;
	}

	public String getId() {
		return id;
	}

	public String getSource() {
		return source;
	}

	public String getTarget() {
		return target;
	}

	public String getRel() {
		return rel;
	}

	public int getSourceOrder() {
		return sourceOrder;
	}

	public List<String> getEvidenceIds() {
		return evidenceIds;
	}
}
