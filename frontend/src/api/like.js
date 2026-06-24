// 点赞相关接口
import request from './request'

/**
 * 点赞/取消点赞笔记（toggle）
 * @param {number|string} postId
 * @returns {Promise<{liked:boolean, message:string}>}
 */
export function toggleLikePost(postId) {
  return request.post(`/like/post/${postId}`)
}

/**
 * 点赞/取消点赞评论（toggle）
 * @param {number|string} commentId
 * @returns {Promise<{liked:boolean, message:string}>}
 */
export function toggleLikeComment(commentId) {
  return request.post(`/like/comment/${commentId}`)
}

/**
 * 查询笔记点赞状态
 * @param {number|string} postId
 * @returns {Promise<{liked:boolean}>}
 */
export function getLikeStatusPost(postId) {
  return request.get(`/like/status/post/${postId}`)
}

/**
 * 查询评论点赞状态
 * @param {number|string} commentId
 * @returns {Promise<{liked:boolean}>}
 */
export function getLikeStatusComment(commentId) {
  return request.get(`/like/status/comment/${commentId}`)
}
