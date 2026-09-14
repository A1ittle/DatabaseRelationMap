package com.lineage.api.interfaces;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.lineage.api.application.ApiException;
import com.lineage.api.application.ImportService;
import com.lineage.api.interfaces.dto.ImportDetail;
import com.lineage.api.interfaces.dto.ImportResponse;
import com.lineage.api.interfaces.dto.PublishResponse;

@RestController
public class ImportController {

	private final ObjectProvider<ImportService> importServices;

	public ImportController(ObjectProvider<ImportService> importServices) {
		this.importServices = importServices;
	}

	@PostMapping("/api/imports")
	public ResponseEntity<ImportResponse> createImport(@RequestBody JsonNode body) {
		ImportResponse response = service().importBatch(body);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
	}

	@GetMapping("/api/imports/{runId}")
	public ImportDetail getImport(@PathVariable("runId") String runId,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "limit", required = false) Integer limit) {
		return service().getImport(runId, cursor, limit);
	}

	@PostMapping("/api/imports/{runId}/publish")
	public PublishResponse publishImport(@PathVariable("runId") String runId, @RequestBody JsonNode body) {
		return service().publish(runId, body);
	}

	private ImportService service() {
		ImportService service = importServices.getIfAvailable();
		if (service == null) {
			throw ApiException.unavailable("database is not configured");
		}
		return service;
	}
}
