package com.xiaohongshu.app.core.design

import androidx.compose.ui.graphics.Color

/**
 * 色板 —— 线框图 v6 章节 T-4a（真机截图采样 → 甜点值）。**编码以本文件为准。**
 * 扁平风格：不使用投影分层，只靠 hairline 与 #F5F5F5。
 */
object XhsColor {

    /** 品牌主红：＋按钮、关注/发布主操作、点赞收藏激活、徽标。原样保留实测值。 */
    val Red = Color(0xFFEA3F4A)

    /** 文字主色：标题/正文/选中态。 */
    val Text1 = Color(0xFF333333)

    /** 文字次级：未选 Tab/频道、时间戳、辅助说明。 */
    val Text2 = Color(0xFF8C8C8C)

    /** 弱文字/占位：输入框占位、禁用、空态插画线。 */
    val Text3 = Color(0xFFCCCCCC)

    /** 分隔线（0.6dp hairline）。 */
    val Divider = Color(0xFFEEEEEE)

    /** 页面背景。 */
    val Bg = Color(0xFFFFFFFF)

    /** 瀑布流背景。 */
    val WaterfallBg = Color(0xFFF4F4F4)

    /** 浅灰胶囊/分隔底：详情输入胶囊、灰底块、覆盖层图标底。 */
    val BgGray = Color(0xFFF5F5F5)

    /** 视频沉浸页底栏区。 */
    val Dark = Color(0xFF1A1A1A)

    /** 视频沉浸页背景。 */
    val Black = Color(0xFF000000)

    /** 弹层/遮罩输入/弹窗遮罩。 */
    val Scrim = Color(0x99000000)
    val ScrimGray = Color(0x66000000)

    /** 轮播圆点未选中 / 进度轨道 / 骨架条底色。 */
    val DotInactive = Color(0xFFD9D9D9)

    /** 次级按钮底（如「已关注」态、灰按钮）。 */
    val BtnGray = Color(0xFFE4E4E4)

    /** 「已关注」类次级按钮文字。 */
    val BtnGrayText = Color(0xFF555555)

    /** 骨架屏条。 */
    val Skeleton = Color(0xFFEFEFEF)

    /** 错误文案（H3-2 正式 UI 为红色气泡）。 */
    val Error = Color(0xFFE34D4D)

    /** 图片占位斜纹底（线框 .ph 的近似）。 */
    val PlaceholderBg = Color(0xFFEDEDED)

    /** 图片页码角标底（线框实测 rgba(0,0,0,.45)）。 */
    val PageBadge = Color(0x73000000)

    /** 数字徽标白字。 */
    val OnBadge = Color(0xFFFFFFFF)
}
