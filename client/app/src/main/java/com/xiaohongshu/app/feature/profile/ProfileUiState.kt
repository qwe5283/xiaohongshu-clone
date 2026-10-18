package com.xiaohongshu.app.feature.profile

import com.xiaohongshu.app.core.list.PagedState
import com.xiaohongshu.app.domain.model.Note
import com.xiaohongshu.app.domain.model.User

internal enum class ProfilePresentation {
    TAB_ROOT,
    PUSHED,
}

internal data class ProfileUiState(
    val user: User = User.Empty,
    val isMe: Boolean = false,
    val followed: Boolean = false,
    val tab: ProfileTab = ProfileTab.NOTES,
    // 规范 §4.3：分页态保留服务端原值，互动写操作不能使用下面的渲染列表。
    val notesState: PagedState<Note> = PagedState(),
    val collectedState: PagedState<Note> = PagedState(),
    val likedState: PagedState<Note> = PagedState(),
    val notes: List<Note> = emptyList(),
    val collected: List<Note> = emptyList(),
    val liked: List<Note> = emptyList(),
) {
    val visibleTabs: List<ProfileTab>
        get() = if (isMe) ProfileTab.entries else ProfileTab.Public

    fun pageStateOf(tab: ProfileTab): PagedState<Note> = when (tab) {
        ProfileTab.NOTES -> notesState
        ProfileTab.COLLECTED -> collectedState
        ProfileTab.LIKED -> likedState
    }

    fun itemsOf(tab: ProfileTab): List<Note> = when (tab) {
        ProfileTab.NOTES -> notes
        ProfileTab.COLLECTED -> collected
        ProfileTab.LIKED -> liked
    }

    val pageState: PagedState<Note> get() = pageStateOf(tab)
    val items: List<Note> get() = itemsOf(tab)
}
