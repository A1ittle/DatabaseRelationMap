import { describe, expect, it } from 'vitest'
import { groupColumns } from './columns.js'
import {
  ALL_DEPTH,
  DEFAULT_DEPTH,
  DEPTH_SEGMENTS,
  OVERVIEW_FORCE_NODE_THRESHOLD,
  filterProjectionByDepth,
  nodePassesDepth,
  shouldForceOverview
} from './depthFilter.js'

function node(id, rank, type) {
  return {
    object: { id: id, type: type || 'table', technicalName: id },
    layoutRank: rank
  }
}

var sample = {
  nodes: [
    node('seed', 0, 'table'),
    node('hop1', 1, 'view'),
    node('hop2', 2, 'procedure'),
    node('hop3', 3, 'java')
  ],
  edges: [
    { relation: { id: 'e1', source: 'seed', target: 'hop1' }, kind: 'tree' },
    { relation: { id: 'e2', source: 'hop1', target: 'hop2' }, kind: 'tree' },
    { relation: { id: 'e3', source: 'hop2', target: 'hop3' }, kind: 'tree' },
    { relation: { id: 'e4', source: 'seed', target: 'hop3' }, kind: 'cross' }
  ]
}

describe('DEPTH_SEGMENTS', function () {
  it('is the locked 1 / 2 / 全部 · 聚类 trio with default 1', function () {
    expect(DEFAULT_DEPTH).toBe(1)
    expect(DEPTH_SEGMENTS.map(function (s) { return s.value })).toEqual([1, 2, 99])
    expect(DEPTH_SEGMENTS[2].label).toBe('全部 · 聚类')
  })
})

describe('nodePassesDepth', function () {
  it('always keeps seed rank 0 and drops ranks beyond depth', function () {
    expect(nodePassesDepth(node('seed', 0), 1)).toBe(true)
    expect(nodePassesDepth(node('a', 1), 1)).toBe(true)
    expect(nodePassesDepth(node('b', 2), 1)).toBe(false)
    expect(nodePassesDepth(node('c', 2), 2)).toBe(true)
    expect(nodePassesDepth(node('d', 3), ALL_DEPTH)).toBe(true)
  })

  it('lets an expanded parent reveal one extra rank', function () {
    var child = node('hop2', 2, 'procedure')
    expect(
      nodePassesDepth(child, 1, {
        parentOf: { hop2: 'hop1' },
        expanded: { hop1: true }
      })
    ).toBe(true)
    expect(
      nodePassesDepth(node('hop3', 3, 'java'), 1, {
        parentOf: { hop3: 'hop2' },
        expanded: { hop2: true }
      })
    ).toBe(false)
  })
})

describe('filterProjectionByDepth', function () {
  it('keeps only layoutRank ≤ depth columns on default depth=1', function () {
    var next = filterProjectionByDepth(sample, DEFAULT_DEPTH)
    expect(next.nodes.map(function (n) { return n.object.id })).toEqual(['seed', 'hop1'])
    expect(next.edges.map(function (e) { return e.relation.id })).toEqual(['e1'])
    expect(groupColumns(next).map(function (c) { return c.rank })).toEqual([0, 1])
  })

  it('does not mutate the input projection', function () {
    var before = sample.nodes.length
    filterProjectionByDepth(sample, 1)
    expect(sample.nodes.length).toBe(before)
  })

  it('is a no-op at 全部 · 聚类', function () {
    expect(filterProjectionByDepth(sample, ALL_DEPTH)).toBe(sample)
  })
})

describe('shouldForceOverview', function () {
  it('forces overview only at depth 99 on a large or limited graph', function () {
    expect(shouldForceOverview({ depth: 1, statsDownstream: 500 })).toBe(false)
    expect(
      shouldForceOverview({
        depth: ALL_DEPTH,
        matchedDownstream: OVERVIEW_FORCE_NODE_THRESHOLD
      })
    ).toBe(false)
    expect(shouldForceOverview({ depth: ALL_DEPTH, matchedDownstream: 41 })).toBe(true)
    expect(shouldForceOverview({ depth: ALL_DEPTH, statsDownstream: 520 })).toBe(true)
    expect(shouldForceOverview({ depth: ALL_DEPTH, nodeCount: 12 })).toBe(false)
    expect(shouldForceOverview({ depth: ALL_DEPTH, nodeCount: 12, renderLimited: true })).toBe(true)
  })
})
