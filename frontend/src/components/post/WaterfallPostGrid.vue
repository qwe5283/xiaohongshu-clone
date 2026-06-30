<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue';
import PostCard from './PostCard.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import LoadingState from '@/components/common/LoadingState.vue';

const props = defineProps({
  posts: {
    type: Array,
    default: () => [],
  },
  emptyText: {
    type: String,
    default: '暂无笔记',
  },
  loadingMore: {
    type: Boolean,
    default: false,
  },
  hasMore: {
    type: Boolean,
    default: true,
  },
  enableLoadMore: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['open', 'openProfile', 'loadMore']);

const postColumns = ref([]);
const columnCount = ref(5);
const columnHeights = ref([]);
const loadMoreTrigger = ref(null);
let observer = null;

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
  assignPostsToColumns(props.posts);
};

const syncColumnCount = () => {
  const nextCount = getColumnCount();
  if (nextCount === columnCount.value && postColumns.value.length) return;

  columnCount.value = nextCount;
  rebuildColumns();
};

const setupLoadMoreObserver = async () => {
  observer?.disconnect();
  if (!props.enableLoadMore) return;

  await nextTick();

  observer = new IntersectionObserver(
    ([entry]) => {
      if (
        entry.isIntersecting &&
        props.hasMore &&
        !props.loadingMore &&
        props.posts.length > 0
      ) {
        emit('loadMore');
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

watch(
  () => props.posts,
  (nextPosts, oldPosts) => {
    const oldLength = oldPosts?.length ?? 0;
    const isAppend =
      oldPosts &&
      nextPosts.length > oldLength &&
      oldPosts.every((post, index) => post.id === nextPosts[index]?.id);

    if (isAppend) {
      assignPostsToColumns(nextPosts.slice(oldLength));
    } else {
      rebuildColumns();
    }

    setupLoadMoreObserver();
  },
  { deep: false },
);

watch(
  () => props.enableLoadMore,
  () => {
    setupLoadMoreObserver();
  },
);

onMounted(() => {
  syncColumnCount();
  setupLoadMoreObserver();
  window.addEventListener('resize', syncColumnCount);
});

onUnmounted(() => {
  observer?.disconnect();
  window.removeEventListener('resize', syncColumnCount);
});
</script>

<template>
  <div
    v-if="posts.length > 0"
    class="grid gap-5"
    :style="{ gridTemplateColumns: `repeat(${columnCount}, minmax(0, 1fr))` }"
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
        @click="emit('open', post.id)"
        @open-profile="emit('openProfile', $event)"
      />
    </div>
  </div>

  <EmptyState v-else :text="emptyText" />

  <div v-if="enableLoadMore" ref="loadMoreTrigger" class="h-10"></div>

  <div v-if="loadingMore" class="py-4">
    <LoadingState text="加载更多..." size="sm" />
  </div>

  <div
    v-else-if="enableLoadMore && posts.length > 0 && !hasMore"
    class="text-center text-gray-300 py-4"
  >
    没有更多了
  </div>
</template>
