<script setup>
import { provide, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Sidebar from './components/layout/Sidebar.vue';
import LoginModal from './components/common/LoginModal.vue';
import PostDetailModal from './components/post/PostDetailModal.vue';
import PublishModal from './components/post/PublishModal.vue';
import { useUserStore } from './stores/user';
import { useUiStore } from './stores/ui';
import { onAuthExpired } from './auth/session';
import { showToast } from './utils/toast';
import { usePostDetailRoute } from './composables/usePostDetailRoute';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const uiStore = useUiStore();

const {
  selectedPostId,
  showPostDetail,
  displayRoute,
  currentSidebarPage,
  openPostDetail,
  closePostDetail,
  closePostDetailSilent,
} = usePostDetailRoute(route, router);

// 通过 provide 注入给子组件，避免逐层传递 prop / emit
provide('openPostDetail', openPostDetail);
provide('closePostDetailSilent', closePostDetailSilent);
provide('displayRoute', displayRoute);

const stopAuthExpired = onAuthExpired((message) => {
  userStore.logout();
  showToast(message || '登录已失效，请重新登录', 'error');
  if (route.name !== 'home') {
    router.push({ name: 'home' });
  }
});
onUnmounted(stopAuthExpired);

// ====== 导航 ======
// /profile 重定向到 /user/me，再由路由守卫解析成当前用户真实 id
const navigateHome = () => router.push({ name: 'home' });
const navigateProfile = () => router.push('/user/me');
const navigateMessages = () => router.push({ name: 'messages' });
const navigateAssistant = () => router.push({ name: 'assistant' });

// 启动恢复登录态：有 token 则校验 /me，失败会清 token
onMounted(async () => {
  if (userStore.token) {
    await userStore.fetchMe();
  }
});
</script>

<template>
  <!-- 侧边栏 -->
  <Sidebar
    :current-page="currentSidebarPage"
    @login="uiStore.openLoginModal"
    @navigate-home="navigateHome"
    @navigate-profile="navigateProfile"
    @navigate-messages="navigateMessages"
    @navigate-assistant="navigateAssistant"
    @publish="uiStore.openPublishModal"
  />
  <!-- 主页面：modal route 时继续把来源路由传给 router-view -->
  <router-view v-slot="{ Component }" :route="displayRoute">
    <component :is="Component" :key="displayRoute.fullPath" />
  </router-view>
  <!-- 弹窗 -->
  <LoginModal
    v-if="uiStore.showLoginModal"
    @close="uiStore.closeLoginModal"
    @login-success="uiStore.closeLoginModal"
  />
  <PublishModal
    v-if="uiStore.showPublishModal"
    @close="uiStore.closePublishModal"
    @publish-success="uiStore.markPublishSuccess"
  />
  <PostDetailModal
    v-if="showPostDetail"
    :key="String(selectedPostId)"
    :post-id="selectedPostId"
    @close="closePostDetail"
  />
</template>
