/**
 * Workspace URL contract helpers.
 *
 * Search params (not queryId): mode, seedId, snapshotId, selectedId, targetId, types.
 * queryId is a session credential and is never encoded. Restore recreates the
 * query from seedId (+ optional snapshotId) and re-fetches from the APIs.
 */

export var VIEW_MODES = ['tree', 'overview', 'impact', 'path']

export var ALL_TYPES = ['table', 'view', 'procedure', 'java']

export var DEFAULT_VIEW_STATE = {
  mode: 'tree',
  seedId: null,
  snapshotId: null,
  selectedId: null,
  targetId: null,
  types: ALL_TYPES.slice()
}

function emptyToNull(value) {
  if (value == null) {
    return null
  }
  var s = String(value).trim()
  return s ? s : null
}

export function isAllTypes(types) {
  if (!types || types.length !== ALL_TYPES.length) {
    return false
  }
  var i
  for (i = 0; i < ALL_TYPES.length; i++) {
    if (types.indexOf(ALL_TYPES[i]) === -1) {
      return false
    }
  }
  return true
}

export function normalizeTypes(raw) {
  var parts = []
  if (Array.isArray(raw)) {
    parts = raw
  } else if (raw != null && String(raw).trim()) {
    parts = String(raw).split(',')
  }
  var seen = {}
  var i
  var t
  for (i = 0; i < parts.length; i++) {
    t = String(parts[i] || '').trim()
    if (ALL_TYPES.indexOf(t) !== -1) {
      seen[t] = true
    }
  }
  var out = ALL_TYPES.filter(function (name) {
    return !!seen[name]
  })
  return out.length ? out : ALL_TYPES.slice()
}

export function parseViewState(search) {
  var raw = search == null ? '' : String(search)
  if (raw.charAt(0) === '?') {
    raw = raw.slice(1)
  }
  var params = new URLSearchParams(raw)
  var mode = emptyToNull(params.get('mode')) || 'tree'
  if (VIEW_MODES.indexOf(mode) === -1) {
    mode = 'tree'
  }
  return {
    mode: mode,
    seedId: emptyToNull(params.get('seedId')),
    snapshotId: emptyToNull(params.get('snapshotId')),
    selectedId: emptyToNull(params.get('selectedId')),
    targetId: emptyToNull(params.get('targetId')),
    types: normalizeTypes(params.get('types'))
  }
}

export function serializeViewState(state) {
  var s = state || {}
  var params = new URLSearchParams()
  var mode = s.mode || 'tree'
  if (VIEW_MODES.indexOf(mode) === -1) {
    mode = 'tree'
  }
  if (mode !== 'tree') {
    params.set('mode', mode)
  }
  if (s.seedId) {
    params.set('seedId', s.seedId)
  }
  if (s.snapshotId) {
    params.set('snapshotId', s.snapshotId)
  }
  if (s.selectedId) {
    params.set('selectedId', s.selectedId)
  }
  if (s.targetId) {
    params.set('targetId', s.targetId)
  }
  if (s.types && s.types.length && !isAllTypes(s.types)) {
    params.set('types', normalizeTypes(s.types).join(','))
  }
  return params.toString()
}

export function viewStateFromApp(app) {
  return {
    mode: (app && app.mode) || 'tree',
    seedId: (app && app.seedId) || null,
    snapshotId: (app && app.meta && app.meta.snapshotId) || null,
    selectedId: (app && app.selectedId) || null,
    targetId: (app && app.targetId) || null,
    types: (app && app.filterTypes) || ALL_TYPES.slice()
  }
}

export function adjacentMode(mode, delta) {
  var i = VIEW_MODES.indexOf(mode)
  if (i < 0) {
    i = 0
  }
  var n = VIEW_MODES.length
  var step = delta == null ? 1 : delta
  return VIEW_MODES[(i + step + n * 10) % n]
}

export function cycleNotice(treeStatus) {
  if (treeStatus === 'unavailable_cycle') {
    return '检测到循环依赖，已切换关系清单'
  }
  if (treeStatus === 'unavailable_incomplete') {
    return '计算未完成，主树分类不可用。请使用影响清单或最短路径。'
  }
  return ''
}

export function treeUnavailable(treeStatus) {
  return treeStatus === 'unavailable_cycle' || treeStatus === 'unavailable_incomplete'
}

export function pathStatusMessage(path) {
  if (!path) {
    return ''
  }
  if (path.status === 'found') {
    return ''
  }
  if (path.status === 'not_found') {
    return '无可达路径。'
  }
  if (path.reason === 'PATH_LENGTH_LIMIT') {
    return '路径未知：超过长度上限（未查全不等于无影响）。'
  }
  if (path.reason === 'QUERY_INCOMPLETE') {
    return '路径未知：查询计算未完成（未查全不等于无影响）。'
  }
  return '路径未知。'
}

export function objectLabel(obj, fallback) {
  if (!obj) {
    return fallback || ''
  }
  return obj.displayName || obj.technicalName || obj.id || fallback || ''
}
