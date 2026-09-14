#!/usr/bin/env node
/**
 * P4 visual freeze capture (fixture seed only).
 *
 * Assumes API at 127.0.0.1:8080 and web at WEB_BASE (dev or preview).
 * Imports + publishes fixtures/v1/import.json when search has no root hit.
 * Does not invent lineage. Exits 2 if API/web is down.
 *
 * Usage (from apps/web):
 *   npm run capture:p4-visual
 *   WEB_BASE=http://127.0.0.1:4173 node scripts/capture-p4-visual.mjs
 */
import { mkdir, copyFile, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const WEB_ROOT = path.resolve(__dirname, '..')
const REPO_ROOT = path.resolve(WEB_ROOT, '../..')
const OUT_DIR = path.resolve(REPO_ROOT, 'evidence/implementation/p4-visual')
const BEST_DIR = path.join(OUT_DIR, 'best')

const API_BASE = (process.env.API_BASE || 'http://127.0.0.1:8080').replace(/\/$/, '')
const WEB_BASE = (process.env.WEB_BASE || 'http://127.0.0.1:5173').replace(/\/$/, '')
const CSRF = process.env.VITE_CSRF_TOKEN || 'dev'
const CHROME = process.env.CHROME_PATH || '/usr/bin/google-chrome'
const COPY_BEST = process.env.COPY_BEST !== '0'

const MODES = ['tree', 'overview', 'impact', 'path']
const VIEWPORTS = [
  { name: '1280', width: 1280, height: 800 },
  { name: '390', width: 390, height: 844 }
]

const SEED = 'root'
const SELECTED = 'view-a'
const PATH_TARGET = 'java-j'
const FIXTURE = path.join(REPO_ROOT, 'fixtures/v1/import.json')

function log(msg) {
  process.stdout.write(msg + '\n')
}

async function httpJson(method, url, body) {
  const headers = { Accept: 'application/json' }
  let payload
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    headers['X-CSRF-Token'] = CSRF
    payload = typeof body === 'string' ? body : JSON.stringify(body)
  }
  const res = await fetch(url, { method, headers, body: payload })
  const text = await res.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    json = { raw: text }
  }
  return { ok: res.ok, status: res.status, json }
}

async function ensureApiAndFixture() {
  let health
  try {
    health = await httpJson('GET', API_BASE + '/api/health')
  } catch (err) {
    log('BLOCKED: API is not reachable at ' + API_BASE)
    log('Start PostgreSQL from deploy/ and the Java 8 API (docs/RUNBOOK.md).')
    throw Object.assign(new Error('api-down'), { code: 2 })
  }
  if (!health.ok) {
    log('BLOCKED: /api/health status ' + health.status)
    throw Object.assign(new Error('api-unhealthy'), { code: 2 })
  }
  log('API health ok')

  let search
  try {
    search = await httpJson('GET', API_BASE + '/api/lineage/search?q=root')
  } catch (err) {
    log('BLOCKED: search failed: ' + err.message)
    throw Object.assign(new Error('search-down'), { code: 2 })
  }
  if (search.status === 404) {
    log('BLOCKED: /api/lineage/search is 404 — rebuild/restart the current API jar.')
    throw Object.assign(new Error('api-stale'), { code: 2 })
  }
  const hits = (search.json && search.json.items) || []
  const hasRoot = hits.some(function (h) {
    return h.object && h.object.id === SEED
  })
  if (hasRoot) {
    log('fixture already published (search hit root)')
    return
  }
  log('importing fixtures/v1/import.json')
  if (!existsSync(FIXTURE)) {
    log('BLOCKED: missing ' + FIXTURE)
    throw Object.assign(new Error('no-fixture'), { code: 2 })
  }
  const { readFile } = await import('node:fs/promises')
  const batch = await readFile(FIXTURE, 'utf8')
  const imported = await httpJson('POST', API_BASE + '/api/imports', batch)
  if (!imported.ok) {
    log('BLOCKED: import failed ' + imported.status + ' ' + JSON.stringify(imported.json))
    throw Object.assign(new Error('import-failed'), { code: 2 })
  }
  const runId = imported.json && imported.json.runId
  log('import runId=' + runId + ' status=' + (imported.json && imported.json.status))
  let run = imported.json
  for (let i = 0; i < 20 && run && run.status === 'validating'; i++) {
    await new Promise(function (r) {
      setTimeout(r, 200)
    })
    const poll = await httpJson('GET', API_BASE + '/api/imports/' + runId)
    run = poll.json
  }
  if (!run || run.status !== 'ready') {
    log('BLOCKED: import not ready: ' + JSON.stringify(run))
    throw Object.assign(new Error('import-not-ready'), { code: 2 })
  }
  const published = await httpJson('POST', API_BASE + '/api/imports/' + runId + '/publish', {
    expectedActiveSnapshotId: null
  })
  if (!published.ok) {
    log('BLOCKED: publish failed ' + published.status + ' ' + JSON.stringify(published.json))
    throw Object.assign(new Error('publish-failed'), { code: 2 })
  }
  log('published snapshotId=' + (published.json && published.json.snapshotId))
}

async function ensureWeb() {
  try {
    const res = await fetch(WEB_BASE + '/')
    if (!res.ok) {
      throw new Error('status ' + res.status)
    }
  } catch (err) {
    log('BLOCKED: web is not reachable at ' + WEB_BASE + ' (' + err.message + ')')
    log('Start: cd apps/web && npm run dev   # or npm run preview after build')
    throw Object.assign(new Error('web-down'), { code: 2 })
  }
  log('WEB ok ' + WEB_BASE)
}

function pageUrl(mode) {
  const params = new URLSearchParams()
  params.set('mode', mode)
  params.set('seedId', SEED)
  params.set('selectedId', SELECTED)
  if (mode === 'path') {
    params.set('targetId', PATH_TARGET)
  }
  return WEB_BASE + '/?' + params.toString()
}

async function waitSettled(page, mode) {
  await page.waitForSelector('.shell', { timeout: 20000 })
  if (mode === 'tree') {
    await page.waitForSelector('.tree-row', { timeout: 25000 })
    const closed = page.locator('.twist[aria-expanded="false"]')
    if ((await closed.count()) > 0) {
      await closed.first().click()
      await page.waitForTimeout(600)
    }
  } else if (mode === 'overview') {
    await page.waitForFunction(
      function () {
        return (
          document.querySelector('.cluster-row') ||
          (document.querySelector('.overview-panel') &&
            /簇|没有簇|加载/.test(document.querySelector('.overview-panel').textContent || ''))
        )
      },
      { timeout: 25000 }
    )
  } else if (mode === 'impact') {
    await page.waitForFunction(
      function () {
        return (
          document.querySelector('.impact-row') ||
          (document.querySelector('.impact-panel') &&
            /没有可达|加载影响/.test(document.querySelector('.impact-panel').textContent || ''))
        )
      },
      { timeout: 25000 }
    )
  } else if (mode === 'path') {
    await page.waitForFunction(
      function () {
        const panel = document.querySelector('.path-panel')
        if (!panel) return false
        return !!(
          document.querySelector('.hop') ||
          document.querySelector('.banner.notice') ||
          /加载路径|没有/.test(panel.textContent || '')
        )
      },
      { timeout: 25000 }
    )
  }
  await page.waitForSelector('.detail-panel', { timeout: 10000 }).catch(function () {})
  await page.waitForTimeout(400)
}

async function capture() {
  await mkdir(OUT_DIR, { recursive: true })
  const launchOpts = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--font-render-hinting=none']
  }
  if (existsSync(CHROME)) {
    launchOpts.executablePath = CHROME
  } else {
    launchOpts.channel = 'chrome'
  }
  log('launch chrome ' + (launchOpts.executablePath || launchOpts.channel))
  const browser = await chromium.launch(launchOpts)
  const written = []
  try {
    for (const vp of VIEWPORTS) {
      const context = await browser.newContext({
        viewport: { width: vp.width, height: vp.height },
        deviceScaleFactor: 1,
        reducedMotion: 'reduce',
        colorScheme: 'light',
        locale: 'zh-CN'
      })
      const page = await context.newPage()
      await page.addInitScript(function () {
        try {
          document.documentElement.style.setProperty('--ease', 'linear')
        } catch (e) {
          /* ignore */
        }
      })
      for (const mode of MODES) {
        const url = pageUrl(mode)
        log('goto ' + vp.name + ' ' + mode + ' ' + url)
        await page.goto(url, { waitUntil: 'networkidle', timeout: 30000 })
        await waitSettled(page, mode)
        const file = mode + '-' + vp.name + '.png'
        const dest = path.join(OUT_DIR, file)
        await page.screenshot({
          path: dest,
          fullPage: false,
          animations: 'disabled',
          scale: 'css'
        })
        log('wrote ' + dest)
        written.push(file)
      }
      await context.close()
    }
  } finally {
    await browser.close()
  }
  if (COPY_BEST) {
    await mkdir(BEST_DIR, { recursive: true })
    for (const file of written) {
      await copyFile(path.join(OUT_DIR, file), path.join(BEST_DIR, file))
    }
    log('copied ' + written.length + ' files to best/ (历史最佳)')
  }
  const manifest = {
    seedId: SEED,
    selectedId: SELECTED,
    targetId: PATH_TARGET,
    viewports: VIEWPORTS,
    modes: MODES,
    files: written,
    api: API_BASE,
    web: WEB_BASE,
    note: 'fixtures/v1 only; do not scale PNGs before Diff'
  }
  await writeFile(path.join(OUT_DIR, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n')
  return written
}

async function main() {
  log('API_BASE=' + API_BASE)
  log('WEB_BASE=' + WEB_BASE)
  await ensureApiAndFixture()
  await ensureWeb()
  const files = await capture()
  log('captured ' + files.join(', '))
}

main().catch(function (err) {
  log(String(err && err.stack ? err.stack : err))
  process.exit(err && err.code === 2 ? 2 : 1)
})
