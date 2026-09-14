import { describe, expect, it } from 'vitest'
import { createRevisionGuard } from './revision.js'
import {
  BUDGET_KEEP_OLD,
  emptyCanvas,
  isCompleteProjection,
  isProjectionLimitError,
  resolveProjectionOutcome
} from './projectionSession.js'

function canvasFrom(projection) {
  return resolveProjectionOutcome({
    canvas: emptyCanvas(),
    guard: createRevisionGuard(),
    sentRevision: projection.clientRevision,
    sentEpoch: 0,
    projection: projection,
    previousCandidates: ['root'],
    attemptedCandidates: ['root'],
    seedId: 'root'
  }).canvas
}

describe('projectionSession', function () {
  it('replaces nodes and edges together and rejects a partial graph', function () {
    var guard = createRevisionGuard()
    var first = {
      clientRevision: 0,
      nodes: [
        { object: { id: 'root' }, parentEdgeId: null, layoutRank: 0 },
        { object: { id: 'view-a' }, parentEdgeId: 'e02', layoutRank: 1 }
      ],
      edges: [
        { kind: 'tree', relation: { id: 'e02', source: 'root', target: 'view-a' } },
        { kind: 'cross', relation: { id: 'e01', source: 'root', target: 'view-a' } }
      ]
    }
    var applied = resolveProjectionOutcome({
      canvas: emptyCanvas(),
      guard: guard,
      sentRevision: 0,
      sentEpoch: 0,
      projection: first,
      previousCandidates: ['root'],
      attemptedCandidates: ['root', 'view-a'],
      seedId: 'root'
    })
    expect(applied.applied).toBe(true)
    expect(applied.canvas.projection.nodes.length).toBe(2)
    expect(applied.canvas.projection.edges.length).toBe(2)
    expect(applied.canvas.index.treeChildren.root).toEqual(['view-a'])

    var partial = resolveProjectionOutcome({
      canvas: applied.canvas,
      guard: guard,
      sentRevision: guard.next(),
      sentEpoch: guard.epoch(),
      projection: { clientRevision: 1, nodes: [{ object: { id: 'only-nodes' } }] },
      previousCandidates: applied.candidateIds,
      attemptedCandidates: ['root', 'too-many'],
      seedId: 'root'
    })
    expect(isCompleteProjection({ nodes: [], edges: [] })).toBe(true)
    expect(isCompleteProjection({ nodes: [] })).toBe(false)
    expect(partial.applied).toBe(false)
    expect(partial.keepOld).toBe(true)
    expect(partial.canvas).toBe(applied.canvas)
    expect(partial.canvas.projection.edges.length).toBe(2)
    expect(partial.candidateIds).toEqual(applied.candidateIds)
  })

  it('keeps the previous canvas on PROJECTION_LIMIT (超预算，已保留原图)', function () {
    var guard = createRevisionGuard()
    var live = {
      clientRevision: 0,
      nodes: [{ object: { id: 'root' }, parentEdgeId: null }],
      edges: [{ kind: 'tree', relation: { id: 'e10', source: 'root', target: 'proc-s' } }]
    }
    var start = resolveProjectionOutcome({
      canvas: emptyCanvas(),
      guard: guard,
      sentRevision: 0,
      sentEpoch: 0,
      projection: live,
      previousCandidates: ['root'],
      attemptedCandidates: ['root'],
      seedId: 'root'
    })
    var oversizeIds = ['root']
    var i
    for (i = 0; i < 201; i++) {
      oversizeIds.push('synth-' + i)
    }
    var sent = guard.next()
    var budgetErr = new Error('projection exceeds 200 objects')
    budgetErr.code = 'PROJECTION_LIMIT'
    budgetErr.status = 400
    budgetErr.body = { code: 'PROJECTION_LIMIT', message: 'projection exceeds 200 objects' }

    expect(isProjectionLimitError(budgetErr)).toBe(true)

    var kept = resolveProjectionOutcome({
      canvas: start.canvas,
      guard: guard,
      sentRevision: sent,
      sentEpoch: guard.epoch(),
      error: budgetErr,
      previousCandidates: start.candidateIds,
      attemptedCandidates: oversizeIds,
      seedId: 'root'
    })
    expect(kept.applied).toBe(false)
    expect(kept.keepOld).toBe(true)
    expect(kept.error).toBe(BUDGET_KEEP_OLD)
    expect(kept.canvas).toBe(start.canvas)
    expect(kept.canvas.projection.nodes).toEqual(live.nodes)
    expect(kept.canvas.projection.edges).toEqual(live.edges)
    expect(kept.candidateIds).toEqual(start.candidateIds)
    expect(kept.candidateIds.indexOf('synth-0')).toBe(-1)
  })

  it('does not apply a stale in-flight success over a newer canvas', function () {
    var guard = createRevisionGuard()
    var first = guard.next()
    var second = guard.next()
    var newer = {
      clientRevision: second,
      nodes: [{ object: { id: 'root' } }, { object: { id: 'view-a' } }],
      edges: [{ kind: 'tree', relation: { id: 'e02', source: 'root', target: 'view-a' } }]
    }
    var afterSecond = resolveProjectionOutcome({
      canvas: canvasFrom({
        clientRevision: 0,
        nodes: [{ object: { id: 'root' } }],
        edges: []
      }),
      guard: guard,
      sentRevision: second,
      sentEpoch: guard.epoch(),
      projection: newer,
      previousCandidates: ['root'],
      attemptedCandidates: ['root', 'view-a'],
      seedId: 'root'
    })
    expect(afterSecond.applied).toBe(true)
    var late = resolveProjectionOutcome({
      canvas: afterSecond.canvas,
      guard: guard,
      sentRevision: first,
      sentEpoch: guard.epoch(),
      projection: {
        clientRevision: first,
        nodes: [{ object: { id: 'root' } }],
        edges: []
      },
      previousCandidates: afterSecond.candidateIds,
      attemptedCandidates: ['root'],
      seedId: 'root'
    })
    expect(late.applied).toBe(false)
    expect(late.stale).toBe(true)
    expect(late.canvas.projection.nodes.length).toBe(2)
  })
})
