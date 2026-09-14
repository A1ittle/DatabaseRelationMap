package com.lineage.api.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class ObjectDto {

	private final String id;
	private final String type;
	private final String namespace;
	private final String technicalName;
	private final String displayName;
	private final String system;
	private final String owner;
	private final String javaKind;

	public ObjectDto(String id, String type, String namespace, String technicalName, String displayName,
			String system, String owner, String javaKind) {
		this.id = id;
		this.type = type;
		this.namespace = namespace;
		this.technicalName = technicalName;
		this.displayName = displayName;
		this.system = system;
		this.owner = owner;
		this.javaKind = javaKind;
	}

	public String getId() {
		return id;
	}

	public String getType() {
		return type;
	}

	public String getNamespace() {
		return namespace;
	}

	public String getTechnicalName() {
		return technicalName;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getSystem() {
		return system;
	}

	public String getOwner() {
		return owner;
	}

	public String getJavaKind() {
		return javaKind;
	}
}
