# P4 visual freeze — CSS change log

Fixture only (`fixtures/v1`). Comparison target = prototype **tabs / filters /
detail / list / CSS tokens** in `reference/offline-style-update/lineage-map.css`.
Do **not** force Vue tree topology to match the prototype flowchart.

Record the three questions **before** each CSS change. If a change looks worse
than `best/`, revert.

---

## 2026-09-14 — extract prototype `:root` tokens into the Vue shell

### (1) Biggest visual gap vs prototype tokens?

The embedded shell used Primer-like greys (`#1f2328` / `#57606a` / `#d0d7de`)
and accent `#1a7f37` on a translucent white page, not prototype `--bg` `#f6f9fc`,
`--surface`, `--fg` `#121c23`, `--muted`, `--border` `#d9dfe3`, `--accent`
`#299236`, `--hover` / `--press`, type colors, `--radius`, or the system UI
font stacks. Selected **tabs** painted accent-green instead of prototype
`.modes` (selected = `--fg` on `--surface`). **Filters** were raw checkboxes,
not pill chips. **Panels / lists** used ad-hoc rgba overlays instead of
`--surface` / `--hover` / `--press`.

### (2) Which selector?

- New `apps/web/src/tokens.css` (`:root` + `@supports oklch` from the prototype,
  plus Vue-only `--tree` / `--cross` / `--unclassified`)
- `.shell`, `button`, `.search input`, `.shell-nav button.active`
- `.view-tabs` / `.view-tab` (prototype `.modes`)
- `.type-filter` / `.type-chip` (prototype `.chip`)
- `.tree-panel`, `.detail-panel`, `.cross-panel`, `.overview-panel`,
  `.impact-panel`, `.path-panel`, `.import-page` (`--surface`)
- list hover/selected (`.tree-row`, `.cluster-btn`, `.member-btn`,
  `.impact-row`, `.hop-node`)

### (3) What will change?

Token values and chrome colors/fonts/radius only. Tab selected state and filter
chips follow prototype tabs/filters. Panel and list surfaces use `--surface` /
`--hover` / `--press`. Tree **topology** (expand, layoutRank, kinds) is
unchanged. `html, body` stay transparent for iframe embed; `.shell` fills with
`--bg`. Reduced-motion media query disables transitions before shots.

If worse than `best/`, revert this slice.
