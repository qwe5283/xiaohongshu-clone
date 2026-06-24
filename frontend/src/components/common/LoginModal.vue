<script setup>
import { ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { showToast } from '@/utils/toast'
import closeIcon from '../../assets/icons/close.svg?raw'

const emit = defineEmits(['close', 'login-success'])

const userStore = useUserStore()

// 当前模式：'login' | 'register'
const mode = ref('login')

// 登录表单
const loginForm = ref({ username: '', password: '' })
// 注册表单
const registerForm = ref({ username: '', password: '', nickname: '', phone: '' })

const loading = ref(false)
const errorMsg = ref('')

const switchMode = (m) => {
  mode.value = m
  errorMsg.value = ''
}

const validateLogin = () => {
  if (!loginForm.value.username.trim()) {
    errorMsg.value = '请输入用户名'
    return false
  }
  if (!loginForm.value.password) {
    errorMsg.value = '请输入密码'
    return false
  }
  return true
}

const validateRegister = () => {
  const f = registerForm.value
  if (!f.username.trim() || f.username.trim().length < 3 || f.username.trim().length > 20) {
    errorMsg.value = '用户名长度为3-20个字符'
    return false
  }
  if (!f.password || f.password.length < 6 || f.password.length > 20) {
    errorMsg.value = '密码长度为6-20个字符'
    return false
  }
  if (f.phone && !/^1[3-9]\d{9}$/.test(f.phone)) {
    errorMsg.value = '手机号格式不正确'
    return false
  }
  return true
}

const handleLogin = async () => {
  errorMsg.value = ''
  if (!validateLogin()) return
  loading.value = true
  try {
    await userStore.login({
      username: loginForm.value.username.trim(),
      password: loginForm.value.password,
    })
    showToast('登录成功', 'success')
    emit('login-success')
  } catch (e) {
    // request 拦截器已弹 toast，这里把信息显示在表单下方
    errorMsg.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  errorMsg.value = ''
  if (!validateRegister()) return
  loading.value = true
  try {
    await userStore.register({
      username: registerForm.value.username.trim(),
      password: registerForm.value.password,
      nickname: registerForm.value.nickname.trim() || undefined,
      phone: registerForm.value.phone.trim() || undefined,
    })
    showToast('注册成功，请登录', 'success')
    // 注册成功后切回登录，并预填用户名
    loginForm.value.username = registerForm.value.username.trim()
    loginForm.value.password = ''
    switchMode('login')
  } catch (e) {
    errorMsg.value = e.message || '注册失败'
  } finally {
    loading.value = false
  }
}

const handleClose = () => {
  if (loading.value) return
  emit('close')
}
</script>

<template>
  <div class="fixed inset-0 bg-black/30 z-[100] flex justify-center items-center" @click.self="handleClose">
    <div class="relative bg-white rounded-2xl p-10 w-[400px] max-w-[90%] shadow-[0_10px_30px_rgba(0,0,0,0.2)]">
      <button
        class="absolute top-4 right-4 bg-transparent border-none text-2xl cursor-pointer text-gray-500 p-0 size-8 flex items-center justify-center rounded-full transition-colors duration-300 hover:bg-gray-100"
        @click="handleClose"
      >
        <span class="[&>svg]:size-4 text-gray-500" v-html="closeIcon"></span>
      </button>

      <div class="flex justify-center items-center mb-[30px]">
        <h2 class="text-xl font-bold text-gray-800">{{ mode === 'login' ? '登录小红书' : '注册小红书' }}</h2>
      </div>

      <!-- 错误提示 -->
      <div v-if="errorMsg" class="mb-3 text-sm text-primary text-center bg-red-50 rounded-lg py-2 px-3">
        {{ errorMsg }}
      </div>

      <!-- 登录表单 -->
      <div v-if="mode === 'login'" class="flex flex-col gap-5">
        <input v-model="loginForm.username" type="text"
          class="w-full px-4 py-[14px] border-none bg-[#F7F7F7] rounded-full text-base outline-none transition-colors duration-300 placeholder:text-[#BBBBBB] focus:bg-[#EEEEEE]"
          placeholder="输入用户名" @keyup.enter="handleLogin" />
        <input v-model="loginForm.password" type="password"
          class="w-full px-4 py-[14px] border-none bg-[#F7F7F7] rounded-full text-base outline-none transition-colors duration-300 placeholder:text-[#BBBBBB] focus:bg-[#EEEEEE]"
          placeholder="输入密码" @keyup.enter="handleLogin" />
        <button
          class="bg-primary text-white border-none p-3 rounded-3xl text-base font-bold cursor-pointer w-full mt-2.5 disabled:opacity-60 disabled:cursor-not-allowed"
          :disabled="loading"
          @click="handleLogin"
        >{{ loading ? '登录中...' : '登录' }}</button>
        <p class="text-center mt-5 text-sm text-gray-400">
          没有账号?
          <a href="javascript:void(0)" class="text-primary no-underline" @click="switchMode('register')">前往注册</a>
        </p>
      </div>

      <!-- 注册表单 -->
      <div v-else class="flex flex-col gap-5">
        <input v-model="registerForm.username" type="text"
          class="w-full px-4 py-[14px] border-none bg-[#F7F7F7] rounded-full text-base outline-none transition-colors duration-300 placeholder:text-[#BBBBBB] focus:bg-[#EEEEEE]"
          placeholder="设置用户名（3-20字符）" />
        <input v-model="registerForm.password" type="password"
          class="w-full px-4 py-[14px] border-none bg-[#F7F7F7] rounded-full text-base outline-none transition-colors duration-300 placeholder:text-[#BBBBBB] focus:bg-[#EEEEEE]"
          placeholder="设置密码（6-20字符）" />
        <input v-model="registerForm.nickname" type="text"
          class="w-full px-4 py-[14px] border-none bg-[#F7F7F7] rounded-full text-base outline-none transition-colors duration-300 placeholder:text-[#BBBBBB] focus:bg-[#EEEEEE]"
          placeholder="昵称（选填）" />
        <input v-model="registerForm.phone" type="text"
          class="w-full px-4 py-[14px] border-none bg-[#F7F7F7] rounded-full text-base outline-none transition-colors duration-300 placeholder:text-[#BBBBBB] focus:bg-[#EEEEEE]"
          placeholder="手机号（选填）" />
        <button
          class="bg-primary text-white border-none p-3 rounded-3xl text-base font-bold cursor-pointer w-full mt-2.5 disabled:opacity-60 disabled:cursor-not-allowed"
          :disabled="loading"
          @click="handleRegister"
        >{{ loading ? '注册中...' : '注册' }}</button>
        <p class="text-center mt-5 text-sm text-gray-400">
          已有账号?
          <a href="javascript:void(0)" class="text-primary no-underline" @click="switchMode('login')">前往登录</a>
        </p>
      </div>
    </div>
  </div>
</template>
