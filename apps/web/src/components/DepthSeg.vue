<template>
  <div class="seg depth-seg" role="group" aria-label="展开深度">
    <button
      v-for="seg in segments"
      :key="seg.value"
      type="button"
      :data-depth="seg.value"
      :aria-pressed="value === seg.value ? 'true' : 'false'"
      :disabled="disabled"
      @click="$emit('input', seg.value)"
    >
      {{ seg.label }}
    </button>
  </div>
</template>

<script>
import { DEFAULT_DEPTH, DEPTH_SEGMENTS } from '../graph/depthFilter.js'

export default {
  name: 'DepthSeg',
  props: {
    value: { type: Number, default: DEFAULT_DEPTH },
    disabled: { type: Boolean, default: false }
  },
  data: function () {
    return { segments: DEPTH_SEGMENTS }
  }
}
</script>
