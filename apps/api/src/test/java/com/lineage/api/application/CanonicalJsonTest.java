package com.lineage.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class CanonicalJsonTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void objectKeysAreSortedAndArrayOrderPreserved() throws Exception {
		ObjectNode a = mapper.createObjectNode();
		a.put("z", 1);
		a.put("a", 2);
		a.set("list", mapper.readTree("[3,1,2]"));
		ObjectNode b = mapper.createObjectNode();
		b.put("a", 2);
		b.put("z", 1);
		b.set("list", mapper.readTree("[3,1,2]"));
		assertEquals(CanonicalJson.stringify(a), CanonicalJson.stringify(b));
		assertEquals(CanonicalJson.sha256Hex(a), CanonicalJson.sha256Hex(b));
		assertEquals(64, CanonicalJson.sha256Hex(a).length());

		ObjectNode reorderedArray = mapper.createObjectNode();
		reorderedArray.put("a", 2);
		reorderedArray.put("z", 1);
		reorderedArray.set("list", mapper.readTree("[1,2,3]"));
		assertNotEquals(CanonicalJson.sha256Hex(a), CanonicalJson.sha256Hex(reorderedArray));
	}
}
