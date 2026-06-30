<script setup>
import { computed } from 'vue';
import heartIcon from '../../assets/icons/heart.svg?raw';
import heartFilledIcon from '../../assets/icons/heart-filled.svg?raw';
import starIcon from '../../assets/icons/star.svg?raw';
import starFilledIcon from '../../assets/icons/star-filled.svg?raw';
import commentIcon from '../../assets/icons/comment.svg?raw';

const props = defineProps({
  post: {
    type: Object,
    required: true,
  },
  liked: {
    type: Boolean,
    default: false,
  },
  collected: {
    type: Boolean,
    default: false,
  },
  commentText: {
    type: String,
    default: '',
  },
});

const emit = defineEmits([
  'update:commentText',
  'submit-comment',
  'toggle-like',
  'toggle-collect',
]);

const currentHeartIcon = computed(() =>
  props.liked ? heartFilledIcon : heartIcon,
);
const currentStarIcon = computed(() =>
  props.collected ? starFilledIcon : starIcon,
);
</script>

<template>
  <div class="border-t border-gray-200 pt-4 flex items-center gap-4 shrink-0">
    <input
      :value="commentText"
      type="text"
      class="flex-1 bg-gray-100 border-none rounded-[20px] px-4 py-2.5 text-sm outline-none"
      placeholder="说点什么..."
      @input="emit('update:commentText', $event.target.value)"
      @keyup.enter="emit('submit-comment')"
    />
    <div class="flex gap-4 text-gray-500 items-center">
      <div
        class="flex items-center gap-1 text-sm cursor-pointer"
        @click="emit('toggle-like')"
      >
        <span
          class="size-5 [&>svg]:size-5 transition-colors"
          :class="liked ? 'text-red-500' : 'text-gray-500'"
          v-html="currentHeartIcon"
        ></span>
        <span :class="liked ? 'text-red-500' : 'text-gray-500'">
          {{ post.likeCount }}
        </span>
      </div>
      <div
        class="flex items-center gap-1 text-sm cursor-pointer"
        @click="emit('toggle-collect')"
      >
        <span
          class="size-5 [&>svg]:size-5 transition-colors"
          :class="collected ? 'text-amber-400' : 'text-gray-500'"
          v-html="currentStarIcon"
        ></span>
        <span :class="collected ? 'text-amber-400' : 'text-gray-500'">
          {{ post.collectCount }}
        </span>
      </div>
      <div class="flex items-center gap-1 text-sm">
        <span class="size-5 [&>svg]:size-5" v-html="commentIcon"></span>
        {{ post.commentCount }}
      </div>
    </div>
  </div>
</template>
