<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import PostDetailModal from './PostDetailModal.vue';

const route = useRoute();
const postId = computed(() => route.params.id);
</script>

<template>
  <PostDetailModal
    v-if="postId"
    :key="String(postId)"
    :post-id="postId"
    display-mode="page"
  />
</template>
