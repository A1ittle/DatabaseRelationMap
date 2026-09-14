# 08 · 工程改造与落地顺序

## 直接复用

- `prototype/lineage-map.css` 完整组件样式 + `spec/tokens.css` 变量 → design tokens（离线已含 hex 回退）
- NodeCard / 列头 / Inspector / 空状态 / 主按钮 hover 配对 → 组件规范
- `TYPES` / `REL` / `STRENGTH` → 枚举
- `buildTree()` → 后端布局/拆树（`spec/build-tree.mjs`）

## 必须换掉

**数据**

- 删除 `buildGraph`、`CORE`、`TOPICS`、16/520 开关
- 接口见 `docs/03-api.md`
- 展开懒加载
- 状态进 URL

**渲染**

- 组件化，禁止整页 `innerHTML`
- 节点多时虚拟化
- 边不要每次全量 `getBoundingClientRect`；先按 hop 分列，量大再 ELK / dagre
- `id` 含点号，选择器用属性选择器

**产品**

- 鉴权、租户、对象权限
- 真实错误：失败 / 无权限 / 未采集
- 去掉演示控件和「交易订单域」
- 字体自托管

## 推荐落地顺序

1. **验收标准：** 对照 `docs/06-interaction.md` 写 Story，先用 fixture 跑通
2. **先定接口：** 下游树、节点详情、搜索、懒展开、路径。拆树后端做
3. **按组件拆：** 上一节名单
4. **先接 16 对象真实数据**（或 fixture）：虚线、设为根、路径
5. **再上规模：** 默认 1 层；全部走总览聚类
6. **权限与空态** 与画布逻辑并行，不要最后才补

## 性能门槛（建议写进 Definition of Done）

- 首屏 ≤ 1 层，节点数有硬顶
- 切换视图不重拉整棵树（树已在内存 / 缓存）
- 展开只请求 children
- 500 对象总览只渲染簇，不渲染 500 张 NodeCard

## 风险

| 风险 | 做法 |
|---|---|
| 前后端各写一套拆树 | 以后端为准，前端 fixture 对 `verify-tree.mjs` |
| 把跨支画成第二条实线 | QA 用 16 对象三条虚线验收 |
| focus + 全部 = 卡死 | depth=99 强制 overview |
| 搜索出无权限对象 | 服务端过滤，不要前端藏 |
| 设为根可对视图/Java | API 400 `INVALID_SEED`，按钮根本不出现 |
