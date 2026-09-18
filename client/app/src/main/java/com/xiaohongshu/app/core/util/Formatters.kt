package com.xiaohongshu.app.core.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 时间与计数的展示格式化。
 *
 * 契约里的时间是 `LocalDateTime` 文本（无时区），形如 `2026-09-14T10:16:00`。
 * 统一解析为本地时区 epoch millis，再由下列函数按线框语义渲染。
 */
object Formatters {

    private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val SPACE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val DATE_ONLY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
    private val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val YEAR_MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 解析服务端时间文本；无法解析返回 0（UI 侧渲染为空字符串）。 */
    fun parseApiTime(raw: String): Long {
        if (raw.isBlank()) return 0L
        val text = raw.trim()
        val parsed: LocalDateTime? = runCatching { LocalDateTime.parse(text, ISO) }
            .recoverCatching { LocalDateTime.parse(text, SPACE) }
            .recoverCatching {
                // 只有日期（如 birthday / birthday 回填）→ 当天 00:00
                LocalDate.parse(text, DATE_ONLY).atStartOfDay()
            }
            .getOrNull()
        return parsed?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L
    }

    private fun toLocal(epochMillis: Long): LocalDateTime =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())

    /**
     * C1-1 正文下的时间行 / C2 作者行：今天 `今天 10:16`、昨天 `昨天 10:16`、更早 `2026-09-14`。
     */
    fun formatNoteTime(epochMillis: Long): String {
        if (epochMillis <= 0) return ""
        val time = toLocal(epochMillis)
        val today = LocalDate.now()
        return when (time.toLocalDate()) {
            today -> "今天 ${time.format(HOUR_MINUTE)}"
            today.minusDays(1) -> "昨天 ${time.format(HOUR_MINUTE)}"
            else -> time.format(YEAR_MONTH_DAY)
        }
    }

    /**
     * 评论/通知的相对时间：`3分钟前` `2小时前` `昨天 13:14` `09-14`。
     * 线框评论 meta 行与通知条目使用。
     */
    fun formatRelative(epochMillis: Long): String {
        if (epochMillis <= 0) return ""
        val now = System.currentTimeMillis()
        val diffMinutes = ChronoUnit.MINUTES.between(
            toLocal(epochMillis), toLocal(now),
        )
        return when {
            diffMinutes < 1 -> "刚刚"
            diffMinutes < 60 -> "${diffMinutes}分钟前"
            diffMinutes < 60 * 24 -> "${diffMinutes / 60}小时前"
            diffMinutes < 60 * 24 * 2 -> "昨天 ${toLocal(epochMillis).format(HOUR_MINUTE)}"
            diffMinutes < 60 * 24 * 30 -> toLocal(epochMillis).format(MONTH_DAY)
            else -> toLocal(epochMillis).format(YEAR_MONTH_DAY)
        }
    }

    /** 会话列表右侧时间（G1）：今天 → `10:16`，昨天 → `昨天`，更早 → `09-14`。 */
    fun formatConversationTime(epochMillis: Long): String {
        if (epochMillis <= 0) return ""
        val time = toLocal(epochMillis)
        val today = LocalDate.now()
        return when (time.toLocalDate()) {
            today -> time.format(HOUR_MINUTE)
            today.minusDays(1) -> "昨天"
            today.minusYears(1) -> time.format(MONTH_DAY)
            else -> time.format(YEAR_MONTH_DAY)
        }
    }

    /**
     * 计数展示：`0` → 返回 true（调用方渲染文字标签「赞」「收藏」「评论」）；`>0` → 数字。
     * ≥10000 收敛为 `1.2万`（线框 B1 卡片示例「♥ 1.2万」）。
     */
    fun formatCount(count: Int): String {
        if (count <= 0) return ""
        if (count < 10_000) return count.toString()
        val wan = count / 10_000.0
        // 1.0万 显示为 1万；1.25万 显示为 1.2万
        val text = if (wan >= 100) "%.0f".format(wan) else "%.1f".format(wan)
        return text.trimEnd('0').trimEnd('.') + "万"
    }

    /** 角标数字：>99 显示 `99+`（线框「通知」与「聊天未读」规范）。 */
    fun formatBadge(count: Long): String = if (count > 99) "99+" else count.toString()

    /**
     * 视频时间轴 `mm:ss`（C2-5 seek 态的时间文本、进度条两侧）。
     */
    fun formatVideoTime(millis: Long): String {
        if (millis <= 0) return "00:00"
        val totalSeconds = millis / 1000
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    /** 小红书号 / ID 展示兜底。 */
    fun redIdOf(redId: String, userId: Long): String =
        redId.ifBlank { userId.toString() }
}
