<script setup>
import { computed } from 'vue';
import heartIcon from '../../assets/icons/heart.svg?raw';
import heartFilledIcon from '../../assets/icons/heart-filled.svg?raw';
import { toggleLikePost } from '@/api/like';
import { useUserStore } from '@/stores/user';
import { usePostStore } from '@/stores/post';
import { showToast } from '@/utils/toast';

const props = defineProps({
  post: {
    type: Object,
    required: true,
  },
});

const emit = defineEmits(['openProfile']);
const userStore = useUserStore();
const postStore = usePostStore();

// 优先从 store 读取最新状态（详情弹窗修改后会同步），兜底用 prop 初始值
const liked = computed(
  () => postStore.getPostState(props.post.id)?.liked ?? props.post.liked,
);
const likeCount = computed(
  () =>
    postStore.getPostState(props.post.id)?.likeCount ?? props.post.likeCount,
);

const currentHeartIcon = computed(() =>
  liked.value ? heartFilledIcon : heartIcon,
);

const isVideo = computed(() => props.post.type === 1 || !!props.post.videoUrl);

const formatLikeCount = (count) => {
  if (count >= 10000) {
    return (count / 10000).toFixed(1) + '万';
  }
  return count.toString();
};

// 点赞 toggle（乐观更新 + 同步 store）
const handleToggleLike = async () => {
  if (!userStore.isLoggedIn) {
    showToast('请先登录', 'error');
    return;
  }
  const wasLiked = liked.value;
  const newLiked = !wasLiked;
  const newCount = likeCount.value + (wasLiked ? -1 : 1);
  postStore.updateLike(props.post.id, newLiked, newCount);
  try {
    await toggleLikePost(props.post.id);
  } catch (e) {
    // 失败回滚
    postStore.updateLike(props.post.id, wasLiked, likeCount.value);
  }
};
</script>

<template>
  <div
    class="bg-white rounded-xl overflow-hidden mb-5 break-inside-avoid cursor-pointer"
    :class="{ 'bg-[#fffbe6]': post.isTextCard }"
  >
    <!-- 图片卡片 -->
    <div
      v-if="!post.isTextCard && post.coverImage"
      class="m-1 relative overflow-hidden rounded-xl shadow-[0_0_1px_rgba(0,0,0,0.6)] group"
    >
      <img
        :src="post.coverImage"
        class="w-full block object-cover transition-transform duration-300 group-hover:scale-105"
        alt="cover"
      />
      <!-- 悬浮渐变遮罩 -->
      <div
        class="absolute inset-0 bg-linear-to-b from-black/5 to-black/30 opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none"
      ></div>
      <!-- 视频播放图标 -->
      <div
        v-if="isVideo"
        class="absolute top-2 right-2 bg-black/50 text-white text-xs rounded px-1.5 py-0.5 flex items-center gap-1"
      >
        <span>▶</span> 视频
      </div>
    </div>

    <!-- 纯文字卡片 -->
    <div v-if="post.isTextCard" class="p-[40px_20px] text-center">
      <div class="text-lg font-medium mb-5">{{ post.title }}</div>
      <div class="text-[40px]">😂</div>
    </div>

    <!-- 普通卡片内容 -->
    <div v-if="!post.isTextCard" class="p-3">
      <div class="text-sm font-medium leading-[1.4] mb-3 line-clamp-2">
        {{ post.title }}
      </div>
    </div>

    <!-- 底部信息 -->
    <div
      class="flex justify-between items-center text-xs text-gray-500 px-3 pb-3"
    >
      <div
        class="flex items-center gap-1.5 cursor-pointer hover:underline"
        @click.stop="emit('openProfile', post.author?.id)"
      >
        <img
          :src="post.author.avatar"
          class="size-5 rounded-full object-cover"
        />
        <span>{{ post.author.nickname }}</span>
      </div>
      <div class="flex items-center gap-1 cursor-pointer" @click.stop="handleToggleLike">
        <span
          class="size-4 [&>svg]:size-4 transition-colors"
          :class="liked ? 'text-red-500' : 'text-gray-500'"
          v-html="currentHeartIcon"
        ></span>
        <span :class="liked ? 'text-red-500' : 'text-gray-500'">{{
          formatLikeCount(likeCount)
        }}</span>
      </div>
    </div>
  </div>
</template>
