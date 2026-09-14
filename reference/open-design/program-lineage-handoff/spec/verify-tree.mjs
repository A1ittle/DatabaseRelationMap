#!/usr/bin/env node
/**
 * 对照 fixtures/seed-ods-trade-order.expected.json 回归拆树语义。
 * 用法：在本包根目录执行  node spec/verify-tree.mjs
 */
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildTree, shortestDown, edgeKey } from './build-tree.mjs'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const graph = JSON.parse(readFileSync(join(root, 'fixtures/small-graph.json'), 'utf8'))
const expected = JSON.parse(
  readFileSync(join(root, 'fixtures/seed-ods-trade-order.expected.json'), 'utf8'),
)

const fail = []
const ok = (cond, msg) => {
  if (!cond) fail.push(msg)
}

const tree = buildTree(graph.nodes, graph.edges, expected.seed)
const reach = new Set(tree.reach)

ok(reach.has(expected.seed), 'seed 必须在 reach 中')
for (const id of expected.notInTree) {
  ok(!reach.has(id), `侧输入/上游不应进树: ${id}`)
}

const byId = Object.fromEntries(expected.nodes.map((n) => [n.id, n]))
ok(expected.nodes.length === tree.reach.length, `reach 数量 ${tree.reach.length} != ${expected.nodes.length}`)
for (const n of expected.nodes) {
  ok(reach.has(n.id), `缺节点 ${n.id}`)
  ok(tree.rank[n.id] === n.rank, `${n.id} rank ${tree.rank[n.id]} != ${n.rank}`)
  ok((tree.parent[n.id] || null) === n.parent, `${n.id} parent ${tree.parent[n.id]} != ${n.parent}`)
  const rel = tree.parentEdge[n.id]?.rel || null
  ok(rel === n.rel, `${n.id} parent rel ${rel} != ${n.rel}`)
}

const treeSet = new Set(tree.tree.map(edgeKey))
const expTree = new Set(expected.tree.map(edgeKey))
ok(treeSet.size === expTree.size, `tree 边数 ${treeSet.size} != ${expTree.size}`)
for (const k of expTree) ok(treeSet.has(k), `缺主依赖 ${k}`)
for (const k of treeSet) ok(expTree.has(k), `多主依赖 ${k}`)

const crossSet = new Set(tree.cross.map(edgeKey))
const expCross = new Set(expected.cross.map(edgeKey))
ok(crossSet.size === expCross.size, `cross 边数 ${crossSet.size} != ${expCross.size}`)
for (const k of expCross) ok(crossSet.has(k), `缺跨支 ${k}`)
for (const k of crossSet) ok(expCross.has(k), `多跨支 ${k}`)

const path = shortestDown(graph.nodes, graph.edges, tree, expected.seed, 'GmvReportService')
ok(!!path, '到 GmvReportService 的最短路径应存在')
if (path) {
  ok(path.path.join('>') === expected.shortestPathToGmv.path.join('>'), `最短路径 ${path.path.join('>')} 不符`)
  ok(path.edges[1]?.kind === 'cross', '最短路径第二跳必须是跨支')
}

if (fail.length) {
  console.error('FAIL')
  fail.forEach((m) => console.error(' -', m))
  process.exit(1)
}
console.log('OK  seed=ods.trade_order  reach=%d tree=%d cross=%d', tree.reach.length, tree.tree.length, tree.cross.length)
