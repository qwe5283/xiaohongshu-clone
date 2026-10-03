# 小红书复刻 · Android 客户端 API 契约 v2

> 基线：`docs/API接口文档.md`（后端现有实现）+ 后端源码实测（notification / ai 模块已存在但旧文档未收录）。
> 本文 = **客户端唯一依赖的契约**。相对后端的差异分两类：
> - **[已有]** 后端已实现，客户端直接调用（旧文档遗漏或描述有误的，以本文为准）。
> - **[新增]** 后端需实现（客户端开发期由 mock server 提供）。
>
> 通用约定
> - Base URL：`http://<host>:8080`，所有路径前缀 `/api`（下文均已含前缀）。
> - 认证：`Authorization: Bearer <token>`。
> - 响应信封：`{ "code": 200, "message": "操作成功", "data": <T>, "timestamp": 1789000000000 }`。
>   `code == 200` 为成功，其余为业务失败（HTTP 状态码见 §9）。客户端只读 `code`/`message`/`data`，忽略 `timestamp`。
> - 分页信封（MyBatis-Plus `IPage`）：`{ "records": [...], "total": 0, "size": 20, "current": 1, "pages": 0 }`。
>   **注意**：字段是 `size`/`current`，不是 `pageSize`/`pageNum`。请求参数才是 `pageNum`/`pageSize`。
> - 分页参数：`pageNum`（≥1，默认 1）、`pageSize`（默认 **20**，1–100）。客户端所有列表统一传 `pageSize=20`。
> - 时间格式：`yyyy-MM-ddTHH:mm:ss`（`LocalDateTime` 无时区），客户端本地化展示。
> - 空集合返回 `[]`，不返回 `null`；可空字符串返回 `""`。
> - 客户端统一超时 **15s**（AI 对话 70s）。

---

## 0. 契约变更总表（转发后端用）

| # | 类型 | 端点/对象 | 变更 | 用途（客户端屏号） |
|---|------|-----------|------|-------------------|
| 1 | 新增字段 | `PostVO.collected:Boolean` | 当前用户是否已收藏笔记 | C1-1 底栏 ★ 态、D1 |
| 2 | 新增字段 | `PostVO.followed:Boolean` | 当前用户是否已关注该笔记作者 | C1-1/C2-1 作者栏关注钮、D2 |
| 3 | 新增端点 | `GET /api/post/following-feed` | 关注流分页 | B5 |
| 4 | 新增端点 | `GET /api/like/posts/{userId}` | 某用户「赞过」的笔记分页 | F1「赞过」Tab |
| 5 | 新增端点 | `GET /api/post/hot-keywords` | 热搜/猜你想搜词条 | B2「猜你想搜」 |
| 6 | 新增字段 | `unread-count` 响应 +3 个分类未读数 | 三入口/底 Tab 角标 | G1 |
| 7 | 新增参数 | `GET /api/notification/list?category=1\|2\|3` | 通知按入口分类查询 | G2/G3/G4 |
| 8 | 新增字段 | `UserVO` + `backgroundImage/birthday/region/occupation/school/redId` | 资料页字段 | F3 |
| 9 | 新增字段 | `UserUpdateDTO` 同上（可编辑项） | 资料保存 | F3 |
| 10 | 新增字段 | `ChatResponseVO.notes:PostVO[]` | AI 回复携带笔记卡 | H2 |
| 11 | 新增端点 | `GET /api/ai/suggestions` | 点点空态建议问题 | H1 |
| 12 | 新增端点 | `POST /api/post/text-image` | 文字配图**并返回 URL**（免客户端二次上传） | E3 |
| 13 | 语义明确 | `PostVO.commentCount` | = 一级评论数 **+ 回复数** 总和 | C1-1「共 n 条评论」与底栏 💬 一致 |
| 14 | 语义明确 | `NotificationVO.type` | 以实体注释为准：1..6（见 §7.2） | G2/G3/G4 |
| 15 | 语义明确 | 列表 `pageSize` 默认 | 建议后端默认值由 10 调为 20（客户端已显式传参，非阻塞） | 全局 |
| 16 | 新增字段 | `UserVO` + `collectedPostCount` / `likedPostCount` | **我**收藏/赞过的笔记数（见下方语义说明） | F1 小组件行副文 |
| 17 | 新增端点 | `GET /api/user/search` | 用户搜索分页（昵称/小红书号/登录账号模糊匹配） | B3-1「用户」页签 |
| 18 | 语义明确 | `GET /api/post/list` 的 `type` | B3-1 页签取数：**全部不传 `type`**、**视频传 `type=1`** | B3-1「全部/视频」页签 |
| 19 | 新增字段 | `UserBriefVO` + `redId` / `followersCount` | B3-1 行副文：小红书号 / 粉丝数 | B3-1「用户」页签 |
| 20 | 语义收紧 | `POST /api/post/create` / `PUT /api/post/update` 媒体结构 | **二选一**：图文 = 1–9 图无视频；视频 = 1 视频 + **恰好 1 张封面图**（客户端默认填充视频首帧，用户可重新选择）。服务端校验：视频请求不允许缺省封面（`2006`），其余非二选一结构拒绝（`2007`） | E1/E2/E6 发布 |

### 语义说明：`likeCount` / `collectCount` 与新增两字段的区别（易混淆，必读）
原后端 `UserVO.likeCount` / `collectCount` 的既有语义是 **获赞数 / 获藏数**（*别人*赞了我/收藏了我的笔记的累计，用于 F1 统计行），**不是**我赞过/收藏过多少。两者含义相反，故新增两个字段：

| UserVO 字段 | 含义 | 用途 |
|-------------|------|------|
| `followingCount` / `followersCount` | 关注数 / 粉丝数 | F1 统计行 |
| `likeAndCollectCount` | **获赞与收藏**总数（收到） | F1 统计行第三项 |
| `likeCount` / `collectCount` | 获赞数 / 获藏数（收到，兼容旧语义） | 暂不展示（保留） |
| **`collectedPostCount`** [新增] | **我收藏的笔记数** | F1 小组件卡「收藏」副文 |
| **`likedPostCount`** [新增] | **我赞过的笔记数** | F1 小组件卡「赞过」副文 |

> 小组件卡在 F1 首屏渲染，早于「收藏/赞过」Tab 的懒加载，因此必须在用户资料接口里返回，不能靠列表 `total` 兜底。
> 这两个数字需与 `GET /api/collect/posts/{me}`、`GET /api/like/posts/{me}` 的 `total` 一致（收藏/取消收藏、点赞/取消点赞时同步维护）。
> 他人主页（F2）不展示小组件行，故这两个字段对非本人可为 0。

> 客户端在 mock server 下开发，**以上 20 项均按本文实现**。后端实现完成后，客户端只需把 Base URL 切到真实服务，无需改代码。

---

## 1. 用户模块

### 1.1 注册 — [已有] `POST /api/user/register` · 公开
请求：`{ "username": "testuser", "password": "test123456", "nickname": "测试用户", "phone": "13900001111" }`
- `username` 必填 3–20；`password` 必填 6–20；`nickname` 可选 ≤20；`phone` 可选 `1[3-9]\d{9}`。
响应 `data`：`UserVO`（§1.7）。
失败：`1003` 用户名已存在；`5001` 参数不合规。

### 1.2 登录 — [已有] `POST /api/user/login` · 公开
请求：`{ "username": "admin", "password": "123456" }`
响应 `data`：
```json
{ "token": "eyJhbGciOiJIUzM4NCJ9...", "expiresIn": 604800, "user": { /* UserVO */ } }
```
失败：`1001` 用户不存在、`1002` 密码错误、`1004` 用户被禁用。
> 客户端：注册成功**不自动登录**，回登录页预填用户名（A4 → A3）。

### 1.3 当前用户 — [已有] `GET /api/user/me` · 🔒
响应 `data`：`UserVO`。用于启动时静默校验恢复登录态（I1）。

### 1.4 用户详情 — [已有] `GET /api/user/{id}` · 公开
响应 `data`：`UserVO`。（F2 他人主页）

### 1.5 更新资料 — [已有，字段扩充] `PUT /api/user/update` · 🔒
请求（全部可选，仅传有变化的字段）：
```json
{
  "nickname": "新昵称", "avatar": "https://...", "gender": 1,
  "email": "t@example.com", "bio": "简介",
  "backgroundImage": "https://...",
  "birthday": "1998-06-01",
  "region": "北京",
  "occupation": "设计师",
  "school": "某某大学"
}
```
- `nickname` ≤20；`bio` ≤200；`gender` 0-未知/1-男/2-女；`birthday` `yyyy-MM-dd` 或 `""` 清除。
- 头像/背景图先走 §8 上传得到 URL 再提交。
响应 `data`：更新后的 `UserVO`。
> **F3 行序（实测）**：名字 / 小红书号(不可编辑) / 背景图 / 简介 / 性别 / 生日 / 地区 / 职业 / 学校。

### 1.6 [新增] `redId` — 小红书号
`UserVO.redId: String`，展示用唯一号（建议后端返回用户 ID 的字符串形式，或独立生成的不重复编号）。只读，F3 该行不可点击。

### 1.7 `UserVO`
```json
{
  "id": 1, "username": "admin", "nickname": "管理员",
  "avatar": "https://...", "gender": 1, "phone": "13800138000", "email": "",
  "bio": "系统管理员",
  "backgroundImage": "", "birthday": "", "region": "", "occupation": "", "school": "",
  "redId": "1",
  "followingCount": 5, "followersCount": 10,
  "likeCount": 100, "collectCount": 50, "likeAndCollectCount": 150,
  "collectedPostCount": 8, "likedPostCount": 12,
  "createTime": "2026-06-23T09:22:47"
}
```
> F1 统计行：关注 / 粉丝 / **获赞与收藏**（`likeAndCollectCount`）。
> F1 小组件行两卡副文：收藏（`collectedPostCount`）/ 赞过（`likedPostCount`）—— 含义见 §0 语义说明，**勿用** `likeCount`/`collectCount`。

### 1.8 [新增] 用户搜索 — `GET /api/user/search` · 公开（带 token 时填充 `followed`）
参数：`keyword`（必填，模糊匹配 `nickname` / 小红书号 `redId` / 登录账号 `username`，忽略大小写）、`pageNum`、`pageSize`。
响应 `data`：`IPage<UserBriefVO>`（§1.9）。
失败：`5002` 参数缺失（`keyword` 为空或缺省）。
> B3-1「用户」页签：单独的用户列表流（非瀑布流）。条目＝头像 48 + 昵称 15sp + 副文两行 13sp（`粉丝 n` / `小红书号：…`，即 `followersCount` / `redId`）+ 右侧关注钮（`XhsFollowPill`，同一 D2 状态机）；点条目 → F2。
> 「全部 / 视频」两页签**无新端点**，走 §2.5 的 `keyword`+`type`：全部不传 `type`，视频传 `type=1`（变更 #18）。

### 1.9 `UserBriefVO`
```json
{ "id": 6, "nickname": "林小满", "avatar": "https://...", "bio": "早秋穿搭 / 通勤 ootd", "redId": "100822", "followersCount": 128, "followed": false }
```
> `followed` 语义同 `PostVO.followed`：当前用户是否已关注该用户；未登录一律 `false`。关注后由客户端 `POST /api/follow/{userId}` 维护。
> `redId` / `followersCount`（变更 #19）为 B3-1 行副文：小红书号 / 粉丝数。

---

## 2. 笔记模块

### 约束
- 媒体结构**二选一**（互斥，变更 #20；服务端校验并拒绝其他结构）：
  - **图文笔记**：`imageUrls` 1–9 张，`videoUrl` 缺省或 `""`；
  - **视频笔记**：`videoUrl` 恰好 1 个 + `imageUrls` **恰好 1 张 = 封面**。封面由客户端生成（默认取视频首帧，用户可重新选择）；**服务端校验发布视频的请求不允许缺省封面**（`imageUrls` 为空 → `2006`）。
- `type` 由后端推导（有视频→1，仅图→0）。
- `coverImage` 由后端生成：图文笔记取首图；视频笔记取封面（即 `imageUrls[0]`）。

### 2.1 创建 — [已有] `POST /api/post/create` · 🔒
图文笔记（1–9 张图、无视频）：
```json
{ "title": "标题", "content": "正文", "videoUrl": "", "imageUrls": ["https://a.jpg", "https://b.jpg"] }
```
视频笔记（恰好 1 视频 + 恰好 1 封面图）：
```json
{ "title": "标题", "content": "正文", "videoUrl": "https://v.mp4", "imageUrls": ["https://cover.jpg"] }
```
- `title` 必填 ≤200；`content` ≤10000。
- 失败：`2005` 无媒体（图/视频全空）、`2004` 图片超 9（图文笔记）、`2006` 视频笔记缺省封面、`2007` 媒体结构不合法（图文带视频 / 视频带多图等非二选一结构）。
响应 `data`：`PostVO`。

### 2.2 更新 — [已有] `PUT /api/post/update` · 🔒（仅作者，`imageUrls` 为全量替换；媒体结构约束同 2.1，失败码同上）
### 2.3 删除 — [已有] `DELETE /api/post/delete/{postId}` · 🔒（仅作者）

### 2.4 详情 — [已有，字段扩充] `GET /api/post/{postId}` · 公开（浏览量 +1）
响应 `data`：`PostVO`，**新增 `collected`、`followed`**。

### 2.5 笔记列表 — [已有] `GET /api/post/list` · 公开
参数：`pageNum`、`pageSize`、`keyword`（标题 LIKE）、`type`、`sortType`(`latest`|`hot`)。
响应 `data`：`IPage<PostVO>`。（B1 首页发现流、B3 搜索结果）

### 2.6 用户的笔记 — [已有] `GET /api/post/user/{userId}` · 公开（参数同 2.5）→ F2「笔记」
### 2.7 我的笔记 — [已有] `GET /api/post/my` · 🔒（参数同 2.5）

### 2.8 [新增] 关注流 — `GET /api/post/following-feed` · 🔒
参数：`pageNum`、`pageSize`。响应 `data`：`IPage<PostVO>`，按创建时间倒序，仅含已关注作者的已发布笔记。
> B5：无关注或关注者无笔记 → 空数组（客户端渲染空态「还没有关注的人，去发现逛逛吧」）。

### 2.9 [新增] 热搜词 — `GET /api/post/hot-keywords` · 公开
响应 `data`：`["咖啡拉花", "周末徒步", ...]`（`string[]`，建议 6–10 条）。
> B2「猜你想搜」两列展示。

### 2.10 文本配图 — `GET /api/post/text-image/generate?text=` · 公开（≤20 字，返回 `image/png` 二进制）
### 2.11 [新增] 文本配图并返回 URL — `POST /api/post/text-image` · 公开
请求：`{ "text": "所写文字（建议 ≤100 字，超出后端截断）" }`
响应 `data`：
```json
{ "url": "http://minio:9000/xiaohongshu/images/2026/09/14/uuid.png", "width": 800, "height": 1200 }
```
> E3：客户端用本地写入的文字调用本接口，拿到 URL 后加入 E2 媒体列表（作为唯一图片），标题预填为所写文字。
> 后端可直接复用现有 `TextImageService` 生成 2:3 PNG 并落 MinIO。失败 → 客户端 Toast「生成配图失败，请稍后重试」并停留 E3（不允许无素材笔记进入 E2）。

### 2.12 `PostVO`
```json
{
  "id": 1, "userId": 1, "authorNickname": "管理员", "authorAvatar": "https://...",
  "title": "笔记标题", "content": "正文",
  "type": 0, "coverImage": "https://...", "videoUrl": "",
  "images": [ { "id": 1, "imageUrl": "https://...", "sortOrder": 1, "width": 400, "height": 300 } ],
  "viewCount": 101, "likeCount": 50, "commentCount": 10, "collectCount": 20,
  "liked": false,
  "collected": false,
  "followed": false,
  "status": 1, "createTime": "2026-06-23T10:00:00", "updateTime": "2026-06-23T10:00:00"
}
```
- `images` 按 `sortOrder` 升序；`width`/`height` 供客户端按真实宽高比布局（瀑布流 4:3 / 3:4，详情全宽 3:4）。
- 媒体结构互斥（变更 #20）：`type=1`（视频）时 `images` **恰好 1 张 = 封面**、`videoUrl` 非空；`type=0`（图文）时 `images` 1–9 张、`videoUrl` 为 `""`。
- 未登录时 `liked`/`collected`/`followed` 一律 `false`。

---

## 3. 评论模块

### 3.1 发表评论/回复 — [已有] `POST /api/comment/create` · 🔒
```json
{ "postId": 1, "content": "评论内容", "parentId": 0, "replyUserId": 0 }
```
- `content` 必填 ≤500（后端校验，前端无计数）；一级评论 `parentId=0`、`replyUserId=0`；回复时 `parentId` = 一级评论 ID，`replyUserId` = 被回复者 ID。
响应 `data`：`CommentVO`。

### 3.2 删除评论 — [已有] `DELETE /api/comment/delete/{commentId}` · 🔒（仅作者）
> 客户端未设删除入口，不调用；契约保留。

### 3.3 一级评论列表 — [已有] `GET /api/comment/post/{postId}` · 公开
参数：`pageNum`、`pageSize`（每页 **20**）。响应 `data`：`IPage<CommentVO>`（`total` = 一级评论数），按时间倒序。

### 3.4 回复列表 — [已有] `GET /api/comment/replies/{commentId}` · 公开
参数：`pageNum`、`pageSize`。响应 `data`：`IPage<CommentVO>`，按时间正序。
> C3：每次「展开 N 条回复」/「展开更多回复」拉取 **10 条**（`pageSize=10`），就地平铺，全部加载完后控件消失（无「收起」）。N = 该组回复总数（`CommentVO.replyCount`，无上限）。

### 3.5 `CommentVO`
```json
{
  "id": 1, "postId": 1, "userId": 1, "userNickname": "张三", "userAvatar": "https://...",
  "content": "写得真好", "parentId": 0, "replyUserId": 0, "replyUserNickname": "",
  "likeCount": 5, "liked": false, "replyCount": 3,
  "createTime": "2024-01-01T12:00:00"
}
```
> C1-1：`userId == post.userId` 的评论展示「作者」徽章（客户端判定）。
> 「共 n 条评论」取 `PostVO.commentCount`（含回复），与底栏 💬 一致（变更 #13）。

---

## 4. 点赞模块（toggle）

| 端点 | 权限 | 说明 |
|------|------|------|
| `POST /api/like/post/{postId}` | 🔒 | 响应 `{ "liked": true, "message": "点赞成功" }` |
| `POST /api/like/comment/{commentId}` | 🔒 | 同上 |
| `GET /api/like/status/post/{postId}` | 🔒 | `{ "liked": true }` |
| `GET /api/like/status/comment/{commentId}` | 🔒 | `{ "liked": true }` |
| **`GET /api/like/posts/{userId}` [新增]** | 🔒 | 「赞过」列表，参数 `pageNum`/`pageSize`，响应 `IPage<PostVO>`，按点赞时间倒序 |

> D1：客户端**乐观更新 + 失败回滚**；游客点击 → 推入登录页（A2），不发请求。
> 列表页赞数显示规则：`0` → 文字「赞」；`>0` → 数字（≥10000 → `1.2万`）。

---

## 5. 收藏模块（toggle）

| 端点 | 权限 | 说明 |
|------|------|------|
| `POST /api/collect/post/{postId}` | 🔒 | 响应 `{ "collected": true, "message": "收藏成功" }` |
| `GET /api/collect/status/post/{postId}` | 🔒 | `{ "collected": true }` |
| `GET /api/collect/posts/{userId}` | 🔒 | 用户收藏列表（可看他人），`IPage<PostVO>`，按收藏时间倒序 → F1「收藏」/F2「收藏」 |

---

## 6. 关注模块（toggle）

| 端点 | 权限 | 说明 |
|------|------|------|
| `POST /api/follow/{userId}` | 🔒 | 响应 `{ "followed": true, "message": "关注成功" }`；关注自己 → `6003` |
| `GET /api/follow/status/{userId}` | 🔒 | `{ "followed": true }` |
| `GET /api/follow/following/{userId}` | 公开（带 token 时填充 `followed`） | `IPage<FollowUserVO>` |
| `GET /api/follow/followers/{userId}` | 同上 | `IPage<FollowUserVO>` |
| `GET /api/follow/count/{userId}` | 公开 | `{ "followingCount": 2, "followersCount": 3 }` |

> D2：详情作者栏＝描边胶囊；他人主页＝通栏大按钮；同一状态机。自己的笔记/主页不显示关注钮。
> F2：关注数/粉丝数仅数字展示，**无**列表页（客户端不调用 following/followers 列表）。

---

## 7. 消息通知模块 — [已有]（旧文档遗漏）

### 7.1 未读数 — `GET /api/notification/unread-count` · 🔒 · **[字段扩充]**
```json
{ "unreadCount": 12, "likeUnread": 5, "commentUnread": 4, "followUnread": 3 }
```
- `unreadCount` = 总数（底 Tab 角标，>99 显示 `99+`）。
- 三个分类未读数分别对应 G1 三入口卡角标，**为 0 时客户端隐藏角标**。
> 客户端每 **15s** 轮询本接口（仅登录态、App 处于前台）。未读总数无变化时不做任何 UI 抖动。

### 7.2 通知列表 — `GET /api/notification/list` · 🔒 · **[参数扩充]**
参数：`pageNum`、`pageSize`、`category`、`type`。
- **[新增] `category`**：`1` = 赞和收藏（G2）、`2` = 评论和@（G3）、`3` = 新增关注（G4）。
- `type` 保留（细分）：`1` 点赞笔记、`2` 收藏笔记、`3` 评论笔记、`4` 回复评论、`5` 点赞评论、`6` 新增关注。
- 映射：`category=1 → type∈{1,2,5}`；`category=2 → type∈{3,4}`；`category=3 → type∈{6}`。
响应 `data`：`IPage<NotificationVO>`，按时间倒序。

### 7.3 单条已读 — `PUT /api/notification/read/{id}` · 🔒
### 7.4 一键已读 — `PUT /api/notification/read-all` · 🔒
- [建议] 支持 `?category=` 限定范围，未传 = 全部。G6 一键已读作用于**当前分类列表**；后端若只支持全量，客户端仍按全量处理（G6 确认文案已按「当前列表」表述，行为差异可接受）。

### 7.5 `NotificationVO`
```json
{
  "id": 1, "receiverId": 1, "senderId": 2,
  "senderNickname": "用户A", "senderAvatar": "https://...",
  "type": 1, "typeText": "赞了你的笔记",
  "postId": 10, "postTitle": "笔记标题", "postCoverImage": "https://...",
  "commentId": 0, "content": "",
  "replyContent": "",
  "read": false, "createTime": "2026-09-14T10:16:00"
}
```
> G2 缩略图 = `postCoverImage`；`typeText` 建议后端返回中文（如「赞了你的笔记」「收藏了你的笔记」「赞了你的评论」「评论了你的笔记」「回复了你的评论」「关注了你」）。客户端在 `typeText` 为空时按 `type` 本地兜底。
> `replyContent` = 被回复的评论正文，仅 `type=4`（回复评论）返回，其余类型为空串；G3 楼中楼行在正文与操作行之间预览该条（竖体育场形引导条 + 单行截断）。
> 已读条目整行变淡；未读左侧 8dp 红点；点条目 → 先 `read/{id}` 再跳 C1-1（`postId`）；点头像 → F2（`senderId`）。
> 自己操作自己不产生通知（后端保证）。

---

## 8. 文件上传模块 — [已有]

| 端点 | 权限 | 参数 | 响应 |
|------|------|------|------|
| `POST /api/upload/image` | 🔒 | `multipart/form-data`，`file`（图片 ≤10MB） | `{ "url": "https://..." }` |
| `POST /api/upload/video` | 🔒 | `file`（视频 ≤200MB） | `{ "url": "https://..." }` |
| `POST /api/upload/file` | 🔒 | `file` | `{ "url": "https://..." }` |

> E6 提交流程：先逐个上传媒体拿 URL（`上传中` 进度），再 `POST /api/post/create`。
> E3 已由 `POST /api/post/text-image` 直接返回 URL，无需二次上传。

---

## 9. 错误码

| 码 | 含义 | HTTP | 客户端行为 |
|----|------|------|-----------|
| 200 | 成功 | 200 | — |
| 500 | 操作失败 | 400 | 表单错误条 / 全局 Toast |
| 1001 | 用户不存在 | 400 | 全局 Toast（Auth 页） |
| 1002 | 密码错误 | 400 | 全局 Toast（Auth 页） |
| 1003 | 用户已存在 | 400 | 全局 Toast（Auth 页） |
| 1004 | 用户被禁用 | 400 | 全局 Toast（Auth 页） |
| **1005** | 用户未登录 | 401 | **清登录态 → 推入登录页（I1）** |
| **1006** | Token 已过期 | 401 | **同上** |
| **1007** | Token 无效 | 401 | **同上** |
| 2001 | 笔记不存在 | 400 | 详情失败态（C1-3 / C2-3） |
| 2002 | 笔记已删除 | 400 | 同上 |
| 2003 | 无权操作此笔记 | 400 | Toast |
| 2004 | 图片数量超 9 张 | 400 | Toast「最多上传9张图片」 |
| 2005 | 必须含至少一张图片或视频 | 400 | Toast |
| 2006 | 视频笔记必须携带封面图 | 400 | Toast |
| 2007 | 媒体结构不合法（非图文/视频二选一） | 400 | Toast |
| 3001/3002 | 评论不存在/已删除 | 400 | Toast |
| 4001 | 文件上传失败 | 400 | Toast |
| 5001 | 参数错误 | 400 | 按场景 |
| 5002 | 参数缺失 | 400 | 按场景 |
| 6001/6002 | 已关注/未关注 | 400 | 忽略（toggle 幂等） |
| 6003 | 不能关注自己 | 400 | Toast |
| 7001/7002 | 已操作过/未操作过 | 400 | 忽略（toggle 幂等） |

> 客户端判定登录失效：**HTTP 401** 或 **业务码 ∈ {1005,1006,1007}** → `SessionManager` 清 token/用户，并向 UI 广播 `SessionExpired` 事件 → 当前页推入登录页（A3），登录成功回原位置（A6）。
> 网络层错误（超时/DNS/连接失败）→ 全局 Toast（I2 文案集），统一超时 15s。
> HTTP 4xx（非 401）与 HTTP 200 一样携带统一信封：客户端解析 `code/message` 得到业务失败文案（如 1002「密码错误」）；只有 5xx 或信封不可解析时才落到「服务异常，请稍后重试」兜底。

---

## 10. AI 助手「点点」模块 — [已有]（旧文档遗漏）

### 10.1 对话 — `POST /api/ai/chat` · 🔒 · **[字段扩充]**
请求：
```json
{ "message": "帮我规划每周3次的居家有氧运动方案", "systemPrompt": "" }
```
- `message` 必填 ≤4000；`systemPrompt` 可选 ≤1000（客户端不传，由后端用默认人设）。
- **单轮问答**：每次只发当前问题，不携带历史上下文（客户端内存仅用于展示）。
响应 `data`：
```json
{
  "answer": "……（支持 \n 与「• 」列表渲染）",
  "notes": [ /* PostVO[]，可为空数组；H2 渲染为 164×236 笔记卡 */ ]
}
```
> **H2 笔记卡**：AI 可在回答下附带相关笔记推荐。`notes` 为空时只渲染文本气泡。
> **H3-1 思考中**：三点脉冲动画，期间输入框与发送禁用。
> **H3-2 回复失败**：错误以对话流内气泡呈现（正式 UI 为红色），超时文案「请求超时，请检查网络」；失败不中断会话，可直接重新提问，历史消息保留。

### 10.2 [新增] 建议问题 — `GET /api/ai/suggestions` · 🔒
响应 `data`：`["帮我写一段周末徒步的文案", "推荐3个适合新手的妆容", ...]`（`string[]`，建议 3–5 条）
> H1 空态建议问题 chips（高 44，宽 200–220，间距 12），点击直接发送 → H2。
> 客户端在接口失败时使用本地兜底文案，不阻塞。

---

## 11. 客户端本地能力（非后端契约）

| 能力 | 实现 | 说明 |
|------|------|------|
| 搜索历史 | 本地 DataStore | B2 历史 chip；为空时整块隐藏；🗑 清空；点击直接搜该词。建议上限 20 条、去重、最新在前 |
| AI 对话记录 | 内存 | 仅当前会话；「新建对话」清空回 H1 |
| 登录态 | 本地 DataStore（token + UserVO JSON） | 启动静默校验（`GET /api/user/me`），失败则清态 |
| 未读轮询 | 15s 定时器 | 仅登录 + 前台；`unread-count` |
| 点赞/收藏/关注 | 乐观更新 + 失败回滚 | 失败伴随全局 Toast |
| 系统权限 | 仅相机（`E1`「拍摄」首次触发） | 相册选择走系统照片选择器，无需权限；拒绝 → 「未授权相机，可从相册选择」并回 E1 |

## 12. 待议（未定型，转发后端讨论）

### [待议] toggle 幂等与乱序防护 — `opSeq` 操作序号

现状：点赞/收藏/关注三组 toggle 均为**盲翻转**（已赞则取消，请求不携带方向），服务端无幂等保护。两个残余风险：

1. **重复执行**：客户端 OkHttp 开启 `retryOnConnectionFailure`，toggle 请求无 body、可重放；若「服务端已处理但响应丢失、连接重置」，传输层重试会让同一请求**翻转两次**（用户点赞，服务端终态未赞）。
2. **乱序**：先后发出的 A、B 两个 toggle 走连接池不同连接，弱网下可能 B 先到 A 后到；盲翻转按到达顺序执行，服务端终态 = A 的效果 ≠ 用户最后意图 B。

> 随机 `requestId`（幂等键 + 响应回放）只能解决 ①；② 需要顺序语义。
> 客户端 UI 层竞态已用本地指令序号消除（见客户端架构规范 §4.3），但管不到服务端执行序。

**建议**：toggle 请求携带按（用户，目标）单调递增的 `opSeq`（请求头 `X-Op-Seq` 或查询参数均可）；服务端按（用户，目标）记录 `lastAppliedSeq`，收到 `opSeq ≤ lastAppliedSeq` 的请求忽略（或回放缓存响应）。单一机制同时解决 ①②。客户端本地序号机制已在运行（`InteractionStore` 指令序号），后端定型后带上即可。
