// 评论相关接口
import request from './request';

/**
 * 获取笔记的一级评论（分页）
 * @param {number|string} postId
 * @param {{pageNum?:number, pageSize?:number}} params
 * @returns {Promise<{records:object[], total:number, current:number, size:number, pages:number}>}
 */
export function getComments(postId, params = {}) {
  return request.get(`/comment/post/${postId}`, {
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 20,
    },
  });
}

/**
 * 获取某条一级评论的回复列表（分页）
 * @param {number|string} commentId
 * @param {{pageNum?:number, pageSize?:number}} params
 */
export function getReplies(commentId, params = {}) {
  return request.get(`/comment/replies/${commentId}`, {
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 20,
    },
  });
}

/**
 * 发表评论（一级评论或回复）
 * @param {{postId:number, content:string, parentId?:number, replyUserId?:number}} data
 * @returns {Promise<object>} CommentVO
 */
export function createComment(data) {
  return request.post('/comment/create', data);
}

/**
 * 删除评论（仅作者本人可删）
 * @param {number|string} commentId
 */
export function deleteComment(commentId) {
  return request.delete(`/comment/delete/${commentId}`);
}
