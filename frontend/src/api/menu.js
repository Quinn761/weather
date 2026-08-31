import http from './http' // 导入封装后的 axios 实例供本文件使用

export function listMenus() { // 定义 listMenus 方法的入口
  return http.get('/menus') // 拉取完整菜单树
} // 

export function createMenu(payload) { // 定义 createMenu 方法的入口
  return http.post('/menus', payload) // 新建菜单
} // 

export function updateMenu(id, payload) { // 定义 updateMenu 方法的入口
  return http.put(`/menus/${id}`, payload) // 更新菜单
} // 

export function deleteMenu(id) { // 定义 deleteMenu 方法的入口
  return http.delete(`/menus/${id}`) // 删除菜单
} // 
