<script setup>
defineProps({
  message: {
    type: String,
    default: '加载失败',
  },
});

const emit = defineEmits(['retry']);
</script>

<template>
  <div class="state-panel-column">
    <div class="mb-3">{{ message }}</div>
    <button
      class="bg-primary text-white px-5 py-2 rounded-full text-sm cursor-pointer"
      @click="emit('retry')"
    >
      重试
    </button>
  </div>
</template>
