package com.lineage.api.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.lineage.api.application.QueryContextStore;
import com.lineage.api.application.QueryService;

@Configuration
@Conditional(DataSourceUrlPresentCondition.class)
public class JdbcQueryConfiguration {

	@Bean
	QueryJdbcRepository queryJdbcRepository(JdbcTemplate jdbcTemplate) {
		return new QueryJdbcRepository(jdbcTemplate);
	}

	@Bean
	QueryContextStore queryContextStore() {
		return new QueryContextStore();
	}

	@Bean
	QueryService queryService(QueryJdbcRepository queryJdbcRepository, QueryContextStore queryContextStore) {
		return new QueryService(queryJdbcRepository, queryContextStore);
	}
}
