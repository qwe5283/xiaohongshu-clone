<script setup>
import { ref } from 'vue';
import commentIcon from '@/assets/icons/comment.svg?raw';
import heartIcon from '@/assets/icons/heart.svg?raw';
import heartFilledIcon from '@/assets/icons/heart-filled.svg?raw';

const props = defineProps({
  data: {
    type: Object,
    required: true,
    default: () => ({}),
  },
});

const emit = defineEmits([
  'open-post',
  'submit-reply',
  'toggle-comment-like',
]);

const replyText = ref('');
const showReplyBox = ref(false);

function toggleReplyBox() {
  showReplyBox.value = !showReplyBox.value;
  if (!showReplyBox.value) replyText.value = '';
}

function submitReply() {
  const text = replyText.value.trim();
  if (!text) return;
  emit('submit-reply', props.data, text);
  replyText.value = '';
  showReplyBox.value = false;
}
</script>

<template>
  <div
    class="flex gap-3 p-4 hover:bg-gray-50 transition-colors cursor-pointer"
    @click="emit('open-post', data)"
  >
    <span
      v-if="!data.read"
      class="mt-4 size-2 rounded-full bg-primary flex-shrink-0"
    ></span>

    <!-- 头像 -->
    <img
      :src="data.avatar"
      class="w-10 h-10 rounded-full object-cover flex-shrink-0"
      alt=""
    />

    <!-- 中间内容 -->
    <div class="flex-1 min-w-0">
      <!-- 第一行：用户名 + 动作 + 时间 -->
      <div class="flex items-center gap-2 text-sm mb-1">
        <span class="font-bold text-gray-900">{{ data.username }}</span>
        <span
          v-if="data.tag"
          class="field-hint border border-gray-200 px-1 rounded"
          >{{ data.tag }}</span
        >
        <span class="text-gray-500">{{ data.action }}</span>
        <span class="field-hint">{{ data.time }}</span>
      </div>

      <!-- 第二行：评论内容 -->
      <div v-if="data.content" class="text-sm text-gray-800 mb-1">
        {{ data.content }}
      </div>

      <!-- 被回复的内容 (引用) -->
      <div
        v-if="data.replyTo"
        class="field-hint bg-gray-50 inline-block px-2 py-1 rounded mb-2"
      >
        {{ data.replyTo }}
      </div>

      <!-- 底部操作栏 (仅当有回复按钮时显示，参考截图 momo 和 小红薯...) -->
      <div v-if="data.hasReply" class="flex items-center gap-2 mt-2">
        <button
          type="button"
          class="flex items-center gap-1 text-sm text-gray-500 border border-gray-200 px-3 h-8 rounded-full hover:bg-gray-100"
          @click.stop="toggleReplyBox"
        >
          <span v-html="commentIcon" class="size-4"></span> 回复
        </button>
        <button
          v-if="data.commentId"
          type="button"
          class="flex items-center gap-1 text-sm border border-gray-200 size-8 rounded-full hover:bg-gray-100"
          :class="data.commentLiked ? 'text-red-500' : 'text-gray-500'"
          @click.stop="emit('toggle-comment-like', data)"
        >
          <span
            class="size-4 [&>svg]:size-4 mx-auto"
            v-html="data.commentLiked ? heartFilledIcon : heartIcon"
          ></span>
        </button>
      </div>

      <div
        v-if="showReplyBox"
        class="mt-3 flex items-center gap-2"
        @click.stop
      >
        <input
          v-model="replyText"
          type="text"
          class="min-w-0 flex-1 rounded-full border-none bg-gray-100 px-3 py-2 text-xs outline-none"
          :placeholder="`回复 ${data.username}...`"
          @keyup.enter="submitReply"
        />
        <button
          type="button"
          class="rounded-full bg-primary px-3 py-2 text-xs font-medium text-white disabled:opacity-50"
          :disabled="!replyText.trim()"
          @click="submitReply"
        >
          发送
        </button>
      </div>
    </div>

    <!-- 右侧缩略图 -->
    <img
      v-if="data.thumbnail"
      :src="data.thumbnail"
      class="w-12 h-12 rounded object-cover flex-shrink-0 bg-gray-100"
      alt=""
    />
  </div>
</template>
