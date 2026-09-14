package com.lineage.api.interfaces.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class PathResponseDto {

	private final MetaDto meta;
	private final String status;
	private final List<ObjectDto> nodes;
	private final List<GraphEdgeDto> edges;
	private final String reason;

	public PathResponseDto(MetaDto meta, String status, List<ObjectDto> nodes, List<GraphEdgeDto> edges,
			String reason) {
		this.meta = meta;
		this.status = status;
		this.nodes = nodes;
		this.edges = edges;
		this.reason = reason;
	}

	public MetaDto getMeta() {
		return meta;
	}

	public String getStatus() {
		return status;
	}

	public List<ObjectDto> getNodes() {
		return nodes;
	}

	public List<GraphEdgeDto> getEdges() {
		return edges;
	}

	public String getReason() {
		return reason;
	}
}
