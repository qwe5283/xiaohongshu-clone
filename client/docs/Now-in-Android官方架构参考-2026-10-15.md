# client/ 与 Now in Android：架构对照与评价标准

核验日期：2026-10-15。本笔记先记录官方基线，供 `client/` 本地架构对照使用；下方评价问题属于依据官方原则提出的工程判断，不是 Google 制定的评分表。

## 版本与来源

核验时 `android/nowinandroid` 的 `main` 指向提交 [`a49ed253d75e61a2b6ab80a8da677b57437b08eb`](https://github.com/android/nowinandroid/commit/a49ed253d75e61a2b6ab80a8da677b57437b08eb)，提交日期为 2026-09-22。下面的源码链接固定到该提交，避免之后的更新改变比较结果。[S1]

Android Developers 的 `developer.android.com` 在本次访问中连接超时，因此实际读取 Google 官方中国站 `developer.android.google.cn` 的英文页面；来源表同时保留官方主站和此次读取的官方中国站链接。

## 官方建议不等于必须复制示例工程

Google 的架构推荐页明确要求将内容视为建议而非严格要求，并按应用需要调整；它把建议分成“强烈推荐”“推荐”“可选”三个等级。明确的 UI/数据边界、Repository、单向数据流、生命周期感知的状态收集、依赖注入、测试都比目录名称更重要。[S2]

NIA 的模块化说明更直接：不存在唯一正确的模块化答案；其方案兼顾了一个相对小的示例应用和面向大工程的展示目标；数据层较小时放在一个模块里完全可以。Android 模块化指南也说明，每个模块都会增加构建配置和样板代码成本；如果项目不需要继续扩大，模块化的伸缩性与构建收益可能并不成立。[S3][S4]

因此，“Gradle 模块更多”“目录更像 NIA”“用了 Hilt”不能单独证明架构更好。应该考察这些选择是否解决当前项目的实际问题。[S2][S3][S4]

## NIA 当前实现与通用建议

| 维度 | NIA 当前实现 | 官方建议的适用边界 |
| --- | --- | --- |
| 物理模块 | `app` 装配应用；`core` 提供共享数据、网络、数据库、设计系统、导航、测试等；多数 feature 分成 `api` 与 `impl`。`api` 暴露导航键，其他 feature 可以依赖目标 feature 的 `api`，不应依赖其 `impl`。 | 模块可以加强编译期边界、协作与复用，但粒度需要按规模和成本决定。仅按 package 分层不等于没有架构，只是边界主要靠约定和检查维护。[S3][S4][S5] |
| 分层与 Use Case | 有 UI、data、domain。`GetFollowableTopicsUseCase` 组合 topic 与 user data；`ForYouViewModel` 同时使用 Use Case 与 Repository，收藏、关注等操作直接调用 `UserDataRepository`。 | Domain 是可选层：跨多个 ViewModel 复用业务逻辑，或单个 ViewModel 的逻辑复杂时再引入。不要求每个 Repository 方法都包装成 Use Case。[S6][S7][S8] |
| UI 与状态 | Compose UI；`ForYouViewModel` 通过 `StateFlow` 和 `stateIn(WhileSubscribed(5_000))` 暴露 UI 状态；外层 `ForYouScreen` 以 `collectAsStateWithLifecycle` 收集，然后把状态与回调传给内层 Screen。 | 强烈推荐 UDF、屏幕级 ViewModel、生命周期感知收集。推荐暴露不可变 UI 状态；没有跨层数据流的简单场景可以使用内部 `MutableStateFlow`，公开为 `StateFlow`。Screen/Route 的命名不是评价重点。[S2][S8][S9] |
| Repository 与数据一致性 | `OfflineFirstNewsRepository` 的读取只来自 Room；网络同步先更新数据库，数据库 Flow 推送到上层。用户偏好由 Proto DataStore 提供。多个页面共同订阅 Repository 的数据。 | Repository 是其他层访问应用数据的边界；SSOT 指每种数据有明确的所有者，修改由该所有者集中处理，公开不可变数据。SSOT 不必永远是数据库；离线优先应用通常才以本地数据库作为应用数据 SSOT。[S6][S10][S11] |
| 离线能力 | NIA 是 offline-first：网络数据写入本地，再从本地读取；同步由 WorkManager 协调，并具备重试策略。 | 离线优先是产品与数据策略：需要离线读取、弱网连续性和同步一致性时价值突出。不能把“所有应用都必须 Room + WorkManager”当成通用架构要求。官方离线指南是在选择 offline-first 的前提下规定本地数据源。[S6][S10][S12] |
| 依赖注入 | Hilt + 构造函数注入；Repository 接口绑定到实现。ViewModel 单元测试仍可手工构造实例。 | 官方强烈推荐 DI，尤其构造函数注入；Hilt 属于“推荐”。简单应用可手动 DI；有多个 ViewModel 屏幕、WorkManager、导航栈作用域等复杂情况时推荐使用 Hilt。[S2][S13][S14] |
| 测试 | 有 ViewModel、Repository、导航、截图及性能测试；Repository 用测试替身替换，部分测试使用真实 DataStore 或内存数据库，不使用 mocking 库。 | 官方强烈推荐测试与优先使用 fake，而非 mock。可替换依赖、可控制协程调度、可在无真实服务器条件下验证状态行为，是比“看起来可测试”更强的证据。[S2][S14][S15] |
| 导航 | 已使用 Navigation 3：`NavKey`、`entryProvider`、`NavDisplay`；共享 `Navigator` 操作多个 back stack；使用 saveable-state 和 ViewModel-store decorators；部分页面采用自适应 list/detail scene。 | 当前推荐页已建议单 Activity + Navigation 3。对已有应用评价时仍需看栈行为、状态保存恢复、参数类型和测试是否可靠，不能只比较路由类名称。[S2][S16][S17][S18] |

## 两个容易混淆的地方

1. **NIA 不等于严格 Clean Architecture。** 它自己的架构文档明确提醒与其他架构不同。Google 的 Domain 常依赖数据层 Repository；UI/ViewModel 可以直接访问 Repository。因此“UI 必须只访问 Use Case”“接口一定要定义在 Domain”并不是这套官方架构的普遍硬规则。[S6][S7][S8]
2. **示例文档可能落后于代码。** 当前 `ArchitectureLearningJourney.md` 仍以 `GetUserNewsResourcesUseCase` 说明新闻与用户数据的组合；当前源码中该责任已经由 `CompositeUserNewsResourceRepository` 承担，`ForYouViewModel` 使用 `UserNewsResourceRepository`。模块化文档也残留 `NiaNavHost` 的例子，但实际 app 使用的是 `NavDisplay`。本次比较以固定提交的代码为准。[S6][S8][S16][S19]

## 判断“哪个更好”的操作性问题

以下是依据官方原则归纳的项目评估方法，适合用具体场景衡量：

- **修改是否局部化？** 改一个接口、分页策略、收藏规则或某页 UI，需要修改哪些包/模块？是否迫使无关 feature 一起改？依据关注点分离与封装原则。[S4][S10]
- **状态是否一致？** 同一条内容在首页、详情、收藏列表中是否同步？请求失败后如何回滚？并发点击、旧响应、账号切换是否有明确规则？检查数据所有权与状态传播，而不是只数 Repository 类。[S10][S11]
- **能否验证行为？** 是否能替换网络和存储，在没有真实服务器和设备的情况下验证 ViewModel、Repository、状态合并与失败路径？现有测试是否实际保护这些行为？[S2][S14][S15]
- **Android 生命周期是否正确？** 页面离开与返回、配置变化、进程重建后，状态、导航和请求能否按预期恢复？使用 Flow 或 ViewModel 只能说明采用了工具，仍需检查其作用域和状态归属。[S2][S9][S18]
- **是否服务产品需求？** 哪些内容必须离线可读？是否存在多客户端、跨页面共享状态、批量同步需求？根据需求决定缓存与同步设计。[S11][S12]
- **收益是否覆盖复杂度？** 拆模块或引入 Hilt/Use Case 后，依赖是否更清楚、构建是否更快、多人工作是否更少冲突？如果只有样板代码增加，应重新考虑粒度。[S2][S3][S4]

## 本仓库的实际架构

本地比较依据是 2026-10-15 的工作区源码，包含未提交改动；当时 HEAD 为 `d5782fc`。本节做源码和依赖检查，没有执行构建或运行测试，也没有给两套架构做性能跑分。

`client/` 采用单 Activity、Compose、按 feature 分包、ViewModel + StateFlow、Repository、手工构造函数注入。只有一个 Gradle 模块 `:app`。首页通过 `Route → Screen` 传递状态与回调，ViewModel 组合分页状态与互动覆盖量，UI 使用生命周期感知收集。这些与 NIA 的 UI 架构原则一致。[L1][L2][L3][L4]

| 维度 | client/ 当前实现 | 与 NIA 的实质差异 |
| --- | --- | --- |
| 工程边界 | `core/data/domain/feature/navigation` 是同一模块内的包；未发现 feature 互相 import。 | NIA 用 Gradle 模块与 API/实现分离约束依赖；本项目主要靠约定维持边界。模块拆分本身不保证边界合理。[L1][L2][S3][S5] |
| 数据流 | 主要业务数据直接从 REST 获取；`PostRepository` 返回映射后的模型，分页快照由各 ViewModel 持有，`InteractionStore` 管理共享互动覆盖量。登录态、搜索历史有 DataStore，不能把整个应用描述成完全没有本地存储。 | NIA 新闻数据先落 Room，多个页面从 Repository 订阅本地数据。区别是数据状态的归属与传播方式，以及 offline-first 产品策略。[L3][L5][L6][S10][S11] |
| 业务层 | `domain/` 只有 `model/Models.kt`；业务规则存在于 ViewModel、Repository、SessionManager 与共享 Store 中。 | 目录名不代表已经建立独立业务层；没有 Use Case 也不自动构成问题。出现复用或复杂编排时再抽取。[L2][L7][S7][S8] |
| DI | `AppContainer` 构造具体对象，ViewModel 从构造参数获得依赖；Compose 的入口层读取容器。 | 已经在使用 DI；与 Hilt 的差别主要是装配、作用域管理和替换依赖的成本。[L4][S2][S13] |
| 导航 | Navigation Compose 2.9、字符串路由、统一 `AppNavigator` 接口。 | 当前 NIA 是 Navigation 3、类型化导航键、多 back stack。迁移价值应由参数安全、状态恢复和导航需求决定。[L8][S16][S17][S18] |
| 验证 | 未发现 `app/src/test`、`app/src/androidTest`；`app/build.gradle.kts` 没有测试依赖。现有开发规范明确排除了测试框架。 | NIA 有真实的自动化测试与 fake 替身。对于已经包含乐观更新、回滚、乱序响应和账号切换的客户端，验证能力比框架数量更影响长期维护。[L2][L9][S14][S15] |

## 当前最值得改善的地方

### 1. 数据一致性规则应由更少的调用方承担

`InteractionStore` 统一处理点赞、收藏、关注，是现有设计的优点。但 `merge(rawNote)` 与 `toggleLike(serverNote)` 都使用 `Note` 类型；后者要求必须传入未合并的服务端值，否则计数会重复叠加。`HomeViewModel.toggleLike(displayed)` 因而先按 ID 找回原始快照，再交给 Store。[L3][L6]

这是一个可观察的接口成本：页面必须理解底层覆盖量算法、保存两种语义的快照，并记住不能把展示值回传。可以逐步让共享数据组件拥有快照与互动状态，向页面暴露统一的观察流和按目标 ID 表达的操作；也可以先用不同类型明确区分原始值与展示值。这里不要求立刻引入 Room，统一的内存数据所有者也能改善边界。[L3][L6][S11]

IM 还有类似的扩展压力：`ChatRepository` 负责 REST，`ChatViewModel` 同时订阅 `RealtimeConnection`，在页面内协调历史、推送、乐观发送和去重。若这些规则要被会话列表、通知、后台同步复用，可以再收拢到聊天数据组件。[L10]

### 2. 依赖方向应先整理，再考虑物理拆分

`core.net.ApiClient` 创建具体的 `data.api` 接口；`data.api` 又依赖 `core.net.ApiEnvelope`。这是源码包之间的依赖环，当前单模块仍可以编译，不代表运行时 DI 循环。`domain.model` 还引用 `core.design.Dimens`，承担封面展示比例规则；编辑资料的 ViewModel 直接构造 `UpdateUserRequest` DTO。[L7][L11][L12]

这些事实说明包名与职责之间已有偏差。可以先把业务 API 装配移到 data/DI，把传输 DTO 留在数据边界内部，并明确哪些模型是展示模型。NIA 自身也有 `core:data` 等业务共享模块，因此问题不在于叫 `core`，而在于依赖方向和职责是否清楚。[S5]

### 3. 优先验证复杂状态行为

当前 Repository 多为具体类，互动组件自己创建 `Main.immediate` 协程作用域并调用 Toast；替换数据源和控制时间的入口有限。构造函数注入已经提供了好的起点，可以只在需要 fake 替换的边界建立接口或函数依赖，并允许注入协程执行域；不需要给每个类都增加接口。[L4][L5][L6][S2][S15]

最有价值的验证场景是：两个请求按反序返回、请求取消、分页重置后旧响应到达、连续赞/藏及失败回滚、账号切换、REST 与 WS 重复消息。它们检查业务不变量，能帮助判断架构是否把复杂性封装在了合适的位置。此处是建议清单，不是本次已经通过的测试结果。

## 对当前项目的选择建议

保留单模块和手工 DI 是合理的起点。优先整理数据所有权、封装复杂状态规则、提供可替换依赖和自动化验证；跨 ViewModel 复用或复杂业务编排出现时再加入 Use Case；离线要求明确时再设计本地数据库与同步；团队协作或构建依赖出现实际问题时再拆 Gradle 模块。[S2][S3][S4][S7][S11]

对于长期多人维护、离线体验和持续回归验证，NIA 展示了更完整的工程机制。本项目目前减少了模块与框架装配负担，但部分复杂性由页面和调用约定承担。判断“更好”时，应比较实现、修改、验证以及错误恢复的总成本，并用上面的具体场景检验。

## 本地证据

- **[L1] 单模块设置**：[settings.gradle.kts:39](../settings.gradle.kts#L39)。
- **[L2] 项目规范与包边界**：[客户端架构与开发规范.md](客户端架构与开发规范.md)，技术选型、目录结构与职责边界。
- **[L3] 首页状态与原始快照**：[HomeViewModel.kt:107](../app/src/main/java/com/xiaohongshu/app/feature/home/HomeViewModel.kt#L107)，互动操作位于 201 行。
- **[L4] 手工依赖注入**：[AppContainer.kt:42](../app/src/main/java/com/xiaohongshu/app/di/AppContainer.kt#L42)；[HomeRoute.kt:47](../app/src/main/java/com/xiaohongshu/app/feature/home/HomeRoute.kt#L47)。
- **[L5] 取数与映射型 Repository**：[PostRepository.kt:21](../app/src/main/java/com/xiaohongshu/app/data/repo/PostRepository.kt#L21)。
- **[L6] 共享互动状态与接口约定**：[InteractionStore.kt:64](../app/src/main/java/com/xiaohongshu/app/core/interact/InteractionStore.kt#L64)，执行域位于 76 行，合并位于 102 行，原始值约定与操作位于 138–143 行。
- **[L7] 模型中的展示规则**：[Models.kt:3](../app/src/main/java/com/xiaohongshu/app/domain/model/Models.kt#L3)，比例规则位于 53、92–93 行。
- **[L8] 导航入口与接口**：[AppNavHost.kt:106](../app/src/main/java/com/xiaohongshu/app/navigation/AppNavHost.kt#L106)；[AppNavigator.kt](../app/src/main/java/com/xiaohongshu/app/navigation/AppNavigator.kt)。
- **[L9] 依赖配置与测试目录检查**：[app/build.gradle.kts](../app/build.gradle.kts)；本次文件检查确认 `app/src/test` 与 `app/src/androidTest` 不存在。
- **[L10] IM 页面中的多来源数据编排**：[ChatRepository.kt:18](../app/src/main/java/com/xiaohongshu/app/data/repo/ChatRepository.kt#L18)；[ChatViewModel.kt:53](../app/src/main/java/com/xiaohongshu/app/feature/message/ChatViewModel.kt#L53)。
- **[L11] 网络装配依赖业务 API**：[ApiClient.kt:122](../app/src/main/java/com/xiaohongshu/app/core/net/ApiClient.kt#L122)；[UserApi.kt:3](../app/src/main/java/com/xiaohongshu/app/data/api/UserApi.kt#L3)。
- **[L12] ViewModel 直接依赖传输 DTO**：[EditProfileViewModel.kt:8](../app/src/main/java/com/xiaohongshu/app/feature/profile/EditProfileViewModel.kt#L8)。

## 官方来源

- **[S1] 固定版本**：[GitHub main 提交 API](https://api.github.com/repos/android/nowinandroid/commits/a49ed253d75e61a2b6ab80a8da677b57437b08eb)。
- **[S2] 架构推荐与优先级**：[Android Developers 主站](https://developer.android.com/topic/architecture/recommendations)；[本次读取的 Google 官方中国站](https://developer.android.google.cn/topic/architecture/recommendations?hl=en)。对应段落：Layered architecture、UI layer、ViewModel、Handle dependencies、Testing。
- **[S3] NIA 模块化说明**：[ModularizationLearningJourney.md](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/docs/ModularizationLearningJourney.md#L68)，尤其 Feature modules（75–80 行）与 Further considerations（228–232 行）。
- **[S4] 模块化收益与成本**：[Android Developers 主站](https://developer.android.com/topic/modularization)；[本次读取的 Google 官方中国站](https://developer.android.google.cn/topic/modularization?hl=en)。对应段落：Benefits of modularization、Common pitfalls、Is modularization the right technique for me?
- **[S5] NIA 模块列表**：[settings.gradle.kts:48](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/settings.gradle.kts#L48)。
- **[S6] NIA 架构说明**：[ArchitectureLearningJourney.md](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/docs/ArchitectureLearningJourney.md)，对应 Architecture overview、Data layer、Domain layer、UI Layer。
- **[S7] Domain 可选与访问规则**：[Android Developers 主站](https://developer.android.com/topic/architecture/domain-layer)；[本次读取的 Google 官方中国站](https://developer.android.google.cn/topic/architecture/domain-layer?hl=en)。开头说明可选；Access restriction 说明只在需要时添加 Use Case；Testing 推荐 fake Repository。
- **[S8] NIA ViewModel 与写操作**：[ForYouViewModel.kt:44](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/feature/foryou/impl/src/main/kotlin/com/google/samples/apps/nowinandroid/feature/foryou/impl/ForYouViewModel.kt#L44)，状态流在 86–109 行，直接写 Repository 在 112–127 行；[GetFollowableTopicsUseCase.kt:31](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/core/domain/src/main/kotlin/com/google/samples/apps/nowinandroid/core/domain/GetFollowableTopicsUseCase.kt#L31)。
- **[S9] NIA Compose 状态收集与拆分**：[ForYouScreen.kt:108](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/feature/foryou/impl/src/main/kotlin/com/google/samples/apps/nowinandroid/feature/foryou/impl/ForYouScreen.kt#L108)。
- **[S10] NIA 本地读取与同步**：[OfflineFirstNewsRepository.kt:44](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/core/data/src/main/kotlin/com/google/samples/apps/nowinandroid/core/data/repository/OfflineFirstNewsRepository.kt#L44)，网络取回与本地 upsert 在 102–124 行；[NIA Data synchronization 说明](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/docs/ArchitectureLearningJourney.md#L244)。
- **[S11] SSOT 与两层基础架构**：[Android Developers 主站](https://developer.android.com/topic/architecture)；[本次读取的 Google 官方中国站](https://developer.android.google.cn/topic/architecture?hl=en)。对应 Single source of truth、Recommended app architecture、Domain layer。
- **[S12] 离线优先前提与本地数据源**：[Android Developers 主站](https://developer.android.com/topic/architecture/data-layer/offline-first)；[本次读取的 Google 官方中国站](https://developer.android.google.cn/topic/architecture/data-layer/offline-first?hl=en)。对应 Design an offline-first app、The local data source、Reads。
- **[S13] NIA Hilt 绑定**：[DataModule.kt:38](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/core/data/src/main/kotlin/com/google/samples/apps/nowinandroid/core/data/di/DataModule.kt#L38)。
- **[S14] NIA 测试策略**：[README.md:83](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/README.md#L83)。说明 Hilt 测试替身、ViewModel 手工构造、真实 DataStore 与不使用 mocking 库。
- **[S15] 实际测试**：[ForYouViewModelTest.kt:62](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/feature/foryou/impl/src/test/kotlin/com/google/samples/apps/nowinandroid/feature/foryou/impl/ForYouViewModelTest.kt#L62)；[OfflineFirstNewsRepositoryTest.kt:53](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/core/data/src/test/kotlin/com/google/samples/apps/nowinandroid/core/data/repository/OfflineFirstNewsRepositoryTest.kt#L53)。
- **[S16] NIA Navigation 3 入口**：[NiaApp.kt:257](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/app/src/main/kotlin/com/google/samples/apps/nowinandroid/ui/NiaApp.kt#L257)。
- **[S17] NIA 共享 Navigator**：[Navigator.kt:26](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/core/navigation/src/main/kotlin/com/google/samples/apps/nowinandroid/core/navigation/Navigator.kt#L26)；[TopicNavKey.kt:23](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/feature/topic/api/src/main/kotlin/com/google/samples/apps/nowinandroid/feature/topic/api/navigation/TopicNavKey.kt#L23)。
- **[S18] 导航状态与作用域**：[NavigationState.kt:34](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/core/navigation/src/main/kotlin/com/google/samples/apps/nowinandroid/core/navigation/NavigationState.kt#L34)，back stack 保存位于 38–50 行，entry decorators 位于 84–96 行。
- **[S19] 当前新闻组合实现**：[CompositeUserNewsResourceRepository.kt:29](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/core/data/src/main/kotlin/com/google/samples/apps/nowinandroid/core/data/repository/CompositeUserNewsResourceRepository.kt#L29)。
