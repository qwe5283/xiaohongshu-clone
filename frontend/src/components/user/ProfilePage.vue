<script setup>
import { ref, computed, onMounted, watch, inject } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUserById } from '@/api/auth'
import { getUserPosts } from '@/api/post'
import { getCollectedPosts } from '@/api/collect'
import { getFollowCount, getFollowStatus, toggleFollow } from '@/api/follow'
import { useUserStore } from '@/stores/user'
import { showToast } from '@/utils/toast'
import { adaptPost } from '@/api/post'
import SearchBar from '@/components/layout/SearchBar.vue'
import PageShell from '@/components/layout/PageShell.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import PostGrid from '@/components/post/PostGrid.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const openPostDetail = inject('openPostDetail')

// 路由 id 可能是 'me'（已在上层路由守卫解析，但组件内仍需考虑刷新/边界情况）
const userId = computed(() => Number(route.params.id) || null)
const isMe = computed(() => userStore.userInfo?.id === userId.value)

const loading = ref(false)
const user = ref(null)
const activeTab = ref('notes')
const notes = ref([])
const collects = ref([])
const followStats = ref({ followingCount: 0, followersCount: 0 })
const isFollowed = ref(false)

const defaultAvatar =
  'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23eee"/><text x="50%" y="55%" text-anchor="middle" font-size="18" fill="%23bbb">U</text></svg>'

const loadProfile = async () => {
  if (!userId.value) return
  loading.value = true
  try {
    const [profile, stats] = await Promise.all([
      getUserById(userId.value),
      getFollowCount(userId.value),
    ])
    user.value = profile
    followStats.value = stats
    // 关注状态
    if (userStore.isLoggedIn && !isMe.value) {
      getFollowStatus(userId.value).then(res => { isFollowed.value = !!res.followed }).catch(() => {})
    }
    // 默认加载笔记
    await loadNotes()
  } catch (e) {
    // request 拦截器已处理错误
  } finally {
    loading.value = false
  }
}

const loadNotes = async () => {
  if (!userId.value) return
  try {
    const page = await getUserPosts(userId.value, { pageNum: 1, pageSize: 20 })
    notes.value = (page?.records || []).map(adaptPost)
  } catch (e) {
    // request 拦截器已处理错误
  }
}

const loadCollects = async () => {
  if (!userId.value) return
  try {
    const res = await getCollectedPosts(userId.value, { pageNum: 1, pageSize: 20 })
    collects.value = (res?.records || []).map(adaptPost)
  } catch (e) {
    // request 拦截器已处理错误
  }
}

onMounted(loadProfile)

// 路由参数变化时重新加载（他人主页切换）
watch(() => route.params.id, () => {
  activeTab.value = 'notes'
  notes.value = []
  collects.value = []
  user.value = null
  isFollowed.value = false
  loadProfile()
})

const handleTabChange = async (tab) => {
  activeTab.value = tab
  if (tab === 'notes' && notes.value.length === 0) {
    await loadNotes()
  }
  if (tab === 'collect' && collects.value.length === 0) {
    await loadCollects()
  }
}

const handleToggleFollow = async () => {
  if (isMe.value || !user.value) return
  if (!userStore.isLoggedIn) { showToast('请先登录', 'error'); return }
  const wasFollowed = isFollowed.value
  isFollowed.value = !wasFollowed
  // 乐观更新粉丝数
  if (isFollowed.value) {
    followStats.value.followersCount += 1
  } else {
    followStats.value.followersCount -= 1
  }
  try {
    await toggleFollow(userId.value)
    showToast(wasFollowed ? '已取消关注' : '关注成功', 'success')
  } catch (e) {
    isFollowed.value = wasFollowed
    followStats.value.followersCount += wasFollowed ? 1 : -1
  }
}

const goPostDetail = (postId) => {
  openPostDetail(postId)
}

const formatCount = (num) => {
  if (num == null) return '0'
  if (num >= 10000) return (num / 10000).toFixed(1) + '万'
  return num.toString()
}
</script>

<template>
  <PageShell>
    <!-- 顶部搜索栏 -->
    <header class="sticky top-0 bg-white py-7 z-[5]">
      <SearchBar />
    </header>

    <!-- 加载中 -->
    <div v-if="loading" class="flex flex-col items-center py-20 text-gray-400">
      <span class="inline-block size-8 border-2 border-gray-300 border-t-primary rounded-full animate-spin mb-3"></span>
      加载中...
    </div>

    <template v-else-if="user">
      <!-- 个人资料区域 -->
      <section class="bg-white p-10 rounded-2xl mb-16">
        <!-- 头像和信息 -->
        <div class="flex justify-center">
          <div class="flex gap-10 mb-10 pb-5">
            <img :src="user.avatar || defaultAvatar" class="size-40 rounded-full object-cover shrink-0" />
            <div class="flex-1">
              <!-- 名字行 -->
              <div class="flex items-center gap-5 mb-2.5 w-[480px]">
                <span class="text-2xl font-bold">{{ user.nickname || user.username }}</span>
                <BaseButton
                    v-if="!isMe"
                    class="w-24 ml-auto"
                    :variant="isFollowed ? 'outline' : 'primary'"
                    size="sm"
                    @click="handleToggleFollow"
                >
                  {{ isFollowed ? '已关注' : '关注' }}
                </BaseButton>
                <BaseButton
                    v-else
                    class="w-24 ml-auto"
                    variant="outline"
                    size="sm"
                >
                  编辑资料
                </BaseButton>
              </div>
              <!-- ID -->
              <div class="text-xs text-gray-400 mb-2.5">小红书号：{{ user.id }}</div>
              <!-- 简介 -->
              <div class="text-sm text-gray-500 mb-5 leading-[1.6] whitespace-pre-line">{{ user.bio || '这个人很懒，还没有写简介～' }}</div>
              <!-- 统计数据 -->
              <div class="flex gap-5 text-sm text-gray-500 mb-5">
                <div><span class="font-bold text-gray-800 mr-1">{{ formatCount(user.followingCount) }}</span>关注</div>
                <div><span class="font-bold text-gray-800 mr-1">{{ formatCount(user.followersCount) }}</span>粉丝</div>
                <div><span class="font-bold text-gray-800 mr-1">{{ formatCount(user.likeAndCollectCount) }}</span>获赞与收藏</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 标签栏 -->
        <div class="flex justify-center gap-10 mb-[30px]">
          <div
            class="text-base cursor-pointer pb-2.5 transition-colors duration-200"
            :class="activeTab === 'notes' ? 'text-gray-800 font-bold border-b-2 border-gray-800' : 'text-gray-500'"
            @click="handleTabChange('notes')"
          >笔记</div>
          <div
            class="text-base cursor-pointer pb-2.5 transition-colors duration-200"
            :class="activeTab === 'collect' ? 'text-gray-800 font-bold border-b-2 border-gray-800' : 'text-gray-500'"
            @click="handleTabChange('collect')"
          >收藏</div>
        </div>

        <!-- 笔记网格 -->
        <PostGrid v-if="activeTab === 'notes'" :posts="notes" empty-text="暂无笔记" @open="goPostDetail" />

        <!-- 收藏网格 -->
        <PostGrid v-else :posts="collects" empty-text="暂无收藏" @open="goPostDetail" />
      </section>
    </template>

    <div v-else class="flex flex-col items-center py-20 text-gray-400">
      用户加载失败
    </div>
  </PageShell>
</template>
