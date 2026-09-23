<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Clock, Collection, Delete, Loading, Notebook, Plus, RefreshRight, Timer, WarningFilled } from '@element-plus/icons-vue'
import { acceptWorkOrder, addWorkOrderProgress, assignWorkOrder, createWorkOrder, deleteWorkOrder, getWorkOrderDetail, listEvents, listWorkOrders, updateWorkOrderStatus } from '@/api/operations'
import { listUsers } from '@/api/user'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const dialog = ref(false)
const status = ref('')
const orders = ref([])
const events = ref([])
const users = ref([])
const collaborationDialog = ref(false)
const detailDialog = ref(false)
const progressDialog = ref(false)
const selectedOrder = ref(null)
const detail = ref(null)
const assignment = reactive({ assigneeId: null })
const progressForm = reactive({ content: '', imageUrls: '', longitude: null, latitude: null, statusAfter: '' })
const progressMap = ref(null)
const timelineMaps = ref([])
let progressMapInstance
let progressMarker
let timelineMapInstances = []
const canWrite = computed(() => auth.hasPermission('ops:work-order:write'))
const canAssign = computed(() => auth.hasPermission('ops:work-order:assign'))
const canProgress = computed(() => auth.hasPermission('ops:work-order:progress'))
const canAccept = computed(() => auth.hasPermission('ops:work-order:accept'))
const selectableUsers = computed(() => users.value.filter((user) => user.id !== auth.user?.id && user.status === 'ENABLED'))
const isAssignee = (order) => order?.assigneeId === auth.user?.id
const form = reactive({ eventId: null, title: '', description: '', priority: 'YELLOW', assigneeId: null, dueAt: null })
const statusText = { TODO: '待处置', PROCESSING: '处理中', PENDING_ACCEPTANCE: '待验收', DONE: '已完成' }
const priorityText = { RED: '红色', ORANGE: '橙色', YELLOW: '黄色', BLUE: '蓝色' }
const counts = computed(() => ({
  all: orders.value.length,
  todo: orders.value.filter((item) => item.status === 'TODO').length,
  processing: orders.value.filter((item) => item.status === 'PROCESSING').length,
  pendingAcceptance: orders.value.filter((item) => item.status === 'PENDING_ACCEPTANCE').length,
  done: orders.value.filter((item) => item.status === 'DONE').length,
}))
const share = (value) => {
  const total = counts.value.all
  if (!total) return 0
  return Math.round((value / total) * 100)
}
const formatTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '未设置时限'

async function load() { loading.value = true; try { orders.value = await listWorkOrders(status.value ? { status: status.value } : {}) } finally { loading.value = false } }
async function openCreate() { events.value = await listEvents({ status: 'OPEN' }); await loadUsers(); if (!events.value.length) return ElMessage.warning('请先在事件中心登记一条开放事件'); Object.assign(form, { eventId: events.value[0].id, title: '', description: '', priority: 'YELLOW', assigneeId: null, dueAt: null }); dialog.value = true }
async function save() { if (!form.eventId || !form.title.trim() || !form.assigneeId) return ElMessage.warning('请选择事件、负责人并填写工单标题'); await createWorkOrder({ ...form, title: form.title.trim(), dueAt: form.dueAt || null }); dialog.value = false; ElMessage.success('工单已创建并已指派负责人'); await load() }
async function advance(row) { const next = row.status === 'TODO' ? 'PROCESSING' : 'DONE'; await updateWorkOrderStatus(row.id, next); ElMessage.success(`工单已更新为${statusText[next]}`); await load() }
async function remove(row) { await ElMessageBox.confirm(`确定删除工单“${row.title}”吗？该操作会一并清除关联的站内通知。`, '删除工单', { type: 'warning' }); await deleteWorkOrder(row.id); ElMessage.success('工单已删除'); await load() }
async function loadUsers() { const page = await listUsers({ current: 1, size: 100 }); users.value = (page.records || []).filter((user) => user.id !== auth.user?.id && user.status === 'ENABLED') }
async function openCollaboration(row) { await loadUsers(); selectedOrder.value = row; assignment.assigneeId = row.assigneeId; collaborationDialog.value = true }
async function saveAssignment() { if (!assignment.assigneeId) return ElMessage.warning('请选择负责人'); await assignWorkOrder(selectedOrder.value.id, { assigneeId: assignment.assigneeId }); ElMessage.success('负责人已指派，并已发送待办提醒'); collaborationDialog.value = false; await load() }
const TIANDITU_TOKEN = import.meta.env.VITE_TIANDITU_TOKEN || '38ca5876c8ba7b71eb08803d408b6184'
const mapOptions = { subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'], maxZoom: 18, attribution: '天地图' }
function addTianDiTuLayers(map) {
  L.tileLayer(`https://t{s}.tianditu.gov.cn/img_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=img&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${TIANDITU_TOKEN}`, mapOptions).addTo(map)
  L.tileLayer(`https://t{s}.tianditu.gov.cn/cia_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=cia&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${TIANDITU_TOKEN}`, mapOptions).addTo(map)
}
function destroyProgressMap() { progressMapInstance?.remove(); progressMapInstance = undefined; progressMarker = undefined }
function destroyTimelineMaps() { timelineMapInstances.forEach((map) => map.remove()); timelineMapInstances = [] }
async function openDetail(row) { selectedOrder.value = row; detail.value = await getWorkOrderDetail(row.id); detailDialog.value = true; await nextTick(); initTimelineMaps() }
async function openProgress(row) {
  selectedOrder.value = row
  Object.assign(progressForm, { content: '', imageUrls: '', longitude: null, latitude: null, statusAfter: row.status === 'TODO' ? 'PROCESSING' : '' })
  progressDialog.value = true
  await nextTick()
  initProgressMap()
}
function initProgressMap() {
  if (!progressMap.value) return
  destroyProgressMap()
  progressMapInstance = L.map(progressMap.value, { attributionControl: false }).setView([30.0, 120.0], 7)
  addTianDiTuLayers(progressMapInstance)
  progressMapInstance.on('click', (event) => {
    progressForm.longitude = Number(event.latlng.lng.toFixed(6)); progressForm.latitude = Number(event.latlng.lat.toFixed(6))
    if (progressMarker) progressMarker.remove()
    progressMarker = L.circleMarker(event.latlng, { radius: 8, color: '#0b7189', weight: 2, fillColor: '#59e1d1', fillOpacity: .92 }).addTo(progressMapInstance)
  })
  window.setTimeout(() => progressMapInstance?.invalidateSize(), 80)
  window.setTimeout(() => progressMapInstance?.invalidateSize(), 280)
}
function initTimelineMaps() {
  destroyTimelineMaps()
  const items = (detail.value?.progress || []).filter((item) => item.longitude != null && item.latitude != null)
  timelineMaps.value.forEach((element, index) => {
    const item = items[index]
    if (!element || !item) return
    const map = L.map(element, { zoomControl: false, attributionControl: false, dragging: false, scrollWheelZoom: false, doubleClickZoom: false, touchZoom: false }).setView([item.latitude, item.longitude], 13)
    addTianDiTuLayers(map)
    L.circleMarker([item.latitude, item.longitude], { radius: 8, color: '#0b7189', weight: 2, fillColor: '#59e1d1', fillOpacity: .95 }).addTo(map)
    timelineMapInstances.push(map)
    window.setTimeout(() => map.invalidateSize(), 100)
  })
}
async function saveProgress(statusAfter = progressForm.statusAfter) {
  if (!progressForm.content.trim()) return ElMessage.warning('请填写处置进展')
  const { imageUrls, ...payload } = progressForm
  await addWorkOrderProgress(selectedOrder.value.id, { ...payload, statusAfter, content: progressForm.content.trim() })
  progressDialog.value = false; destroyProgressMap(); await load()
  ElMessage.success('处置进展已记录')
}
async function completeDisposal(row) {
  await ElMessageBox.confirm('确认现场处置已完成，并提交管理员验收吗？', '提交验收', { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' })
  await addWorkOrderProgress(row.id, { content: '负责人已确认现场处置完成，提交管理员验收。', longitude: null, latitude: null, statusAfter: 'PENDING_ACCEPTANCE' })
  ElMessage.success('已提交管理员验收')
  await load()
}
async function accept(row, approved) {
  const action = approved ? '验收通过' : '退回处置'
  const { value } = await ElMessageBox.prompt(`请填写${action}说明`, action, { inputPattern: /\S+/, inputErrorMessage: '请填写验收说明', confirmButtonText: '确认', cancelButtonText: '取消' })
  await acceptWorkOrder(row.id, { approved, comment: value.trim() })
  detailDialog.value = false; await load(); ElMessage.success(`工单已${action}`)
}
onBeforeUnmount(() => { destroyProgressMap(); destroyTimelineMaps() })
onMounted(load)
</script>

<template>
  <div class="page work-orders-page">
    <section class="work-header">
      <div class="work-header-copy">
        <p class="eyebrow">Response workflow</p>
        <h2>事件处置工单</h2>
        <p>把风险事件拆解为可执行、可追踪的现场处置任务。</p>
      </div>
      <div class="work-header-actions">
        <el-button
          v-if="canWrite"
          class="cmd-btn"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          创建处置工单
        </el-button>
      </div>
    </section>

    <section class="work-summary">
      <button
        type="button"
        class="summary-card is-lead tone-all"
        :class="{ active: !status }"
        @click="status = ''; load()"
      >
        <span class="summary-icon" aria-hidden="true"><el-icon><Collection /></el-icon></span>
        <div class="summary-copy">
          <small>全部工单</small>
          <strong>{{ counts.all }}</strong>
          <em>处置队列总览</em>
        </div>
        <span class="summary-meter" aria-hidden="true"><b style="width: 100%" /></span>
      </button>

      <button
        type="button"
        class="summary-card tone-todo"
        :class="{ active: status === 'TODO' }"
        @click="status = 'TODO'; load()"
      >
        <span class="summary-icon" aria-hidden="true"><el-icon><Clock /></el-icon></span>
        <div class="summary-copy">
          <small>待处置</small>
          <strong>{{ counts.todo }}</strong>
          <em>占队列 {{ share(counts.todo) }}%</em>
        </div>
        <span class="summary-meter" aria-hidden="true"><b :style="{ width: `${share(counts.todo)}%` }" /></span>
      </button>

      <button
        type="button"
        class="summary-card tone-processing"
        :class="{ active: status === 'PROCESSING' }"
        @click="status = 'PROCESSING'; load()"
      >
        <span class="summary-icon" aria-hidden="true"><el-icon><Loading /></el-icon></span>
        <div class="summary-copy">
          <small>处理中</small>
          <strong>{{ counts.processing }}</strong>
          <em>占队列 {{ share(counts.processing) }}%</em>
        </div>
        <span class="summary-meter" aria-hidden="true"><b :style="{ width: `${share(counts.processing)}%` }" /></span>
      </button>

      <button
        type="button"
        class="summary-card tone-done"
        :class="{ active: status === 'DONE' }"
        @click="status = 'DONE'; load()"
      >
        <span class="summary-icon" aria-hidden="true"><el-icon><CircleCheck /></el-icon></span>
        <div class="summary-copy">
          <small>已完成</small>
          <strong>{{ counts.done }}</strong>
          <em>占队列 {{ share(counts.done) }}%</em>
        </div>
        <span class="summary-meter" aria-hidden="true"><b :style="{ width: `${share(counts.done)}%` }" /></span>
      </button>
    </section>

    <section class="work-list-head">
      <div class="work-list-copy">
        <span class="work-list-eyebrow">WORK ORDER QUEUE</span>
        <strong>{{ status ? statusText[status] : '全部工单' }}</strong>
      </div>
      <el-select v-model="status" clearable placeholder="按状态筛选" @change="load">
        <el-option v-for="(label, key) in statusText" :key="key" :label="label" :value="key" />
      </el-select>
    </section>

    <section v-loading="loading" class="work-list">
      <article
        v-for="order in orders"
        :key="order.id"
        class="work-card"
        :class="`priority-${order.priority?.toLowerCase() || 'yellow'}`"
      >
        <div class="work-marker"><el-icon><WarningFilled /></el-icon></div>
        <div class="work-main">
          <div class="work-meta">
            <span>工单 #{{ order.id }}</span>
            <span>{{ order.eventTitle || '未关联事件' }}</span>
          </div>
          <h3>{{ order.title }}</h3>
          <p>{{ order.description || '暂无补充处置要求，请结合关联事件开展现场核查。' }}</p>
          <div class="work-footer">
            <span><el-icon><Timer /></el-icon>{{ formatTime(order.dueAt) }}</span>
            <span class="priority-chip" :class="`priority-chip-${order.priority?.toLowerCase() || 'yellow'}`">
              {{ priorityText[order.priority] || order.priority }}
            </span>
          </div>
        </div>
        <div class="work-side">
          <span class="status-tag" :class="`status-${order.status?.toLowerCase() || 'todo'}`">
            <i aria-hidden="true" />
            {{ statusText[order.status] || order.status }}
          </span>
          <el-button class="cmd-btn cmd-btn-soft" size="small" @click="openDetail(order)">查看处置进展</el-button>
          <div v-if="canWrite || canAssign || canProgress" class="work-actions">
            <el-button
              v-if="canAssign"
              class="cmd-btn cmd-btn-soft"
              size="small"
              :icon="RefreshRight"
              @click="openCollaboration(order)"
            >
              重新指派
            </el-button>
            <el-button
              v-if="canProgress && isAssignee(order) && !['DONE', 'PENDING_ACCEPTANCE'].includes(order.status)"
              class="cmd-btn cmd-btn-action"
              size="small"
              type="primary"
              :icon="CircleCheck"
              @click="openProgress(order)"
            >
              {{ order.status === 'TODO' ? '开始处置' : '填写进展' }}
            </el-button>
            <el-button
              v-if="canProgress && isAssignee(order) && order.status === 'PROCESSING'"
              class="cmd-btn cmd-btn-action"
              size="small"
              type="primary"
              :icon="CircleCheck"
              @click="completeDisposal(order)"
            >
              处置完成
            </el-button>
            <el-button
              v-if="canWrite"
              class="cmd-btn cmd-btn-danger"
              size="small"
              :icon="Delete"
              @click="remove(order)"
            >
              删除
            </el-button>
          </div>
        </div>
      </article>

      <div v-if="!loading && !orders.length" class="work-empty">
        <el-icon><CircleCheck /></el-icon>
        <strong>当前没有工单</strong>
        <p>从开放事件创建第一张处置工单，开始跟进。</p>
      </div>
    </section>

    <el-dialog v-model="dialog" title="创建处置工单" width="560px" destroy-on-close append-to-body align-center>
      <el-form label-position="top">
        <el-form-item label="关联事件" required>
          <el-select v-model="form.eventId" placeholder="请选择需要处置的开放事件">
            <el-option v-for="event in events" :key="event.id" :label="event.title" :value="event.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人" required>
          <el-select v-model="form.assigneeId" filterable placeholder="选择负责处置的人员">
            <el-option v-for="user in users" :key="user.id" :label="user.nickname || user.username" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="工单标题" required>
          <el-input v-model="form.title" maxlength="160" placeholder="例如：安排人员核查东港码头积水情况" />
        </el-form-item>
        <div class="form-pair">
          <el-form-item label="优先级">
            <el-select v-model="form.priority" placeholder="请选择处置优先级">
              <el-option v-for="(label, key) in priorityText" :key="key" :label="label" :value="key" />
            </el-select>
          </el-form-item>
          <el-form-item label="完成时限">
            <el-date-picker v-model="form.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择预计完成时间" />
          </el-form-item>
        </div>
        <el-form-item label="处置要求">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="说明执行步骤、现场联系人或需要反馈的处置结果"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="cmd-btn-ghost" @click="dialog = false">取消</el-button>
        <el-button class="cmd-btn" type="primary" @click="save">创建工单</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="collaborationDialog" title="工单指派与协同" width="500px" append-to-body align-center>
      <el-form label-position="top"><el-form-item label="负责人" required><el-select v-model="assignment.assigneeId" filterable placeholder="选择负责处理的人员"><el-option v-for="user in users" :key="user.id" :label="user.nickname || user.username" :value="user.id" /></el-select></el-form-item></el-form>
      <template #footer>
        <el-button class="cmd-btn-ghost" @click="collaborationDialog = false">取消</el-button>
        <el-button class="cmd-btn" type="primary" @click="saveAssignment">保存并通知</el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="detailDialog"
      title="处置进展"
      width="720px"
      append-to-body
      align-center
      class="progress-dialog"
      @closed="destroyTimelineMaps"
    >
      <div class="progress-timeline" :class="{ 'is-empty': !detail?.progress?.length }">
        <ol v-if="detail?.progress?.length" class="timeline-list">
          <li
            v-for="(item, index) in detail.progress"
            :key="item.id"
            class="timeline-item"
            :class="{ 'is-last': index === detail.progress.length - 1 }"
          >
            <div class="timeline-marker" aria-hidden="true">
              <svg class="timeline-node" viewBox="0 0 20 20" width="20" height="20">
                <circle cx="10" cy="10" r="5.5" fill="#0d2433" stroke="#4eb8d0" stroke-width="2.5" />
              </svg>
            </div>
            <div class="timeline-card">
              <div class="timeline-head">
                <strong>{{ item.createdByName || '系统记录' }}</strong>
                <time>{{ formatTime(item.createdAt) }}</time>
              </div>
              <p>{{ item.content }}</p>
              <template v-if="item.longitude != null && item.latitude != null">
                <div ref="timelineMaps" class="timeline-map" />
                <small>天地图定位：{{ item.longitude }}, {{ item.latitude }}</small>
              </template>
              <div v-if="item.imageUrls?.length" class="progress-images">
                <a v-for="url in item.imageUrls" :key="url" :href="url" target="_blank" rel="noopener">查看现场图片</a>
              </div>
            </div>
          </li>
        </ol>

        <div v-else class="timeline-empty">
          <span class="timeline-empty-icon" aria-hidden="true"><el-icon><Notebook /></el-icon></span>
          <strong>暂无处置记录</strong>
          <p>负责人开始处置并填写进展后，这里会按时间顺序展示现场反馈与定位信息。</p>
        </div>
      </div>
      <template #footer>
        <el-button class="cmd-btn-ghost" @click="detailDialog = false">关闭</el-button>
        <template v-if="canAccept && selectedOrder?.status === 'PENDING_ACCEPTANCE'">
          <el-button class="cmd-btn cmd-btn-danger" @click="accept(selectedOrder, false)">退回处置</el-button>
          <el-button class="cmd-btn" type="primary" @click="accept(selectedOrder, true)">验收通过</el-button>
        </template>
      </template>
    </el-dialog>
    <el-dialog v-model="progressDialog" :title="selectedOrder?.status === 'TODO' ? '开始处置' : '填写处置进展'" width="620px" append-to-body align-center @closed="destroyProgressMap">
      <el-form label-position="top" class="progress-form">
        <el-form-item label="处置进展"><el-input v-model="progressForm.content" type="textarea" :rows="3" placeholder="填写现场处置情况和处理结果" /></el-form-item>
        <el-form-item label="现场处置定位">
          <div ref="progressMap" class="progress-map" />
          <p class="coordinate-value">{{ progressForm.longitude == null ? '请在天地图中点击选择现场位置' : `经度 ${progressForm.longitude}，纬度 ${progressForm.latitude}` }}</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="cmd-btn-ghost" @click="progressDialog = false">取消</el-button>
        <el-button class="cmd-btn" type="primary" plain @click="saveProgress()">记录进展</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style>
.work-orders-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 8px 4px 30px;
}

.work-header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
  padding: 20px 24px;
  border: 1px solid #4f9fc72b;
  border-radius: 16px;
  background: linear-gradient(120deg, #0e2c40, #102036 54%, #0c1828);
  box-shadow: inset 0 1px #7be9ff16;
}

.work-header-copy .eyebrow {
  margin: 0 0 8px;
  color: #57dff5;
  font-size: 11px;
  letter-spacing: .16em;
  text-transform: uppercase;
}

.work-header h2 {
  margin: 0 0 8px;
  color: #eff9ff;
  font-size: 30px;
  letter-spacing: -.04em;
}

.work-header-copy > p:last-child {
  margin: 0;
  color: #93aec4;
  font-size: 14px;
  line-height: 1.6;
}

.work-header-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.work-summary {
  display: grid;
  grid-template-columns: minmax(200px, 1.28fr) repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 18px 0;
}

.summary-card {
  --tone: #5eb8d0;
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  grid-template-rows: auto auto;
  column-gap: 12px;
  row-gap: 14px;
  align-items: start;
  min-height: 112px;
  padding: 16px 16px 14px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--tone) 26%, #24384c);
  border-radius: 16px;
  background:
    radial-gradient(circle at var(--glow-x, 92%) var(--glow-y, 18%), color-mix(in srgb, var(--tone) 18%, transparent), transparent 46%),
    linear-gradient(160deg, color-mix(in srgb, var(--tone) 12%, #102233), #0a1521f2 70%);
  color: #9ab3c6;
  font: inherit;
  text-align: left;
  cursor: pointer;
  appearance: none;
  transition:
    border-color .18s ease,
    background .18s ease,
    transform .18s ease,
    box-shadow .18s ease,
    color .18s ease;
}

.summary-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--tone) 88%, #fff), color-mix(in srgb, var(--tone) 35%, transparent));
  opacity: .7;
}

.summary-card::after {
  content: '';
  position: absolute;
  inset: auto -18% -42% auto;
  width: 120px;
  height: 120px;
  border: 1px solid color-mix(in srgb, var(--tone) 22%, transparent);
  border-radius: 50%;
  opacity: .45;
  pointer-events: none;
}

.summary-card:hover {
  border-color: color-mix(in srgb, var(--tone) 58%, #3a5a70);
  color: #d7ebf6;
  transform: translateY(-2px);
  box-shadow: 0 14px 28px #00101d4a;
}

.summary-card.active {
  border-color: color-mix(in srgb, var(--tone) 66%, #fff 8%);
  background:
    radial-gradient(circle at var(--glow-x, 92%) var(--glow-y, 18%), color-mix(in srgb, var(--tone) 26%, transparent), transparent 48%),
    linear-gradient(160deg, color-mix(in srgb, var(--tone) 20%, #133247), #0d1d2cf5 72%);
  color: #eaf6ff;
  box-shadow:
    inset 0 0 0 1px color-mix(in srgb, var(--tone) 16%, transparent),
    0 14px 28px #00101d50;
}

.summary-card.is-lead {
  --glow-x: 12%;
  --glow-y: 88%;
  min-height: 112px;
  padding: 18px 18px 15px;
}

.summary-card.is-lead::after {
  inset: -28% auto auto 58%;
  width: 150px;
  height: 150px;
}

.summary-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid color-mix(in srgb, var(--tone) 40%, transparent);
  border-radius: 12px;
  background: color-mix(in srgb, var(--tone) 14%, #0a1824);
  color: var(--tone);
  font-size: 18px;
  box-shadow: inset 0 1px color-mix(in srgb, var(--tone) 18%, transparent);
}

.summary-card.is-lead .summary-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  font-size: 20px;
}

.summary-copy {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.summary-copy small {
  color: inherit;
  font-size: 12px;
  letter-spacing: .03em;
  opacity: .88;
}

.summary-copy strong {
  color: #f1f8ff;
  font: 700 30px/1 ui-monospace, Consolas, monospace;
  letter-spacing: -.05em;
}

.summary-card.is-lead .summary-copy strong {
  font-size: 36px;
}

.summary-copy em {
  color: color-mix(in srgb, var(--tone) 55%, #8eabbf);
  font-size: 11px;
  font-style: normal;
  letter-spacing: .04em;
}

.summary-meter {
  grid-column: 1 / -1;
  display: block;
  height: 4px;
  overflow: hidden;
  border-radius: 999px;
  background: #132536;
  box-shadow: inset 0 1px #02080f66;
}

.summary-meter b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, color-mix(in srgb, var(--tone) 55%, #1a3344), var(--tone));
  box-shadow: 0 0 10px color-mix(in srgb, var(--tone) 35%, transparent);
  transition: width .28s ease;
}

.summary-card.tone-all { --tone: #4eb8d0; --glow-x: 14%; --glow-y: 86%; }
.summary-card.tone-todo { --tone: #7ea7bd; --glow-x: 88%; --glow-y: 16%; }
.summary-card.tone-processing { --tone: #e0b04a; --glow-x: 18%; --glow-y: 12%; }
.summary-card.tone-done { --tone: #5ecf9a; --glow-x: 84%; --glow-y: 82%; }

.tone-processing .summary-icon .el-icon {
  animation: summary-spin 2.4s linear infinite;
}

@keyframes summary-spin {
  to { transform: rotate(360deg); }
}

.work-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 24px 2px 10px;
}

.work-list-eyebrow {
  color: #6ebed3;
  font: 700 10px/1 monospace;
  letter-spacing: .14em;
}

.work-list-copy strong {
  display: block;
  margin-top: 4px;
  color: #d9ebf8;
  font-size: 15px;
}

.work-list-head .el-select {
  width: 174px;
}

.work-list-head .el-select__wrapper {
  min-height: 34px;
  background: #0c1b2c;
  box-shadow: 0 0 0 1px #30445d inset;
}

.work-list-head .el-select__selected-item,
.work-list-head .el-select__placeholder,
.work-list-head .el-select__placeholder span {
  color: #93aec4;
  font: 400 13px/1.2 'Aptos', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  letter-spacing: normal;
}

.work-list-head .el-select__selected-item {
  color: #d9ebf8;
}

.work-list {
  display: grid;
  gap: 10px;
}

.work-card {
  position: relative;
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) auto;
  gap: 14px;
  padding: 17px 18px;
  overflow: hidden;
  border: 1px solid #31516a;
  border-radius: 13px;
  background: #0c1b2be8;
  box-shadow: 0 8px 20px #02081328;
}

.work-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--priority-color, #e7cf59);
}

.priority-red { --priority-color: #fa596d; }
.priority-orange { --priority-color: #ff9a48; }
.priority-yellow { --priority-color: #efd35e; }
.priority-blue { --priority-color: #56bdf5; }

.work-marker {
  display: grid;
  place-items: center;
  align-self: start;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: var(--priority-color);
  background: color-mix(in srgb, var(--priority-color) 15%, #102335);
}

.work-main {
  min-width: 0;
}

.work-meta {
  display: flex;
  gap: 10px;
  color: #7596ad;
  font-size: 12px;
}

.work-meta span + span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.work-main h3 {
  margin: 6px 0;
  color: #edf8ff;
  font-size: 16px;
}

.work-main p {
  margin: 0;
  color: #8eaabe;
  font-size: 13px;
  line-height: 1.6;
}

.work-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  color: #7898ad;
  font-size: 12px;
}

.work-footer span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.work-side {
  display: flex;
  flex-direction: column;
  align-items: end;
  justify-content: space-between;
  gap: 14px;
  min-width: 126px;
}

.priority-chip,
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 10px;
  border: 1px solid color-mix(in srgb, var(--tone, #7eb8cc) 42%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--tone, #7eb8cc) 12%, #0a1824);
  color: color-mix(in srgb, var(--tone, #9ed4e4) 78%, #fff 22%);
  font-size: 12px;
  font-weight: 650;
  letter-spacing: .02em;
  line-height: 1;
  white-space: nowrap;
  box-shadow: inset 0 1px color-mix(in srgb, var(--tone, #9ed4e4) 14%, transparent);
}

.priority-chip-red { --tone: #f06a7a; }
.priority-chip-orange { --tone: #f0a35a; }
.priority-chip-yellow { --tone: #dfc55a; }
.priority-chip-blue { --tone: #5bb8e4; }

.status-tag i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--tone, #8ec9da);
  box-shadow: 0 0 8px color-mix(in srgb, var(--tone, #8ec9da) 70%, transparent);
}

.status-todo {
  --tone: #7ea7bd;
}

.status-processing {
  --tone: #e0b04a;
}

.status-pending_acceptance {
  --tone: #c98ce8;
}

.status-done {
  --tone: #5ecf9a;
}

.work-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.work-actions .el-button + .el-button {
  margin-left: 0;
}

.work-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 60px 24px;
  border: 1px dashed #3c627b;
  border-radius: 13px;
  color: #8eaabe;
  text-align: center;
}

.work-empty .el-icon {
  font-size: 30px;
  color: #54d6ee;
}

.work-empty strong {
  color: #e6f5ff;
}

.work-empty p {
  margin: 0;
  font-size: 13px;
}

.form-pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.form-pair .el-date-editor {
  width: 100%;
}

.collaboration-members {
  display: flex;
  gap: 18px;
  margin-bottom: 16px;
  color: #9cb7ca;
  font-size: 13px;
}

.collaboration-members strong {
  color: #e7f5ff;
}

.progress-timeline {
  max-height: 540px;
  margin: 2px 0 4px;
  overflow: auto;
  scrollbar-width: none;
}

.progress-timeline::-webkit-scrollbar {
  display: none;
}

.progress-timeline.is-empty {
  display: grid;
  place-items: center;
  min-height: 220px;
  padding: 8px;
}

.timeline-list {
  position: relative;
  margin: 0;
  padding: 4px 4px 4px 0;
  list-style: none;
}

/* One continuous center line through all markers (marker col = 28px, center = 14px) */
.timeline-list::before {
  content: '';
  position: absolute;
  top: 22px;
  bottom: 28px;
  left: 13px;
  width: 2px;
  background: linear-gradient(180deg, #4eb8d0aa, #355f76 55%, #2a4558);
  border-radius: 1px;
}

.timeline-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  column-gap: 14px;
  align-items: start;
  margin: 0;
  padding: 0 0 16px;
  color: #9db8ca;
}

.timeline-item.is-last {
  padding-bottom: 4px;
}

.timeline-marker {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 28px;
  height: 40px;
}

.timeline-node {
  display: block;
  flex: none;
  width: 20px;
  height: 20px;
  overflow: visible;
  filter: drop-shadow(0 0 7px #4eb8d066);
}

.timeline-card {
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid #31566e;
  border-radius: 12px;
  background: linear-gradient(160deg, #12283a, #0d1c2b);
  box-shadow: 0 8px 18px #02081328;
}

.timeline-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.timeline-card strong {
  color: #e7f5ff;
  font-size: 13px;
}

.timeline-card time {
  color: #6f91a8;
  font-size: 12px;
  white-space: nowrap;
}

.timeline-card > p {
  margin: 8px 0 0;
  color: #a9c2d3;
  font-size: 13px;
  line-height: 1.65;
}

.timeline-card > small {
  display: inline-block;
  margin-top: 8px;
  color: #6fbbcf;
  font-size: 12px;
}

.progress-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.progress-images a {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border: 1px solid #2f7a90;
  border-radius: 999px;
  background: #143f52;
  color: #9edceb;
  font-size: 12px;
  text-decoration: none;
  transition: background .16s ease, border-color .16s ease, color .16s ease;
}

.progress-images a:hover {
  border-color: #45a0b8;
  background: #1a5168;
  color: #e8f8ff;
}

.timeline-empty {
  display: grid;
  justify-items: center;
  gap: 10px;
  width: min(100%, 420px);
  padding: 36px 28px;
  border: 1px dashed #3d647c;
  border-radius: 16px;
  background:
    radial-gradient(circle at 50% 0%, #1a4a5e33, transparent 55%),
    linear-gradient(165deg, #122637, #0d1a27);
  color: #8eaabe;
  text-align: center;
}

.timeline-empty-icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border: 1px solid #3d7f96;
  border-radius: 16px;
  background: #14384a;
  color: #5ec9e0;
  font-size: 24px;
  box-shadow: inset 0 1px #9fd8ea22, 0 0 18px #3db8d022;
}

.timeline-empty strong {
  color: #e6f5ff;
  font-size: 15px;
}

.timeline-empty p {
  margin: 0;
  max-width: 32ch;
  color: #7f9bb0;
  font-size: 13px;
  line-height: 1.65;
}

.progress-form {
  padding-top: 0;
}

.progress-map {
  width: 100%;
  height: 260px;
  overflow: hidden;
  border: 1px solid #315c76;
  border-radius: 10px;
}

.coordinate-value {
  margin: 8px 0 0;
  color: #6ebed3;
  font-size: 12px;
}

.timeline-map {
  width: 100%;
  height: 176px;
  margin: 10px 0 0;
  overflow: hidden;
  border: 1px solid #315c76;
  border-radius: 8px;
}

@media (max-width: 720px) {
  .work-header {
    align-items: start;
    flex-direction: column;
  }

  .work-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-card.is-lead {
    grid-column: span 2;
  }

  .work-card {
    grid-template-columns: 36px minmax(0, 1fr);
  }

  .work-side {
    grid-column: 2;
    align-items: start;
    flex-direction: row;
    min-width: 0;
  }

  .form-pair {
    grid-template-columns: 1fr;
    gap: 0;
  }
}

@media (max-width: 460px) {
  .work-list-head {
    align-items: start;
    flex-direction: column;
  }

  .work-list-head .el-select {
    width: 100%;
  }

  .work-summary {
    gap: 7px;
  }

  .summary-card {
    min-height: 100px;
    padding: 14px;
  }

  .summary-copy strong,
  .summary-card.is-lead .summary-copy strong {
    font-size: 26px;
  }

  .work-header {
    padding: 18px;
  }

  .work-header-actions {
    width: 100%;
  }

  .work-header-actions .cmd-btn {
    width: 100%;
  }
}
</style>
