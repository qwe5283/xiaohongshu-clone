package com.xiaohongshu.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.userMessage
import com.xiaohongshu.app.core.ui.ToastController
import com.xiaohongshu.app.data.dto.UpdateUserRequest
import com.xiaohongshu.app.data.local.SessionManager
import com.xiaohongshu.app.data.repo.UploadRepository
import com.xiaohongshu.app.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** 名字长度上限（线框 F3-1：名字 ≤20）。 */
internal const val NAME_MAX = 20

/** 简介长度上限（线框 F3-1：简介 ≤200）。 */
internal const val BIO_MAX = 200

/** 「没有修改任何信息」——线框 F3 定稿文案。 */
internal const val NoChangeToast = "没有修改任何信息"

/** 「保存成功」——线框 F3 定稿文案。 */
internal const val SaveSuccessToast = "保存成功"

/**
 * F3 编辑资料走 **F3-1 遮罩输入**的文本字段（线框 F3-1：名字 / 简介 / 邮箱 / 手机号）。
 *
 * 说明：本版按任务给定的 F3 行序（名字 / 小红书号 / 背景图 / 简介 / 性别 / 生日 / 地区 / 职业 / 学校）
 * 实现，其中文本行只有名字 / 简介 / 生日 / 地区 / 职业 / 学校；**没有**邮箱 / 手机号行，
 * 故 `1[3-9]\d{9}` 的手机号格式校验未接入（`UpdateUserRequest` 也没有 phone 字段）。
 */
internal enum class EditField(
    /** 遮罩内输入框占位（线框 F3-1：占位随字段变化）。 */
    val placeholder: String,
    /** 非空时显示剩余字数（线框 F3-1：名字 / 简介有计数，其余无）。 */
    val maxLength: Int?,
) {
    NAME("填写名字", NAME_MAX),
    BIO("介绍一下自己", BIO_MAX),
    BIRTHDAY("选择生日", null),
    REGION("填写地区", null),
    OCCUPATION("填写职业", null),
    SCHOOL("填写学校", null),
}

/** 生日格式（契约 §1.5：`yyyy-MM-dd`，或 `""` 清除）。 */
private val BirthdayPattern = Regex("\\d{4}-\\d{2}-\\d{2}")

/**
 * F3-1 发送前校验：返回非空字符串表示**不通过**，调用方停留遮罩并 Toast 该文案
 * （线框 F3-1 定稿文案：「名字最多20个字符」「手机号格式不正确」）。
 */
internal fun validateField(field: EditField, text: String): String? = when (field) {
    EditField.NAME -> if (text.length > NAME_MAX) "名字最多20个字符" else null
    EditField.BIO -> if (text.length > BIO_MAX) "简介最多200个字符" else null
    // 生日可留空（清除）；填了就必须是 yyyy-MM-dd
    EditField.BIRTHDAY ->
        if (text.isNotBlank() && !BirthdayPattern.matches(text)) "生日格式应为 1998-06-01" else null

    EditField.REGION, EditField.OCCUPATION, EditField.SCHOOL -> null
}

/** F3 本页暂存的草稿（**未提交后端**，由右上「保存」统一提交）。 */
internal data class ProfileDraft(
    val nickname: String = "",
    val bio: String = "",
    val gender: Int = GENDER_UNKNOWN,
    val birthday: String = "",
    val region: String = "",
    val occupation: String = "",
    val school: String = "",
    /** 已选但**未上传**的头像本地路径（选中即预览，保存时先上传换 URL）。 */
    val avatarLocalPath: String? = null,
    /** 已选但**未上传**的背景图本地路径。 */
    val backgroundLocalPath: String? = null,
) {
    fun valueOf(field: EditField): String = when (field) {
        EditField.NAME -> nickname
        EditField.BIO -> bio
        EditField.BIRTHDAY -> birthday
        EditField.REGION -> region
        EditField.OCCUPATION -> occupation
        EditField.SCHOOL -> school
    }

    /** 性别行展示（0/未设置 显示「保密」）。 */
    val genderLabel: String
        get() = when (gender) {
            GENDER_MALE -> "男"
            GENDER_FEMALE -> "女"
            else -> "保密"
        }

    companion object {
        val Empty = ProfileDraft()

        fun of(user: User): ProfileDraft = ProfileDraft(
            nickname = user.nickname,
            bio = user.bio,
            gender = user.gender,
            birthday = user.birthday,
            region = user.region,
            occupation = user.occupation,
            school = user.school,
        )
    }
}

/** F3 UI 状态。 */
internal data class EditProfileUiState(
    val user: User = User.Empty,
    val draft: ProfileDraft = ProfileDraft.Empty,
    /** 提交中：右上「保存中...」禁用，且 ← 不可返回（线框 F3）。 */
    val saving: Boolean = false,
)

/**
 * F3 编辑资料（线框 F3 / F3-1 / F3-2）。
 *
 * - 字段修改**先落本页草稿**（[ProfileDraft]），不逐字段提交；
 * - 右上「保存」→ 只提交**有变化的字段**（`UpdateUserRequest` 的空值即「未变化」，
 *   `Json.explicitNulls = false` 保证不出现在请求体里）；
 * - 头像 / 背景图先经 [UploadRepository.uploadImage] 上传换 URL，再随资料一起提交；
 * - 全未变 → Toast「没有修改任何信息」并返回；成功 → Toast「保存成功」并返回（F1 由
 *   `SessionManager.state` 自动拿到新值）；失败 → 停留本页可重试。
 */
internal class EditProfileViewModel(
    private val session: SessionManager,
    private val uploads: UploadRepository,
    private val toasts: ToastController,
) : ViewModel() {

    private val _draft = MutableStateFlow(ProfileDraft.Empty)
    private val _saving = MutableStateFlow(false)

    val state: StateFlow<EditProfileUiState> = combine(
        session.state,
        _draft,
        _saving,
    ) { sessionState, draft, saving ->
        EditProfileUiState(user = sessionState.user, draft = draft, saving = saving)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = EditProfileUiState(
            user = session.state.value.user,
            draft = ProfileDraft.of(session.state.value.user),
        ),
    )

    init {
        // 进入本页时以当前资料预填（线框 F3：字段预填当前资料）
        _draft.value = ProfileDraft.of(session.state.value.user)
    }

    // ------------------------------------------------------------------ 本页暂存

    fun updateField(field: EditField, text: String) {
        val value = text.trim()
        val draft = _draft.value
        _draft.value = when (field) {
            EditField.NAME -> draft.copy(nickname = value)
            EditField.BIO -> draft.copy(bio = value)
            EditField.BIRTHDAY -> draft.copy(birthday = value)
            EditField.REGION -> draft.copy(region = value)
            EditField.OCCUPATION -> draft.copy(occupation = value)
            EditField.SCHOOL -> draft.copy(school = value)
        }
    }

    /** F3-2 性别遮罩选项（保密 = 未设置 → `gender = 0`，资料页不显示性别图标）。 */
    fun updateGender(gender: Int) {
        _draft.value = _draft.value.copy(gender = gender)
    }

    fun updateAvatar(localPath: String) {
        _draft.value = _draft.value.copy(avatarLocalPath = localPath)
    }

    fun updateBackground(localPath: String) {
        _draft.value = _draft.value.copy(backgroundLocalPath = localPath)
    }

    // ------------------------------------------------------------------ 保存

    fun save(onSuccess: () -> Unit) {
        if (_saving.value) return
        val draft = _draft.value
        val original = session.state.value.user
        val body = diff(original, draft)
        val avatarPath = draft.avatarLocalPath
        val backgroundPath = draft.backgroundLocalPath

        if (body == null && avatarPath == null && backgroundPath == null) {
            // 线框 F3：全未变 → Toast 后直接返回
            toasts.show(NoChangeToast)
            onSuccess()
            return
        }

        _saving.value = true
        viewModelScope.launch {
            // 契约 §1.5：头像/背景图先上传拿 URL，再提交资料
            var avatarUrl: String? = null
            if (avatarPath != null) {
                when (val result = uploads.uploadImage(File(avatarPath))) {
                    is ApiResult.Ok -> avatarUrl = result.data
                    else -> {
                        // 上传失败：停留本页，草稿（含已选图片）保留可重试
                        _saving.value = false
                        toasts.show(result.userMessage())
                        return@launch
                    }
                }
            }
            var backgroundUrl: String? = null
            if (backgroundPath != null) {
                when (val result = uploads.uploadImage(File(backgroundPath))) {
                    is ApiResult.Ok -> backgroundUrl = result.data
                    else -> {
                        _saving.value = false
                        toasts.show(result.userMessage())
                        return@launch
                    }
                }
            }

            val request = (body ?: UpdateUserRequest()).copy(
                avatar = avatarUrl,
                backgroundImage = backgroundUrl,
            )

            when (val result = session.updateProfile(request)) {
                is ApiResult.Ok -> {
                    _saving.value = false
                    // 已提交成功：清掉本地待上传路径，避免二次保存重复上传
                    _draft.value = _draft.value.copy(
                        avatarLocalPath = null,
                        backgroundLocalPath = null,
                    )
                    toasts.show(SaveSuccessToast)
                    onSuccess()
                }
                else -> {
                    // 失败：停留本页，草稿保留可重试
                    _saving.value = false
                    toasts.show(result.userMessage())
                }
            }
        }
    }

    /**
     * 只构造**有变化**的字段：`null` = 未变化（`UpdateUserRequest` 的可空语义）。
     * 返回 null 表示本页暂存的文本/性别字段全部未变（头像/背景图另算）。
     */
    private fun diff(original: User, draft: ProfileDraft): UpdateUserRequest? {
        var changed = false

        fun <T> pick(new: T, old: T): T? = if (new != old) {
            changed = true
            new
        } else {
            null
        }

        val body = UpdateUserRequest(
            nickname = pick(draft.nickname, original.nickname),
            bio = pick(draft.bio, original.bio),
            gender = pick(draft.gender, original.gender),
            birthday = pick(draft.birthday, original.birthday),
            region = pick(draft.region, original.region),
            occupation = pick(draft.occupation, original.occupation),
            school = pick(draft.school, original.school),
        )
        return if (changed) body else null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
