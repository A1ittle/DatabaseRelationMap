/**
 * Client-side hop depth for the focus canvas.
 * Seed (layoutRank 0) is always kept. Default depth is 1 so first paint
 * does not lay out every reachable rank.
 */

export var DEFAULT_DEPTH = 1
export var ALL_DEPTH = 99
export var OVERVIEW_FORCE_NODE_THRESHOLD = 40
export var EXPAND_EXTRA_RANKS = 1

export var DEPTH_SEGMENTS = [
  { value: 1, label: '1 层' },
  { value: 2, label: '2 层' },
  { value: 99, label: '全部 · 聚类' }
]

export function nodeLayoutRank(node) {
  if (!node || node.layoutRank == null) {
    return 0
  }
  var rank = Number(node.layoutRank)
  return isFinite(rank) ? rank : 0
}

export function nodePassesDepth(node, depth, extra) {
  var rank
  var id
  var parentId
  var limit
  extra = extra || {}
  rank = nodeLayoutRank(node)
  if (rank === 0) {
    return true
  }
  limit = Number(depth)
  if (!isFinite(limit) || limit >= ALL_DEPTH) {
    return true
  }
  if (rank <= limit) {
    return true
  }
  id = node && node.object && node.object.id
  parentId = id && extra.parentOf ? extra.parentOf[id] : null
  if (parentId && extra.expanded && extra.expanded[parentId] && rank <= limit + EXPAND_EXTRA_RANKS) {
    return true
  }
  return false
}

export function filterProjectionByDepth(projection, depth, extra) {
  var nodes
  var kept
  var ids
  var edges
  var i
  var n
  var id
  var rel
  var limit
  if (!projection) {
    return projection
  }
  limit = Number(depth)
  if (!isFinite(limit) || limit >= ALL_DEPTH) {
    return projection
  }
  nodes = projection.nodes || []
  kept = []
  ids = {}
  for (i = 0; i < nodes.length; i++) {
    n = nodes[i]
    if (!nodePassesDepth(n, limit, extra)) {
      continue
    }
    id = n && n.object && n.object.id
    if (!id) {
      continue
    }
    kept.push(n)
    ids[id] = true
  }
  edges = []
  for (i = 0; i < (projection.edges || []).length; i++) {
    rel = projection.edges[i] && projection.edges[i].relation
    if (!rel || !ids[rel.source] || !ids[rel.target]) {
      continue
    }
    edges.push(projection.edges[i])
  }
  return Object.assign({}, projection, { nodes: kept, edges: edges })
}

export function shouldForceOverview(opts) {
  var n
  opts = opts || {}
  if (Number(opts.depth) !== ALL_DEPTH) {
    return false
  }
  if (opts.renderLimited) {
    return true
  }
  n = opts.matchedDownstream
  if (n == null) {
    n = opts.statsDownstream
  }
  if (n == null) {
    n = opts.nodeCount
  }
  return Number(n) > OVERVIEW_FORCE_NODE_THRESHOLD
}
