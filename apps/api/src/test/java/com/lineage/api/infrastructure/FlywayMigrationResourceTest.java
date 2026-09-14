package com.lineage.api.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * No Docker / no H2: PostgreSQL-specific DDL (IDENTITY, functional indexes)
 * is not executed here. This test only proves the V1 resource is on the
 * classpath and matches {@code spec/v1/storage.sql} after comment stripping.
 */
class FlywayMigrationResourceTest {

	private static final List<String> REQUIRED_TABLES = Arrays.asList(
		"catalog_scope",
		"ingest_run",
		"snapshot",
		"object_identity",
		"object_version",
		"evidence",
		"relation_version",
		"relation_evidence",
		"quality_issue",
		"scope_grant",
		"object_grant",
		"policy_revision");

	@Test
	void v1MigrationIsOnClasspath() throws IOException {
		String sql = readClasspath("db/migration/V1__storage.sql");
		assertNotNull(sql);
		assertTrue(sql.contains("GENERATED ALWAYS AS IDENTITY"));
		assertTrue(sql.contains("lower(technical_name)"));
		for (String table : REQUIRED_TABLES) {
			assertTrue(sql.contains("CREATE TABLE " + table), "missing table " + table);
		}
	}

	@Test
	void v1MigrationMatchesSpecStorageSql() throws IOException {
		String migration = stripSqlCommentsAndWhitespace(readClasspath("db/migration/V1__storage.sql"));
		String spec = stripSqlCommentsAndWhitespace(readClasspath("spec/v1/storage.sql"));
		assertEquals(spec, migration);
	}

	private static String readClasspath(String path) throws IOException {
		InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
		assertNotNull(in, "missing classpath resource: " + path);
		try {
			byte[] bytes = readAll(in);
			return new String(bytes, StandardCharsets.UTF_8);
		}
		finally {
			in.close();
		}
	}

	private static byte[] readAll(InputStream in) throws IOException {
		byte[] buffer = new byte[4096];
		byte[] out = new byte[0];
		int n;
		while ((n = in.read(buffer)) >= 0) {
			byte[] next = new byte[out.length + n];
			System.arraycopy(out, 0, next, 0, out.length);
			System.arraycopy(buffer, 0, next, out.length, n);
			out = next;
		}
		return out;
	}

	static String stripSqlCommentsAndWhitespace(String sql) {
		StringBuilder out = new StringBuilder();
		String[] lines = sql.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			String trimmed = lines[i].trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			int comment = trimmed.indexOf("--");
			if (comment >= 0) {
				trimmed = trimmed.substring(0, comment).trim();
			}
			if (trimmed.isEmpty()) {
				continue;
			}
			out.append(trimmed).append('\n');
		}
		return out.toString();
	}
}
