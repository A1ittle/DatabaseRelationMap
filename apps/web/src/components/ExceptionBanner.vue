<template>
  <div
    v-if="banner"
    class="banner exception-banner"
    :class="banner.level || 'error'"
    :role="banner.level === 'error' ? 'alert' : undefined"
  >
    <div class="banner-row">
      <code v-if="banner.code && banner.code !== 'EMPTY' && banner.code !== 'NO_HITS'" class="ex-code">{{
        banner.code
      }}</code>
      <span>{{ banner.message }}</span>
    </div>
    <ul v-if="banner.issues && banner.issues.length" class="issue-list">
      <li v-for="(issue, i) in banner.issues" :key="issueKey(issue, i)">
        <span class="badge" :class="issue.severity === 'warning' ? 'quiet' : ''">{{ issue.severity || 'error' }}</span>
        <code>{{ issue.code }}</code>
        <span class="mono muted">{{ issue.path }}</span>
        {{ issue.message }}
      </li>
    </ul>
    <div v-if="banner.action" class="banner-actions">
      <button v-if="banner.action === 'research'" type="button" class="ghost" @click="$emit('research')">
        重新搜索
      </button>
      <button v-if="banner.action === 'import'" type="button" class="ghost" @click="$emit('go-import')">
        前往数据导入
      </button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ExceptionBanner',
  props: {
    banner: { type: Object, default: null }
  },
  methods: {
    issueKey: function (issue, i) {
      if (!issue) {
        return String(i)
      }
      return [issue.code, issue.path, i].join(':')
    }
  }
}
</script>
