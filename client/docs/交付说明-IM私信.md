# 交付说明 · IM 私信（契约 §12）

> 本轮范围：**单聊 + 文本消息**。群聊、表情、图片消息不做（消息 `type` 预留扩展）。
> 构建：`cd client && ./gradlew :app:compileDebugKotlin --offline` 零 error 零 warning。

## 实现范围

| 模块 | 内容 | 状态 |
|---|---|---|
| F2 资料页按钮拆分 | 他人主页「通栏关注」右拆为 **关注 + 私信** 两枚等宽 pill（同高间距 8），点击建会话推 G8 | ✅ |
| G1 消息页会话列表 | 「点点」固定首行 + REST 私信会话列表（进 Tab 首刷 + WS `chat.msg` 就地置顶更新） | ✅ |
| G8 私聊页 | 气泡流（我=右侧蓝底白字/对方=左侧灰底无边框、均四角圆角；对方头像在左、本人头像在右，均 36dp；短消息按内容收缩，长消息使用两侧头像槽之间的最大宽度后换行）、乐观发送、时间分隔、已读上报、WS 实时上屏 | ✅ |
| WS 长连接 | `ws://<host>/ws/chat`（用户级单连接多路复用；心跳/退避重连/notify.unread 推送） | ✅ |
| 通知未读数 | WS `notify.unread` 实时推送，**无定时轮询**；断线期间角标保持上次值，WS 重连成功自动拉取校正；进 G1 消息 Tab 再拉一次 | ✅ |

## 技术方案（为什么这么做）

**HTTP 管状态，WS 管增量**：会话列表/历史消息/已读上报走 REST（复用现有拦截器/超时/错误码
链路）；新消息/已读回执/通知未读数变化走同一条 WS 长连接。连接维度 = **登录用户**（非会话），
与未读数通道同一生命周期（`AppNavHost` ON_START/ON_STOP）。

**乐观发送**：本地临时消息用**负数 id**（`LocalIdSeed` 递减，绝不与服务端正数 id 冲突）先上屏
「发送中」→ REST 响应按 `clientId` 匹配替换；失败置红边气泡，点按用**同一 clientId** 重试
（服务端按 `clientId` 幂等去重，不会重复落库）。WS 推回的自己消息（多设备回显）按 id 去重跳过。

**I1 边界**：WS 断线/握手 401 **不触发**登录失效跳转（静默退避重试）——REST 401 是登录失效
唯一权威信号，WS 是增强通道。

## 已知取舍（不阻塞，记录在案）

- **时间分隔**：相邻消息间隔 >5min 插居中灰字（`formatNoteTime`）；5min 内连发不重复分隔。种子数据包含 2 分钟连发与跨 5 分钟的消息组。
- **历史终点提示**：顶部不显示「没有更多了」；`noMoreEarlier` 仅用于停止继续翻页，加载中提示保留。
- **已读回执**：`chat.read` 帧已解析并派发事件，但本版**不渲染**已读态（契约 §12.3 简化模型）。
- **消息本地持久化**：无 Room，会话内消息仅内存（VM 持有）。杀进程重进会重拉历史——契约 §11
  允许（AI 对话也是内存态）。
- **顶栏头像尺寸**：32dp 在 Dimens 里没有对应 token，用 `avatarConversation / 2` 表达（见
  ChatRoutes.kt 头部 TODO）。
- **未读轮询移除**：原 15s 轮询彻底删除，断线期间角标保持上次值、仅 WS 重连成功那一刻
  校正（外加进消息 Tab 拉取）。放弃的是断线期 ≤15s 的收敛上界；依据：全量故障下轮询与
  WS 同死同活、无增量价值，剩余价值仅间歇网络的恢复时差，demo 定位下不值得多养一条通道。
- **读跟随写只覆盖 REST 层**：已读写与未读/会话拉取在 Repository 层共用一把 Mutex 串行
  （写落库后才放行拉取），根治 pop 场景的角标/红点闪回。两个残留窗口记录在案：写在途时
  第三方动作触发的 `notify.unread` 推送仍可能短暂带回写前旧值（单线程服务端把推送发在
  read-all 之前时）；以及写开始**前**已在途的拉取响应无法追回。两者窗口均在数百毫秒内、
  被写后推送或下一次校正自愈，demo 场景不可观测。通知列表 `list`（行内已读态）未纳入
  串行——重新进入子页时 pop 的 read-all 早已落库，实际不可观测。

## E2E 验收清单（测试账号）

mock server 种子：**admin↔user1**（admin 有 **2 条未读**）、**admin↔user2**（**1 条未读**）、
admin↔user3、user1↔user2、user2↔user3；**testuser 无会话**（空态可测）。密码统一 `123456`。

| # | 场景 | 预期 |
|---|---|---|
| 1 | admin 登录 → 消息 Tab | 三入口区下方显示 3 个会话行；admin↔user1 行带未读红点 |
| 2 | 点会话行（admin↔user1） | 推入 G8；7 条历史气泡（我=右蓝底白字/对方=左灰底无边框，均四角圆角；对方头像在左、本人头像在右；短消息按内容收缩，长消息在两侧头像槽之间换行）；2 分钟连发不重复显示时间，跨 5 分钟恢复时间分隔；顶部无「没有更多了」；自动已读上报 |
| 3 | 返回消息 Tab | 会话行未读红点消失 |
| 4 | 底部输入「你好」发送 | 气泡右侧灰底「发送中」→ 闪变服务端消息；时间分隔「今天 15:xx」 |
| 5 | 另一设备/账号登录 user1 发消息 | admin 侧 G8 实时上屏新消息（左侧灰底无边框气泡）并滚底 |
| 6 | F2 他人主页（user1 主页） | 操作行 = 「关注」「私信」两枚等宽 pill 并排；点私信 → 推 G8（幂等返回同一会话） |
| 7 | 断网后发消息 | 气泡红边「发送失败，点击重试」；恢复网络点气泡重发成功（同一 clientId 幂等） |
| 8 | 杀进程重进 admin | 消息 Tab 会话列表重拉；未读数与断网前一致（服务端权威） |
| 9 | 通知未读数 | WS 在线时，他人点赞/评论后 **秒级** 角标更新（不再等 15s 轮询） |
| 10 | 杀掉 mockserver 进程再重启（WS 网络级断开，走 `onFailure`）| 断线期间角标保持上次值（无轮询请求）；恢复后 WS 重连成功并自动拉取校正角标 |
| 11 | App 在前台，对 mockserver 执行 `POST /api/_reset`（改状态**不推帧**、WS 连接保留），切走再切回消息 Tab | 三入口角标被进 Tab 拉取校正回种子值 |
| 12 | G2/G3/G4 有未读时进入后立刻 pop 返回 G1 | 角标在 pop 动画全程保持 0，无「闪回旧值再归零」（读跟随写：`unread-count` 拉取等 `read-all` 落库） |
| 13 | G8 中对方新消息到达（触发已读上报）后立刻返回 G1 | 会话行未读红点不闪回（`conversations` 拉取等已读上报落库） |

## 文件清单

**Android（client/app/，新增 8 / 修改 11）**

| 文件 | 说明 |
|---|---|
| `data/dto/ChatDto.kt` `data/api/ChatApi.kt` `data/repo/ChatRepository.kt` | IM REST 数据源 |
| `core/realtime/RealtimeConnection.kt` | WS 长连接封装（心跳/退避/帧解析/事件总线） |
| `feature/message/ConversationsViewModel.kt` | G1 会话列表 VM |
| `feature/message/ChatRoutes.kt` `ChatViewModel.kt` `ChatComponents.kt` | G8 私聊页 |
| `domain/model/Models.kt` | +`Conversation`/`ImMessage`/`LocalUserIdProvider`（AI 的 `ChatMessage` 不动） |
| `data/local/SessionManager.kt` | 登录态变化时同步 `LocalUserIdProvider` |
| `data/mapper/Mappers.kt` | +chat DTO→domain |
| `core/net/ApiClient.kt` | `Apis` 加 `chat` 字段 |
| `core/notify/UnreadCountCenter.kt` | WS 推送通道接入（定时轮询移除，断线保持上次值） |
| `di/AppContainer.kt` | 注册 `chatRepository`/`realtimeConnection` |
| `navigation/{Routes,AppNavigator,AppNavHost}.kt` | `chat/{id}/{peerId}` 路由 + WS 生命周期 |
| `feature/message/MessageRoutes.kt` | G1 会话列表接入 |
| `feature/profile/{ProfileRoutes,ProfileScreen,ProfileCommon}.kt` | F2 按钮拆分（2026-10-18 归入共用主页） |

**MockServer（client/mockserver/，修改 4 / 新增 1 依赖）**

| 文件 | 说明 |
|---|---|
| `server.mjs` | 5 个 chat REST 端点 + `/ws/chat`（ws 包）+ 点赞/收藏/关注运行时通知补齐 |
| `seed.mjs` | 5 会话 25 条消息种子（admin 未读 2+1） |
| `package.json` `package-lock.json` | 唯一新依赖 `ws@^8.22.0` |
| `README.md` | IM 章节（REST/WS 表 + 验证命令） |

**文档（client/docs/，修改 2）**

| 文件 | 说明 |
|---|---|
| `API契约-v2.md` | §12 IM 私信模块（变更 #22、错误码 8001-8003） |
| `客户端架构与开发规范.md` | §1 选型表、§2 目录、§4.8 速查、§7 G8/F2 条目 |
