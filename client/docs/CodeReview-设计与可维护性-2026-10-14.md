# Android 客户端 Code Review：设计、表达与可维护性

审查日期：2026-10-14  
审查范围：`client/` 中的主要业务链路与公共状态模块  
版本记录：分支 `feat/android-client`，HEAD `d5782fc`  
交付性质：审查意见；未修改业务源码。

## 审查原则与结论

本次按当前客户端实现审查，未限定某个提交之间的差异，也不代表逐行覆盖所有源码。重点包括会话、分页、互动状态、发布草稿与提交、公共输入组件、图片解码、详情评论流程及包依赖方向。

文档和注释用于辅助理解；与实现冲突时，以代码和小红书同类业务的合理行为为判断依据。练手项目的规模也是判断成本的一部分：不要求为了分层引入框架或大量接口，只建议能消除具体风险、减少调用方负担的改进。

主要技术债集中在**公共模块暴露了状态维护规则，却没有完整承担这些规则**：调用方需要记住请求属于哪个会话、互动数据是否经过合并、视频列表两个索引的含义，以及提交失败时怎样把文本回填。已有跨账号状态污染、陈旧请求覆盖和输入丢失等具体风险，因此这些意见并非仅针对代码风格。

共记录 **10 项发现：2 项 P1、6 项 P2、2 项 P3**。

| 编号 | 优先级 | 发现 | 主要设计问题 |
| --- | --- | --- | --- |
| 1 | P1 | 会话切换缺少统一的请求归属 | 会话生命周期没有约束异步结果写回 |
| 2 | P1 | 分页模块没有封装请求生命周期 | 并发、重置、取消规则泄漏给调用方 |
| 3 | P2 | 互动接口要求调用方理解内部计数算法 | 同一种数据类型承载不同语义 |
| 4 | P2 | 发布过程读取了不同时间的表单数据 | 一次提交缺少完整、固定的输入快照 |
| 5 | P2 | 草稿没有封装媒体结构的合法性 | 可变列表与固定索引代替业务约束 |
| 6 | P2 | 输入组件擅自决定提交后清空文本 | 展示组件承担了业务成功后的决策 |
| 7 | P2 | 图片解码策略分散，内存约束不一致 | 同一资源预算规则由多处分别维护 |
| 8 | P2 | 包分层存在反向依赖与源码依赖环 | 基础能力与具体业务装配耦合 |
| 9 | P3 | 图文与视频详情重复维护评论输入流程 | 同一业务规则有两份实现 |
| 10 | P3 | 确认事件隐含执行取消逻辑 | 回调命名与实际事件语义不一致 |

优先级含义：P1 应优先修复，有状态污染或公共流程阻塞的风险；P2 应纳入近期改进，存在可触发的业务风险或明确的维护负担；P3 适合在相关模块调整时收敛。

## 1. [P1] 会话切换缺少统一的请求归属

**定位**：[SessionManager.kt:158](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/data/local/SessionManager.kt:158)，`refreshMe()`；同类逻辑见 `updateProfile()` 第 166–171 行。[InteractionStore.kt:330](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/core/interact/InteractionStore.kt:330)，`clear()`。

**问题与触发场景**：`refreshMe()` 发出请求后，直到响应回来才读取 `currentToken` 并写入存储、更新 `_state`。如果账号 A 的请求尚未结束，用户退出并登录 B，A 的迟到响应会把 A 的资料与 B 的 token 配对持久化，并将当前用户资料改成 A。`updateProfile()` 使用相同的写回方式。

互动状态也存在类似问题：`clear()` 清空覆盖量和各类 epoch，但没有取消全局 scope 中的在途任务。B 对相同目标开始新操作时，epoch 可以再次从 1 开始；A 的旧 epoch 1 响应可能通过 `isLatest()` 校验，把旧账号的结果写回。

**维护影响**：页面销毁、退出登录和开始新会话是不同的生命周期。让互动任务跨页面存活是合理的，但它们必须停止影响后续会话。单个页面补充清理无法完整保护全局状态，之后增加账号切换、自动登录或更多后台刷新都会扩大问题。

**建议**：由会话层提供单调递增的会话代际；请求开始时捕获代际及身份，写回前校验仍属于当前会话。会话结束时取消该会话的任务，再清理用户状态。互动的操作序号应与会话代际共同参与校验，避免清空序号后旧、新请求重新相等。将持久化写入与身份校验集中在会话层，避免每个 ViewModel 自行处理。

**验证依据**：隔离源码逻辑验证复现了“A 资料与 B token 配对写入”以及“清空 epoch 后旧账号互动响应通过校验”。未进行真实账号与网络的端到端实测。

## 2. [P1] 分页模块没有封装请求生命周期

**定位**：[PagedList.kt:67](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/core/list/PagedList.kt:67)，`refresh()`；同文件第 95 行 `loadMore()`、第 150 行 `resetTo()`。

**问题与触发场景**：`refresh()` 直接设置 `inFlight = true` 并发起请求，没有限制重复刷新或检查请求代际。旧、新请求可以按返回顺序覆盖列表，而不是按用户的最新意图更新。`resetTo()` 只重置列表状态，没有让旧请求失效，因此旧请求返回后仍能重新填入被清理的数据。

此外，`refresh()` 和 `loadMore()` 都在正常流程末尾恢复标志，没有用 `finally` 处理取消。协程在 `fetch()` 处取消后，`loading`、`loadingMore` 或 `inFlight` 可能残留，后续追加会被入口检查阻止。

**维护影响**：搜索关键词变化、刷新、退出登录与分页追加都需要调用方安排正确时序；请求又由 ViewModel 和 Compose 副作用共同驱动。公共分页模块虽然减少了取数代码，却把最复杂的生命周期规则留给了使用方。

**建议**：在 `PagedList` 内统一管理请求代际以及刷新、追加之间的互斥关系。刷新或重置应使旧响应失效，追加只允许写入它所属的列表代际。使用 `try/finally` 恢复取消状态，同时避免旧请求的清理逻辑覆盖新请求的加载标志。调用方只负责表达“刷新”“追加”“重置”的业务意图。

**验证依据**：隔离验证复现了旧搜索结果覆盖新结果、`resetTo()` 后旧响应重新写入，以及取消后标志残留并阻塞 `loadMore()`。

## 3. [P2] 互动接口要求调用方理解内部计数算法

**定位**：[InteractionStore.kt:138](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/core/interact/InteractionStore.kt:138)，`toggleLike()` 的入参约定；调用侧见 [HomeViewModel.kt:201](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/feature/home/HomeViewModel.kt:201)、[SearchViewModel.kt:249](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/feature/search/SearchViewModel.kt:249) 和 [NotificationViewModel.kt:234](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/feature/message/NotificationViewModel.kt:234)。

**问题与触发场景**：`merge()` 返回 `Note`，`toggleLike()` 也接受 `Note`，但后者必须接收尚未合并的服务端快照，否则计数基准可能重复叠加。这个约束主要靠注释及 `serverNote` 参数名维护，类型与接口本身无法区分两种语义。

Home、Search、Profile 分别维护原始对象的查找；Home 和 Search 在找不到原始对象时还会回退到展示对象，重新引入接口明确禁止的输入。通知模型没有评论的点赞基准，调用方为满足接口构造 `liked = false`、`likeCount = 0` 的 `Comment`；通知关注动作也把初始关注态固定为 false。

**维护影响**：新增互动入口时，开发者必须理解 Store 的计数算法，并自行寻找或构造基准。真实快照、合并后的展示值和未知状态都被包装成同一种模型，编译器无法帮助发现误用，也容易把“未知”误当作 false。

**建议**：优先让 Store 管理已取得的服务端快照，调用方按目标 id 表达互动意图；没有快照时，通过明确的未知状态流程取得基准或使用服务端权威结果。若暂时保留当前模式，至少区分服务端快照与展示模型的类型，并去掉回退到展示对象的路径。不要通过构造假评论来满足完整领域对象的入参要求。

**验证依据**：源码检查确认了接口约束、原始对象查找及通知基准构造；本项未另行做 UI 或真实网络验证。

## 4. [P2] 发布过程读取了不同时间的表单数据

**定位**：[PublishViewModel.kt:287](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/feature/publish/PublishViewModel.kt:287)，创建笔记时读取正文；第 238–245 行在点击发布时取得标题。输入侧见 [PublishRoutes.kt:372](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/feature/publish/PublishRoutes.kt:372) 与 [PublishComponents.kt:395](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/feature/publish/PublishComponents.kt:395)。

**问题与触发场景**：提交入口将标题冻结为 `normalizedTitle`，随后上传媒体；正文直到上传结束、调用 `posts.create()` 时才读取。上传期间标题和正文输入仍可编辑，因此一次提交可能包含点击发布时的旧标题和上传结束时的新正文。

**维护影响**：同一次业务操作的输入不属于同一时间点。以后加入话题、位置、可见范围等字段，每个字段何时被读取都会成为隐含规则，校验结果也容易与最终提交的数据不一致。

**建议**：提交入口生成完整的不可变快照，至少包含标题、正文和媒体集合；校验、上传和创建笔记都使用该快照。上传进度可单独更新展示状态，或按稳定的媒体标识关联上传结果。是否允许继续编辑可以作为交互决策，但本次提交使用哪份数据必须明确。

**验证依据**：源码检查确认了标题和正文读取时间不同，以及输入组件没有随提交状态禁用；未进行上传期间编辑的 Android UI 实测。

## 5. [P2] 草稿没有封装媒体结构的合法性

**定位**：[PublishDraft.kt:39](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/core/publish/PublishDraft.kt:39)，公开可变媒体列表；第 89 行 `removeAt()`、第 116 行 `enterVideoMode()`、第 123 行 `replaceVideo()`。

**问题与触发场景**：视频草稿依赖列表固定位置表示 `[视频, 封面]`，但 `media` 是对外公开的 `mutableStateListOf<DraftMedia>()`。调用方能够绕过草稿方法直接改变数量、类型及顺序；`enterVideoMode()` 不校验传入项类型，通用 `removeAt()` 也能破坏视频结构。

`replaceVideo()` 只确认草稿存在视频，就按固定索引写入。若草稿只剩视频、封面已被删除，`isVideoMode` 仍成立，但写封面索引会越界。这说明模型允许表达非法结构；并不意味着所有当前 UI 操作都已经触发该问题。

**维护影响**：各调用方必须自行维持媒体数量、顺序与类型的约束。新增恢复草稿、调整封面或编辑媒体入口时，很容易绕过现有路径中的保护。

**建议**：隐藏可变集合，通过草稿方法暴露修改能力；将图文与视频表达为互斥的媒体状态，例如 `Images(items)` 和 `Video(video, cover)`。草稿集中校验图片上限、媒体类型、切换及删除规则，调用方无需知道视频和封面的索引。

**验证依据**：源码检查确认了公开修改入口和索引依赖；未对全部 UI 媒体操作路径做实测。

## 6. [P2] 输入组件擅自决定提交后清空文本

**定位**：[Sheets.kt:373](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/core/ui/Sheets.kt:373)，发送按钮；第 346–349 行键盘发送使用相同逻辑，第 260 行使用 `remember(visible, initialText)`。补偿逻辑见 [ProfileRoutes.kt:174](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/feature/profile/ProfileRoutes.kt:174)。

**问题与触发场景**：`XhsOverlayInputBar` 调用 `onSend(text)` 后立即清空本地文本，却无法知道业务校验或异步提交是否成功。调用方只能通过改变 `initialText` 回填。

例如输入 T，首次提交失败后父级把 `initialText` 改成 T，组件据此恢复文本；再次发送同一个 T 并失败时，父级仍写入 T，`visible` 和 `initialText` 都没有变化，`remember` 不会重新初始化，组件内部被清空的文本无法恢复。若初始值本来就是 T，首次失败也可能遇到相同情况。

**维护影响**：资料编辑、评论和通知回复都要补偿公共组件的清空行为。组件拥有文本，业务层又通过初始化参数反向控制文本，形成两份状态和不稳定的同步规则。

**建议**：改为受控输入，暴露 `value` 与 `onValueChange`，由调用方在业务成功后清空或关闭。发送中状态单独传入组件，用于防止重复提交。这样失败保留文本不再依赖改变 `remember` 的 key。

**验证依据**：已用 JavaScript 做重复失败的状态推演；这是状态模型验证，未执行 Compose 或真机 UI 测试。

## 7. [P2] 图片解码策略分散，内存约束已经不一致

**定位**：[ImageLoader.kt:123](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/core/image/ImageLoader.kt:123)，远端图片采样；其他实现见 [PublishComponents.kt:350](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/feature/publish/PublishComponents.kt:350) 和 [ProfileCommon.kt:292](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/feature/profile/ProfileCommon.kt:292)。审查时还包含公用本地图片解码组件 `XhsLocalImage`；该组件因无业务调用方已于 2026-11-12 删除，以下为审查当时的问题记录。

**问题与触发场景**：远端图片、公用本地图片、发布缩略图及资料预览分别维护采样策略，最后一处固定使用采样率 4。

远端算法在下一次采样会让任意一边小于目标时停止。输入尺寸为 1080×15000、目标尺寸为 0×0 时，默认两边目标都是 1080，第一次判断因为宽度降到 540 而停止，最终 `inSampleSize = 1`。因此默认值并没有限制长图的最长边；按每像素 4 字节估算，仅像素数据约需 61.8 MiB。

**维护影响**：同一张图从远端、本地或不同页面加载时，质量与内存成本不同。修复一处解码规则后，其他入口仍可能保持原有行为。这里应统一的是资源预算语义，读取 URL、文件或 content URI 的 IO 适配仍可各自保留。

**建议**：集中计算目标解码尺寸、最长边或总像素预算，并明确显示质量和内存限制冲突时的取舍。缩略图传入实际显示尺寸；统一策略覆盖普通图、长图、宽图和超大图片。无需仅为此引入庞大的图片框架。

**验证依据**：执行原始采样函数确认了上述输入得到采样率 1；61.8 MiB 为像素内存理论估算，未执行实际 Bitmap 分配或真机内存测试。

## 8. [P2] 包分层存在反向依赖与源码依赖环

**定位**：[ApiClient.kt:121](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/core/net/ApiClient.kt:121)，创建具体 API；同文件第 137–148 行聚合 `Apis`。[Mappers.kt:13](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/data/mapper/Mappers.kt:13) 与 [PostRepository.kt:14](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/data/repo/PostRepository.kt:14) 构成另一组双向依赖。

**问题与影响**：`core.net` 创建和聚合具体的 `data.api` 接口；`data.api` 又依赖 `core.net.ApiEnvelope`，例如 [UserApi.kt:3](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/data/api/UserApi.kt:3)。基础网络包因而必须知道每个业务 API，新增业务接口也要修改基础网络文件。

此外，Mapper 为判断视频类型依赖 `PostRepository.POST_TYPE_VIDEO`，而 Repository 本身又依赖 Mapper。这是包与源码依赖方向上的环，不是已证实的运行期 DI 对象构造循环；当前仍可以编译。

**维护影响**：底层规则修改牵动上层装配，简单的 DTO 映射也绑定 Repository。后续独立测试、移动模块或替换数据来源时，难以保留清楚的单向依赖。

**建议**：将具体 API 的创建和聚合移到 `data` 或 DI 装配层，`core.net` 只提供网络配置和通用协议处理能力。把协议类型常量移出 Repository，放到 DTO/协议定义或相应映射定义附近。通过移动装配与常量即可改善方向，不需要先改成多模块工程或全面引入 Clean Architecture。

**验证依据**：源码检查确认了双向引用；本项未运行模块拆分或运行期 DI 验证。

## 9. [P3] 图文与视频详情重复维护同一评论输入流程

**定位**：[DetailViewModel.kt:478](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/feature/detail/DetailViewModel.kt:478) 与 [DetailViewModel.kt:588](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/feature/detail/DetailViewModel.kt:588)，两套 `sendOverlay()`；`openReplyInput()` 和 `dismissOverlay()` 也维护相同的评论输入规则。

**问题与影响**：两处都执行文本规范化、重复发送检查、回复目标选择、发送状态更新、成功计数与失败回填。这里是同一评论业务语义的重复，而不是两种页面恰好长得相似。

修复输入保留、回复目标或发送状态时，需要同步修改两处。随着评论能力增加，两套流程容易产生行为差异。

**建议**：通过组合提取小型评论输入状态持有者，例如 `CommentComposer`，集中维护目标与提交状态，并提供业务结果。图文和视频 ViewModel 保留各自的详情数据及面板状态。先收敛已确认相同的流程，不必为两种详情页建立泛化 UI 基类。

**验证依据**：源码对照确认了相同的流程；未单独执行评论 UI 测试。

## 10. [P3] 确认事件隐含执行取消逻辑

**定位**：[Sheets.kt:219](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/core/ui/Sheets.kt:219)，`XhsConfirmSheet` 确认按钮；补偿逻辑见 [PublishRoutes.kt:253](D:/Projects/FreeCoding/xiaohongshu/xiaohongshu-clone/client/app/src/main/java/com/xiaohongshu/app/feature/publish/PublishRoutes.kt:253)。

**问题与影响**：确认按钮先调用 `onDismiss()`，再调用 `onConfirm()`。发布页将 `onDismiss` 接到 `cancelSwitchToVideo()`，这会清空 `pendingVideo`。调用方因此必须在组合期捕获待处理对象，否则确认时再从 ViewModel 读取将得到 null。

当前发布页已经通过捕获参数补偿该时序，所以本项主要是公共接口的语义与维护风险：名为确认的事件同时触发取消，不同调用方可能把关闭和放弃业务操作混在一起。

**建议**：确认只触发 `onConfirm()`，由调用方完成确认并更新弹层可见状态；取消按钮、遮罩和返回操作触发 `onDismiss()`。若确实需要统一的关闭通知，应使用独立且含义明确的事件，避免借用取消回调执行确认路径。

**验证依据**：源码检查确认了回调顺序及发布页的补偿实现；未执行弹层 UI 实测。

## 合理的现有设计

- 当前手工 DI 与按业务 feature 划分代码，对练手项目规模是合理的，没必要仅为形式完整引入更重的框架。
- `appViewModel` 使用 `inline`、`reified` 服务于 ViewModel 获取，使用目的清楚。审查范围内未发现需要单独整改的 Kotlin 语法糖滥用。
- 互动 Store 集中乐观更新、分页类集中分页、草稿对象承载跨页面编辑状态，抽象方向总体合理。优先补全这些模块的状态约束与生命周期，比继续增加抽象层更有收益。
- 重复逻辑应按业务语义判断。建议提取的是相同的评论输入流程与图片预算计算，不是把所有相似 UI、IO 或网络调用都机械合并。

## 建议修复顺序

1. 先处理第 1、2 项：建立会话代际和分页请求生命周期，防止旧请求污染当前状态。
2. 再处理第 4、6、10 项：固定提交输入，明确成功后的文本处理与弹层事件语义。
3. 收敛第 3、5、7 项：由互动 Store、草稿模型和图片策略承担各自的业务约束，减少页面中的补偿代码。
4. 最后处理第 8、9 项：调整装配与常量位置，再提取已确认相同的评论流程；根据影响范围分步修改即可。

后续修复应覆盖与问题直接相关的回归场景：账号切换时旧响应返回、不同关键词刷新乱序、分页取消后重试、上传期间编辑表单、相同文本连续提交失败，以及长图的采样预算。上述建议是后续修复验收方向，不表示本次已经执行全部用例。

## 验证方式与限制

审查中执行了 6 个隔离 Kotlin 源码逻辑验证。验证复制实际方法或类中的相关实现，以 stub 替代网络、本地存储及部分框架依赖，使用本机现有 Kotlin 2.2.10 和 coroutines 1.9.0。

| 隔离验证 | 观察结果 | 对应发现 |
| --- | --- | --- |
| 两次刷新按相反顺序返回 | 旧响应覆盖新结果 | 2 |
| 请求挂起后取消 | 加载或在途标志残留，后续追加被阻止 | 2 |
| 请求在途时调用 `resetTo()` | 重置后的列表被旧响应重新写入 | 2 |
| 1080×15000 图片、目标尺寸 0×0 | 采样率为 1 | 7 |
| 清空互动 epoch 后启动同目标的新操作 | 旧账号响应仍能通过最新操作校验 | 1 |
| A 的资料请求在 B 登录后返回 | A 的资料与 B 的 token 配对写入 | 1 |

另用 JavaScript 推演了公共输入组件在相同文本连续提交失败时的状态变化，对应第 6 项。这不属于 Compose 或真机测试。

本次未执行完整 Android 构建、真机 UI 测试、实际 Bitmap 内存测试或真实网络端到端测试。隔离验证用于确认具体控制流和状态问题，不替代完整应用的运行验证；其他意见依据已定位的源码与调用关系提出。

临时验证文件已清理。审查未修改业务源码，本次 Markdown 交付仅新增本文件。源码行号对应本次审查时的工作区版本，后续修改后应按方法名重新定位。
