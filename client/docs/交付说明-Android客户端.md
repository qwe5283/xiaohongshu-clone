# 小红书复刻 · Android 客户端交付说明

> 分支：`feat/android-client` · 交付日期：2026-09-15
> 安装包：`client/dist/xiaohongshu-clone-release.apk`（已签名，可直接安装）、`client/dist/xiaohongshu-clone-debug.apk`
> 相关文档：`client/docs/archive/低保真线框图-移动端-v6.html`（历史线框图，已归档，仅供追溯）、`client/docs/API契约-v2.md`（接口权威）、`client/docs/客户端架构与开发规范.md`（架构与开发规范）

---

## 0. 一句话总结

按线框图 v6 实现了全部 **67 个页面/状态**的 Android 客户端（Kotlin + Jetpack Compose，单 Activity，89 个源文件约 1.8 万行），
在模拟器上用 mock server 跑通了主旅程（游客浏览 → 登录拦截 → 登录 → 互动 → 评论 → 搜索 → 消息 → 点点 → 发布入口 → 个人主页 → 退出登录），
逐屏与线框图核对（线框图现已归档，后续以代码为准）；**release APK 已签名并实测安装运行**。

---

## 1. 交付物

| 交付物 | 路径 |
|--------|------|
| 安装包（release，已签名，推荐） | `client/dist/xiaohongshu-clone-release.apk`（26 MB） |
| 安装包（debug） | `client/dist/xiaohongshu-clone-debug.apk`（35 MB，2026-09-18 重出：含全部图标规范改造） |
| **接口契约 v2（转交后端）** | `client/docs/API契约-v2.md` |
| 架构与开发规范 | `client/docs/客户端架构与开发规范.md` |
| Mock Server（API 联调用，零依赖 Node） | `client/mockserver/`（含 README、44 个路由、50 篇种子笔记、真实 PNG 生成、可播放 mp4） |
| 图标等素材 | `client/app/src/main/res/drawable/`（32 个真实图标素材，含 logo；另有 2 个占位与启动图标）。**源 SVG 与图标规范见 §8.1** |

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
   - `UserVO.likeCount/collectCount` 是**收到**的赞/藏，而 `collectedPostCount/likedPostCount` 是**我赞过/收藏过**——两者语义相反（契约 §0 有专门的对照表）；后两个字段继续保留，当前主页不再渲染小组件卡；
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
| 持久化 | DataStore Preferences（登录态、搜索历史）；AI 对话仅内存 | 与契约 §11 一致 |

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
- **C3-1 评论面板**：打开时**视频上移缩小**；圆角弹层约 2/3 高；Tab 行「评论 3 / 赞和收藏 5」+ ≡ 排序占位 + × 关闭；底部输入行 52「爱评论的人运气都不差」且空输入时「发送」禁用（定稿文案，逐字实现）。
- **D1 点赞乐观更新 + 无计数漂移**：对低计数卡片连续点两次 ♡，UI 与服务端最终都回到 `likeCount=2 / liked=false`（专门验证了我修掉的计数偏移缺陷）。
- **E1 发布入口弹层**：遮罩 + 三行（从相册选择/拍摄/写文字）20sp 居中 + 间隔 + 取消。
- **F1 我的主页**：共用 `ProfileScreen`，头图、浮层顶栏（☰/编辑主页 pill/扫一扫/分享）、头像、昵称、小红书号+复制+IP 属地、统计行（关注/粉丝/**获赞与收藏**）、简介、性别图标、segment 三 Tab、各 Tab 瀑布流；当前不渲染小组件卡或「去发布」banner。
- **F2 推入主页**：共用 `ProfileScreen`，他人访问时 Tab 为「笔记/收藏」并展示关注/私信；本人访问时显示编辑入口及「赞过」，通过返回按钮回来源页，无底 Tab。2026-10-18 重构记录见 §11。
- **F4 抽屉 / F6 退出确认**：抽屉宽 308 + 社区公约/关于我们/退出登录 + 底部三宫格；确认退出后**回到 A1 游客首页**（＋ 变灰、角标消失、悬浮条回来）。
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

## 8. 图标补齐与图片占位

以下清单随当前代码维护。固定图标已补齐。图片占位按场景使用现有素材。

- **已移除 `ic_placeholder.xml` / `ic_placeholder_illus.xml`**。空态使用 `ic_no_data / ic_no_network / ic_no_msg` 正式插画。
- 另：**结构性控件已用 Canvas 精确绘制**（`XhsPlusGlyph` ＋ / `XhsCheckCircle` 协议勾选），**不需要替换**；原先同一批就地画的 ☰ / ▶ / 人形已改为矢量资源（`ic_menu` / `ic_play` / `ic_user_circle`），见 §8.1。

**固定图标占位为 0 个**（不含图片加载兜底）。2026-10-30 新绘的 21 枚图标中，当前使用 11 枚。SVG、Android 资源、逐项映射和预览见 [图标交付清单](assets/图标清单-2026-10-30.md)。

| 位置 | 已接入图标 | 槽位数 |
|------|---------------|--------|
| `feature/ai/AiComponents.kt` | 语音、复制、踩、重新生成 | 4 |
| `feature/profile/ProfileDrawer.kt` | 社区公约、退出登录，以及底部夜间模式/二维码/意见反馈候选字形 | 5 |
| `feature/detail/VideoDetailScreen.kt` | 作者行音乐碟 | 1 |
| `feature/publish/PublishComponents.kt` | 「写长文」卡片图标 | 1 |

抽屉底部三格展示装饰性候选字形。其余 8 枚图标按既有用途接入。

**条件触发的图片占位**：头像（含编辑资料的本地头像预览）在加载中、URL/路径为空或解码失败时显示原色 `ic_avatar_fallback`；个人页背景、AI 推荐卡封面与通知缩略图不叠加占位图案。发布页本地媒体缩略图和编辑资料的本地背景预览在加载中或失败时显示淡色 `ic_image`（`XhsColor.Text3`）；视频地址为空时显示 `ic_logo`。通用 `XhsAsyncImage` 仅在显式提供 `fallbackPainter` 时绘制兜底图片。

**死代码清理**：删除 `PlaceholderIconRes`、`showGlyphOnFailure`、`XhsPlaceholderBox`、无调用的 `XhsCoverImage`，以及整个无业务调用方的 `XhsLocalImage.kt`（含 `XhsLocalImage / XhsMediaImage / XhsMediaThumb`）；旧占位资源的审计分类同步移除。

2026-10-30 复用的槽位中，头像相机角标、AI 表情、AI 更多及抽屉关于我们分别使用 `ic_camera`、`ic_emoji`、`ic_plus_circle` 和 `ic_about`。当时的其余 3 个槽位随 F5 和抽屉「设置」入口移除（§17）。

此前清单中的搜索相机/历史删除、消息页创建、个人页搜索及详情分享已使用正式素材；复制小红书号改为点击文字，个人页页签及评论面板的旧图标已取消渲染。

**可复用的真实素材**包括 `ic_home / ic_assistant / ic_heart(_filled) / ic_star(_filled) / ic_comment / ic_search / ic_more / ic_close / ic_chevron_left / ic_chevron_right / ic_publish / ic_live / ic_male / ic_female / ic_about / ic_red / ic_logo / ic_logo_filled / ic_share / ic_scan / ic_edit / ic_broom / ic_play / ic_menu / ic_user / ic_user_circle / ic_pin_top / ic_notify_mute / ic_camera / ic_emoji / ic_plus_circle / ic_delete / ic_discover / ic_image`。

**2026-11-12 LOGO 资源拆分**：原 `ic_logo.png` 重命名为 `ic_logo_filled.png`，登录/注册页及文档引用同步迁移；新 `ic_logo.xml` 复制自 `ic_launcher_foreground.xml`，仅将默认填充改为常规图标黑色 `#FF000000`，保留启动前景的路径、108 视口、分组变换与留白（品牌资源例外）。

验证：`:app:compileDebugKotlin :app:assembleDebug --offline` 通过，零编译 error / warning；已核对包含分组变换的等大对照图，原 PNG 与启动前景的 SHA256 均未变化，新矢量仅默认颜色不同。Debug APK 已刷新至 `dist/xiaohongshu-clone-debug.apk` 并核对 SHA256，未进行真机验收。

2026-10-30 新绘图标的源 SVG 位于 `docs/assets/icons-2026-10-30/`。仍在使用的图标在 `app/src/main/res/drawable/` 有同名 VectorDrawable，均为 24 视口、1.8dp 圆角描边，图形外缘长边约 19.5dp。

> 2026-10-18 更新：新增两个 iconfont **实心**字形（24 视口 / 占位按长边 19.5dp）——`ic_pin_top`（置顶：上箭头 + 顶横条）已接线到 `MessageRoutes.kt` 会话行的 12dp 置顶位，上表对应条目已移除；`ic_notify_mute`（消息免打扰：带斜杠的铃铛）**当前无调用方**，留作备用（会话模型尚无免打扰字段）。源 SVG 为 `docs/assets/top-filling.svg` 与 `docs/assets/消息免打扰.svg`（后者转换时用了临时 ASCII 副本，仓库只保留中文名源）。

> 2026-10-03 更新：新增 8 个 iconfont 线形图标（24 视口 / 1.8dp / 占位 18~20dp，源 SVG 为 `docs/assets/` 下中文名文件）：`ic_delete`（垃圾桶）、`ic_plus_circle`（圆环＋）、`ic_discover`（指南针）、`ic_image`（图片）、`ic_camera`（相机）、`ic_emoji`（笑脸）、`ic_settings`（齿轮），以及**换代的 `ic_more`**——旧三点版与 `ic_menu` 语义重复已废弃，现为「圆环+三点」（AiRoute 一处引用自动生效）。当前接线与剩余占位以上表为准。

> 2026-09-15 更新：底 Tab 改为**纯文字无图标**（对齐原版），故 `ic_home`、`ic_notify` 已无用例（文件保留但不再引用）；`ic_assistant` 仍用于首页顶栏点点气泡与 H1 会话页。
>
> 2026-09-17 更新（第一批）：新增 `ic_share`、`ic_scan`、`ic_edit`、`ic_broom`、`ic_play` 五个素材并已接线（分享 3 处 / 扫一扫 / 编辑主页 pill / 一键已读 / 视频角标与发布页缩略图），上述清单里对应条目已移除。`ic_heart` / `ic_heart_filled` / `ic_star_filled` / `ic_assistant` 同时修掉了线宽与配对跳动问题。
>
> 2026-09-17 更新（第二批）：`ic_chevron_left/right`（1.5→1.8dp）、`ic_close`（2.0→1.8dp）统一线宽并重绘为「中线 + 描边」；就地画的 ☰ 与 人形 落地为 `ic_menu`（3 处调用：首页/个人页顶栏、评论排序）、`ic_user_circle`（消息页「新增关注」入口），Canvas 字形 `XhsMenuGlyph` / `XhsPersonGlyph` 删除。`ic_user`（实心人形）已转换但**当前无用例**，留作备用。

### 8.1 图标规范与源文件

**规范**：24 视口（`viewportWidth/Height=24`，`width/height=24dp`）+ 描边 1.8dp + 图形占位 18~20dp（工具/结构性字形例外）+ 填充版与描边版必须同外缘。细则见 `客户端架构与开发规范.md` §4.5.1。

**源文件**：共用那批图标（heart / star / comment / search / close / chevron / more / home / notify / about / live / male / female / publish / red / assistant / ai-* 等 22 个）的**源 SVG 在 `frontend/src/assets/icons/`**——Android 的 drawable 就是从这里转出来的；新下载的 `user.svg` / `user_circle.svg` 在 `client/docs/assets/`。2026-10-18 新增的 `top-filling.svg`（`ic_pin_top`）与 `消息免打扰.svg`（`ic_notify_mute`）也在 `client/docs/assets/`。（第一批下载的 `分享 / 扫一扫 / 扫帚 / 播放_填充 / 编辑` 五个源 SVG 已不在仓库里，**以 `res/drawable/*.xml` 为准**。）它们是**设计源**，不是可编译资源；再要新增或改动图标时，按上一节规范改 `res/drawable/*.xml`（或重新走一遍 SVG→VectorDrawable 转换），**不要把 .svg 放回 `res/drawable/`**（aapt2 只收 `.xml`/`.png`，放了会直接构建失败）。

> ⚠️ **跨端待办**：这批线宽统一只做了 Android 侧，`frontend/src/assets/icons/` 里同名图标仍是老线宽（heart / chevron 6.25%、close 8.3%），两端现在不一致。要对齐就照规范 §4.5.1 第 2 条处理 Web 那批。

---

## 9. 需要你拍板的几个判断（我做了决定，但可以改）

1. **登录页协议勾选**：未勾选时「登录」按钮禁用（对齐真实 App，且复用了既有禁用态样式）；设计未明确，此处为自拟决定。
2. **「帮助」链接**：Toast「帮助内容暂未提供」，不新建页面（占位）。协议两个书名号链接为纯 no-op。
3. **F1/F2 头图遮罩与文字**（2026-10-18 已定）：背景图加 `ProfileHeaderScrim` 黑色遮罩；用户名、三个统计数值、简介及空简介引导用 `Text1Dark`，小红书号及三个统计标签用 `Text3Dark`。色值以 `XhsColor` 为准。
4. **卡片脚栏 ♥ 尺寸取 16dp**：30dp 图标会压过 12sp 计数，且 30dp 留给详情/视频底栏互动图标，故卡片脚栏取 16dp 档，详情底栏仍用 30dp。
5. **E3 左上用 × 而不是 ←**：× 直接放弃返回（定稿行为）。
6. **G2 未单独做「查看历史消息」控件**：按规范统一用分页页脚（「没有更多了」/「加载更多」）。
7. **未实现「直播」**：无对应接口与页面，且 E1 的「拍摄」按你的说明只做拍摄。
8. **G4「新增关注」整行点击 → F2 对方主页**（2026-10-18 修正）：关注类通知（`type=6`）没有 `postId`，原先照搬「点条目 → C1-1」的写法使整行点击只剩标已读、无跳转（点空白处「没反应」）。现按线框图 g4「点击头像/条目 → F2」把行与头像统一到 F2；G2/G3 仍按 `postId > 0` 跳 C1-1。代价：关注行失去「点行跳笔记」的假想入口（本就不存在），换来整行可点的一致性。

---

## 10. 明确不在范围内的

- 后端实现（契约已备好，见 §3）。
- 单元测试 / 集成测试 / E2E 自动化脚本（按要求不做；本 APP 由你负责 Code Review 与 E2E 验收）。
- 正式签名密钥与上架配置（见 §1 签名说明）。
- 「关注/粉丝列表页」「合集」「话题」「位置」「可见范围」「评论表情/配图」「置顶评论」「IP 属地」「私信」等已显式排除或标注为占位的功能。
- 明暗主题：产品为浅色为主 + 视频页局部深色沉浸，**不跟随系统暗色**（与原版行为一致）。

---

## 11. 个人主页重构（2026-10-18）

- F1/F2 共用 `ProfileScreen` 与 `ProfileUiState`，删除各自的 Screen 和重复展示状态；抽屉移入 `ProfileDrawer.kt`，继续由 `MainScaffold` 挂载。
- `isMe` 决定编辑入口、简介引导、关注/私信和页签，`ProfilePresentation` 独立决定菜单或返回。两个 Route、ViewModel 实例及各页签滚动状态继续独立。
- 本人资料统一订阅 `SessionManager.state`，推入的本人主页也能即时反映编辑回写；本人主页的头部下方间距统一。失去本人身份时「赞过」回到「笔记」。
- 页签懒加载、列表四态、分页页脚统一实现；游客只请求公开笔记，收藏不发受保护请求，F1 保留登录兜底。
- 补充：F1/F2 复制小红书号后统一提示「小红书号复制成功」，仅在成功写入系统剪贴板后触发全局 Toast；空文本和剪贴板服务不可用时不提示成功。
- 补充：`XhsColor` 新增 `Text1Dark / Text2Dark / Text3Dark` 和 `ProfileHeaderScrim`，F1/F2 头图统一遮罩与文字颜色，`XhsColors` 的深色文字访问器复用色板。
- 补充：性别图标移到 `ProfileBioRow` 下方独立一行并左对齐，沿用现有资源、尺寸与颜色；未知或保密时隐藏整行，无简介的他人主页仍可显示性别。
- 补充：`ProfileSegmentItem` 改为 `UnderlineLabel` 的文字与红色圆角下划线样式，选中加粗；`ProfileTab.iconRes` 和条目图标参数默认 `null`，隐藏「笔记/收藏/赞过」三个图标及间距。

### 已知取舍

- 放弃两份布局分别演进，统一为一套主页内容；保留两个 ViewModel 的取数与生命周期，避免把 Tab 根页与推入页的分页和滚动状态绑在一起。
- 沿用他人仅「笔记/收藏」、游客收藏为空态的现有裁剪；不新增隐私设置、私密笔记、草稿或关注/粉丝列表。
- 当前代码原本已不渲染小组件卡和「去发布」banner，本次同步清理旧说明；计数字段仍保留既有接口语义。
- 性别独立成行后，资料头从固定高度改为最小高度，顶部留白参与测量，换取访客操作行与页签不重叠；背景图与遮罩随内容增高。
- 主页页签放弃原图标加文字的默认展示，换成文字加下划线；保留显式传入图标的能力，现有 drawable 素材继续保留。
- 当时保留的图标占位已在后续补齐，当前资源与图片占位规则见 §8、§16；扫一扫/分享和更多菜单的业务范围沿用各轮记录。头图按用户指定使用均匀黑色遮罩，放弃此前待议的渐变方案；仅在资料头显式使用暗色文字，页签、瀑布流及全局浅色主题沿用原配色。

### 验证

- 主页页签样式补充：`./gradlew :app:compileDebugKotlin :app:assembleDebug --offline` 通过，零编译 error / warning；三个页签的图标默认 `null`，未修改 drawable。Debug APK 已刷新至 `dist/xiaohongshu-clone-debug.apk`，与构建产物 SHA256 一致，未进行真机交互验收。
- 性别独立成行补充：`./gradlew :app:compileDebugKotlin :app:assembleDebug --offline` 通过，零编译 error / warning；现有两枚性别图标按徽标例外体检，0 项需处理，等大对照图已核对。Debug APK 已刷新至 `dist/xiaohongshu-clone-debug.apk`，与构建产物 SHA256 一致，未代为安装。
- `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --offline` 通过，零编译 error / warning；现有 11 项单元测试全部通过。
- `git diff --check` 通过；旧 Screen / UiState 引用已清理，并按架构规范 §8 检查 Token、列表四态、互动、登录拦截和交付范围。本轮未进行真机交互验收。

---

## 12. 页签高度解耦（2026-10-19）

- 个人页整行与单个页签改用 `Dimens.profileSegmentBar`，评论面板页签栏改用独立的 `Dimens.commentPanelTabBar`；删除旧共用常量，三个引用全部迁移。
- 已知取舍：两个常量保留原高度，独立赋值，避免今后调整其中一处时改变另一处的布局。
- 验证：`./gradlew :app:compileDebugKotlin --offline` 通过，零 error / warning；旧共用常量无残留引用，`git diff --check` 通过，并按架构规范 §8 完成适用项自检。


---

## 13. 个人主页资料头滚动与顶栏过渡（2026-10-26）

- F1/F2 的 `ProfileHeader` 随瀑布流滚动收起，`ProfileFloatingTopBar` 拆至 `ProfileTopBar.kt` 并固定覆盖顶部；页签最终停在顶栏下方。
- `ProfileCollapsingLayout` 协调上滑收起、列表滚动及回滑展开，资料头与无内容四态也支持拖动。共享当前主页的头部收起量，各页签保留独立列表位置，换用户重置。
- 顶栏颜色由背景图平均色叠加头图遮罩得到，复用图片加载缓存并在后台计算；缺图/失败使用占位底色与同款遮罩。背景 URL 更换后重新取色。
- 按实测头像底部与顶栏底部的距离将背景透明度从 0 插值到 1；越过阈值后隐藏编辑入口，顶栏居中头像向上推入；回滑越过阈值时向下退出并恢复编辑入口。
- 现有资料内容、身份规则、游客请求门禁和分页四态保持原有实现；顶栏拦截穿透点击，完全收起的资料头从无障碍语义中隐藏；未新增素材占位或修改公共组件。

### 已知取舍

- 资料头通过嵌套滚动和实测布局收起，以保留现有分页索引和每个页签的瀑布流状态；页签固定在顶栏下方。只有列表回到顶部后才展开资料头。
- 取色使用网格采样平均色并叠加现有遮罩，保留背景色调和浅色控件对比度；不增加调色板依赖。

### 验证

- `:app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest --offline` 通过，零编译 error / warning；新增 5 项测试覆盖滚动消费顺序、边界、透明度阈值、回滑与尺寸变化，连同原有 11 项测试全部通过。
- 模拟器实测本人/他人主页展开、部分收起、完全收起、回滑恢复、各页签切换、头部区域拖动及空收藏页上滑/回滑；顶栏纯色、居中头像与编辑入口切换均符合预期。
- `git diff --check` 通过；按架构规范 §8 完成设计 Token、列表四态、身份与登录门禁、文件范围自检。本轮 Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，已安装至现有模拟器。

---

## 14. 复用现有图标（2026-10-30）

- 接入 7 个语义匹配的图标槽位，固定占位由 28 个减至 21 个，具体映射与剩余清单见 §8；同步修正架构规范中的旧空态插画组件引用。
- 已知取舍（本轮历史状态）：仅接入已有 drawable，沿用原尺寸、颜色与点击行为；当时保留的语音、复制、踩、重新生成等占位已在 §15 补齐，图片占位现按 §16 分场景处理。
- `:app:compileDebugKotlin :app:assembleDebug --offline` 通过，零编译 error / warning；Debug APK 已刷新至 `dist/xiaohongshu-clone-debug.apk`。按用户要求未运行测试，未进行真机或模拟器验收。

---

## 15. 补全剩余图标（2026-10-30）

- 手工绘制 21 枚 SVG 并生成同名 Android VectorDrawable，接入 AI、抽屉、设置、视频作者行及长文卡片；固定图标占位由 21 个减至 0 个。设计规范、逐项清单及两张预览图见 `docs/assets/图标清单-2026-10-30.md`。
- 已知取舍：抽屉底部三格采用夜间模式、二维码、意见反馈候选字形，沿用无业务行为的装饰性展示；其余位置沿用原尺寸、颜色与点击回调。图片加载及视频地址为空时的兜底占位保留。
- 21 枚新图标几何检查通过，并完成与现有图标的等大对照；全量扫描报告的 `ic_about` 线宽与 `ic_live` 外缘偏差属于既有资源，记录在图标清单中。
- `:app:compileDebugKotlin :app:assembleDebug --offline` 通过，零编译 error / warning；Debug APK 已刷新至 `dist/xiaohongshu-clone-debug.apk`，与构建产物 SHA256 一致。沿用用户要求未运行单元测试或界面测试，未进行真机或模拟器验收。

---

## 16. 分场景图片占位与死代码清理（2026-11-12）

- 全局头像与编辑资料的本地头像预览使用原色 `ic_avatar_fallback`，覆盖加载中、空引用与失败；个人页背景、AI 推荐封面、通知缩略图不叠加占位图案。
- 发布页本地媒体缩略图和编辑资料的本地背景预览使用淡色 `ic_image`（`XhsColor.Text3`）；视频地址为空时使用 `ic_logo`。沿用各场景的容器、裁剪与成功加载后的图片显示方式。
- 已知取舍：放弃用单一缺失素材图案提示所有图片问题，改为按场景选择已有资源；通用 `XhsAsyncImage` 默认只保留容器，显式传入 `fallbackPainter` 时在尚无位图的状态显示兜底图片。
- 删除两份旧占位资源、`PlaceholderIconRes` 和 `showGlyphOnFailure`，清除未使用的 `XhsPlaceholderBox`、`XhsCoverImage` 与 `XhsLocalImage.kt` 及其媒体包装组件；同步清理调用参数、文档规则和审计分类。
- 验证：`:app:compileDebugKotlin :app:assembleDebug --offline` 通过，零编译 error / warning；源码无旧占位引用，APK 资源表确认旧资源已移除、替代资源均已打包。Debug APK 已刷新至 `dist/xiaohongshu-clone-debug.apk`，与构建产物 SHA256 一致。未运行界面测试或进行真机验收。

---

## 17. 移除独立设置页（2026-11-22）

删除 F5 页面、导航和个人主页抽屉的「设置」入口，并清理专用的设计 Token 和 12 个 Android 图标资源。

### 已知取舍

设置分类和「切换账号」占位随 F5 移除。退出登录由 F4 抽屉直接打开 F6 确认弹层，确认后回到游客首页（A1）。

### 验证

- `:app:compileDebugKotlin :app:assembleDebug --offline` 通过，零编译错误和警告。源码引用检查与 `git diff --check` 通过。
- APK 资源表已确认移除 12 个图标，抽屉的 6 个图标均已打包。已刷新 `dist/xiaohongshu-clone-debug.apk` 并核对构建产物的 SHA256。
