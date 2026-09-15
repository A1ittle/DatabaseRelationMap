## 0. 确认门禁
- [ ] 最终树形态以 OpenDesign 原型为准，产品不得另起嵌套列表或流程图拓扑。
- [ ] 先用 16 对象看清结构、再用 520 对象看清规模策略，两套示意图都签字后再动产品代码。
- [ ] 16/520 开关、页脚「交易订单域」、`localStorage`、假数据 `buildGraph()` 只属于原型，产品不得带上线。
- [ ] 只画下游、Java 无出边、一节点一条主父实线、其余可达边为跨支虚线——语义与 `LOCKED-RULES.md` 一致。

## 1. 画布结构（列式 hops、NodeCard、SVG 边、跨支虚线）
- [ ] 桌面壳：`Header / Toolbar / SummaryBar / Body(画布 1fr + Inspector 320px) / Footer`，行网格 `auto auto auto minmax(0,1fr) auto`。
- [ ] 画布 `.stage`：28px 发丝网格浅底，横向可滚多列，不要大面积插画或渐变洗底。
- [ ] 下游树按 hop/`layoutRank` 分列（`.hops` + `.col`），seed = 第 0 列「当前表」，列宽约 240–280px。
- [ ] 第 0 列外面不再套白底大框；当前表只靠卡片自身绿色描边 + 浅绿光晕。
- [ ] 该列节点全是 Java 时列头为「第 k 层 · 终端」，列背景浅紫 `oklch(97% 0.012 280)`，此底色不得用到第一列。
- [ ] 列头右侧数字用表格数字；每列默认最多 5 张卡（`CAP=5`），其余「本层还有 N 个对象」。
- [ ] NodeCard：类型色块 + 1.75 描边单线 SVG（原型 `ICO`），等宽 `name` 最多两行 + `title` 完整名，中文标题 muted。
- [ ] 徽章优先级：当前 > `+N`/已展开 > 另 N（跨支）> 终端（Java）> 类型名。
- [ ] 卡片状态：`is-seed` 绿描边+光晕；`is-sel` 前景内描边；两者可同时；`is-dim` 非高亮变淡；hover `--hover` 且文字不变浅。
- [ ] 卡片高度：普通 ≥52px，根卡 ≥72px；触控目标 ≥44px。
- [ ] SVG `.edge-layer` 铺在卡片后：主依赖实线中性灰 1.25；跨支虚线 `4/3.5`、更深蓝灰 1.35；高亮 `--accent` 宽 2；其余 dim opacity 0.22。
- [ ] 边只画两端都可见的 tree/cross；选中时高亮 `treePath(sel)` 上的主边 + 与 sel 相连的跨支。
- [ ] 产品优先保证「实线=主依赖 / 虚线=跨支」；`calls`/`derives` 的额外 dash 可省略，避免三种虚线抢语义。
- [ ] Summary 图例必须同时有：「实线 · 主依赖」「虚线 · 跨支关联」「Java 只作为叶子」。
- [ ] 16 对象（seed=`ods.trade_order`）必须能看见 3 条跨支：`v_order_enriched→GmvReportService`、`ods.trade_order→v_risk_daily`、`risk.order_score→v_risk_daily`。
- [ ] 点卡片=选中+开详情+高亮路径；点 `+N` 只展开 **tree 子节点**（不展开跨支邻居）；点空白不取消选中。
- [ ] 四种视图是互斥页签（focus/overview/impact/path），不是同一张图的滤镜。

## 2. 520 规模默认行为（只展开 1 层、聚类总览）
- [ ] 520 进入：`depth=1`、`mode=focus`、`expanded={}`；加载文案「正在准备 520 个对象的下游树，默认只展开 1 层。」
- [ ] 默认只渲染 hop≤1 的 NodeCard，禁止首屏铺开全部叶子。
- [ ] 点「全部 · 聚类」（`depth=99`）且图很大时，**强制切 `overview`**，禁止在 focus 里一次铺开 500+ 叶子。
- [ ] 分层总览按 **hop × 类型** 聚类；某类型 >5 先出数量条 ClusterCard，点击再展开名字。
- [ ] 500 对象总览只渲染簇，不渲染 500 张 NodeCard。
- [ ] 深度三段固定为：1 层 / 2 层 / 全部 · 聚类。
- [ ] 点 `+N` 只多露该节点的树子节点（产品可简化为多露一层，但不得把跨支邻居当子节点展开）。
- [ ] 搜索到深层对象：提高 depth 或切 overview 以看见它，不得在 focus 突然铺开整层。
- [ ] 16 对象用于看清跨支虚线；520 只证明「默认 1 层 + 总览聚类」策略，不必复现那份随机扇出数据。
- [ ] Summary 计数按整棵可达下游（`stats.downstream` / Java 终端 / 跨支边数），不要按当前可见节点计。

## 3. tokens / 禁止项
- [ ] 颜色/圆角/字号全部走 `spec/tokens.css`（hex 回退 + `@supports oklch`），组件内不散落写死色。
- [ ] Accent 只给当前表描边/光晕 + 高亮路径；筛选芯片、页签禁止用绿。
- [ ] 主按钮 / 选中页签 / 深度段：深底浅字，hover 成对 `--fg-hover`/`--surface`，禁止只把字变 muted。
- [ ] 次按钮/芯片：浅底，hover `--hover`，文字保持 `--fg`。
- [ ] `focus-visible`：`outline: 2px solid var(--fg); outline-offset: 2px`。
- [ ] 对象名 `--font-mono` 12px 最多两行；数字 `tabular-nums`；卡片圆角 6px，芯片胶囊 999px。
- [ ] 产品不挂 Google Fonts；`JetBrains Mono` 换成公司允许的等宽或自托管 IBM Plex Mono。
- [ ] **禁止**嵌套 `<ul>` / 折叠列表当树。
- [ ] **禁止**移动端优先、390 视口验收、把 ≤960 当树画布目标（本期只要桌面）。
- [ ] **禁止** emoji 当类型图标；必须用原型 24 视口单线 SVG。
- [ ] **禁止**根列再套白框；禁止左侧色条 + 大圆角营销卡 / AI 仪表盘造型。
- [ ] **禁止**把跨支画成第二条等权实线；禁止 focus+全部一次渲染 500+ 叶子。
- [ ] `--muted` 不得再降到 11px 浅灰；普通文字对比 ≥4.5:1。
- [ ] `prefers-reduced-motion: reduce` 时关掉 spinner 以外的动画。

## 4. 产品应对齐的验收截图集（全部 1280×800，`deviceScaleFactor=1`，关动画）
对照源：先锁 OpenDesign 原型，再锁产品同构图。建议文件名如下。

**结构锁（16 对象，seed=`ods.trade_order`）**
- [ ] `od-16-focus-default-1280.png`：下游树默认（16 可 depth=全部以便看清全链）；第 0 列仅当前表、无白框。
- [ ] `od-16-focus-cross-1280.png`：三条跨支虚线同时可见（`GmvReportService` 主父为 `ads.merchant_gmv` 实线）。
- [ ] `od-16-focus-select-path-1280.png`：选中非根节点，主路径 accent、相连跨支高亮、其余 dim。
- [ ] `od-16-focus-expand-1280.png`：点某节点 `+N` 后只多出 tree 子节点。
- [ ] `od-16-focus-seed-glow-1280.png`：根卡「当前」+ 绿描边光晕；Java 终端列浅紫底。
- [ ] `od-16-overview-1280.png`：分层总览（簇/列头语义）。
- [ ] `od-16-impact-1280.png`：三栏（当前表 / 下游影响 / 跨支关联非空）。
- [ ] `od-16-path-gmv-1280.png`：调用路径到 `GmvReportService`，步骤上标出跨支。

**规模锁（520 对象）——确认最终形态的关键示意图**
- [ ] `od-520-focus-depth1-1280.png`：**默认只展开 1 层**，可见卡远少于 520，列有「本层还有 N」。
- [ ] `od-520-overview-cluster-1280.png`：点「全部 · 聚类」进入总览，按 hop×类型出数量簇，不是 520 张卡。
- [ ] `od-520-focus-plusn-1280.png`：在 1 层上点一个 `+N`，只展开该支子节点，整图仍不是全叶子。
- [ ] `od-520-overview-expand-cluster-1280.png`：点开一个 >5 的簇，出现名字列表 +「其余 N」。

**壳层与禁止项锁（16 即可）**
- [ ] `od-16-empty-types-1280.png`：四类芯片全关 → 空状态 +「显示全部类型」。
- [ ] `od-16-inspector-recenter-1280.png`：选中非根表，主按钮「把这张表设为根」，hover 仍深底浅字。
- [ ] `od-16-chrome-tokens-1280.png`：页签/芯片/图例/Inspector 用 token，芯片页签无绿色、无 emoji。

**产品对齐时**
- [ ] 产品截图与上列同构图、同 viewport 对齐；不 Diff 16 拓扑 vs 产品 fixture 拓扑，但 **画布结构、默认 1 层、聚类总览、token/禁止项必须同构**。
- [ ] 不提交、不要求 390 / 移动端截图。
- [ ] 本清单勾完并保存上述示意图后，才允许改产品树视图代码。
