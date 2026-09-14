<template>
  <section class="overview-panel" aria-label="层级类型总览">
    <div class="panel-head">总览 · 层 × 类型（仅 count，不连线）</div>
    <p class="hint">簇来自 overview API。展开成员走 members 分页；定位才选中对象，不根据 count 发明边。</p>
    <p v-if="loading && !clusters.length" class="hint">加载总览…</p>
    <p v-if="!loading && !clusters.length" class="hint">没有簇。环查询仍可按类型聚合（layoutRank 可能为空）。</p>
    <ul class="cluster-list">
      <li
        v-for="cluster in clusters"
        :key="cluster.id"
        class="cluster-row"
        :class="{ open: cluster.id === selectedClusterId }"
      >
        <button type="button" class="cluster-btn" @click="$emit('open-cluster', cluster)">
          <span class="rank">层 {{ rankLabel(cluster.layoutRank) }}</span>
          <span class="badge type" :class="'type-' + cluster.type">{{ cluster.type }}</span>
          <span class="count">count {{ cluster.count }}</span>
          <span class="mono muted truncate" :title="cluster.id">{{ cluster.id }}</span>
        </button>
        <div v-if="cluster.id === selectedClusterId" class="member-block">
          <p v-if="membersLoading && !members.length" class="hint">加载成员…</p>
          <ul class="member-list">
            <li v-for="node in members" :key="node.object.id">
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
    rankLabel: function (rank) {
      return rank == null ? '—' : String(rank)
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
