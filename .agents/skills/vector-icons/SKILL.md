---
name: vector-icons
description: 处理本仓库（Android 客户端 + Web 前端）的矢量图标：把 SVG/iconfont 素材转成 VectorDrawable、把线宽统一到 1.8dp、把画布占位归一到 18~20dp、修描边版与填充版的配对跳动、重绘 Canvas 字形为资源、生成等大对照图做验收。凡是涉及图标、icon、drawable、VectorDrawable、ic_*.xml、res/drawable、SVG 转换、条粗细/线宽/描边、图标尺寸/占位/留白、iconfont、图标对照图，用户提到"图标不一致/偏粗偏细/重绘/加粗/归一化/换成资源"，或者要新增替换某个图标、接线 iconRes、检查图标规范时，都用本 skill——哪怕用户没说"图标规范"。
compatibility: 需要 Node.js（脚本为 .mjs）与 Python 3 + Pillow（对照图）。脚本内路径按本工作区预置，可用 --dir/--assets 覆盖。
metadata:
  version: "1.0"
  scope: 本仓库（client/ 与 frontend/）
---

# 矢量图标流程

本 skill 是**流程 + 工具**：规范正文不在这里，避免两份文档漂移。

**规范唯一来源**：`client/docs/客户端架构与开发规范.md` §4.5.1（24 视口 / 线宽 1.8dp / 占位 18~20dp /
填充版与描边版同外缘），占位清单与交付状态在 `client/docs/交付说明-Android客户端.md` §8。
动手前先读这两节的当前内容，本 skill 只讲怎么干、怎么验。

## 资产地图

| 位置 | 是什么 |
|---|---|
| `client/app/src/main/res/drawable/` | Android 图标本体（唯一可编译位置，只收 `.xml`/`.png`） |
| `frontend/src/assets/icons/` | Web 端图标**源**，Android 的 drawable 大多从这里转出来，两边同名同路径 |
| `client/docs/assets/` | 用户新下载的 SVG（转完即可清走） |

改动 Android 侧后，**Web 端同名图标不会自动一致**——收尾时提醒用户这一点。

## 工作流

### 1. 先量，再改

```bash
node scripts/audit.mjs                 # 全量体检：视口 / 外缘占位 / 可见线宽 / 判定
node scripts/audit.mjs ic_heart.xml    # 指定文件
```

判读：`✗ 线宽偏离` 要处理；`△ 占位长边出区间` 看情况（`ic_share` 这类宽扁字形短边天然更小，
判定已按长边）；`—` 开头是已知例外（AI 图标族 2.0/2.4dp、性别徽标、插画），不要动；
`△ 量值不一致，人工判读` 表示这个字形的环带不由两条平行轮廓组成，按构造判断或看对照图。

**别跳过量测直接改**：本仓库历史上"看起来该粗一点"的直觉错过两次（心形的 1.5dp 来自视口换算、
扫帚的线宽基准被量测 bug 带偏）。量出数字再动手，改完再量一次。

### 2. 三类缺陷 → 四条修法

| 缺陷 | 判据 | 修法 |
|---|---|---|
| **线宽偏差** | audit 报线宽 ≠ 1.8dp | 见下面四条按场景选 |
| **画布留白** | 外缘占位 < 18dp（iconfont 的 1024 栅格自带 12~14% 安全边距） | `convert.mjs svg <f> --art 19.5` 归一化并居中 |
| **配对跳动** | 描边版与填充版的外缘 bbox 不一致 | 填充版改用描边版的**同一外轮廓**：`convert.mjs pair` |

线宽的四条修法，按"能不动形状就不动"排序：

1. **缩放到目标线宽**（源线条比例和本套接近时）：`convert.mjs svg <f> --line 64`。
   输出的占位可能出区间，工具会提示——这时改用第 3 条。
2. **叠同色描边加粗**（形状不能动、只是偏细）：`convert.mjs thicken ic_x.xml --from 1.5 --to 1.8`。
   描边是真实偏移，环带精确变宽、原路径一字不改。
3. **缩到目标占位 + 叠描边**（既要 1.8dp 又要 18~20dp 占位，源线条比例又不合适）：
   `convert.mjs rescale ic_x.xml --art 19.5` 然后 `convert.mjs thicken ... --from <新线宽> --to 1.8`。
   两个目标可同时达成，别急着下"只能二选一"的结论。
4. **重绘为「中线 + strokeWidth」**（原字形是丝带/描摹产物，或想让它以后好调）：
   直线与圆弧直接写出中线（`convert.mjs stroke`），复杂形状手工拟合后也用这种表达。
   外缘会与原来差 0.5dp 以内，可接受。

### 3. 新增/替换素材

1. 素材进 `client/docs/assets/`，**不要放进 drawable**（`.svg` 会让 aapt2 直接构建失败，中文名也打不开）。
2. 转：`convert.mjs svg <f.svg> --art 19.5`（实心字形）或 `--line 64`（线形字形）→ 产出 `ic_xxx.xml`。
3. 立刻 `audit.mjs ic_xxx.xml` 复测 + 出对照图（见第 5 步）。
4. 命名：`ic_` + 语义化小写（`ic_share` / `ic_broom`）。工具类小字形（close/chevron/menu/more/play）
   保持各自原有占位，只统一线宽。

### 4. 接线

- 一律 `Icon(painter = painterResource(R.drawable.ic_x), contentDescription = ..., tint = ..., modifier = Modifier.size(Dimens.iconNN))`；
  尺寸取 `core/design/XhsDimens.kt` 的档位（12/16/20/24/30 等），别写魔法数。
- 顶栏动作按钮用 `XhsIconButton(iconRes = ...)`。
- 若某个图标原本由 **Canvas 字形**（`core/ui/XhsGlyphs.kt`）画：建成资源后替换全部调用处，
  并删掉那个字形函数（本项目已如此处理 `XhsPlayGlyph` / `XhsMenuGlyph` / `XhsPersonGlyph`）。
  确认删干净：`grep -rn "<GlyphName>" --include=*.kt`。
- 替换占位素材的，在 `交付说明` §8 的清单里划掉对应条目并更新计数。

### 5. 验收（三件套，缺一不可）

```bash
# ① 量：线宽全部落在 1.79~1.82dp、配对外缘一致
node scripts/audit.mjs

# ② 看：等大对照图（新图标 + 邻居一起排，才看得出粗细是否一致）
node scripts/audit.mjs --json /tmp/geom.json
python scripts/contact-sheet.py --json /tmp/geom.json \
  --drawable client/app/src/main/res/drawable -o /tmp/sheet.png \
  ic_heart.xml ic_star.xml ic_comment.xml ic_new.xml

# ③ 构建
cd client && ./gradlew :app:assembleDebug --offline
```

再 `cp app/build/outputs/apk/debug/app-debug.apk dist/xiaohongshu-clone-debug.apk` 刷新交付包
（`client/dist/` 是用户装的那份）。真机验收由用户自己做，不要代为安装。

### 6. 收尾

- 规范/例外有变化 → 更新 `客户端架构与开发规范.md` §4.5.1；
- 占位清单有变化 → 更新 `交付说明-Android客户端.md` §8；
- 改了 Android 图标线宽 → 提醒用户 Web 端 `frontend/src/assets/icons/` 同名图标还没对齐。

## 坑（出手前扫一眼）

- **视口不统一**是本项目线宽散乱的根因：16/20/22/24/48/960/1024 混用。新建一律 24 视口
  （`width/height=24dp`，坐标≈dp）。
- **描摹路径**会带补丁、0.01 量级碎片子路径、自环。evenOdd 下"补丁 ∩ 环带"会互相抵消，
  表现为**发丝裂缝**。用 `audit.mjs` 的几何 + 分段染色渲染确认，别猜。
- **`fillType="evenOdd"` vs nonzero** 的渲染结果可能完全不同。做对照图时**不能用简单并集**，
  环形图标会被填成实心（`contact-sheet.py` 已按 nonzero 分正负绕向、evenOdd 走 XOR）。
- **光栅量线宽**只在纯水平/垂直线段上可信；斜线被水平切会让 run 偏大。
- **量测工具必须支持 `s/S`、`t/T`**（平滑曲线要反射上一控制点）。本仓库的工具曾因缺这个丢整段路径、
  量出偏大的假线宽（详见 casebook 第 4 条）。改工具时别动这段。
- **写脚本改图标要先做幂等性保护**：把"已改过的文件"再改一次会得到 2.25 倍偏移的垃圾
  （历史上真发生过）。读原文件前先断言特征（如"16 栅格下应约 13.3 单位宽"），或从 git 还原后再跑。
- **别用 `git checkout -- <某个图标>` 来"还原"**：如果该文件在提交之后又被手工修过，
  checkout 会把那些修复一起回退（本项目踩过：扫帚被回退成未修版本，全量体检才发现）。
  回退前先 `git log --oneline -- <file>` 确认最后一次修改在哪；改完记得再跑一次 `audit.mjs`。
- **命令行里带中文文件名**：Git Bash 的 heredoc 会把中文写坏，脚本请用编辑器/写文件工具生成；
  能用英文名就别用中文名。
- 改完记得**重出 APK 并刷新 `client/dist/`**，否则用户装到的还是旧的。

## 脚本

| 脚本 | 用途 |
|---|---|
| `scripts/lib.mjs` | 矢量工具库：`parseAbs`（含 S/T）、`applyTransform`、`emit`、`bbox`、`flatten`、`readIcon`、`svgSegs` |
| `scripts/audit.mjs` | 体检：线宽 + 占位 + 判定；`--json` 导出几何给对照图用 |
| `scripts/convert.mjs` | 转换/修复：`svg`、`stroke`、`rescale`、`thicken`、`pair` |
| `scripts/contact-sheet.py` | 等大对照图（nonzero/evenOdd + 描边，超采样） |
| `references/casebook.md` | 6 个真实故障的复盘 + 测量口径与陷阱速查 —— **诊断新问题时先读它** |

路径默认按本仓库预置（脚本自动定位工作区根），需要时用 `--dir` / `--assets` / `--drawable` 覆盖。
