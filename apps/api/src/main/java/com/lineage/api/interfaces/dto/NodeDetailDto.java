package com.lineage.api.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class NodeDetailDto {

	private final MetaDto meta;
	private final GraphNodeDto node;
	private final int directDownstreamCount;
	private final boolean canSetAsRoot;

	public NodeDetailDto(MetaDto meta, GraphNodeDto node, int directDownstreamCount, boolean canSetAsRoot) {
		this.meta = meta;
		this.node = node;
		this.directDownstreamCount = directDownstreamCount;
		this.canSetAsRoot = canSetAsRoot;
	}

	public MetaDto getMeta() {
		return meta;
	}

	public GraphNodeDto getNode() {
		return node;
	}

	public int getDirectDownstreamCount() {
		return directDownstreamCount;
	}

	public boolean isCanSetAsRoot() {
		return canSetAsRoot;
	}
}
