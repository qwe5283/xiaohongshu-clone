<script setup>
defineProps({
  text: {
    type: String,
    default: '加载中...',
  },
  size: {
    type: String,
    default: 'md',
    validator: (value) => ['sm', 'md', 'lg'].includes(value),
  },
});
</script>

<template>
  <div class="flex justify-center items-center text-text-muted">
    <span
      class="inline-block border-2 border-gray-300 border-t-primary rounded-full animate-spin mr-2"
      :class="{
        'size-4': size === 'sm',
        'size-5': size === 'md',
        'size-8': size === 'lg',
      }"
    ></span>
    {{ text }}
  </div>
</template>
