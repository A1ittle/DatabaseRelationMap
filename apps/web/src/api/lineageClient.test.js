import { describe, expect, it } from 'vitest'
import { createLineageClient } from './lineageClient.js'

function mockFetch(handler) {
  return function (url, init) {
    return Promise.resolve(handler(url, init))
  }
}

function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status: status,
    statusText: 'X',
    text: function () {
      return Promise.resolve(JSON.stringify(body))
    }
  }
}

describe('lineageClient', function () {
  it('sends CSRF on POST and optional embed groups', async function () {
    var seen = []
    var client = createLineageClient({
      getConfig: function () {
        return {
          apiBase: 'http://127.0.0.1:8080',
          csrfToken: 'dev',
          embedGroups: 'g-view'
        }
      },
      fetchImpl: mockFetch(function (url, init) {
        seen.push({ url: String(url), init: init })
        return jsonResponse(201, { meta: { queryId: 'q1' }, seed: { id: 'root' }, projection: { clientRevision: 0, nodes: [], edges: [] } })
      })
    })
    await client.createQuery('root')
    expect(seen[0].url).toBe('http://127.0.0.1:8080/api/lineage/queries')
    expect(seen[0].init.method).toBe('POST')
    expect(seen[0].init.headers['X-CSRF-Token']).toBe('dev')
    expect(seen[0].init.headers['X-Embed-Groups']).toBe('g-view')
    expect(JSON.parse(seen[0].init.body)).toEqual({ seedId: 'root' })
  })

  it('does not send CSRF on GET search and uses default API base', async function () {
    var seen = []
    var client = createLineageClient({
      getConfig: function () {
        return { apiBase: 'http://127.0.0.1:8080', csrfToken: 'dev', embedGroups: '' }
      },
      fetchImpl: mockFetch(function (url, init) {
        seen.push({ url: String(url), init: init })
        return jsonResponse(200, { requestId: 'r', items: [], page: { nextCursor: null, hasMore: false, total: 0 } })
      })
    })
    await client.search('root', { queryId: 'q1' })
    expect(seen[0].url).toBe('http://127.0.0.1:8080/api/lineage/search?q=root&queryId=q1')
    expect(seen[0].init.headers['X-CSRF-Token']).toBeUndefined()
  })

  it('posts a full-replace projection body including clientRevision', async function () {
    var seen = []
    var client = createLineageClient({
      getConfig: function () {
        return { apiBase: 'http://127.0.0.1:8080', csrfToken: 'dev', embedGroups: '' }
      },
      fetchImpl: mockFetch(function (url, init) {
        seen.push({ url: String(url), body: JSON.parse(init.body) })
        return jsonResponse(200, { clientRevision: 2, nodes: [], edges: [] })
      })
    })
    var body = {
      candidateIds: ['root', 'view-a'],
      selectedId: 'view-a',
      types: client.ALL_TYPES,
      revealSelectedPath: false,
      clientRevision: 2
    }
    await client.projection('qid-1', body)
    expect(seen[0].url).toBe('http://127.0.0.1:8080/api/lineage/queries/qid-1/projection')
    expect(seen[0].body.clientRevision).toBe(2)
    expect(seen[0].body.candidateIds).toEqual(['root', 'view-a'])
  })

  it('surfaces PROJECTION_LIMIT without returning a graph body', async function () {
    var client = createLineageClient({
      getConfig: function () {
        return { apiBase: 'http://127.0.0.1:8080', csrfToken: 'dev', embedGroups: '' }
      },
      fetchImpl: mockFetch(function () {
        return jsonResponse(400, {
          code: 'PROJECTION_LIMIT',
          message: 'projection exceeds 200 objects',
          retryable: false
        })
      })
    })
    var oversize = ['root']
    var i
    for (i = 0; i < 201; i++) {
      oversize.push('synth-' + i)
    }
    var caught = null
    try {
      await client.projection('qid-1', {
        candidateIds: oversize,
        selectedId: 'root',
        types: client.ALL_TYPES,
        revealSelectedPath: false,
        clientRevision: 3
      })
    } catch (err) {
      caught = err
    }
    expect(caught).toBeTruthy()
    expect(caught.code).toBe('PROJECTION_LIMIT')
    expect(caught.status).toBe(400)
  })

  it('calls overview, members, impact, path, evidence as GET without CSRF', async function () {
    var seen = []
    var client = createLineageClient({
      getConfig: function () {
        return { apiBase: 'http://127.0.0.1:8080', csrfToken: 'dev', embedGroups: '' }
      },
      fetchImpl: mockFetch(function (url, init) {
        seen.push({ url: String(url), method: init.method, headers: init.headers })
        return jsonResponse(200, { items: [], page: { nextCursor: null, hasMore: false, total: 0 } })
      })
    })
    await client.overview('qid-1', { types: ['table', 'java'], limit: 50 })
    await client.clusterMembers('qid-1', 'c-0-table', { cursor: 'n1' })
    await client.impact('qid-1', { types: ['view'], system: 'demo' })
    await client.path('qid-1', 'java-j')
    await client.evidence('qid-1', 'e07')
    expect(seen[0].url).toBe(
      'http://127.0.0.1:8080/api/lineage/queries/qid-1/overview?limit=50&types=table&types=java'
    )
    expect(seen[1].url).toBe(
      'http://127.0.0.1:8080/api/lineage/queries/qid-1/clusters/c-0-table/members?cursor=n1'
    )
    expect(seen[2].url).toBe(
      'http://127.0.0.1:8080/api/lineage/queries/qid-1/impact?types=view&system=demo'
    )
    expect(seen[3].url).toBe(
      'http://127.0.0.1:8080/api/lineage/queries/qid-1/path?targetId=java-j'
    )
    expect(seen[4].url).toBe(
      'http://127.0.0.1:8080/api/lineage/queries/qid-1/relations/e07/evidence'
    )
    seen.forEach(function (row) {
      expect(row.method).toBe('GET')
      expect(row.headers['X-CSRF-Token']).toBeUndefined()
    })
  })
})
