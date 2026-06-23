<script setup>
import Sidebar from './components/layout/Sidebar.vue'
import MainContent from './components/layout/MainContent.vue'
import LoginModal from './components/common/LoginModal.vue'
import PostDetailModal from './components/post/PostDetailModal.vue'
import { ref } from 'vue'

const showLoginModal = ref(false)
const showPostDetail = ref(false)
const selectedPostId = ref(null)

const openLoginModal = () => {
  showLoginModal.value = true
}

const closeLoginModal = () => {
  showLoginModal.value = false
}

const openPostDetail = (postId) => {
  selectedPostId.value = postId
  showPostDetail.value = true
}

const closePostDetail = () => {
  showPostDetail.value = false
  selectedPostId.value = null
}
</script>

<template>
  <Sidebar @login="openLoginModal" />
  <MainContent @open-post="openPostDetail" />
  <LoginModal v-if="showLoginModal" @close="closeLoginModal" />
  <PostDetailModal v-if="showPostDetail" :post-id="selectedPostId" @close="closePostDetail" />
</template>

<style scoped>
</style>
