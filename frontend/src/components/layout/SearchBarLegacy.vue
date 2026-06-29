<script setup>
import { ref } from 'vue';
import searchIcon from '../../assets/icons/search.svg?raw';

const searchQuery = ref('');

const handleSearch = () => {
  // 搜索接口尚未接入，保留输入状态避免提交产生副作用。
};
</script>

<template>
  <div class="bg-[#f7f7f7] border-none rounded-[28px] px-4.5 py-3 flex items-center text-gray-400 max-w-130 mx-auto">

    <input
        type="text"
        placeholder="输入搜索内容..."
        class="w-full h-full pl-4 pr-12 text-sm text-gray-700 bg-transparent outline-none placeholder-[#bbbbbb]"
    />

    <button
        type="button"
        aria-label="搜索"
    >
      <span class="size-5 text-gray-600 [&>svg]:size-5" v-html="searchIcon"></span>
    </button>
  </div>
</template>