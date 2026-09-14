<template>
  <aside class="detail-panel">
    <div class="panel-head">对象详情</div>
    <p v-if="!detail && !loading" class="hint">选中树上的节点查看名称、类型、id 与关系摘要。</p>
    <p v-if="loading" class="hint">加载详情…</p>
    <div v-if="detail" class="detail-body">
      <dl>
        <dt>名称</dt>
        <dd class="truncate" :title="name">{{ name }}</dd>
        <dt>类型</dt>
        <dd>
          <span class="badge type" :class="'type-' + objectType">{{ objectType }}</span>
        </dd>
        <dt>id</dt>
        <dd class="mono">{{ objectId }}</dd>
        <dt>namespace</dt>
        <dd class="mono">{{ namespace }}</dd>
        <dt>system</dt>
        <dd>{{ system }}</dd>
        <dt>minHops</dt>
        <dd>{{ minHops }}</dd>
        <dt>layoutRank</dt>
        <dd>{{ layoutRank }}</dd>
        <dt>直接下游</dt>
        <dd>{{ detail.directDownstreamCount }}</dd>
        <dt>可换根</dt>
        <dd>{{ detail.canSetAsRoot ? '是' : '否' }}</dd>
      </dl>
      <button
        type="button"
        class="change-root"
        :disabled="!canChangeRoot"
        @click="$emit('change-root')"
      >
        换根
      </button>
      <div v-if="relations.length" class="rel-block">
        <div class="panel-head nested">关系摘要</div>
        <ul class="rel-list">
          <li v-for="edge in relations" :key="edge.relation.id" :class="kindClass(edge.kind)">
            <span class="kind-label">{{ edge.kind }}</span>
            <span class="rel">{{ edge.relation.rel }}</span>
            <span class="endpoint">{{ edge.relation.source }} → {{ edge.relation.target }}</span>
          </li>
        </ul>
      </div>
    </div>
  </aside>
</template>

<script>
import { kindClass } from '../graph/treeFromProjection.js'

export default {
  name: 'DetailPanel',
  props: {
    detail: { type: Object, default: null },
    relations: { type: Array, default: function () { return [] } },
    loading: { type: Boolean, default: false },
    canChangeRoot: { type: Boolean, default: false }
  },
  methods: {
    kindClass: kindClass
  },
  computed: {
    node: function () {
      return (this.detail && this.detail.node) || {}
    },
    obj: function () {
      return this.node.object || {}
    },
    name: function () {
      return this.obj.displayName || this.obj.technicalName || this.obj.id || ''
    },
    objectType: function () {
      return this.obj.type || ''
    },
    objectId: function () {
      return this.obj.id || ''
    },
    namespace: function () {
      return this.obj.namespace || ''
    },
    system: function () {
      return this.obj.system || ''
    },
    minHops: function () {
      return this.node.minHops
    },
    layoutRank: function () {
      return this.node.layoutRank
    }
  }
}
</script>
