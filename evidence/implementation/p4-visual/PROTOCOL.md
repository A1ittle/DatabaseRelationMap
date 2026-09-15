# P4 visual freeze — screenshot protocol

Fixture-only freeze of the Vue 2 embedded shell against prototype **tabs /
filters / detail / list / CSS tokens**. Not a screenshot-to-code rewrite of
the tree, not OIDC, not Ling/auto-heal CI.

## Seed (never invent lineage)

Use **only** `fixtures/v1/import.json` → validate → publish (see
[docs/RUNBOOK.md](../../../docs/RUNBOOK.md)). Compare shots from the **same**
published snapshot + the same URL restore context:

| Param | Freeze value |
|---|---|
| `seedId` | `root` |
| `selectedId` | `view-a` |
| `targetId` | `java-j` (path view only) |
| `types` | omit (all four) |
| `mode` | `tree` · `overview` · `impact` · `path` |

`queryId` is a session credential and is **never** in the URL. Reload recreates
the query from `seedId`.

## Pixel rules (before Diff)

1. **Same pixel size as the browser viewport.** Desktop `1280×800`. Narrow
   `390×844` (A19/A20). `deviceScaleFactor = 1`. Never scale, crop-to-fit, or
   CSS-zoom a PNG before Diff.
2. **Fixed viewport** for the whole shot. Capture the layout viewport
   (`fullPage: false`), not a scrolled document stitch.
3. **Disable motion** before navigation: `prefers-reduced-motion: reduce` (and
   the shell’s reduced-motion CSS). Do not screenshot mid-transition.
4. **Wait until expand/query settled.** URL restore finished, network idle,
   and the active view’s list/tree/detail has painted. For tree, wait until
   default projection rows exist; if a twist is expanded, wait until children
   replace (revision), not the click frame.
5. **Fixed font stack** — prototype system UI (`--font-body` / `--font-mono`
   from `reference/offline-style-update/lineage-map.css`). Do not mix a second
   webfont or change OS font mid-series.
6. **Compare only** the same seed/query context **and** the same view mode.
   Do not Diff tree vs overview, 1280 vs 390, or fixture vs invented data.

## Files

Eight PNGs under `evidence/implementation/p4-visual/`:

```text
tree-1280.png     tree-390.png
overview-1280.png overview-390.png
impact-1280.png   impact-390.png
path-1280.png     path-390.png
```

`best/` is the labeled「历史最佳」set. After a capture that is not worse than
the previous best, copy the eight files into `best/`. If a CSS change looks
worse, restore CSS **and** keep `best/` (do not overwrite it with the worse
shot).

## Capture command

API at `http://127.0.0.1:8080`, web at `http://127.0.0.1:5173` (dev) or
preview. Import+publish fixture if search has no `root` hit.

```bash
cd apps/web
npm install          # includes playwright-core (Chrome channel, no extra browser)
npm run capture:p4-visual
# WEB_BASE=http://127.0.0.1:4173 API_BASE=http://127.0.0.1:8080 npm run capture:p4-visual
```

The script writes PNGs and a run log line; copy the log into
`evidence/implementation/p4-visual-freeze.txt`.

## What to compare (and what not)

**Do** compare: page chrome, view tabs, type filter chips, detail panel, list
rows, CSS tokens (bg/surface/fg/muted/border/accent/type/radius/fonts).

**Do not** require the Vue downstream tree to match the prototype flowchart
topology or SVG canvas. Layout comes from the fixture projection, not from
the HTML mock.
