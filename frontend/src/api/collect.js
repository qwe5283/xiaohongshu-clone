// 收藏相关接口
import request from './request';

/**
 * 收藏/取消收藏笔记（toggle）
 * @param {number|string} postId
 * @returns {Promise<{collected:boolean, message:string}>}
 */
export function toggleCollectPost(postId) {
  return request.post(`/collect/post/${postId}`);
}

/**
 * 查询笔记收藏状态
 * @param {number|string} postId
 * @returns {Promise<{collected:boolean}>}
 */
export function getCollectStatusPost(postId) {
  return request.get(`/collect/status/post/${postId}`);
}

/**
 * 获取用户的收藏笔记列表（分页，需登录）
 * @param {number|string} userId
 * @param {{pageNum?:number, pageSize?:number}} params
 * @returns {Promise<{records:object[], total:number, pageNum:number, pageSize:number, pages:number}>}
 */
export function getCollectedPosts(userId, params = {}) {
  return request.get(`/collect/posts/${userId}`, {
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 20,
    },
  });
}
