package com.lineage.api.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class StatsDto {

	private final Integer downstream;
	private final Integer javaTerminals;
	private final Integer leaves;
	private final Integer crossEdges;
	private final TypeCounts byType;
	private final String countStatus;

	public StatsDto(Integer downstream, Integer javaTerminals, Integer leaves, Integer crossEdges, TypeCounts byType,
			String countStatus) {
		this.downstream = downstream;
		this.javaTerminals = javaTerminals;
		this.leaves = leaves;
		this.crossEdges = crossEdges;
		this.byType = byType;
		this.countStatus = countStatus;
	}

	public Integer getDownstream() {
		return downstream;
	}

	public Integer getJavaTerminals() {
		return javaTerminals;
	}

	public Integer getLeaves() {
		return leaves;
	}

	public Integer getCrossEdges() {
		return crossEdges;
	}

	public TypeCounts getByType() {
		return byType;
	}

	public String getCountStatus() {
		return countStatus;
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	public static final class TypeCounts {

		private final Integer table;
		private final Integer view;
		private final Integer procedure;
		private final Integer java;

		public TypeCounts(Integer table, Integer view, Integer procedure, Integer java) {
			this.table = table;
			this.view = view;
			this.procedure = procedure;
			this.java = java;
		}

		public Integer getTable() {
			return table;
		}

		public Integer getView() {
			return view;
		}

		public Integer getProcedure() {
			return procedure;
		}

		public Integer getJava() {
			return java;
		}
	}
}
