package com.xiaohongshu.app.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全局 Toast 中心（I2）。
 *
 * 规范：**屏幕中心**显示 2.5s 后自动消失；黑底白字圆角条。
 * 三类反馈分工（I2）：Toast（本类）/ 列表错误态+重试（B4-2）/ 表单错误条（A5-1、E4）。
 */
class ToastController {

    private val _messages = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun show(message: String) {
        if (message.isBlank()) return
        _messages.tryEmit(message)
    }
}

/** Toast 展示时长。 */
private const val TOAST_DURATION_MS = 2000L

/**
 * 把 [ToastController] 的流渲染为屏幕中心黑底圆角条。挂在根布局最上层，覆盖所有页面。
 * 同一时刻只展示最新一条；连续触发会重新计时。
 */
@Composable
fun XhsToastHost(
    controller: ToastController,
    modifier: Modifier = Modifier,
) {
    var message by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    // 每条消息递增，作为计时 key：相同文案连续触发也能重新计时。
    var showCount by remember { mutableStateOf(0) }

    LaunchedEffect(controller) {
        controller.messages.collect { incoming ->
            message = incoming
            visible = true
            showCount++
        }
    }

    LaunchedEffect(showCount) {
        if (showCount > 0) {
            delay(TOAST_DURATION_MS)
            // 只收起可见性、保留文本，淡出动画期间才不会渲染成空胶囊。
            visible = false
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 80.dp, max = 280.dp)
                    .background(Color(0xE6000000), RoundedCornerShape(Dimens.radiusPill))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = message.orEmpty(),
                    style = XhsType.meta,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
