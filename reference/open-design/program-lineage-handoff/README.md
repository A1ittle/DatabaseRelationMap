# 程序血缘地图 · 开发交接包

本包是 `lineage-map-v2.html` 的工程交接件，不是可上线源码。

**怎么用**

| 角色 | 先看什么 |
|---|---|
| 产品 / QA | `LOCKED-RULES.md` → `docs/06-interaction.md` → 打开 `prototype/lineage-map-v2.html` 当验收标准 |
| 后端 | `LOCKED-RULES.md` → `docs/02-domain.md` → `docs/03-api.md` → `docs/04-tree-algorithm.md` |
| 前端 | `docs/05-views-and-state.md` → `docs/06-interaction.md` → `docs/07-visual.md` → `spec/types.ts` |
| 工程负责人 | `docs/08-engineering.md` |

浏览器打开 `handbook.html` 可通读整份交接（与 docs 内容一致，便于评审）。

**不要改的语义**（详见 `LOCKED-RULES.md`）

1. 只画当前表的下游
2. Java 是叶子，没有出边
3. 每个节点只留一条主父边；其余边是跨支，画虚线
4. 主父边：更长路径优先，并列时 `writes > derives > calls > reads`

**本包不是什么**

- 不是 Vue / React 业务页面
- 不是真实血缘服务
- 原型里的 16 / 520 切换、`localStorage`、`innerHTML` 整页重绘、Google Fonts **都要换掉**

**建议落地顺序**

1. 后端实现下游树接口（主依赖 / 跨支由服务端算好）
2. 前端按四种视图接 16 对象真实数据，对照原型走通虚线、设为根、路径
3. 再上懒加载、聚类、权限、URL 状态

跑样例拆树校验：

```bash
node spec/verify-tree.mjs
```
