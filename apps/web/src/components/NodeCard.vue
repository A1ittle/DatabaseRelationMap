<template>
  <button
    type="button"
    class="node"
    :class="nodeClass"
    :data-id="nodeId"
    :aria-current="isSeed ? 'true' : 'false'"
    :aria-label="ariaLabel"
    :title="copy.name"
    @click="$emit('select', nodeId)"
  >
    <span class="node-ico" :class="icoClass" aria-hidden="true">
      <svg v-if="objectType === 'table'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
        <rect x="4" y="5" width="16" height="14" rx="1.5" />
        <path d="M4 10h16M10 5v14" />
      </svg>
      <svg v-else-if="objectType === 'view'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
        <ellipse cx="12" cy="12" rx="8" ry="4" />
        <path d="M4 12v3c0 2.2 3.6 4 8 4s8-1.8 8-4v-3" />
      </svg>
      <svg v-else-if="objectType === 'procedure'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
        <path d="M8 7H6a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2M16 7h2a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2h-2M10 12h4" />
      </svg>
      <svg v-else-if="objectType === 'java'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
        <path d="M8 8l-4 4 4 4M16 8l4 4-4 4M13 6l-2 12" />
      </svg>
    </span>
    <span class="node-copy">
      <span class="node-name">{{ copy.name }}</span>
      <span v-if="copy.title" class="node-title">{{ copy.title }}</span>
    </span>
    <span
      v-if="badge.kind === 'now'"
      class="now"
    >{{ badge.text }}</span>
    <span
      v-else
      class="badge"
      :class="{ 'cross-b': badge.kind === 'cross', 'is-expand': badge.expand }"
      :data-expand="badge.expand ? nodeId : null"
      @click="onBadgeClick"
    >{{ badge.text }}</span>
  </button>
</template>

<script>
import { nodeBadge, nodeCopy } from '../graph/nodeBadge.js'

export default {
  name: 'NodeCard',
  props: {
    node: { type: Object, required: true },
    seedId: { type: String, default: null },
    selectedId: { type: String, default: null },
    expanded: { type: Boolean, default: false },
    dim: { type: Boolean, default: false }
  },
  computed: {
    nodeId: function () {
      return (this.node.object && this.node.object.id) || ''
    },
    objectType: function () {
      return (this.node.object && this.node.object.type) || 'unknown'
    },
    isSeed: function () {
      return !!this.seedId && this.nodeId === this.seedId
    },
    isSel: function () {
      return !!this.selectedId && this.nodeId === this.selectedId
    },
    copy: function () {
      return nodeCopy(this.node.object)
    },
    treeChildCount: function () {
      return this.node.treeChildCount || 0
    },
    badge: function () {
      return nodeBadge({
        isSeed: this.isSeed,
        treeChildCount: this.treeChildCount,
        expanded: this.expanded,
        crossCount: this.node.crossCount || 0,
        type: this.objectType
      })
    },
    icoClass: function () {
      return 't-' + this.objectType
    },
    nodeClass: function () {
      return {
        'is-seed': this.isSeed,
        'is-sel': this.isSel,
        'is-dim': this.dim,
        'is-leaf': this.objectType === 'java'
      }
    },
    ariaLabel: function () {
      var parts = [this.copy.title, this.copy.name].filter(Boolean)
      if (this.treeChildCount) {
        parts.push(this.treeChildCount + ' 个子节点')
      }
      if (this.node.crossCount) {
        parts.push('另有 ' + this.node.crossCount + ' 条跨支')
      }
      return parts.join('，')
    }
  },
  methods: {
    onBadgeClick: function (ev) {
      if (!this.badge.expand) {
        return
      }
      ev.stopPropagation()
      this.$emit('toggle', this.nodeId)
      this.$emit('select', this.nodeId)
    }
  }
}
</script>
