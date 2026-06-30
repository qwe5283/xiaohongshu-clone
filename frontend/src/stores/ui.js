import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useUiStore = defineStore('ui', () => {
  const showLoginModal = ref(false);
  const showPublishModal = ref(false);
  const publishVersion = ref(0);

  function openLoginModal() {
    showLoginModal.value = true;
  }

  function closeLoginModal() {
    showLoginModal.value = false;
  }

  function openPublishModal() {
    showPublishModal.value = true;
  }

  function closePublishModal() {
    showPublishModal.value = false;
  }

  function markPublishSuccess() {
    publishVersion.value += 1;
  }

  return {
    showLoginModal,
    showPublishModal,
    publishVersion,
    openLoginModal,
    closeLoginModal,
    openPublishModal,
    closePublishModal,
    markPublishSuccess,
  };
});
