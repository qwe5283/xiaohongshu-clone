// 笔记相关接口
import request from './request';

/**
 * 分页查询笔记列表
 * @param {{pageNum?:number, pageSize?:number, keyword?:string, sortType?:string}} params
 * @returns {Promise<{records:object[], total:number, current:number, size:number}>}
 * 后端返回 MyBatis-Plus 的 IPage，结构为 { records, total, size, current, pages }
 */
export function getPosts(params = {}) {
  return request.get('/post/list', {
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 20,
      ...(params.keyword ? { keyword: params.keyword } : {}),
      ...(params.sortType ? { sortType: params.sortType } : {}),
    },
  });
}

/**
 * 创建笔记（需登录）
 * @param {{title:string, content?:string, imageUrls?:string[], videoUrl?:string, coverImage?:string}} data
 * @returns {Promise<object>} PostVO
 */
export function createPost(data) {
  return request.post('/post/create', data);
}

/**
 * 文本配图生成：根据标题文本生成一张 2:3 PNG 配图
 * @param {string} text - 文本内容，最多20字
 * @returns {Promise<Blob>} PNG 图片 Blob
 */
export function generateTextImage(text) {
  return request.get('/post/text-image/generate', {
    params: { text },
    responseType: 'blob',
  });
}

/**
 * 获取笔记详情
 * @param {number|string} postId
 * @returns {Promise<object>} PostVO
 */
export function getPostDetail(postId) {
  return request.get(`/post/${postId}`);
}

/**
 * 获取指定用户的笔记列表（分页）
 * @param {number|string} userId
 * @param {{pageNum?:number, pageSize?:number, sortType?:string}} params
 * @returns {Promise<{records:object[], total:number, current:number, size:number, pages:number}>}
 */
export function getUserPosts(userId, params = {}) {
  return request.get(`/post/user/${userId}`, {
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 20,
      ...(params.sortType ? { sortType: params.sortType } : {}),
    },
  });
}

/**
 * 获取当前登录用户的笔记列表（分页，需 auth）
 * @param {{pageNum?:number, pageSize?:number, sortType?:string}} params
 */
export function getMyPosts(params = {}) {
  return request.get('/post/my', {
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 20,
      ...(params.sortType ? { sortType: params.sortType } : {}),
    },
  });
}

/**
 * 把后端 PostVO 适配成前端 PostCard 期望的形状
 * 差异：后端用 authorNickname/authorAvatar 拍平字段，前端用 author.{nickname,avatar}
 *      后端无 isTextCard，前端用它区分纯文字卡片
 */
export function adaptPost(post) {
  if (!post) return post;
  const isTextCard = !post.coverImage && post.type === 0;
  return {
    ...post,
    // 拍平的作者字段 → 嵌套对象，兼容 PostCard 的 post.author.xxx
    author: {
      id: post.userId,
      nickname: post.authorNickname || '未知用户',
      avatar:
        post.authorAvatar ||
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23eee"/><text x="50%" y="55%" text-anchor="middle" font-size="18" fill="%23bbb">U</text></svg>',
    },
    isTextCard,
    likeCount: post.likeCount ?? 0,
    commentCount: post.commentCount ?? 0,
  };
}
