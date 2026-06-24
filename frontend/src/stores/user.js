// 用户状态管理：token 持久化到 localStorage，userInfo 内存维护
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import { getToken, setToken, clearToken } from '@/api/request'

export const useUserStore = defineStore('user', () => {
  // token 初始化时从 localStorage 恢复（裸 JWT）
  const token = ref(getToken())
  // userInfo: UserVO | null
  const userInfo = ref(null)

  const isLoggedIn = computed(() => !!token.value)

  /**
   * 登录：调接口 → 存 token → 存 userInfo
   * @param {{username:string, password:string}} payload
   */
  async function login(payload) {
    const data = await authApi.login(payload)
    // 后端返回的 token 是裸 JWT，前端发请求时由拦截器拼 Bearer
    token.value = data.token
    setToken(data.token)
    userInfo.value = data.user
    return data
  }

  /**
   * 注册：仅创建账号，不自动登录（让用户手动登录）
   * @param {object} payload
   * @returns {Promise<object>} UserVO
   */
  async function register(payload) {
    return authApi.register(payload)
  }

  /**
   * 恢复登录态：用已有 token 调 /me 校验并取回 userInfo
   * 失败（401 等）会清掉 token
   */
  async function fetchMe() {
    if (!token.value) return null
    try {
      const user = await authApi.getMe()
      userInfo.value = user
      return user
    } catch (e) {
      // 拦截器已清 token，这里同步本地 state
      token.value = ''
      userInfo.value = null
      return null
    }
  }

  /**
   * 登出：清 token + userInfo
   */
  function logout() {
    token.value = ''
    userInfo.value = null
    clearToken()
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    login,
    register,
    fetchMe,
    logout,
  }
})
