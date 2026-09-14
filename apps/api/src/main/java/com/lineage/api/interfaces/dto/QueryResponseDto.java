package com.lineage.api.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class QueryResponseDto {

	private final MetaDto meta;
	private final ObjectDto seed;
	private final StatsDto stats;
	private final ProjectionResponseDto projection;

	public QueryResponseDto(MetaDto meta, ObjectDto seed, StatsDto stats, ProjectionResponseDto projection) {
		this.meta = meta;
		this.seed = seed;
		this.stats = stats;
		this.projection = projection;
	}

	public MetaDto getMeta() {
		return meta;
	}

	public ObjectDto getSeed() {
		return seed;
	}

	public StatsDto getStats() {
		return stats;
	}

	public ProjectionResponseDto getProjection() {
		return projection;
	}
}
