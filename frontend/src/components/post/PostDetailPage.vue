<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import PostDetailModal from './PostDetailModal.vue';
import SearchBar from "@/components/layout/SearchBar.vue";
import SearchBarLegacy from "@/components/layout/SearchBarLegacy.vue";

const route = useRoute();
const postId = computed(() => route.params.id);
</script>

<template>
  <!-- 顶部搜索栏 -->
  <header class="sticky top-0 bg-white py-7 z-[5]">
    <SearchBarLegacy />
  </header>

  <PostDetailModal
    v-if="postId"
    :key="String(postId)"
    :post-id="postId"
    display-mode="page"
  />
</template>
