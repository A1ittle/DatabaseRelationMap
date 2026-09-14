# 06 · 交互契约与验收

把 `prototype/lineage-map-v2.html` 当验收标准。下列每条都应能写成 Story 或测例。

建议 QA 用 `fixtures/small-graph.json`，seed = `ods.trade_order`，对照虚线 3 条。

---

## 组件拆分（建议）

`Header` · `Toolbar` · `SummaryBar` · `LineageCanvas` · `NodeCard` · `ClusterCard` · `EdgeLayer` · `ImpactList` · `CallPath` · `Inspector` · `SearchCombobox` · `EmptyState`

---

## 节点卡片 NodeCard

**始终显示**

- 类型色块 + 图标
- `name`（等宽，最多两行，`overflow-wrap: anywhere`，完整名放 `title`）
- `title`（中文，muted）
- 徽章，优先级：当前表「当前」> focus 且有 tree 子节点时 `+N` /「已展开」> 有跨支「另 N」> Java「终端」> 类型名

**状态**

| class | 何时 | 视觉 |
|---|---|---|
| `is-seed` | id === seed | 绿色描边 + 浅绿色光晕，「当前」 |
| `is-sel` | id === sel | 前景色内描边 |
| 两者同时 | 根且选中 | 光晕 + 内描边同时存在 |
| `is-dim` | 有 highlightSet 且不在其中，且不是 seed | 背景变底，文字变 muted |
| hover | | 背景 `--hover`，描边略加深；文字不变浅 |
| focus-visible | | 2px `--fg` 描边，offset 2px |

**禁止：** 根列再套一层白底大框（已从原型去掉）。当前表只靠卡片自己的绿色标记。

触控目标高度 ≥ 44px（卡片 ≥ 52px，根卡片 ≥ 72px）。

---

## 画布交互

| 操作 | 结果 |
|---|---|
| 单击节点 | `sel = id`，打开详情，高亮主路径和该点跨支 |
| 单击 `+N` | 只展开 tree 子节点，不展开跨支邻居；该点变为选中 |
| 单击「本层还有 N」 | 展开该列超过 CAP 的卡片 |
| 双击 **表** | `recenter` |
| 双击非表 | 忽略（或仅选中，不要换根） |
| 单击空白 | 原型不取消选中；产品保持同样，避免误触丢详情 |

---

## 详情 Inspector

字段顺序：类型 kicker → 中文标题 → 等宽 name → desc → 位置 / 主父节点 / 系统 / 负责人 / 状态 → 主依赖与跨支邻接（最多 8）→ 动作。

动作：

- 仅当 `type === 'table' && id !== seed`：主按钮「把这张表设为根」（实心 `--fg`，hover 成对改成更深前景 + 白字）
- 有 tree 子节点：「展开 +N 子节点」/「收起子节点」

Java 固定说明：「Java 是叶子：不写出表，也不调用其他程序。」

窄屏（≤960px）：详情改底栏，可收起。选中节点时自动展开。

---

## 搜索

- `/` 聚焦（焦点不在 input/select 时）
- combobox：输入即搜，上下键移动，回车选中
- 无结果必须出现关键字本身
- 命中不在当前下游的 **表**：副标题「不在当前下游」，选中后换根
- Esc：关下拉并清空 q；若已关，则 `sel = seed`

---

## 键盘

| 键 | 条件 | 行为 |
|---|---|---|
| `/` | 不在字段内 | 聚焦搜索 |
| `1` `2` `3` `4` | 不在字段内 | 四视图 |
| 左右方向 | 焦点在页签 | 切换视图 |
| Esc | 搜索开 | 关搜索 |
| Esc | 搜索关且 sel ≠ seed | 回到当前表 |

不要用 `scrollIntoView` 滚整页（嵌入预览会坏）。列表内可用 `block: 'nearest'`。

---

## 类型筛选与深度

- 芯片 `aria-pressed`，可多选
- 全关 → 空状态 + 「显示全部类型」，不要空白画布
- 深度三段：1 层 / 2 层 / 全部 · 聚类
- 点全部且数据量大 → 切 overview

---

## 边的视觉

| 种类 | 画法 |
|---|---|
| 主依赖 tree | 实线，中性灰 `oklch(72% 0.01 240)`，宽 1.25 |
| 跨支 cross | 虚线 4 / 3.5，更深蓝灰 `oklch(44% 0.04 250)`，宽 1.35 |
| 高亮 on | `--accent`，宽 2 |
| 其余 dim | opacity 0.22 |

原型里 `calls` / `derives` 在主树上还有自己的 dash。产品若只区分主/跨，**优先保证实线主依赖 vs 虚线跨支**，rel 可以用标注或详情表达，避免三种虚线抢语义。

图例必须同时存在：「实线 · 主依赖」「虚线 · 跨支关联」「Java 只作为叶子」。

---

## 验收清单（16 对象）

打开原型或接好 fixture 的页面，seed = `ods.trade_order`：

- [ ] 第一列只有当前表，绿色「当前」，外面没有额外白框
- [ ] 看不到 `dim.merchant`、`core.t_order`、`OrderIngestJob`
- [ ] 看得到 `v_order_enriched`、`sp_risk`、`OrderRiskEngine`
- [ ] `GmvReportService` 的主父是 `ads.merchant_gmv`（实线）
- [ ] `v_order_enriched` 到 `GmvReportService` 是虚线
- [ ] `v_risk_daily` 的主父是 `dwd.fact_order`；来自 seed 和 `risk.order_score` 的是虚线
- [ ] 点 `+N` 只多出该节点的下游树子节点
- [ ] 双击 `dwd.fact_order` 后它成为根，上游 ODS 消失
- [ ] 关掉四类芯片出现空状态，点「显示全部类型」恢复
- [ ] 搜索 `zzzz` 出现「没有匹配「zzzz」的对象」
- [ ] 搜索 `merchant` 能看到不在下游的 `dim.merchant`，选中后换根
- [ ] 影响清单有下游计数，且跨支列非空
- [ ] 调用路径选 `GmvReportService`，步骤上能看到跨支标记
- [ ] Esc 先关搜索，再回到当前表
- [ ] 主按钮「把这张表设为根」hover 仍是深底浅字
- [ ] 窄屏详情可收起，点节点会展开

---

## 明确的产品文案（不要改成空话）

| 场景 | 文案 |
|---|---|
| 下游树说明 | 从输入表往下长树。实线是主依赖，虚线是下游之间的跨支关联。点 +N 展开子节点。 |
| 总览说明 | 按层和类型收成簇。最右若全是 Java，即为终端层。 |
| 影响说明 | 只清点下游。跨支关联单独一列，避免两条下游链互相看不见。 |
| 路径说明 | 从当前表走到一个终端（通常是 Java）。路径上的跨支会标出来。 |
| Summary | 以 **{name}** 为根，只画下游：**N** 个对象（…），其中 Java 终端 **M** 个。跨支关联 **K** 条。 |
