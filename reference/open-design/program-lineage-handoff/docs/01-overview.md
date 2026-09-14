# 01 · 这份稿是什么

## 结论

`prototype/lineage-map-v2.html` 是 **高保真可点击原型 + 血缘规则说明书**。

- 当验收标准：四种视图、筛选、展开、跨支虚线、空/加载/错误态
- 当算法说明书：`buildTree()` 的拆树结果必须被服务端复现
- **不当** 业务页面源码：不要把这一文件原样塞进仓库上线

## 界面结构（实现时按组件拆）

```
AppShell
├── Header          当前表选择、搜索
├── Toolbar         四种视图、类型筛选、展开深度
├── SummaryBar      下游计数 + 实线/虚线图例
├── Body
│   ├── Stage       下游树 / 总览 / 影响清单 / 调用路径
│   └── Inspector   选中对象详情、设为根、展开子节点
└── Footer          快捷键说明（产品里可收进帮助）
```

演示控件必须删除：规模切换（16 / 520）、页脚「交易订单域」样例文案。

## 原型里可抄 vs 必须换

**抄语义、抄视觉、抄交互。**

**换掉：**

| 原型做法 | 产品做法 |
|---|---|
| `buildGraph()` 生成 16/520 假数据 | `GET /lineage` 等接口 |
| `localStorage` 存 seed / mode / sel | URL：`?seed=&sel=&mode=&depth=` |
| 字符串模板 + `innerHTML` | Vue / React 组件 |
| 每次 `getBoundingClientRect` 画全量边 | 布局坐标缓存；量大用分层图引擎 |
| Google Fonts · JetBrains Mono | 公司允许的等宽字体或自托管 |
| 前端一次拿 520 节点再筛选 | 默认 depth=1，子节点懒加载 |

## 16 对象样例在本包中的角色

`fixtures/small-graph.json` 是交易订单域的命名对象图，用来：

1. 讲清楚「侧输入不进树」
2. 作为拆树回归（`fixtures/seed-ods-trade-order.expected.json`）
3. 前端第一期接真实接口前，用同一份 fixture 对照原型

520 对象只证明「默认 1 层 + 总览聚类」这条产品策略，不需要复现那份随机扇出数据。
