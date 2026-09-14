package com.lineage.api.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.lineage.api.application.ImportService;
import com.lineage.api.application.ImportValidator;

@Configuration
@Conditional(DataSourceUrlPresentCondition.class)
public class JdbcImportConfiguration {

	@Bean
	ImportJdbcRepository importJdbcRepository(JdbcTemplate jdbcTemplate) {
		return new ImportJdbcRepository(jdbcTemplate);
	}

	@Bean
	ImportValidator importValidator() {
		return new ImportValidator();
	}

	@Bean
	ImportService importService(ImportJdbcRepository importJdbcRepository, ImportValidator importValidator) {
		return new ImportService(importJdbcRepository, importValidator);
	}
}
