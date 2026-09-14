<template>
  <section class="path-panel" aria-label="最短路径">
    <div class="panel-head">最短路径</div>
    <p class="hint">调用 path API（授权查询图，可走跨支）。证据来自 evidence API；sourceRef 仅作可复制文本。</p>
    <form class="path-form" @submit.prevent="$emit('submit', (targetId || '').trim())">
      <label class="path-label">
        目标 id
        <input
          :value="targetId"
          type="text"
          name="targetId"
          maxlength="200"
          placeholder="targetId"
          aria-label="路径目标对象 id"
          @input="$emit('update-target', $event.target.value)"
        />
      </label>
      <button type="submit" :disabled="loading || !(targetId || '').trim()">查询路径</button>
      <button
        type="button"
        class="ghost"
        :disabled="!selectedId"
        @click="$emit('use-selected')"
      >
        使用当前选中
      </button>
    </form>
    <p v-if="loading" class="hint">加载路径…</p>
    <p v-if="statusMessage" class="banner notice">{{ statusMessage }}</p>
    <ol v-if="path && path.status === 'found'" class="hop-list">
      <li v-for="(node, i) in path.nodes" :key="node.id" class="hop">
        <button
          type="button"
          class="hop-node"
          :class="{ selected: selectedId === node.id }"
          :title="node.displayName || node.technicalName || node.id"
          @click="$emit('select', node.id)"
        >
          <span class="hop-index">{{ i }}</span>
          <span class="truncate name">{{ label(node) }}</span>
          <span class="badge type" :class="'type-' + node.type">{{ node.type }}</span>
        </button>
        <div v-if="path.edges[i]" class="hop-edge" :class="'kind-' + path.edges[i].kind">
          <span class="kind-label">{{ path.edges[i].kind }}</span>
          <span>{{ path.edges[i].relation.rel }}</span>
          <span class="mono muted">{{ path.edges[i].relation.id }}</span>
          <button
            type="button"
            class="linkish"
            @click="$emit('evidence', path.edges[i].relation.id)"
          >
            证据
          </button>
        </div>
      </li>
    </ol>
    <div v-if="evidenceRelationId" class="evidence-block">
      <div class="panel-head nested">关系证据 {{ evidenceRelationId }}</div>
      <p v-if="evidenceLoading" class="hint">加载证据…</p>
      <ul v-else class="evidence-list">
        <li v-for="ev in evidenceItems" :key="ev.id" class="evidence-item">
          <div><span class="badge quiet">{{ ev.state }}</span> {{ ev.description }}</div>
          <label class="source-ref">
            sourceRef
            <input
              class="mono"
              type="text"
              readonly
              :value="ev.sourceRef"
              :title="ev.sourceRef"
            />
            <button type="button" class="ghost" @click="copy(ev.sourceRef)">复制</button>
          </label>
        </li>
        <li v-if="!evidenceLoading && !evidenceItems.length" class="empty-row">无证据条目</li>
      </ul>
    </div>
  </section>
</template>

<script>
import { objectLabel, pathStatusMessage } from '../url/viewState.js'

export default {
  name: 'PathView',
  props: {
    targetId: { type: String, default: '' },
    selectedId: { type: String, default: null },
    path: { type: Object, default: null },
    loading: { type: Boolean, default: false },
    evidenceRelationId: { type: String, default: null },
    evidenceItems: { type: Array, default: function () { return [] } },
    evidenceLoading: { type: Boolean, default: false }
  },
  computed: {
    statusMessage: function () {
      return pathStatusMessage(this.path)
    }
  },
  methods: {
    label: function (obj) {
      return objectLabel(obj, obj && obj.id)
    },
    copy: function (text) {
      var value = text == null ? '' : String(text)
      if (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(value).catch(function () {})
        return
      }
      if (typeof document === 'undefined') {
        return
      }
      var area = document.createElement('textarea')
      area.value = value
      area.setAttribute('readonly', 'readonly')
      document.body.appendChild(area)
      area.select()
      try {
        document.execCommand('copy')
      } catch (err) {
        /* ignore */
      }
      document.body.removeChild(area)
    }
  }
}
</script>
