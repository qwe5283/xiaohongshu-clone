import { computed, ref, shallowRef, watch } from 'vue';

export function usePostDetailRoute(route, router) {
  const selectedPostId = ref(null);
  const postOpenedInApp = ref(false);
  const backgroundRouteLocation = shallowRef(null);

  const showPostDetail = computed(() => selectedPostId.value != null);
  const backgroundResolvedRoute = computed(() => {
    if (!backgroundRouteLocation.value) return null;
    return router.resolve(backgroundRouteLocation.value.fullPath);
  });
  const displayRoute = computed(() => backgroundResolvedRoute.value || route);
  const currentSidebarPage = computed(
    () => backgroundResolvedRoute.value?.name || route.name,
  );

  function openPostDetail(postId) {
    selectedPostId.value = postId;
    postOpenedInApp.value = true;
    backgroundRouteLocation.value = {
      fullPath: route.fullPath,
    };
    const source = route.name === 'user-profile' ? 'user' : 'feed';
    router.push({
      name: 'post-page',
      params: { id: postId },
      query: { source },
      state: {
        modal: true,
        background: backgroundRouteLocation.value,
      },
    });
  }

  function closePostDetail() {
    if (selectedPostId.value === null) return;
    selectedPostId.value = null;
    if (route.name === 'post-page' && window.history.state?.modal) {
      if (postOpenedInApp.value) {
        router.back();
      } else {
        router.replace(backgroundRouteLocation.value?.fullPath || '/');
      }
    } else {
      backgroundRouteLocation.value = null;
    }
    postOpenedInApp.value = false;
  }

  function closePostDetailSilent() {
    if (selectedPostId.value === null) return;
    selectedPostId.value = null;
    postOpenedInApp.value = false;
    backgroundRouteLocation.value = null;
  }

  watch(
    () => ({
      name: route.name,
      postId: route.params.id,
      stateKey: window.history.state?.key,
      modal: window.history.state?.modal,
      background: window.history.state?.background,
    }),
    (to) => {
      if (to.name === 'post-page' && to.postId && to.modal && to.background) {
        selectedPostId.value = to.postId;
        backgroundRouteLocation.value = to.background;
      } else {
        selectedPostId.value = null;
        backgroundRouteLocation.value = null;
        postOpenedInApp.value = false;
      }
    },
    { immediate: true },
  );

  return {
    selectedPostId,
    showPostDetail,
    displayRoute,
    currentSidebarPage,
    openPostDetail,
    closePostDetail,
    closePostDetailSilent,
  };
}
