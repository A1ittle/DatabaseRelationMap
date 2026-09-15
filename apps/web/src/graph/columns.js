/**
 * Group projection nodes into OpenDesign hops columns by layoutRank.
 */
import { layoutPositions } from './layout.js'

export var COL_CAP = 5

function objectType(node) {
  return (node && node.object && node.object.type) || ''
}

export function isLeafColumn(nodes) {
  var i
  if (!nodes || !nodes.length) {
    return false
  }
  for (i = 0; i < nodes.length; i++) {
    if (objectType(nodes[i]) !== 'java') {
      return false
    }
  }
  return true
}

export function columnLabel(rank, nodes) {
  if (Number(rank) === 0) {
    return '当前表'
  }
  if (isLeafColumn(nodes)) {
    return '第 ' + rank + ' 层 · 终端'
  }
  return '第 ' + rank + ' 层'
}

export function groupColumns(projection) {
  var positions = layoutPositions(projection)
  var nodes = (projection && projection.nodes) || []
  var byRank = {}
  var i
  var n
  var id
  var rank
  var ranks
  var cols
  var list

  for (i = 0; i < nodes.length; i++) {
    n = nodes[i]
    id = n && n.object && n.object.id
    if (!id || !positions[id]) {
      continue
    }
    rank = positions[id].layoutRank
    if (!byRank[rank]) {
      byRank[rank] = []
    }
    byRank[rank].push(n)
  }

  ranks = Object.keys(byRank)
    .map(Number)
    .sort(function (a, b) {
      return a - b
    })
  cols = []
  for (i = 0; i < ranks.length; i++) {
    rank = ranks[i]
    list = byRank[rank]
    list.sort(function (a, b) {
      return positions[a.object.id].y - positions[b.object.id].y
    })
    cols.push({
      rank: rank,
      label: columnLabel(rank, list),
      leaf: isLeafColumn(list),
      nodes: list
    })
  }
  return cols
}

export function sliceColumn(nodes, open, cap) {
  var limit = cap == null ? COL_CAP : cap
  if (open || !nodes || nodes.length <= limit) {
    return { shown: nodes || [], rest: 0, open: !!open }
  }
  return { shown: nodes.slice(0, limit), rest: nodes.length - limit, open: false }
}
