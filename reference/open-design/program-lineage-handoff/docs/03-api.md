# 03 · 接口约定

路径前缀按你们网关调整。字段名可映射，**语义必须对齐**。完整 schema 见 `spec/openapi.yaml`。

原则：**主依赖 / 跨支由后端算好再返回，前端只负责画。**

---

## GET `/lineage`

当前表的下游树。第一期 `direction` 只支持 `downstream`。

### Query

| 参数 | 必填 | 说明 |
|---|---|---|
| `seed` | 是 | 根表 id |
| `direction` | 否 | 默认 `downstream`。其他值 400 |
| `depth` | 否 | `1`（默认）/ `2` / `all`。`all` 仍建议只返回聚类摘要，或配合 `view=overview` |
| `types` | 否 | `table,view,procedure,java`，默认全开 |
| `expand` | 否 | 已展开节点 id，逗号分隔。只对这些节点多返回一层 **tree 子节点** |
| `view` | 否 | `focus` \| `overview` \| `impact` \| `path`。影响默认裁剪策略 |

### 200 Body

```json
{
  "seed": { "id": "ods.trade_order", "name": "ods.trade_order", "title": "交易订单表", "type": "table" },
  "stats": {
    "downstream": 9,
    "leaves": 2,
    "crossCount": 3,
    "byType": { "table": 3, "view": 2, "procedure": 2, "java": 2 }
  },
  "nodes": [ "/* 本响应里出现的节点，含 seed */" ],
  "tree": [ { "from": "ods.trade_order", "to": "v_order_enriched", "rel": "reads", "kind": "tree" } ],
  "cross": [ { "from": "ods.trade_order", "to": "v_risk_daily", "rel": "reads", "kind": "cross" } ],
  "rank": { "ods.trade_order": 0, "v_order_enriched": 1 },
  "parent": { "v_order_enriched": "ods.trade_order" },
  "truncated": false
}
```

`stats` 按 **整棵可达下游** 计（权限过滤后），不要按当前 depth 的可见节点计。SummaryBar 用这个数字。

`truncated: true` 表示还有未下发的更深节点。前端在父节点上画 `+N`，N 来自该节点的 `childCount`（见节点扩展字段或另一次 children 接口）。

### 节点扩展字段（推荐）

每个 `nodes[]` 项额外带：

| 字段 | 说明 |
|---|---|
| `childCount` | 类型筛选后的 tree 子节点数。`+N` 用它 |
| `crossCount` | 类型筛选后的跨支条数。徽章「另 N」用它 |
| `hasMore` | depth 裁剪后是否还有未加载子节点 |

### 错误

| HTTP | `code` | 何时 |
|---|---|---|
| 400 | `INVALID_SEED` | seed 不是表，或参数非法 |
| 401 | `UNAUTHENTICATED` | 未登录 |
| 403 | `FORBIDDEN` | 无权限看这张表 |
| 404 | `SEED_NOT_FOUND` | 对象不存在 |
| 409 | `LINEAGE_NOT_COLLECTED` | 对象存在但尚未采集血缘 |
| 500 | `INTERNAL` | 构建失败 |

前端文案：

- 403：「没有查看该对象血缘的权限」
- 409：「这张表还没有采集到血缘」+ 空状态，不要画假节点
- 500：「无法绘制血缘图」+ 重试

---

## GET `/lineage/children`

懒展开。点 `+N` 时调用，不要为了展开去重拉整棵树。

### Query

| 参数 | 说明 |
|---|---|
| `seed` | 当前根，保证 rank / 主父边与当前树一致 |
| `id` | 要展开的节点 |
| `types` | 同 `/lineage` |

### 200

该节点的 **tree 子节点** 列表，以及这些子节点之间、与已在画布上节点之间的 `tree` / `cross` 边增量。

---

## GET `/lineage/search`

### Query

| 参数 | 说明 |
|---|---|
| `q` | 关键字，匹配 `name` / `title` / `owner`，大小写不敏感 |
| `seed` | 可选。有则结果里标记 `inDownstream` |
| `limit` | 默认 10，最大 20 |

表可以出现在「不在当前下游」里，选中后前端换根。非表且不在当前下游的对象：**不要返回**（原型只允许用表换根）。

```json
{
  "query": "gmv",
  "items": [
    {
      "id": "ads.merchant_gmv",
      "name": "ads.merchant_gmv",
      "title": "商户 GMV 集市",
      "type": "table",
      "owner": "分析域",
      "inDownstream": true
    }
  ]
}
```

无匹配：`200` + `items: []`。前端说「没有匹配「q」的对象」。

---

## GET `/lineage/nodes/{id}`

详情栏。除 Node 字段外返回：

- `hop`、`parentId`
- `treeIn` / `treeOut`（主依赖邻接，可截断前 8 条并带 `total`）
- `cross`（跨支邻接）
- `canSetAsRoot`: `type === 'table' && id !== seed`

---

## GET `/lineage/path`

调用路径视图。

### Query

`from`（seed）、`to`（目标，优先 Java）

算法：在 **liveEdges ∩ reach** 上做 BFS 最短下游路径（边数最少，不是主树路径）。

返回：

```json
{
  "from": "ods.trade_order",
  "to": "GmvReportService",
  "found": true,
  "nodes": [ "/* 路径上的节点 */" ],
  "edges": [
    { "from": "...", "to": "...", "rel": "reads", "kind": "tree" }
  ]
}
```

某条边若不属于主树，`kind: "cross"`，UI 标「跨支 · 读取」。

`found: false` 时空状态：「没有通往该对象的下游路径」。

---

## URL 状态（前端，不要只靠 localStorage）

```
/lineage-map?seed=ods.trade_order&sel=dwd.fact_order&mode=focus&depth=1&types=table,view,procedure,java
```

| 参数 | 默认 |
|---|---|
| `seed` | 产品默认表或用户上次合法表 |
| `sel` | `seed` |
| `mode` | `focus` |
| `depth` | `1` |
| `types` | 四类全开 |
| `pathTo` | 仅 `mode=path` 时需要 |

分享链接必须能复现同一棵树和选中对象。`expand` 可以放 session，不必进 URL。

---

## 性能门槛

- 首屏默认 `depth=1`
- `depth=all` 走总览聚类，或只返回每层每类型的 count + 抽样，禁止一次下发全部叶子
- 单次 `/lineage` 建议硬顶（例如 200 个节点）；超出设 `truncated: true`
- 展开走 `/lineage/children`
