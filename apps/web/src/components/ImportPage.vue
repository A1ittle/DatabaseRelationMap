<template>
  <section class="import-page" aria-label="数据导入">
    <div class="panel-head">导入 / 校验 / 发布</div>
    <p class="hint">
      粘贴或上传 ImportBatch JSON。文档样例路径
      <code>fixtures/v1/import.json</code>
      （不是生产血缘）。CSRF <code>X-CSRF-Token</code> 与工作台相同；可选
      <code>X-Embed-Groups</code>。
    </p>

    <exception-banner :banner="exception" />

    <form class="import-form" @submit.prevent="submitImport">
      <label class="path-label">
        ImportBatch JSON
        <textarea
          v-model="rawJson"
          rows="14"
          spellcheck="false"
          class="mono import-json"
          aria-label="ImportBatch JSON"
          placeholder='{"batchKey":"...","scopeId":"..."}'
        />
      </label>
      <div class="import-actions">
        <label class="file-label">
          上传 JSON 文件
          <input type="file" accept="application/json,.json" @change="onFile" />
        </label>
        <button type="submit" :disabled="busy || !rawJson.trim()">提交导入</button>
        <button type="button" class="ghost" :disabled="busy || !runId" @click="refresh(false)">刷新详情</button>
      </div>
    </form>

    <section v-if="run" class="import-run tree-panel">
      <div class="panel-head">批次 {{ run.runId }}</div>
      <dl class="detail-body">
        <dt>runId</dt>
        <dd class="mono">{{ run.runId }}</dd>
        <dt>status</dt>
        <dd>{{ run.status }}</dd>
        <dt>errors</dt>
        <dd>{{ run.errorCount }}</dd>
        <dt>warnings</dt>
        <dd>{{ run.warningCount }}</dd>
        <dt>snapshotId</dt>
        <dd class="mono">{{ run.snapshotId || 'null' }}</dd>
      </dl>
      <p v-if="polling" class="hint">正在轮询校验状态…</p>

      <form v-if="canPublish" class="path-form" @submit.prevent="submitPublish">
        <label class="path-label">
          expectedActiveSnapshotId（空 = null，首次发布）
          <input v-model="expectedActive" type="text" maxlength="200" aria-label="expectedActiveSnapshotId" />
        </label>
        <button type="submit" :disabled="busy">发布快照</button>
      </form>
      <p v-else-if="run.status === 'failed'" class="hint">失败批次不能发布；上一活动快照保持不变。</p>
      <p v-else-if="run.status === 'published'" class="hint">已发布。</p>
    </section>

    <section v-if="publishResult" class="tree-panel">
      <div class="panel-head">发布结果</div>
      <p>
        snapshotId <code>{{ publishResult.snapshotId }}</code>
        · publishedAt {{ publishResult.publishedAt }}
      </p>
    </section>

    <section v-if="run" class="tree-panel">
      <div class="panel-head">校验问题</div>
      <p v-if="!issues.length" class="hint">本页无问题条目。</p>
      <ul class="issue-list">
        <li v-for="(issue, i) in issues" :key="issueKey(issue, i)">
          <span class="badge" :class="issue.severity === 'warning' ? 'quiet' : ''">{{ issue.severity }}</span>
          <code>{{ issue.code }}</code>
          <span class="mono muted">{{ issue.path }}</span>
          {{ issue.message }}
        </li>
      </ul>
      <button
        v-if="issuesPage && issuesPage.hasMore"
        type="button"
        class="linkish"
        :disabled="busy"
        @click="refresh(true)"
      >
        {{ busy ? '加载中…' : '加载更多问题' }}
      </button>
    </section>
  </section>
</template>

<script>
import { importClient } from '../api/importClient.js'
import { bannerForError } from '../exceptions/queryExceptions.js'
import ExceptionBanner from './ExceptionBanner.vue'

function isPendingStatus(status) {
  return status === 'received' || status === 'validating'
}

export default {
  name: 'ImportPage',
  components: { ExceptionBanner },
  props: {
    initialRunId: { type: String, default: '' }
  },
  data: function () {
    return {
      rawJson: '',
      busy: false,
      exception: null,
      run: null,
      issues: [],
      issuesPage: null,
      publishResult: null,
      expectedActive: '',
      polling: false,
      pollTimer: null
    }
  },
  computed: {
    runId: function () {
      return (this.run && this.run.runId) || ''
    },
    canPublish: function () {
      return !!(this.run && this.run.status === 'ready' && this.run.snapshotId)
    }
  },
  watch: {
    initialRunId: function (id) {
      if (id && id !== this.runId) {
        this.loadRun(id, false)
      }
    }
  },
  created: function () {
    if (this.initialRunId) {
      this.loadRun(this.initialRunId, false)
    }
  },
  beforeDestroy: function () {
    this.stopPoll()
  },
  methods: {
    issueKey: function (issue, i) {
      return [issue && issue.code, issue && issue.path, i].join(':')
    },
    fail: function (err, fallback) {
      this.exception = bannerForError(err, { fallback: fallback || '导入失败' })
    },
    onFile: function (ev) {
      var self = this
      var input = ev && ev.target
      var file = input && input.files && input.files[0]
      if (!file) {
        return
      }
      var reader = new FileReader()
      reader.onload = function () {
        self.rawJson = String(reader.result || '')
      }
      reader.onerror = function () {
        self.exception = bannerForError({ code: 'IMPORT_INVALID', message: '无法读取文件' })
      }
      reader.readAsText(file)
    },
    submitImport: function () {
      var self = this
      var raw = (this.rawJson || '').trim()
      if (!raw) {
        return
      }
      var batch
      try {
        batch = JSON.parse(raw)
      } catch (err) {
        this.exception = bannerForError({
          code: 'IMPORT_INVALID',
          status: 400,
          message: 'request body is not valid JSON'
        })
        return
      }
      if (!batch || typeof batch !== 'object' || Array.isArray(batch)) {
        this.exception = bannerForError({
          code: 'IMPORT_INVALID',
          status: 400,
          message: 'ImportBatch must be a JSON object'
        })
        return
      }
      this.busy = true
      this.exception = null
      this.publishResult = null
      this.issues = []
      this.issuesPage = null
      importClient
        .createImport(batch)
        .then(function (res) {
          self.run = res
          self.$emit('run', res && res.runId)
          if (res && res.runId) {
            return self.loadRun(res.runId, false)
          }
        })
        .catch(function (err) {
          self.fail(err, '导入失败')
        })
        .then(function () {
          self.busy = false
        })
    },
    loadRun: function (runId, append) {
      var self = this
      if (!runId) {
        return Promise.resolve()
      }
      var extra = { limit: 50 }
      if (append && this.issuesPage && this.issuesPage.nextCursor) {
        extra.cursor = this.issuesPage.nextCursor
      }
      this.busy = true
      return importClient
        .getImport(runId, extra)
        .then(function (detail) {
          self.run = (detail && detail.run) || self.run
          var items = (detail && detail.issues) || []
          self.issues = append ? self.issues.concat(items) : items
          self.issuesPage = (detail && detail.page) || { nextCursor: null, hasMore: false, total: null }
          self.$emit('run', self.runId)
          self.syncPoll()
        })
        .catch(function (err) {
          self.fail(err, '加载导入详情失败')
        })
        .then(function () {
          self.busy = false
        })
    },
    refresh: function (append) {
      if (!this.runId) {
        return
      }
      this.loadRun(this.runId, append)
    },
    syncPoll: function () {
      if (this.run && isPendingStatus(this.run.status)) {
        this.startPoll()
      } else {
        this.stopPoll()
      }
    },
    startPoll: function () {
      var self = this
      if (this.pollTimer) {
        return
      }
      this.polling = true
      this.pollTimer = setInterval(function () {
        if (!self.runId) {
          return
        }
        self.loadRun(self.runId, false)
      }, 2000)
    },
    stopPoll: function () {
      this.polling = false
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },
    submitPublish: function () {
      var self = this
      if (!this.canPublish) {
        return
      }
      this.busy = true
      this.exception = null
      var expected = (this.expectedActive || '').trim()
      importClient
        .publish(this.runId, expected || null)
        .then(function (res) {
          self.publishResult = res
          if (self.run) {
            self.run = Object.assign({}, self.run, {
              status: 'published',
              snapshotId: (res && res.snapshotId) || self.run.snapshotId
            })
          }
        })
        .catch(function (err) {
          self.fail(err, '发布失败')
        })
        .then(function () {
          self.busy = false
        })
    }
  }
}
</script>
