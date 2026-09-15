# lineage-web

P4 Vue 2 embedded shell for 程序血缘地图: search → create query → four views
(tree / overview / impact / path) + URL restore + **数据导入** (validate/publish)
and distinct exception banners + visual freeze (fixture screenshots). P3 tree / detail / cross / budget keep-old
still work. No React Flow (ADR 0001). Graph layout (`src/graph/*`) is separate
from API/domain. Lists and paths always come from their APIs, never from the
on-screen tree. Fixtures are **not** real lineage.

```bash
npm install
npm run generate:api  # spec/v1/openapi.json → src/generated/openapi.d.ts
npm run dev           # http://127.0.0.1:5173
npm run test          # vitest (URL, importClient, exception banners, budget)
npm run build
npm run capture:p4-visual  # 8 fixture screenshots → evidence/implementation/p4-visual/
```

Iframe-friendly (`html, body` stay transparent; `.shell` uses prototype `--bg`).
Host mount (iframe / same-origin `/api` proxy, CSRF, `X-Embed-Groups`) is in
[docs/IMPLEMENTATION_ACCEPTANCE.md](../../docs/IMPLEMENTATION_ACCEPTANCE.md)
and [docs/RUNBOOK.md](../../docs/RUNBOOK.md). Graph canvas libraries are
intentionally not included. Visual freeze protocol:
[evidence/implementation/p4-visual/PROTOCOL.md](../../evidence/implementation/p4-visual/PROTOCOL.md).
CSS tokens live in `src/tokens.css` (from `reference/offline-style-update/lineage-map.css`).

### Config

| Variable | Default | Meaning |
|---|---|---|
| `VITE_API_BASE` | `http://127.0.0.1:8080` | Lineage API origin. Empty string = same-origin (uses Vite `/api` proxy in `npm run dev`). |
| `VITE_CSRF_TOKEN` | `dev` | Sent as `X-CSRF-Token` on POSTs (open-mode API accepts any non-empty value). |
| `VITE_EMBED_GROUPS` | *(omit)* | Optional `X-Embed-Groups` (host-trusted groups; deny wins on the API). |

Copy `.env.example` to `.env.local` to override. Do not commit secrets.

### URL contract (search params)

The shell encodes workspace state in **query search params** (not the hash).
`queryId` is a session credential and is **never** written to the URL. On load
the page recreates the query from `seedId` (optional `snapshotId`) and
**re-fetches** the active view from the API — it does not restore a cached
screen graph. Opaque pagination cursors are not persisted (reload starts at
the first page).

| Param | Values | Restore |
|---|---|---|
| `mode` | `tree` (default, omitted) · `overview` · `impact` · `path` | Selects the tab and loads that view's API |
| `seedId` | object id | `POST /api/lineage/queries` with this seed |
| `snapshotId` | snapshot id | Passed on create; omit = latest published |
| `selectedId` | object id | After the query exists, `GET .../nodes/{id}` (and may `POST projection` with `revealSelectedPath` if the object is not on the default canvas) |
| `targetId` | object id | Path view: `GET .../path?targetId=` |
| `types` | comma list `table,view,procedure,java` | Overview / impact filter; omit = all four types |
| `page` | `import` (omit = 血缘工作台) | Switches to the import/validate/publish page |
| `runId` | ingest run id | Only with `page=import`; reloads `GET /api/imports/{runId}` |

Example:

```text
/?mode=path&seedId=root&snapshotId=snap-1&selectedId=view-a&targetId=java-j&types=table,java
/?page=import&runId=…
```

Keyboard: view tabs are a `tablist` (Tab moves focus; ←/→ or Home/End change
view). Long names use CSS ellipsis plus a `title` tooltip. Below 960px the
workspace stacks panels.

### Manual demo (>3 layers)

Needs a running API with `fixtures/v1/import.json` imported and published
(see [docs/RUNBOOK.md](../../docs/RUNBOOK.md)). Then:

1. `npm run dev` and open http://127.0.0.1:5173
2. Search `root` → pick the hit (`recenter`) → query meta + layer-1 tree
   (`root` → `view-a`, `proc-s`).
3. Select a node → detail panel (name/type/id + relation summary).
4. Expand `view-a` → `proc-b` → `table-c` (optional `java-j`). That is
   layoutRank 0..3+ on the main tree. Each expand calls `GET .../children`
   (cursor pagination / 加载更多) then `POST .../projection` with
   `candidateIds` and a new `clientRevision`. The UI **replaces** nodes/edges
   atomically and **drops** responses whose revision is older than current.
5. Tree edges use class `kind-tree`; cross / unclassified edges `kind-cross` /
   `kind-unclassified`. The **跨支 / 未分类** panel lists the same set from
   projection kinds (independent of expand order). **定位** pins the other
   endpoint into candidates and refreshes projection with `revealSelectedPath`
   when that node is not already drawn.
6. Meta bar **绘制节点 / 边** is the actual rendered projection count (tree vs
   cross), not only server `stats.downstream` (kept as **服务端 下游**).
7. **换根**: select a non-seed object (详情 `canSetAsRoot`) and click 换根.
   This cancels in-flight requests, clears expand pages and locate pins, creates
   a new query with that object as seed, and remounts the tree (fit/reset).
8. If projection returns `PROJECTION_LIMIT` (API 4xx when the visible set would
   exceed 200 objects or 2000 relations), the previous canvas is **kept** and
   the banner reads **超预算，已保留原图**. Edges are never dropped silently.

9. **四视图**: tabs 树/图 · 总览 · 影响清单 · 最短路径. Overview clusters are
   layer×type **count only** (members via the members API; locating a member
   selects it and may request projection — no invented edges). Impact is the
   independent paginated impact API (full reach), never the drawn tree.
   Path shows hops/relations from the path API; evidence `sourceRef` is
   copyable text only (no HTML). Cycle queries (`treeStatus=unavailable_cycle`)
   show **检测到循环依赖，已切换关系清单**; the tree tab is unavailable, list/path
   remain.

10. **数据导入** (nav 血缘工作台 | 数据导入): paste or upload ImportBatch JSON
    (documented sample path `fixtures/v1/import.json`, not production lineage).
    `POST /api/imports` → 202 with runId/status/errorCount/warningCount/snapshotId;
    `GET /api/imports/{runId}` paginates issues; **发布** when status is `ready`
    (`POST .../publish` with `expectedActiveSnapshotId`, empty = `null`).
    Oversize → `PAYLOAD_TOO_LARGE` / 413; schema errors → `IMPORT_INVALID` plus
    the issues list; CAS mismatch → `PUBLISH_CONFLICT`. Same CSRF
    `X-CSRF-Token: dev` and optional `X-Embed-Groups` as the query UI.

11. **异常态**: empty / no snapshot / no hits; `LINEAGE_NOT_COLLECTED` (未采集);
    request failure / `TEMPORARILY_UNAVAILABLE`; `POLICY_CHANGED` and
    `QUERY_EXPIRED` destroy the query context and prompt 重新搜索; incomplete
    `coverage` / `computationStatus` as quality notices; `FORBIDDEN` /
    `UNAUTHENTICATED` only when the API returns those codes (embed groups /
    demo-header — no invented OIDC mappings). Cycle 环→清单/路径 stays as in (9).

### P4 visual freeze (fixture screenshots)

Same pixel size as the viewport; never scale PNGs before Diff. Desktop
**1280×800** and narrow **390×844**. Four modes × two viewports = 8 files
(`tree-1280.png` … `path-390.png`). Reduced motion on; wait until URL restore
and expand have settled. Compare only the same `fixtures/v1` seed (`seedId=root`,
`selectedId=view-a`, path `targetId=java-j`) and the same view mode. Do **not**
force the Vue tree to match the prototype flowchart — tabs / filters / detail /
list / tokens only. `best/` is the labeled「历史最佳」set.

Needs API `127.0.0.1:8080` (published fixture) and web dev or preview:

```bash
cd apps/web
npm run dev                    # already running is fine
npm run capture:p4-visual      # imports fixtures/v1 if search has no root hit
```

Requires `playwright-core` (devDependency) and system Chrome
(`/usr/bin/google-chrome` or `CHROME_PATH`). Exits 2 if API/web is down.

Without a UI, the same expand path can be curled:

```bash
chmod +x scripts/demo-deep-expand.sh
./scripts/demo-deep-expand.sh
```

Exits 2 with import/publish instructions if the API is down. Fixtures are
**not** real lineage.

### OpenAPI types

Client types are generated with `openapi-typescript` 7.x from
`../../spec/v1/openapi.json`. Output is committed at
`src/generated/openapi.d.ts`. Runtime calls are plain `fetch` in
`src/api/lineageClient.js` and `src/api/importClient.js` (Vue 2 remains JavaScript).
