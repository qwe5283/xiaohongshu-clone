// 鉴权相关接口
import request from './request'

/**
 * 登录
 * @param {{username:string, password:string}} data
 * @returns {Promise<{token:string, expiresIn:number, user:object}>}
 */
export function login(data) {
  return request.post('/user/login', data)
}

/**
 * 注册
 * @param {{username:string, password:string, nickname?:string, phone?:string}} data
 * @returns {Promise<object>} UserVO
 */
export function register(data) {
  return request.post('/user/register', data)
}

/**
 * 获取当前登录用户信息（需带 token）
 * @returns {Promise<object>} UserVO
 */
export function getMe() {
  return request.get('/user/me')
}
