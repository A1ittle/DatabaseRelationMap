import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import {
  edgeIncidentTo,
  incidentNonTreeEdges,
  listNonTreeEdges,
  otherEndpoint
} from './crossList.js'

var here = dirname(fileURLToPath(import.meta.url))
var fixture = JSON.parse(
  readFileSync(resolve(here, '../../../../fixtures/v1/query-response.json'), 'utf8')
)

function shuffleCopy(list) {
  var copy = list.slice()
  copy.reverse()
  copy.push(copy.shift())
  return copy
}

describe('crossList', function () {
  it('lists cross and unclassified from projection kinds, independent of order', function () {
    var projection = fixture.projection
    var ids = listNonTreeEdges(projection).map(function (e) {
      return e.relation.id
    })
    expect(ids).toEqual(['e01'])

    var shuffled = {
      nodes: shuffleCopy(projection.nodes),
      edges: shuffleCopy(projection.edges)
    }
    expect(
      listNonTreeEdges(shuffled).map(function (e) {
        return e.relation.id
      })
    ).toEqual(ids)

    var withUnclassified = {
      nodes: projection.nodes,
      edges: projection.edges.concat([
        {
          kind: 'unclassified',
          relation: { id: 'e99', source: 'proc-s', target: 'view-a', rel: 'unknown' }
        },
        {
          kind: 'tree',
          relation: { id: 'e02', source: 'root', target: 'view-a', rel: 'derives' }
        }
      ])
    }
    expect(
      listNonTreeEdges(withUnclassified).map(function (e) {
        return e.relation.id
      })
    ).toEqual(['e01', 'e99'])
  })

  it('resolves the other endpoint and selected-incident subset', function () {
    var edge = listNonTreeEdges(fixture.projection)[0]
    expect(otherEndpoint(edge, 'root')).toBe('view-a')
    expect(otherEndpoint(edge, 'view-a')).toBe('root')
    expect(edgeIncidentTo(edge, 'root')).toBe(true)
    expect(edgeIncidentTo(edge, 'proc-s')).toBe(false)
    expect(
      incidentNonTreeEdges(fixture.projection, 'root').map(function (e) {
        return e.relation.id
      })
    ).toEqual(['e01'])
    expect(incidentNonTreeEdges(fixture.projection, 'proc-s')).toEqual([])
  })
})
