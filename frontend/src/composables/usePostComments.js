import { reactive, ref } from 'vue';
import { createComment, getReplies } from '@/api/comment';
import { toggleLikeComment } from '@/api/like';
import { showToast } from '@/utils/toast';
import { requireLogin } from '@/composables/useRequireLogin';

export function usePostComments(userStore, postRef, postIdRef) {
  const comments = ref([]);
  const repliesMap = reactive({});
  const repliesLoadedMap = reactive({});
  const showRepliesMap = reactive({});
  const commentText = ref('');
  const replyingTo = ref(null);
  const replyText = ref('');
  const submitting = ref(false);
  const submittingReply = ref(false);

  function setComments(nextComments) {
    comments.value = nextComments || [];
    comments.value
      .filter((comment) => (comment.replyCount || 0) > 0)
      .forEach((comment) => loadReplyPreview(comment));
  }

  function applyCommentLike(comment) {
    const liked = !comment.liked;
    const currentCount = comment.likeCount || 0;
    comment.liked = liked;
    comment.likeCount = Math.max(currentCount + (liked ? 1 : -1), 0);
  }

  async function toggleCommentLike(comment) {
    if (!requireLogin(userStore)) return;
    if (!comment) return;

    const previousLiked = !!comment.liked;
    const previousLikeCount = comment.likeCount || 0;
    applyCommentLike(comment);

    try {
      const result = await toggleLikeComment(comment.id);
      const nextLiked = !!result?.liked;
      comment.liked = nextLiked;
      comment.likeCount = Math.max(
        previousLikeCount +
          (nextLiked === previousLiked ? 0 : nextLiked ? 1 : -1),
        0,
      );
    } catch (e) {
      comment.liked = previousLiked;
      comment.likeCount = previousLikeCount;
    }
  }

  async function submitComment() {
    if (!requireLogin(userStore)) return;
    const text = commentText.value.trim();
    if (!text) return;

    submitting.value = true;
    try {
      const newComment = await createComment({
        postId: postIdRef.value,
        content: text,
        parentId: 0,
      });
      newComment.liked = false;
      comments.value.unshift(newComment);
      postRef.value.commentCount += 1;
      commentText.value = '';
      showToast('评论成功', 'success');
    } catch (e) {
      // request 拦截器已处理错误
    } finally {
      submitting.value = false;
    }
  }

  function cancelReply() {
    replyingTo.value = null;
    replyText.value = '';
  }

  function handleReplyClick(comment, rootComment = comment) {
    if (replyingTo.value?.commentId === comment.id) {
      cancelReply();
      return;
    }

    replyingTo.value = {
      commentId: comment.id,
      rootCommentId: rootComment.id,
      userId: comment.userId,
      nickname: comment.userNickname,
    };
    replyText.value = '';
  }

  async function submitReply() {
    if (!requireLogin(userStore)) return;
    if (!replyingTo.value) return;

    const text = replyText.value.trim();
    if (!text) return;

    submittingReply.value = true;
    try {
      const rootComment = comments.value.find(
        (item) => item.id === replyingTo.value.rootCommentId,
      );
      const newReply = await createComment({
        postId: postIdRef.value,
        content: text,
        parentId: replyingTo.value.commentId,
        replyUserId: replyingTo.value.userId,
      });
      newReply.liked = false;

      if (!repliesMap[replyingTo.value.rootCommentId]) {
        repliesMap[replyingTo.value.rootCommentId] = [];
      }
      repliesMap[replyingTo.value.rootCommentId].push(newReply);
      showRepliesMap[replyingTo.value.rootCommentId] = true;

      if (rootComment) {
        rootComment.replyCount = (rootComment.replyCount || 0) + 1;
      }
      postRef.value.commentCount += 1;
      replyText.value = '';
      cancelReply();
      showToast('回复成功', 'success');
    } catch (e) {
      // request 拦截器已处理错误
    } finally {
      submittingReply.value = false;
    }
  }

  async function toggleReplies(comment) {
    if (showRepliesMap[comment.id]) {
      showRepliesMap[comment.id] = false;
      return;
    }

    if (
      !repliesMap[comment.id] ||
      !repliesLoadedMap[comment.id] ||
      repliesMap[comment.id].length < (comment.replyCount || 0)
    ) {
      try {
        const page = await getReplies(comment.id, { pageSize: 50 });
        repliesMap[comment.id] = page?.records || [];
        repliesLoadedMap[comment.id] = true;
      } catch (e) {
        return;
      }
    }
    showRepliesMap[comment.id] = true;
  }

  async function loadReplyPreview(comment) {
    if (repliesMap[comment.id]) return;

    try {
      const page = await getReplies(comment.id, { pageSize: 1 });
      repliesMap[comment.id] = page?.records || [];
      repliesLoadedMap[comment.id] = (comment.replyCount || 0) <= 1;
    } catch (e) {
      // 评论主体仍可正常展示
    }
  }

  return {
    comments,
    repliesMap,
    repliesLoadedMap,
    showRepliesMap,
    commentText,
    replyingTo,
    replyText,
    submitting,
    submittingReply,
    setComments,
    submitComment,
    handleReplyClick,
    submitReply,
    toggleReplies,
    toggleCommentLike,
  };
}
