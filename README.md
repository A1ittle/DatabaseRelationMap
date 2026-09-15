# 程序血缘关系地图 · 完整设计与编码交接 v1.0

基于 Open Design 原型，并参考 DataHub、OpenMetadata、OpenLineage、Marquez、React Flow 和 G6 的官方文档及固定提交源码。2026-09-14。**设计与编码规格已交付；业务代码尚未实现。**

## 先看这三份

| 文档 | 用途 |
|---|---|
| [HANDOFF.md](HANDOFF.md) | 给编码 agent / 开发者：阅读顺序、工程结构、阶段任务、门禁与续跑 |
| [完整实现设计](docs/07-implementation-design.md) | 已收敛的架构、技术栈、数据模型、算法、API、前端状态、权限及部署 |
| [开源参考与采用理由](docs/06-opensource-research.md) | 六个项目的已核实能力、源码证据、借鉴点与复用边界 |

HANDOFF 设计默认方案仍为 React + TypeScript + React Flow；Java 21 + Spring Boot 4.1.x；PostgreSQL 17（见 [HANDOFF.md](HANDOFF.md)，不改产品语义）。工程栈按项目管理要求为 **Java 8 + Spring Boot 2.7.x + Vue 2 嵌入式壳**，见 [ADR 0001](docs/adr/0001-java8-vue2-embedded-vs-handoff.md)、[依赖基线](docs/DEPENDENCY_BASELINE.md) 与 [本地运行](docs/RUNBOOK.md)。PostgreSQL 由 SRE 提供，本仓库不提交密钥或 `.env`。P4：四视图 + URL 恢复 + 数据导入/发布页与异常态 + 视觉冻结（fixture 截图协议，见 [进度](docs/IMPLEMENTATION_PROGRESS.md)）。P5 嵌入式交付验收（本地已通过 / 未验证 / BLOCKED，**不是**生产可上线）见 [验收与限制](docs/IMPLEMENTATION_ACCEPTANCE.md)。

## 机器可读规格与示例

- [OpenAPI 契约](spec/v1/openapi.json)：14 个路径、32 个数据结构。
- [导入 JSON Schema](spec/v1/import.schema.json)：规范化全量 scope 数据。
- [数据库 DDL 设计](spec/v1/storage.sql)：待转为并验证实际迁移，本轮未在数据库执行。
- [输入样例](fixtures/v1/import.json)、[人工指定期望](fixtures/v1/expected.json)、[查询响应](fixtures/v1/query-response.json)、[路径响应](fixtures/v1/path-response.json)。
- [校验工具](tools/validate_design.py)与[本轮验证报告](evidence/design-v1-verification.json)。

## 产品与验收材料

[产品流程](docs/01-project-design.md)、[领域模型](docs/02-lineage-model.md)、[架构背景](docs/03-architecture.md)、[接口阅读索引](docs/04-api-and-state.md)、[22 个验收场景](docs/05-delivery-and-acceptance.md)、[原型核对问题](docs/00-prototype-review.md)。

旧文档保留背景；发生差异时按完整实现设计和 spec/v1 编码。核心规则：仅下游、Java 展示终点、主树与跨支完整分类、证据属于关系、固定查询快照、服务端先授权再遍历。新版 children 只分页候选对象，projection 统一返回完整有界画布，替代早期增量连线协议。

## 原型与来源

- [离线样式修订后的原型](reference/offline-style-update/lineage-map-v2.html)及[完整 CSS](reference/offline-style-update/lineage-map.css)。
- [最初保留的原型](reference/open-design/lineage-map-v2.html)、[原开发手册](reference/open-design/program-lineage-handoff/handbook.html)仅作历史参考。
- 两个 reference 目录的 source-manifest 固定原始文件 hash；本轮保持未改。
- [开源固定提交/许可证来源清单](evidence/opensource-sources.json)。无第三方业务源码复制，React Flow Pro 示例不作为本项目实现依赖。

## 交付边界

已验证设计契约结构、示例及图不变量、错误输入拒绝、文档链接与原型副本哈希；原型参考样例回归为 10 个可达对象、9 条主边、3 条跨支。均不能代替业务应用验收。

真实关系数据、企业登录/对象权限映射、目标部署环境仍未提供。设计采用明确默认值使本地编码可继续；真实接入、性能、SSO 和生产部署需后续单独验收。嵌入式工具在 disposable PG + fixture 上的本机绿灯见 [docs/IMPLEMENTATION_ACCEPTANCE.md](docs/IMPLEMENTATION_ACCEPTANCE.md)；该文不声明生产可上线。

## 本地启动

前端工作台与导入页、API 健康检查可在无数据库时启动；导入/查询需要 PostgreSQL。命令见 [RUNBOOK](docs/RUNBOOK.md)。

```bash
cd apps/web && npm install && npm run dev     # http://127.0.0.1:5173
cd apps/api && JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw spring-boot:run
# http://localhost:8080/api/health → {"status":"ok"}
```
