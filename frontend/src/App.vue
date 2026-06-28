<script setup>
import { ref, computed, provide, watch, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Sidebar from './components/layout/Sidebar.vue';
import LoginModal from './components/common/LoginModal.vue';
import PostDetailModal from './components/post/PostDetailModal.vue';
import PublishModal from './components/post/PublishModal.vue';
import { useUserStore } from './stores/user';
import { onAuthExpired } from './auth/session';
import { showToast } from './utils/toast';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

// 登录弹窗：由 Sidebar 触发，LoginModal 内部成功后关闭
const showLoginModal = ref(false);
const openLoginModal = () => {
  showLoginModal.value = true;
};
const closeLoginModal = () => {
  showLoginModal.value = false;
};
const onLoginSuccess = () => {
  showLoginModal.value = false;
};

// ====== 发布弹窗 ======
const showPublishModal = ref(false);
// 发布版本号：每次发布成功后 +1，MainContent 监听此值刷新列表
const publishVersion = ref(0);
provide('publishVersion', publishVersion);

const openPublishModal = () => {
  showPublishModal.value = true;
};
const closePublishModal = () => {
  showPublishModal.value = false;
};
const onPublishSuccess = () => {
  publishVersion.value++;
};
provide('openPublishModal', openPublishModal);

// ====== 详情弹窗：由 Vue Router query 驱动，保留当前背景页 ======
const selectedPostId = ref(null);
const postOpenedInApp = ref(false);
const showPostDetail = computed(() => selectedPostId.value != null);

const openPostDetail = (postId) => {
  selectedPostId.value = postId;
  postOpenedInApp.value = true;
  router.push({
    name: route.name,
    params: route.params,
    query: { ...route.query, postId },
  });
};

const closePostDetail = () => {
  if (selectedPostId.value === null) return;
  selectedPostId.value = null;
  if (route.query.postId) {
    if (postOpenedInApp.value) {
      router.back();
    } else {
      const { postId, ...restQuery } = route.query;
      router.replace({
        name: route.name,
        params: route.params,
        query: restQuery,
      });
    }
  } else if (route.name === 'post-detail') {
    router.push({ name: 'home' });
  }
  postOpenedInApp.value = false;
};

// 静默关闭弹窗：用于弹窗内跳转其他路由（如点击作者头像跳个人主页）
const closePostDetailSilent = () => {
  if (selectedPostId.value === null) return;
  selectedPostId.value = null;
  postOpenedInApp.value = false;
};

// 路由变化时同步弹窗；站内用 ?postId= 保留背景页，直接访问 /post/:id 兼容打开
watch(
  () => ({
    name: route.name,
    routePostId: route.params.id,
    queryPostId: route.query.postId,
  }),
  (to) => {
    if (to.queryPostId) {
      selectedPostId.value = to.queryPostId;
    } else if (to.name === 'post-detail' && to.routePostId) {
      selectedPostId.value = to.routePostId;
    } else {
      selectedPostId.value = null;
      postOpenedInApp.value = false;
    }
  },
  { immediate: true },
);

// 通过 provide 注入给子组件，避免逐层传递 prop / emit
provide('openPostDetail', openPostDetail);
provide('closePostDetailSilent', closePostDetailSilent);

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
    :current-page="route.name"
    @login="openLoginModal"
    @navigate-home="navigateHome"
    @navigate-profile="navigateProfile"
    @publish="openPublishModal"
  />
  <!-- 主页面 -->
  <router-view />
  <!-- 弹窗 -->
  <LoginModal
    v-if="showLoginModal"
    @close="closeLoginModal"
    @login-success="onLoginSuccess"
  />
  <PublishModal
    v-if="showPublishModal"
    @close="closePublishModal"
    @publish-success="onPublishSuccess"
  />
  <PostDetailModal
    v-if="showPostDetail"
    :key="String(selectedPostId)"
    :post-id="selectedPostId"
    @close="closePostDetail"
  />
</template>
