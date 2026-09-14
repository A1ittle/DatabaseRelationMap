# 程序血缘地图 · 编码 Handoff v1.0

给接手编码的开发者或 agent。目标是在当前目录实现可运行的 V1：**规范数据导入→校验并发布快照→搜索根表→四种视图探索下游→查看对象和关系证据**。本文件交接的是后续编码工作；截至本次设计交付，业务前后端尚未实现。

## 1. 开工先读与采用基线

先读 [完整实现设计](docs/07-implementation-design.md)，再读 [接口契约](spec/v1/openapi.json)、[导入格式](spec/v1/import.schema.json)和[指定输入输出](fixtures/v1/expected.json)。做 UI 时参考 [离线原型](reference/offline-style-update/lineage-map-v2.html)及其同目录完整 CSS；做业务验收时读 [A01–A22 场景](docs/05-delivery-and-acceptance.md)。

涉及技术取舍或开源复用时读 [开源研究](docs/06-opensource-research.md)：已核对 DataHub、OpenMetadata、OpenLineage、Marquez、React Flow 和 G6 的官方材料及固定源码提交。开源产品不替代本项目的严格下游、Java 终点、主跨分类、快照和权限规则。

**v1.0 决策优先于旧稿。** `reference/open-design/program-lineage-handoff/LOCKED-RULES.md` 是原型历史材料，环兜底和按端点归类等已知问题不能作为生产要求复制。`docs/04` 的 POST children 增量协议被替换为 GET children 候选分页 + POST projection 完整替换；具体字段只以 spec/v1 为准。

## 2. 明确默认值与外部缺口

默认：React/TypeScript/Vite、React Flow 开源核心、自有分列布局；Java 21/Spring Boot 4.1.x/Maven/JDBC/Flyway；PostgreSQL 17；单实例同源部署；规范 JSON 全量 scope 导入；Java 默认 class；OIDC 服务端会话。开始编码时固定可用发行版与锁文件，并在 `docs/DEPENDENCY_BASELINE.md` 记录版本、来源和选择理由。

用户尚未提供真实关系文件、目标内网环境、企业 OIDC 和授权映射。先完成明确标记 DEMO 的本地闭环；缺失真实数据或认证配置时，将对应试点验收记为 BLOCKED，继续能独立完成的编码。不要编造真实连接器或把 fixture 当真实关系。若后续用户提供不同栈，先记录 ADR 和契约兼容方案，再调整工程，不改变已确认产品语义。

自动 SQL/Java 源码解析、字段血缘、跨 scope 合成、SCC 组内图、多实例、导出审批不在本轮编码目标内。

## 3. 目录与修改范围

建议工程骨架：

```text
apps/web/                   React 页面、组件、状态、布局、客户端测试
apps/api/                   Spring Boot、Maven Wrapper、API 与数据库集成
apps/api/.../domain/graph/   纯 Java 算法
apps/api/.../application/    查询上下文、导入和发布
apps/api/.../infrastructure/ JDBC、授权、OIDC、缓存
apps/api/.../interfaces/     HTTP DTO 和控制器
spec/v1/                    权威接口和导入格式、迁移参考
fixtures/v1/                固定输入输出，不是服务实现
tests/acceptance/            API/UI/并发/权限/负向验收
deploy/                     本地部署与生产配置模板
docs/                       实施记录、运行说明、ADR 与验证报告
```

可新增/修改上述应用、测试、部署与新增工程配置；保留当前用户文件，先检查实际目录和 Git 状态。`reference/**`、原有 evidence 和指定 expected fixture 是审查基线，保持原样；新增验证写 `evidence/implementation/`。发现规格错误，先新增 ADR 描述输入、预期和冲突，明确修订权威文件后再实现，不能悄悄降低标准。

只在本地工作。提交、推送、外部发布、生产接入和生产配置修改由后续用户明确授权。依赖安装限本工程所需、使用官方发行来源，不执行第三方仓库中未经理解的安装脚本。

## 4. 实施顺序与每阶段完成条件

### P0：环境与契约基线

核对 Node/包管理器/Java/Maven/PostgreSQL 或容器可用性；验证当前设计检查；建立工程、锁文件和本地配置。输出 `docs/IMPLEMENTATION_PROGRESS.md`，写清目标、阶段、实际环境与缺口。

完成条件：开发环境可启动空前后端与临时数据库；契约生成客户端类型；真实可执行命令已写进包脚本/Maven 生命周期，所有工具版本可查。文档里的计划命令在本阶段前都不视为存在。

### P1：领域算法与样例

先写反例验收，再实现授权后 BFS、Java 截断、自环预处理、环检测、DAG 主父排序、relationId 分类、统计和最短路径。使用邻接表和显式队列，避免对所有边重复全扫描。

完成条件：`fixtures/v1/import.json` 得到 `expected.json` 的人工指定结果；旧样例 10/9/3 可兼容或有明确稳定键映射说明；平行关系、自环、环尾部、同图重排、Java 出边、隐藏中间对象和路径 unknown 反例全部有断言。不能直接把生成器输出作为自己的预期值。

### P2：真实存储、导入、权限和查询 API

将 storage.sql 转为实际 Flyway 迁移，在可销毁 PostgreSQL 上验证。实现 batchKey 幂等、schema/语义校验、失败报告、原子发布 CAS、查询上下文、稳定游标、授权失效和有界缓存。OIDC 开发适配与生产配置分开。

完成条件：通过 HTTP 导入并发布样例，服务重启后数据可用；错误批次不改变 activeSnapshot；并发发布仅一个成功；直接伪造对象 ID 不泄漏数据；API 响应通过契约验证。集成测试实际连接数据库，不能 mock JDBC 后声称存储完成。

### P3：下游树与详情纵切

先实现搜索→建查询→默认一层→选中详情→children 分页→完整 projection→跨支列表定位→换根。节点和边一次替换，revision 避免旧响应回写；图布局与业务算法分离。

完成条件：真实后端返回的数据能操作；不同展开顺序得到同样跨支；连续展开超过三层；两类同端点关系不丢失；统计显示实际绘制数量；主树超预算保留原图并给出明确操作。

### P4：四视图、导入页和异常状态

实现按层×类型总览、影响清单、最短路径、证据详情、URL 恢复和导入/校验/发布页面。环查询显示清单/路径，未采集、空数据、来源不完整、请求失败及权限变化分别处理。

完成条件：A01–A22 场景均有对应测试/手工证据；所有显示值来自真实 API；清单/路径不从屏幕节点反推；键盘、长名称、窄屏详情可操作。图和业务说明与原型一致的部分逐项保留，修订点按 v1.0 验收。

### P5：规模、部署与交付

补 500+ 可达、高扇出、密集跨支、长链、并发返回、权限撤销、导入失败恢复。实际记录冷/热查询和渲染耗时。打包离线静态资源，配置日志/指标/健康检查，演练备份恢复。

完成条件：文档中的本地启动和验收命令实际运行成功；有 API+数据库+UI 完整链路证据和已知限制；性能报告注明设备/数据/并发。真实数据、SSO 或生产环境仍缺失则分开报告，不能给出“生产可上线”的无条件结论。

## 5. 必须保持的不变量

- 原事实保留，只有展示查询停止 Java 出边；侧输入不补入当前根。
- DAG 非根恰一条主父 edgeId，tree/cross 完整互斥；children 按对象去重。
- 环不强行改边；所有最短路在原授权查询图求解，跨支可被选用。
- 同快照、同权限、同算法、同输入集合，主父和计数与分页/数组顺序无关。
- children 是候选分页；projection 返回当前节点集的全部合法关系。节点/边预算超出明确失败，不随机漏边。
- 权限先于遍历和计数；查询绑定当前会话和策略版本；页面不混快照。
- 来源不完整、计算截断和画布折叠分别表达。未查全不等于无影响。

## 6. 验收命令与证据

当前已存在且本轮运行的设计校验命令：

```bash
python3 tools/validate_design.py
node reference/open-design/program-lineage-handoff/spec/verify-tree.mjs
node evidence/probe-reference.mjs
```

第一条需要 Python jsonschema（当前环境已有）。这些只验证设计契约、示例、原型回归和已知问题，**不验证后续应用已经完成**。

P0 需要创建并实际运行的工程命令：前端 lint/typecheck/test/build，API 测试与数据库集成 verify，Playwright E2E，完整验收入口。具体以生成后的 package.json / Maven 配置为准；交付 `docs/RUNBOOK.md` 列实际命令、端口、环境变量、启动/停止和故障恢复方法。

验收不得通过 skip、删除断言、只测 mock、吞错误或将失败写成日志来“绿灯”。至少制造一次无权限请求、一次悬空端点导入和一次旧 projection 响应，证明系统正确拒绝/丢弃；基准样例外增加独立反例。schema 合法和旧回归通过都不能代替业务验收。

## 7. 续跑与交付格式

每阶段更新 `docs/IMPLEMENTATION_PROGRESS.md`：已完成文件、实际命令与结果、下一步、外部阻塞。新会话先读本文件、进度和最新 ADR，只复查当前修改需要的门禁。连续失败先形成最小复现和原因，再更换方案，不大范围回退用户已有工作。

最终交付应用源码、依赖锁、迁移、实际部署说明和 `docs/IMPLEMENTATION_ACCEPTANCE.md`。验收报告逐项区分：本地已通过、真实试点已通过、未验证、BLOCKED。代码完成的判断由可复跑证据支持；不能只依据执行 agent 自评。
