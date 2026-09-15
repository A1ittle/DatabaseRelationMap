import { describe, expect, it } from 'vitest'
import { readEnv } from './config.js'

describe('readEnv', function () {
  it('defaults API base to 127.0.0.1:8080 and CSRF to dev', function () {
    expect(readEnv({})).toEqual({
      apiBase: 'http://127.0.0.1:8080',
      csrfToken: 'dev',
      embedGroups: ''
    })
  })

  it('treats empty VITE_API_BASE as same-origin', function () {
    expect(readEnv({ VITE_API_BASE: '', VITE_EMBED_GROUPS: 'g1,g2' }).apiBase).toBe('')
    expect(readEnv({ VITE_API_BASE: '', VITE_EMBED_GROUPS: 'g1,g2' }).embedGroups).toBe('g1,g2')
  })

  it('reads VITE_CSRF_TOKEN and defaults to dev matching the API', function () {
    expect(readEnv({}).csrfToken).toBe('dev')
    expect(readEnv({ VITE_CSRF_TOKEN: 'host-token' }).csrfToken).toBe('host-token')
  })
})
