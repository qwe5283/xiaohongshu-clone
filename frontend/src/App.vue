<script setup>
import Sidebar from './components/layout/Sidebar.vue'
import MainContent from './components/layout/MainContent.vue'
import ProfilePage from './components/user/ProfilePage.vue'
import LoginModal from './components/common/LoginModal.vue'
import PostDetailModal from './components/post/PostDetailModal.vue'
import { ref } from 'vue'

const showLoginModal = ref(false)
const showPostDetail = ref(false)
const selectedPostId = ref(null)
const currentPage = ref('home')

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

const navigateToProfile = () => {
  currentPage.value = 'profile'
}

const navigateToHome = () => {
  currentPage.value = 'home'
}
</script>

<template>
  <Sidebar
    :current-page="currentPage"
    @login="openLoginModal"
    @navigate-home="navigateToHome"
    @navigate-profile="navigateToProfile"
  />
  <MainContent v-if="currentPage === 'home'" @open-post="openPostDetail" @open-profile="navigateToProfile" />
  <ProfilePage v-if="currentPage === 'profile'" />
  <LoginModal v-if="showLoginModal" @close="closeLoginModal" />
  <PostDetailModal v-if="showPostDetail" :post-id="selectedPostId" @close="closePostDetail" />
</template>
