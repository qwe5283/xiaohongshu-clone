<script setup>
import { ref } from 'vue';
import searchIcon from '../../assets/icons/search.svg?raw';

const searchQuery = ref('');

const handleSearch = () => {
  // 搜索接口尚未接入，保留输入状态避免提交产生副作用。
};
</script>

<template>
  <div
    class="bg-white border border-[#d9d9d9] rounded-[28px] px-4.5 py-3 flex items-center text-gray-400 max-w-225 mx-auto shadow-[0_4px_12px_rgba(0,0,0,0.06)]"
  >
    <span class="size-5 [&>svg]:size-5" v-html="searchIcon"></span>
    <input
      v-model="searchQuery"
      type="text"
      placeholder="登录探索更多内容"
      class="border-none outline-none flex-1 ml-2.5 text-base bg-transparent placeholder:text-gray-300"
      @keyup.enter="handleSearch"
    />
    <div
      class="size-8 bg-gray-800 rounded-full flex items-center justify-center cursor-pointer ml-2"
      @click="handleSearch"
    >
      <span class="size-5 text-white [&>svg]:size-5" v-html="searchIcon"></span>
    </div>
  </div>
</template>
