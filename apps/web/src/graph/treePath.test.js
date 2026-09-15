import { describe, expect, it } from 'vitest'
import { edgeOnPath, highlightIdSet, treePathIds } from './treePath.js'

describe('treePathIds', function () {
  it('walks parentOf up to the seed without looping', function () {
    var parentOf = { 'view-a': 'root', 'table-c': 'view-a', 'java-j': 'table-c' }
    expect(treePathIds(parentOf, 'java-j', 'root')).toEqual(['root', 'view-a', 'table-c', 'java-j'])
    expect(treePathIds({ a: 'a' }, 'a', 'seed')).toEqual(['a'])
  })
})

describe('highlightIdSet', function () {
  it('is null for seed selection and includes incident cross endpoints otherwise', function () {
    var parentOf = { 'view-a': 'root' }
    expect(highlightIdSet(parentOf, 'root', 'root', [])).toBe(null)
    expect(highlightIdSet(parentOf, 'view-a', 'root', [
      { relation: { source: 'view-a', target: 'proc-s' } }
    ])).toEqual({ root: true, 'view-a': true, 'proc-s': true })
  })
})

describe('edgeOnPath', function () {
  it('highlights tree edges on the selected path and incident cross edges', function () {
    var pathSet = { root: true, 'view-a': true }
    expect(edgeOnPath('tree', 'root', 'view-a', 'view-a', pathSet)).toBe(true)
    expect(edgeOnPath('tree', 'root', 'proc-s', 'view-a', pathSet)).toBe(false)
    expect(edgeOnPath('cross', 'view-a', 'proc-s', 'view-a', pathSet)).toBe(true)
    expect(edgeOnPath('cross', 'root', 'proc-s', 'view-a', pathSet)).toBe(false)
  })
})
