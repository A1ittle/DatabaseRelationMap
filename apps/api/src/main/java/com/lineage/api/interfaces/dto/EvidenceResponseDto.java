package com.lineage.api.interfaces.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class EvidenceResponseDto {

	private final MetaDto meta;
	private final String relationId;
	private final List<EvidenceDto> items;

	public EvidenceResponseDto(MetaDto meta, String relationId, List<EvidenceDto> items) {
		this.meta = meta;
		this.relationId = relationId;
		this.items = items;
	}

	public MetaDto getMeta() {
		return meta;
	}

	public String getRelationId() {
		return relationId;
	}

	public List<EvidenceDto> getItems() {
		return items;
	}
}
