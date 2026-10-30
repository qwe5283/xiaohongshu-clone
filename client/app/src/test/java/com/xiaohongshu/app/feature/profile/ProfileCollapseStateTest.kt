package com.xiaohongshu.app.feature.profile

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCollapseStateTest {
    private fun state() = ProfileCollapseState().apply {
        updateMeasurements(headerHeight = 300, barHeight = 60)
        avatarBottom = 180f
    }

    @Test
    fun `upward scroll collapses header and leaves excess distance for the waterfall`() {
        val state = state()
        assertEquals(Offset(0f, -240f), state.onPreScroll(Offset(0f, -300f), NestedScrollSource.UserInput))
        assertEquals(240f, state.offset)
        assertEquals(Offset.Zero, state.onPreScroll(Offset(0f, -100f), NestedScrollSource.SideEffect))
    }

    @Test
    fun `downward scroll reaches the list first and only unconsumed distance expands the header`() {
        val state = state()
        state.consumeScroll(-240f)
        assertEquals(Offset.Zero, state.onPreScroll(Offset(0f, 100f), NestedScrollSource.UserInput))
        assertEquals(240f, state.offset)
        assertEquals(Offset(0f, 40f), state.onPostScroll(Offset(0f, 60f), Offset(0f, 40f), NestedScrollSource.UserInput))
        assertEquals(200f, state.offset)
        assertEquals(Offset(0f, 200f), state.onPostScroll(Offset.Zero, Offset(0f, 300f), NestedScrollSource.SideEffect))
        assertEquals(0f, state.offset)
    }

    @Test
    fun `opacity follows measured avatar threshold and reverses when expanded`() {
        val state = state()
        assertEquals(0f, state.topBarAlpha)
        assertFalse(state.showTopBarAvatar)
        state.consumeScroll(-60f)
        assertEquals(0.5f, state.topBarAlpha)
        assertFalse(state.showTopBarAvatar)
        state.consumeScroll(-60f)
        assertEquals(1f, state.topBarAlpha)
        assertTrue(state.showTopBarAvatar)
        state.consumeScroll(-120f)
        assertEquals(1f, state.topBarAlpha)
        state.consumeScroll(121f)
        assertFalse(state.showTopBarAvatar)
        assertTrue(state.topBarAlpha < 1f)
    }

    @Test
    fun `restored offset survives measurement and clamps if profile becomes shorter`() {
        val state = ProfileCollapseState(initialOffset = 200f)
        state.updateMeasurements(headerHeight = 400, barHeight = 80)
        assertEquals(200f, state.offset)
        state.updateMeasurements(headerHeight = 240, barHeight = 80)
        assertEquals(160f, state.offset)
        state.avatarBottom = 180f
        assertEquals(1f, state.topBarAlpha)
        state.updateMeasurements(headerHeight = 400, barHeight = 20)
        assertTrue(state.showTopBarAvatar)
        state.avatarBottom = 220f
        assertEquals(0.8f, state.topBarAlpha)
        assertFalse(state.showTopBarAvatar)
    }

    @Test
    fun `unmeasured avatar does not flash a collapsed toolbar`() {
        val state = ProfileCollapseState(initialOffset = 100f)
        state.updateMeasurements(headerHeight = 300, barHeight = 60)
        assertEquals(0f, state.topBarAlpha)
        assertFalse(state.showTopBarAvatar)
    }
}
