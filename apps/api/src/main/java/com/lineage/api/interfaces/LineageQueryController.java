package com.lineage.api.interfaces;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

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
import com.lineage.api.application.QueryService;
import com.lineage.api.interfaces.dto.ClusterPageDto;
import com.lineage.api.interfaces.dto.EvidenceResponseDto;
import com.lineage.api.interfaces.dto.NodeDetailDto;
import com.lineage.api.interfaces.dto.NodePageDto;
import com.lineage.api.interfaces.dto.PathResponseDto;
import com.lineage.api.interfaces.dto.ProjectionResponseDto;
import com.lineage.api.interfaces.dto.QueryResponseDto;
import com.lineage.api.interfaces.dto.RelationPageDto;
import com.lineage.api.interfaces.dto.SearchResponseDto;

@RestController
public class LineageQueryController {

	private final ObjectProvider<QueryService> queryServices;

	public LineageQueryController(ObjectProvider<QueryService> queryServices) {
		this.queryServices = queryServices;
	}

	@GetMapping("/api/lineage/search")
	public SearchResponseDto search(@RequestParam("q") String q,
			@RequestParam(name = "queryId", required = false) String queryId,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "limit", required = false) Integer limit, HttpServletRequest request) {
		return service().search(q, queryId, cursor, limit, EmbedGroups.from(request), RequestIdFilter.from(request));
	}

	@PostMapping("/api/lineage/queries")
	public ResponseEntity<QueryResponseDto> createQuery(@RequestBody JsonNode body, HttpServletRequest request) {
		QueryResponseDto response = service().createQuery(body, EmbedGroups.from(request),
			RequestIdFilter.from(request));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/api/lineage/queries/{qid}/projection")
	public ProjectionResponseDto projection(@PathVariable("qid") String qid, @RequestBody JsonNode body,
			HttpServletRequest request) {
		return service().projection(qid, body, EmbedGroups.from(request), RequestIdFilter.from(request));
	}

	@GetMapping("/api/lineage/queries/{qid}/children")
	public NodePageDto children(@PathVariable("qid") String qid, @RequestParam("parentId") String parentId,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "limit", required = false) Integer limit,
			@RequestParam(name = "types", required = false) List<String> types, HttpServletRequest request) {
		return service().children(qid, parentId, cursor, limit, types, EmbedGroups.from(request),
			RequestIdFilter.from(request));
	}

	@GetMapping("/api/lineage/queries/{qid}/overview")
	public ClusterPageDto overview(@PathVariable("qid") String qid,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "limit", required = false) Integer limit,
			@RequestParam(name = "types", required = false) List<String> types, HttpServletRequest request) {
		return service().overview(qid, cursor, limit, types, EmbedGroups.from(request), RequestIdFilter.from(request));
	}

	@GetMapping("/api/lineage/queries/{qid}/clusters/{cid}/members")
	public NodePageDto clusterMembers(@PathVariable("qid") String qid, @PathVariable("cid") String cid,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "limit", required = false) Integer limit, HttpServletRequest request) {
		return service().clusterMembers(qid, cid, cursor, limit, EmbedGroups.from(request),
			RequestIdFilter.from(request));
	}

	@GetMapping("/api/lineage/queries/{qid}/impact")
	public NodePageDto impact(@PathVariable("qid") String qid,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "limit", required = false) Integer limit,
			@RequestParam(name = "types", required = false) List<String> types,
			@RequestParam(name = "system", required = false) String system, HttpServletRequest request) {
		return service().impact(qid, cursor, limit, types, system, EmbedGroups.from(request),
			RequestIdFilter.from(request));
	}

	@GetMapping("/api/lineage/queries/{qid}/nodes/{id}")
	public NodeDetailDto getNode(@PathVariable("qid") String qid, @PathVariable("id") String id,
			HttpServletRequest request) {
		return service().getNode(qid, id, EmbedGroups.from(request), RequestIdFilter.from(request));
	}

	@GetMapping("/api/lineage/queries/{qid}/nodes/{id}/relations")
	public RelationPageDto relations(@PathVariable("qid") String qid, @PathVariable("id") String id,
			@RequestParam("kind") String kind, @RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "limit", required = false) Integer limit, HttpServletRequest request) {
		return service().relations(qid, id, kind, cursor, limit, EmbedGroups.from(request),
			RequestIdFilter.from(request));
	}

	@GetMapping("/api/lineage/queries/{qid}/path")
	public PathResponseDto path(@PathVariable("qid") String qid, @RequestParam("targetId") String targetId,
			HttpServletRequest request) {
		return service().path(qid, targetId, EmbedGroups.from(request), RequestIdFilter.from(request));
	}

	@GetMapping("/api/lineage/queries/{qid}/relations/{rid}/evidence")
	public EvidenceResponseDto evidence(@PathVariable("qid") String qid, @PathVariable("rid") String rid,
			HttpServletRequest request) {
		return service().evidence(qid, rid, EmbedGroups.from(request), RequestIdFilter.from(request));
	}

	private QueryService service() {
		QueryService service = queryServices.getIfAvailable();
		if (service == null) {
			throw ApiException.unavailable("database is not configured");
		}
		return service;
	}
}
