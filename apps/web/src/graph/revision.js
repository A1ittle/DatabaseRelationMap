/**
 * Monotonic clientRevision guard.
 * Same (queryId, revision) is applied at most once; older in-flight
 * responses are dropped so a late projection cannot overwrite a newer canvas.
 */
export function createRevisionGuard() {
  var current = 0
  var epoch = 0

  return {
    current: function () {
      return current
    },
    epoch: function () {
      return epoch
    },
    next: function () {
      current += 1
      return current
    },
    reset: function () {
      current = 0
      epoch += 1
    },
    adopt: function (revision) {
      var n = Number(revision)
      if (!isFinite(n) || n < 0) {
        return
      }
      if (n > current) {
        current = n
      }
    },
    isStale: function (sentRevision, sentEpoch) {
      if (sentEpoch != null && Number(sentEpoch) !== epoch) {
        return true
      }
      if (sentRevision == null) {
        return true
      }
      return Number(sentRevision) < current
    },
    /**
     * Apply only when the response revision matches the request we sent
     * and is not older than the latest issued revision. A reset() bumps
     * epoch so in-flight replies from a previous query cannot land.
     */
    shouldApply: function (responseRevision, sentRevision, sentEpoch) {
      if (sentEpoch != null && Number(sentEpoch) !== epoch) {
        return false
      }
      if (responseRevision == null || !isFinite(Number(responseRevision))) {
        return false
      }
      var response = Number(responseRevision)
      if (response < 0) {
        return false
      }
      if (sentRevision != null && response !== Number(sentRevision)) {
        return false
      }
      if (response < current) {
        return false
      }
      return true
    }
  }
}
