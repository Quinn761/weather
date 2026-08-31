import http from './http' // 导入封装后的 axios 实例供本文件使用

export function listRoles() { // 定义 listRoles 方法的入口
  return http.get('/roles') // 拉取角色列表
} // 

export function createRole(payload) { // 定义 createRole 方法的入口
  return http.post('/roles', payload) // 新建角色
} // 

export function updateRole(id, payload) { // 定义 updateRole 方法的入口
  return http.put(`/roles/${id}`, payload) // 更新角色
} // 

export function deleteRole(id) { // 定义 deleteRole 方法的入口
  return http.delete(`/roles/${id}`) // 删除角色
} // 
