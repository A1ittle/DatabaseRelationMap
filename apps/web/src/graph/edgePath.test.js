import { describe, expect, it } from 'vitest'
import { edgeClassNames, edgePath } from './edgePath.js'

describe('edgePath', function () {
  it('draws a forward cubic when the target is to the right', function () {
    expect(edgePath(0, 10, 100, 50, false)).toBe('M 0 10 C 50 10, 50 50, 100 50')
    expect(edgePath(10, 0, 40, 0, false)).toBe('M 10 0 C 46 0, 4 0, 40 0')
  })

  it('loops to the right when the target is not further right', function () {
    expect(edgePath(80, 10, 80, 40, false)).toBe('M 80 10 C 100 10, 100 25, 80 40')
    expect(edgePath(80, 10, 80, 40, true)).toBe('M 80 10 C 108 10, 108 25, 80 40')
    expect(edgePath(90, 0, 10, 20, true)).toBe('M 90 0 C 118 0, 118 10, 10 20')
  })
})

describe('edgeClassNames', function () {
  it('marks tree vs cross and on/dim', function () {
    expect(edgeClassNames('tree', 'reads', false, false)).toBe('edge reads tree')
    expect(edgeClassNames('tree', 'derives', true, true)).toBe('edge derives tree on')
    expect(edgeClassNames('cross', 'reads', false, true)).toBe('edge reads cross dim')
    expect(edgeClassNames('unclassified', 'calls', false, false)).toBe('edge calls cross')
  })
})
