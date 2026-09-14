import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { COL_GAP, layoutPositions } from './layout.js'

var here = dirname(fileURLToPath(import.meta.url))
var fixture = JSON.parse(
  readFileSync(resolve(here, '../../../../fixtures/v1/query-response.json'), 'utf8')
)

describe('layoutPositions', function () {
  it('places nodes by layoutRank with a stable order inside a column', function () {
    var pos = layoutPositions(fixture.projection)
    expect(pos.root.x).toBe(0)
    expect(pos.root.layoutRank).toBe(0)
    expect(pos['view-a'].x).toBe(COL_GAP)
    expect(pos['proc-s'].x).toBe(COL_GAP)
    expect(pos['view-a'].y).not.toBe(pos['proc-s'].y)

    var again = layoutPositions({
      nodes: fixture.projection.nodes.slice().reverse(),
      edges: fixture.projection.edges
    })
    expect(again).toEqual(pos)
  })
})
