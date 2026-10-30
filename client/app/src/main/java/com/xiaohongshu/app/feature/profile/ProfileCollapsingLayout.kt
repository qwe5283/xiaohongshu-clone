package com.xiaohongshu.app.feature.profile

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

@Stable
internal class ProfileCollapseState(initialOffset: Float = 0f) : NestedScrollConnection {
    var offset by mutableFloatStateOf(initialOffset)
        private set
    private var collapseRange by mutableFloatStateOf(0f)
    private var topBarHeight by mutableFloatStateOf(0f)
    var avatarBottom by mutableFloatStateOf(0f)

    private val avatarThreshold: Float get() = avatarBottom - topBarHeight
    val topBarAlpha: Float
        get() = if (avatarThreshold > 0f) (offset / avatarThreshold).coerceIn(0f, 1f) else 0f
    val showTopBarAvatar: Boolean
        get() = avatarThreshold > 0f && offset >= avatarThreshold
    val isFullyCollapsed: Boolean
        get() = collapseRange > 0f && offset >= collapseRange

    fun updateMeasurements(headerHeight: Int, barHeight: Int) {
        topBarHeight = barHeight.toFloat()
        collapseRange = (headerHeight - barHeight).coerceAtLeast(0).toFloat()
        offset = offset.coerceIn(0f, collapseRange)
    }

    fun consumeScroll(delta: Float): Float {
        val previous = offset
        offset = (previous - delta).coerceIn(0f, collapseRange)
        return previous - offset
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        Offset(0f, if (available.y < 0f) consumeScroll(available.y) else 0f)

    // 契约 §12.8：回滑先还原瀑布流位置，列表到顶后才展开资料头。
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        Offset(0f, if (available.y > 0f) consumeScroll(available.y) else 0f)

    companion object {
        val Saver = Saver<ProfileCollapseState, Float>(
            save = { it.offset },
            restore = { ProfileCollapseState(it) },
        )
    }
}

@Composable
internal fun rememberProfileCollapseState(userId: Long): ProfileCollapseState =
    rememberSaveable(userId, saver = ProfileCollapseState.Saver) { ProfileCollapseState() }

@Composable
internal fun ProfileCollapsingLayout(
    state: ProfileCollapseState,
    header: @Composable () -> Unit,
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 契约 §12.8：资料头、页签及无列表的四态也需接受拖动和惯性滚动。
    val scrollState = rememberScrollableState(state::consumeScroll)
    val headerHidden by remember(state) { derivedStateOf { state.isFullyCollapsed } }
    Layout(
        modifier = modifier
            .clipToBounds()
            .scrollable(scrollState, Orientation.Vertical)
            .nestedScroll(state),
        content = {
            Box(modifier = if (headerHidden) Modifier.clearAndSetSemantics {} else Modifier) { header() }
            Box { content() }
            Box { topBar() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val headerPlaceable = measurables[0].measure(loose)
        val barPlaceable = measurables[2].measure(loose)
        state.updateMeasurements(headerPlaceable.height, barPlaceable.height)
        val headerOffset = state.offset.roundToInt()
        val contentTop = headerPlaceable.height - headerOffset
        val contentPlaceable = measurables[1].measure(
            Constraints.fixed(constraints.maxWidth, (constraints.maxHeight - contentTop).coerceAtLeast(0)),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            headerPlaceable.placeRelative(0, -headerOffset)
            contentPlaceable.placeRelative(0, contentTop)
            barPlaceable.placeRelative(0, 0)
        }
    }
}
