<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import logo from '../../assets/logo.png'
import homeIcon from '../../assets/icons/home.svg?raw'
import exploreIcon from '../../assets/icons/explore.svg?raw'
import publishIcon from '../../assets/icons/publish.svg?raw'
import notifyIcon from '../../assets/icons/notify.svg?raw'
import moreIcon from '../../assets/icons/more.svg?raw'
import aboutIcon from '../../assets/icons/about.svg?raw'
import { useUserStore } from '@/stores/user'
import { showToast } from '@/utils/toast'

const props = defineProps({
  currentPage: {
    type: String,
    default: 'home'
  }
})

const emit = defineEmits(['login', 'navigate-home', 'navigate-profile', 'publish'])

const userStore = useUserStore()

// 登录态从 store 派生，刷新后由 App.vue 调 /me 恢复
const isLoggedIn = computed(() => userStore.isLoggedIn)
const userInfo = computed(() => userStore.userInfo)

// 默认头像：后端用户若无头像，用内置 SVG 兜底
const defaultAvatar =
  'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23eee"/><text x="50%" y="55%" text-anchor="middle" font-size="18" fill="%23bbb">U</text></svg>'
const avatarUrl = computed(() => userInfo.value?.avatar || defaultAvatar)
const nickname = computed(() => userInfo.value?.nickname || '我')

const menuItems = [
  { key: 'home', label: '首页', icon: homeIcon },
  { key: 'explore', label: '点点', icon: exploreIcon },
  { key: 'publish', label: '发布', icon: publishIcon },
  { key: 'notify', label: '通知', icon: notifyIcon }
]

// 当前激活菜单：以路由 name 为准，保证刷新/直接访问 URL 时高亮正确
const activeMenu = computed(() => {
  if (props.currentPage === 'user-profile') return 'profile'
  if (props.currentPage === 'home') return 'home'
  return props.currentPage || 'home'
})

const setActiveMenu = (key) => {
  if (key === 'home') {
    emit('navigate-home')
  } else if (key === 'publish') {
    emit('publish')
  }
}

const handleLogout = () => {
  userStore.logout()
  showToast('已退出登录', 'info')
  // 退出后统一回首页，避免停留在需要登录态的页面
  emit('navigate-home')
  showMoreMenu.value = false
}

// 更多菜单状态
const showMoreMenu = ref(false)
const moreMenuRef = ref(null)

const toggleMoreMenu = () => {
  showMoreMenu.value = !showMoreMenu.value
}

const closeMoreMenu = () => {
  showMoreMenu.value = false
}

// 点击外部区域关闭菜单
const handleClickOutside = (event) => {
  if (moreMenuRef.value && !moreMenuRef.value.contains(event.target)) {
    closeMoreMenu()
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})

const handleUserAgreement = () => {
  showToast('用户协议功能开发中', 'info')
  closeMoreMenu()
}

const handleCommunityGuidelines = () => {
  showToast('社区规范功能开发中', 'info')
  closeMoreMenu()
}
</script>

<template>
  <aside class="fixed left-0 top-0 h-screen w-41 bg-[#fafafa] pt-8 px-3 flex flex-col z-10">
    <!-- Logo -->
    <div class="mb-7.5 ml-4">
      <img :src="logo" alt="小红书Logo" width="74" height="35" />
    </div>

    <!-- 导航菜单 -->
    <ul class="mt-3.5 list-none flex-1">
      <li
        v-for="item in menuItems"
        :key="item.key"
        class="flex items-center py-3 px-4 mb-2 rounded-3xl cursor-pointer text-[#333] text-base font-bold transition-all duration-200 hover:bg-[#f2f2f2]"
        :class="{ 'bg-[#F2F2F2] font-bold': activeMenu === item.key }"
        @click="setActiveMenu(item.key)"
      >
        <span class="mr-3 flex items-center justify-center size-6 [&>svg]:size-5.5" v-html="item.icon"></span>
        {{ item.label }}
      </li>

      <!-- 登录后显示用户头像 -->
      <li v-if="isLoggedIn"
        class="flex items-center py-3 px-4 mb-2 rounded-3xl cursor-pointer text-[#333] text-base font-bold transition-all duration-200 hover:bg-gray-100"
        :class="{ 'bg-[#F2F2F2] font-bold': props.currentPage === 'user-profile' }"
        @click="emit('navigate-profile')">
        <span class="mr-3 flex items-center justify-center size-6">
          <img class="size-5.5 rounded-full object-cover" :src="avatarUrl" />
        </span>
        我
      </li>

      <!-- 登录按钮 -->
      <button v-if="!isLoggedIn" class="bg-primary text-white border-none p-3 rounded-3xl text-base font-bold cursor-pointer w-full" @click="emit('login')">登录</button>
    </ul>

    <!-- 底部菜单 -->
    <ul class="list-none">
      <!-- 更多：带弹出菜单 -->
      <li ref="moreMenuRef" class="relative">
        <div
          class="flex items-center py-3 px-4 mb-2 rounded-3xl cursor-pointer text-[#333] text-base font-bold transition-all duration-200 hover:bg-[#F2F2F2]"
          @click="toggleMoreMenu">
          <span class="mr-3 flex items-center justify-center size-6 [&>svg]:size-5.5" v-html="moreIcon"></span>
          更多
        </div>

        <!-- 弹出菜单 -->
        <div v-if="showMoreMenu"
          class="absolute bottom-full left-0 mb-2 w-48 bg-white rounded-lg shadow-lg border border-gray-200 py-2 z-50">
          <div class="px-4 py-2 text-sm text-gray-500 font-medium border-b border-gray-100">
            更多选项
          </div>
          <button
            class="w-full text-left px-4 py-2.5 text-sm text-gray-700 hover:bg-gray-50 transition-colors duration-150 flex items-center"
            @click="handleUserAgreement">
            <svg class="w-4 h-4 mr-3 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
            </svg>
            用户协议
          </button>
          <button
            class="w-full text-left px-4 py-2.5 text-sm text-gray-700 hover:bg-gray-50 transition-colors duration-150 flex items-center"
            @click="handleCommunityGuidelines">
            <svg class="w-4 h-4 mr-3 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"></path>
            </svg>
            社区规范
          </button>
          <div v-if="isLoggedIn" class="border-t border-gray-100 mt-1 pt-1">
            <button
              class="w-full text-left px-4 py-2.5 text-sm text-red-600 hover:bg-red-50 transition-colors duration-150 flex items-center"
              @click="handleLogout">
              <svg class="w-4 h-4 mr-3 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"></path>
              </svg>
              退出登录
            </button>
          </div>
        </div>
      </li>
      <li class="flex items-center py-3 px-4 mb-2 rounded-3xl cursor-pointer text-[#333] text-base font-bold transition-all duration-200 hover:bg-[#F2F2F2]">
        <span class="mr-3 flex items-center justify-center size-6 [&>svg]:size-5.5" v-html="aboutIcon"></span>
        关于我们
      </li>
    </ul>
  </aside>
</template>
