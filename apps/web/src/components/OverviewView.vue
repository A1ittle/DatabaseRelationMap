<template>
  <section class="overview-panel" aria-label="层级类型总览">
    <div class="panel-head">总览 · 层 × 类型（仅 count，不连线）</div>
    <p class="hint">按 hop × 类型收成簇。某类型超过 5 个先出数量条，点开再看名字。定位才选中对象，不根据 count 发明边。</p>
    <p v-if="loading && !clusters.length" class="hint">加载总览…</p>
    <p v-if="!loading && !clusters.length" class="hint">没有簇。环查询仍可按类型聚合（layoutRank 可能为空）。</p>
    <ul class="cluster-list">
      <li
        v-for="cluster in clusters"
        :key="cluster.id"
        class="cluster-row"
        :class="{ open: cluster.id === selectedClusterId }"
      >
        <button
          v-if="showsCountBar(cluster)"
          type="button"
          class="cluster"
          :aria-expanded="cluster.id === selectedClusterId ? 'true' : 'false'"
          @click="$emit('open-cluster', cluster)"
        >
          <div class="cluster-top">
            <span>{{ clusterTitle(cluster) }}</span>
            <span class="badge">{{ cluster.id === selectedClusterId ? '已展开' : '点击展开' }}</span>
          </div>
          <div class="cluster-count">{{ cluster.count }}</div>
          <div class="bars"><i :style="{ width: '100%', background: typeColor(cluster.type) }"></i></div>
          <span class="node-title">先看数量，避免 {{ cluster.count }} 个名字同时出现</span>
        </button>
        <button
          v-else
          type="button"
          class="cluster-btn"
          @click="$emit('open-cluster', cluster)"
        >
          <span class="rank">{{ clusterTitle(cluster) }}</span>
          <span class="badge type" :class="'type-' + cluster.type">{{ cluster.type }}</span>
          <span class="count">count {{ cluster.count }}</span>
          <span class="mono muted truncate" :title="cluster.id">{{ cluster.id }}</span>
        </button>
        <div v-if="cluster.id === selectedClusterId" class="member-block">
          <p v-if="membersLoading && !members.length" class="hint">加载成员…</p>
          <ul class="member-list">
            <li v-for="node in clusterMembers(cluster)" :key="node.object.id">
              <button
                type="button"
                class="member-btn"
                :class="{ selected: selectedId === node.object.id }"
                :title="fullName(node)"
                @click="$emit('locate-member', node.object.id)"
              >
                <span class="truncate name">{{ label(node) }}</span>
                <span class="badge type" :class="'type-' + node.object.type">{{ node.object.type }}</span>
                <span class="muted">hops {{ node.minHops }}</span>
              </button>
            </li>
          </ul>
          <p v-if="restMemberCount(cluster)" class="hint">其余 {{ restMemberCount(cluster) }} 个</p>
          <button
            v-if="membersPage && membersPage.hasMore"
            type="button"
            class="linkish"
            :disabled="membersLoading"
            @click="$emit('more-members')"
          >
            {{ membersLoading ? '加载中…' : '加载更多成员' }}
          </button>
        </div>
      </li>
    </ul>
    <button
      v-if="page && page.hasMore"
      type="button"
      class="linkish"
      :disabled="loading"
      @click="$emit('more-clusters')"
    >
      {{ loading ? '加载中…' : '加载更多簇' }}
    </button>
  </section>
</template>

<script>
import { objectLabel } from '../url/viewState.js'

var TYPE_LABEL = {
  table: '表',
  view: '视图',
  procedure: '存储过程',
  java: '终端 · Java'
}

var TYPE_COLOR = {
  table: 'var(--table)',
  view: 'var(--view)',
  procedure: 'var(--proc)',
  java: 'var(--java)'
}

var CLUSTER_NAME_CAP = 5

export default {
  name: 'OverviewView',
  props: {
    clusters: { type: Array, default: function () { return [] } },
    page: { type: Object, default: null },
    loading: { type: Boolean, default: false },
    selectedClusterId: { type: String, default: null },
    members: { type: Array, default: function () { return [] } },
    membersPage: { type: Object, default: null },
    membersLoading: { type: Boolean, default: false },
    selectedId: { type: String, default: null }
  },
  methods: {
    clusterMembers: function (cluster) {
      if (!cluster || cluster.id !== this.selectedClusterId) {
        return []
      }
      return this.members || []
    },
    restMemberCount: function (cluster) {
      var shown
      var total
      if (!cluster || cluster.id !== this.selectedClusterId) {
        return 0
      }
      shown = (this.members || []).length
      total = cluster.count == null ? shown : Number(cluster.count)
      return total > shown ? total - shown : 0
    },
    showsCountBar: function (cluster) {
      return !!(cluster && Number(cluster.count) > CLUSTER_NAME_CAP)
    },
    clusterTitle: function (cluster) {
      var rank = cluster && cluster.layoutRank
      var type = (cluster && cluster.type) || ''
      var typeLabel = TYPE_LABEL[type] || type
      var col
      if (rank === 0) {
        col = '当前表'
      } else if (rank == null) {
        col = '层 —'
      } else {
        col = '第 ' + rank + ' 层'
      }
      return col + ' · ' + typeLabel
    },
    typeColor: function (type) {
      return TYPE_COLOR[type] || 'var(--muted)'
    },
    label: function (node) {
      return objectLabel(node && node.object, node && node.object && node.object.id)
    },
    fullName: function (node) {
      var obj = node && node.object
      if (!obj) {
        return ''
      }
      return (obj.displayName || obj.technicalName || '') + ' ' + (obj.id || '')
    }
  }
}
</script>
