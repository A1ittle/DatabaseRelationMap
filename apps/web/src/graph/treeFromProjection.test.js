import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import {
  candidateIdsFromProjection,
  crossEdgesFor,
  findRoots,
  indexProjection,
  kindClass,
  mergeCandidateIds
} from './treeFromProjection.js'

var here = dirname(fileURLToPath(import.meta.url))
var fixture = JSON.parse(
  readFileSync(resolve(here, '../../../../fixtures/v1/query-response.json'), 'utf8')
)

describe('treeFromProjection', function () {
  it('builds tree children from parentEdgeId and keeps cross edges distinct', function () {
    var index = indexProjection(fixture.projection)
    expect(findRoots(index, 'root')).toEqual(['root'])
    expect(index.treeChildren.root).toEqual(['view-a', 'proc-s'])
    expect(index.parentOf['view-a']).toBe('root')
    expect(index.parentOf['proc-s']).toBe('root')
    var cross = crossEdgesFor(index, 'root')
    expect(cross.map(function (e) { return e.relation.id })).toEqual(['e01'])
    expect(kindClass(cross[0].kind)).toBe('kind-cross')
    expect(kindClass('tree')).toBe('kind-tree')
  })

  it('merges candidate ids with the seed first and no duplicates', function () {
    expect(candidateIdsFromProjection(fixture.projection)).toEqual(['root', 'view-a', 'proc-s'])
    expect(mergeCandidateIds(['root', 'view-a'], ['view-a', 'proc-b'], 'root')).toEqual([
      'root',
      'view-a',
      'proc-b'
    ])
  })
})
