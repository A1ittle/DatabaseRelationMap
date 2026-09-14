<template>
  <div class="shell">
    <header class="header">
      <div class="brand">
        <h1>程序血缘地图</h1>
        <p class="kicker">嵌入式下游树 · 非独立产品壳</p>
      </div>
      <form class="search" @submit.prevent="runSearch">
        <input
          v-model="q"
          type="search"
          name="q"
          maxlength="200"
          placeholder="搜索表 / 技术名 / 显示名"
          aria-label="搜索血缘对象"
        />
        <button type="submit" :disabled="searching || !q.trim()">搜索</button>
      </form>
    </header>

    <p v-if="error" class="banner error" role="alert">{{ error }}</p>
    <p v-if="notice" class="banner notice">{{ notice }}</p>

    <section v-if="hits.length" class="hits">
      <button
        v-for="hit in hits"
        :key="hit.object.id + hit.action"
        type="button"
        class="hit"
        @click="pickHit(hit)"
      >
        <span class="name">{{ hit.object.displayName || hit.object.technicalName }}</span>
        <span class="badge type" :class="'type-' + hit.object.type">{{ hit.object.type }}</span>
        <span class="badge quiet">{{ hit.action }}</span>
        <span class="mono muted">{{ hit.object.id }}</span>
      </button>
    </section>

    <section v-if="meta" class="meta-bar">
      <span>queryId <code>{{ meta.queryId }}</code></span>
      <span>snapshot <code>{{ meta.snapshotId }}</code></span>
      <span>rev {{ revision.current() }}</span>
      <span>tree {{ meta.treeStatus }}</span>
      <span>coverage {{ meta.coverage }}</span>
      <span v-if="drawn">绘制节点 {{ drawn.nodes }} / 边 {{ drawn.edges }}（主树 {{ drawn.tree }} · 跨支 {{ drawn.cross }}<template v-if="drawn.unclassified"> · 未分类 {{ drawn.unclassified }}</template>）</span>
      <span v-if="stats">服务端 下游 {{ stats.downstream }} · 跨支 {{ stats.crossEdges }} · Java {{ stats.javaTerminals }}</span>
      <button type="button" class="ghost" :disabled="!canChangeRoot" @click="changeRoot">换根</button>
    </section>

    <div class="workspace">
      <lineage-tree
        :key="'tree-' + viewNonce"
        :index="index"
        :roots="roots"
        :expanded="expanded"
        :selected-id="selectedId"
        :child-pages="childPages"
        @select="selectNode"
        @toggle="toggleExpand"
        @more="loadMoreChildren"
      />
      <cross-panel
        v-if="meta"
        :edges="crossEdges"
        :selected-id="selectedId"
        :nodes-by-id="index.nodesById"
        @locate="locateCross"
      />
      <detail-panel
        :detail="detail"
        :relations="relations"
        :loading="detailLoading"
        :can-change-root="canChangeRoot"
        @change-root="changeRoot"
      />
    </div>
  </div>
</template>

<script>
import { lineageClient } from './api/lineageClient.js'
import { createRevisionGuard } from './graph/revision.js'
import { listNonTreeEdges, otherEndpoint } from './graph/crossList.js'
import { drawnStats } from './graph/drawnStats.js'
import {
  isAbortError,
  resolveProjectionOutcome
} from './graph/projectionSession.js'
import {
  indexProjection,
  findRoots,
  mergeCandidateIds
} from './graph/treeFromProjection.js'
import LineageTree from './components/LineageTree.vue'
import CrossPanel from './components/CrossPanel.vue'
import DetailPanel from './components/DetailPanel.vue'

var emptyIndex = indexProjection({ nodes: [], edges: [] })

function freshAbort() {
  if (typeof AbortController === 'undefined') {
    return { abort: function () {}, signal: undefined }
  }
  return new AbortController()
}

export default {
  name: 'App',
  components: { LineageTree, CrossPanel, DetailPanel },
  data: function () {
    return {
      q: '',
      hits: [],
      searching: false,
      error: '',
      notice: '',
      meta: null,
      stats: null,
      seed: null,
      projection: null,
      index: emptyIndex,
      candidateIds: [],
      expanded: {},
      childPages: {},
      selectedId: null,
      detail: null,
      relations: [],
      detailLoading: false,
      revision: createRevisionGuard(),
      searchSeq: 0,
      detailSeq: 0,
      projectInFlight: false,
      pins: [],
      abortCtl: freshAbort(),
      viewNonce: 0
    }
  },
  computed: {
    queryId: function () {
      return this.meta && this.meta.queryId
    },
    seedId: function () {
      return this.seed && this.seed.id
    },
    roots: function () {
      return findRoots(this.index, this.seedId)
    },
    drawn: function () {
      return drawnStats(this.projection)
    },
    crossEdges: function () {
      return listNonTreeEdges(this.projection)
    },
    canChangeRoot: function () {
      if (!this.selectedId || this.selectedId === this.seedId) {
        return false
      }
      if (this.detail && this.detail.canSetAsRoot === false) {
        return false
      }
      return true
    }
  },
  methods: {
    abortSignal: function () {
      return this.abortCtl && this.abortCtl.signal
    },
    cancelInFlight: function () {
      if (this.abortCtl && this.abortCtl.abort) {
        try {
          this.abortCtl.abort()
        } catch (err) {
          /* ignore */
        }
      }
      this.abortCtl = freshAbort()
      this.searchSeq += 1
      this.detailSeq += 1
      this.projectInFlight = false
    },
    runSearch: function () {
      var self = this
      var q = (this.q || '').trim()
      if (!q) {
        return
      }
      this.searching = true
      this.error = ''
      var seq = ++this.searchSeq
      var extra = { signal: this.abortSignal() }
      if (this.queryId) {
        extra.queryId = this.queryId
      }
      lineageClient
        .search(q, extra)
        .then(function (res) {
          if (seq !== self.searchSeq) {
            return
          }
          self.hits = (res && res.items) || []
          if (!self.hits.length) {
            self.notice = '没有命中。若尚未发布快照，请先按 README 导入 fixtures/v1。'
          } else {
            self.notice = ''
          }
        })
        .catch(function (err) {
          if (seq !== self.searchSeq || isAbortError(err)) {
            return
          }
          self.hits = []
          self.error = (err && err.message) || '搜索失败'
        })
        .then(function () {
          if (seq === self.searchSeq) {
            self.searching = false
          }
        })
    },
    pickHit: function (hit) {
      if (!hit || !hit.object) {
        return
      }
      if (hit.action === 'reveal' && this.queryId) {
        this.ensureCandidateAndSelect(hit.object.id)
        return
      }
      this.createQuery(hit.object.id)
    },
    createQuery: function (seedId) {
      var self = this
      this.cancelInFlight()
      this.error = ''
      this.notice = ''
      this.revision.reset()
      var sentEpoch = this.revision.epoch()
      lineageClient
        .createQuery(seedId, null, { signal: this.abortSignal() })
        .then(function (res) {
          if (sentEpoch !== self.revision.epoch()) {
            return
          }
          self.meta = res.meta
          self.stats = res.stats
          self.seed = res.seed
          self.childPages = {}
          self.expanded = {}
          self.pins = []
          self.candidateIds = []
          self.viewNonce += 1
          var initialRev = 0
          if (res.projection && res.projection.clientRevision != null) {
            initialRev = res.projection.clientRevision
          }
          self.commitProjectionResult({
            projection: res.projection,
            sentRevision: initialRev,
            sentEpoch: sentEpoch,
            previousCandidates: [],
            attemptedCandidates: []
          })
          var sid = res.seed && res.seed.id
          if (sid) {
            self.$set(self.expanded, sid, true)
            self.primeChildren(sid)
            self.selectNode(sid)
          }
        })
        .catch(function (err) {
          if (isAbortError(err)) {
            return
          }
          self.error = (err && err.message) || '创建查询失败'
        })
    },
    changeRoot: function () {
      if (!this.canChangeRoot) {
        return
      }
      this.createQuery(this.selectedId)
    },
    commitProjectionResult: function (input) {
      var outcome = resolveProjectionOutcome({
        canvas: { projection: this.projection, index: this.index },
        guard: this.revision,
        sentRevision: input.sentRevision,
        sentEpoch: input.sentEpoch,
        projection: input.projection,
        error: input.error,
        previousCandidates: input.previousCandidates,
        attemptedCandidates: input.attemptedCandidates,
        seedId: this.seedId
      })
      if (outcome.aborted) {
        return false
      }
      if (outcome.applied && outcome.canvas) {
        this.projection = outcome.canvas.projection
        this.index = outcome.canvas.index
      }
      if (!outcome.stale) {
        this.candidateIds = outcome.candidateIds
      }
      if (outcome.error) {
        this.error = outcome.error
      } else if (!outcome.stale) {
        this.error = ''
      }
      if (outcome.notice) {
        this.notice = outcome.notice
      }
      return outcome.applied
    },
    replaceProjection: function (opts) {
      var self = this
      opts = opts || {}
      if (!this.queryId) {
        return Promise.resolve(false)
      }
      var previousCandidates =
        opts.previousCandidates || this.candidateIds.slice()
      var sent = this.revision.next()
      var sentEpoch = this.revision.epoch()
      var selected = opts.selectedId || this.selectedId || this.seedId
      var body = {
        candidateIds: this.candidateIds.slice(),
        selectedId: selected,
        types: lineageClient.ALL_TYPES.slice(),
        revealSelectedPath: !!opts.revealSelectedPath,
        clientRevision: sent
      }
      this.projectInFlight = true
      return lineageClient
        .projection(this.queryId, body, { signal: this.abortSignal() })
        .then(function (res) {
          return self.commitProjectionResult({
            projection: res,
            sentRevision: sent,
            sentEpoch: sentEpoch,
            previousCandidates: previousCandidates,
            attemptedCandidates: body.candidateIds
          })
        })
        .catch(function (err) {
          if (isAbortError(err)) {
            return false
          }
          return self.commitProjectionResult({
            error: err,
            sentRevision: sent,
            sentEpoch: sentEpoch,
            previousCandidates: previousCandidates,
            attemptedCandidates: body.candidateIds
          })
        })
        .then(function (applied) {
          self.projectInFlight = false
          return applied
        })
    },
    toggleExpand: function (nodeId) {
      if (this.expanded[nodeId]) {
        this.$delete(this.expanded, nodeId)
        return
      }
      this.$set(this.expanded, nodeId, true)
      this.fetchChildrenAndProject(nodeId, false)
    },
    loadMoreChildren: function (nodeId) {
      this.fetchChildrenAndProject(nodeId, true)
    },
    primeChildren: function (nodeId) {
      var self = this
      if (!this.queryId) {
        return
      }
      lineageClient
        .children(this.queryId, nodeId, { limit: 50, signal: this.abortSignal() })
        .then(function (page) {
          self.recordChildPage(nodeId, page, false)
        })
        .catch(function (err) {
          if (isAbortError(err)) {
            return
          }
          /* first-layer projection is enough if children fails */
        })
    },
    fetchChildrenAndProject: function (nodeId, useCursor) {
      var self = this
      if (!this.queryId) {
        return
      }
      var pageState = this.childPages[nodeId] || {}
      var extra = { limit: 50, signal: this.abortSignal() }
      if (useCursor && pageState.nextCursor) {
        extra.cursor = pageState.nextCursor
      }
      this.$set(
        this.childPages,
        nodeId,
        Object.assign({}, pageState, { loading: true })
      )
      lineageClient
        .children(this.queryId, nodeId, extra)
        .then(function (page) {
          var extraIds = ((page && page.items) || []).map(function (item) {
            return item.object && item.object.id
          })
          var previousCandidates = self.candidateIds.slice()
          self.candidateIds = mergeCandidateIds(self.candidateIds, extraIds, self.seedId)
          return self.replaceProjection({ previousCandidates: previousCandidates }).then(function (ok) {
            if (ok) {
              self.recordChildPage(nodeId, page, useCursor)
            } else {
              self.candidateIds = previousCandidates
              self.$set(
                self.childPages,
                nodeId,
                Object.assign({}, pageState, { loading: false })
              )
            }
          })
        })
        .catch(function (err) {
          self.$set(
            self.childPages,
            nodeId,
            Object.assign({}, self.childPages[nodeId] || {}, { loading: false })
          )
          if (isAbortError(err)) {
            return
          }
          self.error = (err && err.message) || '加载子对象失败'
        })
    },
    recordChildPage: function (nodeId, page, append) {
      var info = page && page.page ? page.page : {}
      var prev = this.childPages[nodeId] || {}
      this.$set(this.childPages, nodeId, {
        nextCursor: info.nextCursor || null,
        hasMore: !!info.hasMore,
        total: info.total,
        loading: false,
        loaded: true,
        append: append || prev.loaded
      })
    },
    ensureCandidateAndSelect: function (id, reveal) {
      var self = this
      if (this.candidateIds.indexOf(id) === -1 || reveal) {
        var previousCandidates = this.candidateIds.slice()
        var previousPins = this.pins.slice()
        this.pins = mergeCandidateIds(this.pins, [id], this.seedId)
        this.candidateIds = mergeCandidateIds(this.candidateIds, this.pins, this.seedId)
        var needReveal = !!reveal || !this.index.nodesById[id]
        this.replaceProjection({
          revealSelectedPath: needReveal,
          selectedId: id,
          previousCandidates: previousCandidates
        }).then(function (ok) {
          if (ok) {
            self.selectNode(id)
          } else {
            self.pins = previousPins
            self.candidateIds = previousCandidates
          }
        })
        return
      }
      this.selectNode(id)
    },
    locateCross: function (edge) {
      var other = otherEndpoint(edge, this.selectedId)
      if (!other) {
        return
      }
      this.ensureCandidateAndSelect(other, !this.index.nodesById[other])
    },
    selectNode: function (id) {
      var self = this
      this.selectedId = id
      if (!this.queryId || !id) {
        return
      }
      this.detailLoading = true
      var seq = ++this.detailSeq
      var signal = this.abortSignal()
      Promise.all([
        lineageClient.getNode(this.queryId, id, { signal: signal }),
        lineageClient.relations(this.queryId, id, 'all', { limit: 50, signal: signal })
      ])
        .then(function (pair) {
          if (seq !== self.detailSeq) {
            return
          }
          self.detail = pair[0]
          self.relations = (pair[1] && pair[1].items) || []
        })
        .catch(function (err) {
          if (seq !== self.detailSeq || isAbortError(err)) {
            return
          }
          self.detail = null
          self.relations = []
          self.error = (err && err.message) || '加载详情失败'
        })
        .then(function () {
          if (seq === self.detailSeq) {
            self.detailLoading = false
          }
        })
    }
  }
}
</script>

<style>
:root {
  --fg: #1f2328;
  --muted: #57606a;
  --border: #d0d7de;
  --accent: #1a7f37;
  --tree: #1a7f37;
  --cross: #9a6700;
  --unclassified: #8250df;
  --table: #324457;
  --view: #004d51;
  --proc: #633f00;
  --java: #37395c;
  --danger: #9e2225;
  --radius: 6px;
}

html,
body {
  margin: 0;
  padding: 0;
  background: transparent;
}

.shell {
  font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
  color: var(--fg);
  padding: 12px 16px 16px;
  background: rgba(255, 255, 255, 0.82);
  min-height: 100vh;
  box-sizing: border-box;
}

.header {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
  align-items: flex-end;
  margin-bottom: 10px;
}

h1 {
  font-size: 1.2rem;
  font-weight: 600;
  margin: 0;
}

.kicker {
  margin: 2px 0 0;
  color: var(--muted);
  font-size: 0.8rem;
}

.search {
  display: flex;
  gap: 8px;
  flex: 1;
  min-width: 220px;
}

.search input {
  flex: 1;
  height: 36px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 0 10px;
  background: rgba(255, 255, 255, 0.9);
}

button {
  font: inherit;
  cursor: pointer;
  height: 36px;
  padding: 0 12px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: #fff;
}

button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.search button[type='submit'] {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
}

.banner {
  margin: 0 0 8px;
  padding: 8px 10px;
  border-radius: var(--radius);
  font-size: 0.85rem;
}

.banner.error {
  background: #fff1f0;
  color: var(--danger);
}

.banner.notice {
  background: #fff8c5;
  color: #633f00;
}

.hits {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.hit {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  height: auto;
  padding: 6px 10px;
  text-align: left;
}

.meta-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  font-size: 0.78rem;
  color: var(--muted);
  margin-bottom: 10px;
}

.meta-bar code,
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.78rem;
}

.muted {
  color: var(--muted);
}

.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(200px, 260px) minmax(220px, 320px);
  gap: 12px;
  min-height: 280px;
}

@media (max-width: 960px) {
  .workspace {
    grid-template-columns: 1fr;
  }
}

.tree-panel,
.detail-panel,
.cross-panel {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.72);
  padding: 8px 10px 12px;
  min-width: 0;
}

.panel-head {
  font-size: 0.75rem;
  letter-spacing: 0.04em;
  color: var(--muted);
  margin-bottom: 6px;
}

.panel-head.nested {
  margin-top: 10px;
}

.hint {
  margin: 0;
  color: var(--muted);
  font-size: 0.85rem;
}

.tree-list {
  list-style: none;
  margin: 0;
  padding: 0 0 0 12px;
}

.tree-list.root {
  padding-left: 0;
}

.tree-item {
  margin: 2px 0;
}

.tree-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 3px 4px;
  border-radius: 4px;
  cursor: pointer;
}

.tree-row:hover {
  background: rgba(31, 35, 40, 0.06);
}

.tree-row.selected,
.tree-item.selected > .tree-row {
  background: rgba(26, 127, 55, 0.12);
}

.twist {
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  background: transparent;
  line-height: 1;
}

.twist.spacer {
  display: inline-block;
  width: 22px;
}

.badge {
  font-size: 0.7rem;
  padding: 1px 6px;
  border-radius: 999px;
  border: 1px solid var(--border);
}

.badge.quiet {
  color: var(--muted);
}

.badge.type-table {
  color: var(--table);
}
.badge.type-view {
  color: var(--view);
}
.badge.type-procedure {
  color: var(--proc);
}
.badge.type-java {
  color: var(--java);
}

.kind-tree {
  border-left: 3px solid var(--tree);
  padding-left: 6px;
}

.kind-cross {
  border-left: 3px dashed var(--cross);
  padding-left: 6px;
  color: var(--cross);
}

.kind-unclassified {
  border-left: 3px dotted var(--unclassified);
  padding-left: 6px;
  color: var(--unclassified);
}

.badge.kind-cross {
  border-style: dashed;
  color: var(--cross);
  padding-left: 6px;
}

.cross-row,
.rel-list li,
.more-row,
.empty-row {
  font-size: 0.78rem;
  margin: 2px 0;
  list-style: none;
}

.empty-row {
  color: var(--muted);
}

.kind-label {
  text-transform: uppercase;
  font-size: 0.65rem;
  margin-right: 6px;
}

.linkish {
  height: auto;
  border: none;
  background: none;
  color: var(--accent);
  padding: 0;
}

.detail-body dl {
  display: grid;
  grid-template-columns: 88px 1fr;
  gap: 4px 8px;
  margin: 0;
  font-size: 0.85rem;
}

.detail-body dt {
  color: var(--muted);
}

.detail-body dd {
  margin: 0;
  word-break: break-all;
}

.rel-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.ghost,
.change-root {
  height: 28px;
  font-size: 0.8rem;
}

.detail-body .change-root {
  margin: 10px 0 4px;
}

.cross-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.cross-item {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin: 6px 0;
  font-size: 0.78rem;
}

.cross-item.incident {
  background: rgba(154, 103, 0, 0.08);
  border-radius: 4px;
  padding: 4px;
}

.locate {
  height: 24px;
  padding: 0 8px;
  font-size: 0.75rem;
}
</style>
