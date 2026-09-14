import { describe, expect, it } from 'vitest'
import { drawnStats } from './drawnStats.js'

describe('drawnStats', function () {
  it('counts rendered nodes and tree vs cross vs unclassified edges', function () {
    var stats = drawnStats({
      nodes: [{ object: { id: 'a' } }, { object: { id: 'b' } }, { object: { id: 'c' } }],
      edges: [
        { kind: 'tree', relation: { id: 't1' } },
        { kind: 'tree', relation: { id: 't2' } },
        { kind: 'cross', relation: { id: 'c1' } },
        { kind: 'unclassified', relation: { id: 'u1' } }
      ]
    })
    expect(stats).toEqual({
      nodes: 3,
      edges: 4,
      tree: 2,
      cross: 1,
      unclassified: 1,
      other: 0
    })
  })

  it('is empty for a missing projection', function () {
    expect(drawnStats(null)).toEqual({
      nodes: 0,
      edges: 0,
      tree: 0,
      cross: 0,
      unclassified: 0,
      other: 0
    })
  })
})
