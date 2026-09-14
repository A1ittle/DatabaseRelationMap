<template>
  <aside class="cross-panel">
    <div class="panel-head">跨支 / 未分类</div>
    <p class="hint">来自当前投影 kind，与展开顺序无关。点击定位另一端点。</p>
    <p v-if="!edges.length" class="hint">当前视图没有跨支或未分类边。</p>
    <ul v-else class="cross-list">
      <li
        v-for="edge in edges"
        :key="edge.relation.id"
        class="cross-item"
        :class="[kindClass(edge.kind), { incident: isIncident(edge) }]"
      >
        <button type="button" class="locate" @click="$emit('locate', edge)">定位</button>
        <span class="kind-label">{{ edge.kind }}</span>
        <span class="rel">{{ edge.relation.rel }}</span>
        <span class="endpoint">{{ label(edge.relation.source) }} → {{ label(edge.relation.target) }}</span>
      </li>
    </ul>
  </aside>
</template>

<script>
import { edgeIncidentTo, otherEndpoint } from '../graph/crossList.js'
import { kindClass } from '../graph/treeFromProjection.js'

export default {
  name: 'CrossPanel',
  props: {
    edges: { type: Array, default: function () { return [] } },
    selectedId: { type: String, default: null },
    nodesById: { type: Object, default: function () { return {} } }
  },
  methods: {
    kindClass: kindClass,
    otherEndpoint: otherEndpoint,
    isIncident: function (edge) {
      return edgeIncidentTo(edge, this.selectedId)
    },
    label: function (id) {
      var node = this.nodesById && this.nodesById[id]
      var obj = node && node.object
      if (obj) {
        return obj.displayName || obj.technicalName || obj.id
      }
      return id
    }
  }
}
</script>
