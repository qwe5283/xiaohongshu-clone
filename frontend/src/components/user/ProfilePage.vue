<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import heartIcon from '../../assets/icons/heart.svg?raw'
import { getUserById } from '@/api/auth'
import { getUserPosts } from '@/api/post'
import { getCollectedPosts } from '@/api/collect'
import { getFollowCount, getFollowStatus, toggleFollow } from '@/api/follow'
import { useUserStore } from '@/stores/user'
import { showToast } from '@/utils/toast'
import { adaptPost } from '@/api/post'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

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

function isTextCard(post) {
  return !post.coverImage && post.type === 0
}

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
  router.push({ name: 'post-detail', params: { id: postId } })
}

const formatCount = (num) => {
  if (num == null) return '0'
  if (num >= 10000) return (num / 10000).toFixed(1) + '万'
  return num.toString()
}
</script>

<template>
  <div class="ml-[164px] flex-1 px-10 max-w-[1600px] bg-white min-h-screen">
    <!-- 顶部搜索栏 -->
    <header class="sticky top-0 bg-white py-7 z-[5]">
      <div class="bg-white border border-[#d9d9d9] rounded-[28px] px-[18px] py-3 flex items-center text-gray-400 max-w-[900px] mx-auto shadow-[0_4px_12px_rgba(0,0,0,0.06)]">
        <input type="text" placeholder="登录探索更多内容" class="border-none outline-none flex-1 text-base bg-transparent placeholder:text-gray-300" />
        <div class="size-8 bg-gray-800 rounded-full flex items-center justify-center cursor-pointer">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="size-5 fill-white"><path d="M11.5 3a8.5 8.5 0 0 1 6.613 13.84l3.023 3.024a.9.9 0 1 1-1.272 1.272l-3.024-3.023A8.5 8.5 0 1 1 11.5 3m0 15.2a6.7 6.7 0 1 0 0-13.4 6.7 6.7 0 0 0 0 13.4"/></svg>
        </div>
      </div>
    </header>

    <!-- 加载中 -->
    <div v-if="loading" class="flex flex-col items-center py-20 text-gray-400">
      <span class="inline-block size-8 border-2 border-gray-300 border-t-primary rounded-full animate-spin mb-3"></span>
      加载中...
    </div>

    <template v-else-if="user">
      <!-- 个人资料区域 -->
      <section class="bg-white p-10 rounded-2xl mb-16 border border-gray-200">
        <!-- 头像和信息 -->
        <div class="flex gap-10 mb-10 pb-5 border-b border-gray-200">
          <img :src="user.avatar || defaultAvatar" class="size-[120px] rounded-full object-cover shrink-0" />
          <div class="flex-1">
            <!-- 名字行 -->
            <div class="flex items-center gap-5 mb-2.5">
              <span class="text-2xl font-bold">{{ user.nickname || user.username }}</span>
              <button
                v-if="!isMe"
                class="border border-primary w-24 px-4 py-2 rounded-[20px] text-sm font-bold cursor-pointer transition-all duration-200"
                :class="isFollowed ? 'bg-white text-primary' : 'bg-primary text-white'"
                @click="handleToggleFollow"
              >
                {{ isFollowed ? '已关注' : '关注' }}
              </button>
              <button
                v-else
                class="border border-primary w-24 px-4 py-2 rounded-[20px] text-sm font-bold bg-white text-primary cursor-pointer transition-all duration-200"
              >
                编辑资料
              </button>
            </div>
            <!-- ID -->
            <div class="text-xs text-gray-400 mb-2.5">小红书号：{{ user.id }}</div>
            <!-- 简介 -->
            <div class="text-sm text-gray-500 mb-5 leading-[1.6] whitespace-pre-line">{{ user.bio || '这个人很懒，还没有写简介～' }}</div>
            <!-- 统计数据 -->
            <div class="flex gap-[30px] text-sm">
              <div><span class="font-bold text-gray-800 mr-1">{{ formatCount(user.followingCount) }}</span>关注</div>
              <div><span class="font-bold text-gray-800 mr-1">{{ formatCount(user.followersCount) }}</span>粉丝</div>
              <div><span class="font-bold text-gray-800 mr-1">{{ formatCount(user.likeAndCollectCount) }}</span>获赞与收藏</div>
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
        <div v-if="activeTab === 'notes'" class="grid grid-cols-5 gap-5">
          <div
            v-for="note in notes"
            :key="note.id"
            class="bg-white rounded-xl overflow-hidden cursor-pointer hover:shadow-md transition-shadow"
            :class="{ 'flex items-center justify-center h-[200px] border border-gray-100': isTextCard(note) }"
            @click="goPostDetail(note.id)"
          >
            <template v-if="isTextCard(note)">
              <div class="text-center font-bold text-lg whitespace-pre-line p-4">{{ note.title }}</div>
            </template>
            <template v-else>
              <div class="m-1 relative overflow-hidden rounded-xl shadow-[0_0_1px_rgba(0,0,0,0.6)] group">
                <img :src="note.coverImage" class="w-full block object-cover transition-transform duration-300 group-hover:scale-105" />
                <div class="absolute inset-0 bg-gradient-to-b from-black/5 to-black/25 opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none"></div>
              </div>
              <div class="p-3">
                <div class="text-sm font-medium leading-[1.4] line-clamp-2">{{ note.title }}</div>
                <div v-if="note.likeCount" class="flex justify-end items-center text-xs text-gray-500 mt-2">
                  <span class="size-4 [&>svg]:size-4 mr-1" v-html="heartIcon"></span>
                  {{ formatCount(note.likeCount) }}
                </div>
              </div>
            </template>
          </div>
          <div v-if="notes.length === 0" class="col-span-5 text-center text-gray-400 py-20">暂无笔记</div>
        </div>

        <!-- 收藏网格 -->
        <div v-else class="grid grid-cols-5 gap-5">
          <div
            v-for="note in collects"
            :key="note.id"
            class="bg-white rounded-xl overflow-hidden cursor-pointer hover:shadow-md transition-shadow"
            :class="{ 'flex items-center justify-center h-[200px] border border-gray-100': isTextCard(note) }"
            @click="goPostDetail(note.id)"
          >
            <template v-if="isTextCard(note)">
              <div class="text-center font-bold text-lg whitespace-pre-line p-4">{{ note.title }}</div>
            </template>
            <template v-else>
              <div class="m-1 relative overflow-hidden rounded-xl shadow-[0_0_1px_rgba(0,0,0,0.6)] group">
                <img :src="note.coverImage" class="w-full block object-cover transition-transform duration-300 group-hover:scale-105" />
                <div class="absolute inset-0 bg-gradient-to-b from-black/5 to-black/25 opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none"></div>
              </div>
              <div class="p-3">
                <div class="text-sm font-medium leading-[1.4] line-clamp-2">{{ note.title }}</div>
                <div v-if="note.likeCount" class="flex justify-end items-center text-xs text-gray-500 mt-2">
                  <span class="size-4 [&>svg]:size-4 mr-1" v-html="heartIcon"></span>
                  {{ formatCount(note.likeCount) }}
                </div>
              </div>
            </template>
          </div>
          <div v-if="collects.length === 0" class="col-span-5 text-center text-gray-400 py-20">暂无收藏</div>
        </div>
      </section>
    </template>

    <div v-else class="flex flex-col items-center py-20 text-gray-400">
      用户加载失败
    </div>
  </div>
</template>
