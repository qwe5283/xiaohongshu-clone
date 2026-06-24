<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Sidebar from './components/layout/Sidebar.vue'
import LoginModal from './components/common/LoginModal.vue'
import PostDetailModal from './components/post/PostDetailModal.vue'
import { useUserStore } from './stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 登录弹窗：由 Sidebar 触发，LoginModal 内部成功后关闭
const showLoginModal = ref(false)
const openLoginModal = () => {
  showLoginModal.value = true
}
const closeLoginModal = () => {
  showLoginModal.value = false
}
const onLoginSuccess = () => {
  showLoginModal.value = false
}

// 详情弹窗：由路由 /post/:id 驱动，关闭时 router.back 回到来源页
const showPostDetail = computed(() => route.name === 'post-detail')
const selectedPostId = computed(() => route.params.id ?? null)
const closePostDetail = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push({ name: 'home' })
  }
}

// Sidebar 跳转事件：home / profile
const navigateHome = () => router.push({ name: 'home' })
const navigateProfile = () => router.push({ name: 'profile' })

// 启动恢复登录态：有 token 则校验 /me，失败会清 token
onMounted(async () => {
  if (userStore.token) {
    await userStore.fetchMe()
  }
})
</script>

<template>
  <Sidebar
    :current-page="route.name"
    @login="openLoginModal"
    @navigate-home="navigateHome"
    @navigate-profile="navigateProfile"
  />
  <router-view />
  <LoginModal
    v-if="showLoginModal"
    @close="closeLoginModal"
    @login-success="onLoginSuccess"
  />
  <PostDetailModal
    v-if="showPostDetail"
    :post-id="selectedPostId"
    @close="closePostDetail"
  />
</template>

<style scoped>
</style>
