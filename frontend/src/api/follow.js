// 关注相关接口
import request from './request';

/**
 * 关注/取消关注用户（toggle）
 * @param {number|string} userId 被关注用户ID
 * @returns {Promise<{followed:boolean, message:string}>}
 */
export function toggleFollow(userId) {
  return request.post(`/follow/${userId}`);
}

/**
 * 查询关注状态
 * @param {number|string} userId
 * @returns {Promise<{followed:boolean}>}
 */
export function getFollowStatus(userId) {
  return request.get(`/follow/status/${userId}`);
}

/**
 * 获取关注数 / 粉丝数
 * @param {number|string} userId
 * @returns {Promise<{followingCount:number, followersCount:number}>}
 */
export function getFollowCount(userId) {
  return request.get(`/follow/count/${userId}`);
}

/**
 * 获取用户的关注列表（分页）
 * @param {number|string} userId
 * @param {{pageNum?:number, pageSize?:number}} params
 */
export function getFollowingList(userId, params = {}) {
  return request.get(`/follow/following/${userId}`, {
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 20,
    },
  });
}

/**
 * 获取用户的粉丝列表（分页）
 * @param {number|string} userId
 * @param {{pageNum?:number, pageSize?:number}} params
 */
export function getFollowersList(userId, params = {}) {
  return request.get(`/follow/followers/${userId}`, {
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 20,
    },
  });
}
