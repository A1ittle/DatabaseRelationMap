import { describe, expect, it } from 'vitest'
import {
  bannerForError,
  codeOf,
  coverageBanner,
  idleWorkbenchBanner,
  issuesFromBody,
  noHitsBanner,
  shouldDestroyQuery
} from './queryExceptions.js'

describe('queryExceptions', function () {
  it('maps LINEAGE_NOT_COLLECTED to 未采集', function () {
    var b = bannerForError({ code: 'LINEAGE_NOT_COLLECTED', status: 404, message: 'no published snapshot' })
    expect(b.code).toBe('LINEAGE_NOT_COLLECTED')
    expect(b.message).toMatch(/未采集/)
    expect(b.action).toBe('import')
    expect(shouldDestroyQuery(b.code)).toBe(false)
  })

  it('destroys query context on POLICY_CHANGED and QUERY_EXPIRED', function () {
    var policy = bannerForError({ code: 'POLICY_CHANGED', status: 409 })
    expect(policy.message).toMatch(/策略/)
    expect(policy.action).toBe('research')
    expect(shouldDestroyQuery('POLICY_CHANGED')).toBe(true)
    var expired = bannerForError({ code: 'QUERY_EXPIRED', status: 410 })
    expect(expired.message).toMatch(/过期/)
    expect(shouldDestroyQuery('QUERY_EXPIRED')).toBe(true)
  })

  it('surfaces TEMPORARILY_UNAVAILABLE and generic request failure', function () {
    var unavail = bannerForError({
      code: 'TEMPORARILY_UNAVAILABLE',
      status: 503,
      retryable: true,
      message: 'database is not configured'
    })
    expect(unavail.code).toBe('TEMPORARILY_UNAVAILABLE')
    expect(unavail.retryable).toBe(true)
    var net = bannerForError({ name: 'TypeError', message: 'Failed to fetch' })
    expect(codeOf({ name: 'TypeError' })).toBe('TEMPORARILY_UNAVAILABLE')
    expect(net.code).toBe('TEMPORARILY_UNAVAILABLE')
    var fail = bannerForError({ message: 'boom' }, { fallback: '搜索失败' })
    expect(fail.code).toBe('REQUEST_FAILED')
  })

  it('does not invent OIDC: FORBIDDEN/UNAUTHENTICATED only from API codes', function () {
    expect(bannerForError({ code: 'FORBIDDEN', status: 403 }).message).toMatch(/嵌入组/)
    expect(bannerForError({ code: 'UNAUTHENTICATED', status: 401 }).code).toBe('UNAUTHENTICATED')
    expect(codeOf({ status: 401 })).toBe('UNAUTHENTICATED')
    expect(codeOf({ status: 403 })).toBe('FORBIDDEN')
  })

  it('maps import 413 / IMPORT_INVALID issues / PUBLISH_CONFLICT', function () {
    expect(codeOf({ status: 413 })).toBe('PAYLOAD_TOO_LARGE')
    var tooBig = bannerForError({ status: 413, code: 'PAYLOAD_TOO_LARGE' })
    expect(tooBig.code).toBe('PAYLOAD_TOO_LARGE')
    var invalid = bannerForError({
      code: 'IMPORT_INVALID',
      status: 400,
      body: {
        code: 'IMPORT_INVALID',
        message: 'batchKey already used',
        issues: [{ code: 'DUP', path: '/objects/0/id', message: 'dup', severity: 'error' }]
      }
    })
    expect(invalid.issues).toHaveLength(1)
    expect(invalid.issues[0].path).toBe('/objects/0/id')
    expect(bannerForError({ code: 'PUBLISH_CONFLICT', status: 409 }).message).toMatch(/PUBLISH_CONFLICT/)
  })

  it('keeps empty / no-hit / coverage banners distinct', function () {
    expect(idleWorkbenchBanner().code).toBe('EMPTY')
    expect(noHitsBanner().code).toBe('NO_HITS')
    expect(coverageBanner(null)).toBe(null)
    expect(coverageBanner({ coverage: 'complete', computationStatus: 'complete' })).toBe(null)
    var partial = coverageBanner({ coverage: 'partial', computationStatus: 'complete' })
    expect(partial.code).toBe('SOURCE_INCOMPLETE')
    expect(partial.message).toMatch(/partial/)
    expect(partial.message).toMatch(/未查全不等于无影响/)
    var unknown = coverageBanner({ coverage: 'unknown', computationStatus: 'incomplete' })
    expect(unknown.message).toMatch(/unknown/)
    expect(unknown.message).toMatch(/incomplete/)
  })

  it('reads issues from Error body without inventing fields', function () {
    expect(issuesFromBody(null)).toEqual([])
    expect(issuesFromBody({ issues: [{ code: 'X' }] })).toEqual([{ code: 'X' }])
  })
})
