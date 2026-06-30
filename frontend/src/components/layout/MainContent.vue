<script setup>
import { ref, onMounted, watch, inject } from 'vue';
import { useRouter } from 'vue-router';
import SearchBar from './SearchBar.vue';
import CategoryTabs from './CategoryTabs.vue';
import PageShell from './PageShell.vue';
import WaterfallPostGrid from '../post/WaterfallPostGrid.vue';
import LoadingState from '@/components/common/LoadingState.vue';
import ErrorState from '@/components/common/ErrorState.vue';
import { getPosts } from '@/api/post';
import { usePostStore } from '@/stores/post';
import { useUiStore } from '@/stores/ui';
import { usePaginatedPosts } from '@/composables/usePaginatedPosts';

const router = useRouter();
const postStore = usePostStore();
const uiStore = useUiStore();
const openPostDetail = inject('openPostDetail');

const activeCategory = ref('推荐');

const categories = [
  '推荐',
  '穿搭',
  '美食',
  '彩妆',
  '影视',
  '职场',
  '情感',
  '家居',
  '游戏',
  '旅行',
  '健身',
  '视频',
];

const {
  items: posts,
  loading,
  loadingMore,
  hasMore,
  error,
  load: loadPosts,
} = usePaginatedPosts(getPosts, {
  onItemsLoaded: (items) => postStore.initPosts(items),
});

onMounted(() => loadPosts(true));

// 发布成功后自动刷新列表
watch(
  () => uiStore.publishVersion,
  () => {
    loadPosts(true);
  },
);

const handleCategoryChange = (category) => {
  activeCategory.value = category;
  // 本次只接通首页基础数据，分类筛选留到后续批次
};

// 点击卡片 → 打开详情弹窗（不触发路由组件切换，保留滚动位置）
const handlePostClick = (postId) => {
  openPostDetail(postId);
};

// 点击作者 → 跳转用户主页
const handleOpenProfile = (userId) => {
  if (!userId) return;
  router.push({ name: 'user-profile', params: { id: userId } });
};
</script>

<template>
  <PageShell>
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
      <div v-if="loading" class="py-20">
        <LoadingState />
      </div>

      <!-- 错误态 -->
      <ErrorState v-else-if="error" :message="error" @retry="loadPosts(true)" />

      <WaterfallPostGrid
        v-else
        :posts="posts"
        :loading-more="loadingMore"
        :has-more="hasMore"
        enable-load-more
        empty-text="还没有笔记，快来发布第一条吧～"
        @open="handlePostClick"
        @open-profile="handleOpenProfile"
        @load-more="loadPosts(false)"
      />
    </section>
  </PageShell>
</template>
