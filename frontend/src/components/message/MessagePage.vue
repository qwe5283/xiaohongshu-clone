<script setup>
import { computed, onMounted, ref } from 'vue';
import PageShell from '@/components/layout/PageShell.vue';
import SearchBarLegacy from "@/components/layout/SearchBarLegacy.vue";
import CommentNotificationItem from './CommentNotificationItem.vue';
import LikeNotificationItem from './LikeNotificationItem.vue';
import FollowNotificationItem from './FollowNotificationItem.vue';
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsAsRead,
} from '@/api/notification';
import { useUserStore } from '@/stores/user';
import { showToast } from '@/utils/toast';

const activeTab = ref('comments');
const loading = ref(false);
const unreadCount = ref(0);
const messages = ref({
  comments: [],
  likes: [],
  follows: [],
});
const userStore = useUserStore();

const tabTypes = {
  comments: [3, 4],
  likes: [1, 2, 5],
  follows: [6],
};

const defaultAvatar =
  'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" rx="20" fill="%23eee"/><text x="50%" y="55%" text-anchor="middle" font-size="16" fill="%23bbb">U</text></svg>';
const defaultThumbnail =
  'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48"><rect width="48" height="48" rx="4" fill="%23f2f2f2"/><text x="50%" y="56%" text-anchor="middle" font-size="11" fill="%23999">笔记</text></svg>';

const currentMessages = computed(() => messages.value[activeTab.value] || []);
const isLoggedIn = computed(() => userStore.isLoggedIn);

const formatTime = (value) => {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const diffSeconds = Math.floor((Date.now() - date.getTime()) / 1000);
  if (diffSeconds < 60) return '刚刚';
  if (diffSeconds < 3600) return `${Math.floor(diffSeconds / 60)}分钟前`;
  if (diffSeconds < 86400) return `${Math.floor(diffSeconds / 3600)}小时前`;
  if (diffSeconds < 604800) return `${Math.floor(diffSeconds / 86400)}天前`;
  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
};

const adaptNotification = (item) => {
  const common = {
    id: item.id,
    raw: item,
    read: item.read,
    avatar: item.senderAvatar || defaultAvatar,
    username: item.senderNickname || '小红薯用户',
    action: item.typeText || '给你发来一条通知',
    time: formatTime(item.createTime),
    thumbnail: item.postCoverImage || defaultThumbnail,
  };

  if (item.type === 3 || item.type === 4) {
    return {
      ...common,
      content: item.content,
      hasReply: true,
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

const fetchUnreadCount = async () => {
  if (!isLoggedIn.value) {
    unreadCount.value = 0;
    return;
  }
  const data = await getUnreadNotificationCount();
  unreadCount.value = data?.unreadCount ?? 0;
};

const fetchMessages = async (tab = activeTab.value) => {
  if (!isLoggedIn.value) {
    messages.value[tab] = [];
    return;
  }
  loading.value = true;
  try {
    const pages = await Promise.all(
      tabTypes[tab].map((type) => getNotifications({ type, pageNum: 1, pageSize: 50 })),
    );
    const records = pages
      .flatMap((page) => page?.records || [])
      .sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime())
      .map(adaptNotification);
    messages.value[tab] = records;
  } finally {
    loading.value = false;
  }
};

// 切换 Tab
const switchTab = async (tab) => {
  activeTab.value = tab;
  await fetchMessages(tab);
};

const handleReadAll = async () => {
  await markAllNotificationsAsRead();
  unreadCount.value = 0;
  messages.value = Object.fromEntries(
    Object.entries(messages.value).map(([key, list]) => [
      key,
      list.map((item) => ({ ...item, read: true })),
    ]),
  );
  window.dispatchEvent(new CustomEvent('notifications-read-all'));
  showToast('已全部标记为已读', 'success');
};

onMounted(async () => {
  if (!isLoggedIn.value) return;
  await Promise.all([fetchUnreadCount(), fetchMessages(activeTab.value)]);
});
</script>

<template>
  <PageShell>
    <div class="flex flex-col h-full bg-white">
      <!-- 顶部搜索栏 -->
      <header class="sticky top-0 bg-white py-3 px-4 z-10 border-b border-gray-50">
        <SearchBarLegacy placeholder="巴西日本1点淘汰赛" />
      </header>

      <div class="mx-auto w-3/5">
        <!-- Tab 导航 -->
        <div class="flex items-center justify-between py-3 text-sm font-medium text-gray-500 bg-white sticky top-[60px] z-10">
          <div class="flex items-center justify-start gap-8">
            <button
                @click="switchTab('comments')"
                :class="['transition-colors', activeTab === 'comments' ? 'text-black font-bold bg-gray-100 px-4 py-1.5 rounded-full' : '']"
            >
              评论和@
            </button>
            <button
                @click="switchTab('likes')"
                :class="['transition-colors', activeTab === 'likes' ? 'text-black font-bold bg-gray-100 px-4 py-1.5 rounded-full' : '']"
            >
              赞和收藏
            </button>
            <button
                @click="switchTab('follows')"
                :class="['transition-colors', activeTab === 'follows' ? 'text-black font-bold bg-gray-100 px-4 py-1.5 rounded-full' : '']"
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
          <div v-if="!isLoggedIn" class="py-24 text-center text-sm text-gray-400">
            登录后查看消息通知
          </div>
          <div v-else-if="loading" class="py-24 text-center text-sm text-gray-400">
            加载中...
          </div>
          <div v-else-if="currentMessages.length === 0" class="py-24 text-center text-sm text-gray-400">
            暂无消息
          </div>
          <!-- 评论和@ 列表 -->
          <div v-else-if="activeTab === 'comments'" class="divide-y divide-gray-50">
            <CommentNotificationItem
                v-for="msg in currentMessages"
                :key="msg.id"
                :data="msg"
            />
          </div>

          <!-- 赞和收藏 列表 -->
          <div v-else-if="activeTab === 'likes'" class="divide-y divide-gray-50">
            <LikeNotificationItem
                v-for="msg in currentMessages"
                :key="msg.id"
                :data="msg"
            />
          </div>

          <!-- 新增关注 列表 -->
          <div v-else-if="activeTab === 'follows'" class="divide-y divide-gray-50">
            <FollowNotificationItem
                v-for="msg in currentMessages"
                :key="msg.id"
                :data="msg"
            />
          </div>
        </div>
      </div>
    </div>
  </PageShell>
</template>
