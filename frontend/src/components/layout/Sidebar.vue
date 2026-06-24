<script setup>
import { ref } from 'vue'
import logo from '../../assets/logo.png'
import homeIcon from '../../assets/icons/home.svg?raw'
import exploreIcon from '../../assets/icons/explore.svg?raw'
import publishIcon from '../../assets/icons/publish.svg?raw'
import notifyIcon from '../../assets/icons/notify.svg?raw'
import moreIcon from '../../assets/icons/more.svg?raw'
import aboutIcon from '../../assets/icons/about.svg?raw'

const emit = defineEmits(['login'])

const activeMenu = ref('home')

const menuItems = [
  { key: 'home', label: '首页', icon: homeIcon },
  { key: 'explore', label: '点点', icon: exploreIcon },
  { key: 'publish', label: '发布', icon: publishIcon },
  { key: 'notify', label: '通知', icon: notifyIcon }
]

const isLoggedIn = ref(false)
const userInfo = ref({
  avatar: 'https://picsum.photos/id/1005/100/100',
  nickname: '我'
})

const setActiveMenu = (key) => {
  activeMenu.value = key
}
</script>

<template>
  <aside class="fixed left-0 top-0 h-screen w-[164px] bg-[#fafafa] pt-8 px-3 flex flex-col z-10">
    <!-- Logo -->
    <div class="mb-[30px] ml-4">
      <img :src="logo" alt="小红书Logo" width="74" height="35" />
    </div>

    <!-- 导航菜单 -->
    <ul class="mt-[14px] list-none flex-1">
      <li
        v-for="item in menuItems"
        :key="item.key"
        class="flex items-center py-3 px-4 mb-2 rounded-3xl cursor-pointer text-[#333] text-base font-bold transition-all duration-200 hover:bg-[#f2f2f2]"
        :class="{ 'bg-gray-100 font-bold text-primary': activeMenu === item.key }"
        @click="setActiveMenu(item.key)"
      >
        <span class="mr-3 flex items-center justify-center size-6 [&>svg]:size-[22px]" v-html="item.icon"></span>
        {{ item.label }}
      </li>

      <!-- 登录后显示用户头像 -->
      <li v-if="isLoggedIn" class="flex items-center py-3 px-4 mb-2 rounded-3xl cursor-pointer text-[#333] text-base font-bold transition-all duration-200 hover:bg-gray-100">
        <span class="mr-3 flex items-center justify-center size-6">
          <img class="size-[22px] rounded-full" :src="userInfo.avatar" />
        </span>
        {{ userInfo.nickname }}
      </li>

      <!-- 登录按钮 -->
      <button v-if="!isLoggedIn" class="bg-primary text-white border-none p-3 rounded-3xl text-base font-bold cursor-pointer w-full" @click="emit('login')">登录</button>
    </ul>

    <!-- 底部菜单 -->
    <ul class="list-none">
      <li class="flex items-center py-3 px-4 mb-2 rounded-3xl cursor-pointer text-[#333] text-base font-bold transition-all duration-200 hover:bg-gray-100">
        <span class="mr-3 flex items-center justify-center size-6 [&>svg]:size-[22px]" v-html="moreIcon"></span>
        更多
      </li>
      <li class="flex items-center py-3 px-4 mb-2 rounded-3xl cursor-pointer text-[#333] text-base font-bold transition-all duration-200 hover:bg-gray-100">
        <span class="mr-3 flex items-center justify-center size-6 [&>svg]:size-[22px]" v-html="aboutIcon"></span>
        关于我们
      </li>
    </ul>
  </aside>
</template>
