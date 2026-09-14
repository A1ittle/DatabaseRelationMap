package com.lineage.api.domain.graph;

import java.util.Objects;

/**
 * Object vertex used by the P1 graph façade. Identity is {@code id}.
 */
public final class GraphObject {

	private final String id;
	private final String type;
	private final String technicalName;
	private final String javaKind;

	public GraphObject(String id, String type, String technicalName, String javaKind) {
		this.id = Objects.requireNonNull(id, "id");
		this.type = Objects.requireNonNull(type, "type");
		this.technicalName = technicalName;
		this.javaKind = javaKind;
	}

	public String getId() {
		return id;
	}

	public String getType() {
		return type;
	}

	public String getTechnicalName() {
		return technicalName;
	}

	public String getJavaKind() {
		return javaKind;
	}

	public boolean isJava() {
		return "java".equals(type);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof GraphObject)) {
			return false;
		}
		GraphObject that = (GraphObject) o;
		return id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public String toString() {
		return "GraphObject{id=" + id + ", type=" + type + "}";
	}
}
