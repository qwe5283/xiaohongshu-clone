import { ref } from 'vue';
import { getFollowStatus, toggleFollow } from '@/api/follow';
import { showToast } from '@/utils/toast';
import { requireLogin } from '@/composables/useRequireLogin';

export function useFollowToggle(userStore, options = {}) {
  const isFollowed = ref(false);

  async function loadFollowStatus(userId) {
    if (!userStore.isLoggedIn || !userId) return;
    try {
      const res = await getFollowStatus(userId);
      isFollowed.value = !!res.followed;
    } catch (e) {
      isFollowed.value = false;
    }
  }

  async function toggle(userId) {
    if (!userId || !requireLogin(userStore)) return;

    const wasFollowed = isFollowed.value;
    isFollowed.value = !wasFollowed;
    options.onOptimisticChange?.(isFollowed.value, wasFollowed);

    try {
      await toggleFollow(userId);
      showToast(wasFollowed ? '已取消关注' : '关注成功', 'success');
    } catch (e) {
      isFollowed.value = wasFollowed;
      options.onRollback?.(wasFollowed);
    }
  }

  return {
    isFollowed,
    loadFollowStatus,
    toggleFollow: toggle,
  };
}
