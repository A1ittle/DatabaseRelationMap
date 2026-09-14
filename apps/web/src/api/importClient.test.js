import { describe, expect, it } from 'vitest'
import { createImportClient } from './importClient.js'

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

describe('importClient', function () {
  it('POSTs ImportBatch with CSRF and optional embed groups, accepts 202', async function () {
    var seen = []
    var client = createImportClient({
      getConfig: function () {
        return { apiBase: 'http://127.0.0.1:8080', csrfToken: 'dev', embedGroups: 'g-view' }
      },
      fetchImpl: mockFetch(function (url, init) {
        seen.push({ url: String(url), init: init })
        return jsonResponse(202, {
          runId: 'run-1',
          status: 'ready',
          snapshotId: 'snap-1',
          errorCount: 0,
          warningCount: 1
        })
      })
    })
    var res = await client.createImport({ batchKey: 'b1', scopeId: 's1' })
    expect(seen[0].url).toBe('http://127.0.0.1:8080/api/imports')
    expect(seen[0].init.method).toBe('POST')
    expect(seen[0].init.headers['X-CSRF-Token']).toBe('dev')
    expect(seen[0].init.headers['X-Embed-Groups']).toBe('g-view')
    expect(JSON.parse(seen[0].init.body)).toEqual({ batchKey: 'b1', scopeId: 's1' })
    expect(res.runId).toBe('run-1')
    expect(res.status).toBe('ready')
  })

  it('GETs import detail with cursor pagination and no CSRF', async function () {
    var seen = []
    var client = createImportClient({
      getConfig: function () {
        return { apiBase: 'http://127.0.0.1:8080', csrfToken: 'dev', embedGroups: '' }
      },
      fetchImpl: mockFetch(function (url, init) {
        seen.push({ url: String(url), init: init })
        return jsonResponse(200, {
          run: { runId: 'run-1', status: 'failed', snapshotId: null, errorCount: 1, warningCount: 0 },
          issues: [{ code: 'DUP_ID', path: '/objects/0/id', message: 'dup', severity: 'error' }],
          page: { nextCursor: '9', hasMore: true, total: 2 }
        })
      })
    })
    var res = await client.getImport('run-1', { cursor: '3', limit: 50 })
    expect(seen[0].url).toBe('http://127.0.0.1:8080/api/imports/run-1?cursor=3&limit=50')
    expect(seen[0].init.headers['X-CSRF-Token']).toBeUndefined()
    expect(res.issues[0].code).toBe('DUP_ID')
    expect(res.page.hasMore).toBe(true)
  })

  it('publishes with expectedActiveSnapshotId null by default', async function () {
    var seen = []
    var client = createImportClient({
      getConfig: function () {
        return { apiBase: 'http://127.0.0.1:8080', csrfToken: 'dev', embedGroups: '' }
      },
      fetchImpl: mockFetch(function (url, init) {
        seen.push({ url: String(url), body: JSON.parse(init.body), headers: init.headers })
        return jsonResponse(200, { snapshotId: 'snap-1', publishedAt: '2026-09-14T00:00:00Z' })
      })
    })
    var res = await client.publish('run-1', '')
    expect(seen[0].url).toBe('http://127.0.0.1:8080/api/imports/run-1/publish')
    expect(seen[0].headers['X-CSRF-Token']).toBe('dev')
    expect(seen[0].body).toEqual({ expectedActiveSnapshotId: null })
    expect(res.snapshotId).toBe('snap-1')
  })

  it('surfaces PAYLOAD_TOO_LARGE 413', async function () {
    var client = createImportClient({
      getConfig: function () {
        return { apiBase: 'http://127.0.0.1:8080', csrfToken: 'dev', embedGroups: '' }
      },
      fetchImpl: mockFetch(function () {
        return jsonResponse(413, {
          code: 'PAYLOAD_TOO_LARGE',
          message: 'request body exceeds 50 MiB',
          retryable: false
        })
      })
    })
    var caught = null
    try {
      await client.createImport({ batchKey: 'x', scopeId: 'y' })
    } catch (err) {
      caught = err
    }
    expect(caught).toBeTruthy()
    expect(caught.code).toBe('PAYLOAD_TOO_LARGE')
    expect(caught.status).toBe(413)
  })

  it('surfaces IMPORT_INVALID and PUBLISH_CONFLICT', async function () {
    var n = 0
    var client = createImportClient({
      getConfig: function () {
        return { apiBase: 'http://127.0.0.1:8080', csrfToken: 'dev', embedGroups: '' }
      },
      fetchImpl: mockFetch(function (url) {
        n += 1
        if (String(url).indexOf('/publish') !== -1) {
          return jsonResponse(409, {
            code: 'PUBLISH_CONFLICT',
            message: 'active snapshot does not match expectedActiveSnapshotId',
            retryable: false
          })
        }
        return jsonResponse(400, {
          code: 'IMPORT_INVALID',
          message: 'ImportBatch must be a JSON object',
          retryable: false
        })
      })
    })
    var invalid = null
    try {
      await client.createImport('not-an-object')
    } catch (err) {
      invalid = err
    }
    expect(invalid.code).toBe('IMPORT_INVALID')
    var conflict = null
    try {
      await client.publish('run-1', null)
    } catch (err) {
      conflict = err
    }
    expect(conflict.code).toBe('PUBLISH_CONFLICT')
    expect(conflict.status).toBe(409)
    expect(n).toBe(2)
  })
})
