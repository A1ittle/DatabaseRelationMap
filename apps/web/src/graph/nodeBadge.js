/** OpenDesign NodeCard copy + badge priority (06-interaction). */

export var TYPE_LABEL = {
  table: '表',
  view: '视图',
  procedure: '存储过程',
  java: 'Java 程序'
}

export function nodeCopy(object) {
  var obj = object || {}
  var technical = obj.technicalName || obj.id || ''
  var display = obj.displayName || ''
  var name = technical || display
  var title = display && display !== name ? display : ''
  return { name: name, title: title }
}

/**
 * Priority: 当前 (seed) > +N/已展开 > 另 N cross > 终端 (java) > type.
 */
export function nodeBadge(input) {
  var treeChildCount = (input && input.treeChildCount) || 0
  var crossCount = (input && input.crossCount) || 0
  var type = (input && input.type) || ''
  if (input && input.isSeed) {
    return { kind: 'now', text: '当前', expand: false }
  }
  if (treeChildCount > 0) {
    return {
      kind: 'expand',
      text: input.expanded ? '已展开' : '+' + treeChildCount,
      expand: true
    }
  }
  if (crossCount > 0) {
    return { kind: 'cross', text: '另 ' + crossCount, expand: false }
  }
  if (type === 'java') {
    return { kind: 'terminal', text: '终端', expand: false }
  }
  return {
    kind: 'type',
    text: TYPE_LABEL[type] || type || '对象',
    expand: false
  }
}
