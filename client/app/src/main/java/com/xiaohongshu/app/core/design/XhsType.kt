package com.xiaohongshu.app.core.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * 字号阶梯 —— 线框图 v6 章节 T-4b（收敛为 8 档：11/12/14/15/16/17/20/24）。
 * 字重仅两档：常规 400、强调 600（选中 Tab、计数、按钮文字、页面大标题）。
 *
 * 用 [XhsType.s] 构造自定义档位，用下列具名 style 保证同一个语义在全局一致。
 */
object XhsType {

    private val Normal = FontWeight.Normal
    private val Emphasis = FontWeight.SemiBold

    /** 按档位取字号，[emphasis] = true 用 600 字重。 */
    fun s(size: Int, emphasis: Boolean = false): TextStyle = TextStyle(
        fontSize = size.sp,
        fontWeight = if (emphasis) Emphasis else Normal,
    )

    // ---- 11sp：卡片赞数、「直播中」徽标、写长文副文案 ----
    val badge = s(11)
    val captionSub = s(11)

    // ---- 12sp：卡片脚栏昵称/日期、IP、消息入口标签、详情底栏计数、点点 AI 声明 ----
    val cardFooter = s(12)
    val meta = s(12)
    val aiDisclaimer = s(12)

    // ---- 14sp：Tab、正文、评论内容、话题标签、顶栏昵称 ----
    val tabSelected = s(14, emphasis = true)
    val tabUnselected = s(14)
    val body = s(14)
    val comment = s(14)
    val topBarNickname = s(14)

    // ---- 15sp：Tab/底 Tab、列表标题、会话标题、设置行、segment、搜索条目 ----
    val listTitle = s(15)
    val settingRow = s(15)
    val segment = s(15)
    val searchEntry = s(15)

    // ---- 16sp：详情标题、搜索区块标题 ----
    val detailTitle = s(16, emphasis = true)
    val sectionTitle = s(16, emphasis = true)

    // ---- 17sp：评论用户名、页面大标题、输入占位 ----
    val commentName = s(17)
    val pageTitle = s(17, emphasis = true)
    val inputPlaceholder = s(17)

    /** 底 Tab 五栏文字（纯文字无图标）。 */
    val bottomTabUnselected = s(16)
    val bottomTabSelected = s(17, emphasis = true)

    // ---- 20sp：顶栏页签「关注/发现」、发布弹层行、编辑资料 label、详情页码 ----
    val topBarTabSelected = s(16, emphasis = true)
    val topBarTabUnselected = s(16)
    val sheetRow = s(17)
    val pageIndicator = s(20, emphasis = true)

    // ---- 24sp：个人主页昵称 ----
    val profileNickname = s(24, emphasis = true)

    // ---- 按钮/计数等不使用强调档 ----
    val countEmphasis = s(17, emphasis = true)
    val buttonLabel = s(15)
    val buttonLabelSmall = s(13)

    val centerAlign = TextAlign.Center
}
