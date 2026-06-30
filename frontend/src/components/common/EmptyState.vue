<script setup>
defineProps({
  text: {
    type: String,
    default: '暂无内容',
  },
});
</script>

<template>
  <div class="state-panel">
    {{ text }}
  </div>
</template>
