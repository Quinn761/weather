import { ref } from 'vue' // 导入 vue 的 ref 供本文件使用
import { defineStore } from 'pinia' // 导入 pinia 的 defineStore 供本文件使用
import { createUser, deleteUser, listUsers, updateUser } from '@/api/user' // 导入用户接口方法供本文件使用

export const useUserStore = defineStore('user', () => { // 声明 user 状态仓库
  const list = ref([]) // 计算并保存 list 的值
  const total = ref(0) // 计算并保存 total 的值
  const loading = ref(false) // 计算并保存 loading 的值
  const current = ref(1) // 计算并保存 current 的值
  const size = ref(10) // 计算并保存 size 的值
  const keyword = ref('') // 计算并保存 keyword 的值

  async function fetchPage() { // 定义 fetchPage 方法的入口
    loading.value = true // 执行赋值语句完成当前步骤
    try { // 开始当前声明或控制结构的代码块
      const page = await listUsers({ // 计算并保存 page 的值
        current: current.value, // 声明枚举值或多行参数的一项
        size: size.value, // 声明枚举值或多行参数的一项
        keyword: keyword.value || undefined, // 声明枚举值或多行参数的一项
      }) // 结束当前多行语句
      list.value = page?.records || [] // 执行赋值语句完成当前步骤
      total.value = Number(page?.total || 0) // 执行赋值语句完成当前步骤
    } finally { // 开始当前声明或控制结构的代码块
      loading.value = false // 执行赋值语句完成当前步骤
    } // 
  } // 

  async function create(payload) { // 定义 create 方法的入口
    const created = await createUser(payload) // 计算并保存 created 的值
    await fetchPage() // 执行 fetchPage 语句完成当前步骤
    return created // 返回当前方法的处理结果
  } // 

  async function update(id, payload) { // 定义 update 方法的入口
    const saved = await updateUser(id, payload) // 计算并保存 saved 的值
    await fetchPage() // 执行 fetchPage 语句完成当前步骤
    return saved // 返回当前方法的处理结果
  } // 

  async function remove(id) { // 定义 remove 方法的入口
    await deleteUser(id) // 执行 deleteUser 语句完成当前步骤
    if (list.value.length === 1 && current.value > 1) { // 判断条件是否成立以决定是否进入分支
      current.value -= 1 // 执行赋值语句完成当前步骤
    } // 
    await fetchPage() // 执行 fetchPage 语句完成当前步骤
  } // 

  return { list, total, loading, current, size, keyword, fetchPage, create, update, remove } // 返回当前方法的处理结果
}) // 
