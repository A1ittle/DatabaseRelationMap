package com.lineage.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = {
		"org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
		"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
		"org.springframework.boot.sql.init.autoconfigure.SqlInitializationAutoConfiguration",
		"org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
		"org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
public class LineageApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(LineageApiApplication.class, args);
	}

}
