<template>
  <li class="tree-item" :class="['type-' + objectType, { selected: selected }]">
    <div class="tree-row" :class="{ selected: selected }" @click="$emit('select', nodeId)">
      <button
        v-if="expandable"
        type="button"
        class="twist"
        :aria-expanded="open ? 'true' : 'false'"
        :title="open ? '折叠' : '展开'"
        @click.stop="$emit('toggle', nodeId)"
      >
        {{ open ? '▾' : '▸' }}
      </button>
      <span v-else class="twist spacer"></span>
      <span class="name">{{ displayName }}</span>
      <span class="badge type" :class="'type-' + objectType">{{ objectType }}</span>
      <span v-if="treeChildCount" class="badge quiet">子 {{ treeChildCount }}</span>
      <span v-if="crossCount" class="badge kind-cross">跨 {{ crossCount }}</span>
    </div>
    <ul v-if="open" class="tree-list">
      <tree-node
        v-for="cid in treeKids"
        :key="cid"
        :node-id="cid"
        :index="index"
        :expanded="expanded"
        :selected-id="selectedId"
        :child-pages="childPages"
        @select="$emit('select', $event)"
        @toggle="$emit('toggle', $event)"
        @more="$emit('more', $event)"
      />
      <li
        v-for="edge in crossList"
        :key="edge.relation.id"
        class="cross-row"
        :class="kindClass(edge.kind)"
      >
        <span class="kind-label">{{ edge.kind }}</span>
        <span class="rel">{{ edge.relation.rel }}</span>
        <span class="endpoint">{{ edge.relation.source }} → {{ edge.relation.target }}</span>
      </li>
      <li v-if="page && page.hasMore" class="more-row">
        <button type="button" class="linkish" :disabled="page.loading" @click.stop="$emit('more', nodeId)">
          {{ page.loading ? '加载中…' : '加载更多子对象' }}
        </button>
      </li>
      <li v-if="open && !treeKids.length && !(page && page.hasMore) && !crossList.length" class="empty-row">
        无已投影的子节点
      </li>
    </ul>
  </li>
</template>

<script>
import { kindClass, crossEdgesFor } from '../graph/treeFromProjection.js'

export default {
  name: 'TreeNode',
  props: {
    nodeId: { type: String, required: true },
    index: { type: Object, required: true },
    expanded: { type: Object, required: true },
    selectedId: { type: String, default: null },
    childPages: { type: Object, required: true }
  },
  methods: {
    kindClass: kindClass
  },
  computed: {
    node: function () {
      return this.index.nodesById[this.nodeId] || { object: { id: this.nodeId, type: '', displayName: this.nodeId } }
    },
    objectType: function () {
      return (this.node.object && this.node.object.type) || 'unknown'
    },
    displayName: function () {
      var obj = this.node.object || {}
      return obj.displayName || obj.technicalName || obj.id || this.nodeId
    },
    treeChildCount: function () {
      return this.node.treeChildCount || 0
    },
    crossCount: function () {
      return this.node.crossCount || 0
    },
    selected: function () {
      return this.selectedId === this.nodeId
    },
    open: function () {
      return !!this.expanded[this.nodeId]
    },
    expandable: function () {
      var count = this.treeChildCount
      var page = this.childPages[this.nodeId]
      var kids = (this.index.treeChildren && this.index.treeChildren[this.nodeId]) || []
      return count > 0 || kids.length > 0 || (page && page.hasMore)
    },
    treeKids: function () {
      return (this.index.treeChildren && this.index.treeChildren[this.nodeId]) || []
    },
    crossList: function () {
      return crossEdgesFor(this.index, this.nodeId)
    },
    page: function () {
      return this.childPages[this.nodeId] || null
    }
  }
}
</script>
