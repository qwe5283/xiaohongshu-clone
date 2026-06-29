<script setup>
import { ref } from 'vue';
import heartIcon from '@/assets/icons/heart.svg?raw';
import commentIcon from '@/assets/icons/comment.svg?raw';

defineProps({
  data: {
    type: Object,
    required: true,
    default: () => ({})
  }
});
</script>

<template>
  <div class="flex gap-3 p-4 hover:bg-gray-50 transition-colors">
    <!-- 头像 -->
    <img :src="data.avatar" class="w-10 h-10 rounded-full object-cover flex-shrink-0" />

    <!-- 中间内容 -->
    <div class="flex-1 min-w-0">
      <!-- 第一行：用户名 + 动作 + 时间 -->
      <div class="flex items-center gap-2 text-sm mb-1">
        <span class="font-bold text-gray-900">{{ data.username }}</span>
        <span v-if="data.tag" class="text-xs text-gray-400 border border-gray-200 px-1 rounded">{{ data.tag }}</span>
        <span class="text-gray-500">{{ data.action }}</span>
        <span class="text-gray-400 text-xs">{{ data.time }}</span>
      </div>

      <!-- 第二行：评论内容 -->
      <div v-if="data.content" class="text-sm text-gray-800 mb-1">
        {{ data.content }}
      </div>

      <!-- 被回复的内容 (引用) -->
      <div v-if="data.replyTo" class="text-xs text-gray-400 bg-gray-50 inline-block px-2 py-1 rounded mb-2">
        {{ data.replyTo }}
      </div>

      <!-- 底部操作栏 (仅当有回复按钮时显示，参考截图 momo 和 小红薯...) -->
      <div v-if="data.hasReply" class="flex items-center gap-4 mt-2">
        <button class="flex items-center gap-1 text-xs text-gray-500 border border-gray-200 px-3 py-1 rounded-full hover:bg-gray-100">
          <span v-html="commentIcon" class="w-3 h-3"></span> 回复
        </button>
      </div>
    </div>

    <!-- 右侧缩略图 -->
    <img v-if="data.thumbnail" :src="data.thumbnail" class="w-12 h-12 rounded object-cover flex-shrink-0 bg-gray-100" />
  </div>
</template>