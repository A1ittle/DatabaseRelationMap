# 02 · 领域模型

权威类型定义：`spec/types.ts`。

## 对象 Node

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 稳定主键。接口、URL、选中态都用它，不要用展示名 |
| `name` | string | 技术名，等宽字体。如 `ods.trade_order`、`com.bi.GmvReportService` |
| `title` | string | 中文名。如「交易订单表」 |
| `type` | `'table' \| 'view' \| 'procedure' \| 'java'` | 四类对象，筛选芯片与颜色都按它 |
| `owner` | string | 负责人 / 团队。搜索可命中 |
| `system` | string | 所在系统。如 Hive、Oracle SP、Java 服务 |
| `status` | `'verified' \| 'inferred'` | 已校验 / 推断。详情胶囊 |
| `desc` | string? | 一句话说明 |

约束：

- 只有 `table` 能当 seed（根）
- `java` 在图上是终端叶子
- `id` 可含点号（`ods.trade_order`）。前端选择器不要用 `CSS.escape` 当 class，用 `data-id` 属性选择

## 边 Edge

| 字段 | 类型 | 说明 |
|---|---|---|
| `from` | string | 起点 node id |
| `to` | string | 终点 node id |
| `rel` | `'reads' \| 'writes' \| 'calls' \| 'derives'` | 关系 |

方向：`from → to` 表示数据或控制流向下游。

| rel | 中文 | 强度 | 典型含义 |
|---|---|---|---|
| `writes` | 写入 | 3 | 存储过程 / 作业写出一张表 |
| `derives` | 派生 | 2 | 视图或下游表由上游派生 |
| `calls` | 调用 | 1 | 程序调用（本期 Java 无出边，预留） |
| `reads` | 读取 | 0 | 读取一张表 / 视图 |

## 树拆分后的边

服务端在原始 `Edge[]` 上算出两类，前端不要自己猜：

```ts
type ClassifiedEdge = Edge & { kind: 'tree' | 'cross' }
```

- `tree`：主依赖，实线，构成唯一父指针森林
- `cross`：跨支，虚线，仍要画，用于汇合和高亮

## 下游树 LineageTree

以一张 seed 表为根：

```ts
{
  seed: string
  reach: string[]                  // 含 seed
  rank: Record<string, number>     // seed = 0，下游 hop
  parent: Record<string, string>   // 非根 → 主父节点
  parentEdge: Record<string, Edge>
  tree: Edge[]
  cross: Edge[]
  children: Record<string, string[]>  // 仅 tree 子节点
}
```

`crossOf[id]` = 所有 `from` 或 `to` 等于 id 的跨支边。详情栏「另 N」、选中高亮都用它。

## 对象类型的产品语义

| type | 标签 | 色 token | 特殊规则 |
|---|---|---|---|
| table | 表 | `--table` | 唯一可设为根 |
| view | 视图 | `--view` | 普通下游节点 |
| procedure | 存储过程 | `--proc` | 普通下游节点 |
| java | Java 程序 / 终端 | `--java` | 无出边；列全是 Java 时列头写「第 k 层 · 终端」 |

## 权限

看不到的对象：

- 不得出现在搜索结果
- 不得出现在树、清单、路径、详情邻接
- 边的另一端不可见时，这条边对当前用户不存在

未采集血缘与无权限要分开报错，见 `docs/03-api.md`。
