<template>
  <div class="tree-panel tree-canvas-panel">
    <div class="panel-head">下游主树</div>
    <p class="tree-legend">
      <span><i class="swatch tree"></i>实线 · 主依赖</span>
      <span><i class="swatch cross"></i>虚线 · 跨支关联</span>
      <span>Java 只作为叶子</span>
    </p>
    <p v-if="!columns.length" class="hint">搜索并选择一个表入口后，这里显示默认一层下游。</p>
    <div v-else ref="stage" class="tree-stage stage">
      <div ref="inner" class="stage-inner">
        <svg
          class="edge-layer"
          aria-hidden="true"
          :viewBox="'0 0 ' + edgeW + ' ' + edgeH"
          :width="edgeW"
          :height="edgeH"
        >
          <defs>
            <marker
              id="arr-tree"
              markerWidth="6"
              markerHeight="6"
              refX="5"
              refY="3"
              orient="auto"
            >
              <path d="M0 0L6 3L0 6Z" fill="currentColor" />
            </marker>
          </defs>
          <path
            v-for="edge in edgeDraws"
            :key="edge.id"
            :class="edge.cls"
            :d="edge.d"
            marker-end="url(#arr-tree)"
          />
        </svg>
        <div class="hops" :style="hopsStyle">
          <section
            v-for="col in columns"
            :key="col.rank"
            class="col"
            :class="{ 'leaf-col': col.leaf }"
            :data-rank="col.rank"
          >
            <div class="col-head">
              <span class="k">{{ col.label }}</span>
              <span class="n">{{ col.nodes.length }}</span>
            </div>
            <template v-for="node in col.shown">
              <node-card
                :key="node.object.id"
                :node="node"
                :seed-id="seedId"
                :selected-id="selectedId"
                :expanded="!!expanded[node.object.id]"
                :dim="isDim(node.object.id)"
                @select="$emit('select', $event)"
                @toggle="$emit('toggle', $event)"
                @change-root="$emit('change-root', $event)"
              />
              <button
                v-if="showMore(node)"
                :key="node.object.id + '-more'"
                type="button"
                class="more"
                :disabled="pageOf(node).loading"
                @click.stop="$emit('more', node.object.id)"
              >
                {{ pageOf(node).loading ? '加载中…' : '加载更多子对象' }}
              </button>
            </template>
            <button
              v-if="col.rest"
              type="button"
              class="more"
              :aria-expanded="'false'"
              @click="openColumn(col.rank)"
            >
              本层还有 {{ col.rest }} 个对象
            </button>
            <button
              v-else-if="openCols[col.rank] && col.nodes.length > colCap"
              type="button"
              class="more"
              aria-expanded="true"
              @click="closeColumn(col.rank)"
            >
              收起本层
            </button>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import NodeCard from './NodeCard.vue'
import { COL_CAP, groupColumns, sliceColumn } from '../graph/columns.js'
import { DEFAULT_DEPTH, filterProjectionByDepth } from '../graph/depthFilter.js'
import { edgeClassNames, edgePath } from '../graph/edgePath.js'
import { crossEdgesFor } from '../graph/treeFromProjection.js'
import { edgeOnPath, highlightIdSet, treePathIds } from '../graph/treePath.js'

export default {
  name: 'LineageTree',
  components: { NodeCard },
  props: {
    index: { type: Object, required: true },
    roots: { type: Array, required: true },
    expanded: { type: Object, required: true },
    selectedId: { type: String, default: null },
    childPages: { type: Object, required: true },
    seedId: { type: String, default: null },
    projection: { type: Object, default: null },
    depth: { type: Number, default: DEFAULT_DEPTH }
  },
  data: function () {
    return {
      openCols: {},
      edgeDraws: [],
      edgeW: 0,
      edgeH: 0,
      colCap: COL_CAP
    }
  },
  computed: {
    visibleProjection: function () {
      return filterProjectionByDepth(this.projection, this.depth, {
        parentOf: this.index && this.index.parentOf,
        expanded: this.expanded
      })
    },
    columns: function () {
      var raw = groupColumns(this.visibleProjection)
      var self = this
      return raw.map(function (col) {
        var sliced = sliceColumn(col.nodes, !!self.openCols[col.rank], COL_CAP)
        return {
          rank: col.rank,
          label: col.label,
          leaf: col.leaf,
          nodes: col.nodes,
          shown: sliced.shown,
          rest: sliced.rest
        }
      })
    },
    hopsStyle: function () {
      var n = Math.max(this.columns.length, 1)
      return {
        gridTemplateColumns: 'repeat(' + n + ', 260px)'
      }
    },
    highlightSet: function () {
      var incident = []
      if (this.selectedId && this.index) {
        incident = crossEdgesFor(this.index, this.selectedId)
      }
      return highlightIdSet(
        this.index && this.index.parentOf,
        this.selectedId,
        this.seedId,
        incident
      )
    },
    pathSet: function () {
      var ids
      var set = {}
      var i
      if (!this.selectedId) {
        return null
      }
      ids = treePathIds(this.index && this.index.parentOf, this.selectedId, this.seedId)
      for (i = 0; i < ids.length; i++) {
        set[ids[i]] = true
      }
      return set
    },
    visibleIdSet: function () {
      var set = {}
      var c
      var i
      var id
      for (c = 0; c < this.columns.length; c++) {
        for (i = 0; i < this.columns[c].shown.length; i++) {
          id = this.columns[c].shown[i].object && this.columns[c].shown[i].object.id
          if (id) {
            set[id] = true
          }
        }
      }
      return set
    }
  },
  watch: {
    columns: function () {
      this.scheduleEdges()
    },
    depth: function () {
      this.scheduleEdges()
    },
    selectedId: function () {
      this.scheduleEdges()
    },
    expanded: {
      deep: true,
      handler: function () {
        this.scheduleEdges()
      }
    }
  },
  mounted: function () {
    this._onResize = this.scheduleEdges.bind(this)
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', this._onResize)
    }
    this.scheduleEdges()
  },
  beforeDestroy: function () {
    if (typeof window !== 'undefined' && this._onResize) {
      window.removeEventListener('resize', this._onResize)
    }
    if (this._edgeTimer) {
      clearTimeout(this._edgeTimer)
    }
  },
  methods: {
    isDim: function (id) {
      var hl = this.highlightSet
      if (!hl) {
        return false
      }
      if (this.seedId && id === this.seedId) {
        return false
      }
      return !hl[id]
    },
    pageOf: function (node) {
      var id = node && node.object && node.object.id
      return (id && this.childPages[id]) || {}
    },
    showMore: function (node) {
      var id = node && node.object && node.object.id
      var page = this.pageOf(node)
      return !!(id && this.expanded[id] && page.hasMore)
    },
    openColumn: function (rank) {
      this.$set(this.openCols, rank, true)
      this.scheduleEdges()
    },
    closeColumn: function (rank) {
      this.$delete(this.openCols, rank)
      this.scheduleEdges()
    },
    scheduleEdges: function () {
      var self = this
      if (this._edgeTimer) {
        clearTimeout(this._edgeTimer)
      }
      this._edgeTimer = setTimeout(function () {
        self.$nextTick(function () {
          self.drawEdges()
        })
      }, 16)
    },
    drawEdges: function () {
      var inner = this.$refs.inner
      var vis = this.visibleIdSet
      var edges = (this.visibleProjection && this.visibleProjection.edges) || []
      var cr
      var sw
      var sh
      var draws = []
      var i
      var e
      var rel
      var kind
      var a
      var b
      var ar
      var br
      var x1
      var y1
      var x2
      var y2
      var on
      var dim
      var hl = this.highlightSet
      var pathSet = this.pathSet
      var root = this.$el
      if (!inner || !root) {
        this.edgeDraws = []
        return
      }
      cr = inner.getBoundingClientRect()
      sw = inner.scrollWidth || inner.offsetWidth || 0
      sh = Math.max(inner.scrollHeight, inner.offsetHeight, 0)
      this.edgeW = sw
      this.edgeH = sh
      for (i = 0; i < edges.length; i++) {
        e = edges[i]
        rel = e && e.relation
        if (!rel || !rel.id) {
          continue
        }
        if (!vis[rel.source] || !vis[rel.target]) {
          continue
        }
        a = root.querySelector('[data-id="' + rel.source + '"]')
        b = root.querySelector('[data-id="' + rel.target + '"]')
        if (!a || !b) {
          continue
        }
        ar = a.getBoundingClientRect()
        br = b.getBoundingClientRect()
        x1 = ar.right - cr.left
        y1 = ar.top + ar.height / 2 - cr.top
        x2 = br.left - cr.left
        y2 = br.top + br.height / 2 - cr.top
        kind = e.kind || 'tree'
        on = edgeOnPath(kind, rel.source, rel.target, this.selectedId, pathSet)
        dim = !!(hl && !on)
        draws.push({
          id: rel.id,
          cls: edgeClassNames(kind, rel.rel, on, dim),
          d: edgePath(x1, y1, x2, y2, kind === 'cross' || kind === 'unclassified')
        })
      }
      this.edgeDraws = draws
    }
  }
}
</script>

<style>
.tree-panel.tree-canvas-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 360px;
  padding: 8px 0 0;
  overflow: hidden;
  background: var(--bg);
}

.tree-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin: 0 10px 6px;
  font-size: 12px;
  color: var(--muted);
  letter-spacing: 0.02em;
}

.tree-legend .swatch {
  display: inline-block;
  width: 22px;
  height: 0;
  border-top: 2px solid #8b969e;
  border-top-color: oklch(62% 0.01 240);
  vertical-align: middle;
  margin-right: 6px;
}

.tree-legend .swatch.cross {
  border-top-style: dashed;
  border-top-color: var(--cross);
}

.tree-stage {
  position: relative;
  flex: 1;
  min-height: 280px;
  min-width: 0;
  overflow: auto;
  background:
    linear-gradient(var(--border) 1px, transparent 1px) 0 0 / 28px 28px,
    linear-gradient(90deg, var(--border) 1px, transparent 1px) 0 0 / 28px 28px,
    var(--bg);
}

.stage-inner {
  position: relative;
  min-height: 100%;
  min-width: max-content;
}

.tree-canvas-panel .edge-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: visible;
  color: #8b969e;
  z-index: 0;
}

.tree-canvas-panel .edge {
  fill: none;
  stroke: #8b969e;
  stroke: oklch(72% 0.01 240);
  stroke-width: 1.25;
}

.tree-canvas-panel .edge.calls {
  stroke-dasharray: 5 4;
}

.tree-canvas-panel .edge.derives {
  stroke-dasharray: 2 3;
}

.tree-canvas-panel .edge.cross {
  stroke: var(--cross);
  stroke-width: 1.35;
  stroke-dasharray: 4 3.5;
}

.tree-canvas-panel .edge.dim {
  opacity: 0.22;
}

.tree-canvas-panel .edge.on {
  stroke: var(--accent);
  stroke-width: 2;
  opacity: 1;
}

.tree-canvas-panel .panel-head {
  padding: 0 10px;
}

.tree-canvas-panel .hops {
  display: grid;
  gap: 0 12px;
  padding: 20px 24px 56px;
  position: relative;
  z-index: 1;
  min-width: max-content;
  justify-items: start;
}

.tree-canvas-panel .col {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 260px;
  min-width: 240px;
  max-width: 280px;
}

.tree-canvas-panel .col-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 0 4px 8px;
  border-bottom: 1px solid var(--border);
}

.tree-canvas-panel .col-head .k {
  font-size: 12px;
  letter-spacing: 0.02em;
  color: var(--muted);
}

.tree-canvas-panel .col-head .n {
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  font-weight: 510;
}

.tree-canvas-panel .col.leaf-col {
  background: var(--java-col);
  border-radius: 10px;
  padding: 8px;
}

.tree-canvas-panel button.node {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  width: 100%;
  height: auto;
  min-height: 52px;
  text-align: left;
  padding: 10px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  color: var(--fg);
  box-sizing: border-box;
}

.tree-canvas-panel button.node:hover:not(:disabled) {
  background: var(--hover);
  border-color: #9aa7b0;
  color: var(--fg);
}

.tree-canvas-panel button.node.is-seed {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(41, 146, 54, 0.18);
  min-height: 72px;
}

.tree-canvas-panel button.node.is-sel {
  border-color: var(--fg);
  box-shadow: inset 0 0 0 1px var(--fg);
}

.tree-canvas-panel button.node.is-seed.is-sel {
  box-shadow:
    0 0 0 3px rgba(41, 146, 54, 0.18),
    inset 0 0 0 1px var(--fg);
}

.tree-canvas-panel button.node.is-dim {
  background: var(--bg);
}

.tree-canvas-panel button.node.is-dim .node-name,
.tree-canvas-panel button.node.is-dim .node-title {
  color: var(--muted);
}

.tree-canvas-panel button.node:focus-visible {
  outline: 2px solid var(--fg);
  outline-offset: 2px;
}

.node-ico {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 4px;
  color: var(--surface);
  flex: none;
}

.node-ico svg {
  width: 15px;
  height: 15px;
}

.t-table {
  background: var(--table);
}
.t-view {
  background: var(--view);
}
.t-procedure {
  background: var(--proc);
}
.t-java {
  background: var(--java);
}

.node-copy {
  min-width: 0;
}

.node-name {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.45;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  max-width: none;
  white-space: normal;
  text-overflow: unset;
}

.node-title {
  display: block;
  font-size: 12px;
  color: var(--muted);
  line-height: 1.45;
}

.tree-canvas-panel .badge {
  font-size: 12px;
  letter-spacing: 0.02em;
  color: var(--muted);
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 2px 8px;
  background: var(--bg);
  white-space: nowrap;
}

.tree-canvas-panel .badge.cross-b {
  color: var(--fg);
  border-color: #8ea0b0;
  border-style: dashed;
}

.tree-canvas-panel .now {
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  font-family: var(--font-mono);
  color: var(--accent);
  font-weight: 600;
}

.tree-canvas-panel button.more {
  width: 100%;
  justify-content: center;
  height: 44px;
  border-style: dashed;
  border-radius: var(--radius);
  background: var(--surface);
}

.tree-canvas-panel button.more[aria-expanded='true'] {
  background: var(--bg);
  color: var(--fg);
  border-style: solid;
}

.tree-canvas-panel .hint {
  margin: 0 10px 10px;
}
</style>
