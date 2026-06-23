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
  <div class="login-modal-overlay" @click.self="handleClose">
    <div class="login-modal-content">
      <button class="login-modal-close" @click="handleClose">X</button>
      <div class="login-modal-header">
        <h2>登录小红书</h2>
      </div>
      <div class="login-modal-body">
        <input
          v-model="username"
          type="text"
          class="login-input"
          placeholder="输入用户名"
        />
        <input
          v-model="password"
          type="password"
          class="login-input"
          placeholder="输入密码"
        />
        <button class="btn-login login-btn" @click="handleLogin">登录</button>
        <p class="register-hint">没有账号? <a href="#">前往注册</a></p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.3);
  z-index: 100;
  display: flex;
  justify-content: center;
  align-items: center;
}

.login-modal-content {
  position: relative;
  background: #fff;
  border-radius: 16px;
  padding: 40px;
  width: 400px;
  max-width: 90%;
  box-shadow: 0 10px 30px rgba(0,0,0,0.2);
}

.login-modal-header {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 30px;
}

.login-modal-header h2 {
  font-size: 20px;
  font-weight: bold;
  color: var(--text-main);
}

.login-modal-close {
  position: absolute;
  top: 16px;
  right: 16px;
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: var(--text-secondary);
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: background-color 0.3s;
}

.login-modal-close:hover {
  background-color: #f0f0f0;
}

.login-modal-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.login-input {
  width: 100%;
  padding: 14px 16px;
  border: none;
  background: #F7F7F7;
  border-radius: 999px;
  font-size: 16px;
  outline: none;
  transition: background-color 0.3s;
}

.login-input:focus {
  background-color: #EEEEEE;
}

.login-input::placeholder {
  color: lightgray;
}

.login-btn {
  width: 100%;
  margin-top: 10px;
}

.register-hint {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: var(--text-light);
}

.register-hint a {
  color: var(--primary-red);
  text-decoration: none;
}
</style>
