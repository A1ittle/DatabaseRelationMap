package com.lineage.api.interfaces;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

/**
 * Dev/open embed principal. {@code null} groups means authorize-all (no header).
 * A present header (even empty) is an explicit group list.
 */
public final class EmbedGroups {

	public static final String HEADER = "X-Embed-Groups";
	public static final String ATTR = "lineage.embed.groups";
	public static final String PRESENT_ATTR = "lineage.embed.groups.present";

	private EmbedGroups() {
	}

	@SuppressWarnings("unchecked")
	public static List<String> from(HttpServletRequest request) {
		if (request == null) {
			return null;
		}
		Object present = request.getAttribute(PRESENT_ATTR);
		if (!Boolean.TRUE.equals(present)) {
			return null;
		}
		Object value = request.getAttribute(ATTR);
		if (value instanceof List) {
			return (List<String>) value;
		}
		return Collections.emptyList();
	}

	public static List<String> parse(String header) {
		if (header == null) {
			return null;
		}
		String[] parts = header.split(",");
		List<String> groups = new ArrayList<String>();
		for (int i = 0; i < parts.length; i++) {
			String g = parts[i].trim();
			if (!g.isEmpty() && g.length() <= 200 && !groups.contains(g)) {
				groups.add(g);
			}
		}
		return groups;
	}
}
