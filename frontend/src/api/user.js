import http from './http' // 导入封装后的 axios 实例供本文件使用

export function listUsers(params) { // 定义 listUsers 方法的入口
  return http.get('/users', { params }) // 返回当前方法的处理结果
} // 

export function getUser(id) { // 定义 getUser 方法的入口
  return http.get(`/users/${id}`) // 返回当前方法的处理结果
} // 

export function createUser(payload) { // 定义 createUser 方法的入口
  return http.post('/users', payload) // 返回当前方法的处理结果
} // 

export function updateUser(id, payload) { // 定义 updateUser 方法的入口
  return http.put(`/users/${id}`, payload) // 返回当前方法的处理结果
} // 

export function deleteUser(id) { // 定义 deleteUser 方法的入口
  return http.delete(`/users/${id}`) // 返回当前方法的处理结果
} // 
