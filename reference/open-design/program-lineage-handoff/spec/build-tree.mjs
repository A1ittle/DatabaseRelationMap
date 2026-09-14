/**
 * 与 prototype/lineage-map-v2.html 中 liveEdges / buildTree / shortestDown 同语义。
 * 供后端对照，以及 node spec/verify-tree.mjs 回归。
 */

export const STRENGTH = { writes: 3, derives: 2, calls: 1, reads: 0 }

export function liveEdges(nodes, edges) {
  const byId = Object.fromEntries(nodes.map((n) => [n.id, n]))
  return edges.filter((e) => byId[e.from] && byId[e.to] && byId[e.from].type !== 'java')
}

export function buildTree(nodes, edges, seed) {
  const byId = Object.fromEntries(nodes.map((n) => [n.id, n]))
  if (!byId[seed] || byId[seed].type !== 'table') {
    throw new Error('INVALID_SEED')
  }
  const usable = liveEdges(nodes, edges)
  const out = {}
  usable.forEach((e) => {
    ;(out[e.from] || (out[e.from] = [])).push(e)
  })

  const reach = new Set([seed])
  const q = [seed]
  while (q.length) {
    const cur = q.shift()
    ;(out[cur] || []).forEach((e) => {
      if (!reach.has(e.to)) {
        reach.add(e.to)
        q.push(e.to)
      }
    })
  }

  const indeg = {}
  reach.forEach((id) => {
    indeg[id] = 0
  })
  usable.forEach((e) => {
    if (reach.has(e.from) && reach.has(e.to)) indeg[e.to]++
  })

  const rank = new Map([[seed, 0]])
  const parent = new Map()
  const parentEdge = new Map()
  const tq = []
  reach.forEach((id) => {
    if (indeg[id] === 0) tq.push(id)
  })
  const seen = new Set()
  while (tq.length) {
    const u = tq.shift()
    if (seen.has(u)) continue
    seen.add(u)
    ;(out[u] || []).forEach((e) => {
      if (!reach.has(e.to)) return
      const cand = (rank.get(u) || 0) + 1
      const prev = rank.get(e.to)
      const prevRel = (parentEdge.get(e.to) || {}).rel
      const better =
        prev == null ||
        cand > prev ||
        (cand === prev && STRENGTH[e.rel] > STRENGTH[prevRel])
      if (better) {
        rank.set(e.to, cand)
        parent.set(e.to, u)
        parentEdge.set(e.to, e)
      }
      indeg[e.to]--
      if (indeg[e.to] === 0) tq.push(e.to)
    })
  }

  reach.forEach((id) => {
    if (!rank.has(id)) rank.set(id, id === seed ? 0 : 1)
  })

  const treeKey = new Set()
  parentEdge.forEach((e) => treeKey.add(e.from + '>' + e.to))
  const tree = []
  const cross = []
  usable.forEach((e) => {
    if (!reach.has(e.from) || !reach.has(e.to) || e.from === e.to) return
    ;(treeKey.has(e.from + '>' + e.to) ? tree : cross).push(e)
  })

  const children = {}
  tree.forEach((e) => {
    ;(children[e.from] || (children[e.from] = [])).push(e.to)
  })
  const crossOf = {}
  cross.forEach((e) => {
    ;(crossOf[e.from] || (crossOf[e.from] = [])).push(e)
    ;(crossOf[e.to] || (crossOf[e.to] = [])).push(e)
  })

  return {
    seed,
    rank: Object.fromEntries(rank),
    parent: Object.fromEntries(parent),
    parentEdge: Object.fromEntries(parentEdge),
    reach: [...reach],
    tree,
    cross,
    children,
    crossOf,
    treeKey: [...treeKey],
  }
}

export function shortestDown(nodes, edges, tree, from, to) {
  if (from === to) return { path: [from], edges: [] }
  const prev = new Map()
  const q = [from]
  const seen = new Set([from])
  const reach = new Set(tree.reach)
  const usable = liveEdges(nodes, edges).filter((e) => reach.has(e.from) && reach.has(e.to))
  const treeKey = new Set(tree.tree.map((e) => e.from + '>' + e.to))
  while (q.length) {
    const cur = q.shift()
    for (const e of usable) {
      if (e.from !== cur || seen.has(e.to)) continue
      seen.add(e.to)
      prev.set(e.to, { id: cur, e })
      q.push(e.to)
      if (e.to === to) {
        const path = [to]
        const pathEdges = []
        while (path[0] !== from) {
          const p = prev.get(path[0])
          pathEdges.unshift({
            ...p.e,
            kind: treeKey.has(p.e.from + '>' + p.e.to) ? 'tree' : 'cross',
          })
          path.unshift(p.id)
        }
        return { path, edges: pathEdges }
      }
    }
  }
  return null
}

export function edgeKey(e) {
  return e.from + '>' + e.to + ':' + e.rel
}
