package com.lineage.api.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scope view + object allow/deny. Deny wins. {@code groups == null} is open/dev
 * (authorize every object in the snapshot).
 */
public final class EmbedAuthz {

	private EmbedAuthz() {
	}

	public static Set<String> resolve(Set<String> objectIds, List<String> groups, boolean scopeView,
			Set<String> allowIds, Set<String> denyIds) {
		if (groups == null) {
			return new LinkedHashSet<String>(objectIds);
		}
		Set<String> result = new LinkedHashSet<String>();
		if (scopeView) {
			for (String id : objectIds) {
				if (!denyIds.contains(id)) {
					result.add(id);
				}
			}
			return result;
		}
		for (String id : allowIds) {
			if (objectIds.contains(id) && !denyIds.contains(id)) {
				result.add(id);
			}
		}
		return result;
	}

	public static boolean sameGroups(List<String> left, List<String> right) {
		if (left == null && right == null) {
			return true;
		}
		if (left == null || right == null) {
			return false;
		}
		if (left.size() != right.size()) {
			return false;
		}
		return new LinkedHashSet<String>(left).equals(new LinkedHashSet<String>(right));
	}
}
