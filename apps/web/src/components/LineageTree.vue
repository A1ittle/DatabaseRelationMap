<template>
  <div class="tree-panel">
    <div class="panel-head">下游主树</div>
    <p v-if="!roots.length" class="hint">搜索并选择一个表入口后，这里显示默认一层下游。</p>
    <ul v-else class="tree-list root">
      <tree-node
        v-for="id in roots"
        :key="id"
        :node-id="id"
        :index="index"
        :expanded="expanded"
        :selected-id="selectedId"
        :child-pages="childPages"
        @select="$emit('select', $event)"
        @toggle="$emit('toggle', $event)"
        @more="$emit('more', $event)"
      />
    </ul>
  </div>
</template>

<script>
import TreeNode from './TreeNode.vue'

export default {
  name: 'LineageTree',
  components: { TreeNode },
  props: {
    index: { type: Object, required: true },
    roots: { type: Array, required: true },
    expanded: { type: Object, required: true },
    selectedId: { type: String, default: null },
    childPages: { type: Object, required: true }
  }
}
</script>
