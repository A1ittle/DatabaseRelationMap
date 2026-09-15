package com.lineage.api.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.lineage.api.domain.graph.ClassificationResult;
import com.lineage.api.domain.graph.GraphObject;
import com.lineage.api.domain.graph.GraphRelation;
import com.lineage.api.domain.graph.LineageGraph;
import com.lineage.api.domain.graph.LineageGraphAlgorithms;
import com.lineage.api.domain.graph.LineageGraphAlgorithmsFactory;
import com.lineage.api.domain.graph.PathResult;
import com.lineage.api.domain.graph.TreeStatus;
import com.lineage.api.infrastructure.QueryJdbcRepository;
import com.lineage.api.infrastructure.QueryJdbcRepository.ActiveSnapshot;
import com.lineage.api.infrastructure.QueryJdbcRepository.CatalogObject;
import com.lineage.api.infrastructure.QueryJdbcRepository.EvidenceRecord;
import com.lineage.api.infrastructure.QueryJdbcRepository.ObjectGrant;
import com.lineage.api.infrastructure.QueryJdbcRepository.RelationRecord;
import com.lineage.api.infrastructure.QueryJdbcRepository.SeedHit;
import com.lineage.api.infrastructure.QueryJdbcRepository.SnapshotCatalog;
import com.lineage.api.interfaces.dto.ClusterDto;
import com.lineage.api.interfaces.dto.ClusterPageDto;
import com.lineage.api.interfaces.dto.EvidenceDto;
import com.lineage.api.interfaces.dto.EvidenceResponseDto;
import com.lineage.api.interfaces.dto.GraphEdgeDto;
import com.lineage.api.interfaces.dto.GraphNodeDto;
import com.lineage.api.interfaces.dto.MetaDto;
import com.lineage.api.interfaces.dto.NodeDetailDto;
import com.lineage.api.interfaces.dto.NodePageDto;
import com.lineage.api.interfaces.dto.ObjectDto;
import com.lineage.api.interfaces.dto.PageInfo;
import com.lineage.api.interfaces.dto.PathResponseDto;
import com.lineage.api.interfaces.dto.ProjectionResponseDto;
import com.lineage.api.interfaces.dto.QueryResponseDto;
import com.lineage.api.interfaces.dto.RelationDto;
import com.lineage.api.interfaces.dto.RelationPageDto;
import com.lineage.api.interfaces.dto.SearchHitDto;
import com.lineage.api.interfaces.dto.SearchResponseDto;
import com.lineage.api.interfaces.dto.StatsDto;
import com.lineage.api.interfaces.dto.StatsDto.TypeCounts;

public class QueryService {

	private static final int SEARCH_DEFAULT = 10;
	private static final int SEARCH_MAX = 20;
	private static final int PAGE_DEFAULT = 50;
	private static final int PAGE_MAX = 100;
	private static final int FIRST_LAYER_CHILDREN = 99;
	private static final int NODE_BUDGET = 200;
	private static final int EDGE_BUDGET = 2000;

	private static final String[] TYPE_ORDER = new String[] { "table", "view", "procedure", "java" };

	private final QueryJdbcRepository repository;
	private final QueryContextStore store;
	private final LineageGraphAlgorithms algorithms;

	public QueryService(QueryJdbcRepository repository, QueryContextStore store) {
		this.repository = repository;
		this.store = store;
		this.algorithms = LineageGraphAlgorithmsFactory.create();
	}

	@Transactional(readOnly = true)
	public SearchResponseDto search(String q, String queryId, String cursor, Integer limit, List<String> groups,
			String requestId) {
		String query = requireText(q, "q", 1, 200);
		int pageLimit = bound(limit, SEARCH_DEFAULT, 1, SEARCH_MAX, "limit");
		if (cursor != null && cursor.length() > 4096) {
			throw ApiException.cursorMismatch("cursor is invalid");
		}
		List<RankedHit> hits = new ArrayList<RankedHit>();
		Set<String> revealIds = Collections.emptySet();
		if (queryId != null && !queryId.isEmpty()) {
			QueryContext ctx = requireQuery(queryId, groups);
			revealIds = ctx.getClassification().getReach();
			String like = likePattern(query);
			List<String> authorized = new ArrayList<String>(ctx.getAuthorizedObjectIds());
			List<CatalogObject> found = repository.search(ctx.getSnapshotId(), like, authorized);
			for (int i = 0; i < found.size(); i++) {
				hits.add(new RankedHit(found.get(i), ctx.getScopeId(), ctx.getSnapshotId()));
			}
		}
		else {
			List<ActiveSnapshot> active = repository.listActiveSnapshots();
			if (active.isEmpty()) {
				throw ApiException.lineageNotCollected("no published snapshot");
			}
			String like = likePattern(query);
			for (int i = 0; i < active.size(); i++) {
				ActiveSnapshot snap = active.get(i);
				Set<String> authorized = resolveAuthorized(snap.snapshotId, snap.scopeId, groups);
				List<CatalogObject> found = repository.search(snap.snapshotId, like,
					groups == null ? null : new ArrayList<String>(authorized));
				for (int j = 0; j < found.size(); j++) {
					hits.add(new RankedHit(found.get(j), snap.scopeId, snap.snapshotId));
				}
			}
		}
		sortRanked(hits);
		int from = offsetAfter(hits, cursor);
		boolean hasMore = hits.size() > from + pageLimit;
		int to = Math.min(hits.size(), from + pageLimit);
		List<SearchHitDto> items = new ArrayList<SearchHitDto>();
		for (int i = from; i < to; i++) {
			RankedHit hit = hits.get(i);
			String action = revealIds.contains(hit.obj.id) ? "reveal" : "recenter";
			items.add(new SearchHitDto(toObject(hit.obj), action, hit.scopeId, hit.snapshotId));
		}
		String next = hasMore && to > from ? cursorOf(hits.get(to - 1)) : null;
		return new SearchResponseDto(requestId, items, new PageInfo(next, hasMore, Integer.valueOf(hits.size())));
	}

	@Transactional(readOnly = true)
	public QueryResponseDto createQuery(JsonNode body, List<String> groups, String requestId) {
		if (body == null || !body.isObject()) {
			throw ApiException.invalidArgument("QueryRequest must be a JSON object");
		}
		if (!body.has("seedId") || !body.get("seedId").isTextual()) {
			throw ApiException.invalidArgument("seedId is required");
		}
		String seedId = requireText(body.get("seedId").asText(), "seedId", 1, 200);
		String snapshotId = null;
		if (body.has("snapshotId") && !body.get("snapshotId").isNull()) {
			if (!body.get("snapshotId").isTextual()) {
				throw ApiException.invalidArgument("snapshotId must be a string");
			}
			snapshotId = requireText(body.get("snapshotId").asText(), "snapshotId", 1, 200);
		}
		java.util.Iterator<String> names = body.fieldNames();
		while (names.hasNext()) {
			String name = names.next();
			if (!"seedId".equals(name) && !"snapshotId".equals(name)) {
				throw ApiException.invalidArgument("QueryRequest has unknown properties");
			}
		}
		List<SeedHit> seeds = repository.findActiveSeeds(seedId, snapshotId);
		if (seeds.isEmpty()) {
			throw ApiException.invalidSeed("seed is not in the active snapshot");
		}
		if (seeds.size() > 1) {
			throw ApiException.invalidSeed("seed is ambiguous across scopes; pass snapshotId");
		}
		SeedHit seed = seeds.get(0);
		if (snapshotId != null && !snapshotId.equals(seed.activeSnapshotId)) {
			throw ApiException.policyChanged("snapshotId is not the active snapshot");
		}
		QueryContext ctx = buildContext(seed, groups);
		store.put(ctx);
		MetaDto meta = meta(requestId, ctx);
		List<String> first = firstLayerCandidates(ctx);
		ProjectionResponseDto projection = project(ctx, first, seedId, typeFilter(allTypes()), false, 0, requestId);
		return new QueryResponseDto(meta, toObject(ctx.getObjects().get(seedId)), stats(ctx), projection);
	}

	@Transactional(readOnly = true)
	public NodePageDto children(String qid, String parentId, String cursor, Integer limit, List<String> types,
			List<String> groups, String requestId) {
		QueryContext ctx = requireQuery(qid, groups);
		String parent = requireText(parentId, "parentId", 1, 200);
		if (!visible(ctx, parent)) {
			throw ApiException.notFound("not found");
		}
		int pageLimit = bound(limit, PAGE_DEFAULT, 1, PAGE_MAX, "limit");
		Set<String> typeFilter = typeFilter(types);
		List<String> childIds = treeChildren(ctx, parent);
		List<String> filtered = new ArrayList<String>();
		for (int i = 0; i < childIds.size(); i++) {
			CatalogObject obj = ctx.getObjects().get(childIds.get(i));
			if (obj != null && (typeFilter.isEmpty() || typeFilter.contains(obj.type))) {
				filtered.add(childIds.get(i));
			}
		}
		return nodePage(ctx, filtered, cursor, pageLimit, requestId);
	}

	@Transactional(readOnly = true)
	public ProjectionResponseDto projection(String qid, JsonNode body, List<String> groups, String requestId) {
		QueryContext ctx = requireQuery(qid, groups);
		if (body == null || !body.isObject()) {
			throw ApiException.invalidArgument("ProjectionRequest must be a JSON object");
		}
		if (!body.has("candidateIds") || !body.get("candidateIds").isArray()) {
			throw ApiException.invalidArgument("candidateIds is required");
		}
		JsonNode idsNode = body.get("candidateIds");
		if (idsNode.size() < 1 || idsNode.size() > 200) {
			throw ApiException.invalidArgument("candidateIds size must be between 1 and 200");
		}
		LinkedHashSet<String> candidates = new LinkedHashSet<String>();
		for (int i = 0; i < idsNode.size(); i++) {
			if (!idsNode.get(i).isTextual()) {
				throw ApiException.invalidArgument("candidateIds must be strings");
			}
			String id = idsNode.get(i).asText();
			if (id.isEmpty() || id.length() > 200) {
				throw ApiException.invalidArgument("candidateIds contains an invalid id");
			}
			if (!candidates.add(id)) {
				throw ApiException.invalidArgument("candidateIds must be unique");
			}
		}
		if (!candidates.contains(ctx.getSeedId())) {
			throw ApiException.invalidArgument("candidateIds must include the query seed");
		}
		for (String id : candidates) {
			if (!visible(ctx, id)) {
				throw ApiException.notFound("not found");
			}
		}
		if (!body.has("selectedId") || !body.get("selectedId").isTextual()) {
			throw ApiException.invalidArgument("selectedId is required");
		}
		String selectedId = requireText(body.get("selectedId").asText(), "selectedId", 1, 200);
		if (!visible(ctx, selectedId)) {
			throw ApiException.notFound("not found");
		}
		if (!body.has("types") || !body.get("types").isArray()) {
			throw ApiException.invalidArgument("types is required");
		}
		List<String> types = new ArrayList<String>();
		for (int i = 0; i < body.get("types").size(); i++) {
			if (!body.get("types").get(i).isTextual()) {
				throw ApiException.invalidArgument("types must be strings");
			}
			types.add(body.get("types").get(i).asText());
		}
		if (types.size() > 4) {
			throw ApiException.invalidArgument("types exceeds maxItems");
		}
		if (!body.has("revealSelectedPath") || !body.get("revealSelectedPath").isBoolean()) {
			throw ApiException.invalidArgument("revealSelectedPath is required");
		}
		boolean reveal = body.get("revealSelectedPath").asBoolean();
		if (!body.has("clientRevision") || !body.get("clientRevision").canConvertToInt()) {
			throw ApiException.invalidArgument("clientRevision is required");
		}
		int revision = body.get("clientRevision").asInt();
		if (revision < 0) {
			throw ApiException.invalidArgument("clientRevision must be >= 0");
		}
		return project(ctx, new ArrayList<String>(candidates), selectedId, typeFilter(types), reveal, revision,
			requestId);
	}

	@Transactional(readOnly = true)
	public PathResponseDto path(String qid, String targetId, List<String> groups, String requestId) {
		QueryContext ctx = requireQuery(qid, groups);
		String target = requireText(targetId, "targetId", 1, 200);
		if (!visible(ctx, target)) {
			throw ApiException.notFound("not found");
		}
		PathResult result = algorithms.shortestPath(ctx.getGraph(), ctx.getSeedId(), target,
			ctx.getAuthorizedObjectIds());
		List<ObjectDto> nodes = new ArrayList<ObjectDto>();
		List<GraphEdgeDto> edges = new ArrayList<GraphEdgeDto>();
		for (int i = 0; i < result.getNodeIds().size(); i++) {
			nodes.add(toObject(ctx.getObjects().get(result.getNodeIds().get(i))));
		}
		for (int i = 0; i < result.getEdgeIds().size(); i++) {
			edges.add(toEdge(ctx, result.getEdgeIds().get(i)));
		}
		String reason = result.getReason() == null ? null : result.getReason().wire();
		return new PathResponseDto(meta(requestId, ctx), result.getStatus().wire(), nodes, edges, reason);
	}

	@Transactional(readOnly = true)
	public ClusterPageDto overview(String qid, String cursor, Integer limit, List<String> types, List<String> groups,
			String requestId) {
		QueryContext ctx = requireQuery(qid, groups);
		int pageLimit = bound(limit, PAGE_DEFAULT, 1, PAGE_MAX, "limit");
		Set<String> typeFilter = typeFilter(types);
		List<ClusterDto> clusters = new ArrayList<ClusterDto>();
		Map<String, ClusterDto> index = new LinkedHashMap<String, ClusterDto>();
		ClassificationResult c = ctx.getClassification();
		boolean ranked = c.getTreeStatus() == TreeStatus.AVAILABLE;
		for (String id : c.getReach()) {
			CatalogObject obj = ctx.getObjects().get(id);
			if (obj == null) {
				continue;
			}
			if (!typeFilter.isEmpty() && !typeFilter.contains(obj.type)) {
				continue;
			}
			Integer rank = ranked ? c.getLayoutRank().get(id) : null;
			String cid = (rank == null ? "none" : String.valueOf(rank.intValue())) + ":" + obj.type;
			ClusterDto existing = index.get(cid);
			if (existing == null) {
				existing = new ClusterDto(cid, rank, obj.type, 1);
				index.put(cid, existing);
				clusters.add(existing);
			}
			else {
				ClusterDto next = new ClusterDto(existing.getId(), existing.getLayoutRank(), existing.getType(),
					existing.getCount() + 1);
				index.put(cid, next);
				clusters.set(clusters.indexOf(existing), next);
			}
		}
		int from = offsetCluster(clusters, cursor);
		boolean hasMore = clusters.size() > from + pageLimit;
		int to = Math.min(clusters.size(), from + pageLimit);
		List<ClusterDto> page = new ArrayList<ClusterDto>(clusters.subList(from, to));
		String next = hasMore && to > from ? page.get(page.size() - 1).getId() : null;
		return new ClusterPageDto(meta(requestId, ctx), page,
			new PageInfo(next, hasMore, Integer.valueOf(clusters.size())));
	}

	@Transactional(readOnly = true)
	public NodePageDto clusterMembers(String qid, String cid, String cursor, Integer limit, List<String> groups,
			String requestId) {
		QueryContext ctx = requireQuery(qid, groups);
		String clusterId = requireText(cid, "cid", 1, 200);
		int colon = clusterId.indexOf(':');
		if (colon <= 0 || colon == clusterId.length() - 1) {
			throw ApiException.notFound("not found");
		}
		String rankPart = clusterId.substring(0, colon);
		String type = clusterId.substring(colon + 1);
		Integer rank = "none".equals(rankPart) ? null : parseRank(rankPart);
		int pageLimit = bound(limit, PAGE_DEFAULT, 1, PAGE_MAX, "limit");
		ClassificationResult c = ctx.getClassification();
		boolean ranked = c.getTreeStatus() == TreeStatus.AVAILABLE;
		List<String> ids = new ArrayList<String>();
		for (String id : c.getReach()) {
			CatalogObject obj = ctx.getObjects().get(id);
			if (obj == null || !type.equals(obj.type)) {
				continue;
			}
			Integer nodeRank = ranked ? c.getLayoutRank().get(id) : null;
			if (rank == null ? nodeRank == null : rank.equals(nodeRank)) {
				ids.add(id);
			}
		}
		sortObjectIds(ctx, ids);
		if (ids.isEmpty()) {
			throw ApiException.notFound("not found");
		}
		return nodePage(ctx, ids, cursor, pageLimit, requestId);
	}

	@Transactional(readOnly = true)
	public NodePageDto impact(String qid, String cursor, Integer limit, List<String> types, String system,
			List<String> groups, String requestId) {
		QueryContext ctx = requireQuery(qid, groups);
		int pageLimit = bound(limit, PAGE_DEFAULT, 1, PAGE_MAX, "limit");
		Set<String> typeFilter = typeFilter(types);
		List<String> ids = new ArrayList<String>();
		for (String id : ctx.getClassification().getReach()) {
			if (id.equals(ctx.getSeedId())) {
				continue;
			}
			CatalogObject obj = ctx.getObjects().get(id);
			if (obj == null) {
				continue;
			}
			if (!typeFilter.isEmpty() && !typeFilter.contains(obj.type)) {
				continue;
			}
			if (system != null && !system.isEmpty() && !system.equals(obj.system)) {
				continue;
			}
			ids.add(id);
		}
		sortObjectIds(ctx, ids);
		return nodePage(ctx, ids, cursor, pageLimit, requestId);
	}

	@Transactional(readOnly = true)
	public NodeDetailDto getNode(String qid, String id, List<String> groups, String requestId) {
		QueryContext ctx = requireQuery(qid, groups);
		String objectId = requireText(id, "id", 1, 200);
		if (!visible(ctx, objectId)) {
			throw ApiException.notFound("not found");
		}
		int direct = 0;
		for (RelationRecord rel : ctx.getRelations().values()) {
			if (objectId.equals(rel.source) && classified(ctx, rel.id) != null
					&& ctx.getAuthorizedObjectIds().contains(rel.target)) {
				direct++;
			}
		}
		return new NodeDetailDto(meta(requestId, ctx), toNode(ctx, objectId), direct, true);
	}

	@Transactional(readOnly = true)
	public RelationPageDto relations(String qid, String id, String kind, String cursor, Integer limit,
			List<String> groups, String requestId) {
		QueryContext ctx = requireQuery(qid, groups);
		String objectId = requireText(id, "id", 1, 200);
		if (!visible(ctx, objectId)) {
			throw ApiException.notFound("not found");
		}
		if (kind == null || (!"all".equals(kind) && !"tree".equals(kind) && !"cross".equals(kind))) {
			throw ApiException.invalidArgument("kind must be all, tree, or cross");
		}
		int pageLimit = bound(limit, PAGE_DEFAULT, 1, PAGE_MAX, "limit");
		List<GraphEdgeDto> edges = new ArrayList<GraphEdgeDto>();
		for (RelationRecord rel : ctx.getRelations().values()) {
			if (!objectId.equals(rel.source) && !objectId.equals(rel.target)) {
				continue;
			}
			String edgeKind = classified(ctx, rel.id);
			if (edgeKind == null) {
				continue;
			}
			if (!"all".equals(kind) && !kind.equals(edgeKind)) {
				continue;
			}
			edges.add(toEdge(ctx, rel.id));
		}
		int from = offsetEdge(edges, cursor);
		boolean hasMore = edges.size() > from + pageLimit;
		int to = Math.min(edges.size(), from + pageLimit);
		List<GraphEdgeDto> page = new ArrayList<GraphEdgeDto>(edges.subList(from, to));
		LinkedHashSet<String> endpoints = new LinkedHashSet<String>();
		for (int i = 0; i < page.size(); i++) {
			endpoints.add(page.get(i).getRelation().getSource());
			endpoints.add(page.get(i).getRelation().getTarget());
		}
		List<ObjectDto> endpointObjects = new ArrayList<ObjectDto>();
		for (String eid : endpoints) {
			CatalogObject obj = ctx.getObjects().get(eid);
			if (obj != null) {
				endpointObjects.add(toObject(obj));
			}
		}
		String next = hasMore && !page.isEmpty() ? page.get(page.size() - 1).getRelation().getId() : null;
		return new RelationPageDto(meta(requestId, ctx), page, endpointObjects,
			new PageInfo(next, hasMore, Integer.valueOf(edges.size())));
	}

	@Transactional(readOnly = true)
	public EvidenceResponseDto evidence(String qid, String rid, List<String> groups, String requestId) {
		QueryContext ctx = requireQuery(qid, groups);
		String relationId = requireText(rid, "rid", 1, 200);
		RelationRecord rel = ctx.getRelations().get(relationId);
		if (rel == null || classified(ctx, relationId) == null) {
			throw ApiException.notFound("not found");
		}
		if (!ctx.getAuthorizedObjectIds().contains(rel.source)
				|| !ctx.getAuthorizedObjectIds().contains(rel.target)) {
			throw ApiException.notFound("not found");
		}
		List<EvidenceDto> items = new ArrayList<EvidenceDto>();
		for (int i = 0; i < rel.evidenceIds.size(); i++) {
			EvidenceRecord ev = ctx.getEvidence().get(rel.evidenceIds.get(i));
			if (ev != null) {
				items.add(new EvidenceDto(ev.id, ev.state, ev.sourceRef, ev.observedAt, ev.description));
			}
		}
		return new EvidenceResponseDto(meta(requestId, ctx), relationId, items);
	}

	private QueryContext buildContext(SeedHit seed, List<String> groups) {
		SnapshotCatalog catalog = repository.loadSnapshot(seed.snapshotId);
		if (catalog == null) {
			throw ApiException.lineageNotCollected("snapshot is not available");
		}
		Set<String> authorized = resolveAuthorized(seed.snapshotId, seed.scopeId, groups);
		if (!authorized.contains(seed.objectId)) {
			throw ApiException.invalidSeed("seed is not in the active snapshot");
		}
		Map<String, CatalogObject> objects = new LinkedHashMap<String, CatalogObject>();
		List<GraphObject> graphObjects = new ArrayList<GraphObject>();
		for (int i = 0; i < catalog.objects.size(); i++) {
			CatalogObject obj = catalog.objects.get(i);
			objects.put(obj.id, obj);
			graphObjects.add(new GraphObject(obj.id, obj.type, obj.technicalName, obj.javaKind));
		}
		Map<String, RelationRecord> relations = new LinkedHashMap<String, RelationRecord>();
		List<GraphRelation> graphRelations = new ArrayList<GraphRelation>();
		for (int i = 0; i < catalog.relations.size(); i++) {
			RelationRecord rel = catalog.relations.get(i);
			relations.put(rel.id, rel);
			graphRelations.add(new GraphRelation(rel.id, rel.source, rel.target, rel.rel, rel.sourceOrder));
		}
		Map<String, EvidenceRecord> evidence = new LinkedHashMap<String, EvidenceRecord>();
		for (int i = 0; i < catalog.evidence.size(); i++) {
			EvidenceRecord ev = catalog.evidence.get(i);
			evidence.put(ev.id, ev);
		}
		LineageGraph graph = new LineageGraph(graphObjects, graphRelations);
		ClassificationResult classification = algorithms.classify(graph, seed.objectId, authorized);
		Instant now = Instant.now();
		return new QueryContext(UUID.randomUUID().toString(), seed.snapshotId, seed.scopeId, seed.objectId,
			seed.coverage, repository.policyRevision(), groups, authorized, graph, classification, objects,
			relations, evidence, now);
	}

	private Set<String> resolveAuthorized(String snapshotId, String scopeId, List<String> groups) {
		SnapshotCatalog catalog = repository.loadSnapshot(snapshotId);
		Set<String> objectIds = new LinkedHashSet<String>();
		for (int i = 0; i < catalog.objects.size(); i++) {
			objectIds.add(catalog.objects.get(i).id);
		}
		if (groups == null) {
			return EmbedAuthz.resolve(objectIds, null, false, Collections.<String>emptySet(),
				Collections.<String>emptySet());
		}
		List<String> viewGroups = repository.scopeViewGroups(scopeId, groups);
		boolean scopeView = !viewGroups.isEmpty();
		Set<String> allow = new HashSet<String>();
		Set<String> deny = new HashSet<String>();
		List<ObjectGrant> grants = repository.objectGrants(scopeId, groups);
		for (int i = 0; i < grants.size(); i++) {
			ObjectGrant g = grants.get(i);
			if ("deny".equals(g.effect)) {
				deny.add(g.objectId);
			}
			else if ("allow".equals(g.effect)) {
				allow.add(g.objectId);
			}
		}
		return EmbedAuthz.resolve(objectIds, groups, scopeView, allow, deny);
	}

	private QueryContext requireQuery(String qid, List<String> groups) {
		String id = requireText(qid, "qid", 1, 200);
		store.evictExpired(Instant.now());
		QueryContext ctx = store.get(id);
		if (ctx == null || ctx.expired(Instant.now())) {
			throw ApiException.queryExpired("query context expired");
		}
		if (!EmbedAuthz.sameGroups(ctx.getGroups(), groups)) {
			throw ApiException.policyChanged("embed groups do not match the query session");
		}
		if (repository.policyRevision() != ctx.getPolicyRevision()) {
			throw ApiException.policyChanged("authorization policy revision changed");
		}
		ctx.touch(Instant.now());
		return ctx;
	}

	private boolean visible(QueryContext ctx, String objectId) {
		return ctx.getAuthorizedObjectIds().contains(objectId)
			&& ctx.getClassification().getReach().contains(objectId);
	}

	private List<String> firstLayerCandidates(QueryContext ctx) {
		List<String> ids = new ArrayList<String>();
		ids.add(ctx.getSeedId());
		List<String> children = treeChildren(ctx, ctx.getSeedId());
		int max = Math.min(children.size(), FIRST_LAYER_CHILDREN);
		for (int i = 0; i < max; i++) {
			ids.add(children.get(i));
		}
		return ids;
	}

	private List<String> treeChildren(QueryContext ctx, String parentId) {
		List<String> ids = new ArrayList<String>();
		ClassificationResult c = ctx.getClassification();
		if (c.getTreeStatus() != TreeStatus.AVAILABLE) {
			return ids;
		}
		for (Map.Entry<String, String> entry : c.getParentEdgeIds().entrySet()) {
			RelationRecord rel = ctx.getRelations().get(entry.getValue());
			if (rel != null && parentId.equals(rel.source) && visible(ctx, entry.getKey())) {
				ids.add(entry.getKey());
			}
		}
		sortObjectIds(ctx, ids);
		return ids;
	}

	private ProjectionResponseDto project(QueryContext ctx, List<String> candidateIds, String selectedId,
			Set<String> types, boolean reveal, int revision, String requestId) {
		List<String> reasons = new ArrayList<String>();
		LinkedHashSet<String> nodeIds = new LinkedHashSet<String>();
		if (types.isEmpty()) {
			reasons.add("TYPE_FILTER");
			return new ProjectionResponseDto(meta(requestId, ctx), revision, Collections.<GraphNodeDto>emptyList(),
				Collections.<GraphEdgeDto>emptyList(), stats(ctx).getDownstream(), false, reasons);
		}
		boolean filtered = false;
		for (int i = 0; i < candidateIds.size(); i++) {
			String id = candidateIds.get(i);
			CatalogObject obj = ctx.getObjects().get(id);
			if (obj == null) {
				continue;
			}
			if (id.equals(ctx.getSeedId()) || types.contains(obj.type)) {
				nodeIds.add(id);
			}
			else {
				filtered = true;
			}
		}
		if (filtered) {
			reasons.add("TYPE_FILTER");
		}
		if (reveal) {
			String cursor = selectedId;
			ClassificationResult c = ctx.getClassification();
			while (cursor != null && !cursor.equals(ctx.getSeedId())) {
				if (!nodeIds.contains(cursor)) {
					CatalogObject obj = ctx.getObjects().get(cursor);
					if (obj != null && !types.contains(obj.type)) {
						if (!reasons.contains("PATH_REQUIRES_HIDDEN_TYPES")) {
							reasons.add("PATH_REQUIRES_HIDDEN_TYPES");
						}
					}
					nodeIds.add(cursor);
				}
				String parentEdge = c.getParentEdgeIds().get(cursor);
				if (parentEdge == null) {
					break;
				}
				RelationRecord rel = ctx.getRelations().get(parentEdge);
				cursor = rel == null ? null : rel.source;
			}
			nodeIds.add(ctx.getSeedId());
		}
		if (ctx.getClassification().getTreeStatus() != TreeStatus.AVAILABLE) {
			reasons.add("LAYOUT_UNAVAILABLE");
		}
		if (nodeIds.size() > NODE_BUDGET) {
			throw ApiException.projectionLimit("projection exceeds 200 objects");
		}
		List<GraphNodeDto> nodes = new ArrayList<GraphNodeDto>();
		for (String id : nodeIds) {
			nodes.add(toNode(ctx, id));
		}
		List<GraphEdgeDto> edges = new ArrayList<GraphEdgeDto>();
		for (RelationRecord rel : ctx.getRelations().values()) {
			if (!nodeIds.contains(rel.source) || !nodeIds.contains(rel.target)) {
				continue;
			}
			if (classified(ctx, rel.id) == null) {
				continue;
			}
			edges.add(toEdge(ctx, rel.id));
		}
		if (edges.size() > EDGE_BUDGET) {
			throw ApiException.projectionLimit("projection exceeds 2000 relations");
		}
		return new ProjectionResponseDto(meta(requestId, ctx), revision, nodes, edges, stats(ctx).getDownstream(),
			false, reasons);
	}

	private NodePageDto nodePage(QueryContext ctx, List<String> ids, String cursor, int limit, String requestId) {
		if (cursor != null && cursor.length() > 4096) {
			throw ApiException.cursorMismatch("cursor is invalid");
		}
		int from = 0;
		if (cursor != null && !cursor.isEmpty()) {
			int idx = ids.indexOf(cursor);
			if (idx < 0) {
				throw ApiException.cursorMismatch("cursor is invalid");
			}
			from = idx + 1;
		}
		boolean hasMore = ids.size() > from + limit;
		int to = Math.min(ids.size(), from + limit);
		List<GraphNodeDto> items = new ArrayList<GraphNodeDto>();
		for (int i = from; i < to; i++) {
			items.add(toNode(ctx, ids.get(i)));
		}
		String next = hasMore && to > from ? ids.get(to - 1) : null;
		return new NodePageDto(meta(requestId, ctx), items, new PageInfo(next, hasMore, Integer.valueOf(ids.size())));
	}

	private StatsDto stats(QueryContext ctx) {
		ClassificationResult c = ctx.getClassification();
		int table = 0;
		int view = 0;
		int procedure = 0;
		int java = 0;
		int javaTerminals = 0;
		Set<String> withOut = new HashSet<String>();
		for (RelationRecord rel : ctx.getRelations().values()) {
			if (classified(ctx, rel.id) != null) {
				withOut.add(rel.source);
			}
		}
		int leaves = 0;
		for (String id : c.getReach()) {
			CatalogObject obj = ctx.getObjects().get(id);
			if (obj == null) {
				continue;
			}
			if (!withOut.contains(id)) {
				leaves++;
			}
			if (id.equals(ctx.getSeedId())) {
				continue;
			}
			if ("table".equals(obj.type)) {
				table++;
			}
			else if ("view".equals(obj.type)) {
				view++;
			}
			else if ("procedure".equals(obj.type)) {
				procedure++;
			}
			else if ("java".equals(obj.type)) {
				java++;
			}
			if ("java".equals(obj.type)) {
				javaTerminals++;
			}
		}
		Integer cross = c.getTreeStatus() == TreeStatus.AVAILABLE ? Integer.valueOf(c.getCrossIds().size()) : null;
		String countStatus = "complete".equals(ctx.getCoverage()) ? "exact" : "lower_bound";
		int downstream = Math.max(0, c.getReach().size() - (c.getReach().contains(ctx.getSeedId()) ? 1 : 0));
		return new StatsDto(Integer.valueOf(downstream), Integer.valueOf(javaTerminals), Integer.valueOf(leaves),
			cross, new TypeCounts(Integer.valueOf(table), Integer.valueOf(view), Integer.valueOf(procedure),
				Integer.valueOf(java)),
			countStatus);
	}

	private MetaDto meta(String requestId, QueryContext ctx) {
		return new MetaDto(requestId, ctx.getQueryId(), ctx.getSnapshotId(), ctx.getCoverage(), "complete",
			ctx.getClassification().getTreeStatus().wire(), QueryContext.ALGORITHM_VERSION,
			ctx.expiresAt(Instant.now()));
	}

	private GraphNodeDto toNode(QueryContext ctx, String id) {
		CatalogObject obj = ctx.getObjects().get(id);
		ClassificationResult c = ctx.getClassification();
		Integer hops = c.getMinHops().get(id);
		int minHops = hops == null ? 0 : hops.intValue();
		boolean ranked = c.getTreeStatus() == TreeStatus.AVAILABLE;
		Integer layoutRank = ranked ? c.getLayoutRank().get(id) : null;
		String parentEdgeId = ranked ? c.getParentEdgeIds().get(id) : null;
		Integer treeChildCount = ranked ? Integer.valueOf(treeChildren(ctx, id).size()) : null;
		Integer crossCount = ranked ? Integer.valueOf(incidentCross(ctx, id)) : null;
		return new GraphNodeDto(toObject(obj), minHops, layoutRank, parentEdgeId, treeChildCount, crossCount);
	}

	private int incidentCross(QueryContext ctx, String id) {
		int n = 0;
		for (String edgeId : ctx.getClassification().getCrossIds()) {
			RelationRecord rel = ctx.getRelations().get(edgeId);
			if (rel != null && (id.equals(rel.source) || id.equals(rel.target))) {
				n++;
			}
		}
		return n;
	}

	private GraphEdgeDto toEdge(QueryContext ctx, String relationId) {
		RelationRecord rel = ctx.getRelations().get(relationId);
		String kind = classified(ctx, relationId);
		if (kind == null) {
			kind = "unclassified";
		}
		return new GraphEdgeDto(new RelationDto(rel.id, rel.source, rel.target, rel.rel, rel.sourceOrder,
			rel.evidenceIds), kind);
	}

	private String classified(QueryContext ctx, String relationId) {
		ClassificationResult c = ctx.getClassification();
		if (c.getTreeStatus() != TreeStatus.AVAILABLE) {
			RelationRecord rel = ctx.getRelations().get(relationId);
			if (rel == null) {
				return null;
			}
			if (c.getReach().contains(rel.source) && c.getReach().contains(rel.target)
					&& !c.getExcludedRelationIds().contains(relationId)) {
				return "unclassified";
			}
			return null;
		}
		if (c.getTreeIds().contains(relationId)) {
			return "tree";
		}
		if (c.getCrossIds().contains(relationId)) {
			return "cross";
		}
		return null;
	}

	private static ObjectDto toObject(CatalogObject obj) {
		return new ObjectDto(obj.id, obj.type, obj.namespace, obj.technicalName, obj.displayName, obj.system,
			obj.owner, obj.javaKind);
	}

	private static void sortObjectIds(final QueryContext ctx, List<String> ids) {
		Collections.sort(ids, new Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				return compareCatalog(ctx.getObjects().get(a), ctx.getObjects().get(b));
			}
		});
	}

	private static void sortRanked(List<RankedHit> hits) {
		Collections.sort(hits, new Comparator<RankedHit>() {
			@Override
			public int compare(RankedHit a, RankedHit b) {
				int c = compareCatalog(a.obj, b.obj);
				if (c != 0) {
					return c;
				}
				int s = a.snapshotId.compareTo(b.snapshotId);
				if (s != 0) {
					return s;
				}
				return a.scopeId.compareTo(b.scopeId);
			}
		});
	}

	private static int compareCatalog(CatalogObject a, CatalogObject b) {
		if (a == null && b == null) {
			return 0;
		}
		if (a == null) {
			return 1;
		}
		if (b == null) {
			return -1;
		}
		int t = Integer.compare(typeOrder(a.type), typeOrder(b.type));
		if (t != 0) {
			return t;
		}
		int n = a.technicalName.compareTo(b.technicalName);
		if (n != 0) {
			return n;
		}
		return a.id.compareTo(b.id);
	}

	private static int typeOrder(String type) {
		for (int i = 0; i < TYPE_ORDER.length; i++) {
			if (TYPE_ORDER[i].equals(type)) {
				return i;
			}
		}
		return 9;
	}

	private static List<String> allTypes() {
		List<String> types = new ArrayList<String>();
		for (int i = 0; i < TYPE_ORDER.length; i++) {
			types.add(TYPE_ORDER[i]);
		}
		return types;
	}

	private static Set<String> typeFilter(List<String> types) {
		if (types == null || types.isEmpty()) {
			return Collections.emptySet();
		}
		Set<String> set = new LinkedHashSet<String>();
		for (int i = 0; i < types.size(); i++) {
			String type = types.get(i);
			if (typeOrder(type) > 3) {
				throw ApiException.invalidArgument("unknown object type");
			}
			set.add(type);
		}
		return set;
	}

	private int offsetAfter(List<RankedHit> hits, String cursor) {
		if (cursor == null || cursor.isEmpty()) {
			return 0;
		}
		for (int i = 0; i < hits.size(); i++) {
			if (cursor.equals(cursorOf(hits.get(i)))) {
				return i + 1;
			}
		}
		throw ApiException.cursorMismatch("cursor is invalid");
	}

	private static String cursorOf(RankedHit hit) {
		return hit.snapshotId + "\t" + hit.obj.id;
	}

	private static final class RankedHit {
		private final CatalogObject obj;
		private final String scopeId;
		private final String snapshotId;

		private RankedHit(CatalogObject obj, String scopeId, String snapshotId) {
			this.obj = obj;
			this.scopeId = scopeId;
			this.snapshotId = snapshotId;
		}
	}

	private int offsetCluster(List<ClusterDto> clusters, String cursor) {
		if (cursor == null || cursor.isEmpty()) {
			return 0;
		}
		if (cursor.length() > 4096) {
			throw ApiException.cursorMismatch("cursor is invalid");
		}
		for (int i = 0; i < clusters.size(); i++) {
			if (cursor.equals(clusters.get(i).getId())) {
				return i + 1;
			}
		}
		throw ApiException.cursorMismatch("cursor is invalid");
	}

	private int offsetEdge(List<GraphEdgeDto> edges, String cursor) {
		if (cursor == null || cursor.isEmpty()) {
			return 0;
		}
		if (cursor.length() > 4096) {
			throw ApiException.cursorMismatch("cursor is invalid");
		}
		for (int i = 0; i < edges.size(); i++) {
			if (cursor.equals(edges.get(i).getRelation().getId())) {
				return i + 1;
			}
		}
		throw ApiException.cursorMismatch("cursor is invalid");
	}

	private static Integer parseRank(String value) {
		try {
			return Integer.valueOf(Integer.parseInt(value));
		}
		catch (NumberFormatException e) {
			throw ApiException.notFound("not found");
		}
	}

	private static String likePattern(String q) {
		return q;
	}

	private static String requireText(String value, String name, int min, int max) {
		if (value == null || value.length() < min || value.length() > max) {
			throw ApiException.invalidArgument(name + " is invalid");
		}
		return value;
	}

	private static int bound(Integer value, int defaultValue, int min, int max, String name) {
		int n = value == null ? defaultValue : value.intValue();
		if (n < min || n > max) {
			throw ApiException.invalidArgument(name + " must be between " + min + " and " + max);
		}
		return n;
	}
}
