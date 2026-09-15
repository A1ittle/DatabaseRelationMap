# 接口与前端状态契约 v1.0

本文件为阅读索引，字段唯一来源是 [OpenAPI](../spec/v1/openapi.json)，语义唯一来源是[完整实现设计第 6–7 节](07-implementation-design.md)。原 v0.1 的“children 直接返回增量节点和边”协议已废弃；历史稿保存在 v0.1 压缩包中。

## 接口职责

| 能力 | 接口 |
|---|---|
| 表入口/当前下游搜索 | GET `/api/lineage/search` |
| 固定根、快照和授权上下文 | POST `/api/lineage/queries` |
| 完整有界画布投影 | POST `/api/lineage/queries/{qid}/projection` |
| 主树直接子对象分页 | GET `/api/lineage/queries/{qid}/children` |
| 层级与类型数量簇 | GET `/api/lineage/queries/{qid}/overview` |
| 簇成员分页 | GET `/api/lineage/queries/{qid}/clusters/{cid}/members` |
| 全范围下游清单 | GET `/api/lineage/queries/{qid}/impact` |
| 对象详情 | GET `/api/lineage/queries/{qid}/nodes/{id}` |
| 主/跨支/未分类关系及端点 | GET `/api/lineage/queries/{qid}/nodes/{id}/relations` |
| 最短关系路径 | GET `/api/lineage/queries/{qid}/path` |
| 关系证据 | GET `/api/lineage/queries/{qid}/relations/{rid}/evidence` |
| 提交规范化全量批次 | POST `/api/imports` |
| 查看导入状态与问题 | GET `/api/imports/{runId}` |
| 原子发布并比较当前版本 | POST `/api/imports/{runId}/publish` |

14 个路径为领域 API；登录回调、会话退出和健康检查属于工程基础端点，编码阶段按 Spring Security/OIDC 和运行规范配置，不伪装成血缘接口。

## 请求流程

1. 搜索到表后建 query，上下文返回默认首层投影。
2. 展开节点调用 children，结果只进入候选对象缓存。
3. 按当前展开意图得到 candidateIds，以新 revision 调 projection。
4. projection 返回全部当前节点与其内部合法边；前端一次替换画布。
5. 详情、总览、清单、路径都使用同一 queryId。切换模式不重新生成领域关系。
6. 换根、权限变化、手动刷新或上下文过期时整体重建；旧响应不能写入新上下文。

投影超过 200 节点/2000 边（包括定位补祖先）时明确报错，保留前一合法图。此行为消除了随机截边或不同展开顺序漏边的问题。

## UI 状态与 URL

URL 保留 `seedId,snapshotId,mode,selectedId,layoutDepth,types,pathTargetId`；不保存 queryId 作为可转交凭据。expanded 页、候选集合、定位 pin、视口在会话内。恢复链接先重新鉴权建 query，再定位 selectedId；没有 snapshotId 则显示“最新版本”。同一 `seedId` 下 `snapshotId` 变化（含清空/省略以跟随活动快照）必须重建 query，不得沿用旧查询与旧投影。参数名未变。

`layoutDepth` 是前端首次选取候选对象的主树层数（1/2）；全部走 overview。加载第二层按父节点候选分页进行并受绘制预算控制，不新增一个隐含无限数据接口。

错误语义：未采集与采集后无关系不同；未知覆盖和计算超限不等于无路径；环使树分类不可用，清单/路径和关系详情仍可用。环查询中关系 `kind=unclassified`，禁止继续显示“跨支数量 0”掩盖没有分类的事实。

## 已完成校验与限制

本轮用官方 OpenAPI 3.1 文档 schema、JSON Schema 2020-12 和指定样例做检查，命令 `python3 tools/validate_design.py`。检查结果见[验证报告](../evidence/design-v1-verification.json)。这验证格式与设计样例一致性，不证明后端已实现。
