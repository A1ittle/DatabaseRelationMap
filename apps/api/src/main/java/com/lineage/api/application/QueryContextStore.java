package com.lineage.api.application;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded in-memory query cache. Idle 15 minutes, absolute 60 minutes.
 */
public final class QueryContextStore {

	private final ConcurrentHashMap<String, QueryContext> contexts = new ConcurrentHashMap<String, QueryContext>();

	public void put(QueryContext context) {
		contexts.put(context.getQueryId(), context);
	}

	public QueryContext get(String queryId) {
		if (queryId == null) {
			return null;
		}
		return contexts.get(queryId);
	}

	public void evictExpired(Instant now) {
		Iterator<Map.Entry<String, QueryContext>> it = contexts.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, QueryContext> entry = it.next();
			if (entry.getValue().expired(now)) {
				it.remove();
			}
		}
	}
}
