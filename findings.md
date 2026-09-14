# 证据与发现

- 2026-09-14：当前目录初始为空。
- Open Design 工具可调用，但当前无活动项目上下文，准备按项目名称定位原型。
- 历史记忆检索未找到本项目记录；其他项目设计不作为本项目需求依据。
- 原生 Open Design 窗口找到“程序血缘地图设计”，项目 b9980d96-beae-4205-acd0-07f5cae39c18；当前工具项目列表为空与应用界面不一致，采用界面提供的精确本地文件路径读取。
- 用户设计对话明确：只展示表及其下游；展示下游之间的关联；Java 为终点；规模陈述为 500+ 节点、300+ 边。原型 520 对象为生成数据，不是真实规模实测。
- 界面可见最终 lineage-map-v2.html 与 program-lineage-handoff 开发交接包。既有助手关于“已验证”“锁定算法”的说法仍需源码核对，不能代替本轮证据。
- 已完整保留 24 份源文件并生成逐文件 SHA-256；顶层原型与交接包原型逐字节一致。见 reference/open-design/source-manifest.json。
- 原始回归本轮通过：reach=10, tree=9, cross=3；small-graph.json 实为 17 个节点、18 条边。
- 独立边界探针：平行关系都成为 tree 且 children 重复；自环导致后继 rank=1 且无主父；环的尾部节点同样失去主父；调换根出边顺序可改变并列节点主父。见 evidence/reference-probes.json。
- 原生窗口 AX 和截图已观察焦点视图：摘要显示 44 可见，但 1 根 + 本层 CAP=5 卡片，其余 38 折叠，计数口径不一致。没有完成所有 UI 交互验收。
- 核对了官方 G6 展开收起、React Flow 性能建议及 OpenAPI 3.1 规范，用于候选方案和契约缺口判断；没有复用旧调研星标或性能结论。

## v1.0 开源参考与编码交接
- research 技能要求的独立调研已完成 6 仓库固定提交、13 份源码/许可证、7 个官方网页；见 docs/06 与 evidence/opensource-sources.json。
- DataHub 日期过滤不是历史快照；Marquez 普通相关 Job 查询不能直接作为严格下游；React Flow 开源核心与 Pro 示例分开处理。
- 默认 React/TypeScript/React Flow + Java21/Spring Boot4.1.x + PostgreSQL17，具体依赖尚未安装或锁定。
- 协议收敛：GET children 只分页候选，POST projection 一次返回整个可见子图及完整边集合；边预算 2000、节点上限 200。
- 14 个 API 路径、32 个结构；官方 OpenAPI 文档 schema 检查、JSON Schema 检查、3 个响应/输入样例及图不变量、3 个错误输入拒绝均通过。
- DDL 是设计草案未在 DB 执行；无业务实现、真实接入、UI运行、性能或生产部署验证。
