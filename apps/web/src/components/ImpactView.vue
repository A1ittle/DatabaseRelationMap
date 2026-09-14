<template>
  <section class="impact-panel" aria-label="影响清单">
    <div class="panel-head">影响清单 · 完整可达（独立 API）</div>
    <p class="hint">列表来自 impact API 的全范围分页，不是当前画布上的节点。</p>
    <p v-if="loading && !items.length" class="hint">加载影响清单…</p>
    <p v-if="!loading && !items.length" class="hint">没有可达对象（或当前类型筛选为空）。</p>
    <p v-if="page && page.total != null" class="hint">共 {{ page.total }}（当前页 {{ items.length }}）</p>
    <ul class="impact-list">
      <li v-for="node in items" :key="node.object.id">
        <button
          type="button"
          class="impact-row"
          :class="{ selected: selectedId === node.object.id }"
          :title="fullName(node)"
          @click="$emit('select', node.object.id)"
        >
          <span class="truncate name">{{ label(node) }}</span>
          <span class="badge type" :class="'type-' + node.object.type">{{ node.object.type }}</span>
          <span class="muted">hops {{ node.minHops }}</span>
          <span class="mono muted truncate" :title="node.object.id">{{ node.object.id }}</span>
        </button>
      </li>
    </ul>
    <button
      v-if="page && page.hasMore"
      type="button"
      class="linkish"
      :disabled="loading"
      @click="$emit('more')"
    >
      {{ loading ? '加载中…' : '加载更多' }}
    </button>
  </section>
</template>

<script>
import { objectLabel } from '../url/viewState.js'

export default {
  name: 'ImpactView',
  props: {
    items: { type: Array, default: function () { return [] } },
    page: { type: Object, default: null },
    loading: { type: Boolean, default: false },
    selectedId: { type: String, default: null }
  },
  methods: {
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
