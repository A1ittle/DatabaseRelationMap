# 07 · 视觉规范

从原型 `:root` 抽出。做成 design tokens。组件里用变量，不要散落写死颜色；hex 只作为不支持 oklch 时的回退。

方向：Tech / utility。浅底、发丝线、等宽对象名。绿色只给当前表和选中路径，不要铺在筛选芯片上。

---

## Tokens

完整组件样式（节点、连线、页签、空状态、响应式）在 `prototype/lineage-map.css`，与验收 HTML 内联 `<style>` 同源。`spec/tokens.css` 只有变量。离线环境先写 hex，再在 `@supports (color: oklch(0% 0 0))` 里覆盖 oklch。

```css
:root {
  --bg:      #f6f9fc;
  --surface: #ffffff;
  --fg:      #121c23;
  --muted:   #444f57;
  --border:  #d9dfe3;
  --accent:  #299236;

  --success: #15561d;
  --warn:    #784600;
  --danger:  #9e2225;

  --table:   #324457;
  --view:    #004d51;
  --proc:    #633f00;
  --java:    #37395c;

  --hover:    #eff2f6;
  --press:    #e4e8ed;
  --fg-hover: #060e15;
  --fg-press: #000408;

  --inspect-w: 320px;
  --radius: 6px;
  --ease: cubic-bezier(0.2, 0, 0, 1);

  --font-display: -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif;
  --font-body:    -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif;
  --font-mono:    ui-monospace, "SF Mono", Menlo, Consolas, "Liberation Mono", monospace;
}
```

产品里不要挂 Google Fonts。等宽用系统栈或公司自托管字体替换 `--font-mono`。

拷贝到工程时用 `prototype/lineage-map.css`，不要只拷 `spec/tokens.css`。

---

## 使用规则

- **Accent 预算：** 当前表描边/光晕 + 高亮路径。筛选芯片、页签不要用绿
- **主按钮：** 背景 `--fg`，文字 `--surface`。hover 必须成对改成 `--fg-hover` / `--surface`，禁止只把字变 muted
- **选中页签 / 深度段：** 同样深底浅字，hover 用 `--fg-hover`
- **次按钮 / 芯片：** 浅底，hover `--hover`，文字保持 `--fg`
- **focus-visible：** `outline: 2px solid var(--fg); outline-offset: 2px`
- **数字：** `font-variant-numeric: tabular-nums`
- **对象名：** `--font-mono`，12px，最多两行
- **Java 终端列：** 浅紫底 `oklch(97% 0.012 280)`，这是终端层语义，不要用到第一列
- **状态胶囊：** 已校验绿底绿字，推断黄底黄字
- **画布：** 28px 发丝网格，不要大面积插画或渐变洗底
- **圆角：** 卡片 6px，芯片胶囊 999px，不要大圆角营销卡
- **禁止：** 根列再套白框；emoji 当图标；左侧色条 + 圆角卡片的 AI 仪表盘造型

---

## 图标

1.75 描边、`currentColor`、24 视口的单线 SVG。四类图标路径见原型 `ICO`。不要换 emoji。

---

## 布局

桌面：

```
grid-template-rows: auto auto auto minmax(0,1fr) auto
body: 1fr  320px
```

≤960px：详情改到底部，画布至少 38vh；搜索和 seed 选择器全宽，字号 16px 防止 iOS 缩放。

≤640px：四视图页签等分拉满。

`prefers-reduced-motion: reduce` 时关掉 spinner 以外的动画。

---

## 对比

普通文字 ≥ 4.5:1。原型已把 `--muted` 提到 `oklch(42% …)`，不要再降到 11px 浅灰。

主按钮、深色页签的 hover **不得** 变成浅字浅底。
