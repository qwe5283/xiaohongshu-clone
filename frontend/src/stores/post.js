// 笔记交互状态管理：跨组件同步点赞/收藏状态
import { defineStore } from 'pinia';
import { reactive } from 'vue';

export const usePostStore = defineStore('post', () => {
  // 使用 reactive 确保 Map 内部对象的属性变更也能被追踪
  const postStates = reactive(new Map());

  // 统一将 postId 转为数字，避免路由字符串 "1" 与 API 数字 1 不匹配
  const toKey = (id) => Number(id);

  /**
   * 批量初始化笔记状态（首页加载列表时调用）
   * @param {object[]} posts - adaptPost 后的笔记数组
   */
  function initPosts(posts) {
    if (!posts || !posts.length) return;
    for (const post of posts) {
      const key = toKey(post.id);
      if (!postStates.has(key)) {
        postStates.set(key, {
          liked: !!post.liked,
          likeCount: post.likeCount ?? 0,
          collected: !!post.collected,
          collectCount: post.collectCount ?? 0,
        });
      }
    }
  }

  /**
   * 更新单篇笔记的点赞状态（详情弹窗调用）
   */
  function updateLike(postId, liked, likeCount) {
    const key = toKey(postId);
    const state = postStates.get(key);
    if (state) {
      state.liked = liked;
      state.likeCount = likeCount;
    } else {
      postStates.set(key, {
        liked,
        likeCount,
        collected: false,
        collectCount: 0,
      });
    }
  }

  /**
   * 更新单篇笔记的收藏状态（详情弹窗调用）
   */
  function updateCollect(postId, collected, collectCount) {
    const key = toKey(postId);
    const state = postStates.get(key);
    if (state) {
      state.collected = collected;
      state.collectCount = collectCount;
    } else {
      postStates.set(key, {
        liked: false,
        likeCount: 0,
        collected,
        collectCount,
      });
    }
  }

  /**
   * 获取笔记状态，若未初始化则返回 null
   */
  function getPostState(postId) {
    return postStates.get(toKey(postId)) || null;
  }

  return {
    postStates,
    initPosts,
    updateLike,
    updateCollect,
    getPostState,
  };
});
