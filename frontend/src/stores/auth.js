import { computed, ref } from 'vue' // 导入 vue 的响应式 API 供本文件使用
import { defineStore } from 'pinia' // 导入 pinia 的 defineStore 供本文件使用
import { getCurrentUser, login as loginApi } from '@/api/auth' // 导入登录接口方法供本文件使用

const TOKEN_KEY = 'wh_token' // 定义本地存储令牌的键名
const USER_KEY = 'wh_user' // 定义本地存储用户资料的键名

function readStoredUser() { // 定义 readStoredUser 方法的入口
  try { // 开始当前声明或控制结构的代码块
    const raw = localStorage.getItem(USER_KEY) // 读取本地缓存的用户资料
    return raw ? JSON.parse(raw) : null // 有缓存则解析，否则视为未登录
  } catch { // 开始当前声明或控制结构的代码块
    return null // 缓存损坏时当作未登录
  } // 
} // 

export const useAuthStore = defineStore('auth', () => { // 声明 auth 状态仓库
  const token = ref(localStorage.getItem(TOKEN_KEY) || '') // 从本地恢复令牌
  const user = ref(readStoredUser()) // 从本地恢复用户资料
  const userLoaded = ref(false)

  const nickname = computed(() => user.value?.nickname || user.value?.username || '未登录') // 顶栏展示用的显示名
  const isLoggedIn = computed(() => Boolean(token.value)) // 判断当前是否已登录

  function persist() { // 定义 persist 方法的入口
    if (token.value) { // 判断是否持有令牌
      localStorage.setItem(TOKEN_KEY, token.value) // 把令牌写入本地
    } else { // 开始当前声明或控制结构的代码块
      localStorage.removeItem(TOKEN_KEY) // 退出时清掉令牌
    } // 
    if (user.value) { // 判断是否持有用户资料
      localStorage.setItem(USER_KEY, JSON.stringify(user.value)) // 把用户资料写入本地
    } else { // 开始当前声明或控制结构的代码块
      localStorage.removeItem(USER_KEY) // 退出时清掉用户资料
    } // 
  } // 

  async function login(username, password) { // 定义 login 方法的入口
    const data = await loginApi({ username, password }) // 调用后端登录接口
    token.value = data.token // 保存返回的 JWT
    user.value = data.user // 保存返回的用户资料
    userLoaded.value = true
    persist() // 同步到本地存储
    return data // 返回登录结果给页面使用
  } // 

  async function fetchMe() { // 定义 fetchMe 方法的入口
    const me = await getCurrentUser() // 用令牌刷新当前用户
    user.value = me // 用最新资料覆盖本地状态
    userLoaded.value = true
    persist() // 同步到本地存储
    return me // 返回当前用户资料
  } // 

  async function ensureFreshUser() {
    if (!token.value) {
      return null
    }
    if (userLoaded.value) {
      return user.value
    }
    return fetchMe()
  }

  function logout() { // 定义 logout 方法的入口
    token.value = '' // 清空令牌
    user.value = null // 清空用户资料
    userLoaded.value = false
    persist() // 同步清理本地存储
  } // 

  function hasPermission(code) { // 定义 hasPermission 方法的入口
    return Boolean(user.value?.permissions?.includes(code)) // 按权限编码判断当前用户能否访问
  } // 

  return { token, user, userLoaded, nickname, isLoggedIn, login, fetchMe, ensureFreshUser, logout, hasPermission } // 暴露给页面使用的状态和方法
}) // 
