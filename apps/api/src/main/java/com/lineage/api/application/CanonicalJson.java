package com.lineage.api.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Canonical JSON: object keys sorted by Java UTF-16 code unit order, array
 * order preserved. Used for batchKey payload SHA-256, not for business
 * de-duplication of objects/edges.
 */
public final class CanonicalJson {

	private CanonicalJson() {
	}

	public static String stringify(JsonNode node) {
		StringBuilder sb = new StringBuilder();
		write(node, sb);
		return sb.toString();
	}

	public static String sha256Hex(JsonNode node) {
		byte[] bytes = stringify(node).getBytes(StandardCharsets.UTF_8);
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
		byte[] hash = digest.digest(bytes);
		char[] hex = new char[hash.length * 2];
		final char[] digits = "0123456789abcdef".toCharArray();
		for (int i = 0; i < hash.length; i++) {
			int v = hash[i] & 0xff;
			hex[i * 2] = digits[v >>> 4];
			hex[i * 2 + 1] = digits[v & 0x0f];
		}
		return new String(hex);
	}

	private static void write(JsonNode node, StringBuilder sb) {
		if (node == null || node.isNull()) {
			sb.append("null");
			return;
		}
		if (node.isObject()) {
			sb.append('{');
			List<String> names = new ArrayList<String>();
			Iterator<String> it = node.fieldNames();
			while (it.hasNext()) {
				names.add(it.next());
			}
			Collections.sort(names);
			for (int i = 0; i < names.size(); i++) {
				if (i > 0) {
					sb.append(',');
				}
				String name = names.get(i);
				writeString(name, sb);
				sb.append(':');
				write(node.get(name), sb);
			}
			sb.append('}');
			return;
		}
		if (node.isArray()) {
			sb.append('[');
			for (int i = 0; i < node.size(); i++) {
				if (i > 0) {
					sb.append(',');
				}
				write(node.get(i), sb);
			}
			sb.append(']');
			return;
		}
		if (node.isTextual()) {
			writeString(node.asText(), sb);
			return;
		}
		if (node.isBoolean()) {
			sb.append(node.booleanValue() ? "true" : "false");
			return;
		}
		if (node.isNumber()) {
			sb.append(node.toString());
			return;
		}
		sb.append(node.toString());
	}

	private static void writeString(String value, StringBuilder sb) {
		sb.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
			case '"':
				sb.append("\\\"");
				break;
			case '\\':
				sb.append("\\\\");
				break;
			case '\b':
				sb.append("\\b");
				break;
			case '\f':
				sb.append("\\f");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			case '\t':
				sb.append("\\t");
				break;
			default:
				if (c < 0x20) {
					sb.append("\\u00");
					sb.append("0123456789abcdef".charAt((c >> 4) & 0xf));
					sb.append("0123456789abcdef".charAt(c & 0xf));
				}
				else {
					sb.append(c);
				}
			}
		}
		sb.append('"');
	}
}
