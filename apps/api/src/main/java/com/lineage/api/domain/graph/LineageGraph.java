package com.lineage.api.domain.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable object/relation snapshot. Display traversal must not mutate facts.
 */
public final class LineageGraph {

	private final List<GraphObject> objects;
	private final List<GraphRelation> relations;
	private final Map<String, GraphObject> objectsById;

	public LineageGraph(List<GraphObject> objects, List<GraphRelation> relations) {
		this.objects = Collections.unmodifiableList(new ArrayList<GraphObject>(objects));
		this.relations = Collections.unmodifiableList(new ArrayList<GraphRelation>(relations));
		Map<String, GraphObject> index = new LinkedHashMap<String, GraphObject>();
		for (GraphObject object : this.objects) {
			index.put(object.getId(), object);
		}
		this.objectsById = Collections.unmodifiableMap(index);
	}

	public List<GraphObject> getObjects() {
		return objects;
	}

	public List<GraphRelation> getRelations() {
		return relations;
	}

	public GraphObject object(String id) {
		return objectsById.get(id);
	}

	public GraphRelation relation(String id) {
		for (GraphRelation relation : relations) {
			if (relation.getId().equals(id)) {
				return relation;
			}
		}
		return null;
	}
}
