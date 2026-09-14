/**
 * Cross / unclassified edges derived only from projection kinds.
 * Expand order does not matter: the set is the projection edge list.
 */
export function listNonTreeEdges(projection) {
  var edges = (projection && projection.edges) || []
  var out = []
  var seen = {}
  var i
  var e
  var id
  for (i = 0; i < edges.length; i++) {
    e = edges[i]
    if (!e || !e.relation || !e.relation.id) {
      continue
    }
    if (e.kind !== 'cross' && e.kind !== 'unclassified') {
      continue
    }
    id = e.relation.id
    if (seen[id]) {
      continue
    }
    seen[id] = true
    out.push(e)
  }
  out.sort(function (a, b) {
    var left = a.relation.id
    var right = b.relation.id
    if (left < right) {
      return -1
    }
    if (left > right) {
      return 1
    }
    return 0
  })
  return out
}

export function incidentNonTreeEdges(projection, nodeId) {
  var all = listNonTreeEdges(projection)
  if (!nodeId) {
    return all
  }
  var out = []
  var i
  var rel
  for (i = 0; i < all.length; i++) {
    rel = all[i].relation
    if (rel.source === nodeId || rel.target === nodeId) {
      out.push(all[i])
    }
  }
  return out
}

export function otherEndpoint(edge, fromId) {
  var rel = edge && edge.relation
  if (!rel) {
    return null
  }
  if (fromId && rel.source === fromId) {
    return rel.target || null
  }
  if (fromId && rel.target === fromId) {
    return rel.source || null
  }
  if (fromId && rel.target === fromId && rel.source === fromId) {
    return fromId
  }
  return rel.target || rel.source || null
}

export function edgeIncidentTo(edge, nodeId) {
  var rel = edge && edge.relation
  if (!rel || !nodeId) {
    return false
  }
  return rel.source === nodeId || rel.target === nodeId
}
