# AGENTS.md — 给 agent / 后续改动的硬约束

> **核心：不要过度设计。** 本仓库是**嵌入式血缘探索工具**，不是企业治理平台。能复用现有契约与 OpenDesign 就不要另起炉灶。

## 1. 产品范围（做 / 不做）

**做（V1 嵌入式）：** 规范 JSON 导入 → 校验发布快照 → 搜根表 → 四种下游视图（树 / 总览 / 影响 / 路径）→ 对象与关系证据。仅下游；Java 为展示终点；主树 / 跨支分类见 HANDOFF + `spec/v1`。

**不做：**

- 全量数据治理平台、企业级控制面、通用工作流 / 编排引擎
- 独立完整 OIDC / SSO / 组织权限体系（宿主已有能力不重复造；真实 SSO 未接入时标 **BLOCKED**，不编造）
- 自动 SQL/Java 源码解析、字段血缘、跨 scope 合成、SCC 组内图、多实例、导出审批（见 HANDOFF 范围外）
- 「将来可能用到」的微服务拆分、配置中心、插件框架、多余抽象层

## 2. 不要过度设计

- 先读再写：`HANDOFF.md`、`docs/07-implementation-design.md`、`spec/v1/*`、`fixtures/v1/*`、ADR。
- **优先改现有模块**（`apps/web`、`apps/api`），禁止为 demo 另立第二套前后端或平行契约。
- 不为假想需求加泛化 API、万能 registry、过度 DI / 策略工厂。
- 发现规格冲突：先写 ADR，再改权威文件；**禁止悄悄降低验收门禁**。
- 不提交密钥 / `.env`；不把 fixture 当真实生产血缘。

## 3. 权威对照

| 主题 | 权威 |
|---|---|
| 产品语义 / 阶段 | `HANDOFF.md`、`docs/07-implementation-design.md` |
| HTTP / 导入契约 | `spec/v1/openapi.json`、`spec/v1/import.schema.json` |
| 视觉 / 交互（树画布） | `reference/open-design/`（`docs/07-visual.md`、`06-interaction.md`、原型 `lineage-map-v2.html`） |
| 工程栈 | **Java 8 + Spring Boot 2.7 + Vue 2 嵌入壳**（ADR 0001）；HANDOFF 里 React/Java 21 是设计默认，**不改产品语义**时以 ADR 为准 |
| 验收与限制 | `docs/IMPLEMENTATION_ACCEPTANCE.md` |

树视图最终形态：**桌面列式 hops + NodeCard + SVG 边**（默认 depth=1；「全部 · 聚类」走 overview）。**禁止**嵌套列表树、禁止 focus 一次铺 500+ 叶子。

## 4. 桌面优先

- 树 / 画布验收目标 ≈ **1280+ 桌面**。
- **不做**移动端优先布局；不把 390 / 窄屏当树画布交付义务（协议已去掉 390）。
- 截图与视觉证据遵循 `evidence/implementation/p4-visual/PROTOCOL.md`。

## 5. 生产边界（必须如实）

- 本地 fixture + 可销毁 PG 绿灯 **≠** 生产可上线。
- 真实数据、企业 SSO、生产备份 / PITR 未验证时写 **BLOCKED**，**禁止**写「可上线」「生产就绪」等无条件结论。
- 嵌入鉴权以最小 stub（如 `X-Embed-Groups` / 配置 CSRF）为限，不冒充企业身份方案已完成。

## 6. 协作与交付

- Notion Tasks 为任务源：认领一条未开始任务 → 进行中 → 完成；阻塞时 Status=Blocked 并写 Note，ping 用户与项目经理。
- 开分支 + PR，**不直接 push `main`**；合入由用户拍板。
- Prefer 中文在项目房间沟通；PR / 证据路径写清楚。
- 始终标出：边界条件、生产风险、回滚 / 回退。

## 7. 一句话自检

改动前问：**这是在完成嵌入式血缘探索，还是在搭一个用不上的平台？** 若是后者——停，删掉，回到 OpenDesign / HANDOFF / spec。
