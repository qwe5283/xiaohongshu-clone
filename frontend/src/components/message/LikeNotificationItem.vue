<script setup>
defineProps({
  data: {
    type: Object,
    required: true,
    default: () => ({}),
  },
});
</script>

<template>
  <div class="flex gap-3 p-4 hover:bg-gray-50 transition-colors">
    <span
      v-if="!data.read"
      class="mt-4 size-2 rounded-full bg-primary flex-shrink-0"
    ></span>

    <!-- 头像 -->
    <img
      :src="data.avatar"
      class="w-10 h-10 rounded-full object-cover flex-shrink-0"
    />

    <!-- 中间内容 -->
    <div class="flex-1 min-w-0">
      <div class="flex items-center gap-2 text-sm mb-1">
        <span class="font-bold text-gray-900">{{ data.username }}</span>
      </div>
      <div class="text-text-muted text-sm mb-1">
        {{ data.action }}&nbsp&nbsp{{ data.time }}
      </div>

      <!-- 如果是赞评论，显示被赞的内容 -->
      <div
        v-if="data.contentPreview"
        class="field-hint bg-gray-50 inline-block px-2 py-1 rounded mt-1"
      >
        {{ data.contentPreview }}
      </div>
    </div>

    <!-- 右侧缩略图 -->
    <img
      v-if="data.thumbnail"
      :src="data.thumbnail"
      class="w-12 h-12 rounded object-cover flex-shrink-0 bg-gray-100"
    />
  </div>
</template>
