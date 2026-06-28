<script setup>
import heartIcon from '../../assets/icons/heart.svg?raw'

defineProps({
  posts: {
    type: Array,
    default: () => [],
  },
  emptyText: {
    type: String,
    default: '暂无笔记',
  },
})

const emit = defineEmits(['open'])

function isTextCard(post) {
  return !post.coverImage && post.type === 0
}

const formatCount = (num) => {
  if (num == null) return '0'
  if (num >= 10000) return (num / 10000).toFixed(1) + '万'
  return num.toString()
}
</script>

<template>
  <div class="grid grid-cols-5 gap-5">
    <div
      v-for="post in posts"
      :key="post.id"
      class="bg-white rounded-xl overflow-hidden cursor-pointer hover:shadow-md transition-shadow"
      :class="{ 'flex items-center justify-center h-[200px] border border-gray-100': isTextCard(post) }"
      @click="emit('open', post.id)"
    >
      <template v-if="isTextCard(post)">
        <div class="text-center font-bold text-lg whitespace-pre-line p-4">{{ post.title }}</div>
      </template>
      <template v-else>
        <div class="m-1 relative overflow-hidden rounded-xl shadow-[0_0_1px_rgba(0,0,0,0.6)] group">
          <img :src="post.coverImage" class="w-full block object-cover transition-transform duration-300 group-hover:scale-105" />
          <div class="absolute inset-0 bg-gradient-to-b from-black/5 to-black/25 opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none"></div>
        </div>
        <div class="p-3">
          <div class="text-sm font-medium leading-[1.4] line-clamp-2">{{ post.title }}</div>
          <div v-if="post.likeCount" class="flex justify-end items-center text-xs text-gray-500 mt-2">
            <span class="size-4 [&>svg]:size-4 mr-1" v-html="heartIcon"></span>
            {{ formatCount(post.likeCount) }}
          </div>
        </div>
      </template>
    </div>
    <div v-if="posts.length === 0" class="col-span-5 text-center text-gray-400 py-20">{{ emptyText }}</div>
  </div>
</template>
