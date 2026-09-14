import { getConfig } from '../config.js'

var ALL_TYPES = ['table', 'view', 'procedure', 'java']

export function createLineageClient(options) {
  var opts = options || {}
  var fetchImpl = opts.fetchImpl || fetch
  var configFn = opts.getConfig || getConfig

  function cfg() {
    return configFn()
  }

  function buildUrl(path, query) {
    var base = cfg().apiBase
    var qs = ''
    if (query) {
      var parts = []
      Object.keys(query).forEach(function (key) {
        var value = query[key]
        if (value == null || value === '') {
          return
        }
        if (Array.isArray(value)) {
          value.forEach(function (item) {
            parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(item))
          })
        } else {
          parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(value))
        }
      })
      if (parts.length) {
        qs = '?' + parts.join('&')
      }
    }
    if (!base) {
      return path + qs
    }
    return base.replace(/\/$/, '') + path + qs
  }

  function headers(method) {
    var c = cfg()
    var h = { Accept: 'application/json' }
    if (method === 'POST') {
      h['Content-Type'] = 'application/json'
      h['X-CSRF-Token'] = c.csrfToken || 'dev'
    }
    if (c.embedGroups) {
      h['X-Embed-Groups'] = c.embedGroups
    }
    return h
  }

  function request(path, spec) {
    var method = (spec && spec.method) || 'GET'
    var url = buildUrl(path, spec && spec.query)
    var init = {
      method: method,
      headers: headers(method)
    }
    if (method === 'POST') {
      init.body = JSON.stringify((spec && spec.body) || {})
    }
    if (spec && spec.signal) {
      init.signal = spec.signal
    }
    return fetchImpl(url, init).then(function (res) {
      return res.text().then(function (text) {
        var json = null
        if (text) {
          try {
            json = JSON.parse(text)
          } catch (err) {
            json = null
          }
        }
        if (!res.ok) {
          var err = new Error((json && json.message) || res.statusText || 'request failed')
          err.code = (json && json.code) || 'HTTP_' + res.status
          err.status = res.status
          err.body = json
          err.retryable = !!(json && json.retryable)
          throw err
        }
        return json
      })
    })
  }

  return {
    ALL_TYPES: ALL_TYPES,
    buildUrl: buildUrl,
    search: function (q, extra) {
      var query = { q: q }
      if (extra) {
        if (extra.queryId) {
          query.queryId = extra.queryId
        }
        if (extra.cursor) {
          query.cursor = extra.cursor
        }
        if (extra.limit != null) {
          query.limit = extra.limit
        }
      }
      return request('/api/lineage/search', { query: query, signal: extra && extra.signal })
    },
    createQuery: function (seedId, snapshotId, extra) {
      var body = { seedId: seedId }
      if (snapshotId) {
        body.snapshotId = snapshotId
      }
      return request('/api/lineage/queries', {
        method: 'POST',
        body: body,
        signal: extra && extra.signal
      })
    },
    children: function (qid, parentId, extra) {
      var query = { parentId: parentId }
      if (extra) {
        if (extra.cursor) {
          query.cursor = extra.cursor
        }
        if (extra.limit != null) {
          query.limit = extra.limit
        }
        if (extra.types) {
          query.types = extra.types
        }
      }
      return request('/api/lineage/queries/' + encodeURIComponent(qid) + '/children', {
        query: query,
        signal: extra && extra.signal
      })
    },
    projection: function (qid, body, extra) {
      return request('/api/lineage/queries/' + encodeURIComponent(qid) + '/projection', {
        method: 'POST',
        body: body,
        signal: extra && extra.signal
      })
    },
    getNode: function (qid, id, extra) {
      return request(
        '/api/lineage/queries/' + encodeURIComponent(qid) + '/nodes/' + encodeURIComponent(id),
        { signal: extra && extra.signal }
      )
    },
    overview: function (qid, extra) {
      var query = {}
      if (extra) {
        if (extra.cursor) {
          query.cursor = extra.cursor
        }
        if (extra.limit != null) {
          query.limit = extra.limit
        }
        if (extra.types) {
          query.types = extra.types
        }
      }
      return request('/api/lineage/queries/' + encodeURIComponent(qid) + '/overview', {
        query: query,
        signal: extra && extra.signal
      })
    },
    clusterMembers: function (qid, cid, extra) {
      var query = {}
      if (extra) {
        if (extra.cursor) {
          query.cursor = extra.cursor
        }
        if (extra.limit != null) {
          query.limit = extra.limit
        }
      }
      return request(
        '/api/lineage/queries/' +
          encodeURIComponent(qid) +
          '/clusters/' +
          encodeURIComponent(cid) +
          '/members',
        { query: query, signal: extra && extra.signal }
      )
    },
    impact: function (qid, extra) {
      var query = {}
      if (extra) {
        if (extra.cursor) {
          query.cursor = extra.cursor
        }
        if (extra.limit != null) {
          query.limit = extra.limit
        }
        if (extra.types) {
          query.types = extra.types
        }
        if (extra.system) {
          query.system = extra.system
        }
      }
      return request('/api/lineage/queries/' + encodeURIComponent(qid) + '/impact', {
        query: query,
        signal: extra && extra.signal
      })
    },
    path: function (qid, targetId, extra) {
      return request('/api/lineage/queries/' + encodeURIComponent(qid) + '/path', {
        query: { targetId: targetId },
        signal: extra && extra.signal
      })
    },
    evidence: function (qid, rid, extra) {
      return request(
        '/api/lineage/queries/' +
          encodeURIComponent(qid) +
          '/relations/' +
          encodeURIComponent(rid) +
          '/evidence',
        { signal: extra && extra.signal }
      )
    },
    relations: function (qid, id, kind, extra) {
      var query = { kind: kind || 'all' }
      if (extra) {
        if (extra.cursor) {
          query.cursor = extra.cursor
        }
        if (extra.limit != null) {
          query.limit = extra.limit
        }
      }
      return request(
        '/api/lineage/queries/' +
          encodeURIComponent(qid) +
          '/nodes/' +
          encodeURIComponent(id) +
          '/relations',
        { query: query, signal: extra && extra.signal }
      )
    }
  }
}

export var lineageClient = createLineageClient()
