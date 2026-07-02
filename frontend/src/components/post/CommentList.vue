<script setup>
import { formatRelativeTime } from '@/utils/format';

defineProps({
  post: {
    type: Object,
    required: true,
  },
  comments: {
    type: Array,
    default: () => [],
  },
  repliesMap: {
    type: Object,
    default: () => ({}),
  },
  showRepliesMap: {
    type: Object,
    default: () => ({}),
  },
  replyingTo: {
    type: Object,
    default: null,
  },
  replyText: {
    type: String,
    default: '',
  },
});

const emit = defineEmits([
  'reply-click',
  'toggle-replies',
  'update:replyText',
  'submit-reply',
]);
</script>

<template>
  <div
    v-if="comments.length === 0"
    class="text-sm text-text-muted py-4 text-center"
  >
    暂无评论，来说点什么吧～
  </div>
  <div v-for="comment in comments" :key="comment.id" class="mb-5">
    <div class="flex items-start gap-2 mb-1.5">
      <img
        :src="comment.userAvatar"
        class="size-6 rounded-full object-cover shrink-0"
      />
      <div class="flex-1 min-w-0">
        <span class="text-xs text-gray-500">
          {{ comment.userNickname }}
          <span
            v-if="comment.userId === post.userId"
            class="bg-[#fee] text-red-500 px-1 py-0.5 rounded text-[10px] ml-1"
            >作者</span
          >
        </span>
        <div class="text-sm text-gray-800 mt-0.5">
          {{ comment.content }}
        </div>
        <div class="flex items-center gap-3 mt-1">
          <span class="field-hint">
            {{ formatRelativeTime(comment.createTime) }}
          </span>
          <span
            class="field-hint cursor-pointer hover:text-primary"
            :class="{
              'text-primary font-medium':
                replyingTo?.commentId === comment.id,
            }"
            @click="emit('reply-click', comment, comment)"
          >
            {{ replyingTo?.commentId === comment.id ? '取消回复' : '回复' }}
          </span>
        </div>

        <div v-if="(comment.replyCount || 0) > 0" class="mt-2">
          <button
            class="text-xs text-primary font-medium"
            @click="emit('toggle-replies', comment)"
          >
            {{
              showRepliesMap[comment.id]
                ? '收起'
                : `展开 ${comment.replyCount} 条回复`
            }}
          </button>
          <div
            v-if="showRepliesMap[comment.id]"
            class="mt-2 space-y-2 pl-3 border-l-2 border-gray-100"
          >
            <div
              v-for="reply in repliesMap[comment.id]"
              :key="reply.id"
              class="text-sm"
            >
              <span class="text-gray-500">
                <span class="font-medium">{{ reply.userNickname }}</span>
                <span
                  v-if="
                    reply.parentId !== comment.id && reply.replyUserNickname
                  "
                >
                  回复
                  <span class="font-medium">
                    {{ reply.replyUserNickname }}
                  </span>
                </span>
                <span class="text-gray-800">：{{ reply.content }}</span>
              </span>
              <div class="flex items-center gap-3 text-xs mt-0.5">
                <span class="text-text-muted">
                  {{ formatRelativeTime(reply.createTime) }}
                </span>
                <span
                  class="text-text-muted cursor-pointer hover:text-primary"
                  :class="{
                    'text-primary font-medium':
                      replyingTo?.commentId === reply.id,
                  }"
                  @click="emit('reply-click', reply, comment)"
                >
                  {{
                    replyingTo?.commentId === reply.id ? '取消回复' : '回复'
                  }}
                </span>
              </div>
              <div
                v-if="replyingTo?.commentId === reply.id"
                class="mt-2 flex items-center gap-2"
              >
                <span class="field-hint shrink-0">
                  回复 @{{ replyingTo.nickname }}：
                </span>
                <input
                  :value="replyText"
                  type="text"
                  class="flex-1 bg-gray-100 border-none rounded-full px-3 py-1.5 text-xs outline-none"
                  :placeholder="`回复 ${replyingTo.nickname}...`"
                  @input="emit('update:replyText', $event.target.value)"
                  @keyup.enter="emit('submit-reply')"
                />
              </div>
            </div>
          </div>
        </div>

        <div
          v-if="replyingTo?.commentId === comment.id"
          class="mt-2 flex items-center gap-2"
        >
          <span class="field-hint shrink-0">
            回复 @{{ replyingTo.nickname }}：
          </span>
          <input
            :value="replyText"
            type="text"
            class="flex-1 bg-gray-100 border-none rounded-full px-3 py-1.5 text-xs outline-none"
            :placeholder="`回复 ${replyingTo.nickname}...`"
            @input="emit('update:replyText', $event.target.value)"
            @keyup.enter="emit('submit-reply')"
          />
        </div>
      </div>
    </div>
  </div>
</template>
