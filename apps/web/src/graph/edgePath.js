/**
 * Cubic connector used by the OpenDesign hops canvas.
 * Ported from prototype lineage-map-v2.html `edgePath`.
 */
export function edgePath(x1, y1, x2, y2, cross) {
  var dx
  var midX
  var midY
  if (x2 > x1 + 12) {
    dx = Math.max(36, Math.abs(x2 - x1) / 2)
    return (
      'M ' +
      x1 +
      ' ' +
      y1 +
      ' C ' +
      (x1 + dx) +
      ' ' +
      y1 +
      ', ' +
      (x2 - dx) +
      ' ' +
      y2 +
      ', ' +
      x2 +
      ' ' +
      y2
    )
  }
  midX = Math.max(x1, x2) + (cross ? 28 : 20)
  midY = (y1 + y2) / 2
  return (
    'M ' +
    x1 +
    ' ' +
    y1 +
    ' C ' +
    midX +
    ' ' +
    y1 +
    ', ' +
    midX +
    ' ' +
    midY +
    ', ' +
    x2 +
    ' ' +
    y2
  )
}

export function edgeClassNames(kind, rel, on, dim) {
  var parts = ['edge']
  var cross = kind === 'cross' || kind === 'unclassified'
  if (rel) {
    parts.push(rel)
  }
  parts.push(cross ? 'cross' : 'tree')
  if (on) {
    parts.push('on')
  } else if (dim) {
    parts.push('dim')
  }
  return parts.join(' ')
}
