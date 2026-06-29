import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { showToast } from '@/utils/toast';

// 首页直接用现有 MainContent 组件作为路由组件
// ProfilePage / PostDetailPage 由路由懒加载
import MainContent from '@/components/layout/MainContent.vue';

const routes = [
  {
    path: '/',
    name: 'home',
    component: MainContent,
  },
  // /profile 重定向到 /user/me，再由守卫解析成真实 id
  { path: '/profile', redirect: '/user/me' },
  {
    path: '/user/:id',
    name: 'user-profile',
    // 路由级代码分割
    component: () => import('@/components/user/ProfilePage.vue'),
  },
  // 直接访问 /post/:id 时渲染完整详情页；站内点击由 App.vue 叠加弹窗
  {
    path: '/post/:id',
    name: 'post-page',
    component: () => import('@/components/post/PostDetailPage.vue'),
  },
  // 兜底：未匹配路径回首页
  { path: '/:pathMatch(.*)*', redirect: '/' },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 禁用自动滚动到顶部，保留用户当前滚动位置
  scrollBehavior() {
    return false;
  },
});

// 全局前置守卫
router.beforeEach(async (to) => {
  // 访问 /user/me：未登录回首页，已登录解析成真实 id
  if (to.name === 'user-profile' && to.params.id === 'me') {
    const userStore = useUserStore();
    if (!userStore.isLoggedIn) {
      showToast('请先登录', 'error');
      return { name: 'home' };
    }
    // userInfo 可能尚未加载（直接访问 URL），先 fetchMe
    if (!userStore.userInfo) {
      await userStore.fetchMe();
    }
    if (userStore.userInfo?.id) {
      return { name: 'user-profile', params: { id: userStore.userInfo.id } };
    }
    // fetchMe 失败（token 失效等），回首页
    return { name: 'home' };
  }
  return true;
});

export default router;
