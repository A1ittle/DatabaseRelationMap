package com.lineage.api.infrastructure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Keeps the P0 no-database start path, and turns JDBC/Flyway on when a URL is
 * provided.
 *
 * <p>
 * Blank {@code SPRING_DATASOURCE_URL} / {@code spring.datasource.url}: exclude
 * DataSource and Flyway auto-configuration so {@code /api/health} still works.
 *
 * <p>
 * Non-blank URL: Flyway runs on startup unless {@code FLYWAY_ENABLED=false}.
 */
public class OptionalDataSourceEnvironmentPostProcessor implements EnvironmentPostProcessor {

	static final String PROPERTY_SOURCE_NAME = "lineageOptionalDatasource";

	private static final String DATASOURCE = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration";
	private static final String FLYWAY = "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		String url = firstNonBlank(environment.getProperty("SPRING_DATASOURCE_URL"),
			environment.getProperty("spring.datasource.url"));
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		if (isBlank(url)) {
			map.put("spring.autoconfigure.exclude", joinExclude(environment, DATASOURCE, FLYWAY));
			map.put("spring.flyway.enabled", "false");
		}
		else {
			map.put("spring.flyway.enabled", flywayEnabledWhenUrlPresent(environment) ? "true" : "false");
		}
		environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, map));
	}

	static boolean flywayEnabledWhenUrlPresent(ConfigurableEnvironment environment) {
		String flag = environment.getProperty("FLYWAY_ENABLED");
		if (isBlank(flag)) {
			return true;
		}
		return !isFalse(flag);
	}

	private static String joinExclude(ConfigurableEnvironment environment, String... extras) {
		List<String> excludes = new ArrayList<String>();
		String existing = environment.getProperty("spring.autoconfigure.exclude");
		if (!isBlank(existing)) {
			String[] parts = existing.split(",");
			for (int i = 0; i < parts.length; i++) {
				String part = parts[i].trim();
				if (!part.isEmpty() && !excludes.contains(part)) {
					excludes.add(part);
				}
			}
		}
		for (int i = 0; i < extras.length; i++) {
			if (!excludes.contains(extras[i])) {
				excludes.add(extras[i]);
			}
		}
		StringBuilder joined = new StringBuilder();
		for (int i = 0; i < excludes.size(); i++) {
			if (i > 0) {
				joined.append(',');
			}
			joined.append(excludes.get(i));
		}
		return joined.toString();
	}

	static String firstNonBlank(String a, String b) {
		if (!isBlank(a)) {
			return a;
		}
		if (!isBlank(b)) {
			return b;
		}
		return null;
	}

	static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	static boolean isFalse(String value) {
		String normalized = value.trim().toLowerCase();
		return "false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)
			|| "off".equals(normalized);
	}
}
