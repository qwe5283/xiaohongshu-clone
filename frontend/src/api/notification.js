// 消息通知相关接口
import request from './request';

/**
 * 获取未读消息数
 * @returns {Promise<{unreadCount:number}>}
 */
export function getUnreadNotificationCount() {
  return request.get('/notification/unread-count');
}

/**
 * 分页获取消息通知
 * @param {{pageNum?:number, pageSize?:number, type?:number}} params
 * @returns {Promise<{records:object[], total:number, current:number, size:number, pages:number}>}
 */
export function getNotifications(params = {}) {
  return request.get('/notification/list', {
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 20,
      ...(params.type ? { type: params.type } : {}),
    },
  });
}

/**
 * 一键已读
 */
export function markAllNotificationsAsRead() {
  return request.put('/notification/read-all');
}

/**
 * 阅读单条消息通知
 * @param {number} id - 通知ID
 */
export function markNotificationAsRead(id) {
  return request.put(`/notification/read/${id}`);
}
