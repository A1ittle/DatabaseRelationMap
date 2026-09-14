/**
 * Build a main-tree adjacency list from a full projection replace.
 * Tree children follow parentEdgeId → that edge's source.
 * Cross / unclassified edges stay on the node for CSS distinction.
 */
export function kindClass(kind) {
  if (kind === 'tree') {
    return 'kind-tree'
  }
  if (kind === 'cross') {
    return 'kind-cross'
  }
  if (kind === 'unclassified') {
    return 'kind-unclassified'
  }
  return 'kind-unknown'
}

export function indexProjection(projection) {
  var nodesById = {}
  var edgesById = {}
  var treeChildren = {}
  var parentOf = {}
  var incident = {}
  var nodes = (projection && projection.nodes) || []
  var edges = (projection && projection.edges) || []
  var i
  var n
  var id
  var e
  var rel
  var pid
  var parentId
  var childId

  for (i = 0; i < nodes.length; i++) {
    n = nodes[i]
    if (!n || !n.object || !n.object.id) {
      continue
    }
    id = n.object.id
    nodesById[id] = n
    treeChildren[id] = []
    incident[id] = []
  }

  for (i = 0; i < edges.length; i++) {
    e = edges[i]
    rel = e && e.relation
    if (!rel || !rel.id) {
      continue
    }
    edgesById[rel.id] = e
    if (incident[rel.source]) {
      incident[rel.source].push(e)
    }
    if (rel.target !== rel.source && incident[rel.target]) {
      incident[rel.target].push(e)
    }
  }

  for (i = 0; i < nodes.length; i++) {
    n = nodes[i]
    if (!n || !n.object) {
      continue
    }
    pid = n.parentEdgeId
    if (!pid) {
      continue
    }
    e = edgesById[pid]
    if (!e || !e.relation) {
      continue
    }
    parentId = e.relation.source
    childId = n.object.id
    parentOf[childId] = parentId
    if (treeChildren[parentId]) {
      treeChildren[parentId].push(childId)
    }
  }

  return {
    nodesById: nodesById,
    edgesById: edgesById,
    treeChildren: treeChildren,
    parentOf: parentOf,
    incident: incident,
    clientRevision: projection && projection.clientRevision,
    matchedDownstream: projection && projection.matchedDownstream,
    renderLimited: projection && projection.renderLimited,
    reasons: (projection && projection.reasons) || []
  }
}

export function findRoots(index, seedId) {
  if (seedId && index.nodesById[seedId]) {
    return [seedId]
  }
  var ids = Object.keys(index.nodesById)
  var roots = []
  var i
  for (i = 0; i < ids.length; i++) {
    if (!index.parentOf[ids[i]]) {
      roots.push(ids[i])
    }
  }
  return roots
}

export function candidateIdsFromProjection(projection) {
  var nodes = (projection && projection.nodes) || []
  var ids = []
  var seen = {}
  var i
  var id
  for (i = 0; i < nodes.length; i++) {
    if (!nodes[i] || !nodes[i].object) {
      continue
    }
    id = nodes[i].object.id
    if (!id || seen[id]) {
      continue
    }
    seen[id] = true
    ids.push(id)
  }
  return ids
}

export function mergeCandidateIds(existing, extra, seedId) {
  var seen = {}
  var out = []
  var i
  var id
  var lists = [existing || [], extra || []]
  var li
  var list
  if (seedId) {
    out.push(seedId)
    seen[seedId] = true
  }
  for (li = 0; li < lists.length; li++) {
    list = lists[li]
    for (i = 0; i < list.length; i++) {
      id = list[i]
      if (!id || seen[id]) {
        continue
      }
      seen[id] = true
      out.push(id)
    }
  }
  return out
}

export function crossEdgesFor(index, nodeId) {
  var list = (index.incident && index.incident[nodeId]) || []
  var out = []
  var i
  for (i = 0; i < list.length; i++) {
    if (list[i].kind === 'cross' || list[i].kind === 'unclassified') {
      out.push(list[i])
    }
  }
  return out
}
