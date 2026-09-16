package com.xiaohongshu.app.core.design

import androidx.compose.ui.unit.dp

/**
 * 尺寸与间距常量 —— 线框图 v6 章节 T-4c（由 T-3 真机实测归一化的「开发甜点值」）。
 * **编码以本文件为准**；与 T-3 实测值冲突时取本文件的甜点值。
 *
 * 换算参考：真机视口 411.4×914.3dp（1440×3200px @560dpi）。
 * 随屏宽变化的尺寸一律用公式（见 [Waterfall.cardWidth]），禁止写死像素宽。
 */
object Dimens {

    // ---- 间距阶梯 4 / 8 / 12 / 16 / 24 / 32 ----
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s24 = 24.dp
    val s32 = 32.dp

    /** 页面左右边距统一 16。 */
    val pagePadding = 16.dp

    /** 卡片标题区内边距 12。 */
    val cardInnerPadding = 12.dp

    // ---- 栏高体系 ----
    /** 页面顶栏 44。 */
    val topBar = 44.dp

    /** 页面顶栏 Tab 选中态装饰条长度 28。 */
    val topBarTabUnderlineWidth = 28.dp

    /** 频道栏 40。 */
    val channelBar = 40.dp

    /** segment 页签行 44。 */
    val segmentBar = 44.dp

    /** 详情顶栏与底栏 56。 */
    val detailBar = 56.dp

    /** 视频底栏 44。 */
    val videoBottomBar = 44.dp

    /** 点点输入条 48。 */
    val aiInputBar = 48.dp

    /** 点点「内容由AI生成」声明行 24（实测 21.4）。 */
    val aiDisclaimerBar = 24.dp

    /** 底 Tab 栏 45。 */
    val bottomBar = 45.dp

    /** 频道栏右侧渐隐宽度 60、箭头 16。 */
    val channelFadeWidth = 60.dp
    val channelFadeArrow = 16.dp

    // ---- 底部 Tab ----
    /** ＋按钮 52×36 圆角 12 满高红块。 */
    val plusButtonWidth = 52.dp
    val plusButtonHeight = 36.dp
    val plusButtonRadius = 12.dp

    // ---- 瀑布流 ----
    /** 瀑布流页边距 4、列距 4。 */
    val waterfallMargin = 4.dp
    val waterfallGap = 4.dp

    // ---- 圆角 ----
    /** 卡片 8。 */
    val radiusCard = 8.dp

    /** 弹层顶部/弹窗/＋按钮 12。 */
    val radiusSheet = 12.dp

    /** chip·胶囊·按钮·徽标 pill（用 50% 或大值兜底）。 */
    val radiusPill = 999.dp

    // ---- 头像 5 档 24 / 36 / 48 / 60 / 108 ----
    val avatarCard = 24.dp   // 卡片脚栏
    val avatarComment = 36.dp
    val avatarConversation = 48.dp
    val avatarDetailAuthor = 60.dp
    val avatarProfile = 108.dp

    /** 二级回复头像 24。 */
    val avatarReply = 24.dp

    /** 视频作者头像 48。 */
    val avatarVideoAuthor = 48.dp

    // ---- 图标 4 档 12 / 16 / 20 / 24 ----
    val icon12 = 12.dp
    val icon16 = 16.dp
    val icon20 = 20.dp
    val icon24 = 24.dp

    /** 互动图标（详情/视频底栏 ♥★💬）30。 */
    val iconInteract = 30.dp

    /** 评论 meta 行赞图标 26×20。 */
    val iconCommentLike = 26.dp

    // ---- 触控热区 ----
    /** 一律 ≥44（图标 24 视觉 + 透明扩边）。 */
    val minTouchTarget = 44.dp

    // ---- 输入胶囊高 ----
    val inputPillDetail = 40.dp
    val inputPillVideo = 36.dp
    val inputPillAi = 48.dp
    val inputSearch = 44.dp

    // ---- 按钮与 chip 高 ----
    /** 关注钮高 28（padding 0 12）。 */
    val buttonFollow = 28.dp

    /** 发送钮 64×40。 */
    val buttonSendWidth = 64.dp
    val buttonSendHeight = 40.dp

    /** 筛选 chip 28。 */
    val chipFilter = 28.dp

    /** 搜索历史 chip 32。 */
    val chipHistory = 32.dp

    /** 建议问题 chip 44。 */
    val chipSuggestion = 44.dp

    /** 「展开 N 条回复」行 32。 */
    val expandRepliesRow = 32.dp

    // ---- 发布弹层：行高 72×3 + 间隔 8 + 取消 56 = 总高 280 ----
    val publishSheetRow = 72.dp
    val publishSheetGap = 8.dp
    val publishSheetCancel = 56.dp
    val publishSheetTotal = 280.dp

    /** E3 编辑器：顶栏 44（「下一步」63.4×32）、画布 379.4×506、「写长文」卡高 98。 */
    val writeTextNextWidth = 64.dp
    val writeTextNextHeight = 32.dp
    val writeTextCardHeight = 98.dp

    // ---- 列表行体系 ----
    /** 会话项 72。 */
    val rowConversation = 72.dp

    /** 设置行 52。 */
    val rowSetting = 52.dp

    /** 编辑资料行 48。 */
    val rowEditProfile = 48.dp

    /** 编辑资料背景图行 40（带缩略图）。 */
    val rowEditBackground = 40.dp

    /** 抽屉项 52（宽 = 抽屉 308 − 24）。 */
    val rowDrawer = 52.dp

    /** 评论项最小高 80。 */
    val rowCommentMin = 80.dp

    // ---- 细部 ----
    /** 视频进度条：轨道 2 / 触区 6，贴底。 */
    val videoProgressTrack = 2.dp
    val videoProgressTouch = 6.dp

    /** 轮播圆点 5 / 点距 8。 */
    val carouselDot = 5.dp
    val carouselDotGap = 8.dp

    /** 未读点 8。 */
    val unreadDot = 8.dp

    /** 数字徽标高 16、最小宽 16。 */
    val badgeHeight = 16.dp
    val badgeMinWidth = 16.dp

    /** 视频角标 20×20，inset 10。 */
    val videoBadge = 20.dp
    val videoBadgeInset = 10.dp

    /** hairline 分隔线厚度（0.6dp）。 */
    val hairline = 0.6.dp

    // ---- G1 消息页 ----
    /** 三入口卡：(W−32)/3 × 高 104；区高 112。 */
    val entryCardHeight = 104.dp
    val entryRegionHeight = 112.dp

    /** 视觉图标方块 48×48 圆角 14（仅视觉，点击区＝整卡）。 */
    val entryIconBox = 48.dp
    val entryIconRadius = 14.dp

    // ---- F1 个人主页 ----
    /** 头图 282。 */
    val profileHeaderImage = 282.dp

    /** 浮层顶栏菜单 29、「编辑主页」pill 93.4×26、扫一扫/分享 24（间距 12）。 */
    val profileMenuIcon = 29.dp
    val profileEditPillWidth = 93.dp
    val profileEditPillHeight = 26.dp
    val profileTopIconGap = 12.dp

    /** 头像 @(2,78)。 */
    val profileAvatarOffsetX = 2.dp
    val profileAvatarOffsetY = 78.dp

    /** 统计行 24 / 简介行 26 / 性别 chip 30×20。 */
    val profileStatsRow = 24.dp
    val profileBioRow = 26.dp
    val genderChipWidth = 30.dp
    val genderChipHeight = 20.dp

    /** 小组件卡 131.4×64（内 123.4×48）：icon 16、label 15sp、副文 12sp。 */
    val widgetCardHeight = 64.dp
    val widgetCardInnerHeight = 48.dp

    /** 页签行条目宽 64/86（icon 18）、右搜索 44×44。 */
    val segmentItemNarrow = 64.dp
    val segmentItemWide = 86.dp
    val segmentIcon = 18.dp
    val segmentSearchBox = 44.dp

    /** 「去发布」banner 401.7×64：icon 40、CTA 46×23.7、关闭 32×64。 */
    val publishBannerHeight = 64.dp
    val publishBannerIcon = 40.dp
    val publishBannerCtaWidth = 46.dp
    val publishBannerCtaHeight = 24.dp
    val publishBannerCloseWidth = 32.dp

    /** F3 编辑资料：label 宽 89 @32、值 @137 宽 226。 */
    val editLabelWidth = 89.dp
    val editLabelStart = 32.dp
    val editValueStart = 137.dp

    /** F5 设置：label 15sp @68、icon 24 @28。 */
    val settingLabelStart = 68.dp
    val settingIconStart = 28.dp

    /** F4 抽屉宽 308、项 284×52 @12（icon 24 @28、字 @64）、分组隔 8、底部三宫格 94.6×72。 */
    val drawerWidth = 308.dp
    val drawerItemWidth = 284.dp
    val drawerIconStart = 28.dp
    val drawerTextStart = 64.dp
    val drawerGroupGap = 8.dp
    val drawerGridCell = 72.dp

    // ---- 维度比例 ----
    /** 瀑布流封面比例候选：4:3 或 3:4。 */
    const val RATIO_WIDE = 4f / 3f
    const val RATIO_TALL = 3f / 4f

    /** 详情大图全宽 3:4。 */
    const val RATIO_DETAIL_IMAGE = 3f / 4f

    /** 文本配图 2:3。 */
    const val RATIO_TEXT_IMAGE = 2f / 3f

    // ================= C1/C2/C3 详情实测值（T-3，Dimens 未覆盖处原样保留） =================
    /** 详情标题左右边距 15.1。 */
    val detailContentPadding = 15.1.dp

    /** 图片页码角标右上 inset 13。 */
    val pageBadgeInset = 13.dp

    /** 页码角标圆角 2。 */
    val pageBadgeRadius = 2.dp

    /** 评论项：头像起点 15、正文列起点 61（距头像 10）。 */
    val commentAvatarStart = 15.dp
    val commentContentStart = 61.dp

    /** 二级回复缩进：头像起点 90，正文列 124。 */
    val replyAvatarStart = 90.dp
    val replyContentStart = 124.dp

    /** 评论 meta 行高 20。 */
    val commentMetaRow = 20.dp

    /** 评论面板输入行高 52。 */
    val commentPanelInputRow = 52.dp

    /** 视频作者行 48、标题行 46。 */
    val videoAuthorRow = 48.dp
    val videoTitleRow = 46.dp

    /** 视频顶栏浮层图标：返回 26、搜索 26、分享 28。 */
    val videoTopIcon = 26.dp
    val videoShareIcon = 28.dp

    /** 视频作者行小关注钮 49×26。 */
    val followPillSmallWidth = 49.dp
    val followPillSmallHeight = 26.dp

    /** 音乐碟 22。 */
    val musicDisc = 22.dp

    /** 进度条锚点：常规 7、seek 放大 14。 */
    val progressDot = 7.dp
    val progressDotSeek = 14.dp

    /** 进度条 seek 态轨道加粗。 */
    val videoProgressTrackSeek = 4.dp

    /** 视频底部 seek 手势区（视频高度的 30%）。 */
    const val VIDEO_SEEK_ZONE_FRACTION = 0.3f
}

/** 随屏宽变化的尺寸公式。 */
object Waterfall {
    /**
     * 卡宽公式 (W − 12) / 2：4 页边距 + 4 列距 + 4 页边距（＝瀑布流两侧各 4）。
     * 411dp 视口下得 199.5 ≈ 实测 198.6，禁止写死像素宽。
     */
    fun cardWidth(screenWidthDp: Float): Float = (screenWidthDp - 12f) / 2f

    /** G1 三入口卡宽 (W − 32) / 3（三等分、无间隙、仅页边距 16）。 */
    fun entryCardWidth(screenWidthDp: Float): Float = (screenWidthDp - 32f) / 3f
}
