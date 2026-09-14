# 开源调研与采用决定

研究日期：2026-09-14（Asia/Shanghai）。结论来自官方文档、JSON Schema 与指定提交源码的静态阅读；没有启动这些产品、跑真实性能对比或验证生产连接器。下列“采用”是本项目设计决定，不是原项目保证。本次未复制第三方业务源码。

## 1. 结论

保留现有“表入口、仅下游、Java 展示终点、主树＋跨支、四种视图”。借鉴 DataHub、OpenMetadata 的渐进探索和影响清单，借鉴 OpenLineage/Marquez 的身份、证据及运行版本概念；V1 自建模块化查询服务，不部署整套元数据目录产品。图引擎采用 React Flow 开源核心，G6 作为有实测理由时的替代方案。500+ 是可达对象规模，不能等同于一次绘制 500 张复杂卡片。

## 2. 项目逐项核实

| 项目 | 已核实能力与借鉴点 | 不能直接套用的差异 / 决定 |
|---|---|---|
| DataHub | Explorer 与 Impact Analysis 分开；可以继续展开任务上下游，按钮显示依赖数量。手工和自动来源可能覆盖冲突。[D1] | 通用跨平台数据血缘不等于存储过程/Java 调用地图。**时间选择器过滤最新版边，不恢复历史血缘**；因此本项目必须独立实现不可变 snapshotId。借鉴交互，不引入完整目录服务。 |
| OpenMetadata | 节点上下游展开、每层数量控制与分页、图/影响表切换；点边查看 SQL 等详情。[O1] Schema 包含 from/to、SQL、pipeline、source 和创建/更新时间。[O2] | 源码注明 lineageDetails 面向表到表边，不能直接覆盖 reads/writes/calls 多类型平行关系。来源类别和时间字段也不证明全图一致快照。借鉴“证据属于边”及渐进分页；保留本项目 relationId 与 evidence 列表。 |
| OpenLineage | Dataset、Job、Run 分离，以 namespace/name 定位对象；运行事件携带 inputs/outputs；同一 Run 可分阶段补充数据，同名 facet 更新整体替换。[L1] 当前官方模型还区分静态 JobEvent、DatasetEvent 与 RunEvent。[L2] | 它是采集事件规范，不是工作台或通用静态 Java 调用分析器。Java 类/方法是否映射为 Job 必须由接入契约声明，不能强套。V1 预留适配器，不以安装 SDK 代替关系采集。 |
| Marquez | lineage API 支持根标识和 depth，返回节点及 inEdges/outEdges；源码由 Job 输入/输出构造 Dataset→Job→Dataset，另外提供基于运行及数据集版本的上游追踪。[M1][M2] | 普通图查询源码用共享输入/输出寻找相关 Job，且读取 current job version，[M3] 不是本项目“严格沿有向边只向下游”的现成实现。借鉴有界查询与版本区分，不复用其遍历 SQL。 |
| React Flow | 核心提供受控节点/边及只读交互开关；onlyRenderVisibleElements 可限制视口内绘制，但注释说明存在额外开销。[R1] 官方建议 memo、隔离状态订阅、折叠大树和简化样式。[R2] | 采用开源核心承载卡片、边、缩放与选择。服务端决定主父/layoutRank，前端自有确定性分列坐标。库不负责领域语义、查询权限或服务器分页；未测得“500+ 必定流畅”。 |
| G6 | collapse-expand 源码支持 node/combo、点击/双击、回调及 align，实际调用 graph.expandElement/collapseElement。[G1] | 其折叠行为不能自动解决后端分片、主父稳定性或跨支补边。当前产品是 React 信息卡片工作台，先选 React Flow 可减少界面适配；若真实高密度需求突破可见预算，再以相同用例比较 G6，不能凭 Canvas 标签宣称更快。 |

OpenLineage parent facet 表示“谁启动了这次 Job Run”，不是通用 Java 方法 calls，更不证明当前表的数据流入被调程序。[L3] Dataset version 可指存储系统版本（例如 Iceberg snapshot），Job 源码位置可带 Git SHA；这些版本与本项目“多来源合成的地图快照”分开存储。[L2]

## 3. 转成编码规则

1. **四视图共享查询上下文。** 主树用稳定主父展示结构；跨支关系按 relationId 检索未成为主边的关系；影响清单给出授权可达对象及统计；调用路径展示原查询图上的最短关系路径之一。借鉴图/清单双入口，但后两者不应从当前屏幕节点推算。路径中含 calls 时注明潜在调用关联。
2. **分页候选与投影分开。** `children` 只返回可展开的候选对象；`projection` 根据完整展开意图，返回一个有界节点集合及该集合内应呈现的完整边集合。示例：A、B 分别分页展开后才出现 A→B；若只合并各自“新子边”就可能漏边。必须在同一 snapshotId/算法/授权上下文计算闭包，并以请求序号拒绝旧响应覆盖。此为本项目推导，不声称上述项目使用同一协议。
3. **边闭包不能取消边预算。** 100 个常用可见对象、200 个上限只是初始设计预算；平行边和高汇合可能让边远多于节点。超边预算应返回明确状态，引导减少可见集合，或采用有计数及明细入口的平行边聚合；不得随机丢跨支后仍宣称完整。对比测试须含并发展开、反序返回、平行关系、自环、长名称与高扇出。
4. **环是数据特征，不能套树编辑器“禁止连环”。** Marquez 的运行追踪 SQL 有深度边界及直接自引用排除，[M3] 不等于通用 SCC 检测。本项目保留事实环，先移除展示自环再检测有向环；有环时主树不可用，清单与 visited BFS 路径仍可用。后续 SCC 聚合需要单独设计组内展开，不由布局库任意断边。
5. **来源与版本不能混用。** 原始事实保留来源定位、来源版本、采集时间、解析/观察方式；人工确认与机器采集并存。快照原子发布，进行中的展开不自动换版。OpenLineage 事件增补和 facet 替换不能被导入器误读为“没出现的对象全部删除”。

数据接入适配也应受边界约束：DataHub/OpenMetadata 若已是企业事实来源，接入它们的稳定实体标识和有向关系，再映射到本项目对象模型；不把对方节点当前显示位置当作血缘层级，不直接继承其全局计数。OpenLineage 可把输入映射为 Dataset→程序、输出映射为程序→Dataset，并为每条边保存事件 ID 与运行 ID；Java 出边只在展示查询阶段截断，原始输出事实照常保留。Marquez 的普通图结果与运行版本结果应使用不同适配模式，不拼成一个未标明时间范围的“完整事实图”。

以上适配方式是设计建议。开工需要逐来源提供至少一个实际对象和关系样本，核对重命名、环境命名空间、删除表达、版本稳定性、访问权限以及未解析对象的表示方法。连接器返回空数组可能是无关系、无权限、不支持或本次未采集；只有来源契约能区分，不能由前端猜测。未经核验的字段保留 unknown，并在覆盖范围中说明。

## 4. 许可证与复用范围

核对各固定提交根 LICENSE：DataHub、OpenMetadata、OpenLineage、Marquez 为 Apache-2.0；xyflow、G6 为 MIT。[LICENSE 来源清单](../evidence/opensource-sources.json) 逐条包含原文件永久链接、SHA-256 与研究提交；这不是对子模块、发行包、商标或部署方式的法律结论。真正引入依赖时以锁文件生成依赖清单并保留通知。

特别区分 **React Flow 核心 MIT** 与 **Pro 内容**：官方 Expand and Collapse 示例明确标注 xyflow Pro License；该许可另列订阅取得、使用与分发条件。[R3][R4] 本项目不依赖购买 Pro，不下载或复制 Pro hook/模板；展开集合、过滤、布局与请求状态自行按本项目契约实现。不要把“库开源”推断成“网站全部示例均为 MIT”。

## 5. 研究限制与开工前验证

本次未验证任何项目连接当前实际数据源的能力；尚无真实数据、浏览器/硬件和团队约束，不能给出性能通过、解析精度或工期承诺。固定提交用于可追溯阅读，不代表推荐采用这些未发布 HEAD。开工时固定实际发行版本和依赖锁；先用 500+ 可达对象、100/200 可见对象及高边密度样本，测首次绘制、展开、切视图、缩放和内存。React Flow 的视口裁剪应分别开/关测量；不得仅以启用配置作为验收证据。

## 6. 官方证据索引

[D1]: https://github.com/datahub-project/datahub/blob/6ece48b05a2a260e6c9df8aec4be5f7f1e7deca6/docs/features/feature-guides/lineage.md
[O1]: https://docs.open-metadata.org/v2.0.x/how-to-guides/data-lineage/explore
[O2]: https://github.com/open-metadata/OpenMetadata/blob/68606d705a225f14dd87847f2be733da5fef38db/openmetadata-spec/src/main/resources/json/schema/type/entityLineage.json
[L1]: https://github.com/OpenLineage/OpenLineage/blob/9ac19298c8ef518ed10c7a3af547c8ac86c2e012/spec/OpenLineage.md
[L2]: https://openlineage.io/docs/spec/object-model/
[L3]: https://openlineage.io/docs/spec/facets/run-facets/parent_run/
[M1]: https://marquezproject.ai/docs/api/get-lineage/
[M2]: https://github.com/MarquezProject/marquez/blob/180f37b22387146187af1ef0279e3ee1d1ccd789/api/src/main/java/marquez/service/LineageService.java
[M3]: https://github.com/MarquezProject/marquez/blob/180f37b22387146187af1ef0279e3ee1d1ccd789/api/src/main/java/marquez/db/LineageDao.java
[R1]: https://github.com/xyflow/xyflow/blob/0a1f9575b25679f2880175de8d3eae21aedde921/packages/react/src/types/component-props.ts
[R2]: https://reactflow.dev/learn/advanced-use/performance
[R3]: https://reactflow.dev/examples/layout/expand-collapse
[R4]: https://xyflow.com/pro-license
[G1]: https://github.com/antvis/G6/blob/7b7ff8e2b52609486840963dc1608d9f565e7f66/packages/g6/src/behaviors/collapse-expand.ts
