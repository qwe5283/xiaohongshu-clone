package com.xiaohongshu.app.core.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字号阶梯 —— **编码以本文件为准**：档位在此定义并登记。
 * 字重仅两档：常规 400、强调 600（选中 Tab、计数、按钮文字、页面大标题）。
 *
 * 用 [XhsType.s] 构造档位；具名 style 保证同一个语义在全局一致，调用点**按语义选用**。
 * 新增档位或语义时在对应分组登记：分组标题的档位值必须与组内 val 一致，改档位时同步改标题。
 */
object XhsType {

    private val Normal = FontWeight.Normal
    private val Emphasis = FontWeight.SemiBold

    /** 按档位取字号，[emphasis] = true 用 600 字重。 */
    fun s(size: Int, emphasis: Boolean = false): TextStyle = TextStyle(
        fontSize = size.sp,
        fontWeight = if (emphasis) Emphasis else Normal,
    )

    // ---- 11sp：辅助短文（加载/空尾/覆盖层状态/副标题/时间） ----
    val badge = s(11)
    val captionSub = s(11)

    // ---- 12sp：卡片脚栏昵称/日期、IP、页码角标、点点 AI 声明 ----
    val cardFooter = s(12)
    val aiDisclaimer = s(12)
    val pageIndicator = s(12)

    // ---- 13sp：次级元信息（消息入口标签、会话预览、通知文案、空态与错误文案） ----
    val meta = s(13)
    val metaBold = s(13, emphasis = true)

    // ---- 14sp：Tab、正文、评论内容、话题标签、顶栏昵称、输入占位 ----
    val tabSelected = s(14, emphasis = true)
    val tabUnselected = s(14)
    val body = s(14)
    val comment = s(14)
    val topBarNickname = s(14)
    val inputPlaceholder = s(14)

    // ---- 15sp：列表标题、会话标题、设置行、segment、搜索条目、编辑资料行 ----
    val listTitle = s(15)
    val settingRow = s(15)
    val segment = s(15)
    val searchEntry = s(15)

    // ---- 16sp：区块/卡片标题（详情标题、搜索区块、写长文卡） ----
    val detailTitle = s(16, emphasis = true)
    val sectionTitle = s(16, emphasis = true)

    // ---- 17sp：大标题（页面/卡片/抽屉）与面板占位 ----
    /** 当前仅用于 C3 评论面板的输入占位；名字与实际用途不符，待改名。 */
    val commentName = s(17)

    /** 页面 / 弹窗卡片 / 抽屉的大标题。 */
    val pageTitle = s(17, emphasis = true)

    /** 底 Tab 五栏文字（纯文字无图标）。 */
    val bottomTabUnselected = s(16)
    val bottomTabSelected = s(17, emphasis = true)

    // ---- 16~17sp：顶栏页签、弹层行 ----
    val topBarTabSelected = s(16, emphasis = true)
    val topBarTabUnselected = s(16)
    val sheetRow = s(17)

    // ---- 24sp：个人主页昵称 ----
    val profileNickname = s(24, emphasis = true)

    // ---- 按钮与计数 ----
    val countEmphasis = s(17, emphasis = true)
    val buttonLabel = s(16)
    val buttonLabelSmall = s(13)
}
