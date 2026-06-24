<script setup>
import { computed } from 'vue'
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

const emit = defineEmits(['login', 'navigate-home', 'navigate-profile'])

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
  }
}

const handleLogout = () => {
  userStore.logout()
  showToast('已退出登录', 'info')
  // 退出后统一回首页，避免停留在需要登录态的页面
  emit('navigate-home')
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
      <!-- 退出登录：仅登录态显示 -->
      <li v-if="isLoggedIn"
        class="flex items-center py-3 px-4 mb-2 rounded-3xl cursor-pointer text-[#333] text-base font-bold transition-all duration-200 hover:bg-[#F2F2F2]"
        @click="handleLogout">
        <span class="mr-3 flex items-center justify-center size-6 [&>svg]:size-5.5" v-html="moreIcon"></span>
        退出登录
      </li>
      <li class="flex items-center py-3 px-4 mb-2 rounded-3xl cursor-pointer text-[#333] text-base font-bold transition-all duration-200 hover:bg-[#F2F2F2]">
        <span class="mr-3 flex items-center justify-center size-6 [&>svg]:size-5.5" v-html="moreIcon"></span>
        更多
      </li>
      <li class="flex items-center py-3 px-4 mb-2 rounded-3xl cursor-pointer text-[#333] text-base font-bold transition-all duration-200 hover:bg-[#F2F2F2]">
        <span class="mr-3 flex items-center justify-center size-6 [&>svg]:size-5.5" v-html="aboutIcon"></span>
        关于我们
      </li>
    </ul>
  </aside>
</template>
