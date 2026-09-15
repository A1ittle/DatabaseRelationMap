/**
 * URL snapshot restore, in-flight epoch, and collapse candidate helpers.
 * Empty/null snapshotId means "follow the active snapshot".
 */

export function normalizeSnapshotId(value) {
  if (value == null) {
    return null
  }
  var s = String(value).trim()
  return s ? s : null
}

/**
 * Recreate the query when the seed changes, or when the URL snapshot
 * (normalized empty/null) differs from the live query meta.snapshotId.
 * Same seed + same snapshot keeps the current query.
 */
export function shouldRecreateQuery(seed, urlSnap, currentSeed, metaSnap) {
  if (!seed) {
    return false
  }
  if (seed !== currentSeed) {
    return true
  }
  return normalizeSnapshotId(urlSnap) !== normalizeSnapshotId(metaSnap)
}

/** Late createQuery failures must not destroy a newer epoch's query. */
export function shouldApplyQueryFailure(sentEpoch, currentEpoch) {
  return sentEpoch === currentEpoch
}

/**
 * Roll back optimistic candidateIds only for the in-flight attempt that is
 * still current. Stale/aborted outcomes leave the newer query's set alone.
 */
export function shouldRollbackCandidates(outcome) {
  if (!outcome) {
    return false
  }
  if (outcome.applied || outcome.stale || outcome.aborted) {
    return false
  }
  return true
}

/**
 * After collapse, candidates are seed + children of still-expanded reachable
 * nodes + pins. Descendants of the collapsed node are dropped even if their
 * expanded flag was left set, so they cannot linger as ghost budget users.
 */
export function candidateIdsAfterCollapse(input) {
  var seedId = input && input.seedId
  var expanded = (input && input.expanded) || {}
  var childPages = (input && input.childPages) || {}
  var pins = (input && input.pins) || []
  var seen = {}
  var out = []

  function add(id) {
    if (!id || seen[id]) {
      return false
    }
    seen[id] = true
    out.push(id)
    return true
  }

  add(seedId)
  var queue = []
  if (seedId) {
    queue.push(seedId)
  }
  var i = 0
  var id
  var page
  var ids
  var j
  while (i < queue.length) {
    id = queue[i++]
    if (!expanded[id]) {
      continue
    }
    page = childPages[id]
    ids = (page && page.ids) || []
    for (j = 0; j < ids.length; j++) {
      if (add(ids[j])) {
        queue.push(ids[j])
      }
    }
  }
  for (i = 0; i < pins.length; i++) {
    add(pins[i])
  }
  return out
}

export function candidateSetsEqual(a, b) {
  var left = a || []
  var right = b || []
  if (left.length !== right.length) {
    return false
  }
  var seen = {}
  var i
  for (i = 0; i < left.length; i++) {
    seen[left[i]] = true
  }
  for (i = 0; i < right.length; i++) {
    if (!seen[right[i]]) {
      return false
    }
  }
  return true
}
