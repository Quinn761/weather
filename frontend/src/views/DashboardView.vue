<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ChatDotRound, Document, Location, Menu, Monitor, Odometer, Operation,
  Promotion, User, VideoCamera, Warning,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useDashboardStore } from '@/stores/dashboard'
import { listNotifications } from '@/api/operations'
import dashboardBackdrop from '@/assets/workspace/dashboard-weather-intelligence.png'

const router = useRouter()
const authStore = useAuthStore()
const dashboard = useDashboardStore()
const notifications = ref([])

const modules = [
  { title: '空间态势', hint: 'GIS 数据研判', icon: Location, path: '/gis', permission: 'gis:read', tone: 'cyan' },
  { title: '视频监控', hint: '现场画面接入', icon: VideoCamera, path: '/cameras', permission: 'camera:read', tone: 'blue' },
  { title: '灾害预警', hint: '官方预警汇聚', icon: Warning, path: '/official-alerts', permission: 'official-alert:read', tone: 'amber' },
  { title: '事件中心', hint: '风险信号处置', icon: Promotion, path: '/events', permission: 'ops:event:read', tone: 'mint' },
  { title: '处置工单', hint: '协同响应跟踪', icon: Operation, path: '/work-orders', permission: 'ops:work-order:read', tone: 'sky' },
  { title: '知识库', hint: '业务知识沉淀', icon: Document, path: '/kb', permission: 'kb:read', tone: 'slate' },
  { title: '智能助手', hint: '数据与知识问答', icon: ChatDotRound, path: '/ai', permission: 'ai:chat', tone: 'violet' },
]

const management = [
  { key: 'userCount', label: '系统用户', icon: User, path: '/users', permission: 'user:read' },
  { key: 'roleCount', label: '角色配置', icon: Odometer, path: '/roles', permission: 'role:read' },
  { key: 'menuCount', label: '功能菜单', icon: Menu, path: '/menus', permission: 'menu:read' },
]

const availableModules = computed(() => modules.filter(item => authStore.hasPermission(item.permission)))
const availableManagement = computed(() => management.filter(item => authStore.hasPermission(item.permission)))
const unreadNotifications = computed(() => notifications.value.filter(item => !item.readAt).length)
const displayedNotifications = computed(() => notifications.value.slice(0, 4))
const roles = computed(() => (authStore.user?.roles || []).filter(Boolean))

onMounted(async () => {
  dashboard.load()
  try { notifications.value = await listNotifications() || [] } catch { notifications.value = [] }
})

function go(path) { router.push(path).catch(() => {}) }
function notificationTone(item) {
  if (item?.type === 'WARNING' || item?.targetType === 'EVENT') return 'warning'
  if (item?.targetType === 'WORK_ORDER') return 'work'
  return 'normal'
}
function notificationTime(value) { return String(value || '').replace('T', ' ').slice(5, 16) || '刚刚' }
</script>

<template>
  <div class="command-desk" v-loading="dashboard.loading">
    <section class="command-hero" :style="{ '--hero-image': `url(${dashboardBackdrop})` }">
      <div class="hero-shade"></div>
      <div class="hero-copy">
        <p class="hero-overline"><span></span> Weather Data Hub</p>
        <h2>让天气信号<br />成为清晰行动</h2>
        <p class="hero-description">统一连接空间态势、现场画面、官方预警与应急处置，为风险研判提供可靠的业务视野。</p>
        <button v-if="authStore.hasPermission('gis:read')" class="hero-action" type="button" @click="go('/gis')">
          <el-icon><Location /></el-icon> 打开空间态势
        </button>
      </div>
      <div class="hero-map-meta" aria-hidden="true">
        <span>气象情报图层</span><i></i><span>卫星遥感 · 雷达回波</span>
      </div>
    </section>

    <section class="telemetry-band" aria-label="系统实时概览">
      <div class="telemetry-item accent-cyan">
        <span class="telemetry-icon"><el-icon><Monitor /></el-icon></span>
        <div><strong>{{ availableModules.length }}</strong><p>可用业务模块</p></div>
        <small>按权限展示</small>
      </div>
      <div class="telemetry-item accent-blue">
        <span class="telemetry-icon"><el-icon><User /></el-icon></span>
        <div><strong>{{ dashboard.overview.userCount ?? 0 }}</strong><p>系统用户</p></div>
        <small>平台配置</small>
      </div>
      <div class="telemetry-item accent-mint">
        <span class="telemetry-icon"><el-icon><Odometer /></el-icon></span>
        <div><strong>{{ dashboard.overview.roleCount ?? 0 }}</strong><p>角色配置</p></div>
        <small>访问控制</small>
      </div>
      <div class="telemetry-item accent-amber">
        <span class="telemetry-icon"><el-icon><Warning /></el-icon></span>
        <div><strong>{{ unreadNotifications }}</strong><p>待关注通知</p></div>
        <small>来自事件与工单</small>
      </div>
    </section>

    <section class="desk-grid">
      <article class="activity-panel">
        <header class="panel-header">
          <div><p>协同动态</p><h3>近期工作提醒</h3></div>
          <button v-if="authStore.hasPermission('ops:event:read')" type="button" @click="go('/events')">进入事件中心 <span>↗</span></button>
        </header>
        <div v-if="displayedNotifications.length" class="activity-list">
          <button v-for="item in displayedNotifications" :key="item.id" class="activity-row" type="button" @click="go(item.targetType === 'WORK_ORDER' ? '/work-orders' : '/events')">
            <span class="activity-status" :class="notificationTone(item)"></span>
            <div class="activity-content"><strong>{{ item.title }}</strong><p>{{ item.content || '系统已同步一条新的业务提醒。' }}</p></div>
            <time>{{ notificationTime(item.createdAt) }}</time>
          </button>
        </div>
        <div v-else class="quiet-state">
          <el-icon><Monitor /></el-icon><div><strong>当前没有新的协同提醒</strong><p>事件与工单的最新动态会显示在这里。</p></div>
        </div>
      </article>

      <section class="capability-panel">
        <header class="panel-header"><div><p>系统能力</p><h3>快速进入业务模块</h3></div><span>{{ availableModules.length }} 项可用</span></header>
        <div v-if="availableModules.length" class="capability-grid">
          <button v-for="item in availableModules" :key="item.path" class="capability-card" :class="`tone-${item.tone}`" type="button" @click="go(item.path)">
            <span class="capability-icon"><el-icon><component :is="item.icon" /></el-icon></span>
            <span class="capability-copy"><strong>{{ item.title }}</strong><small>{{ item.hint }}</small></span>
            <span class="capability-arrow">›</span>
          </button>
        </div>
        <div v-else class="quiet-state"><el-icon><Warning /></el-icon><div><strong>暂未分配业务模块权限</strong><p>请联系管理员配置当前账户的角色。</p></div></div>
      </section>
    </section>

    <section v-if="availableManagement.length" class="platform-strip">
      <div class="identity-block"><span class="identity-avatar"><el-icon><User /></el-icon></span><div><small>当前身份</small><strong>{{ authStore.nickname }}</strong><p>{{ roles.join(' / ') || '已登录用户' }}</p></div></div>
      <div class="management-links">
        <button v-for="item in availableManagement" :key="item.key" type="button" @click="go(item.path)">
          <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span><strong>{{ dashboard.overview[item.key] ?? 0 }}</strong><i>›</i>
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.command-desk{display:grid;gap:18px;padding-bottom:16px}.command-hero{position:relative;isolation:isolate;min-height:338px;overflow:hidden;border:1px solid rgb(99 218 243 / 24%);border-radius:18px;background:var(--hero-image) center 52%/cover;box-shadow:inset 0 1px rgb(224 254 255 / 9%),0 24px 54px rgb(1 8 17 / 35%)}.hero-shade{position:absolute;inset:0;z-index:-1;background:linear-gradient(90deg,rgb(3 15 26 / 97%) 0%,rgb(3 15 26 / 88%) 33%,rgb(3 15 26 / 35%) 64%,rgb(3 15 26 / 12%)),linear-gradient(0deg,rgb(3 14 24 / 64%),transparent 42%)}.hero-copy{display:grid;align-content:center;min-height:338px;max-width:490px;padding:38px 44px}.hero-overline,.panel-header p{display:flex;align-items:center;gap:8px;margin:0;color:#86deeb;font-size:11px;font-weight:700;letter-spacing:.15em;text-transform:uppercase}.hero-overline span{width:7px;height:7px;border-radius:50%;background:#70e5d3;box-shadow:0 0 0 5px rgb(112 229 211 / 12%)}.hero-copy h2{margin:13px 0 0;color:#f1fbff;font-size:clamp(34px,3.2vw,46px);font-weight:720;line-height:1.08;letter-spacing:-.065em;text-shadow:0 3px 24px rgb(0 4 12 / 55%)}.hero-description{margin:16px 0 0;color:#b8d0dc;font-size:14px;line-height:1.75}.hero-action{display:inline-flex;align-items:center;justify-content:center;gap:8px;width:max-content;min-height:40px;margin-top:25px;padding:0 17px;border:1px solid #4dbbc4;border-radius:10px;background:#207381;color:#effeff;cursor:pointer;font-weight:650;box-shadow:inset 0 1px rgb(224 255 255 / 16%),0 10px 22px rgb(0 9 18 / 32%);transition:transform .16s ease,background .16s ease}.hero-action:hover{background:#298492;transform:translateY(-1px)}.hero-action:active{transform:scale(.98)}.hero-map-meta{position:absolute;right:24px;bottom:18px;display:flex;align-items:center;gap:9px;color:rgb(198 234 239 / 74%);font-size:11px;letter-spacing:.05em}.hero-map-meta i{width:18px;height:1px;background:#63d5df}.telemetry-band{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.telemetry-item{--tone:#6edce8;display:grid;grid-template-columns:auto 1fr;align-items:center;gap:0 11px;min-height:103px;padding:16px 17px;border:1px solid rgb(111 190 218 / 14%);border-radius:13px;background:linear-gradient(145deg,rgb(15 36 54 / 96%),rgb(9 24 39 / 95%));box-shadow:inset 0 1px rgb(213 245 255 / 4%),0 12px 26px rgb(1 8 17 / 17%)}.telemetry-icon{display:grid;width:37px;height:37px;place-items:center;border:1px solid color-mix(in srgb,var(--tone) 30%,transparent);border-radius:11px;background:color-mix(in srgb,var(--tone) 11%,transparent);color:var(--tone);font-size:19px}.telemetry-item strong{display:block;color:#effaff;font-size:27px;line-height:1;letter-spacing:-.055em;font-variant-numeric:tabular-nums}.telemetry-item p{margin:5px 0 0;color:#a0bbca;font-size:12px}.telemetry-item small{grid-column:1/-1;margin-top:13px;color:#638398;font-size:11px}.accent-cyan{--tone:#6edce8}.accent-blue{--tone:#8fb7ff}.accent-mint{--tone:#71ddba}.accent-amber{--tone:#e6b75f}.desk-grid{display:grid;grid-template-columns:minmax(0,1.12fr) minmax(360px,.88fr);gap:18px}.activity-panel,.capability-panel{min-height:330px;padding:21px;border:1px solid rgb(111 190 218 / 14%);border-radius:15px;background:linear-gradient(150deg,rgb(13 32 49 / 96%),rgb(8 22 36 / 95%));box-shadow:inset 0 1px rgb(213 245 255 / 4%),0 14px 28px rgb(1 8 17 / 16%)}.panel-header{display:flex;align-items:end;justify-content:space-between;gap:16px}.panel-header h3{margin:7px 0 0;color:#eaf7fc;font-size:18px;font-weight:650;letter-spacing:-.03em}.panel-header button{padding:0;border:0;background:transparent;color:#80dfe6;cursor:pointer;font-size:12px}.panel-header button span{margin-left:4px;font-size:15px}.panel-header>span{color:#779bad;font-size:12px}.activity-list{margin-top:17px}.activity-row{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:12px;width:100%;padding:14px 0;border:0;border-bottom:1px solid rgb(127 190 213 / 11%);background:transparent;color:inherit;text-align:left;cursor:pointer;transition:background .16s ease}.activity-row:first-child{border-top:1px solid rgb(127 190 213 / 11%)}.activity-row:hover{background:rgb(57 166 190 / 6%)}.activity-status{width:7px;height:7px;border-radius:50%;background:#6edce8;box-shadow:0 0 0 4px rgb(110 220 232 / 8%)}.activity-status.warning{background:#e7b65d;box-shadow:0 0 0 4px rgb(231 182 93 / 9%)}.activity-status.work{background:#6cd8a9;box-shadow:0 0 0 4px rgb(108 216 169 / 9%)}.activity-content{min-width:0}.activity-content strong{display:block;overflow:hidden;color:#deeff6;font-size:13px;font-weight:620;text-overflow:ellipsis;white-space:nowrap}.activity-content p{display:-webkit-box;overflow:hidden;margin:5px 0 0;color:#7798ab;font-size:11px;line-height:1.45;-webkit-line-clamp:1;-webkit-box-orient:vertical}.activity-row time{color:#6f8da1;font-size:11px;white-space:nowrap}.capability-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px;margin-top:18px}.capability-card{--tone:#75dbe8;display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:9px;min-height:72px;padding:10px 11px;border:1px solid rgb(117 190 220 / 13%);border-radius:11px;background:rgb(9 25 40 / 42%);color:inherit;text-align:left;cursor:pointer;transition:transform .16s ease,border-color .16s ease,background .16s ease}.capability-card:hover{border-color:color-mix(in srgb,var(--tone) 42%,#315269);background:color-mix(in srgb,var(--tone) 7%,rgb(9 25 40));transform:translateY(-2px)}.capability-card:active{transform:scale(.98)}.capability-icon{display:grid;width:32px;height:32px;place-items:center;border-radius:9px;background:color-mix(in srgb,var(--tone) 12%,transparent);color:var(--tone);font-size:17px}.capability-copy{min-width:0}.capability-copy strong{display:block;overflow:hidden;color:#ddedf5;font-size:13px;font-weight:620;text-overflow:ellipsis;white-space:nowrap}.capability-copy small{display:block;overflow:hidden;margin-top:4px;color:#718ea1;font-size:10px;text-overflow:ellipsis;white-space:nowrap}.capability-arrow{color:color-mix(in srgb,var(--tone) 72%,#d3edf4);font-size:24px;line-height:1}.tone-cyan{--tone:#70e3d9}.tone-blue{--tone:#88b9ff}.tone-amber{--tone:#e4b65d}.tone-mint{--tone:#72ddb1}.tone-sky{--tone:#74d2ef}.tone-slate{--tone:#b0c5d4}.tone-violet{--tone:#af9fe9}.quiet-state{display:flex;align-items:center;gap:13px;min-height:210px;color:#89a6b7}.quiet-state>.el-icon{display:grid;flex:none;width:40px;height:40px;place-items:center;border:1px solid rgb(117 199 216 / 17%);border-radius:12px;background:rgb(69 184 202 / 8%);color:#6edbe7;font-size:19px}.quiet-state strong{color:#dbeef5;font-size:13px}.quiet-state p{margin:6px 0 0;font-size:12px}.platform-strip{display:grid;grid-template-columns:minmax(220px,.8fr) minmax(0,2.2fr);align-items:center;gap:20px;padding:15px 18px;border:1px solid rgb(111 190 218 / 13%);border-radius:14px;background:rgb(11 29 45 / 78%)}.identity-block{display:flex;align-items:center;gap:11px}.identity-avatar{display:grid;width:38px;height:38px;place-items:center;border-radius:11px;background:rgb(86 211 205 / 11%);color:#78e3d2;font-size:18px}.identity-block small,.identity-block p{color:#7796a8;font-size:11px}.identity-block strong{display:block;margin-top:3px;color:#e6f4fa;font-size:13px}.identity-block p{margin:3px 0 0}.management-links{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:7px}.management-links button{display:grid;grid-template-columns:auto 1fr auto auto;align-items:center;gap:8px;min-height:40px;padding:0 9px;border:0;border-radius:9px;background:transparent;color:#a1bdcd;cursor:pointer;text-align:left;transition:background .16s ease}.management-links button:hover{background:rgb(71 178 199 / 9%)}.management-links .el-icon{color:#72dce7;font-size:15px}.management-links span{overflow:hidden;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.management-links strong{color:#e3f3f9;font-size:15px;font-variant-numeric:tabular-nums}.management-links i{color:#6490a2;font-size:18px;font-style:normal}@media (max-width:1250px){.desk-grid{grid-template-columns:1fr}.capability-panel{min-height:0}.platform-strip{grid-template-columns:1fr}.management-links{border-top:1px solid rgb(111 190 218 / 13%);padding-top:11px}}@media (max-width:900px){.command-hero{min-height:310px}.hero-copy{padding:34px 28px}.telemetry-band{grid-template-columns:repeat(2,minmax(0,1fr))}.hero-map-meta{display:none}}@media (max-width:620px){.command-desk{gap:14px}.hero-copy{min-height:300px;padding:28px 20px}.hero-copy h2{font-size:34px}.telemetry-band,.capability-grid,.management-links{grid-template-columns:1fr}.telemetry-item{min-height:82px}.telemetry-item small{display:none}.desk-grid{gap:14px}.activity-panel,.capability-panel{padding:17px}.platform-strip{gap:13px;padding:15px}.management-links{gap:4px}.management-links button{min-height:38px}}@media (prefers-reduced-motion:reduce){.hero-action,.activity-row,.capability-card,.management-links button{transition:none}}
</style>
