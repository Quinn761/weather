<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { Bell, Monitor, SwitchButton, UserFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import BrandLogo from '@/components/BrandLogo.vue'
import WorkspaceBackdrop from '@/components/WorkspaceBackdrop.vue'

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
const pageSubtitle = computed(() => route.meta.subtitle || '')
const isGisPage = computed(() => route.path === '/gis')

function go(path) {
  if (route.path === path) return
  router.push(path).catch(() => {})
}

async function logout() {
  await authStore.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="shell" :class="{ 'page-gis': isGisPage }">
    <WorkspaceBackdrop />
    <aside class="sidebar">
      <div class="brand">
        <BrandLogo :size="40" />
        <div>
          <strong>Weather Data Hub</strong>
          <p>空间 · 智能 · 连接</p>
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
      <header v-if="!isGisPage" class="topbar">
        <div class="topbar-title">
          <h1>{{ pageTitle }}</h1>
          <p v-if="pageSubtitle">{{ pageSubtitle }}</p>
        </div>
        <div class="topbar-right">
          <p class="topbar-motto">地形 · 数据 · 洞察 · 决策</p>
          <button class="topbar-bell" type="button" aria-label="通知">
            <el-icon><Bell /></el-icon>
          </button>
          <div class="topbar-badge">
            <el-icon><UserFilled /></el-icon>
            {{ authStore.user?.username || '已登录' }}
          </div>
        </div>
      </header>
      <main class="content">
        <router-view :key="route.fullPath" />
      </main>
      <footer class="workspace-foot">
        <span>Weather Data Hub · 气象 · 地理 · 海洋 · 灾害 · 让世界更安全</span>
        <span>© 2025 Weather Data Hub. All rights reserved.</span>
      </footer>
    </div>
  </div>
</template>
