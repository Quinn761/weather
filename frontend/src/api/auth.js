import http from './http' // 导入封装后的 axios 实例供本文件使用

export function login(payload) { // 定义 login 方法的入口
  return http.post('/auth/login', payload) // 提交用户名和密码换取令牌
} // 

export function getCurrentUser() { // 定义 getCurrentUser 方法的入口
  return http.get('/auth/me') // 用当前令牌拉取登录用户资料
} // 
