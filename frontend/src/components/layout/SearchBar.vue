<script setup>
import { ref } from 'vue';
import searchIcon from '../../assets/icons/search.svg?raw';

const props = defineProps({
  placeholder: {
    type: String,
    default: '登录探索更多内容',
  },
  variant: {
    type: String,
    default: 'wide',
    validator: (value) => ['wide', 'compact'].includes(value),
  },
});

const emit = defineEmits(['search']);

const searchQuery = ref('');

const handleSearch = () => {
  emit('search', searchQuery.value.trim());
};
</script>

<template>
  <div
    class="rounded-[28px] px-4.5 py-3 flex items-center text-text-muted mx-auto"
    :class="
      props.variant === 'wide'
        ? 'bg-white border border-border-soft max-w-225 shadow-search'
        : 'bg-surface-muted border-none max-w-130'
    "
  >
    <span
      v-if="props.variant === 'wide'"
      class="size-5 [&>svg]:size-5"
      v-html="searchIcon"
    ></span>
    <input
      v-model="searchQuery"
      type="text"
      :placeholder="props.placeholder"
      class="border-none outline-none flex-1 bg-transparent placeholder:text-gray-300"
      :class="
        props.variant === 'wide'
          ? 'ml-2.5 text-base'
          : 'w-full h-full pl-4 pr-12 text-sm text-gray-700 placeholder:text-text-subtle'
      "
      @keyup.enter="handleSearch"
    />
    <button
      type="button"
      aria-label="搜索"
      class="flex items-center justify-center cursor-pointer"
      :class="
        props.variant === 'wide' ? 'size-8 bg-gray-800 rounded-full ml-2' : ''
      "
      @click="handleSearch"
    >
      <span
        class="size-5 [&>svg]:size-5"
        :class="props.variant === 'wide' ? 'text-white' : 'text-gray-600'"
        v-html="searchIcon"
      ></span>
    </button>
  </div>
</template>
