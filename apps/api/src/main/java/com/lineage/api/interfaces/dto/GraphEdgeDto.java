package com.lineage.api.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class GraphEdgeDto {

	private final RelationDto relation;
	private final String kind;

	public GraphEdgeDto(RelationDto relation, String kind) {
		this.relation = relation;
		this.kind = kind;
	}

	public RelationDto getRelation() {
		return relation;
	}

	public String getKind() {
		return kind;
	}
}
