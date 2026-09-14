<template>
  <div
    class="view-tabs"
    role="tablist"
    aria-label="血缘视图"
    @keydown="onKeydown"
  >
    <button
      v-for="tab in tabs"
      :key="tab.id"
      ref="tabButtons"
      type="button"
      class="view-tab"
      role="tab"
      :id="'view-tab-' + tab.id"
      :aria-selected="value === tab.id ? 'true' : 'false'"
      :tabindex="value === tab.id ? 0 : -1"
      :class="{ active: value === tab.id }"
      :disabled="disabled"
      @click="$emit('input', tab.id)"
    >
      {{ tab.label }}
    </button>
  </div>
</template>

<script>
import { VIEW_MODES, adjacentMode } from '../url/viewState.js'

var LABELS = {
  tree: '树/图',
  overview: '总览',
  impact: '影响清单',
  path: '最短路径'
}

export default {
  name: 'ViewTabs',
  props: {
    value: { type: String, default: 'tree' },
    disabled: { type: Boolean, default: false }
  },
  computed: {
    tabs: function () {
      return VIEW_MODES.map(function (id) {
        return { id: id, label: LABELS[id] || id }
      })
    }
  },
  methods: {
    focusIndex: function (mode) {
      var i = VIEW_MODES.indexOf(mode)
      var btns = this.$refs.tabButtons
      if (i >= 0 && btns && btns[i] && btns[i].focus) {
        btns[i].focus()
      }
    },
    onKeydown: function (event) {
      var key = event.key
      var next = this.value
      if (key === 'ArrowRight' || key === 'ArrowDown') {
        next = adjacentMode(this.value, 1)
      } else if (key === 'ArrowLeft' || key === 'ArrowUp') {
        next = adjacentMode(this.value, -1)
      } else if (key === 'Home') {
        next = VIEW_MODES[0]
      } else if (key === 'End') {
        next = VIEW_MODES[VIEW_MODES.length - 1]
      } else {
        return
      }
      event.preventDefault()
      if (next !== this.value) {
        this.$emit('input', next)
      }
      var self = this
      this.$nextTick(function () {
        self.focusIndex(next)
      })
    }
  }
}
</script>
