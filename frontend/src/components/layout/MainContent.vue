<script setup>
import { ref } from 'vue'
import SearchBar from './SearchBar.vue'
import CategoryTabs from './CategoryTabs.vue'
import PostCard from '../post/PostCard.vue'

const emit = defineEmits(['openPost', 'openProfile'])

const activeCategory = ref('推荐')

const categories = ['推荐', '穿搭', '美食', '彩妆', '影视', '职场', '情感', '家居', '游戏', '旅行', '健身', '视频']

// 模拟数据
const posts = ref([
  {
    id: 1,
    title: '新手第一次游泳保姆级教程，学会这个夏天不怕水',
    coverImage: 'https://picsum.photos/400/500?random=1',
    author: { nickname: '南瓜', avatar: 'https://picsum.photos/50/50?random=10' },
    likeCount: 13000
  },
  {
    id: 2,
    title: '三月份的时候看上兆易，现在走势分析',
    coverImage: 'https://picsum.photos/400/300?random=2',
    author: { nickname: '大白兔奶糖', avatar: 'https://picsum.photos/50/50?random=11' },
    likeCount: 508
  },
  {
    id: 3,
    title: '熊猫母子的睡前氛围感，太治愈了',
    coverImage: 'https://picsum.photos/400/400?random=3',
    author: { nickname: '刘荣', avatar: 'https://picsum.photos/50/50?random=12' },
    likeCount: 3695
  },
  {
    id: 4,
    title: '大家都是谈了多久订婚结婚的啊？',
    coverImage: null,
    author: { nickname: '薯条', avatar: 'https://picsum.photos/50/50?random=13' },
    likeCount: 400,
    isTextCard: true
  },
  {
    id: 5,
    title: '合集3 | 2026拼豆图纸必吃榜！',
    coverImage: 'https://picsum.photos/400/600?random=4',
    author: { nickname: 'kt猫悄悄逆袭', avatar: 'https://picsum.photos/50/50?random=14' },
    likeCount: 15000
  },
  {
    id: 6,
    title: '哈尔滨双城区十字街附近美食推荐',
    coverImage: 'https://picsum.photos/400/450?random=5',
    author: { nickname: '吃货小王', avatar: 'https://picsum.photos/50/50?random=15' },
    likeCount: 892
  }
])

const handleCategoryChange = (category) => {
  activeCategory.value = category
}

const handlePostClick = (postId) => {
  emit('openPost', postId)
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
      <div class="[column-count:5] [column-gap:20px]">
        <PostCard
          v-for="post in posts"
          :key="post.id"
          :post="post"
          @click="handlePostClick(post.id)"
          @open-profile="emit('openProfile')"
        />
      </div>
    </section>
  </div>
</template>
