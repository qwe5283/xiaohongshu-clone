package com.xiaohongshu.app.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.ui.SheetAction
import com.xiaohongshu.app.core.ui.XhsActionSheet
import com.xiaohongshu.app.core.ui.XhsOverlayInputBar
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.di.appViewModel
import com.xiaohongshu.app.navigation.AppNavigator

/**
 * WS-Profile 的四个入口（F1–F6），全部由本文件暴露；内容层在同目录的 Screen 文件里。
 *
 * | 入口 | 线框 | 实现文件 |
 * |------|------|----------|
 * | [MyProfileRoute] | F1 + F4 + F6 | `MyProfileScreen.kt` / `ProfileDrawer` |
 * | [UserProfileRoute] | F2 | `UserProfileScreen.kt` |
 * | [EditProfileRoute] | F3 + F3-1 + F3-2 | `EditProfileScreen.kt` |
 * | [SettingsRoute] | F5 + F6 | `SettingsScreen.kt` |
 *
 * 公共件：`ProfileCommon.kt`（页签行 / 小组件卡 / 去发布 banner / 本地选图 / F6 弹层）、
 * `ProfileHeader.kt`（F1/F2 共用资料头）。
 */

/**
 * F1 我的主页（Tab 根页面）+ F4 抽屉 + F6 退出确认。
 *
 * 线框 F1 的层级：头图（含浮层顶栏/头像/昵称/统计/简介/性别）→ 小组件行 → 「去发布」banner
 * → segment 页签行（固定）→ 瀑布流（`flex:1` 的自滚动区）。底 Tab 由 `MainScaffold` 持有。
 */
@Composable
fun MyProfileRoute(navigator: AppNavigator) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val vm: MyProfileViewModel = appViewModel {
        MyProfileViewModel(
            posts = it.postRepository,
            session = it.sessionManager,
            interactions = it.interactionStore,
            toasts = it.toastController,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val session by container.sessionManager.state.collectAsStateWithLifecycle()

    var drawerOpen by remember { mutableStateOf(false) }
    var logoutVisible by remember { mutableStateOf(false) }

    // 进入 / 返回本页时刷新资料（统计行与小组件卡副文）
    LaunchedEffect(session.loggedIn) { if (session.loggedIn) vm.refreshUser() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(XhsColor.Bg),
    ) {
        MyProfileScreen(
            state = state,
            loggedIn = session.loggedIn,
            // F1：☰ → F4 抽屉；抽屉仅登录态可达（游客 ☰ → A2 登录页，线框 F4）
            onLeftAction = { if (session.loggedIn) drawerOpen = true else navigator.toLogin() },
            onEditProfile = {
                if (session.loggedIn) navigator.toEditProfile() else navigator.toLogin()
            },
            onCopyRedId = { copyToClipboard(context, "小红书号", state.user.displayRedId) },
            onBioClick = {
                if (session.loggedIn) navigator.toEditProfile() else navigator.toLogin()
            },
            // 「去发布」：写操作，先过登录拦截（§4.4），未登录则暂存动作并推入登录页
            onPublish = {
                if (!container.loginGate.runOrDefer { navigator.toPublishForm() }) navigator.toLogin()
            },
            onBannerClose = vm::dismissPublishBanner,
            onTabSelect = vm::selectTab,
            onNoteClick = navigator::toNote,
            onAuthorClick = navigator::toUserProfile,
            // 卡片 ♥：入参是渲染值，VM 内部按 id 反查服务端原值再交状态机（§4.3）
            onLikeClick = { note ->
                if (!container.loginGate.runOrDefer { vm.toggleLike(note.id) }) navigator.toLogin()
            },
            onRetry = vm::retry,
            onLogin = navigator::toLogin,
            listFor = vm::listOf,
        )

        // F4 抽屉（F1 内的浮层，不是路由）
        ProfileDrawer(
            visible = drawerOpen,
            user = state.user,
            onDismiss = { drawerOpen = false },
            onSettings = {
                drawerOpen = false
                navigator.toSettings()
            },
            onLogout = {
                drawerOpen = false
                logoutVisible = true
            },
        )

        // F6 退出登录确认（F4 / F5 共用的同一弹层）
        ProfileLogoutSheet(
            visible = logoutVisible,
            onDismiss = { logoutVisible = false },
            onLoggedOut = {
                logoutVisible = false
                navigator.toMain()
            },
        )
    }
}

/**
 * F2 他人主页（推入式，线框 F2）。
 *
 * 与 F1 的差异：无小组件行、无「去发布」banner、无「编辑主页」pill（顶栏左为 ←、右为「⋯」视觉占位）、
 * Tab 仅「笔记 / 收藏」、关注为**通栏大按钮**（D2 同一状态机）；自己的主页不显示关注按钮。
 */
@Composable
fun UserProfileRoute(
    navigator: AppNavigator,
    userId: Long,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    // key 带上 userId：同一路由换用户时不会复用上一个用户的 VM
    val vm: UserProfileViewModel = appViewModel(key = "user-profile-$userId") {
        UserProfileViewModel(
            userId = userId,
            posts = it.postRepository,
            follow = it.followRepository,
            users = it.userRepository,
            session = it.sessionManager,
            interactions = it.interactionStore,
            toasts = it.toastController,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val session by container.sessionManager.state.collectAsStateWithLifecycle()

    LaunchedEffect(userId) { vm.loadAuthor() }

    UserProfileScreen(
        state = state,
        loggedIn = session.loggedIn,
        onBack = navigator::back,
        // F2「⋯」菜单为视觉占位：无行为
        onMoreClick = {},
        onCopyRedId = { copyToClipboard(context, "小红书号", state.author.displayRedId) },
        onFollowToggle = {
            // D2：需登录的写操作，先过登录拦截（§4.4，不弹 Toast）
            if (!container.loginGate.runOrDefer { vm.toggleFollow() }) navigator.toLogin()
        },
        onTabSelect = vm::selectTab,
        onNoteClick = navigator::toNote,
        onAuthorClick = navigator::toUserProfile,
        onLikeClick = { note ->
            if (!container.loginGate.runOrDefer { vm.toggleLike(note.id) }) navigator.toLogin()
        },
        onRetry = vm::retry,
        listFor = vm::listOf,
    )
}

/** F3-2 性别选项（线框 F3-2：男 / 女 / 保密；保密 = 未设置 → `gender = 0`）。 */
private val GenderOptions: List<SheetAction> = listOf(
    SheetAction(label = "男", key = GENDER_MALE.toString()),
    SheetAction(label = "女", key = GENDER_FEMALE.toString()),
    SheetAction(label = "保密", key = GENDER_UNKNOWN.toString()),
)

/** F3 头像 / 背景图的选取目标。 */
private enum class EditImageTarget { AVATAR, BACKGROUND }

/**
 * F3 编辑资料（推入式，线框 F3）+ F3-1 遮罩输入 + F3-2 性别遮罩选项。
 *
 * 行序固定（线框 T-3 实测）：名字 / 小红书号(不可编辑) / 背景图 / 简介 / 性别 / 生日 / 地区 / 职业 / 学校；
 * 头像单独置于列表上方（线框 F3 的居中头像块）。
 */
@Composable
fun EditProfileRoute(navigator: AppNavigator) {
    val container = LocalAppContainer.current
    val vm: EditProfileViewModel = appViewModel {
        EditProfileViewModel(
            session = it.sessionManager,
            uploads = it.uploadRepository,
            toasts = it.toastController,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()

    /** 当前打开的 F3-1 字段（null = 遮罩关闭）。 */
    var activeField by remember { mutableStateOf<EditField?>(null) }

    /** 校验失败后要回填到遮罩输入框的文本（公共组件在发送时会清空自身，故用 initialText 复位）。 */
    var retainedText by remember { mutableStateOf("") }
    var genderSheet by remember { mutableStateOf(false) }
    var imageTarget by remember { mutableStateOf(EditImageTarget.AVATAR) }

    val picker = rememberProfileImagePicker(
        onPicked = { path ->
            when (imageTarget) {
                EditImageTarget.AVATAR -> vm.updateAvatar(path)
                EditImageTarget.BACKGROUND -> vm.updateBackground(path)
            }
        },
        onFailure = { message -> container.toastController.show(message) },
    )

    // F3：提交中 ← 不可返回（线框）；系统返回同样拦掉
    BackHandler(enabled = state.saving) { }

    val field = activeField
    EditProfileScreen(
        state = state,
        onBack = { if (!state.saving) navigator.back() },
        onSave = { vm.save(onSuccess = navigator::back) },
        onFieldClick = { target ->
            retainedText = ""
            activeField = target
        },
        onGenderClick = { genderSheet = true },
        onAvatarClick = {
            imageTarget = EditImageTarget.AVATAR
            picker.pickAvatar()
        },
        onBackgroundClick = {
            imageTarget = EditImageTarget.BACKGROUND
            picker.pickBackground()
        },
    )

    // ---- F3-1 遮罩式弹出输入框（与 C2-4/C3-3 同一公共组件）----
    XhsOverlayInputBar(
        visible = field != null,
        placeholder = field?.placeholder.orEmpty(),
        initialText = retainedText.ifEmpty {
            field?.let { state.draft.valueOf(it) }.orEmpty()
        },
        maxLength = field?.maxLength,
        singleLine = true,
        // 线框 F3-1：点遮罩或**收起键盘**取消（回到 F3）
        dismissOnKeyboardHide = true,
        onDismiss = {
            activeField = null
            retainedText = ""
        },
        onSend = { text ->
            val target = field
            if (target == null) {
                activeField = null
            } else {
                val error = validateField(target, text)
                if (error != null) {
                    // 校验不通过：停留本遮罩 + Toast（线框 F3-1），并把已输入内容回填
                    container.toastController.show(error)
                    retainedText = text
                } else {
                    vm.updateField(target, text)
                    retainedText = ""
                    activeField = null
                }
            }
        },
    )

    // ---- F3-2 遮罩式选项弹层（与 E1 同一公共组件）----
    XhsActionSheet(
        visible = genderSheet,
        actions = GenderOptions,
        onAction = { action ->
            action.key.toIntOrNull()?.let(vm::updateGender)
            genderSheet = false
        },
        onDismiss = { genderSheet = false },
    )
}

/** F5 设置（推入式，线框 F5）；子页均为占位，底部「退出登录」→ F6。 */
@Composable
fun SettingsRoute(navigator: AppNavigator) {
    var logoutVisible by remember { mutableStateOf(false) }

    SettingsScreen(
        onBack = navigator::back,
        onLogoutClick = { logoutVisible = true },
    )

    ProfileLogoutSheet(
        visible = logoutVisible,
        onDismiss = { logoutVisible = false },
        onLoggedOut = {
            logoutVisible = false
            navigator.toMain()
        },
    )
}
