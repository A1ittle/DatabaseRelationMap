<template>
  <div class="shell">
    <header class="header">
      <div class="brand">
        <h1>程序血缘地图</h1>
        <p class="kicker">嵌入式四视图 · 非独立产品壳</p>
      </div>
      <nav class="shell-nav" aria-label="嵌入页">
        <button
          type="button"
          :class="{ active: shellPage === 'workbench' }"
          @click="goWorkbench"
        >
          血缘工作台
        </button>
        <button type="button" :class="{ active: shellPage === 'import' }" @click="goImport">
          数据导入
        </button>
      </nav>
      <form v-if="shellPage === 'workbench'" class="search" @submit.prevent="runSearch">
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

    <exception-banner
      v-if="shellPage === 'workbench'"
      :banner="workbenchBanner"
      @research="onResearch"
      @go-import="goImport"
    />
    <exception-banner v-if="shellPage === 'workbench' && qualityBanner" :banner="qualityBanner" />
    <p v-if="notice" class="banner notice">{{ notice }}</p>

    <import-page
      v-if="shellPage === 'import'"
      :initial-run-id="importRunId"
      @run="onImportRun"
    />

    <template v-if="shellPage === 'workbench'">

    <section v-if="hits.length" class="hits">
      <button
        v-for="hit in hits"
        :key="hit.object.id + hit.action"
        type="button"
        class="hit"
        @click="pickHit(hit)"
      >
        <span class="name truncate" :title="hit.object.displayName || hit.object.technicalName">{{ hit.object.displayName || hit.object.technicalName }}</span>
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

    <view-tabs v-if="meta" v-model="mode" :disabled="!queryId" />
    <type-filter v-if="meta && (mode === 'overview' || mode === 'impact')" v-model="filterTypes" />

    <div class="workspace" :class="'mode-' + mode">
      <template v-if="mode === 'tree'">
        <div v-if="treeIsUnavailable" class="tree-panel">
          <div class="panel-head">下游主树</div>
          <p class="hint">{{ cycleHint }}</p>
        </div>
        <lineage-tree
          v-else
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
      </template>
      <overview-view
        v-else-if="mode === 'overview'"
        :clusters="overviewClusters"
        :page="overviewPage"
        :loading="overviewLoading"
        :selected-cluster-id="selectedClusterId"
        :members="clusterMembers"
        :members-page="membersPage"
        :members-loading="membersLoading"
        :selected-id="selectedId"
        @open-cluster="openCluster"
        @more-clusters="loadOverview(true)"
        @more-members="loadMembers(true)"
        @locate-member="locateMember"
      />
      <impact-view
        v-else-if="mode === 'impact'"
        :items="impactItems"
        :page="impactPage"
        :loading="impactLoading"
        :selected-id="selectedId"
        @select="selectFromList"
        @more="loadImpact(true)"
      />
      <path-view
        v-else-if="mode === 'path'"
        :target-id="targetId"
        :selected-id="selectedId"
        :path="pathResult"
        :loading="pathLoading"
        :evidence-relation-id="evidenceRelationId"
        :evidence-items="evidenceItems"
        :evidence-loading="evidenceLoading"
        @update-target="targetId = $event"
        @submit="runPath"
        @use-selected="useSelectedAsTarget"
        @select="selectFromList"
        @evidence="loadEvidence"
      />
      <detail-panel
        :detail="detail"
        :relations="relations"
        :loading="detailLoading"
        :can-change-root="canChangeRoot"
        @change-root="changeRoot"
      />
    </div>
    </template>
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
import {
  ALL_TYPES,
  cycleNotice,
  isAllTypes,
  parseViewState,
  serializeViewState,
  treeUnavailable,
  viewStateFromApp
} from './url/viewState.js'
import {
  bannerForError,
  coverageBanner,
  idleWorkbenchBanner,
  noHitsBanner,
  shouldDestroyQuery
} from './exceptions/queryExceptions.js'
import LineageTree from './components/LineageTree.vue'
import CrossPanel from './components/CrossPanel.vue'
import DetailPanel from './components/DetailPanel.vue'
import ViewTabs from './components/ViewTabs.vue'
import TypeFilter from './components/TypeFilter.vue'
import OverviewView from './components/OverviewView.vue'
import ImpactView from './components/ImpactView.vue'
import PathView from './components/PathView.vue'
import ImportPage from './components/ImportPage.vue'
import ExceptionBanner from './components/ExceptionBanner.vue'

var emptyIndex = indexProjection({ nodes: [], edges: [] })

function freshAbort() {
  if (typeof AbortController === 'undefined') {
    return { abort: function () {}, signal: undefined }
  }
  return new AbortController()
}

export default {
  name: 'App',
  components: {
    LineageTree,
    CrossPanel,
    DetailPanel,
    ViewTabs,
    TypeFilter,
    OverviewView,
    ImpactView,
    PathView,
    ImportPage,
    ExceptionBanner
  },
  data: function () {
    return {
      shellPage: 'workbench',
      importRunId: '',
      exception: null,
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
      overviewSeq: 0,
      impactSeq: 0,
      pathSeq: 0,
      membersSeq: 0,
      evidenceSeq: 0,
      projectInFlight: false,
      pins: [],
      abortCtl: freshAbort(),
      viewNonce: 0,
      mode: 'tree',
      filterTypes: ALL_TYPES.slice(),
      targetId: '',
      syncingUrl: false,
      restoringUrl: false,
      overviewClusters: [],
      overviewPage: null,
      overviewLoading: false,
      selectedClusterId: null,
      clusterMembers: [],
      membersPage: null,
      membersLoading: false,
      impactItems: [],
      impactPage: null,
      impactLoading: false,
      pathResult: null,
      pathLoading: false,
      evidenceRelationId: null,
      evidenceItems: [],
      evidenceLoading: false
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
    },
    treeIsUnavailable: function () {
      return !!(this.meta && treeUnavailable(this.meta.treeStatus))
    },
    cycleHint: function () {
      return cycleNotice(this.meta && this.meta.treeStatus) || '主树分类不可用。请使用影响清单或最短路径。'
    },
    workbenchBanner: function () {
      if (this.exception) {
        return this.exception
      }
      if (this.hits.length || this.searching || this.meta) {
        return null
      }
      return idleWorkbenchBanner()
    },
    qualityBanner: function () {
      return coverageBanner(this.meta)
    }
  },
  watch: {
    mode: function () {
      this.fetchActiveView()
      this.writeUrl()
    },
    filterTypes: {
      deep: true,
      handler: function () {
        if (this.mode === 'overview') {
          this.loadOverview(false)
        } else if (this.mode === 'impact') {
          this.loadImpact(false)
        }
        this.writeUrl()
      }
    },
    selectedId: function () {
      this.writeUrl()
    },
    targetId: function () {
      this.writeUrl()
    },
    seedId: function () {
      this.writeUrl()
    }
  },
  created: function () {
    this.restoreFromUrl()
  },
  mounted: function () {
    if (typeof window !== 'undefined') {
      this._onPopState = this.restoreFromUrl.bind(this)
      window.addEventListener('popstate', this._onPopState)
    }
  },
  beforeDestroy: function () {
    if (typeof window !== 'undefined' && this._onPopState) {
      window.removeEventListener('popstate', this._onPopState)
    }
  },
  methods: {
    abortSignal: function () {
      return this.abortCtl && this.abortCtl.signal
    },
    typeQuery: function () {
      if (isAllTypes(this.filterTypes)) {
        return undefined
      }
      return this.filterTypes.slice()
    },
    writeUrl: function () {
      if (this.syncingUrl || this.restoringUrl) {
        return
      }
      if (typeof window === 'undefined' || !window.history || !window.history.replaceState) {
        return
      }
      var qs
      if (this.shellPage === 'import') {
        var params = new URLSearchParams()
        params.set('page', 'import')
        if (this.importRunId) {
          params.set('runId', this.importRunId)
        }
        qs = params.toString()
      } else {
        qs = serializeViewState(viewStateFromApp(this))
      }
      var next = window.location.pathname + (qs ? '?' + qs : '') + (window.location.hash || '')
      var cur = window.location.pathname + window.location.search + (window.location.hash || '')
      if (next !== cur) {
        window.history.replaceState(null, '', next)
      }
    },
    restoreFromUrl: function () {
      if (typeof window === 'undefined') {
        return
      }
      var params = new URLSearchParams(window.location.search || '')
      if (params.get('page') === 'import') {
        this.shellPage = 'import'
        this.importRunId = params.get('runId') || ''
        this.syncingUrl = false
        this.restoringUrl = false
        return
      }
      this.shellPage = 'workbench'
      var state = parseViewState(window.location.search)
      this.syncingUrl = true
      this.mode = state.mode
      this.filterTypes = state.types.slice()
      this.targetId = state.targetId || ''
      var seed = state.seedId
      var selected = state.selectedId
      var snap = state.snapshotId
      if (seed && seed !== this.seedId) {
        this.createQuery(seed, snap, { restoreSelectedId: selected, restoringUrl: true })
        return
      }
      if (selected && selected !== this.selectedId) {
        this.selectFromList(selected)
      }
      this.fetchActiveView()
      var self = this
      this.$nextTick(function () {
        self.syncingUrl = false
        self.writeUrl()
      })
    },
    goWorkbench: function () {
      this.shellPage = 'workbench'
      this.writeUrl()
    },
    goImport: function () {
      this.shellPage = 'import'
      this.writeUrl()
    },
    onImportRun: function (runId) {
      this.importRunId = runId || ''
      this.writeUrl()
    },
    onResearch: function () {
      this.exception = null
      this.error = ''
      this.hits = []
      this.notice = '请重新搜索以建立新的查询上下文。'
    },
    fail: function (err, fallback) {
      if (isAbortError(err)) {
        return
      }
      var banner = bannerForError(err, { fallback: fallback || '请求失败' })
      this.exception = banner
      this.error = banner.message
      if (shouldDestroyQuery(banner.code)) {
        this.destroyQuery()
      }
    },
    destroyQuery: function () {
      this.cancelInFlight()
      this.meta = null
      this.stats = null
      this.seed = null
      this.projection = null
      this.index = emptyIndex
      this.candidateIds = []
      this.expanded = {}
      this.childPages = {}
      this.selectedId = null
      this.detail = null
      this.relations = []
      this.pins = []
      this.resetAuxViews()
      this.revision.reset()
      this.viewNonce += 1
    },
    fetchActiveView: function () {
      if (!this.queryId) {
        return
      }
      if (this.mode === 'overview') {
        this.loadOverview(false)
      } else if (this.mode === 'impact') {
        this.loadImpact(false)
      } else if (this.mode === 'path') {
        var t = (this.targetId || '').trim() || this.selectedId
        if (t) {
          if (!this.targetId) {
            this.targetId = t
          }
          this.runPath(t)
        }
      }
    },
    resetAuxViews: function () {
      this.overviewClusters = []
      this.overviewPage = null
      this.selectedClusterId = null
      this.clusterMembers = []
      this.membersPage = null
      this.impactItems = []
      this.impactPage = null
      this.pathResult = null
      this.evidenceRelationId = null
      this.evidenceItems = []
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
      this.overviewSeq += 1
      this.impactSeq += 1
      this.pathSeq += 1
      this.membersSeq += 1
      this.evidenceSeq += 1
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
      this.exception = null
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
            self.exception = noHitsBanner()
            self.notice = ''
          } else {
            self.exception = null
            self.notice = ''
          }
        })
        .catch(function (err) {
          if (seq !== self.searchSeq || isAbortError(err)) {
            return
          }
          self.hits = []
          self.fail(err, '搜索失败')
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
    createQuery: function (seedId, snapshotId, opts) {
      var self = this
      opts = opts || {}
      this.cancelInFlight()
      this.error = ''
      this.exception = null
      this.notice = ''
      this.resetAuxViews()
      this.revision.reset()
      var sentEpoch = this.revision.epoch()
      var restoringUrl = !!opts.restoringUrl
      var restoreSelectedId = opts.restoreSelectedId || null
      this.restoringUrl = restoringUrl
      lineageClient
        .createQuery(seedId, snapshotId || null, { signal: this.abortSignal() })
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
          var treeStatus = res.meta && res.meta.treeStatus
          var cyc = cycleNotice(treeStatus)
          if (cyc) {
            self.notice = cyc
            if (!restoringUrl && self.mode === 'tree') {
              self.mode = 'impact'
            }
          }
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
          }
          var selectId = restoreSelectedId || sid
          if (selectId && selectId !== sid) {
            self.ensureCandidateAndSelect(selectId, true)
          } else if (selectId) {
            self.selectNode(selectId)
          }
          self.fetchActiveView()
          self.restoringUrl = false
          self.syncingUrl = false
          self.writeUrl()
        })
        .catch(function (err) {
          if (isAbortError(err)) {
            return
          }
          self.fail(err, '创建查询失败')
        })
        .then(function () {
          self.restoringUrl = false
          self.syncingUrl = false
        })
    },
    changeRoot: function () {
      if (!this.canChangeRoot) {
        return
      }
      var snap = this.meta && this.meta.snapshotId
      this.createQuery(this.selectedId, snap)
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
        if (outcome.keepOld && input.error && !shouldDestroyQuery((input.error && input.error.code) || '')) {
          this.exception = bannerForError({
            code: input.error.code,
            status: input.error.status,
            message: outcome.error,
            body: input.error.body
          })
        } else if (input.error) {
          this.fail(input.error, outcome.error)
        } else {
          this.exception = bannerForError({ message: outcome.error })
        }
      } else if (!outcome.stale) {
        this.error = ''
        this.exception = null
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
          self.fail(err, '加载子对象失败')
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
          self.fail(err, '加载详情失败')
        })
        .then(function () {
          if (seq === self.detailSeq) {
            self.detailLoading = false
          }
        })
    },
    selectFromList: function (id) {
      this.selectNode(id)
    },
    locateMember: function (id) {
      this.ensureCandidateAndSelect(id, !this.index.nodesById[id])
    },
    openCluster: function (cluster) {
      if (!cluster || !cluster.id) {
        return
      }
      this.selectedClusterId = cluster.id
      this.clusterMembers = []
      this.membersPage = null
      this.loadMembers(false)
    },
    loadOverview: function (append) {
      var self = this
      if (!this.queryId) {
        return
      }
      if (!append) {
        this.overviewClusters = []
        this.overviewPage = null
        this.selectedClusterId = null
        this.clusterMembers = []
      }
      var extra = { limit: 50, signal: this.abortSignal() }
      if (append && this.overviewPage && this.overviewPage.nextCursor) {
        extra.cursor = this.overviewPage.nextCursor
      }
      var types = this.typeQuery()
      if (types) {
        extra.types = types
      }
      var seq = ++this.overviewSeq
      this.overviewLoading = true
      lineageClient
        .overview(this.queryId, extra)
        .then(function (res) {
          if (seq !== self.overviewSeq) {
            return
          }
          var items = (res && res.items) || []
          self.overviewClusters = append ? self.overviewClusters.concat(items) : items
          self.overviewPage = (res && res.page) || { nextCursor: null, hasMore: false, total: null }
        })
        .catch(function (err) {
          if (seq !== self.overviewSeq || isAbortError(err)) {
            return
          }
          self.fail(err, '加载总览失败')
        })
        .then(function () {
          if (seq === self.overviewSeq) {
            self.overviewLoading = false
          }
        })
    },
    loadMembers: function (append) {
      var self = this
      if (!this.queryId || !this.selectedClusterId) {
        return
      }
      var extra = { limit: 50, signal: this.abortSignal() }
      if (append && this.membersPage && this.membersPage.nextCursor) {
        extra.cursor = this.membersPage.nextCursor
      }
      var seq = ++this.membersSeq
      this.membersLoading = true
      lineageClient
        .clusterMembers(this.queryId, this.selectedClusterId, extra)
        .then(function (res) {
          if (seq !== self.membersSeq) {
            return
          }
          var items = (res && res.items) || []
          self.clusterMembers = append ? self.clusterMembers.concat(items) : items
          self.membersPage = (res && res.page) || { nextCursor: null, hasMore: false, total: null }
        })
        .catch(function (err) {
          if (seq !== self.membersSeq || isAbortError(err)) {
            return
          }
          self.fail(err, '加载簇成员失败')
        })
        .then(function () {
          if (seq === self.membersSeq) {
            self.membersLoading = false
          }
        })
    },
    loadImpact: function (append) {
      var self = this
      if (!this.queryId) {
        return
      }
      if (!append) {
        this.impactItems = []
        this.impactPage = null
      }
      var extra = { limit: 50, signal: this.abortSignal() }
      if (append && this.impactPage && this.impactPage.nextCursor) {
        extra.cursor = this.impactPage.nextCursor
      }
      var types = this.typeQuery()
      if (types) {
        extra.types = types
      }
      var seq = ++this.impactSeq
      this.impactLoading = true
      lineageClient
        .impact(this.queryId, extra)
        .then(function (res) {
          if (seq !== self.impactSeq) {
            return
          }
          var items = (res && res.items) || []
          self.impactItems = append ? self.impactItems.concat(items) : items
          self.impactPage = (res && res.page) || { nextCursor: null, hasMore: false, total: null }
        })
        .catch(function (err) {
          if (seq !== self.impactSeq || isAbortError(err)) {
            return
          }
          self.fail(err, '加载影响清单失败')
        })
        .then(function () {
          if (seq === self.impactSeq) {
            self.impactLoading = false
          }
        })
    },
    useSelectedAsTarget: function () {
      if (!this.selectedId) {
        return
      }
      this.targetId = this.selectedId
      this.runPath(this.selectedId)
    },
    runPath: function (targetId) {
      var self = this
      var tid = (targetId || this.targetId || '').trim()
      if (!this.queryId || !tid) {
        return
      }
      this.targetId = tid
      var seq = ++this.pathSeq
      this.pathLoading = true
      this.pathResult = null
      this.evidenceRelationId = null
      this.evidenceItems = []
      lineageClient
        .path(this.queryId, tid, { signal: this.abortSignal() })
        .then(function (res) {
          if (seq !== self.pathSeq) {
            return
          }
          self.pathResult = res
        })
        .catch(function (err) {
          if (seq !== self.pathSeq || isAbortError(err)) {
            return
          }
          self.pathResult = null
          self.fail(err, '加载路径失败')
        })
        .then(function () {
          if (seq === self.pathSeq) {
            self.pathLoading = false
          }
        })
    },
    loadEvidence: function (rid) {
      var self = this
      if (!this.queryId || !rid) {
        return
      }
      this.evidenceRelationId = rid
      this.evidenceItems = []
      var seq = ++this.evidenceSeq
      this.evidenceLoading = true
      lineageClient
        .evidence(this.queryId, rid, { signal: this.abortSignal() })
        .then(function (res) {
          if (seq !== self.evidenceSeq) {
            return
          }
          self.evidenceItems = (res && res.items) || []
        })
        .catch(function (err) {
          if (seq !== self.evidenceSeq || isAbortError(err)) {
            return
          }
          self.evidenceItems = []
          self.fail(err, '加载证据失败')
        })
        .then(function () {
          if (seq === self.evidenceSeq) {
            self.evidenceLoading = false
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

.shell-nav {
  display: flex;
  gap: 6px;
  align-items: center;
}

.shell-nav button.active {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
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

.banner.empty {
  background: #f6f8fa;
  color: var(--muted);
}

.exception-banner .banner-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
}

.ex-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.75rem;
}

.issue-list {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  font-size: 0.8rem;
}

.issue-list li {
  margin: 4px 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: baseline;
}

.banner-actions {
  margin-top: 6px;
}

.import-page {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.72);
  padding: 8px 10px 12px;
}

.import-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.import-json {
  width: 100%;
  min-height: 180px;
  box-sizing: border-box;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 8px;
  background: #f6f8fa;
  resize: vertical;
}

.import-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.file-label {
  font-size: 0.8rem;
  color: var(--muted);
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.import-run {
  margin: 10px 0;
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

.workspace.mode-overview,
.workspace.mode-impact,
.workspace.mode-path {
  grid-template-columns: minmax(0, 1fr) minmax(220px, 320px);
}

@media (max-width: 960px) {
  .workspace,
  .workspace.mode-overview,
  .workspace.mode-impact,
  .workspace.mode-path {
    grid-template-columns: 1fr;
  }
}

.view-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0 0 8px;
}

.view-tab {
  height: 32px;
  padding: 0 12px;
  font-size: 0.85rem;
}

.view-tab.active {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
}

.type-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  font-size: 0.8rem;
  margin: 0 0 10px;
}

.filter-label {
  color: var(--muted);
}

.type-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.truncate {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 220px;
  vertical-align: bottom;
}

.tree-row .name,
.hit .name,
.impact-row .name,
.member-btn .name,
.hop-node .name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
  display: inline-block;
}

.overview-panel,
.impact-panel,
.path-panel {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.72);
  padding: 8px 10px 12px;
  min-width: 0;
}

.cluster-list,
.member-list,
.impact-list,
.hop-list,
.evidence-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.cluster-row {
  margin: 6px 0;
  border-bottom: 1px solid var(--border);
  padding-bottom: 6px;
}

.cluster-btn,
.member-btn,
.impact-row,
.hop-node {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  height: auto;
  text-align: left;
  width: 100%;
  background: transparent;
  border: none;
  padding: 4px 2px;
}

.cluster-btn:hover,
.member-btn:hover,
.impact-row:hover,
.hop-node:hover {
  background: rgba(31, 35, 40, 0.06);
}

.member-btn.selected,
.impact-row.selected,
.hop-node.selected {
  background: rgba(26, 127, 55, 0.12);
}

.cluster-row .count {
  font-variant-numeric: tabular-nums;
  font-size: 0.8rem;
}

.path-form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: flex-end;
  margin-bottom: 8px;
}

.path-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 0.75rem;
  color: var(--muted);
  flex: 1;
  min-width: 160px;
}

.path-label input {
  height: 32px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 0 8px;
}

.hop {
  margin: 8px 0;
}

.hop-index {
  font-size: 0.7rem;
  color: var(--muted);
  min-width: 1.2em;
}

.hop-edge {
  margin: 4px 0 4px 20px;
  font-size: 0.78rem;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.source-ref {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-top: 4px;
  font-size: 0.75rem;
  color: var(--muted);
}

.source-ref input {
  flex: 1;
  min-width: 120px;
  height: 28px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 0 6px;
  background: #f6f8fa;
}

.evidence-item {
  margin: 8px 0;
  font-size: 0.8rem;
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
