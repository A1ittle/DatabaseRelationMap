import { describe, expect, it } from 'vitest'
import { nodeBadge, nodeCopy } from './nodeBadge.js'

describe('nodeCopy', function () {
  it('uses technical name and only shows a distinct Chinese title', function () {
    expect(nodeCopy({ technicalName: 'ods.trade_order', displayName: 'ods.trade_order' })).toEqual({
      name: 'ods.trade_order',
      title: ''
    })
    expect(nodeCopy({ technicalName: 'ods.trade_order', displayName: '交易订单表' })).toEqual({
      name: 'ods.trade_order',
      title: '交易订单表'
    })
    expect(nodeCopy({ id: 'root' })).toEqual({ name: 'root', title: '' })
  })
})

describe('nodeBadge', function () {
  it('follows 当前 > +N/已展开 > 另 N > 终端 > type', function () {
    expect(
      nodeBadge({
        isSeed: true,
        treeChildCount: 3,
        crossCount: 2,
        type: 'java'
      })
    ).toEqual({ kind: 'now', text: '当前', expand: false })

    expect(nodeBadge({ treeChildCount: 3, expanded: false, type: 'table' })).toEqual({
      kind: 'expand',
      text: '+3',
      expand: true
    })
    expect(nodeBadge({ treeChildCount: 3, expanded: true, type: 'table' })).toEqual({
      kind: 'expand',
      text: '已展开',
      expand: true
    })

    expect(nodeBadge({ treeChildCount: 0, crossCount: 2, type: 'view' })).toEqual({
      kind: 'cross',
      text: '另 2',
      expand: false
    })

    expect(nodeBadge({ type: 'java' })).toEqual({
      kind: 'terminal',
      text: '终端',
      expand: false
    })

    expect(nodeBadge({ type: 'procedure' })).toEqual({
      kind: 'type',
      text: '存储过程',
      expand: false
    })
  })
})
