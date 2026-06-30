<script setup>
import { ref, computed, onMounted, inject } from 'vue';
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
} = usePostComments(userStore, post, postId);

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

const isSelf = computed(() => {
  return !!(
    post.value &&
    userStore.userInfo &&
    post.value.userId === userStore.userInfo.id
  );
});

const isModal = computed(() => props.displayMode === 'modal');

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

const handleClose = () => emit('close');
</script>

<template>
  <div
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
      :class="
        isModal
          ? 'w-250 max-w-full h-full bg-white rounded-2xl flex overflow-hidden shadow-[0_10px_30px_rgba(0,0,0,0.2)]'
          : 'w-full max-w-295 h-full bg-white flex overflow-hidden border border-gray-200 rounded-2xl'
      "
    >
      <!-- 左侧媒体 -->
      <PostMediaViewer
        v-model:current-index="currentMediaIndex"
        :media-items="mediaItems"
      />

      <!-- 右侧内容 -->
      <div class="flex-1 flex flex-col p-6 min-w-0">
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

          <div class="field-hint mb-2.5">共 {{ post.commentCount }} 条评论</div>

          <CommentList
            :post="post"
            :comments="comments"
            :replies-map="repliesMap"
            :show-replies-map="showRepliesMap"
            :replying-to="replyingTo"
            v-model:reply-text="replyText"
            @reply-click="handleReplyClick"
            @toggle-replies="toggleReplies"
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
