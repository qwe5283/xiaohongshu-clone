package com.xiaohongshu.app.data.mapper

import com.xiaohongshu.app.core.util.Formatters
import com.xiaohongshu.app.data.dto.CommentDto
import com.xiaohongshu.app.data.dto.FollowUserDto
import com.xiaohongshu.app.data.dto.NotificationDto
import com.xiaohongshu.app.data.dto.NotificationType
import com.xiaohongshu.app.data.dto.PageDto
import com.xiaohongshu.app.data.dto.PostDto
import com.xiaohongshu.app.data.dto.UnreadCountDto
import com.xiaohongshu.app.data.dto.UserDto
import com.xiaohongshu.app.domain.model.Comment
import com.xiaohongshu.app.domain.model.Note
import com.xiaohongshu.app.domain.model.NoteImage
import com.xiaohongshu.app.domain.model.NotificationItem
import com.xiaohongshu.app.domain.model.Paged
import com.xiaohongshu.app.domain.model.UnreadCounts
import com.xiaohongshu.app.domain.model.User
import com.xiaohongshu.app.domain.model.UserBrief

/**
 * DTO → UI 模型。集中一处，避免各 feature 各写一套解析（时间格式/比例/兜底文案容易走样）。
 * 所有函数对空值与 0 做兜底，保证 UI 不需要判空。
 */

fun UserDto.toDomain(): User = User(
    id = id,
    username = username,
    nickname = nickname,
    avatar = avatar,
    gender = gender,
    phone = phone,
    email = email,
    bio = bio,
    backgroundImage = backgroundImage,
    birthday = birthday,
    region = region,
    occupation = occupation,
    school = school,
    redId = redId,
    followingCount = followingCount,
    followersCount = followersCount,
    likeAndCollectCount = likeAndCollectCount,
    collectedPostCount = collectedPostCount,
    likedPostCount = likedPostCount,
)

fun PostDto.toDomain(): Note = Note(
    id = id,
    authorId = userId,
    authorNickname = authorNickname,
    authorAvatar = authorAvatar,
    title = title,
    content = content,
    isVideo = type == 1,
    coverUrl = coverImage,
    videoUrl = videoUrl,
    images = images
        .sortedBy { it.sortOrder }
        .map { NoteImage(url = it.imageUrl, width = it.width, height = it.height) }
        .filter { it.url.isNotBlank() },
    viewCount = viewCount,
    likeCount = likeCount,
    commentCount = commentCount,
    collectCount = collectCount,
    liked = liked,
    collected = collected,
    followed = followed,
    createdAt = Formatters.parseApiTime(createTime),
)

fun List<PostDto>.toNoteList(): List<Note> = map { it.toDomain() }

fun PageDto<PostDto>.toNotePage(): Paged<Note> = Paged(
    items = records.toNoteList(),
    page = current.toInt(),
    total = total,
    pages = pages,
)

fun CommentDto.toDomain(): Comment = Comment(
    id = id,
    postId = postId,
    userId = userId,
    nickname = userNickname,
    avatar = userAvatar,
    content = content,
    parentId = parentId,
    replyUserId = replyUserId,
    replyUserNickname = replyUserNickname,
    likeCount = likeCount,
    liked = liked,
    replyCount = replyCount,
    createdAt = Formatters.parseApiTime(createTime),
)

fun PageDto<CommentDto>.toCommentPage(): Paged<Comment> = Paged(
    items = records.map { it.toDomain() },
    page = current.toInt(),
    total = total,
    pages = pages,
)

fun NotificationDto.toDomain(): NotificationItem = NotificationItem(
    id = id,
    senderId = senderId,
    senderNickname = senderNickname,
    senderAvatar = senderAvatar,
    type = type,
    // 后端未返回 typeText 时按 type 本地兜底（契约 §7.5）
    typeText = typeText.ifBlank { NotificationType.fallbackText(type) },
    postId = postId,
    postCoverUrl = postCoverImage,
    commentId = commentId,
    content = content,
    read = read,
    createdAt = Formatters.parseApiTime(createTime),
)

fun PageDto<NotificationDto>.toNotificationPage(): Paged<NotificationItem> = Paged(
    items = records.map { it.toDomain() },
    page = current.toInt(),
    total = total,
    pages = pages,
)

fun UnreadCountDto.toDomain(): UnreadCounts = UnreadCounts(
    total = unreadCount,
    likeCollect = likeUnread,
    comment = commentUnread,
    follow = followUnread,
)

fun FollowUserDto.toDomain(): UserBrief = UserBrief(
    id = id,
    nickname = nickname,
    avatar = avatar,
    bio = bio,
)
