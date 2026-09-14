# 05 · 四种视图与状态机

`mode` 是互斥页签，不是同一画布的滤镜。每种视图读同一棵 `LineageTree`，但可见节点和主操作不同。

---

## 全局状态

```ts
type Mode = 'focus' | 'overview' | 'impact' | 'path'

type AppState = {
  seed: string                 // 根表
  sel: string                  // 选中对象，默认 seed
  mode: Mode                   // 默认 focus
  types: Record<NodeType, boolean>  // 默认全 true
  depth: 1 | 2 | 99            // 99 = 全部（必须聚类）
  q: string                    // 搜索
  pathTo: string               // path 视图终点
  expanded: Record<string, true>     // focus：已展开的树父节点
  openCols: Record<string, true>     // 列 / 簇是否展开超过 CAP
  openGroups: Record<string, true>   // impact 分组
  inspectOpen: boolean         // 窄屏详情是否展开
}
```

写入 URL 的：`seed, sel, mode, depth, types, pathTo`。  
会话即可：`expanded, openCols, openGroups, inspectOpen, q`。

---

## 状态图

```
                 打开页面
                     │
                     ▼
              loadTree(seed)
                     │
                     ▼
        ┌──────── focus ────────┐
        │  depth 默认 1         │
        │  点 +N → expanded[id] │
        │  点节点 → sel + 高亮   │
        └──────────┬────────────┘
     1-4 / 页签    │
        ┌──────────┼────────────┬────────────┐
        ▼          ▼            ▼            ▼
     focus     overview      impact        path
        │          │            │            │
        │     depth=99 时       │       改 pathTo
        │     强制进入这里       │       拉 /path
        │          │            │            │
        └──────────┴─────┬──────┴────────────┘
                         ▼
              双击表 / 设为根 / 搜索到域外表
                         │
                         ▼
                   seed = 该表
                   sel = 该表
                   expanded = {}
                   重算树
```

---

## 每种视图

### focus · 下游树

**目的：** 从当前表往下长树，看清主链和跨支。

**可见节点 `visibleIds`**

- hop 0（seed）始终可见（类型开着才显示）
- `hop <= depth` 可见
- 额外：若父节点在 `expanded` 里，允许看到 `hop <= depth + 2` 的直接下游（原型行为：展开后多露两层）

产品实现可以简化为：展开只多露 **一层 tree 子节点**（更符合懒加载）。若简化，要在 Story 里写明，并保证 `+N` 仍只展开 tree 子节点。

**列：** 按 `rank` 分列。列头：hop 0 =「当前表」；若该列全是 Java =「第 k 层 · 终端」；否则「第 k 层」。

**每列默认最多 5 张卡片**（`CAP = 5`），其余「本层还有 N 个对象」。

**边：** 只画两端都可见的 tree（实线）和 cross（虚线）。选中时：主路径 `treePath(sel)` 上的 tree 边高亮；与 sel 相连的 cross 边高亮；其余变淡。

**主操作：** 选中、`+N` 展开、双击表设为根。

### overview · 分层总览

**目的：** 看层和类型的体量，而不是 500 个名字。

**可见：** `hop <= depth`。`depth === 99` 时看全部可达节点，但按 **hop × type** 聚类。

**簇：** 某类型超过 5 个时先显示数量条，点击再展开名字。

**强制规则：** 用户点「全部 · 聚类」时，若数据规模大，`mode` 必须切到 `overview`。禁止在 focus 里一次铺开全部叶子。

### impact · 影响清单

**目的：** 清点下游，并单独列出跨支，避免两条链互相看不见。

三栏：

1. 当前表（只一张）
2. 下游影响：`reach \ {seed}`，按类型分组，>8 行默认收起
3. 跨支关联：出现在任意 cross 边两端的节点（不含 seed），同样分组

数字用 `stats.downstream` 和 `stats.crossCount`（跨支是 **边数**）。

无画布连线。

### path · 调用路径

**目的：** 回答「从这张表怎么走到这个终端」。

- 终点下拉：当前 reach 内对象，Java 排前
- 展示 `GET /lineage/path` 的步骤列表
- 每步标「主依赖 · rel」或「跨支 · rel」
- 找不到：空状态，提示换一个下游对象，优先 Java

---

## 关键转换

| 事件 | 状态变化 |
|---|---|
| `setMode(m)` | `mode = m` |
| `setDepth(99)` 且图很大 | `depth = 99`，`mode = overview` |
| `toggleType(t)` | 翻转；若四类全关 → 空状态，树不卸 |
| `select(id)` | `sel = id`；窄屏 `inspectOpen = true` |
| `expand(id)` | 翻转 `expanded[id]`，`sel = id` |
| `recenter(tableId)` | `seed = sel = tableId`，清空 `expanded`，重拉 `/lineage` |
| `reveal(id)` 搜索选中 | 若是域外表 → `recenter`；否则 `sel = id`，沿 `treePath` 把祖先标为 expanded，必要时提高 depth / 切 overview |
| Esc | 搜索开着先关搜索；否则 `sel = seed` |
| 键盘 1–4 | focus / overview / impact / path |

`treePath(id)`：沿 `parent` 回溯到 seed，用于高亮和 reveal 时展开祖先。

`highlightSet`：`treePath(sel)` ∪ 与 sel 相连的跨支两端。seed 始终不 dim。`sel === seed` 时不高亮（全图正常亮度）。

---

## 深度与规模

| 场景 | depth | mode |
|---|---|---|
| 默认进入 | 1 | focus |
| 用户要看第二层 | 2 | 保持当前（通常 focus） |
| 用户要看全部 | 99 | overview |
| 搜索到第 4 层对象 | 提到能看见它，或切 overview | 不要在 focus 里突然铺开整层 |

---

## 空 / 加载 / 错误（画布层）

| 状态 | 文案要点 | 动作 |
|---|---|---|
| 加载 | 「正在构建下游树」 | 无 |
| 四类全关 | 「没有可见对象」 | 显示全部类型 |
| 筛选后 0 节点 | 「当前筛选下没有节点」 | 重置筛选 |
| 路径不存在 | 「没有通往该对象的下游路径」 | 无（改下拉） |
| 接口失败 | 「无法绘制血缘图」 | 重试 |
| 未采集 | 「还没有采集到血缘」 | 无或去采集任务 |
| 无权限 | 「没有查看该对象血缘的权限」 | 无 |
