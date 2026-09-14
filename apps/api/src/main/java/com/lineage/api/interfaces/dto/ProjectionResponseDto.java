package com.lineage.api.interfaces.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class ProjectionResponseDto {

	private final MetaDto meta;
	private final int clientRevision;
	private final List<GraphNodeDto> nodes;
	private final List<GraphEdgeDto> edges;
	private final Integer matchedDownstream;
	private final boolean renderLimited;
	private final List<String> reasons;

	public ProjectionResponseDto(MetaDto meta, int clientRevision, List<GraphNodeDto> nodes,
			List<GraphEdgeDto> edges, Integer matchedDownstream, boolean renderLimited, List<String> reasons) {
		this.meta = meta;
		this.clientRevision = clientRevision;
		this.nodes = nodes;
		this.edges = edges;
		this.matchedDownstream = matchedDownstream;
		this.renderLimited = renderLimited;
		this.reasons = reasons;
	}

	public MetaDto getMeta() {
		return meta;
	}

	public int getClientRevision() {
		return clientRevision;
	}

	public List<GraphNodeDto> getNodes() {
		return nodes;
	}

	public List<GraphEdgeDto> getEdges() {
		return edges;
	}

	public Integer getMatchedDownstream() {
		return matchedDownstream;
	}

	public boolean isRenderLimited() {
		return renderLimited;
	}

	public List<String> getReasons() {
		return reasons;
	}
}
