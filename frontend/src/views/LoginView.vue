<script setup>
import { reactive, ref } from 'vue' // 导入 vue 的组合式 API 供本文件使用
import { useRoute, useRouter } from 'vue-router' // 导入路由对象供本文件使用
import { ElMessage } from 'element-plus' // 导入 element-plus 的消息组件供本文件使用
import { Monitor } from '@element-plus/icons-vue' // 导入登录页品牌图标
import { useAuthStore } from '@/stores/auth' // 导入登录状态仓库供本文件使用

const route = useRoute() // 读取当前路由，登录后按 redirect 跳回
const router = useRouter() // 用于登录成功后跳转
const authStore = useAuthStore() // 读取登录仓库
const submitting = ref(false) // 标记登录按钮加载状态
const form = reactive({ // 保存登录表单
  username: 'admin', // 预填演示管理员账号
  password: 'admin123', // 预填演示管理员密码
}) // 结束当前多行语句

async function submit() { // 定义 submit 方法的入口
  if (!form.username.trim() || !form.password) { // 判断账号或密码是否为空
    ElMessage.warning('请输入用户名和密码') // 提示先填写完整
    return // 中断提交
  } // 
  submitting.value = true // 打开按钮加载状态
  try { // 开始当前声明或控制结构的代码块
    await authStore.login(form.username.trim(), form.password) // 调用登录接口并保存令牌
    ElMessage.success('登录成功') // 提示登录完成
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard' // 优先回到被拦截的页面
    await router.replace(redirect) // 进入系统首页或原目标页
  } finally { // 开始当前声明或控制结构的代码块
    submitting.value = false // 关闭按钮加载状态
  } // 
} // 
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <span class="brand-mark">
          <el-icon :size="22"><Monitor /></el-icon>
        </span>
        <div>
          <strong>Weather Data Hub</strong>
          <p>权限管理中心</p>
        </div>
      </div>
      <h1>登录系统</h1>
      <p class="login-hint">演示账号 admin / admin123，普通用户 user / user123</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" maxlength="64" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            maxlength="64"
            placeholder="请输入密码"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" class="login-submit" :loading="submitting" native-type="submit">
          进入系统
        </el-button>
      </el-form>
    </div>
  </div>
</template>
