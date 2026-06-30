<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import PostDetailModal from './PostDetailModal.vue';
import SearchBar from '@/components/layout/SearchBar.vue';

const route = useRoute();
const postId = computed(() => route.params.id);
</script>

<template>
  <div class="h-screen flex flex-col overflow-hidden">
    <!-- 顶部搜索栏 -->
    <header class="shrink-0 bg-white py-7 z-[5]">
      <SearchBar variant="compact" />
    </header>

    <div class="flex-1 min-h-0">
      <PostDetailModal
        v-if="postId"
        :key="String(postId)"
        :post-id="postId"
        display-mode="page"
      />
    </div>
  </div>
</template>
