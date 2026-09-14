<template>
  <div class="shell">
    <h1>程序血缘地图</h1>
    <p>嵌入式空壳（P0）。本页可放入 iframe，不是独立产品壳；血缘图、导入与登录尚未接入。</p>
  </div>
</template>

<script>
export default {
  name: 'App'
}
</script>

<style>
html,
body {
  margin: 0;
  padding: 0;
  background: transparent;
}

.shell {
  font-family: system-ui, sans-serif;
  padding: 16px;
  color: #1f2328;
}

h1 {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0 0 8px;
}

p {
  margin: 0;
  color: #57606a;
  font-size: 0.9rem;
  line-height: 1.5;
}
</style>
