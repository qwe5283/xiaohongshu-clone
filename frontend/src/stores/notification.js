import { defineStore } from 'pinia';
import { ref } from 'vue';
import {
  getUnreadNotificationCount,
  markAllNotificationsAsRead,
} from '@/api/notification';
import { useUserStore } from '@/stores/user';

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0);
  let timer = null;

  async function fetchUnreadCount() {
    const userStore = useUserStore();
    if (!userStore.isLoggedIn) {
      unreadCount.value = 0;
      return;
    }

    try {
      const data = await getUnreadNotificationCount();
      unreadCount.value = data?.unreadCount ?? 0;
    } catch (e) {
      unreadCount.value = 0;
    }
  }

  function startPolling() {
    const userStore = useUserStore();
    if (timer || !userStore.isLoggedIn) return;
    fetchUnreadCount();
    timer = window.setInterval(fetchUnreadCount, 15000);
  }

  function stopPolling() {
    if (timer) {
      window.clearInterval(timer);
      timer = null;
    }
    unreadCount.value = 0;
  }

  async function markAllRead() {
    await markAllNotificationsAsRead();
    unreadCount.value = 0;
  }

  return {
    unreadCount,
    fetchUnreadCount,
    startPolling,
    stopPolling,
    markAllRead,
  };
});
