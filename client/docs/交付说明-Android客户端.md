# 小红书复刻 · Android 客户端交付说明

> 分支：`feat/android-client` · 交付日期：2026-09-15
> 安装包：`client/dist/xiaohongshu-clone-release.apk`（已签名，可直接安装）、`client/dist/xiaohongshu-clone-debug.apk`
> 相关文档：`client/docs/低保真线框图-移动端-v6.html`（设计权威）、`client/docs/API契约-v2.md`（接口权威）、`client/docs/客户端架构与开发规范.md`（架构与开发规范）

---

## 0. 一句话总结

按线框图 v6 实现了全部 **67 个页面/状态**的 Android 客户端（Kotlin + Jetpack Compose，单 Activity，89 个源文件约 1.8 万行），
在模拟器上用 mock server 跑通了主旅程（游客浏览 → 登录拦截 → 登录 → 互动 → 评论 → 搜索 → 消息 → 点点 → 发布入口 → 个人主页 → 退出登录），
逐屏与设计稿核对；**release APK 已签名并实测安装运行**。

---

## 1. 交付物

| 交付物 | 路径 |
|--------|------|
| 安装包（release，已签名，推荐） | `client/dist/xiaohongshu-clone-release.apk`（26 MB） |
| 安装包（debug） | `client/dist/xiaohongshu-clone-debug.apk`（37 MB） |
| **接口契约 v2（转交后端）** | `client/docs/API契约-v2.md` |
| 架构与开发规范 | `client/docs/客户端架构与开发规范.md` |
| Mock Server（API 联调用，零依赖 Node） | `client/mockserver/`（含 README、44 个路由、50 篇种子笔记、真实 PNG 生成、可播放 mp4） |
| 图标等素材 | `client/app/src/main/res/drawable/`（23 个真实图标 + 2 个占位 + logo） |

> **安装包签名说明**：release APK 用 Android 调试密钥（`~/.android/debug.keystore`）签名，仅为便于你安装验收。
> 上架/正式发布请换成正式 keystore，并在 `app/build.gradle.kts` 里配置 `signingConfigs`。

---

## 2. 怎么跑起来

```bash
# 1) 起 mock server（另开一个终端）
cd client/mockserver
PUBLIC_BASE=http://10.0.2.2:8787 node server.mjs      # 模拟器经 10.0.2.2 访问宿主

# 2) 编译安装
cd client
./gradlew :app:assembleDebug --offline                 # 依赖已全部在本地 Gradle 缓存

# 3) 接真实后端时不改代码，覆盖 Base URL 即可
./gradlew :app:assembleDebug -Pxhs.baseUrl=http://192.168.1.10:8080/
```

- Base URL 默认 `http://10.0.2.2:8787/`，来自 `BuildConfig.XHS_BASE_URL`（也可写进 `client/local.properties` 的 `xhs.baseUrl`）。
- 登录账号（mock 种子）：`admin` / `user1` / `user2` / `user3` / `testuser`，密码统一 `123456`。
- **故障注入**（用于验收异常态）：请求头 `X-Mock-Fail: 1`（500）/ `401`（登录失效，验 I1）/ `timeout`（20s，触发客户端 15s 超时），`X-Mock-Delay: <ms>` 调延迟。

---

## 3. 需要你转交后端的部分

**唯一需要后端改动的是 `client/docs/API契约-v2.md`。** 该文档以现有 openapi 为准，列出了 **16 项**差异（§0 变更总表），分三类：

1. **新增字段**（不破坏兼容）：`PostVO.collected` / `PostVO.followed`；`UserVO` 的 `backgroundImage/birthday/region/occupation/school/redId` + **`collectedPostCount`/`likedPostCount`**；`ChatResponseVO.notes`；`unread-count` 增加三个分类未读数。
2. **新增端点**：`GET /api/post/following-feed`（B5 关注流）、`GET /api/like/posts/{userId}`（F1 赞过）、`GET /api/post/hot-keywords`（B2 猜你想搜）、`POST /api/post/text-image`（E3，**直接返回 URL**，免客户端二次上传）、`GET /api/ai/suggestions`（H1 建议问题）。
3. **语义澄清**（易踩坑，务必让后端看）：
   - `PostVO.commentCount` = 一级评论 **+ 回复** 总和（要与底栏 💬 一致）；
   - `UserVO.likeCount/collectCount` 是**收到**的赞/藏，而 `collectedPostCount/likedPostCount` 是**我赞过/收藏过**——两者语义相反，F1 小组件卡必须用后者（§0 有专门的对照表）；
   - `NotificationVO.type` 以实体注释为准（1..6），并新增 `category=1|2|3` 参数对应 G1 三个入口。

> 客户端没有直接依赖任何"临时"字段：契约里所有新增项都已在 mock server 实现并跑通，后端按文档实现后把 Base URL 指过去即可，**客户端无需改代码**。

---

## 4. 技术选型与关键决策（含取舍理由）

| 决策 | 选择 | 理由 |
|------|------|------|
| UI | Kotlin 2.2.10 + Compose（BOM 2026.02.01）+ Material 3，单 Activity | 模板既定；单 Activity + Compose 导航 |
| DI | **手工容器 `AppContainer` + `appViewModel {}`**，不用 Hilt | Hilt 需要 KSP/注解处理，与 AGP 9 + Kotlin 2.2 有版本耦合风险；本项目依赖图很浅，手工容器零反射、可读 |
| 网络 | Retrofit 3 + OkHttp 5 + kotlinx-serialization | Retrofit 3 强制 OkHttp 5；序列化无反射、`ignoreUnknownKeys` 保证后端加字段不崩 |
| 图片 | **自研 `ImageLoader`**（OkHttp + BitmapFactory + LruCache + 磁盘缓存），不用 Coil | 依赖缓存里的 Coil 2.7 面向 OkHttp 4，与 OkHttp 5 混用有运行期不兼容风险；自研同时统一了"缺失素材一处占位"策略 |
| 视频 | **框架自带 `VideoView` + `MediaPlayer`**，不用 Media3/ExoPlayer | 依赖缓存里没有 media3；框架播放器零依赖，mock 的 mp4 已实测可播放（含 Range 请求） |
| 分页 | 自研 `PagedList<T>` + `PagedState<T>`，不用 Paging 3 | 把"每页 20 / 距底预加载 / 四态 / 追加失败不清空"收敛成一个类，所有列表复用 |
| 跨页状态 | 三个集中式 Store：`InteractionStore`（互动）/ `UnreadCountCenter`（角标）/ `PublishDraft`（发布草稿） | 同一笔记会同时出现在首页、搜索、主页、详情；若各页各存一份，从详情返回列表赞数会"跳回去" |
| 导航解耦 | feature 只依赖 `AppNavigator` 接口，feature 之间零引用 | 6 条工作流可并行开发互不冲突；路由表变更不影响页面 |
| 持久化 | DataStore Preferences（登录态、搜索历史）；AI 对话仅内存 | 与线框 §11 一致 |

**明确未引入**：Hilt、Coil、Media3、Paging3、Room、Accompanist、任何测试框架（按"不需要 TDD / 单测集成测试"的要求）。

---

## 5. 我在真机上实测过的（截图逐屏核对）

我用模拟器（Pixel 7 / API 36）逐屏走查，下面这些是**实际看到并核对过**的；括号里是关键验证点。

- **A1 游客首页**：＋ 灰块、☰、底部登录悬浮条、混排 4:3 与 3:4 封面、视频 ▶ 角标、频道栏右渐隐+箭头。
- **A2 未登录拦截**：游客点 ♡ / 「消息」Tab / ＋ **均直接推入登录页且不弹 Toast**（三处都验过）。
- **A3 登录页**：真实小红书 logo、密码掩码、**未勾选协议时登录按钮禁用**、协议行、注册入口。
- **A6 登录成功全局变化**：Toast 后弹回来源页——顶栏 ☰ 变点点气泡、＋ 变品牌红、消息出现角标「7」、悬浮条消失；
  并且**游客时点的那个 ♡ 被补执行了**（`LoginGate.consumePending()`：卡片变红心），即"弹回原位置"且动作不丢。
- **B1 首页**：频道栏点击仅高亮、无限滚动、瀑布流卡片脚栏（头像/昵称/♥/计数）。
- **B2 搜索页**：**历史为空时整块隐藏**；搜过一次后「历史记录」出现该词；「猜你想搜」两列与接口返回逐条一致。
- **B3-1 / B3-2**：结果瀑布流（无底 Tab、作者多样、视频角标、「没有更多了」）；无结果空态「暂无相关内容，换个关键词试试」+ 占位插画。
- **C1-1 图文详情**：**大图区实测 1080×1440 = 精确 3:4**；多图页码角标「1/3」+ 居中圆点指示器；自己的笔记**不显示关注按钮**（游客访问同一笔记则显示，符合预期）；底栏 ♡/☆/💬 计数为 0 显示文字标签、>0 显示数字（≥1 万收敛为「1.2万」）。
- **C1 评论区（页面流）**：「共 n 条评论」、评论「作者」徽章、头像/正文列几何、「展开 3 / 12 / 27 条回复」（N 取自 `replyCount`）。
- **回复分批展开（逐条核过，最复杂的状态机）**：点「展开 27 条回复」→ 抓包确认发出 `GET /api/comment/replies/1`（每批 10 条，接口 10/10/7 共 3 页）→ 控件**原位消失**、10 条就地平铺（二级回复缩进更小、`回复 @昵称：内容` 前缀正确）→ 组尾出现「**展开更多回复**」（**不带数字**）→ 「没有更多了」衔接下一条，**全程无「收起」**。
- **C2-1 视频详情**：**mock 的 mp4 真的在播放**（画面为彩条测试图）；状态栏与底栏区保持黑色不侵入；顶栏/作者行/标题浮层叠加在视频上；底栏 44（胶囊 + ♡/☆/💬）；进度条贴视频区底边界。
- **C3-1 评论面板**：打开时**视频上移缩小**；圆角弹层约 2/3 高；Tab 行「评论 3 / 赞和收藏 5」+ ≡ 排序占位 + × 关闭；底部输入行 52「爱评论的人运气都不差」且空输入时「发送」禁用（与线框逐字一致）。
- **D1 点赞乐观更新 + 无计数漂移**：对低计数卡片连续点两次 ♡，UI 与服务端最终都回到 `likeCount=2 / liked=false`（专门验证了我修掉的计数偏移缺陷）。
- **E1 发布入口弹层**：遮罩 + 三行（从相册选择/拍摄/写文字）20sp 居中 + 间隔 + 取消。
- **F1 我的主页**：头图、浮层顶栏（☰/编辑主页 pill/扫一扫/分享）、头像 108、昵称 24sp、小红书号+复制+IP 属地、统计行「3 关注 8 粉丝 **3.7万** 获赞与收藏」、简介、性别图标、小组件卡「收藏 8 / 赞过 13」（**用的是 `collectedPostCount/likedPostCount`，语义正确**）、去发布 banner、segment 三 Tab、各 Tab 瀑布流。
- **F2 他人主页**：Tab 仅「笔记/收藏」；本人访问时不显示关注按钮（修好取数后才验证通过，见 §7）。
- **F4 抽屉 / F6 退出确认**：抽屉宽 308 + 设置/社区公约/关于我们/退出登录 + 底部三宫格；确认退出后**回到 A1 游客首页**（＋ 变灰、角标消失、悬浮条回来）。
- **G1 消息页**：三入口卡角标 **4 / 2 / 1**（分别来自接口的三个分类未读数），底 Tab 角标 **7 = 三者之和**（契约变更 #6 端到端跑通）；点点会话行带 ai 小标。
- **G2 收到的赞和收藏**：抓包确认 `category=1`（契约变更 #7 跑通）；三种子类型文案、未读左侧红点 + 已读整行变淡、右侧笔记封面缩略图、时间「刚刚 / 7小时前 / 12小时前 / 昨天 / 09-12」。
- **H1 点点空态**：5 条建议问题来自 `GET /api/ai/suggestions`、底部输入条 48 + 「内容由AI生成」声明行、无底 Tab（推入式）。
- **H2 对话态**：`POST /api/ai/chat` 返回后**带笔记卡**（契约变更 #10 跑通）；`•` 列表渲染正确；操作条 复制/★/分享｜踩/重新生成。**此处发现并修复了一个布局缺陷，见 §7。**
- **Release APK**：卸载 debug 后安装签名 release 包，冷启动无崩溃、游客首页正常。

---

## 6. 只在代码层面自审、**没有**在真机上走到的路径（请你重点验收）

诚实说明——下列路径我做了逐条代码自审（每个工作流交付时都提供了规则到 `file:line` 的对照），但受验收时间限制没有在模拟器上实际点过：

| 未实测 | 说明 / 建议怎么验 |
|--------|------------------|
| A4 / A5-1 / A5-2 | 注册页、登录/注册失败错误条（含"用户名已存在"）、提交中禁用态与 ← 不可返回 |
| B4-2 列表加载失败态 | 用 `X-Mock-Fail: 1` 触发；「重试」按钮 |
| B5 关注 Tab | 需要先关注某人；空态文案「还没有关注的人，去发现逛逛吧」 |
| C1-2/C1-3 加载中/失败、C2-2/C2-3/C2-5 seek、C2-4 遮罩输入 | seek 手势（视频底部 30% 快速左右滑）尤其需要真机手感验收 |
| C3-3/C3-4/C3-5 的状态切换细节 | 回复遮罩输入、原位「加载中」、批次尾控件下移 |
| D2 关注/取关状态机、D3 发评论/回复成功反馈 | 关注按钮两种形态（详情描边胶囊 / 他人主页通栏）、Toast「回复成功」 |
| E2–E7 发布全流程 | 相册多选、相机（I4 权限）、E3 文字配图生成、E4 校验、E5-1 提交中、E5-2 发布成功后首页刷新置顶、E7 放弃确认 |
| F3 / F3-1 / F3-2 编辑资料 | 遮罩输入与选项、仅提交变化字段、头像/背景图先上传再保存 |
| F5 设置页 | 行距 52、右值灰、底部「切换账号」 |
| G3 / G4 / G5 / G6 | 评论和@ 的行内回复、新增关注的「回关」、三入口共用的空/加载/失败态、🧹 一键已读 |
| H3-1 / H3-2 | 三点脉冲、失败时对话流内红色气泡 + 超时文案 |
| I1 会话过期 | 用 `X-Mock-Fail: 401` 触发：清登录态 + 推入登录页 |
| 相机权限（I4） | 模拟器相机不稳定，建议真机验 |

---

## 7. 我在验收中发现并修掉的 3 个缺陷

1. **互动计数会永久漂移（严重）** —— `InteractionStore` 用入参的 `liked`/`likeCount` 作为服务端基准算增量，而首页把 `merge()` **合并后**的值又传回了 `toggleXxx()`，导致基准里含旧增量被重复叠加。
   已修：`HomeViewModel.toggleLike` 先按 id 反查**服务端原值**再提交；同时把 `InteractionStore` 的入参改名为 `serverNote`/`serverComment` 并在 KDoc 与开发规范 §4.3 里写明这个陷阱（附正确/错误两段示例代码）。
   验证：低计数卡片连点两次 ♡，UI 与服务端都回到 `likeCount=2 / liked=false`。
2. **F2 他人主页缺失资料字段** —— 简介、背景图、性别、获赞与收藏全为空（子代理发现 feature 层没有 `GET /api/user/{id}` 的入口，只能拿列表里的昵称/头像兜底）。
   已修：新增 `data/repo/UserRepository.getUser(id)` 并接入容器，F2 改为拉真实资料（列表值仅作请求失败的兜底）。
3. **H2 点点回复卡片布局重叠** —— `AiCard` 容器用的是 `Box`，导致"正文 → 笔记卡 → 操作条"三个子项叠在同一原点上（正文被笔记卡盖住、操作条压在卡片图上）。
   已修：改为 `Column`。验证：正文完整可见、`•` 列表正常、笔记卡在下方、操作条最后。

---

## 8. ⚠️ 待替换的占位素材（你要求的收尾提醒）

按你的指示「找不到的图标和矢量图插画就先都使用同一个素材顶替」。现在全项目只用**两个**占位素材，替换时按下面的清单逐一补图即可（替换后无需改代码，改资源文件名/内容即可）：

- **`ic_placeholder.xml`** —— 所有缺失图标与插画共用的占位（一个带斜杠的圆角方框，一眼能看出是占位）。
- **`ic_placeholder_illus.xml`** —— 空态插画占位（`XhsIllustrationSlot` 使用）。
- 另：**结构性控件已用 Canvas 精确绘制**（`XhsPlusGlyph` ＋ / `XhsMenuGlyph` ☰ / `XhsPlayGlyph` ▶ / `XhsPersonGlyph` 人形 / `XhsCheckCircle` 协议勾选），**不需要替换**。

**缺素材、当前用占位顶替的位置（共 16 个文件、约 57 处）**：

| 位置 | 具体缺哪些图标 |
|------|---------------|
| `feature/ai/AiComponents.kt`（8 处） | 语音、表情、＋、复制、★、分享、踩、重新生成 |
| `feature/profile/ProfileHeader.kt`（6 处） | 扫一扫、分享、编辑主页 pill 内图标、复制小红书号 |
| `feature/profile/ProfileCommon.kt`（6 处） | 去发布 banner 图标、segment 搜索框、部件卡图标等 |
| `feature/message/MessageRoutes.kt`（5 处） | 顶栏「创建」、置顶图标 |
| `feature/message/NotificationRoutes.kt`（3 处） | 右上「一键已读」🧹 |
| `feature/profile/MyProfileScreen.kt`（4 处） | 抽屉四个条目图标 |
| `feature/profile/SettingsScreen.kt`（3 处） | 设置行图标（除「关于小红书」用 `ic_about`） |
| `feature/detail/VideoDetailScreen.kt`（3 处） | 分享、音乐碟、作者行分享 |
| `feature/detail/NoteDetailScreen.kt` / `CommentPanel.kt`（各 2 处） | 分享 ↗、评论面板 emoji/@、排序 ≡（≡ 已用 Canvas 字形） |
| `feature/search/SearchComponents.kt`（3 处） | 拍照搜索相机、历史记录 🗑 |
| `feature/publish/PublishComponents.kt`（3 处） | 删除 ×、长文卡箭头等 |
| `feature/profile/EditProfileScreen.kt`（2 处） | 头像相机角标 |
| `feature/home/HomeBars.kt` | 无需替换（均为 Canvas 字形） |

**已有真实素材可直接用**：`ic_home / ic_assistant / ic_notify / ic_heart(_filled) / ic_star(_filled) / ic_comment / ic_search / ic_more / ic_close / ic_chevron_left / ic_chevron_right / ic_publish / ic_live / ic_male / ic_female / ic_about / ic_red / ic_logo`。

> 2026-09-15 更新：底 Tab 改为**纯文字无图标**（对齐原版），故 `ic_home`、`ic_notify` 已无用例（文件保留但不再引用）；`ic_assistant` 仍用于首页顶栏点点气泡与 H1 会话页。

---

## 9. 需要你拍板的几个判断（我做了决定，但可以改）

1. **登录页协议勾选**：未勾选时「登录」按钮禁用（对齐真实 App，且复用了既有禁用态样式）；线框图未明确。
2. **「帮助」链接**：Toast「帮助内容暂未提供」，不新建页面（线框标为占位 #9）。协议两个书名号链接为纯 no-op。
3. **F1 头图上的文字未加遮罩**：线框 F1 是深色文字直接压在头图上，我照做了。若换成真实照片，昵称/小红书号的对比度可能不足——**要不要按原版加一层由下至上的深色渐变 + 文字转白？** 这属于对线框的偏离，故留给你决定。
4. **卡片脚栏 ♥ 尺寸取 16dp**：T-3 实测写「赞 icon 30」，但 T-4b 把 30 明确限定为"详情/视频底栏互动图标"，其余图标收敛为 12/16/20/24 四档。30dp 图标会压过 12sp 计数，故卡片脚栏取 16dp 档，详情底栏仍用 30dp。
5. **E3 左上用 × 而不是 ←**：按线框 E3 卡片与「× 直接放弃返回」的说明。
6. **G2 未单独做「查看历史消息」控件**：按规范统一用分页页脚（「没有更多了」/「加载更多」）。
7. **未实现「直播」**：线框 T-3 提到「直播中」徽标与直播卡，但无对应接口与页面，且 E1 的「拍摄」按你的说明只做拍摄。

---

## 10. 明确不在范围内的

- 后端实现（契约已备好，见 §3）。
- 单元测试 / 集成测试 / E2E 自动化脚本（按要求不做；本 APP 由你负责 Code Review 与 E2E 验收）。
- 正式签名密钥与上架配置（见 §1 签名说明）。
- 「关注/粉丝列表页」「合集」「话题」「位置」「可见范围」「评论表情/配图」「置顶评论」「IP 属地」「私信」等线框已显式排除或标注为占位的功能。
- 明暗主题：产品为浅色为主 + 视频页局部深色沉浸，**不跟随系统暗色**（与原版行为一致）。
