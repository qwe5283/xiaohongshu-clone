<script setup>
import { computed } from 'vue';
import { formatRelativeTime } from '@/utils/format';
import heartIcon from '@/assets/icons/heart.svg?raw';
import heartFilledIcon from '@/assets/icons/heart-filled.svg?raw';
import commentIcon from '@/assets/icons/comment.svg?raw';

const props = defineProps({
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
  'toggle-comment-like',
  'open-profile',
  'update:replyText',
  'submit-reply',
]);

const AVATAR_SPACE_CLASS = 'pl-9';

const currentHeartIcon = (comment) =>
  comment?.liked ? heartFilledIcon : heartIcon;

const likeLabel = (comment) =>
  (comment?.likeCount || 0) > 0 ? comment.likeCount : '赞';

const replyLabel = (comment) =>
  (comment?.replyCount || 0) > 0 ? comment.replyCount : '回复';

const visibleReplies = (comment) => {
  const replies = props.repliesMap[comment.id] || [];
  return props.showRepliesMap[comment.id] ? replies : replies.slice(0, 1);
};

const hiddenReplyCount = (comment) => {
  const count = comment.replyCount || 0;
  return props.showRepliesMap[comment.id] ? 0 : Math.max(count - 1, 0);
};

const replyInputLabel = computed(() =>
  props.replyingTo ? `回复 @${props.replyingTo.nickname}：` : '',
);

function handleReply(comment, rootComment) {
  emit('reply-click', comment, rootComment);
}

function openProfile(userId) {
  if (!userId) return;
  emit('open-profile', userId);
}
</script>

<template>
  <div
    v-if="comments.length === 0"
    class="text-sm text-text-muted py-4 text-center"
  >
    暂无评论，来说点什么吧～
  </div>

  <div v-else class="space-y-5">
    <div v-for="comment in comments" :key="comment.id">
      <div class="flex items-start gap-3">
        <button
          type="button"
          class="shrink-0"
          @click="openProfile(comment.userId)"
        >
          <img
            :src="comment.userAvatar"
            class="size-8 rounded-full object-cover bg-gray-100 hover:cursor-pointer"
            alt=""
          />
        </button>

        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-1.5 min-h-5">
            <button
              type="button"
              class="truncate text-left text-xs font-medium text-gray-500 transition-colors hover:cursor-pointer"
              @click="openProfile(comment.userId)"
            >
              {{ comment.userNickname }}
            </button>
            <span
              v-if="comment.userId === post.userId"
              class="shrink-0 rounded bg-red-50 px-1 py-0.5 text-[10px] leading-none text-red-500"
            >
              作者
            </span>
          </div>

          <div class="mt-0.5 text-sm leading-5 text-gray-800 break-words">
            {{ comment.content }}
          </div>

          <div class="mt-1 text-xs leading-4 text-text-muted">
            {{ formatRelativeTime(comment.createTime) }}
          </div>

          <div class="mt-1.5 flex items-center gap-4 text-xs text-gray-500">
            <button
              type="button"
              class="flex h-6 items-center gap-1 transition-colors hover:text-red-500"
              :class="comment.liked ? 'text-red-500' : 'text-gray-500'"
              @click="emit('toggle-comment-like', comment)"
            >
              <span
                class="size-4 [&>svg]:size-4"
                v-html="currentHeartIcon(comment)"
              ></span>
              <span>{{ likeLabel(comment) }}</span>
            </button>

            <button
              type="button"
              class="flex h-6 items-center gap-1 transition-colors hover:text-primary"
              :class="
                replyingTo?.commentId === comment.id
                  ? 'text-primary font-medium'
                  : 'text-gray-500'
              "
              @click="handleReply(comment, comment)"
            >
              <span class="size-4 [&>svg]:size-4" v-html="commentIcon"></span>
              <span>{{ replyLabel(comment) }}</span>
            </button>
          </div>
        </div>
      </div>

      <div
        v-if="replyingTo?.commentId === comment.id"
        :class="[AVATAR_SPACE_CLASS, 'mt-2 flex items-center gap-2']"
      >
        <span class="field-hint shrink-0">{{ replyInputLabel }}</span>
        <input
          :value="replyText"
          type="text"
          class="min-w-0 flex-1 rounded-full border-none bg-gray-100 px-3 py-1.5 text-xs outline-none"
          :placeholder="`回复 ${replyingTo.nickname}...`"
          @input="emit('update:replyText', $event.target.value)"
          @keyup.enter="emit('submit-reply')"
        />
      </div>

      <div
        v-if="(comment.replyCount || 0) > 0"
        :class="[AVATAR_SPACE_CLASS, 'mt-3 space-y-3']"
      >
        <div
          v-for="reply in visibleReplies(comment)"
          :key="reply.id"
          class="flex items-start gap-3"
        >
          <button
            type="button"
            class="shrink-0"
            @click="openProfile(reply.userId)"
          >
            <img
              :src="reply.userAvatar"
              class="size-6 rounded-full object-cover bg-gray-100 hover:cursor-pointer"
              alt=""
            />
          </button>

          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-1.5 min-h-5">
              <button
                type="button"
                class="truncate text-left text-xs font-medium text-gray-500 transition-colors hover:cursor-pointer"
                @click="openProfile(reply.userId)"
              >
                {{ reply.userNickname }}
              </button>
              <span
                v-if="reply.userId === post.userId"
                class="shrink-0 rounded bg-red-50 px-1 py-0.5 text-[10px] leading-none text-red-500"
              >
                作者
              </span>
            </div>

            <div class="mt-0.5 text-sm leading-5 text-gray-800 break-words">
              <template
                v-if="reply.parentId !== comment.id && reply.replyUserNickname"
              >
                回复
                <span class="font-medium text-gray-600">
                  {{ reply.replyUserNickname }}
                </span>
                ：
              </template>
              {{ reply.content }}
            </div>

            <div class="mt-1 text-xs leading-4 text-text-muted">
              {{ formatRelativeTime(reply.createTime) }}
            </div>

            <div class="mt-1.5 flex items-center gap-4 text-xs text-gray-500">
              <button
                type="button"
                class="flex h-6 items-center gap-1 transition-colors hover:text-red-500"
                :class="reply.liked ? 'text-red-500' : 'text-gray-500'"
                @click="emit('toggle-comment-like', reply)"
              >
                <span
                  class="size-4 [&>svg]:size-4"
                  v-html="currentHeartIcon(reply)"
                ></span>
                <span>{{ likeLabel(reply) }}</span>
              </button>

              <button
                type="button"
                class="flex h-6 items-center gap-1 transition-colors hover:text-primary"
                :class="
                  replyingTo?.commentId === reply.id
                    ? 'text-primary font-medium'
                    : 'text-gray-500'
                "
                @click="handleReply(reply, comment)"
              >
                <span
                  class="size-4 [&>svg]:size-4"
                  v-html="commentIcon"
                ></span>
                <span>回复</span>
              </button>
            </div>

            <div
              v-if="replyingTo?.commentId === reply.id"
              class="mt-2 flex items-center gap-2"
            >
              <span class="field-hint shrink-0">{{ replyInputLabel }}</span>
              <input
                :value="replyText"
                type="text"
                class="min-w-0 flex-1 rounded-full border-none bg-gray-100 px-3 py-1.5 text-xs outline-none"
                :placeholder="`回复 ${replyingTo.nickname}...`"
                @input="emit('update:replyText', $event.target.value)"
                @keyup.enter="emit('submit-reply')"
              />
            </div>
          </div>
        </div>

        <button
          v-if="hiddenReplyCount(comment) > 0"
          type="button"
          class="text-xs font-medium text-text-clickable hover:opacity-80 ml-9"
          @click="emit('toggle-replies', comment)"
        >
          展开 {{ hiddenReplyCount(comment) }} 条回复
        </button>

        <button
          v-else-if="showRepliesMap[comment.id] && (comment.replyCount || 0) > 1"
          type="button"
          class="text-xs font-medium text-text-clickable hover:opacity-80 ml-9"
          @click="emit('toggle-replies', comment)"
        >
          收起回复
        </button>
      </div>
    </div>
  </div>
</template>
