/**
 * Counts actually rendered on the current projection, not server
 * stats.downstream. Layout/domain classification is not recomputed here.
 */
export function drawnStats(projection) {
  var nodes = (projection && projection.nodes) || []
  var edges = (projection && projection.edges) || []
  var tree = 0
  var cross = 0
  var unclassified = 0
  var other = 0
  var i
  var kind
  for (i = 0; i < edges.length; i++) {
    kind = edges[i] && edges[i].kind
    if (kind === 'tree') {
      tree += 1
    } else if (kind === 'cross') {
      cross += 1
    } else if (kind === 'unclassified') {
      unclassified += 1
    } else {
      other += 1
    }
  }
  return {
    nodes: nodes.length,
    edges: edges.length,
    tree: tree,
    cross: cross,
    unclassified: unclassified,
    other: other
  }
}
