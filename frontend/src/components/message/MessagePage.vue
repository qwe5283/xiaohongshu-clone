<script setup>
import { ref, computed } from 'vue';
import PageShell from '@/components/layout/PageShell.vue';
import SearchBarLegacy from "@/components/layout/SearchBarLegacy.vue";
import CommentNotificationItem from './CommentNotificationItem.vue';
import LikeNotificationItem from './LikeNotificationItem.vue';
import FollowNotificationItem from './FollowNotificationItem.vue';

const activeTab = ref('comments');

// 模拟数据 - 评论和@
const commentMessages = ref([
  {
    id: 1,
    avatar: 'https://placehold.co/100x100/e0e0e0/333?text=M',
    username: 'momo',
    action: '评论了你的笔记',
    time: '06-06',
    content: '过了吗?',
    thumbnail: 'https://placehold.co/60x60/e0f2f1/333?text=Img',
    hasReply: true
  },
  {
    id: 2,
    avatar: 'https://placehold.co/100x100/333/fff?text=听',
    username: '听雨说',
    tag: '作者',
    action: '回复了你的评论',
    time: '05-07',
    content: '。。。',
    replyTo: '没作业吗',
    thumbnail: 'https://placehold.co/60x60/f5f5f5/999?text=Blank',
    hasReply: true
  },
  {
    id: 3,
    avatar: 'https://placehold.co/100x100/333/fff?text=胖',
    username: '胖胖',
    tag: '作者',
    action: '回复了你的评论',
    time: '05-07',
    content: '该评论已删除',
    isDeleted: true,
    thumbnail: 'https://placehold.co/60x60/e0e0e0/333?text=Img'
  },
  {
    id: 4,
    avatar: 'https://placehold.co/100x100/fff3e0/333?text=🥕',
    username: '小红薯5F96BE23',
    action: '回复了你的评论',
    time: '05-02',
    content: '量太少了🤤',
    replyTo: '可以试试阿里云',
    thumbnail: 'https://placehold.co/60x60/e3f2fd/333?text=Code',
    hasReply: true
  }
]);

// 模拟数据 - 赞和收藏
const likeMessages = ref([
  {
    id: 1,
    avatar: 'https://placehold.co/100x100/ffebee/333?text=Y',
    username: 'Ymi在成都回收报废车🚗',
    action: '赞了你的笔记',
    time: '6天前',
    thumbnail: 'https://placehold.co/60x60/e0f2f1/333?text=Img'
  },
  {
    id: 2,
    avatar: 'https://placehold.co/100x100/e3f2fd/333?text=✈️',
    username: '小红薯6A3A4CC1',
    action: '赞了你的笔记',
    time: '7天前',
    thumbnail: 'https://placehold.co/60x60/cfd8dc/333?text=Img'
  },
  {
    id: 3,
    avatar: 'https://placehold.co/100x100/fff9c4/333?text=😂',
    username: '小红薯6A3988D7',
    action: '赞了你的笔记',
    time: '06-21',
    thumbnail: 'https://placehold.co/60x60/cfd8dc/333?text=Img'
  },
  {
    id: 4,
    avatar: 'https://placehold.co/100x100/ffe0b2/333?text=🧋',
    username: '小红薯6A329C9F',
    action: '赞了你的评论',
    time: '06-17',
    contentPreview: '蹲不上🐷',
    thumbnail: 'https://placehold.co/60x60/fff/333?text=Text'
  }
]);

// 模拟数据 - 新增关注
const followMessages = ref([
  {
    id: 1,
    avatar: 'https://placehold.co/100x100/333/fff?text=Anime',
    username: '祈眠',
    action: '开始关注你了',
    time: '05-19',
    status: 'follow' // follow, mutual, back
  },
  {
    id: 2,
    avatar: 'https://placehold.co/100x100/333/fff?text=听',
    username: '听雨说',
    action: '开始关注你了',
    time: '04-14',
    status: 'mutual'
  },
  {
    id: 3,
    avatar: 'https://placehold.co/100x100/f8bbd0/333?text=AI',
    username: '编程小助理(回罐我岭籽料)',
    action: 'Ta关注了你，期待你的回关',
    time: '04-10',
    status: 'back'
  },
  {
    id: 4,
    avatar: 'https://placehold.co/100x100/e1bee7/333?text=X',
    username: 'Xerlite.seraphina',
    action: '开始关注你了',
    time: '03-25',
    status: 'mutual'
  }
]);

// 切换 Tab
const switchTab = (tab) => {
  activeTab.value = tab;
};
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
        <div class="flex items-center justify-start gap-8 py-3 text-sm font-medium text-gray-500 bg-white sticky top-[60px] z-10">
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

        <!-- 列表内容区域 -->
        <div class="flex-1 overflow-y-auto">
          <!-- 评论和@ 列表 -->
          <div v-if="activeTab === 'comments'" class="divide-y divide-gray-50">
            <CommentNotificationItem
                v-for="msg in commentMessages"
                :key="msg.id"
                :data="msg"
            />
          </div>

          <!-- 赞和收藏 列表 -->
          <div v-else-if="activeTab === 'likes'" class="divide-y divide-gray-50">
            <LikeNotificationItem
                v-for="msg in likeMessages"
                :key="msg.id"
                :data="msg"
            />
          </div>

          <!-- 新增关注 列表 -->
          <div v-else-if="activeTab === 'follows'" class="divide-y divide-gray-50">
            <FollowNotificationItem
                v-for="msg in followMessages"
                :key="msg.id"
                :data="msg"
            />
          </div>
        </div>
      </div>
    </div>
  </PageShell>
</template>