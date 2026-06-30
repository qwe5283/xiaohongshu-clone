import { requireLogin } from '@/composables/useRequireLogin';
import { toggleLikePost } from '@/api/like';
import { toggleCollectPost } from '@/api/collect';

export function usePostActions(userStore, postStore) {
  async function toggleLike({ postId, liked, likeCount, setLocalState }) {
    if (!requireLogin(userStore)) return;

    const wasLiked = liked();
    const previousCount = likeCount();
    const nextLiked = !wasLiked;
    const nextCount = previousCount + (wasLiked ? -1 : 1);

    setLocalState?.({ liked: nextLiked, likeCount: nextCount });
    postStore.updateLike(postId, nextLiked, nextCount);

    try {
      await toggleLikePost(postId);
    } catch (e) {
      setLocalState?.({ liked: wasLiked, likeCount: previousCount });
      postStore.updateLike(postId, wasLiked, previousCount);
    }
  }

  async function toggleCollect({
    postId,
    collected,
    collectCount,
    setLocalState,
  }) {
    if (!requireLogin(userStore)) return;

    const wasCollected = collected();
    const previousCount = collectCount();
    const nextCollected = !wasCollected;
    const nextCount = previousCount + (wasCollected ? -1 : 1);

    setLocalState?.({ collected: nextCollected, collectCount: nextCount });
    postStore.updateCollect(postId, nextCollected, nextCount);

    try {
      await toggleCollectPost(postId);
    } catch (e) {
      setLocalState?.({
        collected: wasCollected,
        collectCount: previousCount,
      });
      postStore.updateCollect(postId, wasCollected, previousCount);
    }
  }

  return {
    toggleLike,
    toggleCollect,
  };
}
