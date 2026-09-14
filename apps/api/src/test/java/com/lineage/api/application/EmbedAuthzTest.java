package com.lineage.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class EmbedAuthzTest {

	@Test
	void nullGroupsAuthorizeAll() {
		Set<String> objects = new LinkedHashSet<String>(Arrays.asList("a", "b"));
		Set<String> result = EmbedAuthz.resolve(objects, null, false, Collections.singleton("a"),
			Collections.singleton("b"));
		assertEquals(objects, result);
	}

	@Test
	void denyWinsOverScopeView() {
		Set<String> objects = new LinkedHashSet<String>(Arrays.asList("a", "b", "c"));
		Set<String> result = EmbedAuthz.resolve(objects, Collections.singletonList("g"), true,
			Collections.singleton("a"), Collections.singleton("b"));
		assertTrue(result.contains("a"));
		assertFalse(result.contains("b"));
		assertTrue(result.contains("c"));
	}

	@Test
	void objectAllowWithoutScopeView() {
		Set<String> objects = new LinkedHashSet<String>(Arrays.asList("a", "b"));
		Set<String> result = EmbedAuthz.resolve(objects, Collections.singletonList("g"), false,
			Collections.singleton("a"), Collections.singleton("a"));
		assertTrue(result.isEmpty());
	}
}
