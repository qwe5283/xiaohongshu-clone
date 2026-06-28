// 文件上传相关接口
import request from './request';

/**
 * 上传图片
 * @param {File} file - 图片文件（≤10MB）
 * @returns {Promise<{url: string}>}
 */
export function uploadImage(file) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post('/upload/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/**
 * 上传视频
 * @param {File} file - 视频文件
 * @returns {Promise<{url: string}>}
 */
export function uploadVideo(file) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post('/upload/video', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}
