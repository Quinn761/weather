<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { Bell, Monitor, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import BrandLogo from '@/components/BrandLogo.vue'
import WorkspaceBackdrop from '@/components/WorkspaceBackdrop.vue'
import { listNotifications, readNotification } from '@/api/operations'

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
const unreadAlertCount = computed(() => alerts.value.filter((item) => !item.readAt).length)
let alertTimer

async function loadAlerts() {
  try { alerts.value = await listNotifications() || [] } catch { /* handled by HTTP interceptor */ }
}

async function openAlert(alert) {
  await readNotification(alert.id)
  alerts.value = alerts.value.map(item => item.id === alert.id ? { ...item, readAt: new Date().toISOString() } : item)
  const name = alert.targetType === 'WORK_ORDER' ? 'work-orders' : 'events'
  router.push({ name }).catch(() => {})
}

function alertLabel(alert) {
  if (alert?.targetType === 'WORK_ORDER') return '工单提醒'
  if (alert?.type === 'WARNING' || alert?.targetType === 'EVENT') return '风险预警'
  return '系统通知'
}

function alertTone(alert) {
  if (alert?.targetType === 'WORK_ORDER') return 'work'
  if (alert?.type === 'WARNING' || alert?.targetType === 'EVENT') return 'warn'
  return 'info'
}

function formatAlertTime(value) {
  return String(value || '').replace('T', ' ').slice(0, 16)
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
          <el-popover
            placement="bottom-end"
            :width="380"
            trigger="click"
            :offset="12"
            popper-class="alert-notify-popper"
            @show="loadAlerts"
          >
            <template #reference>
              <button class="topbar-bell" type="button" aria-label="通知">
                <el-icon><Bell /></el-icon>
                <b v-if="unreadAlertCount">{{ unreadAlertCount }}</b>
              </button>
            </template>
            <section class="alert-popover">
              <header class="alert-popover-head">
                <div>
                  <strong>预警通知</strong>
                  <span>{{ alerts.length ? `${alerts.length} 条待关注` : '暂无活动预警' }}</span>
                </div>
                <em v-if="unreadAlertCount">{{ unreadAlertCount }} 未读</em>
              </header>

              <div v-if="alerts.length" class="alert-list">
                <div
                  v-for="alert in alerts"
                  :key="alert.id"
                  class="alert-item"
                  :class="[
                    { unread: !alert.readAt },
                    `tone-${alertTone(alert)}`,
                  ]"
                  role="button"
                  tabindex="0"
                  @click="openAlert(alert)"
                  @keydown.enter.prevent="openAlert(alert)"
                  @keydown.space.prevent="openAlert(alert)"
                >
                  <div class="alert-item-top">
                    <span class="alert-level">{{ alertLabel(alert) }}</span>
                    <time>{{ formatAlertTime(alert.createdAt) }}</time>
                  </div>
                  <div class="alert-title">{{ alert.title }}</div>
                  <div v-if="alert.content" class="alert-content">{{ alert.content }}</div>
                </div>
              </div>

              <div v-else class="alert-empty">
                <el-icon><Bell /></el-icon>
                <strong>暂无新通知</strong>
                <p>系统同步到预警或工单提醒后，会显示在这里。</p>
              </div>
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
.topbar-bell {
  position: relative;
}

.topbar-bell b {
  position: absolute;
  top: -6px;
  right: -8px;
  min-width: 16px;
  padding: 1px 5px;
  border-radius: 9px;
  background: #c9852e;
  color: #fff8ec;
  font-size: 10px;
  font-weight: 700;
  line-height: 14px;
  box-shadow: 0 0 0 2px #0b1a28;
}

.alert-popover {
  display: grid;
  gap: 12px;
}

.alert-popover-head {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 12px;
  padding: 2px 2px 10px;
  border-bottom: 1px solid #35566f66;
}

.alert-popover-head strong {
  display: block;
  color: #eaf6ff;
  font-size: 15px;
  font-weight: 650;
}

.alert-popover-head span {
  display: block;
  margin-top: 4px;
  color: #8eabbf;
  font-size: 12px;
}

.alert-popover-head em {
  flex-shrink: 0;
  padding: 4px 8px;
  border: 1px solid #3d7f9666;
  border-radius: 999px;
  background: #14384a88;
  color: #7fd4e8;
  font-size: 11px;
  font-style: normal;
  font-weight: 650;
}

.alert-list {
  display: grid;
  gap: 8px;
  max-height: 360px;
  overflow: auto;
  padding-right: 2px;
  scrollbar-width: thin;
  scrollbar-color: #38516c transparent;
}

.alert-item {
  --tone: #4eb8d0;
  display: block;
  padding: 12px 14px 12px 16px;
  border: 1px solid color-mix(in srgb, var(--tone) 28%, #2a4458);
  border-radius: 12px;
  background:
    linear-gradient(155deg, color-mix(in srgb, var(--tone) 10%, #122536), #0d1a27ee);
  color: #9cb4c6;
  text-align: left;
  cursor: pointer;
  font: inherit;
  line-height: 1.5;
  box-shadow: inset 3px 0 0 color-mix(in srgb, var(--tone) 55%, transparent);
  transition: border-color .16s ease, background .16s ease, transform .16s ease;
}

.alert-item.tone-warn { --tone: #e0b04a; }
.alert-item.tone-work { --tone: #5ecf9a; }
.alert-item.tone-info { --tone: #4eb8d0; }

.alert-item.unread {
  border-color: color-mix(in srgb, var(--tone) 48%, #35566f);
  box-shadow:
    inset 3px 0 0 var(--tone),
    0 8px 18px #00101d33;
}

.alert-item:hover {
  border-color: color-mix(in srgb, var(--tone) 58%, #3a5a70);
  background:
    linear-gradient(155deg, color-mix(in srgb, var(--tone) 16%, #163246), #102032f2);
  transform: translateY(-1px);
}

.alert-item:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--tone) 70%, #fff 10%);
  outline-offset: 2px;
}

.alert-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 22px;
}

.alert-level {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  height: 22px;
  padding: 0 8px;
  border: 1px solid color-mix(in srgb, var(--tone) 40%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--tone) 14%, #0a1824);
  color: color-mix(in srgb, var(--tone) 78%, #fff 22%);
  font-size: 11px;
  font-weight: 650;
  letter-spacing: .02em;
  line-height: 1;
}

.alert-title {
  display: block;
  margin: 8px 0 0;
  color: #e8f4fc;
  font-size: 13px;
  font-weight: 650;
  line-height: 20px;
  overflow-wrap: anywhere;
}

.alert-content {
  display: block;
  max-height: 40px;
  margin: 6px 0 0;
  overflow: hidden;
  color: #8eaabe;
  font-size: 12px;
  line-height: 20px;
}

.alert-item time {
  flex-shrink: 0;
  color: #6f91a8;
  font-size: 11px;
  line-height: 1.2;
  white-space: nowrap;
}

.alert-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 28px 16px;
  border: 1px dashed #3d647c;
  border-radius: 12px;
  background: linear-gradient(165deg, #12263788, #0d1a2788);
  color: #8eaabe;
  text-align: center;
}

.alert-empty .el-icon {
  font-size: 22px;
  color: #4eb8d0;
}

.alert-empty strong {
  color: #e6f5ff;
  font-size: 13px;
}

.alert-empty p {
  margin: 0;
  max-width: 24ch;
  font-size: 12px;
  line-height: 1.55;
}
</style>

<style>
.alert-notify-popper.el-popover,
.alert-notify-popper.el-popper {
  --el-popover-bg-color: transparent;
  --el-bg-color-overlay: transparent;
  padding: 14px !important;
  border: 1px solid #35566f !important;
  border-radius: 16px !important;
  background:
    radial-gradient(circle at 8% 0%, rgb(62 150 176 / 16%), transparent 42%),
    linear-gradient(165deg, #14283af8, #0e1a28f5) !important;
  box-shadow:
    inset 0 1px rgb(160 220 236 / 10%),
    0 18px 40px rgb(1 8 16 / 55%) !important;
  color: #d9e8fa;
  line-height: 1.5 !important;
  backdrop-filter: blur(14px);
}

.alert-notify-popper .alert-item {
  display: block !important;
  height: auto !important;
}

.alert-notify-popper .alert-item-top,
.alert-notify-popper .alert-title,
.alert-notify-popper .alert-content {
  position: static !important;
  float: none !important;
}

.alert-notify-popper .alert-title {
  display: block !important;
  margin-top: 8px !important;
  line-height: 20px !important;
}

.alert-notify-popper .alert-content {
  display: block !important;
  margin-top: 6px !important;
  line-height: 20px !important;
  max-height: 40px;
  overflow: hidden;
}

.alert-notify-popper .el-popper__arrow::before {
  border: 1px solid #35566f !important;
  background: #122536 !important;
}
</style>
