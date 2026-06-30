import { ref } from 'vue';
import { adaptPost } from '@/api/post';

export function usePaginatedPosts(fetchPage, options = {}) {
  const pageSize = options.pageSize ?? 20;
  const items = ref([]);
  const pageNum = ref(1);
  const hasMore = ref(true);
  const loading = ref(false);
  const loadingMore = ref(false);
  const error = ref('');

  async function load(reset = false) {
    if (loading.value || loadingMore.value || (!reset && !hasMore.value))
      return;

    if (reset) {
      loading.value = true;
      pageNum.value = 1;
      hasMore.value = true;
      items.value = [];
    } else {
      loadingMore.value = true;
    }
    error.value = '';

    try {
      const page = await fetchPage({
        pageNum: pageNum.value,
        pageSize,
      });
      const adapted = (page?.records || []).map(adaptPost);
      items.value = reset ? adapted : [...items.value, ...adapted];
      options.onItemsLoaded?.(adapted);

      const current = Number(page?.current ?? page?.pageNum ?? pageNum.value);
      const pages = Number(page?.pages ?? 0);
      hasMore.value = pages > 0 ? current < pages : adapted.length === pageSize;
      pageNum.value = current + 1;
    } catch (e) {
      error.value = e.message || '加载失败';
    } finally {
      loading.value = false;
      loadingMore.value = false;
    }
  }

  function resetState() {
    items.value = [];
    pageNum.value = 1;
    hasMore.value = true;
    error.value = '';
    loading.value = false;
    loadingMore.value = false;
  }

  return {
    items,
    pageNum,
    hasMore,
    loading,
    loadingMore,
    error,
    load,
    resetState,
  };
}
