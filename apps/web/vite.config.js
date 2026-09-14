const { defineConfig } = require('vite')
const { createVuePlugin } = require('vite-plugin-vue2')

// Relative base so the empty shell can load when hosted inside an iframe.
module.exports = defineConfig({
  plugins: [createVuePlugin()],
  base: './',
  server: {
    port: 5173,
    host: '127.0.0.1'
  },
  build: {
    outDir: 'dist'
  }
})
