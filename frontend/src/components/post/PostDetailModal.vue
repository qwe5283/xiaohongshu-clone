<script setup>
import { ref, computed, onMounted, reactive, inject } from 'vue'
import { useRouter } from 'vue-router'
import heartIcon from '../../assets/icons/heart.svg?raw'
import heartFilledIcon from '../../assets/icons/heart-filled.svg?raw'
import starIcon from '../../assets/icons/star.svg?raw'
import starFilledIcon from '../../assets/icons/star-filled.svg?raw'
import closeIcon from '../../assets/icons/close.svg?raw'
import commentIcon from '../../assets/icons/comment.svg?raw'
import { getPostDetail } from '@/api/post'
import { getComments, getReplies, createComment } from '@/api/comment'
import { toggleLikePost, getLikeStatusPost } from '@/api/like'
import { toggleCollectPost, getCollectStatusPost } from '@/api/collect'
import { toggleFollow, getFollowStatus } from '@/api/follow'
import { useUserStore } from '@/stores/user'
import { usePostStore } from '@/stores/post'
import { showToast } from '@/utils/toast'

const props = defineProps({
  postId: { type: [Number, String], required: true }
})
const emit = defineEmits(['close'])

const router = useRouter()
const userStore = useUserStore()
const postStore = usePostStore()
const closePostDetailSilent = inject('closePostDetailSilent')

const post = ref(null)
const loading = ref(true)
const comments = ref([])
const repliesMap = reactive({})      // commentId -> replies[]
const showRepliesMap = reactive({})   // commentId -> boolean
const isLiked = ref(false)
const isCollected = ref(false)
const isFollowed = ref(false)
const currentImageIndex = ref(0)
const commentText = ref('')
const submitting = ref(false)

const displayImages = computed(() => {
  if (!post.value) return []
  if (post.value.images && post.value.images.length) return post.value.images
  if (post.value.coverImage) return [{ imageUrl: post.value.coverImage }]
  return []
})

const isSelf = computed(() => {
  return !!(post.value && userStore.userInfo && post.value.userId === userStore.userInfo.id)
})

const currentHeartIcon = computed(() => isLiked.value ? heartFilledIcon : heartIcon)
const currentStarIcon = computed(() => isCollected.value ? starFilledIcon : starIcon)

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const md = `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  if (d.getFullYear() === now.getFullYear()) return md
  return `${d.getFullYear()}-${md}`
}

const loadDetail = async () => {
  loading.value = true
  try {
    const [detail, commentPage] = await Promise.all([
      getPostDetail(props.postId),
      getComments(props.postId, { pageNum: 1, pageSize: 20 }),
    ])
    post.value = detail
    comments.value = commentPage?.records || []

    if (userStore.isLoggedIn && detail) {
      const [likeRes, collectRes] = await Promise.all([
        getLikeStatusPost(props.postId).catch(() => ({ liked: false })),
        getCollectStatusPost(props.postId).catch(() => ({ collected: false })),
      ])
      isLiked.value = !!likeRes.liked
      isCollected.value = !!collectRes.collected
      if (!isSelf.value) {
        getFollowStatus(detail.userId).then(res => { isFollowed.value = !!res.followed }).catch(() => {})
      }
    }
  } catch (e) {
    // request 拦截器已处理错误
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)

// 点赞 toggle（乐观更新 + 同步 store）
const handleToggleLike = async () => {
  if (!userStore.isLoggedIn) { showToast('请先登录', 'error'); return }
  const wasLiked = isLiked.value
  const newLiked = !wasLiked
  const newCount = post.value.likeCount + (wasLiked ? -1 : 1)
  isLiked.value = newLiked
  post.value.likeCount = newCount
  // 同步到 store，让首页卡片即时刷新
  postStore.updateLike(props.postId, newLiked, newCount)
  try {
    await toggleLikePost(props.postId)
  } catch (e) {
    isLiked.value = wasLiked
    post.value.likeCount += wasLiked ? 1 : -1
    postStore.updateLike(props.postId, wasLiked, post.value.likeCount)
  }
}

// 收藏 toggle（乐观更新 + 同步 store）
const handleToggleCollect = async () => {
  if (!userStore.isLoggedIn) { showToast('请先登录', 'error'); return }
  const wasCollected = isCollected.value
  const newCollected = !wasCollected
  const newCount = post.value.collectCount + (wasCollected ? -1 : 1)
  isCollected.value = newCollected
  post.value.collectCount = newCount
  // 同步到 store，让首页卡片即时刷新
  postStore.updateCollect(props.postId, newCollected, newCount)
  try {
    await toggleCollectPost(props.postId)
  } catch (e) {
    isCollected.value = wasCollected
    post.value.collectCount += wasCollected ? 1 : -1
    postStore.updateCollect(props.postId, wasCollected, post.value.collectCount)
  }
}

// 关注 toggle
const handleToggleFollow = async () => {
  if (!userStore.isLoggedIn) { showToast('请先登录', 'error'); return }
  if (isSelf.value || !post.value) return
  const wasFollowed = isFollowed.value
  isFollowed.value = !wasFollowed
  try {
    await toggleFollow(post.value.userId)
    showToast(wasFollowed ? '已取消关注' : '关注成功', 'success')
  } catch (e) {
    isFollowed.value = wasFollowed
  }
}

// 发评论
const handleComment = async () => {
  if (!userStore.isLoggedIn) { showToast('请先登录', 'error'); return }
  const text = commentText.value.trim()
  if (!text) return
  submitting.value = true
  try {
    const newComment = await createComment({
      postId: props.postId,
      content: text,
      parentId: 0,
    })
    comments.value.unshift(newComment)
    post.value.commentCount += 1
    commentText.value = ''
    showToast('评论成功', 'success')
  } catch (e) {
    // request 拦截器已处理错误
  } finally {
    submitting.value = false
  }
}

// 加载/切换回复
const handleToggleReplies = async (comment) => {
  if (showRepliesMap[comment.id]) {
    showRepliesMap[comment.id] = false
    return
  }
  if (!repliesMap[comment.id]) {
    try {
      const page = await getReplies(comment.id, { pageSize: 50 })
      repliesMap[comment.id] = page?.records || []
    } catch (e) {
      return
    }
  }
  showRepliesMap[comment.id] = true
}

const prevImage = () => {
  if (currentImageIndex.value > 0) currentImageIndex.value--
}
const nextImage = () => {
  if (currentImageIndex.value < displayImages.value.length - 1) currentImageIndex.value++
}

const goAuthorProfile = () => {
  if (!post.value?.userId) return
  // 静默关闭弹窗（不触发 history.back），再由 router.push 接管 URL 变更
  closePostDetailSilent()
  router.push({ name: 'user-profile', params: { id: post.value.userId } })
}

const handleClose = () => emit('close')
</script>

<template>
  <div class="fixed inset-0 bg-black/30 z-[100] flex justify-center items-center" @click.self="handleClose">
    <!-- 关闭按钮 -->
    <button
      class="absolute top-5 left-5 text-white text-2xl cursor-pointer z-[101] bg-black/50 size-8 rounded-full flex items-center justify-center"
      @click="handleClose"
    >
      <span class="[&>svg]:size-4 text-white" v-html="closeIcon"></span>
    </button>

    <!-- 主容器 -->
    <div v-if="loading" class="w-[1000px] h-[700px] bg-white rounded-2xl flex items-center justify-center">
      <span class="inline-block size-8 border-2 border-gray-300 border-t-primary rounded-full animate-spin mr-3"></span>
      加载中...
    </div>

    <div v-else-if="!post" class="w-[1000px] h-[700px] bg-white rounded-2xl flex items-center justify-center text-gray-400">
      笔记加载失败
    </div>

    <div v-else class="w-[1000px] h-[700px] bg-white rounded-2xl flex overflow-hidden shadow-[0_10px_30px_rgba(0,0,0,0.2)]">
      <!-- 左侧图片轮播 -->
      <div class="flex-[1.2] bg-black flex items-center justify-center relative select-none">
        <template v-if="displayImages.length">
          <img :src="displayImages[currentImageIndex].imageUrl" alt="Detail Image" class="max-w-full max-h-full object-contain" />
          <!-- 左右箭头 -->
          <button
            v-if="displayImages.length > 1"
            class="absolute left-3 top-1/2 -translate-y-1/2 w-10 h-10 bg-black/40 text-white rounded-full flex items-center justify-center cursor-pointer hover:bg-black/60"
            @click="prevImage"
          >‹</button>
          <button
            v-if="displayImages.length > 1"
            class="absolute right-3 top-1/2 -translate-y-1/2 w-10 h-10 bg-black/40 text-white rounded-full flex items-center justify-center cursor-pointer hover:bg-black/60"
            @click="nextImage"
          >›</button>
          <!-- 底部小圆点 -->
          <div v-if="displayImages.length > 1" class="absolute bottom-4 left-1/2 -translate-x-1/2 flex gap-2">
            <span
              v-for="(img, idx) in displayImages"
              :key="idx"
              class="w-2 h-2 rounded-full cursor-pointer"
              :class="idx === currentImageIndex ? 'bg-white' : 'bg-white/40'"
              @click="currentImageIndex = idx"
            ></span>
          </div>
        </template>
        <div v-else class="text-white">暂无图片</div>
      </div>

      <!-- 右侧内容 -->
      <div class="flex-1 flex flex-col p-6 min-w-0">
        <!-- 作者信息 -->
        <div class="flex justify-between items-center mb-5">
          <div class="flex items-center gap-2.5 cursor-pointer" @click="goAuthorProfile">
            <img :src="post.authorAvatar || post.author?.avatar" class="size-9 rounded-full object-cover" />
            <span class="text-sm font-medium truncate">{{ post.authorNickname || post.author?.nickname }}</span>
          </div>
          <button
            v-if="!isSelf"
            class="border px-6 py-2 rounded-[20px] text-sm font-bold cursor-pointer transition-all"
            :class="isFollowed ? 'bg-white text-primary border-primary' : 'bg-primary text-white border-primary'"
            @click="handleToggleFollow"
          >
            {{ isFollowed ? '已关注' : '关注' }}
          </button>
        </div>

        <!-- 内容区域 -->
        <div class="flex-1 overflow-y-auto min-h-0">
          <div class="text-lg font-bold mb-3">{{ post.title }}</div>
          <div class="text-sm leading-[1.6] text-gray-800 mb-3 whitespace-pre-wrap">{{ post.content }}</div>
          <div class="text-xs text-gray-400 mb-5">编辑于 {{ formatTime(post.createTime) }}</div>

          <div class="text-xs text-gray-400 mb-2.5">
            共 {{ post.commentCount }} 条评论
          </div>

          <!-- 评论列表 -->
          <div class="border-t border-gray-200 pt-5">
            <div v-if="comments.length === 0" class="text-sm text-gray-400 py-4 text-center">暂无评论，来说点什么吧～</div>
            <div v-for="comment in comments" :key="comment.id" class="mb-5">
              <div class="flex items-start gap-2 mb-1.5">
                <img :src="comment.userAvatar" class="size-6 rounded-full object-cover shrink-0" />
                <div class="flex-1 min-w-0">
                  <span class="text-xs text-gray-500">
                    {{ comment.userNickname }}
                    <span v-if="comment.userId === post.userId" class="bg-[#fee] text-red-500 px-1 py-0.5 rounded text-[10px] ml-1">作者</span>
                  </span>
                  <div class="text-sm text-gray-800 mt-0.5">{{ comment.content }}</div>
                  <div class="flex items-center gap-3 mt-1">
                    <span class="text-xs text-gray-400">{{ formatTime(comment.createTime) }}</span>
                    <!-- 回复按钮（本次只做展示，不实现回复输入） -->
                    <span class="text-xs text-gray-400 cursor-pointer hover:text-primary">回复</span>
                  </div>

                  <!-- 查看回复 -->
                  <div v-if="(comment.replyCount || 0) > 0" class="mt-2">
                    <button
                      class="text-xs text-primary font-medium"
                      @click="handleToggleReplies(comment)"
                    >
                      {{ showRepliesMap[comment.id] ? '收起' : `展开 ${comment.replyCount} 条回复` }}
                    </button>
                    <!-- 回复列表 -->
                    <div v-if="showRepliesMap[comment.id]" class="mt-2 space-y-2 pl-3 border-l-2 border-gray-100">
                      <div v-for="reply in repliesMap[comment.id]" :key="reply.id" class="text-sm">
                        <span class="text-gray-500">
                          <span class="font-medium">{{ reply.userNickname }}</span>
                          <span v-if="reply.replyUserNickname"> 回复 <span class="font-medium">{{ reply.replyUserNickname }}</span></span>
                          <span class="text-gray-800">：{{ reply.content }}</span>
                        </span>
                        <div class="text-xs text-gray-400 mt-0.5">{{ formatTime(reply.createTime) }}</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部操作栏 -->
        <div class="border-t border-gray-200 pt-4 flex items-center gap-4 shrink-0">
          <input
            v-model="commentText"
            type="text"
            class="flex-1 bg-gray-100 border-none rounded-[20px] px-4 py-2.5 text-sm outline-none"
            placeholder="说点什么..."
            @keyup.enter="handleComment"
          />
          <div class="flex gap-4 text-gray-500 items-center">
            <div class="flex items-center gap-1 text-sm cursor-pointer" @click="handleToggleLike">
              <span
                class="size-5 [&>svg]:size-5 transition-colors"
                :class="isLiked ? 'text-red-500' : 'text-gray-500'"
                v-html="currentHeartIcon"
              ></span>
              <span :class="isLiked ? 'text-red-500' : 'text-gray-500'">{{ post.likeCount }}</span>
            </div>
            <div class="flex items-center gap-1 text-sm cursor-pointer" @click="handleToggleCollect">
              <span
                class="size-5 [&>svg]:size-5 transition-colors"
                :class="isCollected ? 'text-amber-400' : 'text-gray-500'"
                v-html="currentStarIcon"
              ></span>
              <span :class="isCollected ? 'text-amber-400' : 'text-gray-500'">{{ post.collectCount }}</span>
            </div>
            <div class="flex items-center gap-1 text-sm">
              <span class="size-5 [&>svg]:size-5" v-html="commentIcon"></span>
              {{ post.commentCount }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
