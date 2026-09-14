/**
 * Atomic projection replace + budget keep-old.
 * Nodes and edges commit together; errors leave the previous canvas.
 */
import { indexProjection, candidateIdsFromProjection, mergeCandidateIds } from './treeFromProjection.js'

export var BUDGET_KEEP_OLD = '超预算，已保留原图'

export function isProjectionLimitError(err) {
  if (!err) {
    return false
  }
  if (err.code === 'PROJECTION_LIMIT') {
    return true
  }
  if (err.body && err.body.code === 'PROJECTION_LIMIT') {
    return true
  }
  return false
}

export function isAbortError(err) {
  if (!err) {
    return false
  }
  return err.name === 'AbortError' || err.code === 'ABORT_ERR'
}

export function isCompleteProjection(projection) {
  return !!(
    projection &&
    Array.isArray(projection.nodes) &&
    Array.isArray(projection.edges)
  )
}

/**
 * Apply a full projection replace or keep the previous canvas.
 * Never writes nodes without edges (or the reverse).
 */
export function resolveProjectionOutcome(input) {
  var canvas = input.canvas
  var guard = input.guard
  var sentRevision = input.sentRevision
  var sentEpoch = input.sentEpoch
  var projection = input.projection
  var error = input.error
  var previousCandidates = input.previousCandidates || []
  var attemptedCandidates = input.attemptedCandidates || previousCandidates
  var seedId = input.seedId

  if (error) {
    if (isAbortError(error)) {
      return {
        canvas: canvas,
        candidateIds: previousCandidates,
        applied: false,
        aborted: true,
        error: '',
        notice: ''
      }
    }
    if (isProjectionLimitError(error)) {
      return {
        canvas: canvas,
        candidateIds: previousCandidates,
        applied: false,
        keepOld: true,
        error: BUDGET_KEEP_OLD,
        notice: ''
      }
    }
    return {
      canvas: canvas,
      candidateIds: previousCandidates,
      applied: false,
      keepOld: true,
      error: (error && error.message) || '投影失败',
      notice: ''
    }
  }

  if (!isCompleteProjection(projection)) {
    return {
      canvas: canvas,
      candidateIds: previousCandidates,
      applied: false,
      keepOld: true,
      error: '投影响应不完整，已保留原图',
      notice: ''
    }
  }

  if (!guard.shouldApply(projection.clientRevision, sentRevision, sentEpoch)) {
    return {
      canvas: canvas,
      candidateIds: attemptedCandidates,
      applied: false,
      stale: true,
      error: '',
      notice: '已丢弃过期 projection（revision ' + projection.clientRevision + '）'
    }
  }

  guard.adopt(projection.clientRevision)
  return {
    canvas: {
      projection: projection,
      index: indexProjection(projection)
    },
    candidateIds: mergeCandidateIds(
      candidateIdsFromProjection(projection),
      attemptedCandidates,
      seedId
    ),
    applied: true,
    keepOld: false,
    error: '',
    notice: ''
  }
}

export function emptyCanvas() {
  return {
    projection: { nodes: [], edges: [], clientRevision: 0 },
    index: indexProjection({ nodes: [], edges: [] })
  }
}
