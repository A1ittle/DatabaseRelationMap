import { describe, expect, it } from 'vitest'
import { createRevisionGuard } from './revision.js'
import { emptyCanvas, resolveProjectionOutcome } from './projectionSession.js'
import {
  candidateIdsAfterCollapse,
  candidateSetsEqual,
  normalizeSnapshotId,
  shouldApplyQueryFailure,
  shouldRecreateQuery,
  shouldRollbackCandidates
} from './urlSnapshotRace.js'

describe('shouldRecreateQuery (URL snapshot restore)', function () {
  it('recreates when the seed is unchanged but URL snapshotId changes', function () {
    expect(shouldRecreateQuery('root', 'snap-b', 'root', 'snap-a')).toBe(true)
  })

  it('recreates when URL snapshot is cleared so the query follows active', function () {
    expect(shouldRecreateQuery('root', null, 'root', 'snap-a')).toBe(true)
    expect(shouldRecreateQuery('root', '', 'root', 'snap-a')).toBe(true)
    expect(shouldRecreateQuery('root', '  ', 'root', 'snap-a')).toBe(true)
  })

  it('does not recreate when seed and normalized snapshot match', function () {
    expect(shouldRecreateQuery('root', 'snap-a', 'root', 'snap-a')).toBe(false)
    expect(shouldRecreateQuery('root', '', 'root', null)).toBe(false)
    expect(shouldRecreateQuery('root', null, 'root', '')).toBe(false)
  })

  it('recreates when the seed changes and never without a seed', function () {
    expect(shouldRecreateQuery('other', 'snap-a', 'root', 'snap-a')).toBe(true)
    expect(shouldRecreateQuery(null, 'snap-a', 'root', 'snap-a')).toBe(false)
    expect(shouldRecreateQuery('', 'snap-a', 'root', 'snap-a')).toBe(false)
  })

  it('normalizes empty/null snapshot ids the same way', function () {
    expect(normalizeSnapshotId(undefined)).toBe(null)
    expect(normalizeSnapshotId(null)).toBe(null)
    expect(normalizeSnapshotId('')).toBe(null)
    expect(normalizeSnapshotId(' snap-1 ')).toBe('snap-1')
  })
})

describe('stale epoch failure is ignored', function () {
  it('does not apply createQuery failure after a newer epoch', function () {
    expect(shouldApplyQueryFailure(0, 0)).toBe(true)
    expect(shouldApplyQueryFailure(0, 1)).toBe(false)
    expect(shouldApplyQueryFailure(2, 2)).toBe(true)
  })

  it('does not roll back candidates or keepOld-apply a stale-epoch fail', function () {
    var guard = createRevisionGuard()
    var first = guard.next()
    var firstEpoch = guard.epoch()
    var live = {
      clientRevision: first,
      nodes: [{ object: { id: 'root' } }, { object: { id: 'view-a' } }],
      edges: [{ kind: 'tree', relation: { id: 'e02', source: 'root', target: 'view-a' } }]
    }
    var applied = resolveProjectionOutcome({
      canvas: emptyCanvas(),
      guard: guard,
      sentRevision: first,
      sentEpoch: firstEpoch,
      projection: live,
      previousCandidates: ['root'],
      attemptedCandidates: ['root', 'view-a'],
      seedId: 'root'
    })
    expect(applied.applied).toBe(true)

    guard.reset()
    var newer = guard.next()
    var newerCanvas = resolveProjectionOutcome({
      canvas: applied.canvas,
      guard: guard,
      sentRevision: newer,
      sentEpoch: guard.epoch(),
      projection: {
        clientRevision: newer,
        nodes: [{ object: { id: 'root' } }, { object: { id: 'proc-b' } }],
        edges: [{ kind: 'tree', relation: { id: 'e08', source: 'root', target: 'proc-b' } }]
      },
      previousCandidates: ['root'],
      attemptedCandidates: ['root', 'proc-b'],
      seedId: 'root'
    })
    expect(newerCanvas.applied).toBe(true)
    expect(newerCanvas.candidateIds).toEqual(['root', 'proc-b'])

    var lateErr = new Error('QUERY_EXPIRED')
    lateErr.code = 'QUERY_EXPIRED'
    lateErr.status = 410
    var late = resolveProjectionOutcome({
      canvas: newerCanvas.canvas,
      guard: guard,
      sentRevision: first,
      sentEpoch: firstEpoch,
      error: lateErr,
      previousCandidates: ['root'],
      attemptedCandidates: ['root', 'view-a'],
      seedId: 'root'
    })
    expect(late.applied).toBe(false)
    expect(late.stale).toBe(true)
    expect(late.keepOld).toBeFalsy()
    expect(late.error).toBe('')
    expect(late.canvas).toBe(newerCanvas.canvas)
    expect(shouldRollbackCandidates(late)).toBe(false)
    expect(shouldRollbackCandidates({ applied: false, keepOld: true })).toBe(true)
    expect(shouldRollbackCandidates({ applied: false, aborted: true })).toBe(false)
  })
})

describe('candidateIdsAfterCollapse', function () {
  it('shrinks candidates to seed + still-expanded children + pins', function () {
    var expanded = { root: true, 'view-a': true, 'proc-b': true }
    var childPages = {
      root: { ids: ['view-a', 'proc-s'] },
      'view-a': { ids: ['proc-b'] },
      'proc-b': { ids: ['table-c'] }
    }
    var before = ['root', 'view-a', 'proc-s', 'proc-b', 'table-c', 'java-j']
    delete expanded['view-a']
    var after = candidateIdsAfterCollapse({
      seedId: 'root',
      expanded: expanded,
      childPages: childPages,
      pins: ['java-j']
    })
    expect(after).toEqual(['root', 'view-a', 'proc-s', 'java-j'])
    expect(after.indexOf('proc-b')).toBe(-1)
    expect(after.indexOf('table-c')).toBe(-1)
    expect(after.length).toBeLessThan(before.length)
    expect(candidateSetsEqual(after, before)).toBe(false)
  })

  it('drops descendants of a collapsed node even if they stay marked expanded', function () {
    var after = candidateIdsAfterCollapse({
      seedId: 'root',
      expanded: { 'view-a': true, 'proc-b': true },
      childPages: {
        root: { ids: ['view-a'] },
        'view-a': { ids: ['proc-b'] },
        'proc-b': { ids: ['table-c'] }
      },
      pins: []
    })
    expect(after).toEqual(['root'])
  })
})
