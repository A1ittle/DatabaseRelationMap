/**
 * Presentation coordinates from GraphNode.layoutRank.
 * Domain parent/tree/cross stays in the API; this file only places cards.
 * x = layoutRank * COL_GAP; y is a stable order within the rank.
 */
export var COL_GAP = 352
export var ROW_HEIGHT = 76
export var ROW_GAP = 28

function nodeId(node) {
  return node && node.object && node.object.id
}

function sortKey(node) {
  var obj = (node && node.object) || {}
  return String(obj.technicalName || obj.displayName || obj.id || '')
}

export function layoutPositions(projection) {
  var nodes = (projection && projection.nodes) || []
  var byRank = {}
  var i
  var n
  var rank
  var key
  var ids
  var col
  var yStep = ROW_HEIGHT + ROW_GAP
  var positions = {}

  for (i = 0; i < nodes.length; i++) {
    n = nodes[i]
    key = nodeId(n)
    if (!key) {
      continue
    }
    rank = n.layoutRank == null ? 0 : Number(n.layoutRank)
    if (!isFinite(rank)) {
      rank = 0
    }
    if (!byRank[rank]) {
      byRank[rank] = []
    }
    byRank[rank].push(n)
  }

  ids = Object.keys(byRank)
  ids.sort(function (a, b) {
    return Number(a) - Number(b)
  })

  for (i = 0; i < ids.length; i++) {
    col = byRank[ids[i]]
    col.sort(function (a, b) {
      var ka = sortKey(a)
      var kb = sortKey(b)
      if (ka < kb) {
        return -1
      }
      if (ka > kb) {
        return 1
      }
      var ia = nodeId(a)
      var ib = nodeId(b)
      if (ia < ib) {
        return -1
      }
      if (ia > ib) {
        return 1
      }
      return 0
    })
    rank = Number(ids[i])
    col.forEach(function (node, index) {
      positions[nodeId(node)] = {
        x: rank * COL_GAP,
        y: index * yStep,
        layoutRank: rank
      }
    })
  }

  return positions
}
