import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { COL_GAP } from './layout.js'
import { columnLabel, groupColumns, isLeafColumn, sliceColumn } from './columns.js'

var here = dirname(fileURLToPath(import.meta.url))
var fixture = JSON.parse(
  readFileSync(resolve(here, '../../../../fixtures/v1/query-response.json'), 'utf8')
)

describe('groupColumns', function () {
  it('buckets fixture nodes by layoutRank with seed in column 0', function () {
    var cols = groupColumns(fixture.projection)
    expect(cols.map(function (c) { return c.rank })).toEqual([0, 1])
    expect(cols[0].label).toBe('当前表')
    expect(cols[0].leaf).toBe(false)
    expect(cols[0].nodes.map(function (n) { return n.object.id })).toEqual(['root'])
    expect(cols[1].label).toBe('第 1 层')
    expect(cols[1].nodes.map(function (n) { return n.object.id })).toEqual(['proc-s', 'view-a'])
  })

  it('marks an all-Java column as a terminal leaf column', function () {
    var javaNode = {
      object: { id: 'java-j', type: 'java', technicalName: 'java-j' },
      layoutRank: 2
    }
    var cols = groupColumns({
      nodes: [
        { object: { id: 'root', type: 'table', technicalName: 'root' }, layoutRank: 0 },
        javaNode
      ],
      edges: []
    })
    expect(cols[1].leaf).toBe(true)
    expect(cols[1].label).toBe('第 2 层 · 终端')
    expect(isLeafColumn(cols[1].nodes)).toBe(true)
    expect(columnLabel(0, cols[0].nodes)).toBe('当前表')
  })
})

describe('sliceColumn', function () {
  it('caps a column until it is opened', function () {
    var nodes = [1, 2, 3, 4, 5, 6]
    expect(sliceColumn(nodes, false, 5)).toEqual({ shown: [1, 2, 3, 4, 5], rest: 1, open: false })
    expect(sliceColumn(nodes, true, 5).rest).toBe(0)
    expect(sliceColumn(nodes, true, 5).shown).toEqual(nodes)
  })
})

describe('layout gap still applies per rank', function () {
  it('keeps COL_GAP exported for canvas width math', function () {
    expect(COL_GAP).toBe(352)
  })
})
