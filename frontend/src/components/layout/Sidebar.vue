<script setup>
import { ref } from 'vue'

const emit = defineEmits(['login'])

const activeMenu = ref('home')

const menuItems = [
  { key: 'home', label: '首页', icon: 'home' },
  { key: 'explore', label: '点点', icon: 'explore' },
  { key: 'red', label: 'RED', icon: 'red' },
  { key: 'live', label: '直播', icon: 'live' },
  { key: 'publish', label: '发布', icon: 'publish' },
  { key: 'notify', label: '通知', icon: 'notify' }
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
  <aside class="sidebar">
    <!-- Logo -->
    <div class="logo">
      <img src="data:image/png;base64,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" alt="小红书Logo" width="74" height="35" />
    </div>

    <!-- 导航菜单 -->
    <ul class="nav-menu">
      <li
        v-for="item in menuItems"
        :key="item.key"
        class="nav-item"
        :class="{ active: activeMenu === item.key }"
        @click="setActiveMenu(item.key)"
      >
        <span class="nav-icon">
          <!-- 首页图标 -->
          <svg v-if="item.icon === 'home'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
            <path fill="currentColor" d="M20.768 7.934a4 4 0 0 0-1.825-2.153l-5-2.778-.055-.03a4 4 0 0 0-3.83.03l-5 2.778A4 4 0 0 0 3 9.278v7.646q0 .1.005.2A4 4 0 0 0 7 20.924h10q.1 0 .2-.005a4 4 0 0 0 3.8-3.995V9.243a4 4 0 0 0-.232-1.309m-9.776-3.452a2 2 0 0 1 2.016 0l5.2 3.033a2 2 0 0 1 .992 1.728v7.881a2 2 0 0 1-2 2H6.8a2 2 0 0 1-2-2V9.243a2 2 0 0 1 .992-1.728z"></path>
          </svg>
          <!-- 点点图标 -->
          <svg v-else-if="item.icon === 'explore'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
            <path fill="currentColor" d="M9.822 18.999q-.114.01-.222.032.143-.028.315-.032h.049v2h-2c0-.188.028-.358.064-.5a2 2 0 0 0-.069.5H6.647v-2zm8.911-7.059a6.85 6.85 0 0 0-2.029-4.881l-.275-.263C15.039 5.533 13.457 5 11.79 5c-.663 0-1.347.07-2.05.192a5.8 5.8 0 0 0-1.698.593c-1.068.577-1.957 1.51-2.52 2.682a7 7 0 0 0-.585 1.885 16 16 0 0 0-.186 2.264v.022c-.009.384-.016.748-.016 1.13v3.318c-.001.96.003 1.492.05 1.86.368.047.9.053 1.862.053v2l-1.22-.009c-.976-.024-1.583-.116-2.009-.463l-.111-.1c-.43-.43-.537-1.057-.564-2.121l-.009-1.22.001-3.318c0-.404.007-.784.016-1.176q.012-.965.125-1.922l.088-.636c.12-.74.329-1.46.624-2.143l.13-.29c.74-1.54 1.923-2.79 3.374-3.575a7.8 7.8 0 0 1 2.294-.802C10.18 3.084 10.985 3 11.79 3l.427.01c2.13.094 4.17.905 5.9 2.634a8.85 8.85 0 0 1 2.616 6.296l-.005.303a14.5 14.5 0 0 1-.22 2.104l-.059.298a7.8 7.8 0 0 1-.743 1.996c-.784 1.452-2.036 2.633-3.576 3.373a9 9 0 0 1-2.432.756l-.636.087q-.958.113-1.921.124c-.392.008-.718.016-1.176.016L9.964 21v-2a2 2 0 0 1 .874.202 1.9 1.9 0 0 0-.76-.206 56 56 0 0 0 1.023-.013h.012q1.156-.014 2.275-.188a7 7 0 0 0 1.876-.583c1.174-.564 2.106-1.453 2.682-2.52a5.8 5.8 0 0 0 .592-1.687c.123-.71.195-1.396.195-2.064m-7.565 7.461q-.016-.011-.033-.022.016.011.033.022m-.163-.109-.012-.008-.028-.015zm-.927-.297-.113.002-.05.002h-.093q.125-.01.256-.004"></path>
          </svg>
          <!-- RED图标 -->
          <svg v-else-if="item.icon === 'red'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
            <path fill="currentColor" d="M12 20.2a8.2 8.2 0 1 1 0-16.4 8.2 8.2 0 0 1 0 16.4m0 1.8c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10"></path>
            <path fill="currentColor" d="M15.26 12.432a.5.5 0 0 0 0-.864L10.752 8.94a.5.5 0 0 0-.752.432v5.259a.5.5 0 0 0 .752.432z"></path>
          </svg>
          <!-- 直播图标 -->
          <svg v-else-if="item.icon === 'live'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
            <g clip-path="url(#a)">
              <path d="M10.166 3.5h-.133c-1.734 0-3.128 0-4.223.147-1.134.153-2.082.478-2.833 1.23-.752.751-1.077 1.699-1.23 2.833C1.6 8.805 1.6 10.198 1.6 11.933v.133c0 1.735 0 3.128.147 4.223.153 1.134.478 2.082 1.23 2.834.751.751 1.699 1.077 2.833 1.23 1.095.146 2.489.146 4.224.146h.132c1.735 0 3.128 0 4.223-.147 1.135-.152 2.082-.478 2.834-1.23.672-.671 1.005-1.503 1.177-2.49q.46.245.86.395c.607.223 1.3.33 1.964-.049.727-.413.973-1.147 1.076-1.842.1-.674.1-1.562.1-2.612v-1.049c0-1.05 0-1.938-.1-2.611-.103-.696-.35-1.43-1.076-1.843-.664-.378-1.357-.272-1.964-.048a7 7 0 0 0-.86.394c-.172-.987-.505-1.819-1.177-2.49-.752-.752-1.7-1.077-2.834-1.23-1.095-.147-2.488-.147-4.223-.147M4.249 6.15c.362-.362.866-.593 1.801-.719.961-.13 2.233-.131 4.05-.131s3.088.002 4.05.131c.934.126 1.439.357 1.8.718.47.47.714 1.174.802 2.729l.083 1.477 1.274-.753c.821-.486 1.366-.79 1.774-.94.194-.072.31-.09.373-.091.049-.001.065.007.08.015.012.008.03.018.06.075.038.075.086.214.124.467.078.525.08 1.278.08 2.42v.903c0 1.142-.002 1.895-.08 2.42-.038.254-.085.393-.124.467-.03.057-.048.068-.061.075-.014.008-.03.016-.079.015a1.2 1.2 0 0 1-.373-.09c-.408-.15-.953-.454-1.774-.94l-1.274-.754-.083 1.477c-.088 1.555-.333 2.26-.802 2.729-.361.361-.866.592-1.8.718-.962.13-2.234.131-4.05.131-1.817 0-3.089-.002-4.05-.13-.935-.127-1.44-.358-1.8-.72-.362-.36-.593-.865-.719-1.8-.129-.96-.13-2.233-.13-4.05s.001-3.088.13-4.049c.126-.935.357-1.44.718-1.8M11.7 9.123c.636.37 1.177.685 1.574.98.404.298.809.679.974 1.237.127.43.127.888 0 1.318-.165.557-.57.938-.974 1.237-.397.294-.938.609-1.574.98l-.089.05c-.643.376-1.19.694-1.645.896-.463.206-.999.371-1.569.236a2.32 2.32 0 0 1-1.148-.66c-.404-.425-.53-.97-.586-1.474-.054-.496-.054-1.128-.054-1.873v-.103c0-.744 0-1.377.054-1.872.056-.504.182-1.05.586-1.474.31-.327.71-.557 1.148-.66.57-.136 1.106.03 1.569.235.455.202 1.002.52 1.645.895zm-2.464.698c-.305-.135-.41-.13-.423-.13a.53.53 0 0 0-.26.15c-.008.011-.064.1-.1.43-.043.385-.044.918-.044 1.728s.001 1.342.044 1.727c.036.33.092.419.1.43.07.074.16.126.26.15.014.001.118.005.423-.13.353-.157.814-.424 1.514-.832.692-.403 1.146-.668 1.454-.896.287-.212.319-.302.318-.302v.001a.53.53 0 0 0 0-.297c.004.015-.009-.072-.318-.3-.308-.228-.762-.494-1.454-.897-.7-.408-1.16-.675-1.514-.832"></path>
            </g>
          </svg>
          <!-- 发布图标 -->
          <svg v-else-if="item.icon === 'publish'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
            <path fill="currentColor" d="M16 4.8A4.2 4.2 0 0 1 20.2 9v6a4.2 4.2 0 0 1-4.2 4.2H8A4.2 4.2 0 0 1 3.8 15V9A4.2 4.2 0 0 1 8 4.8zM8 3a6 6 0 0 0-6 6v6a6 6 0 0 0 6 6h8a6 6 0 0 0 6-6V9a6 6 0 0 0-6-6z"></path>
            <path fill="currentColor" d="M11.1 15a.9.9 0 1 0 1.8 0v-2.1H15a.9.9 0 1 0 0-1.8h-2.1V9a.9.9 0 1 0-1.8 0v2.1H9a.9.9 0 0 0 0 1.8h2.1z"></path>
          </svg>
          <!-- 通知图标 -->
          <svg v-else-if="item.icon === 'notify'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
            <path fill="currentColor" d="M20.16 13.9v-2.7c0-3.18-1.99-6.07-4.98-7.3C14.58 2.75 13.35 2 12 2s-2.58.75-3.18 1.9c-3 1.23-4.98 4.12-4.98 7.3v2.69C2.77 14.26 2 15.26 2 16.42c0 1.48 1.24 2.68 2.76 2.68H8.5c.28 1.64 1.73 2.9 3.5 2.9s3.22-1.26 3.5-2.9h3.74c1.52 0 2.76-1.2 2.76-2.68 0-1.16-.77-2.15-1.84-2.52M12 20.2c-.74 0-1.36-.46-1.6-1.1h3.19c-.23.64-.85 1.1-1.59 1.1m7.24-2.9H4.76c-.5 0-.9-.39-.9-.88 0-.48.41-.88.91-.88.51 0 .93-.4.93-.9V11.2c0-2.56 1.67-4.88 4.14-5.76.26-.09.46-.29.56-.54.24-.66.89-1.1 1.61-1.1s1.37.44 1.61 1.1c.09.25.3.45.56.54 2.48.88 4.14 3.19 4.14 5.76v3.44c0 .5.42.9.94.9.5 0 .9.39.9.88a.91.91 0 0 1-.92.88"></path>
          </svg>
        </span>
        {{ item.label }}
      </li>

      <!-- 登录后显示用户头像 -->
      <li v-if="isLoggedIn" class="nav-item">
        <span class="nav-icon">
          <img class="nav-icon-avatar" :src="userInfo.avatar" />
        </span>
        {{ userInfo.nickname }}
      </li>

      <!-- 登录按钮 -->
      <button v-if="!isLoggedIn" class="btn-login" @click="emit('login')">登录</button>
    </ul>

    <!-- 底部菜单 -->
    <ul class="sidebar-footer-menu">
      <li class="nav-item">
        <span class="nav-icon">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20">
            <path fill="currentColor" d="M3.25 4.167h13.5a.75.75 0 0 1 0 1.5H3.25a.75.75 0 0 1 0-1.5M3.25 9.25h13.5a.75.75 0 0 1 0 1.5H3.25a.75.75 0 0 1 0-1.5M16.75 14.333H3.25a.75.75 0 0 0 0 1.5h13.5a.75.75 0 0 0 0-1.5"></path>
          </svg>
        </span>
        更多
      </li>
      <li class="nav-item">
        <span class="nav-icon">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 22 22">
            <path fill="currentColor" d="M12.1 6.646a1.146 1.146 0 1 1-2.292 0 1.146 1.146 0 0 1 2.292 0M11.95 8.896c.163.153.24.375.21.595l-.817 5.437.414-.099a.688.688 0 0 1 .32 1.338l-1.396.333a.687.687 0 0 1-.84-.764l.822-5.476-.445.088A.687.687 0 1 1 9.949 9l1.395-.278a.69.69 0 0 1 .607.174"></path>
            <path fill="currentColor" d="M20.167 11a9.167 9.167 0 1 1-18.334 0 9.167 9.167 0 0 1 18.334 0m-1.375 0a7.792 7.792 0 1 0-15.584 0 7.792 7.792 0 0 0 15.584 0"></path>
          </svg>
        </span>
        关于我们
      </li>
    </ul>
  </aside>
</template>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  height: 100vh;
  position: fixed;
  left: 0;
  top: 0;
  background: #fafafa;
  padding: 32px 12px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-color);
  z-index: 10;
}

.logo {
  margin-bottom: 30px;
  margin-left: 16px;
}

.nav-menu {
  margin-top: 14px;
  list-style: none;
  flex: 1;
}

.sidebar-footer-menu {
  list-style: none;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  margin-bottom: 8px;
  border-radius: 24px;
  cursor: pointer;
  color: var(--text-main);
  font-size: 16px;
  font-weight: bold;
  transition: all 0.2s;
}

.nav-item:hover {
  background: #f2f2f2;
}

.nav-item.active {
  background: #f2f2f2;
  font-weight: bold;
  color: var(--primary-red);
}

.nav-icon {
  margin-right: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
}

.nav-icon svg {
  width: 22px;
  height: 22px;
  fill: currentColor;
}

.nav-icon-avatar {
  width: 22px;
  height: 22px;
  border-radius: 11px;
}
</style>
