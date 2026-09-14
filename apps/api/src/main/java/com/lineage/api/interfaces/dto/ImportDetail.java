package com.lineage.api.interfaces.dto;

import java.util.List;

public final class ImportDetail {

	private final ImportResponse run;
	private final List<ErrorItem> issues;
	private final PageInfo page;

	public ImportDetail(ImportResponse run, List<ErrorItem> issues, PageInfo page) {
		this.run = run;
		this.issues = issues;
		this.page = page;
	}

	public ImportResponse getRun() {
		return run;
	}

	public List<ErrorItem> getIssues() {
		return issues;
	}

	public PageInfo getPage() {
		return page;
	}
}
