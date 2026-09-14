/** 程序血缘地图 · 领域类型。与 prototype/lineage-map-v2.html 语义一致。 */

export type NodeType = 'table' | 'view' | 'procedure' | 'java'
export type Rel = 'reads' | 'writes' | 'calls' | 'derives'
export type NodeStatus = 'verified' | 'inferred'
export type EdgeKind = 'tree' | 'cross'
export type Mode = 'focus' | 'overview' | 'impact' | 'path'
export type Direction = 'downstream'

export const REL_LABEL: Record<Rel, string> = {
  reads: '读取',
  writes: '写入',
  calls: '调用',
  derives: '派生',
}

export const TYPE_LABEL: Record<NodeType, string> = {
  table: '表',
  view: '视图',
  procedure: '存储过程',
  java: 'Java 程序',
}

/** 并列 hop 时的决胜。更长路径仍然优先。 */
export const STRENGTH: Record<Rel, number> = {
  writes: 3,
  derives: 2,
  calls: 1,
  reads: 0,
}

export interface Node {
  id: string
  name: string
  title: string
  type: NodeType
  owner: string
  system: string
  status: NodeStatus
  desc?: string
}

export interface Edge {
  from: string
  to: string
  rel: Rel
}

export interface ClassifiedEdge extends Edge {
  kind: EdgeKind
}

export interface LineageTree {
  seed: string
  reach: string[]
  rank: Record<string, number>
  parent: Record<string, string>
  parentEdge: Record<string, Edge>
  tree: Edge[]
  cross: Edge[]
  children: Record<string, string[]>
  crossOf: Record<string, Edge[]>
}

export interface NodeStats {
  downstream: number
  leaves: number
  crossCount: number
  byType: Record<NodeType, number>
}

export interface LineageNodeDTO extends Node {
  childCount: number
  crossCount: number
  hasMore: boolean
}

export interface LineageResponse {
  seed: Node
  stats: NodeStats
  nodes: LineageNodeDTO[]
  tree: ClassifiedEdge[]
  cross: ClassifiedEdge[]
  rank: Record<string, number>
  parent: Record<string, string>
  truncated: boolean
}

export interface SearchHit {
  id: string
  name: string
  title: string
  type: NodeType
  owner: string
  inDownstream: boolean
}

export interface PathResponse {
  from: string
  to: string
  found: boolean
  nodes: Node[]
  edges: ClassifiedEdge[]
}

export interface AppState {
  seed: string
  sel: string
  mode: Mode
  types: Record<NodeType, boolean>
  depth: 1 | 2 | 99
  q: string
  pathTo: string
  expanded: Record<string, true>
  openCols: Record<string, true>
  openGroups: Record<string, true>
  inspectOpen: boolean
}
