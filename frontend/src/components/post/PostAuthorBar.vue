<script setup>
defineProps({
  post: {
    type: Object,
    required: true,
  },
  isSelf: {
    type: Boolean,
    default: false,
  },
  isFollowed: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['open-profile', 'toggle-follow']);
</script>

<template>
  <div class="flex justify-between items-center mb-5">
    <div
      class="flex items-center gap-2.5 cursor-pointer"
      @click="emit('open-profile')"
    >
      <img
        :src="post.authorAvatar || post.author?.avatar"
        class="size-9 rounded-full object-cover"
      />
      <span class="text-sm font-medium truncate">
        {{ post.authorNickname || post.author?.nickname }}
      </span>
    </div>
    <button
      v-if="!isSelf"
      class="border px-6 py-2 rounded-[20px] text-sm font-bold cursor-pointer transition-all"
      :class="
        isFollowed
          ? 'bg-white text-primary border-primary'
          : 'bg-primary text-white border-primary'
      "
      @click="emit('toggle-follow')"
    >
      {{ isFollowed ? '已关注' : '关注' }}
    </button>
  </div>
</template>
