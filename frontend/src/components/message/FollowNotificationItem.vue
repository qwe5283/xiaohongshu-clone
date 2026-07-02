<script setup>
defineProps({
  data: {
    type: Object,
    required: true,
    default: () => ({}),
  },
});

// 按钮配置
const buttonConfig = {
  follow: { text: '关注', class: 'bg-primary text-white' },
  mutual: { text: '互相关注', class: 'bg-primary text-white' },
  back: { text: '回关', class: 'bg-primary text-white' },
};
</script>

<template>
  <div class="flex items-center gap-3 p-4 hover:bg-gray-50 transition-colors">
    <span
      v-if="!data.read"
      class="size-2 rounded-full bg-primary flex-shrink-0"
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
      <div class="field-hint">
        {{ data.action }} <span class="mx-1">{{ data.time }}</span>
      </div>
    </div>

    <!-- 右侧按钮 -->
    <button
      :class="[
        'px-6 py-1.5 rounded-full text-sm font-bold transition-opacity active:opacity-80',
        (buttonConfig[data.status] || buttonConfig.back).class,
      ]"
    >
      {{ (buttonConfig[data.status] || buttonConfig.back).text }}
    </button>
  </div>
</template>
