<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { Bell, Monitor, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import BrandLogo from '@/components/BrandLogo.vue'
import WorkspaceBackdrop from '@/components/WorkspaceBackdrop.vue'
import { listActiveCameraAlerts } from '@/api/camera'

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
const isCameraDetailPage = computed(() => route.name === 'camera-detail')
const isOfficialAlertsPage = computed(() => route.path === '/official-alerts')
const alerts = ref([])
let alertTimer

async function loadAlerts() {
  try { alerts.value = await listActiveCameraAlerts() || [] } catch { /* handled by HTTP interceptor */ }
}

function openAlert(alert) {
  router.push({ name: 'camera-detail', params: { id: alert.cameraId } }).catch(() => {})
}

onMounted(() => {
  loadAlerts()
  alertTimer = window.setInterval(loadAlerts, 60000)
})
onBeforeUnmount(() => window.clearInterval(alertTimer))

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
  <div class="shell" :class="{ 'page-gis': isGisPage, 'page-camera-detail': isCameraDetailPage, 'page-official-alerts': isOfficialAlertsPage }">
    <WorkspaceBackdrop />
    <aside v-if="!isCameraDetailPage" class="sidebar">
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
      <header v-if="!isGisPage && !isCameraDetailPage" class="topbar">
        <div class="topbar-title">
          <h1>{{ pageTitle }}</h1>
          <p v-if="pageSubtitle">{{ pageSubtitle }}</p>
        </div>
        <div class="topbar-right">
          <el-popover placement="bottom-end" :width="360" trigger="click" @show="loadAlerts">
            <template #reference>
              <button class="topbar-bell" type="button" aria-label="通知">
                <el-icon><Bell /></el-icon><b v-if="alerts.length">{{ alerts.length }}</b>
              </button>
            </template>
            <section class="alert-popover">
              <header><strong>预警通知</strong><span>{{ alerts.length ? `${alerts.length} 条待关注` : '暂无活动预警' }}</span></header>
              <button v-for="alert in alerts" :key="alert.id" class="alert-item" type="button" @click="openAlert(alert)">
                <span class="alert-level">一般预警</span><strong>{{ alert.title }}</strong><p>{{ alert.content }}</p>
                <time>{{ String(alert.lastDetectedAt || '').replace('T', ' ').slice(0, 16) }}</time>
              </button>
            </section>
          </el-popover>
        </div>
      </header>
      <main class="content">
        <router-view :key="route.fullPath" />
      </main>
      <footer v-if="!isOfficialAlertsPage" class="workspace-foot">
        <span>Weather Data Hub · 气象 · 地理 · 海洋 · 灾害 · 让世界更安全</span>
        <span>© 2025 Weather Data Hub. All rights reserved.</span>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.topbar-bell { position: relative; }
.topbar-bell b { position: absolute; top: -6px; right: -8px; min-width: 16px; padding: 1px 4px; border-radius: 9px; background: #d97706; color: #fff; font-size: 10px; line-height: 14px; }
.alert-popover { display: grid; gap: 8px; max-height: 420px; overflow: auto; }
.alert-popover header { display: flex; justify-content: space-between; align-items: baseline; padding: 2px 2px 8px; border-bottom: 1px solid var(--el-border-color-lighter); }
.alert-popover header span, .alert-item p, .alert-item time { color: var(--el-text-color-secondary); font-size: 12px; }
.alert-item { display: grid; gap: 4px; padding: 10px; border: 0; border-radius: 7px; background: var(--el-fill-color-light); text-align: left; cursor: pointer; }
.alert-item:hover { background: var(--el-fill-color); }
.alert-item strong { font-size: 13px; color: var(--el-text-color-primary); }.alert-item p { margin: 0; line-height: 1.5; }.alert-item time { font-size: 11px; }
.alert-level { color: #b45309; font-size: 11px; font-weight: 600; }
</style>
