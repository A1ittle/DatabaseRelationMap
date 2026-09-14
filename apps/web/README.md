# lineage-web

P3 Vue 2 embedded shell for 程序血缘地图: search → create query → default
one-level downstream tree → node detail → children pagination → full
projection replace → cross-list locate → change-root → drawn stats → budget
keep-old. No React Flow (ADR 0001); expandable tree + detail / cross panels.
Graph layout (`src/graph/*`) is separate from API/domain.

```bash
npm install
npm run generate:api  # spec/v1/openapi.json → src/generated/openapi.d.ts
npm run dev           # http://127.0.0.1:5173
npm run test          # vitest (revision, budget keep-old, cross list, layout)
npm run build
```

Iframe-friendly (transparent page background). Graph canvas libraries are
intentionally not included.

### Config

| Variable | Default | Meaning |
|---|---|---|
| `VITE_API_BASE` | `http://127.0.0.1:8080` | Lineage API origin. Empty string = same-origin (uses Vite `/api` proxy in `npm run dev`). |
| `VITE_CSRF_TOKEN` | `dev` | Sent as `X-CSRF-Token` on POSTs (open-mode API accepts any non-empty value). |
| `VITE_EMBED_GROUPS` | *(omit)* | Optional `X-Embed-Groups` (host-trusted groups; deny wins on the API). |

Copy `.env.example` to `.env.local` to override. Do not commit secrets.

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
`src/api/lineageClient.js` (Vue 2 remains JavaScript).
