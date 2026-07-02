<script setup>
import { computed, inject, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import PageShell from '@/components/layout/PageShell.vue';
import SearchBar from '@/components/layout/SearchBar.vue';
import CommentNotificationItem from './CommentNotificationItem.vue';
import LikeNotificationItem from './LikeNotificationItem.vue';
import FollowNotificationItem from './FollowNotificationItem.vue';
import { getNotifications, markNotificationAsRead } from '@/api/notification';
import { createComment } from '@/api/comment';
import { getFollowStatus, toggleFollow } from '@/api/follow';
import { getLikeStatusComment, toggleLikeComment } from '@/api/like';
import { useUserStore } from '@/stores/user';
import { useNotificationStore } from '@/stores/notification';
import { showToast } from '@/utils/toast';
import {
  defaultAvatar,
  defaultThumbnail,
  formatRelativeTime,
} from '@/utils/format';

const activeTab = ref('comments');
const loading = ref(false);
const messages = ref({
  comments: [],
  likes: [],
  follows: [],
});
const userStore = useUserStore();
const notificationStore = useNotificationStore();
const router = useRouter();
const openPostDetail = inject('openPostDetail', null);

const tabTypes = {
  comments: [3, 4],
  likes: [1, 2, 5],
  follows: [6],
};

const currentMessages = computed(() => messages.value[activeTab.value] || []);
const isLoggedIn = computed(() => userStore.isLoggedIn);
const unreadCount = computed(() => notificationStore.unreadCount);

const adaptNotification = (item) => {
  const common = {
    id: item.id,
    raw: item,
    read: item.read,
    senderId: item.senderId,
    postId: item.postId,
    commentId: item.commentId,
    avatar: item.senderAvatar || defaultAvatar,
    username: item.senderNickname || '小红薯用户',
    action: item.typeText || '给你发来一条通知',
    time: formatRelativeTime(item.createTime),
    thumbnail: item.postCoverImage || defaultThumbnail,
  };

  if (item.type === 3 || item.type === 4) {
    return {
      ...common,
      content: item.content,
      hasReply: true,
      commentLiked: false,
    };
  }

  if (item.type === 5) {
    return {
      ...common,
      contentPreview: item.content,
    };
  }

  if (item.type === 6) {
    return {
      ...common,
      thumbnail: '',
      status: 'back',
    };
  }

  return common;
};

const fetchMessages = async (tab = activeTab.value) => {
  if (!isLoggedIn.value) {
    messages.value[tab] = [];
    return;
  }
  loading.value = true;
  try {
    const pages = await Promise.all(
      tabTypes[tab].map((type) =>
        getNotifications({ type, pageNum: 1, pageSize: 50 }),
      ),
    );
    const records = pages
      .flatMap((page) => page?.records || [])
      .sort(
        (a, b) =>
          new Date(b.createTime).getTime() - new Date(a.createTime).getTime(),
      )
      .map(adaptNotification);
    await hydrateMessages(tab, records);
    messages.value[tab] = records;
  } finally {
    loading.value = false;
  }
};

const hydrateMessages = async (tab, records) => {
  if (tab === 'follows') {
    await Promise.all(
      records.map(async (item) => {
        if (!item.senderId) return;
        try {
          const res = await getFollowStatus(item.senderId);
          item.status = res?.followed ? 'following' : 'back';
        } catch (e) {
          item.status = 'back';
        }
      }),
    );
  }

  if (tab === 'comments') {
    await Promise.all(
      records
        .filter((item) => item.commentId)
        .map(async (item) => {
          try {
            const res = await getLikeStatusComment(item.commentId);
            item.commentLiked = !!res?.liked;
          } catch (e) {
            item.commentLiked = false;
          }
        }),
    );
  }
};

const markMessageRead = async (message) => {
  if (!message || message.read) return;
  message.read = true;
  try {
    await markNotificationAsRead(message.id);
  } catch (e) {
    // 已读失败不阻断当前操作，稍后刷新会校正未读数
  } finally {
    await notificationStore.fetchUnreadCount();
  }
};

const openPostFromMessage = async (message) => {
  if (!message?.postId) return;
  await markMessageRead(message);
  if (openPostDetail) {
    openPostDetail(message.postId);
  } else {
    router.push({ name: 'post-page', params: { id: message.postId } });
  }
};

const openProfileFromMessage = async (message) => {
  if (!message?.senderId) return;
  await markMessageRead(message);
  router.push({ name: 'user-profile', params: { id: message.senderId } });
};

const toggleFollowFromMessage = async (message) => {
  if (!message?.senderId) return;
  await markMessageRead(message);
  try {
    const res = await toggleFollow(message.senderId);
    message.status = res?.followed ? 'following' : 'back';
  } catch (e) {
    // request 拦截器已提示
  }
};

const replyFromMessage = async (message, text) => {
  if (!message?.postId || !message?.commentId || !message?.senderId) return;
  try {
    await createComment({
      postId: message.postId,
      content: text,
      parentId: message.commentId,
      replyUserId: message.senderId,
    });
    await markMessageRead(message);
    showToast('回复成功', 'success');
  } catch (e) {
    // request 拦截器已提示
  }
};

const toggleCommentLikeFromMessage = async (message) => {
  if (!message?.commentId) return;
  const previousLiked = !!message.commentLiked;
  message.commentLiked = !previousLiked;
  try {
    const res = await toggleLikeComment(message.commentId);
    message.commentLiked = !!res?.liked;
    await markMessageRead(message);
  } catch (e) {
    message.commentLiked = previousLiked;
  }
};

// 切换 Tab
const switchTab = async (tab) => {
  activeTab.value = tab;
  await fetchMessages(tab);
};

const handleReadAll = async () => {
  await notificationStore.markAllRead();
  messages.value = Object.fromEntries(
    Object.entries(messages.value).map(([key, list]) => [
      key,
      list.map((item) => ({ ...item, read: true })),
    ]),
  );
  showToast('已全部标记为已读', 'success');
};

onMounted(async () => {
  if (!isLoggedIn.value) return;
  await Promise.all([
    notificationStore.fetchUnreadCount(),
    fetchMessages(activeTab.value),
  ]);
});
</script>

<template>
  <PageShell>
    <div class="flex flex-col h-full bg-white">
      <!-- 顶部搜索栏 -->
      <header
        class="sticky top-0 bg-white py-4 z-5"
      >
        <SearchBar variant="compact" placeholder="巴西日本1点淘汰赛" />
      </header>

      <div class="mx-auto w-3/5">
        <!-- Tab 导航 -->
        <div
          class="flex items-center justify-between py-3 text-sm font-medium text-gray-500 bg-white sticky top-[60px] z-10"
        >
          <div class="flex items-center justify-start gap-4">
            <button
              @click="switchTab('comments')"
              :class="[
                'px-4 py-1.5 rounded-full transition-colors',
                activeTab === 'comments'
                  ? 'text-black font-bold bg-gray-100'
                  : 'text-gray-500 font-medium',
              ]"
            >
              评论和@
            </button>
            <button
              @click="switchTab('likes')"
              :class="[
                'px-4 py-1.5 rounded-full transition-colors',
                activeTab === 'likes'
                  ? 'text-black font-bold bg-gray-100'
                  : 'text-gray-500 font-medium',
              ]"
            >
              赞和收藏
            </button>
            <button
              @click="switchTab('follows')"
              :class="[
                'px-4 py-1.5 rounded-full transition-colors',
                activeTab === 'follows'
                  ? 'text-black font-bold bg-gray-100'
                  : 'text-gray-500 font-medium',
              ]"
            >
              新增关注
            </button>
          </div>
          <button
            v-if="isLoggedIn && unreadCount > 0"
            class="text-xs text-gray-500 hover:text-gray-900"
            @click="handleReadAll"
          >
            一键已读 {{ unreadCount }}
          </button>
        </div>

        <!-- 列表内容区域 -->
        <div class="flex-1 overflow-y-auto">
          <div
            v-if="!isLoggedIn"
            class="py-24 text-center text-sm text-text-muted"
          >
            登录后查看消息通知
          </div>
          <div
            v-else-if="loading"
            class="py-24 text-center text-sm text-text-muted"
          >
            加载中...
          </div>
          <div
            v-else-if="currentMessages.length === 0"
            class="py-24 text-center text-sm text-text-muted"
          >
            暂无消息
          </div>
          <!-- 评论和@ 列表 -->
          <div
            v-else-if="activeTab === 'comments'"
            class="divide-y divide-gray-50"
          >
            <CommentNotificationItem
              v-for="msg in currentMessages"
              :key="msg.id"
              :data="msg"
              @open-post="openPostFromMessage"
              @submit-reply="replyFromMessage"
              @toggle-comment-like="toggleCommentLikeFromMessage"
            />
          </div>

          <!-- 赞和收藏 列表 -->
          <div
            v-else-if="activeTab === 'likes'"
            class="divide-y divide-gray-50"
          >
            <LikeNotificationItem
              v-for="msg in currentMessages"
              :key="msg.id"
              :data="msg"
              @open-post="openPostFromMessage"
            />
          </div>

          <!-- 新增关注 列表 -->
          <div
            v-else-if="activeTab === 'follows'"
            class="divide-y divide-gray-50"
          >
            <FollowNotificationItem
              v-for="msg in currentMessages"
              :key="msg.id"
              :data="msg"
              @open-profile="openProfileFromMessage"
              @toggle-follow="toggleFollowFromMessage"
            />
          </div>
        </div>
      </div>
    </div>
  </PageShell>
</template>
