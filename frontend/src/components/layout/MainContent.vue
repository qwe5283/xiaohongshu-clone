<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import SearchBar from './SearchBar.vue'
import CategoryTabs from './CategoryTabs.vue'
import PostCard from '../post/PostCard.vue'
import { getPosts, adaptPost } from '@/api/post'

const router = useRouter()

const activeCategory = ref('推荐')

const categories = ['推荐', '穿搭', '美食', '彩妆', '影视', '职场', '情感', '家居', '游戏', '旅行', '健身', '视频']

// 列表数据状态
const posts = ref([])
const loading = ref(false)
const error = ref('')

// 加载首页笔记列表（真实接口）
const loadPosts = async () => {
  loading.value = true
  error.value = ''
  try {
    const page = await getPosts({ pageNum: 1, pageSize: 20 })
    // 后端返回 MyBatis-Plus 的 IPage，数组在 records 字段
    const list = page?.records || []
    posts.value = list.map(adaptPost)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadPosts)

const handleCategoryChange = (category) => {
  activeCategory.value = category
  // 本次只接通首页基础数据，分类筛选留到后续批次
}

// 点击卡片 → 路由跳转详情（弹窗由 App.vue 监听路由渲染）
const handlePostClick = (postId) => {
  router.push({ name: 'post-detail', params: { id: postId } })
}
</script>

<template>
  <div class="ml-[164px] flex-1 px-10 max-w-[1600px] bg-white">
    <!-- 顶部搜索栏 -->
    <header class="sticky top-0 bg-white py-7 z-[5]">
      <SearchBar />
    </header>

    <!-- 分类标签 -->
    <CategoryTabs
      :categories="categories"
      :active="activeCategory"
      @change="handleCategoryChange"
    />

    <!-- 瀑布流笔记列表 -->
    <section class="mb-16">
      <!-- 加载中骨架 -->
      <div v-if="loading" class="flex justify-center items-center py-20 text-gray-400">
        <span class="inline-block size-5 border-2 border-gray-300 border-t-primary rounded-full animate-spin mr-2"></span>
        加载中...
      </div>

      <!-- 错误态 -->
      <div v-else-if="error" class="flex flex-col items-center py-20 text-gray-400">
        <div class="mb-3">{{ error }}</div>
        <button class="bg-primary text-white px-5 py-2 rounded-full text-sm cursor-pointer" @click="loadPosts">重试</button>
      </div>

      <!-- 空态 -->
      <div v-else-if="posts.length === 0" class="flex justify-center items-center py-20 text-gray-400">
        还没有笔记，快来发布第一条吧～
      </div>

      <!-- 瀑布流 -->
      <div v-else class="[column-count:5] [column-gap:20px]">
        <PostCard
          v-for="post in posts"
          :key="post.id"
          :post="post"
          @click="handlePostClick(post.id)"
        />
      </div>
    </section>
  </div>
</template>
