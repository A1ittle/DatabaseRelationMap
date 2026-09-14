# 04 · 主依赖 / 跨支判定

参考实现：`spec/build-tree.mjs`（与原型 `buildTree()` 同语义）。  
回归：`node spec/verify-tree.mjs`。

下游互相关联不是画不出来，而是 **主树只能给每个节点留一条父边**。多出来的边如果还按等权实线画，会和 hop 列打架。所以：主树实线，跨支虚线。

---

## 输入 / 输出

**输入**

- `nodes: Node[]`
- `edges: Edge[]`（原始采集边）
- `seed: string`（必须是 table，且存在于 nodes）

**输出** `LineageTree`（见 `spec/types.ts`）

---

## 伪代码

```
STRENGTH = { writes: 3, derives: 2, calls: 1, reads: 0 }

function liveEdges(nodes, edges):
  byId = index(nodes)
  return edges where
    byId[from] and byId[to] exist
    and byId[from].type != 'java'

function buildTree(nodes, edges, seed):
  usable = liveEdges(nodes, edges)

  # 1) 只向下游 BFS
  out[from] = list of usable edges from from
  reach = { seed }
  queue = [seed]
  while queue:
    u = pop
    for e in out[u]:
      if e.to not in reach:
        add e.to to reach; push e.to

  # 2) 可达子图入度（Kahn）
  indeg[id] = 0 for id in reach
  for e in usable:
    if e.from in reach and e.to in reach:
      indeg[e.to] += 1

  # 3) 更长路径优先选主父边
  rank[seed] = 0
  parent = {}
  parentEdge = {}
  tq = [id in reach where indeg[id] == 0]
  seen = {}

  while tq:
    u = shift tq
    if u in seen: continue
    seen.add(u)
    for e in out[u] where e.to in reach:
      cand = rank.get(u, 0) + 1
      prev = rank.get(e.to)
      better =
        prev is null
        or cand > prev
        or (cand == prev
            and STRENGTH[e.rel] > STRENGTH[parentEdge[e.to].rel])
      if better:
        rank[e.to] = cand
        parent[e.to] = u
        parentEdge[e.to] = e
      indeg[e.to] -= 1
      if indeg[e.to] == 0:
        push e.to

  # 4) 环 / 漏点兜底
  for id in reach:
    if id not in rank:
      rank[id] = 0 if id == seed else 1

  # 5) 分类
  treeKey = { from + '>' + to for e in parentEdge.values }
  tree, cross = [], []
  for e in usable:
    if e.from not in reach or e.to not in reach: continue
    if e.from == e.to: continue
    if (e.from + '>' + e.to) in treeKey: tree.push(e)
    else: cross.push(e)

  children[from].push(to) for e in tree
  crossOf[from].push(e) and crossOf[to].push(e) for e in cross

  return { rank, parent, parentEdge, reach, tree, cross, children, crossOf }
```

---

## 判定细则

### liveEdges

Java **没有出边**。即使原始图里 Java 指向一张表，本产品当这条边不存在。

### 更长路径优先

目的：主树沿「加工链」走，短的旁路变成跨支。

例：`ods.trade_order → v_order_enriched → … → ads.merchant_gmv → GmvReportService` 比 `v_order_enriched → GmvReportService` 更长，所以 GMV 报表服务的主父节点是集市表，补全视图到报表服务是跨支。

### 强度只做并列决胜

只有 `cand === prev` 时才看 `STRENGTH`。不要先按强度选父节点再比长度。

### 跨支挂在两端

`crossOf` 对 from、to 都登记。选中任一端，都要高亮这条虚线。

### 侧输入

`dim.merchant → v_active_merchant → v_order_enriched` 在全图存在，但从 `ods.trade_order` 出发的 BFS 到不了 `dim.merchant`。`v_order_enriched` 仍只因 seed 的 `reads` 进树。不要为了「视图还读了维表」把维表画进主干。

---

## 16 对象、seed = `ods.trade_order` 的期望结果

权威 JSON：`fixtures/seed-ods-trade-order.expected.json`。

**进树（含 seed）**

| id | rank | 主父节点 | 主边 rel |
|---|---|---|---|
| ods.trade_order | 0 | — | — |
| v_order_enriched | 1 | ods.trade_order | reads |
| sp_risk | 1 | ods.trade_order | reads |
| OrderRiskEngine | 1 | ods.trade_order | reads |
| dwd.fact_order | 2 | v_order_enriched | derives |
| risk.order_score | 2 | sp_risk | writes |
| sp_mart | 3 | dwd.fact_order | reads |
| v_risk_daily | 3 | dwd.fact_order | reads |
| ads.merchant_gmv | 4 | sp_mart | writes |
| GmvReportService | 5 | ads.merchant_gmv | reads |

**不进树**

`core.t_order`、`sp_sync`（上游）、`ods.pay_log` / `stg.user_account` / `OrderIngestJob`（另一支）、`dim.merchant` / `v_active_merchant`（侧输入）。

**跨支（3 条）**

1. `ods.trade_order → v_risk_daily` reads（短路径，主父改走 fact）
2. `risk.order_score → v_risk_daily` reads（并列 hop=3，先写入的 fact 边已是主父）
3. `v_order_enriched → GmvReportService` reads（短路径，主父改走集市）

若你们的实现这 3 条变成了主依赖，或 GmvReportService 的父节点不是 `ads.merchant_gmv`，就是语义回归。

---

## 调用路径（与主树不同）

`shortestDown(from, to)`：在 `liveEdges ∩ reach` 上 **BFS 最短路径**（边数最少）。

它 **不是** 沿 `parent` 指针回溯。路径上每条边再查是否属于 `treeKey`，不属于则标跨支。

例：从 seed 到 `GmvReportService` 的最短路可能走 `v_order_enriched` 的直接读取（跨支），而主树父指针走集市。两种展示都要保留。

---

## 建议放在后端的原因

1. 权限裁边后必须重算主父边，前端没完整图会算错
2. 懒加载时，已展开子树的 rank 要和首屏一致
3. 前端只画 `tree` / `cross`，避免两套实现漂移

前端若离线演示，必须用 `spec/build-tree.mjs`，不要重写一版「看起来差不多」的逻辑。
