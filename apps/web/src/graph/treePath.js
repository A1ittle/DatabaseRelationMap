/** Main-tree path from a node up to the seed, plus highlight membership. */

export function treePathIds(parentOf, id, seedId) {
  var path = []
  var guard = {}
  var cur = id
  while (cur && !guard[cur]) {
    path.unshift(cur)
    guard[cur] = true
    if (seedId && cur === seedId) {
      break
    }
    cur = parentOf && parentOf[cur]
  }
  return path
}

export function highlightIdSet(parentOf, selectedId, seedId, incidentCross) {
  var set = {}
  var path
  var i
  var rel
  if (!selectedId || selectedId === seedId) {
    return null
  }
  path = treePathIds(parentOf, selectedId, seedId)
  for (i = 0; i < path.length; i++) {
    set[path[i]] = true
  }
  incidentCross = incidentCross || []
  for (i = 0; i < incidentCross.length; i++) {
    rel = incidentCross[i] && incidentCross[i].relation
    if (!rel) {
      continue
    }
    if (rel.source) {
      set[rel.source] = true
    }
    if (rel.target) {
      set[rel.target] = true
    }
  }
  return set
}

export function edgeOnPath(kind, source, target, selectedId, pathSet) {
  var tree = kind === 'tree'
  var cross = kind === 'cross' || kind === 'unclassified'
  if (tree && pathSet && pathSet[source] && pathSet[target]) {
    return true
  }
  if (cross && selectedId && (source === selectedId || target === selectedId)) {
    return true
  }
  return false
}
