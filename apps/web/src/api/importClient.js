import { getConfig } from '../config.js'

export function createImportClient(options) {
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
        parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(value))
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
      init.body = JSON.stringify((spec && spec.body) != null ? spec.body : {})
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
    buildUrl: buildUrl,
    createImport: function (batch, extra) {
      return request('/api/imports', {
        method: 'POST',
        body: batch,
        signal: extra && extra.signal
      })
    },
    getImport: function (runId, extra) {
      var query = {}
      if (extra) {
        if (extra.cursor) {
          query.cursor = extra.cursor
        }
        if (extra.limit != null) {
          query.limit = extra.limit
        }
      }
      return request('/api/imports/' + encodeURIComponent(runId), {
        query: query,
        signal: extra && extra.signal
      })
    },
    publish: function (runId, expectedActiveSnapshotId, extra) {
      var expected = expectedActiveSnapshotId
      if (expected === '' || expected === undefined) {
        expected = null
      }
      return request('/api/imports/' + encodeURIComponent(runId) + '/publish', {
        method: 'POST',
        body: { expectedActiveSnapshotId: expected },
        signal: extra && extra.signal
      })
    }
  }
}

export var importClient = createImportClient()
