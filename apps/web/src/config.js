/**
 * Runtime config for the embedded Vue 2 shell.
 * VITE_API_BASE defaults to http://127.0.0.1:8080 (OpenAPI host).
 * Empty VITE_API_BASE uses same-origin (Vite /api proxy in dev).
 */
export function readEnv(env) {
  const src = env || (typeof import.meta !== 'undefined' ? import.meta.env : {})
  var rawBase = src.VITE_API_BASE
  var apiBase
  if (rawBase === '') {
    apiBase = ''
  } else {
    apiBase = String(rawBase || 'http://127.0.0.1:8080').replace(/\/$/, '')
  }
  var csrfToken = src.VITE_CSRF_TOKEN ? String(src.VITE_CSRF_TOKEN) : 'dev'
  var embedGroups = src.VITE_EMBED_GROUPS ? String(src.VITE_EMBED_GROUPS).trim() : ''
  return {
    apiBase: apiBase,
    csrfToken: csrfToken,
    embedGroups: embedGroups
  }
}

export function getConfig() {
  return readEnv()
}
