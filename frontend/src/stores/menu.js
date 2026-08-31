import { ref } from 'vue' // 导入 vue 的 ref 供本文件使用
import { defineStore } from 'pinia' // 导入 pinia 的 defineStore 供本文件使用
import { createMenu, deleteMenu, listMenus, updateMenu } from '@/api/menu' // 导入菜单接口方法供本文件使用

export const useMenuStore = defineStore('menu', () => { // 声明 menu 状态仓库
  const tree = ref([]) // 计算并保存 tree 的值
  const loading = ref(false) // 计算并保存 loading 的值

  async function fetchTree() { // 定义 fetchTree 方法的入口
    loading.value = true // 执行赋值语句完成当前步骤
    try { // 开始当前声明或控制结构的代码块
      tree.value = (await listMenus()) || [] // 拉取菜单树
    } finally { // 开始当前声明或控制结构的代码块
      loading.value = false // 执行赋值语句完成当前步骤
    } // 
  } // 

  async function create(payload) { // 定义 create 方法的入口
    const created = await createMenu(payload) // 调用新建接口
    await fetchTree() // 刷新菜单树
    return created // 返回新建结果
  } // 

  async function update(id, payload) { // 定义 update 方法的入口
    const saved = await updateMenu(id, payload) // 调用更新接口
    await fetchTree() // 刷新菜单树
    return saved // 返回保存结果
  } // 

  async function remove(id) { // 定义 remove 方法的入口
    await deleteMenu(id) // 调用删除接口
    await fetchTree() // 刷新菜单树
  } // 

  return { tree, loading, fetchTree, create, update, remove } // 暴露给页面使用的状态和方法
}) // 
