// axios 封装：统一 baseURL、请求塞 Token、响应解包 Result、错误集中处理
import axios from 'axios';
import { showToast } from '@/utils/toast';
import { getToken, clearToken, notifyAuthExpired } from '@/auth/session';

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
});

// 请求拦截器：自动带 Authorization
request.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// 响应拦截器：解包 Result，集中处理错误
// 后端约定：{ code, message, data, timestamp }，code=200 为成功
// 1005=未登录 → 清 token 并跳登录
request.interceptors.response.use(
  (response) => {
    const res = response.data;
    // 非 Result 包装（如下载文件等），直接返回
    if (res == null || typeof res !== 'object' || res.code === undefined) {
      return res;
    }
    if (res.code === 200) {
      // 业务成功：只把 data 暴露给调用方
      return res.data;
    }
    // 业务失败
    if (res.code === 1005) {
      // 未登录 / token 失效
      const msg = res.message || '登录已失效，请重新登录';
      clearToken();
      notifyAuthExpired(msg);
      return Promise.reject(new Error(msg));
    }
    // 其他业务错误（密码错误、用户已存在等）
    showToast(res.message || '操作失败', 'error');
    return Promise.reject(new Error(res.message || '操作失败'));
  },
  (error) => {
    // HTTP 层错误
    let msg = '网络异常，请稍后重试';
    if (error.response) {
      const status = error.response.status;
      const body = error.response.data;
      if (status === 401) {
        clearToken();
        msg = (body && body.message) || '登录已失效，请重新登录';
        notifyAuthExpired(msg);
        return Promise.reject(new Error(msg));
      } else if (body && body.message) {
        msg = body.message;
      } else if (status === 404) {
        msg = '请求的资源不存在';
      } else if (status >= 500) {
        msg = '服务器开小差了，请稍后重试';
      }
    } else if (error.code === 'ECONNABORTED') {
      msg = '请求超时，请检查网络';
    }
    showToast(msg, 'error');
    return Promise.reject(new Error(msg));
  },
);

export default request;
