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
  {
    path: '/profile',
    name: 'profile',
    // 路由级代码分割
    component: () => import('@/components/user/ProfilePage.vue'),
    meta: { requiresAuth: true },
  },
  // 详情弹窗：复用首页/个人中心作为底页，PostDetailModal 由 App.vue 监听路由 param 控制
  {
    path: '/post/:id',
    name: 'post-detail',
    // 底层仍渲染首页组件，弹窗叠加其上；这样关闭弹窗 router.back 后回到来源页
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

// 全局前置守卫：仅校验 requiresAuth
router.beforeEach((to) => {
  if (to.meta.requiresAuth) {
    const userStore = useUserStore()
    if (!userStore.isLoggedIn) {
      showToast('请先登录', 'error')
      return { name: 'home' }
    }
  }
  return true
})

export default router
