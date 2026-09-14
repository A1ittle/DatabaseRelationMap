const { defineConfig } = require('vite')
const { createVuePlugin } = require('vite-plugin-vue2')

const apiTarget = process.env.VITE_API_BASE || 'http://127.0.0.1:8080'

// Relative base so the shell can load when hosted inside an iframe.
module.exports = defineConfig({
  plugins: [createVuePlugin()],
  base: './',
  server: {
    port: 5173,
    host: '127.0.0.1',
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist'
  },
  test: {
    environment: 'node'
  }
})
