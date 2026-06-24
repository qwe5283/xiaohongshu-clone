<script setup>
import { ref } from 'vue'

const emit = defineEmits(['close'])

const username = ref('')
const password = ref('')

const handleLogin = () => {
  console.log('登录:', username.value, password.value)
  // TODO: 调用登录接口
}

const handleClose = () => {
  emit('close')
}
</script>

<template>
  <div class="fixed inset-0 bg-black/30 z-[100] flex justify-center items-center" @click.self="handleClose">
    <div class="relative bg-white rounded-2xl p-10 w-[400px] max-w-[90%] shadow-[0_10px_30px_rgba(0,0,0,0.2)]">
      <button class="absolute top-4 right-4 bg-transparent border-none text-2xl cursor-pointer text-gray-500 p-0 size-8 flex items-center justify-center rounded-full transition-colors duration-300 hover:bg-gray-100" @click="handleClose">X</button>
      <div class="flex justify-center items-center mb-[30px]">
        <h2 class="text-xl font-bold text-gray-800">登录小红书</h2>
      </div>
      <div class="flex flex-col gap-5">
        <input
          v-model="username"
          type="text"
          class="w-full px-4 py-[14px] border-none bg-[#F7F7F7] rounded-full text-base outline-none transition-colors duration-300 placeholder:text-gray-300 focus:bg-[#EEEEEE]"
          placeholder="输入用户名"
        />
        <input
          v-model="password"
          type="password"
          class="w-full px-4 py-[14px] border-none bg-[#F7F7F7] rounded-full text-base outline-none transition-colors duration-300 placeholder:text-gray-300 focus:bg-[#EEEEEE]"
          placeholder="输入密码"
        />
        <button class="bg-primary text-white border-none p-3 rounded-3xl text-base font-bold cursor-pointer w-full mt-2.5" @click="handleLogin">登录</button>
        <p class="text-center mt-5 text-sm text-gray-400">没有账号? <a href="#" class="text-primary no-underline">前往注册</a></p>
      </div>
    </div>
  </div>
</template>
