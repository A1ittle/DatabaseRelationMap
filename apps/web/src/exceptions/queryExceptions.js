/**
 * Distinct query/import exception banners. Codes match OpenAPI Error.code.
 * No OIDC mapping is invented: FORBIDDEN / UNAUTHENTICATED are shown only
 * when the API actually returns those codes (embed groups / demo-header).
 */

export function codeOf(err) {
  if (!err) {
    return 'REQUEST_FAILED'
  }
  if (err.code && String(err.code).indexOf('HTTP_') !== 0) {
    return String(err.code)
  }
  if (err.body && err.body.code) {
    return String(err.body.code)
  }
  var status = err.status
  if (status === 413) {
    return 'PAYLOAD_TOO_LARGE'
  }
  if (status === 401) {
    return 'UNAUTHENTICATED'
  }
  if (status === 403) {
    return 'FORBIDDEN'
  }
  if (status === 410) {
    return 'QUERY_EXPIRED'
  }
  if (status === 409) {
    return 'POLICY_CHANGED'
  }
  if (status === 503 || (status != null && status >= 500)) {
    return 'TEMPORARILY_UNAVAILABLE'
  }
  if (err.name === 'TypeError' || err.name === 'NetworkError') {
    return 'TEMPORARILY_UNAVAILABLE'
  }
  return err.code ? String(err.code) : 'REQUEST_FAILED'
}

export function issuesFromBody(body) {
  if (!body) {
    return []
  }
  if (Array.isArray(body.issues)) {
    return body.issues
  }
  if (body.details && Array.isArray(body.details.issues)) {
    return body.details.issues
  }
  return []
}

export function shouldDestroyQuery(code) {
  return code === 'POLICY_CHANGED' || code === 'QUERY_EXPIRED'
}

function banner(code, message, extra) {
  extra = extra || {}
  return {
    code: code,
    message: message,
    level: extra.level || 'error',
    action: extra.action || '',
    retryable: !!extra.retryable,
    issues: extra.issues || []
  }
}

export function bannerForError(err, opts) {
  opts = opts || {}
  var code = codeOf(err)
  var apiMessage = (err && ((err.body && err.body.message) || err.message)) || ''
  var issues = issuesFromBody(err && err.body)
  var retryable = !!(err && (err.retryable || (err.body && err.body.retryable)))

  if (code === 'LINEAGE_NOT_COLLECTED') {
    return banner(code, '未采集：当前没有已发布的血缘快照。请先在「数据导入」页导入并发布（样例路径 fixtures/v1/import.json）。', {
      level: 'notice',
      action: 'import'
    })
  }
  if (code === 'POLICY_CHANGED') {
    return banner(code, '权限或策略已变化，查询上下文已销毁。请重新搜索。', {
      level: 'error',
      action: 'research'
    })
  }
  if (code === 'QUERY_EXPIRED') {
    return banner(code, '查询已过期，上下文已销毁。请重新搜索。', {
      level: 'error',
      action: 'research'
    })
  }
  if (code === 'TEMPORARILY_UNAVAILABLE') {
    return banner(code, apiMessage || '服务暂不可用，请稍后重试。', {
      level: 'error',
      retryable: true
    })
  }
  if (code === 'UNAUTHENTICATED') {
    return banner(code, apiMessage || '未认证。嵌入会话无效（非 OIDC 映射）。', { level: 'error' })
  }
  if (code === 'FORBIDDEN') {
    return banner(code, apiMessage || '当前嵌入组无权访问。', { level: 'error' })
  }
  if (code === 'PAYLOAD_TOO_LARGE') {
    return banner(code, apiMessage || '请求体过大（PAYLOAD_TOO_LARGE / 413）。', { level: 'error' })
  }
  if (code === 'IMPORT_INVALID') {
    return banner(code, apiMessage || '导入校验失败（IMPORT_INVALID）。', {
      level: 'error',
      issues: issues
    })
  }
  if (code === 'PUBLISH_CONFLICT') {
    return banner(code, apiMessage || '发布冲突：活动快照与 expectedActiveSnapshotId 不一致（PUBLISH_CONFLICT）。', {
      level: 'error'
    })
  }
  if (code === 'PROJECTION_LIMIT') {
    return banner(code, apiMessage || '超预算，已保留原图', { level: 'notice' })
  }
  if (code === 'INVALID_SEED') {
    return banner(code, apiMessage || '种子不在当前活动快照中。', { level: 'error', action: 'research' })
  }
  if (code === 'NOT_FOUND') {
    return banner(code, apiMessage || '对象不存在或当前嵌入组不可见。', { level: 'error' })
  }
  if (code === 'REQUEST_FAILED' || String(code).indexOf('HTTP_') === 0) {
    return banner(code === 'REQUEST_FAILED' ? 'REQUEST_FAILED' : code, apiMessage || opts.fallback || '请求失败。', {
      level: 'error',
      retryable: retryable
    })
  }
  return banner(code, apiMessage || opts.fallback || '请求失败。', {
    level: 'error',
    retryable: retryable,
    issues: issues
  })
}

export function idleWorkbenchBanner() {
  return banner('EMPTY', '尚未搜索。若无活动快照，请先在「数据导入」页提交 fixtures/v1/import.json 并发布。', {
    level: 'empty'
  })
}

export function noHitsBanner() {
  return banner('NO_HITS', '没有搜索命中。未查全不等于无影响；若尚未发布快照，请先导入 fixtures/v1/import.json。', {
    level: 'empty'
  })
}

export function coverageBanner(meta) {
  if (!meta) {
    return null
  }
  var parts = []
  if (meta.coverage === 'partial') {
    parts.push('来源不完整（coverage=partial）。未查全不等于无影响。')
  } else if (meta.coverage === 'unknown') {
    parts.push('来源覆盖未知（coverage=unknown）。未查全不等于无影响。')
  }
  if (meta.computationStatus === 'incomplete') {
    parts.push('计算未完成（incomplete）。')
  }
  if (!parts.length) {
    return null
  }
  return banner('SOURCE_INCOMPLETE', parts.join(' '), { level: 'notice' })
}
