import { describe, expect, it } from 'vitest'
import {
  ALL_TYPES,
  VIEW_MODES,
  adjacentMode,
  cycleNotice,
  isAllTypes,
  normalizeTypes,
  parseViewState,
  pathStatusMessage,
  serializeViewState,
  treeUnavailable,
  viewStateFromApp
} from './viewState.js'

describe('viewState URL encode/decode', function () {
  it('round-trips a full workspace state without queryId', function () {
    var state = {
      mode: 'path',
      seedId: 'root',
      snapshotId: 'snap-1',
      selectedId: 'view-a',
      targetId: 'java-j',
      types: ['table', 'java']
    }
    var qs = serializeViewState(state)
    expect(qs).not.toMatch(/queryId/)
    expect(qs).not.toMatch(/qid=/)
    var back = parseViewState('?' + qs)
    expect(back).toEqual({
      mode: 'path',
      seedId: 'root',
      snapshotId: 'snap-1',
      selectedId: 'view-a',
      targetId: 'java-j',
      types: ['table', 'java']
    })
  })

  it('omits default tree mode and all-types filter', function () {
    var qs = serializeViewState({
      mode: 'tree',
      seedId: 'root',
      types: ALL_TYPES.slice()
    })
    expect(qs).toBe('seedId=root')
    expect(parseViewState(qs)).toEqual({
      mode: 'tree',
      seedId: 'root',
      snapshotId: null,
      selectedId: null,
      targetId: null,
      types: ALL_TYPES.slice()
    })
  })

  it('treats missing search as the default tree workspace', function () {
    expect(parseViewState('')).toEqual({
      mode: 'tree',
      seedId: null,
      snapshotId: null,
      selectedId: null,
      targetId: null,
      types: ALL_TYPES.slice()
    })
    expect(parseViewState(null).mode).toBe('tree')
  })

  it('falls back to tree for unknown mode and drops unknown types', function () {
    var parsed = parseViewState('mode=graph&types=table,nope,view&seedId=')
    expect(parsed.mode).toBe('tree')
    expect(parsed.types).toEqual(['table', 'view'])
    expect(parsed.seedId).toBe(null)
  })

  it('normalizes type order to the API object-type order', function () {
    expect(normalizeTypes(['java', 'table', 'java', 'bogus'])).toEqual(['table', 'java'])
    expect(normalizeTypes('')).toEqual(ALL_TYPES.slice())
    expect(isAllTypes(ALL_TYPES)).toBe(true)
    expect(isAllTypes(['table'])).toBe(false)
  })

  it('builds state from the app snapshot without leaking queryId', function () {
    var encoded = serializeViewState(
      viewStateFromApp({
        mode: 'impact',
        seedId: 'root',
        selectedId: 'proc-b',
        targetId: '',
        filterTypes: ['procedure'],
        meta: { queryId: 'secret-qid', snapshotId: 'snap-9' }
      })
    )
    expect(encoded).toContain('mode=impact')
    expect(encoded).toContain('seedId=root')
    expect(encoded).toContain('snapshotId=snap-9')
    expect(encoded).toContain('selectedId=proc-b')
    expect(encoded).toContain('types=procedure')
    expect(encoded).not.toMatch(/secret-qid/)
    expect(encoded).not.toMatch(/queryId/)
  })
})

describe('view-state helpers', function () {
  it('cycles view modes for keyboard tab switching', function () {
    expect(VIEW_MODES).toEqual(['tree', 'overview', 'impact', 'path'])
    expect(adjacentMode('tree', 1)).toBe('overview')
    expect(adjacentMode('path', 1)).toBe('tree')
    expect(adjacentMode('tree', -1)).toBe('path')
    expect(adjacentMode('nope', 1)).toBe('overview')
  })

  it('returns HANDOFF cycle / incomplete tree notices', function () {
    expect(cycleNotice('unavailable_cycle')).toBe('检测到循环依赖，已切换关系清单')
    expect(cycleNotice('unavailable_incomplete')).toMatch(/主树分类不可用/)
    expect(cycleNotice('available')).toBe('')
    expect(treeUnavailable('unavailable_cycle')).toBe(true)
    expect(treeUnavailable('available')).toBe(false)
  })

  it('does not treat unknown path as no impact', function () {
    expect(pathStatusMessage({ status: 'found' })).toBe('')
    expect(pathStatusMessage({ status: 'not_found' })).toBe('无可达路径。')
    expect(pathStatusMessage({ status: 'unknown', reason: 'QUERY_INCOMPLETE' })).toMatch(
      /未查全不等于无影响/
    )
    expect(pathStatusMessage({ status: 'unknown', reason: 'PATH_LENGTH_LIMIT' })).toMatch(
      /长度上限/
    )
  })
})
