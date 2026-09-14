<template>
  <div class="type-filter" role="group" aria-label="对象类型筛选">
    <span class="filter-label">类型</span>
    <label v-for="t in allTypes" :key="t" class="type-chip">
      <input
        type="checkbox"
        :checked="isOn(t)"
        @change="toggle(t)"
      />
      {{ t }}
    </label>
  </div>
</template>

<script>
import { ALL_TYPES, normalizeTypes } from '../url/viewState.js'

export default {
  name: 'TypeFilter',
  props: {
    value: { type: Array, default: function () { return ALL_TYPES.slice() } }
  },
  data: function () {
    return { allTypes: ALL_TYPES.slice() }
  },
  methods: {
    isOn: function (t) {
      return (this.value || []).indexOf(t) !== -1
    },
    toggle: function (t) {
      var next = (this.value || []).slice()
      var i = next.indexOf(t)
      if (i === -1) {
        next.push(t)
      } else {
        next.splice(i, 1)
      }
      this.$emit('input', normalizeTypes(next))
    }
  }
}
</script>
