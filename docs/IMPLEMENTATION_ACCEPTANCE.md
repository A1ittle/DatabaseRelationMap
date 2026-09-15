# 嵌入式交付验收与已知限制

P5 slim（Engineer）：本机嵌入式壳 + Java 8 API 的交付验收摘记。

- **不是**规模、备份、生产主机验收（SRE 任务）。
- **不是**「生产可上线」或无条件生产就绪声明。
- 本会话只用 [fixtures/v1](../fixtures/v1/) 样例；**不得**把 fixture 当作真实客户血缘。

会话时间：2026-09-15T01:01Z–01:03Z UTC。运行时 `HEAD`：`8fca1adf0eb9fd1529f925293b58203d3670fa56`（`main@8fca1ad`）。分支：`feat/p5-embedded-acceptance`。逐条命令、状态码与 `requestId` 见 [evidence/implementation/p5-embedded-acceptance.txt](../evidence/implementation/p5-embedded-acceptance.txt)。

---

## 本地已通过

本会话实际执行（或明确引用 `evidence/implementation/` 既有记录）。JDK 路径：`JAVA_HOME=/home/box/tools/jdk8u504-b01`。

### 设计门禁

| 命令 | 退出码 | 摘要 |
|---|---|---|
| `python3 tools/validate_design.py` | 0 | `status PASS`；14 paths / 32 schemas |
| `node reference/open-design/program-lineage-handoff/spec/verify-tree.mjs` | 0 | `OK seed=ods.trade_order reach=10 tree=9 cross=3` |
| `node evidence/probe-reference.mjs` | 0 | `fixtureCounts nodes=17 edges=18` |

P0 基线摘记仍在 [validate_design.txt](../evidence/implementation/validate_design.txt)、[verify_tree.txt](../evidence/implementation/verify_tree.txt)、[probe_reference.txt](../evidence/implementation/probe_reference.txt)。本会话重新跑通，门禁未下调。

### `apps/api` `./mvnw test`（JDK 8）

```text
cd apps/api && JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw test
# Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS; exit 0
```

含真实 JDBC：`QueryApiJdbcTest`（fixture 导入发布后 search / query / children / projection / path）、`ImportPublishJdbcTest`。无 JDBC mock。健康检查在无数据源时仍为 200；无线上数据源时 lineage 为 503（`LineageApiApplicationTests`）。

### `apps/web` `npm test` + `npm run build`

```text
cd apps/web && npm test && npm run build
# vitest 1.6.1: 11 files, 42 tests, 0 fail
# vite build: 41 modules; dist/index.html; exit 0
```

### 本地 PG + API 全链路（fixture）

环境：`deploy/docker-compose.yml` 中 `lineage-pg-local`（`postgres:17`，healthy，`:5432`）；已有 Java 8 进程监听 `:8080`。CSRF：`X-CSRF-Token: dev`。未发送 `X-Embed-Groups`（open mode，授权当前活跃快照全集）。

| 步骤 | 状态 | requestId |
|---|---|---|
| `GET /api/health` | 200 | `a09e9903-0841-4896-8528-aa550e6f6c3f` |
| `POST /api/imports`（`fixtures/v1/import.json`） | 202 | `449f50b8-2561-4155-8dbe-3c98708d34c5` |
| `GET /api/imports/{runId}` | 200 | `7e9288f1-6561-45f9-8765-04433fa05263` |
| `POST .../publish` `expectedActiveSnapshotId=null` | 409 | `986d50a6-5982-447a-8f6e-0235706f0eef` |
| `GET /api/lineage/search?q=root` | 200 | `d81aa9aa-5d89-4a96-a040-7b0d3169aae3` |
| `POST /api/lineage/queries` `seedId=root` | 201 | `f3636b22-e17f-4646-a6f4-b6ae0dfffa0a` |
| `GET .../children?parentId=root` | 200 | `6e8df55d-c17e-4a86-86e3-a2a8c6e29de2` |
| `GET .../overview` | 200 | `aea84116-0f0e-4b7c-a0b1-b9abd313d139` |
| `GET .../clusters/0:table/members` | 200 | `4e61fa47-33b8-477b-ab29-2a0e3f886ed9` |
| `GET .../impact` | 200 | `b1a8ac12-3306-4004-986e-a8e0bdf72c5d` |
| `GET .../path?targetId=java-j` | 200 | `cf81227a-9bb3-4f06-b528-8819c9cf0b93` |
| `POST .../projection` | 200 | `7210bcdc-a29d-475c-81ef-1a59e9dc9be2` |
| `GET .../relations/e02/evidence` | 200 | `d3e61003-2d5e-4f6e-b82a-73e581b17710` |
| `POST /api/lineage/queries` 无 CSRF | 400 | `2641692b-03f1-4375-af73-e25fb29e5d6c` |

说明：

- 导入 202 返回已发布 run（`runId=2f2a8f87-…`，`snapshotId=7238bfd2-…`，`status=published`）：同 `batchKey`+payload 的幂等回放，不是新的客户数据。
- 对已发布 run 再 `publish` 且 `expectedActiveSnapshotId=null` 得到 `PUBLISH_CONFLICT` 409，符合 CAS。空库上的首次导入→发布由本会话 `ImportPublishJdbcTest` / `QueryApiJdbcTest` 覆盖。
- 查询：`downstream=5`，默认投影 `root, view-a, proc-s`；路径 `root -e07(cross)-> java-j`；证据 `sourceRef=fixture:manually-authored`。
- `cd apps/web && ./scripts/demo-deep-expand.sh` 退出 0：展开 `view-a → proc-b → table-c → java-j`，`maxRank` 到 4。

JDBC 测试会 `TRUNCATE` 目录表；上表链路跑在 `./mvnw test` **之前**。测试后长驻 `:8080` 进程可能看不到该快照。

---

## 未验证

本会话**没有**作为嵌入式交付退出条件执行的项目：

| 项 | 说明 |
|---|---|
| A01–A22 全矩阵 | 产品场景见 [05-delivery-and-acceptance.md](05-delivery-and-acceptance.md)。本机只跑了 fixture 纵切与 P1 反例套件，不是 22 条人工验收。 |
| Playwright 浏览器 E2E 套件 | 仓库没有独立 E2E 目录。仅有 P4 视觉冻结脚本 `npm run capture:p4-visual`（Chrome + fixture 截图，见 [p4-visual-freeze.txt](../evidence/implementation/p4-visual-freeze.txt)）；本会话未重跑截图。 |
| 性能时延 | 未测 p95 查询/首屏/展开。设计目标仍是建议值，不是实测。 |
| 500+ 规模 / 高扇出 | fixture 远小于投影预算 200 对象 / 2000 关系。规模与备份交给 **SRE**。 |
| 同域反向代理生产形态 | 本机 Vite `:5173` 代理 `/api` 与跨端口 CORS 已用于开发；未搭 nginx/Caddy 同域静态托管。 |
| `demo-header` 与真实 deny 矩阵的手工点击 | 单元/JDBC（`EmbedAuthzTest`、`QueryApiJdbcTest`）覆盖；无宿主系统联调。 |
| 查询缓存寿命 | 进程内 idle 15 min / absolute 60 min，未做过期压测。 |
| 迁移销毁剧本 | `./deploy/scripts/migrate-verify.sh` 本会话未重跑；SRE 记录见 [p2-migrate-verify.txt](../evidence/implementation/p2-migrate-verify.txt)。 |

---

## BLOCKED

在拿到下列输入之前，**不能**把本仓库说成已对真实环境验收：

1. **真实客户血缘数据**（采集范围、对象身份、边方向、Java 粒度）。当前只有 `fixtures/v1` 与手写反例。
2. **企业 SSO / OIDC** 以及宿主→对象权限映射。现实现是 open / `demo-header` + 可选 `X-Embed-Groups`（信任宿主，deny wins）。没有身份提供商、没有真实 group 映射。
3. **生产主机、凭证、密钥、备份窗口**。`deploy/.env` 仅为本地占位（`change-me-local`），gitignore，未提交。

明确：**本文不声明生产可上线。** 本地绿灯只证明嵌入式工具在 disposable PG + fixture 上可走通。

---

## 嵌入挂载说明

宿主如何挂上 Vue 2 壳。命令级细节：[RUNBOOK.md](RUNBOOK.md)、[apps/web/README.md](../apps/web/README.md)、[apps/web/.env.example](../apps/web/.env.example)。

### 两种形态

1. **iframe**  
   构建产物 `apps/web/dist/`（`vite` `base: './'`，相对资源）。`html, body` 透明，`.shell` 使用原型 `--bg`，便于嵌进宿主皮肤。宿主页面：

   ```html
   <iframe src="https://host.example/lineage/" title="程序血缘地图"></iframe>
   ```

   跨源时：API 仅在 `Origin` **精确匹配** `lineage.cors.allowed-origins`（逗号分隔，本地默认 Vite `5173`/`4173`）时设置 `Access-Control-Allow-Origin`，**绝不回显任意 Origin**。允许头 `Content-Type, X-CSRF-Token, X-Embed-Groups, X-Request-Id, X-Lineage-Demo-User`，方法 GET/POST/OPTIONS。凭证走请求头，不用 cookie。**优先同域反代**（无 `Origin` 则无需 ACAO）。

2. **同域静态 + 反代 `/api`**  
   把 `dist/` 放到宿主静态目录；把 `/api` 反代到 Java 8 服务。此时设 **`VITE_API_BASE=`（空字符串）** 走相对 `/api/...`，避免浏览器跨源。本地等价：`npm run dev` 已把 `/api` 代理到 `VITE_API_BASE`（默认 `http://127.0.0.1:8080`）。

### 环境变量

| 变量 | 默认 | 含义 |
|---|---|---|
| `VITE_API_BASE` | `http://127.0.0.1:8080` | API origin。空 = 同域。 |
| `VITE_CSRF_TOKEN` | `dev` | 每个 POST 的 `X-CSRF-Token`。必须与 API `lineage.csrf.token`（默认 `dev`）一致。宿主真实嵌入须换成不可猜测值。 |
| `VITE_EMBED_GROUPS` | 省略 | 可选 `X-Embed-Groups`（逗号分隔）。省略 = 授权活跃快照全部对象（open）。 |

复制 `.env.example` → `.env.local`。不要提交密钥。

### CSRF（所有 POST）

`POST /api/imports*`、`POST /api/lineage/queries`、`POST .../projection` 等 **必须** 带与 `lineage.csrf.token` **常量时间相等** 的 `X-CSRF-Token`（本地默认 `dev`）。缺头或错 token → 400 `INVALID_ARGUMENT`。载荷上限 50 MiB，否则 413 `PAYLOAD_TOO_LARGE`。

### Open mode 注意

- 默认 `lineage.security.mode=open`：**不是登录**。任何能打到 API 的调用者，在省略 `X-Embed-Groups` 时可读当前活跃快照，且可 import/publish（仍须匹配 CSRF）。明确的本地全开模式，不是 SSO。
- 发送 `X-Embed-Groups` 时：查询按 `scope_grant`（`view`）+ `object_grant` 求交，**deny wins**。未授权 / 伪造对象 id 统一 `NOT_FOUND`，不泄露存在性。`POST /api/imports` 与 `POST /api/imports/{runId}/publish` 还要求目标 scope 上有 `ingest` grant，否则 403 `FORBIDDEN`。
- `queryId` 是会话凭据，**禁止写入 URL**。URL 只编码 `mode,seedId,snapshotId,selectedId,targetId,types,page,runId`；刷新会按 `seedId` 重建查询并重拉 API。
- 可选 `LINEAGE_SECURITY_MODE=demo-header` + `X-Lineage-Demo-User`，仍不是 OIDC。

---

## 已知产品/技术限制

| 限制 | 现状 |
|---|---|
| 工程栈 vs HANDOFF | 代码是 **Java 8 + Spring Boot 2.7 + Vue 2 嵌入壳**。[HANDOFF.md](../HANDOFF.md) 设计默认仍是 React + TypeScript + React Flow + Java 21 / Spring Boot 4.1。差值见 [ADR 0001](adr/0001-java8-vue2-embedded-vs-handoff.md)。不改产品语义（仅下游、Java 展示终点、主树/跨支、证据属关系、先授权再遍历）。 |
| 无 React Flow / G6 | 画布是 Vue 2 可展开树 + CSS `kind-tree` / `kind-cross` / `kind-unclassified`。不要用原型流程图拓扑去 Diff 树。 |
| 投影预算 | 服务端 200 对象 / 2000 关系。超限 `PROJECTION_LIMIT`；壳保留旧图并横幅「超预算，已保留原图」。fixture 远小于预算。 |
| 嵌入鉴权 | 最小 stub（open / demo-header / `X-Embed-Groups`），**不是**企业 OIDC。CORS 白名单精确匹配；CSRF 与配置 token 常量时间比较；header 存在时 import/publish 要 `ingest` grant。 |
| 演示数据 | 仅 `fixtures/v1` 与手写 counterexamples。证据文案写明 synthetic。 |
| 查询上下文 | 进程内缓存；发布新快照不会改写已创建 query 的 snapshot 绑定。多实例不共享 query。 |
| 视觉 | P4 冻结的是 chrome/token（1280/390 × 四视图），不是 flowchart 像素对齐。 |
| Flyway | V1 = `spec/v1/storage.sql`。Boot 2.7 自带 Flyway 8.x 对 PG 17 会打 untested 警告；功能本机可用。 |

规模、备份、生产部署演练不在本验收范围。
