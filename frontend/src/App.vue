<script setup>
import { ref, computed, provide, watch, onMounted, onUnmounted } from 'vue'
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
const openLoginModal = () => { showLoginModal.value = true }
const closeLoginModal = () => { showLoginModal.value = false }
const onLoginSuccess = () => { showLoginModal.value = false }

// ====== 详情弹窗：用 ref + history.pushState 驱动，不走 Vue Router 导航 ======
// 这样弹窗不会替换当前页面组件，也不会触发 scrollBehavior
const selectedPostId = ref(null)
const showPostDetail = computed(() => selectedPostId.value != null)

// 防止 close → history.back → popstate → close 的循环
let poppingState = false

const openPostDetail = (postId) => {
  selectedPostId.value = postId
  // 推入历史栈，支持浏览器前进/后退、地址栏可复制
  history.pushState({ postId }, '', `/post/${postId}`)
}

const closePostDetail = () => {
  if (selectedPostId.value === null) return
  selectedPostId.value = null
  // 回退历史栈以恢复原 URL，用标志位阻止 popstate 重复关闭
  if (history.state?.postId) {
    poppingState = true
    history.back()
    // popstate 在当前宏任务结束后才触发，下一帧重置标志
    requestAnimationFrame(() => { poppingState = false })
  }
}

// 静默关闭弹窗：仅隐藏弹窗 + 清除 URL 中的 /post/:id，不触发 history.back()
// 用于弹窗内跳转其他路由（如点击作者头像跳个人主页），避免 history.back 与 router.push 竞态
const closePostDetailSilent = () => {
  if (selectedPostId.value === null) return
  selectedPostId.value = null
  // 用 replaceState 将 /post/:id 替换为当前实际路由的 URL，不留历史记录
  const base = route.fullPath
  history.replaceState({ ...history.state, postId: null }, '', base)
}

// 浏览器后退按钮：popstate 触发时同步关闭弹窗
const onPopState = () => {
  if (poppingState) return
  // popstate 触发后 history.state 已被浏览器更新
  // 如果 state 里没有 postId，说明用户从弹窗状态后退了
  if (!history.state?.postId) {
    selectedPostId.value = null
  }
}

// 直接访问 /post/:id 时（分享链接），由路由驱动弹窗打开
watch(
  () => ({ name: route.name, id: route.params.id }),
  (to) => {
    if (to.name === 'post-detail' && to.id && selectedPostId.value == null) {
      selectedPostId.value = to.id
    }
  },
  { immediate: true }
)

// 通过 provide 注入给子组件，避免逐层传递 prop / emit
provide('openPostDetail', openPostDetail)
provide('closePostDetailSilent', closePostDetailSilent)

onMounted(() => window.addEventListener('popstate', onPopState))
onUnmounted(() => window.removeEventListener('popstate', onPopState))

// ====== 导航 ======
// /profile 重定向到 /user/me，再由路由守卫解析成当前用户真实 id
const navigateHome = () => router.push({ name: 'home' })
const navigateProfile = () => router.push('/user/me')

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
    :key="String(selectedPostId)"
    :post-id="selectedPostId"
    @close="closePostDetail"
  />
</template>

<style scoped>
</style>
