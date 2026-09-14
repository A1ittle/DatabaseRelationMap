// 只诊断保留的参考实现，不修改或替代生产算法。
import { buildTree } from '../reference/open-design/program-lineage-handoff/spec/build-tree.mjs';
import { readFileSync } from 'node:fs';
const n = (id,type='view') => ({id,type});
const e = (from,to,rel='reads') => ({from,to,rel});
const summarize = (nodes,edges) => {
  const t=buildTree(nodes,edges,'r');
  return {reach:t.reach,rank:t.rank,parent:t.parent,tree:t.tree,cross:t.cross,children:t.children};
};
const report={
  parallelRelations:summarize([n('r','table'),n('a')],[e('r','a'),e('r','a','derives')]),
  selfLoop:summarize([n('r','table'),n('a'),n('b')],[e('r','a'),e('a','a'),e('a','b')]),
  cycleWithTail:summarize([n('r','table'),n('a'),n('b'),n('c')],[e('r','a'),e('a','b'),e('b','a'),e('b','c')]),
  tieOrder1:summarize([n('r','table'),n('a'),n('b'),n('z')],[e('r','a'),e('r','b'),e('a','z'),e('b','z')]),
  tieOrder2:summarize([n('r','table'),n('a'),n('b'),n('z')],[e('r','b'),e('r','a'),e('a','z'),e('b','z')])
};
const graph=JSON.parse(readFileSync(new URL('../reference/open-design/program-lineage-handoff/fixtures/small-graph.json',import.meta.url)));
report.fixtureCounts={nodes:graph.nodes.length,edges:graph.edges.length};
console.log(JSON.stringify(report,null,2));
