package com.lineage.api.interfaces.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class ClusterPageDto {

	private final MetaDto meta;
	private final List<ClusterDto> items;
	private final PageInfo page;

	public ClusterPageDto(MetaDto meta, List<ClusterDto> items, PageInfo page) {
		this.meta = meta;
		this.items = items;
		this.page = page;
	}

	public MetaDto getMeta() {
		return meta;
	}

	public List<ClusterDto> getItems() {
		return items;
	}

	public PageInfo getPage() {
		return page;
	}
}
