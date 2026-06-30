import { showToast } from '@/utils/toast';

export function requireLogin(userStore) {
  if (userStore.isLoggedIn) return true;
  showToast('请先登录', 'error');
  return false;
}
