<script setup>
import { ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { showToast } from '@/utils/toast'
import closeIcon from '../../assets/icons/close.svg?raw'
import BaseButton from './BaseButton.vue'
import BaseInput from './BaseInput.vue'
import BaseModal from './BaseModal.vue'

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
  <BaseModal width-class="w-[400px]" panel-class="p-10 max-w-[90%]" @close="handleClose">
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
        <BaseInput v-model="loginForm.username" type="text"
          placeholder="输入用户名" @keyup.enter="handleLogin" />
        <BaseInput v-model="loginForm.password" type="password"
          placeholder="输入密码" @keyup.enter="handleLogin" />
        <BaseButton
          class="mt-2.5"
          block
          :disabled="loading"
          @click="handleLogin"
        >{{ loading ? '登录中...' : '登录' }}</BaseButton>
        <p class="text-center mt-5 text-sm text-gray-400">
          没有账号?
          <a href="javascript:void(0)" class="text-primary no-underline" @click="switchMode('register')">前往注册</a>
        </p>
      </div>

      <!-- 注册表单 -->
      <div v-else class="flex flex-col gap-5">
        <BaseInput v-model="registerForm.username" type="text"
          placeholder="设置用户名（3-20字符）" />
        <BaseInput v-model="registerForm.password" type="password"
          placeholder="设置密码（6-20字符）" />
        <BaseInput v-model="registerForm.nickname" type="text"
          placeholder="昵称（选填）" />
        <BaseInput v-model="registerForm.phone" type="text"
          placeholder="手机号（选填）" />
        <BaseButton
          class="mt-2.5"
          block
          :disabled="loading"
          @click="handleRegister"
        >{{ loading ? '注册中...' : '注册' }}</BaseButton>
        <p class="text-center mt-5 text-sm text-gray-400">
          已有账号?
          <a href="javascript:void(0)" class="text-primary no-underline" @click="switchMode('login')">前往登录</a>
        </p>
      </div>
  </BaseModal>
</template>
