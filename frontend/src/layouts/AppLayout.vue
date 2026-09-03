<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { Monitor, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

function iconOf(name) {
  return ElementPlusIconsVue[name] || Monitor
}

function flattenMenus(nodes) {
  const rows = []
  for (const node of nodes || []) {
    if (node.type === 'MENU' && node.path) {
      rows.push(node)
    }
    if (node.children?.length) {
      rows.push(...flattenMenus(node.children))
    }
  }
  return rows
}

const visibleMenus = computed(() => flattenMenus(authStore.user?.menus || []))
const active = computed(() => route.path)
const pageTitle = computed(() => route.meta.title || 'Weather Data Hub')

function go(path) {
  router.push(path)
}

async function logout() {
  await authStore.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">
          <Monitor />
        </span>
        <div>
          <strong>Weather Data Hub</strong>
          <p>权限管理中心</p>
        </div>
      </div>
      <nav class="menu">
        <button
          v-for="item in visibleMenus"
          :key="item.path"
          class="menu-item"
          :class="{ active: active === item.path }"
          type="button"
          @click="go(item.path)"
        >
          <el-icon><component :is="iconOf(item.icon)" /></el-icon>
          <span>{{ item.name }}</span>
        </button>
      </nav>
      <div class="sidebar-foot">
        <div class="sidebar-user">
          <strong>{{ authStore.nickname }}</strong>
          <p>{{ (authStore.user?.roles || []).join(' / ') || '已登录' }}</p>
        </div>
        <button class="logout-btn" type="button" @click="logout">
          <el-icon><SwitchButton /></el-icon>
          退出登录
        </button>
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <div>
          <h1>{{ pageTitle }}</h1>
          <p>用户绑定角色，角色勾选菜单，菜单决定侧栏和接口权限</p>
        </div>
        <div class="topbar-badge">{{ authStore.user?.username || '已登录' }}</div>
      </header>
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>
