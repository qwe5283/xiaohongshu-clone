import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { showToast } from '@/utils/toast'

// 首页直接用现有 MainContent 组件作为路由组件
// ProfilePage、详情弹窗由 App.vue 在路由变化时统一控制显隐
import MainContent from '@/components/layout/MainContent.vue'

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
  // 详情弹窗：复用首页作为底页，PostDetailModal 由 App.vue 监听路由 param 控制
  {
    path: '/post/:id',
    name: 'post-detail',
    component: MainContent,
    meta: { isPostDetail: true },
  },
  // 兜底：未匹配路径回首页
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

// 全局前置守卫
router.beforeEach(async (to) => {
  // 访问 /user/me：未登录回首页，已登录解析成真实 id
  if (to.name === 'user-profile' && to.params.id === 'me') {
    const userStore = useUserStore()
    if (!userStore.isLoggedIn) {
      showToast('请先登录', 'error')
      return { name: 'home' }
    }
    // userInfo 可能尚未加载（直接访问 URL），先 fetchMe
    if (!userStore.userInfo) {
      await userStore.fetchMe()
    }
    if (userStore.userInfo?.id) {
      return { name: 'user-profile', params: { id: userStore.userInfo.id } }
    }
    // fetchMe 失败（token 失效等），回首页
    return { name: 'home' }
  }
  return true
})

export default router
