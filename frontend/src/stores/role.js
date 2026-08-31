import { ref } from 'vue' // 导入 vue 的 ref 供本文件使用
import { defineStore } from 'pinia' // 导入 pinia 的 defineStore 供本文件使用
import { createRole, deleteRole, listRoles, updateRole } from '@/api/role' // 导入角色接口方法供本文件使用

export const useRoleStore = defineStore('role', () => { // 声明 role 状态仓库
  const list = ref([]) // 计算并保存 list 的值
  const loading = ref(false) // 计算并保存 loading 的值

  async function fetchList() { // 定义 fetchList 方法的入口
    loading.value = true // 执行赋值语句完成当前步骤
    try { // 开始当前声明或控制结构的代码块
      list.value = (await listRoles()) || [] // 拉取角色列表
    } finally { // 开始当前声明或控制结构的代码块
      loading.value = false // 执行赋值语句完成当前步骤
    } // 
  } // 

  async function create(payload) { // 定义 create 方法的入口
    const created = await createRole(payload) // 调用新建接口
    await fetchList() // 刷新列表
    return created // 返回新建结果
  } // 

  async function update(id, payload) { // 定义 update 方法的入口
    const saved = await updateRole(id, payload) // 调用更新接口
    await fetchList() // 刷新列表
    return saved // 返回保存结果
  } // 

  async function remove(id) { // 定义 remove 方法的入口
    await deleteRole(id) // 调用删除接口
    await fetchList() // 刷新列表
  } // 

  return { list, loading, fetchList, create, update, remove } // 暴露给页面使用的状态和方法
}) // 
