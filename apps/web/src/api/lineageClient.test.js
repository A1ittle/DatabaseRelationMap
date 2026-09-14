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
})
