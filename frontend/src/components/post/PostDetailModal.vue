<script setup>
import { ref, computed, onMounted, onUnmounted, watch, inject } from 'vue';
import { useRouter } from 'vue-router';
import closeIcon from '../../assets/icons/close.svg?raw';
import { getPostDetail } from '@/api/post';
import { getComments } from '@/api/comment';
import { getLikeStatusPost } from '@/api/like';
import { getCollectStatusPost } from '@/api/collect';
import { useUserStore } from '@/stores/user';
import { usePostStore } from '@/stores/post';
import { formatRelativeTime } from '@/utils/format';
import { usePostActions } from '@/composables/usePostActions';
import { useFollowToggle } from '@/composables/useFollowToggle';
import { usePostComments } from '@/composables/usePostComments';
import LoadingState from '@/components/common/LoadingState.vue';
import PostMediaViewer from './PostMediaViewer.vue';
import PostAuthorBar from './PostAuthorBar.vue';
import PostActionBar from './PostActionBar.vue';
import CommentList from './CommentList.vue';

const props = defineProps({
  postId: { type: [Number, String], required: true },
  displayMode: {
    type: String,
    default: 'modal',
    validator: (value) => ['modal', 'page'].includes(value),
  },
});
const emit = defineEmits(['close']);

const router = useRouter();
const userStore = useUserStore();
const postStore = usePostStore();
const { toggleLike, toggleCollect } = usePostActions(userStore, postStore);
const closePostDetailSilent = inject('closePostDetailSilent');

const post = ref(null);
const loading = ref(true);
const isLiked = ref(false);
const isCollected = ref(false);
const currentMediaIndex = ref(0);
const detailShellRef = ref(null);
const availableDetailSize = ref({ width: 0, height: 0 });
const coverAspectRatio = ref(null);
const postId = computed(() => props.postId);
const { isFollowed, loadFollowStatus, toggleFollow } =
  useFollowToggle(userStore);
const {
  comments,
  repliesMap,
  showRepliesMap,
  commentText,
  replyingTo,
  replyText,
  setComments,
  submitComment,
  handleReplyClick,
  submitReply,
  toggleReplies,
  toggleCommentLike,
} = usePostComments(userStore, post, postId);

const CONTENT_PANEL_WIDTH = 424;
const DETAIL_PANEL_MIN_WIDTH = 900;
const DETAIL_PANEL_MAX_WIDTH = 1200;
const DEFAULT_COVER_ASPECT_RATIO = 3 / 4;

// 统一媒体列表：视频在前，图片在后
const mediaItems = computed(() => {
  if (!post.value) return [];
  const items = [];
  if (post.value.videoUrl) {
    items.push({ type: 'video', url: post.value.videoUrl });
  }
  if (post.value.images && post.value.images.length) {
    items.push(
      ...post.value.images.map((img) => ({ type: 'image', url: img.imageUrl })),
    );
  } else if (post.value.coverImage && !post.value.videoUrl) {
    items.push({ type: 'image', url: post.value.coverImage });
  }
  return items;
});

const coverImageUrl = computed(() => {
  if (!post.value) return '';
  return post.value.coverImage || '';
});

const normalizedCoverAspectRatio = computed(() => {
  const ratio = coverAspectRatio.value;
  return Number.isFinite(ratio) && ratio > 0
    ? ratio
    : DEFAULT_COVER_ASPECT_RATIO;
});

const detailPanelWidth = computed(() => {
  const panelHeight = availableDetailSize.value.height || 720;
  const availableWidth = availableDetailSize.value.width || 1000;
  const desiredWidth = panelHeight * normalizedCoverAspectRatio.value;
  const targetWidth = CONTENT_PANEL_WIDTH + desiredWidth;
  const upperBound = Math.min(DETAIL_PANEL_MAX_WIDTH, availableWidth);
  const lowerBound = Math.min(DETAIL_PANEL_MIN_WIDTH, upperBound);
  const width = Math.min(upperBound, Math.max(lowerBound, targetWidth));

  return Math.round(width);
});

const detailPanelStyle = computed(() => ({
  '--detail-panel-width': `${detailPanelWidth.value}px`,
  '--detail-content-width': `${CONTENT_PANEL_WIDTH}px`,
}));

const isSelf = computed(() => {
  return !!(
    post.value &&
    userStore.userInfo &&
    post.value.userId === userStore.userInfo.id
  );
});

const isModal = computed(() => props.displayMode === 'modal');

let detailPanelResizeObserver = null;
let coverImageLoadToken = 0;

const updateAvailableDetailSize = (entry) => {
  const rect =
    entry?.contentRect || detailShellRef.value?.getBoundingClientRect();
  if (!rect) return;

  availableDetailSize.value = {
    width: rect.width,
    height: rect.height,
  };
};

const observeDetailPanel = (element) => {
  detailPanelResizeObserver?.disconnect();
  detailPanelResizeObserver = null;

  if (!element) return;

  updateAvailableDetailSize();

  if (typeof ResizeObserver === 'undefined') return;

  detailPanelResizeObserver = new ResizeObserver(([entry]) => {
    updateAvailableDetailSize(entry);
  });
  detailPanelResizeObserver.observe(element);
};

const loadCoverAspectRatio = (url) => {
  const token = ++coverImageLoadToken;
  coverAspectRatio.value = null;

  if (!url || typeof Image === 'undefined') return;

  const image = new Image();
  image.onload = () => {
    if (token !== coverImageLoadToken) return;
    if (!image.naturalWidth || !image.naturalHeight) return;

    coverAspectRatio.value = image.naturalWidth / image.naturalHeight;
  };
  image.onerror = () => {
    if (token === coverImageLoadToken) {
      coverAspectRatio.value = null;
    }
  };
  image.src = url;
};

const loadDetail = async () => {
  loading.value = true;
  try {
    const [detail, commentPage] = await Promise.all([
      getPostDetail(props.postId),
      getComments(props.postId, { pageNum: 1, pageSize: 20 }),
    ]);
    post.value = detail;
    setComments(commentPage?.records || []);

    if (userStore.isLoggedIn && detail) {
      const [likeRes, collectRes] = await Promise.all([
        getLikeStatusPost(props.postId).catch(() => ({ liked: false })),
        getCollectStatusPost(props.postId).catch(() => ({ collected: false })),
      ]);
      isLiked.value = !!likeRes.liked;
      isCollected.value = !!collectRes.collected;
      if (!isSelf.value) loadFollowStatus(detail.userId);
    }
  } catch (e) {
    // request 拦截器已处理错误
  } finally {
    loading.value = false;
  }
};

onMounted(loadDetail);
onUnmounted(() => detailPanelResizeObserver?.disconnect());

watch(detailShellRef, observeDetailPanel, { flush: 'post' });
watch(coverImageUrl, loadCoverAspectRatio, { immediate: true });

const handleToggleLike = async () => {
  await toggleLike({
    postId: props.postId,
    liked: () => isLiked.value,
    likeCount: () => post.value.likeCount,
    setLocalState: ({ liked, likeCount }) => {
      isLiked.value = liked;
      post.value.likeCount = likeCount;
    },
  });
};

const handleToggleCollect = async () => {
  await toggleCollect({
    postId: props.postId,
    collected: () => isCollected.value,
    collectCount: () => post.value.collectCount,
    setLocalState: ({ collected, collectCount }) => {
      isCollected.value = collected;
      post.value.collectCount = collectCount;
    },
  });
};

const handleToggleFollow = async () => {
  if (isSelf.value || !post.value) return;
  await toggleFollow(post.value.userId);
};

const goAuthorProfile = () => {
  if (!post.value?.userId) return;
  // 静默关闭弹窗，再由 router.push 接管 URL 变更
  closePostDetailSilent?.();
  router.push({ name: 'user-profile', params: { id: post.value.userId } });
};

const goUserProfile = (userId) => {
  if (!userId) return;
  closePostDetailSilent?.();
  router.push({ name: 'user-profile', params: { id: userId } });
};

const handleClose = () => emit('close');
</script>

<template>
  <div
    ref="detailShellRef"
    :class="
      isModal
        ? 'fixed inset-0 bg-black/30 z-[100] p-10 flex justify-center'
        : 'h-full bg-white flex justify-center p-10'
    "
    @click.self="handleClose"
  >
    <!-- 关闭按钮 -->
    <button
      v-if="isModal"
      class="absolute top-5 left-5 text-white text-2xl cursor-pointer z-[101] bg-black/50 size-8 rounded-full flex items-center justify-center"
      @click="handleClose"
    >
      <span class="[&>svg]:size-4 text-white" v-html="closeIcon"></span>
    </button>

    <!-- 主容器 -->
    <div
      v-if="loading"
      :class="
        isModal
          ? 'w-250 max-w-full h-full bg-white rounded-2xl flex items-center justify-center'
          : 'w-full max-w-295 h-full bg-white flex items-center justify-center'
      "
    >
      <LoadingState size="lg" />
    </div>

    <div
      v-else-if="!post"
      :class="
        isModal
          ? 'w-250 max-w-full h-full bg-white rounded-2xl flex items-center justify-center text-text-muted'
          : 'w-full max-w-295 h-full bg-white flex items-center justify-center text-text-muted'
      "
    >
      笔记加载失败
    </div>

    <div
      v-else
      :style="detailPanelStyle"
      :class="
        isModal
          ? 'post-detail-panel h-full bg-white rounded-2xl flex overflow-hidden shadow-[0_10px_30px_rgba(0,0,0,0.2)]'
          : 'post-detail-panel h-full bg-white flex overflow-hidden border border-gray-200 rounded-2xl'
      "
    >
      <!-- 左侧媒体 -->
      <PostMediaViewer
        v-model:current-index="currentMediaIndex"
        :media-items="mediaItems"
        class="post-detail-media"
      />

      <!-- 右侧内容 -->
      <div class="post-detail-content flex flex-col p-6">
        <PostAuthorBar
          :post="post"
          :is-self="isSelf"
          :is-followed="isFollowed"
          @open-profile="goAuthorProfile"
          @toggle-follow="handleToggleFollow"
        />

        <!-- 内容区域 -->
        <div class="flex-1 overflow-y-auto min-h-0">
          <div class="text-lg font-bold mb-3">{{ post.title }}</div>
          <div
            class="text-sm leading-[1.6] text-gray-800 mb-3 whitespace-pre-wrap"
          >
            {{ post.content }}
          </div>
          <div class="field-hint mb-5">
            编辑于 {{ formatRelativeTime(post.createTime) }}
          </div>

          <div class="field-hint mb-2.5 border-t border-gray-200 pt-4">共 {{ post.commentCount }} 条评论</div>

          <CommentList
            :post="post"
            :comments="comments"
            :replies-map="repliesMap"
            :show-replies-map="showRepliesMap"
            :replying-to="replyingTo"
            v-model:reply-text="replyText"
            @reply-click="handleReplyClick"
            @toggle-replies="toggleReplies"
            @toggle-comment-like="toggleCommentLike"
            @open-profile="goUserProfile"
            @submit-reply="submitReply"
          />
        </div>

        <PostActionBar
          :post="post"
          :liked="isLiked"
          :collected="isCollected"
          v-model:comment-text="commentText"
          @submit-comment="submitComment"
          @toggle-like="handleToggleLike"
          @toggle-collect="handleToggleCollect"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.post-detail-panel {
  width: var(--detail-panel-width);
  max-width: 100%;
  transition: width 0.18s ease;
}

.post-detail-media {
  flex: 1 1 auto;
  min-width: 0;
}

.post-detail-content {
  flex: 0 0 var(--detail-content-width);
  width: var(--detail-content-width);
  min-width: var(--detail-content-width);
}
</style>
