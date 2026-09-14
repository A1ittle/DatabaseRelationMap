package com.lineage.api.infrastructure;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * True when a JDBC URL is configured. Used instead of {@code @ConditionalOnBean(DataSource)}
 * so import beans are not sensitive to auto-configuration order.
 */
public class DataSourceUrlPresentCondition implements Condition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		String url = firstNonBlank(context.getEnvironment().getProperty("SPRING_DATASOURCE_URL"),
			context.getEnvironment().getProperty("spring.datasource.url"));
		return url != null;
	}

	private static String firstNonBlank(String a, String b) {
		if (a != null && !a.trim().isEmpty()) {
			return a.trim();
		}
		if (b != null && !b.trim().isEmpty()) {
			return b.trim();
		}
		return null;
	}
}
