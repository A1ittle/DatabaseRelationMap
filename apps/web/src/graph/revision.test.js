import { describe, expect, it } from 'vitest'
import { createRevisionGuard } from './revision.js'

describe('createRevisionGuard', function () {
  it('applies the matching in-flight revision and drops older responses', function () {
    var guard = createRevisionGuard()
    expect(guard.current()).toBe(0)
    expect(guard.shouldApply(0, 0)).toBe(true)
    guard.adopt(0)

    var first = guard.next()
    var second = guard.next()
    expect(first).toBe(1)
    expect(second).toBe(2)
    expect(guard.isStale(first)).toBe(true)
    expect(guard.shouldApply(1, first)).toBe(false)
    expect(guard.shouldApply(2, second)).toBe(true)
    guard.adopt(2)
    expect(guard.shouldApply(1, first)).toBe(false)
  })

  it('rejects missing or negative revisions', function () {
    var guard = createRevisionGuard()
    expect(guard.shouldApply(null, 0)).toBe(false)
    expect(guard.shouldApply(-1, -1)).toBe(false)
    expect(guard.isStale(null)).toBe(true)
  })

  it('reset allows a new query to start at revision 0', function () {
    var guard = createRevisionGuard()
    guard.next()
    guard.reset()
    expect(guard.current()).toBe(0)
    expect(guard.shouldApply(0, 0)).toBe(true)
  })
})
