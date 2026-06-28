<script setup>
import { ref, onMounted, onUnmounted, watch, inject, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import SearchBar from './SearchBar.vue';
import CategoryTabs from './CategoryTabs.vue';
import PageShell from './PageShell.vue';
import PostCard from '../post/PostCard.vue';
import { getPosts, adaptPost } from '@/api/post';
import { usePostStore } from '@/stores/post';

const router = useRouter();
const postStore = usePostStore();
const openPostDetail = inject('openPostDetail');
const publishVersion = inject('publishVersion', 0);

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

// 列表数据状态
const posts = ref([]);
const postColumns = ref([]);
const loading = ref(false);
const loadingMore = ref(false);
const pageNum = ref(1);
const hasMore = ref(true);
const error = ref('');
const loadMoreTrigger = ref(null);
const columnCount = ref(5);
const columnHeights = ref([]);
let observer = null;

const PAGE_SIZE = 20;

const getColumnCount = () => {
  const width = window.innerWidth;
  if (width >= 1536) return 5;
  if (width >= 1280) return 4;
  if (width >= 768) return 3;
  return 2;
};

const createEmptyColumns = (count) => Array.from({ length: count }, () => []);

const estimatePostHeight = (post) => {
  if (post.isTextCard) return 190;
  const titleRows = post.title?.length > 16 ? 2 : 1;
  const imageHeight = post.type === 1 ? 300 : 260;
  return imageHeight + titleRows * 20 + 48;
};

const assignPostsToColumns = (list) => {
  if (!list.length) return;

  const columns = postColumns.value.map((column) => [...column]);
  const heights = [...columnHeights.value];

  for (const post of list) {
    const targetIndex = heights.indexOf(Math.min(...heights));
    columns[targetIndex].push(post);
    heights[targetIndex] += estimatePostHeight(post);
  }

  postColumns.value = columns;
  columnHeights.value = heights;
};

const rebuildColumns = () => {
  postColumns.value = createEmptyColumns(columnCount.value);
  columnHeights.value = Array.from({ length: columnCount.value }, () => 0);
  assignPostsToColumns(posts.value);
};

const syncColumnCount = () => {
  const nextCount = getColumnCount();
  if (nextCount === columnCount.value && postColumns.value.length) return;

  columnCount.value = nextCount;
  rebuildColumns();
};

// 加载首页笔记列表（真实接口）
const loadPosts = async (reset = false) => {
  if (loading.value || loadingMore.value || (!reset && !hasMore.value)) return;

  if (reset) {
    loading.value = true;
    pageNum.value = 1;
    hasMore.value = true;
    posts.value = [];
    rebuildColumns();
  } else {
    loadingMore.value = true;
  }
  error.value = '';

  try {
    const page = await getPosts({
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
    });
    // 后端返回 MyBatis-Plus 的 IPage，数组在 records 字段
    const list = page?.records || [];
    const adapted = list.map(adaptPost);
    posts.value = reset ? adapted : [...posts.value, ...adapted];
    if (reset) {
      rebuildColumns();
    } else {
      assignPostsToColumns(adapted);
    }
    // 将后端返回的 liked/likeCount 等状态同步到全局 store，供详情弹窗跨组件同步
    postStore.initPosts(adapted);

    const current = Number(page?.current ?? pageNum.value);
    const pages = Number(page?.pages ?? 0);
    hasMore.value = pages > 0 ? current < pages : adapted.length === PAGE_SIZE;
    pageNum.value = current + 1;
  } catch (e) {
    error.value = e.message || '加载失败';
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
};

const setupLoadMoreObserver = () => {
  observer?.disconnect();
  observer = new IntersectionObserver(
    ([entry]) => {
      if (entry.isIntersecting) {
        loadPosts();
      }
    },
    {
      rootMargin: '600px 0px',
    },
  );

  if (loadMoreTrigger.value) {
    observer.observe(loadMoreTrigger.value);
  }
};

onMounted(async () => {
  syncColumnCount();
  await loadPosts(true);
  await nextTick();
  setupLoadMoreObserver();
  window.addEventListener('resize', syncColumnCount);
});

onUnmounted(() => {
  observer?.disconnect();
  window.removeEventListener('resize', syncColumnCount);
});

// 发布成功后自动刷新列表
watch(publishVersion, () => {
  loadPosts(true);
});

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
      <div
        v-if="loading"
        class="flex justify-center items-center py-20 text-gray-400"
      >
        <span
          class="inline-block size-5 border-2 border-gray-300 border-t-primary rounded-full animate-spin mr-2"
        ></span>
        加载中...
      </div>

      <!-- 错误态 -->
      <div
        v-else-if="error"
        class="flex flex-col items-center py-20 text-gray-400"
      >
        <div class="mb-3">{{ error }}</div>
        <button
          class="bg-primary text-white px-5 py-2 rounded-full text-sm cursor-pointer"
          @click="loadPosts(true)"
        >
          重试
        </button>
      </div>

      <!-- 空态 -->
      <div
        v-else-if="posts.length === 0"
        class="flex justify-center items-center py-20 text-gray-400"
      >
        还没有笔记，快来发布第一条吧～
      </div>

      <!-- 瀑布流 -->
      <div
        v-else
        class="grid gap-5"
        :style="{
          gridTemplateColumns: `repeat(${columnCount}, minmax(0, 1fr))`,
        }"
      >
        <div
          v-for="(columnPosts, columnIndex) in postColumns"
          :key="columnIndex"
          class="min-w-0"
        >
          <PostCard
            v-for="post in columnPosts"
            :key="post.id"
            :post="post"
            @click="handlePostClick(post.id)"
            @open-profile="handleOpenProfile"
          />
        </div>
      </div>

      <div ref="loadMoreTrigger" class="h-10"></div>

      <div
        v-if="loadingMore"
        class="flex justify-center items-center py-4 text-gray-400"
      >
        <span
          class="inline-block size-4 border-2 border-gray-300 border-t-primary rounded-full animate-spin mr-2"
        ></span>
        加载更多...
      </div>

      <div
        v-else-if="posts.length > 0 && !hasMore"
        class="text-center text-gray-300 py-4"
      >
        没有更多了
      </div>
    </section>
  </PageShell>
</template>
