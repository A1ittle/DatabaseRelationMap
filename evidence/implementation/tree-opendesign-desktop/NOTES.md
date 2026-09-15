# Desktop tree vs OpenDesign hops canvas

Fixture seed `root`, selected `view-a`, viewport **1280×800**. Not 390.

## Intent (OpenDesign `lineage-map-v2.html`)

- Columns by hop / `layoutRank`; seed column labeled 当前表, no extra white frame around the root column.
- NodeCard: type color block + 1.75px SVG icon (not emoji), mono name ≤2 lines, badge priority 当前 > +N/已展开 > 另 N > 终端 > type.
- `is-seed` accent border + glow; `is-sel` fg inset; hover `--hover`.
- SVG edge-layer: tree solid (rel may dash), cross dashed muted, selected path `edge.on` accent.
- Java-only column uses `--java-col` (`oklch(97% 0.012 280)`).

## This capture (`tree-1280.png`)

Live Vue 2 shell against published `fixtures/v1` (API `:8080`, web preview `:4173`).

- Nested `<ul>` / `TreeNode` is gone. `LineageTree` paints `.hops` columns + `NodeCard` + `.edge-layer`.
- Seed `root` shows 当前 + green glow; `view-a` is selected (inset) with `+1`; `proc-s` shows 另 1.
- CrossPanel remains beside the canvas; stage uses the 28px hairline grid.

Fixture default projection is one hop (`root` → `view-a` / `proc-s`); deeper Java leaf columns appear after `+N` expand, not in this URL snapshot.

Not 可上线. Fixture-only. No SSO changes.
