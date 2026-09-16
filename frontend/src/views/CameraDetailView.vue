<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Hls from 'hls.js'
import { ArrowLeft, CircleCloseFilled, DataAnalysis, Picture, RefreshRight, VideoCamera } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { cameraDevices, loadCameraDevices } from '@/stores/cameraDevices'
import { getCameraMonitoringRecord, listCameraMonitoringRecords, listCameraSnapshots } from '@/api/camera'
import classificationAtmosphere from '@/assets/monitoring/classification-atmosphere.png'
import metricWeather from '@/assets/monitoring/metric-weather.png'
import metricVisibility from '@/assets/monitoring/metric-visibility.png'
import metricBoats from '@/assets/monitoring/metric-boats.png'

// Kept in sync with the GIS preview until the device API exposes per-camera stream URLs.
const PREVIEW_STREAM_URL = 'https://gcalic.v.myalicdn.com/gc/hswlf_1/index.m3u8?contentid=2820180516001'

const route = useRoute()
const router = useRouter()
const videoEl = ref(null)
const loading = ref(true)
const playerError = ref('')
const activeTab = ref('live')
const snapshots = ref([])
const snapshotCurrent = ref(1)
const snapshotSize = 10
const snapshotTotal = ref(0)
const warnings = ref([])
const previewIndex = ref(-1)
const monitoringRecords = ref([])
const monitoringCurrent = ref(1)
const monitoringSize = 10
const monitoringTotal = ref(0)
const selectedMonitoringId = ref(null)
const monitoringDetails = ref({})
let hlsPlayer

const cameraId = computed(() => String(route.params.id))
const camera = computed(() => cameraDevices.value.find((item) => String(item.id) === cameraId.value))
const isOnline = computed(() => camera.value?.status === 'ONLINE')
const previewImages = computed(() => snapshots.value.filter((item) => item.image).map((item) => item.image))

function predictionList(source) {
  if (Array.isArray(source?.predictions)) return source.predictions
  if (Array.isArray(source)) return source
  return []
}

function topPrediction(source) {
  if (!source || typeof source !== 'object') return { label: '—', confidence: null }
  if (typeof source.top === 'string' && source.top) {
    return { label: source.top, confidence: Number.isFinite(source.confidence) ? source.confidence : null }
  }
  const ranked = [...predictionList(source)].sort((a, b) => Number(b?.confidence || 0) - Number(a?.confidence || 0))
  const best = ranked[0]
  if (!best) return { label: '—', confidence: null }
  return {
    label: best.class || best.label || '—',
    confidence: Number.isFinite(best.confidence) ? best.confidence : null,
  }
}

function formatConfidence(value) {
  if (!Number.isFinite(value)) return '—'
  return `${Math.round(value * 100)}%`
}

function formatAnalyzedAt(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 19)
  return formatDate(date)
}

function toDataUrl(value) {
  if (!value || typeof value !== 'string') return ''
  if (value.startsWith('data:image/')) return value
  return `data:image/jpeg;base64,${value.replace(/\s/g, '')}`
}

function toMonitoringView(record) {
  if (!record) return null
  const weather = topPrediction(record.weatherPredictions)
  const visibility = topPrediction(record.visibilityPredictions)
  const boats = predictionList(record.boatPredictions)
  const tracks = predictionList(record.trackedBoats)
  return {
    id: record.id,
    status: record.status,
    snapshotId: record.snapshotId,
    analyzedAt: formatAnalyzedAt(record.analyzedAt),
    errorMessage: record.errorMessage,
    weather,
    visibility,
    weatherClasses: predictionList(record.weatherPredictions),
    visibilityClasses: predictionList(record.visibilityPredictions),
    boatCount: Number.isFinite(record.visibleBoatCount) ? record.visibleBoatCount : boats.length,
    boats,
    tracks,
    image: toDataUrl(record.outputImage),
  }
}

const monitoringViews = computed(() => monitoringRecords.value
  .map((record) => toMonitoringView(monitoringDetails.value[record.id] || record)).filter(Boolean))
const monitoringView = computed(() => {
  const selected = monitoringViews.value.find((item) => item.id === selectedMonitoringId.value)
  return selected || monitoringViews.value[0] || null
})

async function selectMonitoring(id) {
  selectedMonitoringId.value = id
  if (!monitoringDetails.value[id]) {
    const detail = await getCameraMonitoringRecord(id)
    monitoringDetails.value = { ...monitoringDetails.value, [id]: detail }
  }
}

function openSnapshotPreview(item) {
  if (!item?.image) return
  const index = previewImages.value.indexOf(item.image)
  if (index < 0) return
  previewIndex.value = index
}

function formatDate(date = new Date()) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(date).replaceAll('/', '-')
}

function addWarning(level, content) {
  warnings.value.unshift({ id: crypto.randomUUID(), level, content, occurredAt: formatDate() })
}

async function loadSnapshots(current = snapshotCurrent.value) {
  if (!camera.value) return
  const result = await listCameraSnapshots(camera.value.id, current, snapshotSize)
  snapshots.value = (result.records || []).map((record) => ({ ...record, image: record.imageDataUrl || '' }))
  snapshotCurrent.value = result.current || current
  snapshotTotal.value = result.total || 0
}

function onSnapshotPageChange(current) {
  loadSnapshots(current)
}

async function loadMonitoringRecords(current = monitoringCurrent.value) {
  if (!camera.value) return
  const page = await listCameraMonitoringRecords(camera.value.id, current, monitoringSize)
  monitoringRecords.value = page.records || []
  monitoringCurrent.value = page.current || current
  monitoringTotal.value = page.total || 0
  if (!monitoringRecords.value.some((item) => item.id === selectedMonitoringId.value)) {
    selectedMonitoringId.value = monitoringRecords.value[0]?.id ?? null
  }
  if (selectedMonitoringId.value) await selectMonitoring(selectedMonitoringId.value)
}

function onMonitoringPageChange(current) {
  loadMonitoringRecords(current)
}

function formatFileSize(size) {
  if (!size) return '—'
  return size >= 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(1)} MB` : `${Math.ceil(size / 1024)} KB`
}

function destroyPlayer() {
  hlsPlayer?.destroy()
  hlsPlayer = undefined
  const video = videoEl.value
  if (!video) return
  video.pause()
  video.removeAttribute('src')
  video.load()
}

async function playStream() {
  destroyPlayer()
  playerError.value = ''
  await nextTick()
  const video = videoEl.value
  if (!video || !isOnline.value) return
  const startPlayback = () => video.play().catch(() => {
    playerError.value = '浏览器阻止了自动播放，请点击画面中央的播放按钮。'
  })

  if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = PREVIEW_STREAM_URL
    video.addEventListener('loadedmetadata', startPlayback, { once: true })
  } else if (Hls.isSupported()) {
    hlsPlayer = new Hls({ enableWorker: true, lowLatencyMode: true, backBufferLength: 30 })
    hlsPlayer.on(Hls.Events.ERROR, (_, data) => {
      if (!data.fatal) return
      if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
        playerError.value = '视频流无法连接或播放地址已过期。'
        addWarning('流连接异常', '视频流连接失败，系统正在尝试重新加载。')
        hlsPlayer?.startLoad()
      } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
        hlsPlayer?.recoverMediaError()
      } else {
        playerError.value = '播放器发生错误，请刷新视频流后重试。'
        addWarning('播放异常', '视频播放器发生不可恢复错误，请检查视频流状态。')
      }
    })
    hlsPlayer.loadSource(PREVIEW_STREAM_URL)
    hlsPlayer.attachMedia(video)
    hlsPlayer.on(Hls.Events.MANIFEST_PARSED, startPlayback)
  } else {
    playerError.value = '当前浏览器不支持 HLS 视频流播放。'
  }
}

async function initialize() {
  loading.value = true
  try {
    if (!cameraDevices.value.length) await loadCameraDevices()
    if (!camera.value) {
      ElMessage.warning('未找到该摄像头。')
      router.replace({ name: 'cameras' })
      return
    }
    await loadSnapshots()
    await loadMonitoringRecords()
    await playStream()
  } finally {
    loading.value = false
  }
}

onMounted(initialize)
onBeforeUnmount(() => {
  destroyPlayer()
})
</script>

<template>
  <div v-loading="loading" class="page camera-detail-page">
    <section class="detail-heading">
      <el-button text :icon="ArrowLeft" @click="router.push({ name: 'cameras' })">返回摄像头列表</el-button>
      <div class="detail-title">
        <div class="device-icon"><el-icon><VideoCamera /></el-icon></div>
        <div>
          <p class="eyebrow">Camera Monitoring Center</p>
          <h2>{{ camera?.name || '摄像头详情' }}</h2>
          <p>{{ camera?.brand }} · {{ camera?.serialNumber }}</p>
        </div>
      </div>
      <div class="device-status" :class="isOnline ? 'online' : 'offline'">
        <span />{{ isOnline ? '设备在线' : '设备离线' }}
      </div>
    </section>

    <el-tabs v-model="activeTab" class="camera-tabs">
      <el-tab-pane name="live">
        <template #label><span><el-icon><VideoCamera /></el-icon>实时画面</span></template>
        <section class="live-layout">
          <div class="video-card">
            <div class="video-header">
              <div><span class="live-dot" />实时视频流 <small>LIVE · HLS</small></div>
              <el-button text type="primary" :icon="RefreshRight" @click="playStream">刷新视频流</el-button>
            </div>
            <div class="video-stage">
              <video v-if="isOnline" ref="videoEl" controls autoplay muted playsinline />
              <div v-else class="video-empty"><el-icon><CircleCloseFilled /></el-icon><strong>设备当前离线</strong><span>设备恢复在线后可继续播放实时画面。</span></div>
              <div v-if="playerError" class="video-error">
                <p>{{ playerError }}</p>
                <el-button size="small" type="primary" @click="playStream">重新连接</el-button>
              </div>
            </div>
            <div class="video-footer">
              <span>播放延迟：低延迟模式</span>
              <span>系统每 1 分钟自动抓拍</span>
            </div>
          </div>
          <aside class="device-card">
            <h3>设备信息</h3>
            <dl>
              <div><dt>设备状态</dt><dd><span class="status-dot" :class="isOnline ? 'online' : 'offline'" />{{ isOnline ? '在线' : '离线' }}</dd></div>
              <div><dt>品牌</dt><dd>{{ camera?.brand || '—' }}</dd></div>
              <div><dt>设备序列号</dt><dd>{{ camera?.serialNumber || '—' }}</dd></div>
              <div><dt>部署坐标</dt><dd>{{ Number.isFinite(camera?.longitude) ? `${camera.longitude.toFixed(5)}, ${camera.latitude.toFixed(5)}` : '未设置' }}</dd></div>
            </dl>
          </aside>
        </section>
      </el-tab-pane>

      <el-tab-pane name="snapshots">
        <template #label><span><el-icon><Picture /></el-icon>抓拍记录 <b v-if="snapshotTotal">{{ snapshotTotal }}</b></span></template>
        <el-card shadow="never" class="panel record-panel">
          <div class="record-head"><div><h3>抓拍记录</h3><p>系统按计划自动抓拍；点击缩略图可放大预览。</p></div></div>
          <div v-if="!snapshots.length" class="empty-state">
            <div class="empty-icon"><el-icon><Picture /></el-icon></div>
            <strong>暂无抓拍记录</strong>
            <span>系统将按计划自动抓拍，记录会在这里展示。</span>
          </div>
          <div v-else class="snapshot-grid">
            <article
              v-for="item in snapshots"
              :key="item.id"
              class="snapshot-item"
              :class="{ clickable: !!item.image }"
              @click="openSnapshotPreview(item)"
            >
              <img v-if="item.image" :src="item.image" alt="摄像头抓拍" />
              <div v-else class="snapshot-placeholder"><el-icon><Picture /></el-icon><span>抓拍图片暂不可用</span></div>
              <div><strong>{{ item.capturedAt }}</strong><span>{{ formatFileSize(item.fileSize) }}</span></div>
            </article>
          </div>
          <div v-if="snapshotTotal > snapshotSize" class="snapshot-pager">
            <el-pagination
              background
              layout="total, prev, pager, next"
              :total="snapshotTotal"
              :current-page="snapshotCurrent"
              :page-size="snapshotSize"
              @current-change="onSnapshotPageChange"
            />
          </div>
          <el-image-viewer
            v-if="previewIndex >= 0"
            :url-list="previewImages"
            :initial-index="previewIndex"
            teleported
            @close="previewIndex = -1"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane name="monitoring">
        <template #label><span><el-icon><DataAnalysis /></el-icon>AI 监测 <b v-if="monitoringTotal">{{ monitoringTotal }}</b></span></template>
        <el-card shadow="never" class="panel record-panel monitoring-panel">
          <div class="record-head">
            <div>
              <h3>综合 AI 监测</h3>
              <p>抓拍后由系统自动监测；点击左侧记录可查看详情。</p>
            </div>
          </div>

          <div v-if="!monitoringViews.length" class="empty-state">
            <div class="empty-icon"><el-icon><DataAnalysis /></el-icon></div>
            <strong>暂无监测记录</strong>
            <span>系统完成自动监测后，历史结果会在这里展示。</span>
          </div>
          <div v-else class="monitoring-workspace">
            <div class="monitoring-sidebar">
              <aside class="monitoring-list" aria-label="监测记录列表">
                <button
                  v-for="item in monitoringViews"
                  :key="item.id"
                  type="button"
                  class="monitoring-list-item"
                  :class="{ active: monitoringView?.id === item.id }"
                  @click="selectMonitoring(item.id)"
                >
                  <strong>{{ item.analyzedAt }}</strong>
                  <span>{{ item.weather.label }} · {{ item.visibility.label }} · 船 {{ item.boatCount }}</span>
                  <em :class="['status-pill', item.status === 'SUCCESS' ? 'ok' : 'bad']">{{ item.status || 'UNKNOWN' }}</em>
                </button>
              </aside>
              <div v-if="monitoringTotal > monitoringSize" class="monitoring-pager">
                <el-button
                  text
                  class="monitoring-pager-btn"
                  :disabled="monitoringCurrent <= 1"
                  @click="onMonitoringPageChange(monitoringCurrent - 1)"
                >
                  ‹
                </el-button>
                <span class="monitoring-pager-index">{{ monitoringCurrent }} / {{ Math.max(1, Math.ceil(monitoringTotal / monitoringSize)) }}</span>
                <el-button
                  text
                  class="monitoring-pager-btn"
                  :disabled="monitoringCurrent >= Math.ceil(monitoringTotal / monitoringSize)"
                  @click="onMonitoringPageChange(monitoringCurrent + 1)"
                >
                  ›
                </el-button>
              </div>
            </div>

            <div v-if="monitoringView" class="monitoring-result">
              <div class="monitoring-meta">
                <span>{{ monitoringView.analyzedAt }}</span>
                <span>抓拍 #{{ monitoringView.snapshotId }}</span>
                <span :class="['status-pill', monitoringView.status === 'SUCCESS' ? 'ok' : 'bad']">{{ monitoringView.status || 'UNKNOWN' }}</span>
                <span>共 {{ monitoringTotal }} 条</span>
              </div>

              <div class="result-grid">
                <div class="metric-card metric-weather" :style="{ '--metric-atmosphere': `url(${metricWeather})` }">
                  <div class="metric-copy">
                    <span class="metric-label">天气类别</span>
                    <strong class="metric-value">{{ monitoringView.weather.label }}</strong>
                    <em class="metric-meta">置信度 {{ formatConfidence(monitoringView.weather.confidence) }}</em>
                  </div>
                </div>
                <div class="metric-card metric-visibility" :style="{ '--metric-atmosphere': `url(${metricVisibility})` }">
                  <div class="metric-copy">
                    <span class="metric-label">能见度等级</span>
                    <strong class="metric-value">{{ monitoringView.visibility.label }}</strong>
                    <em class="metric-meta">置信度 {{ formatConfidence(monitoringView.visibility.confidence) }}</em>
                  </div>
                </div>
                <div class="metric-card metric-boats" :style="{ '--metric-atmosphere': `url(${metricBoats})` }">
                  <div class="metric-copy">
                    <span class="metric-label">可见船只数</span>
                    <strong class="metric-value">{{ monitoringView.boatCount }}</strong>
                    <em class="metric-meta">检测框 {{ monitoringView.boats.length }} 个</em>
                  </div>
                </div>
              </div>

              <div class="monitoring-layout">
                <section class="result-block monitoring-image-block">
                  <strong>标注结果图</strong>
                  <img v-if="monitoringView.image" class="monitoring-image" :src="monitoringView.image" alt="AI 监测标注结果" />
                  <div v-else class="snapshot-placeholder"><el-icon><Picture /></el-icon><span>暂无标注结果图</span></div>
                </section>

                <div class="monitoring-side" :style="{ '--classification-atmosphere': `url(${classificationAtmosphere})` }">
                  <section class="result-block">
                    <strong>天气分类</strong>
                    <ul class="score-list">
                      <li v-for="item in monitoringView.weatherClasses" :key="`weather-${item.class_id ?? item.class}`">
                        <span>{{ item.class || '未知' }}</span>
                        <div class="score-bar"><i :style="{ width: `${Math.max(0, Math.min(100, Number(item.confidence || 0) * 100))}%` }" /></div>
                        <em>{{ formatConfidence(item.confidence) }}</em>
                      </li>
                      <li v-if="!monitoringView.weatherClasses.length" class="muted">暂无分类结果</li>
                    </ul>
                  </section>

                  <section class="result-block">
                    <strong>能见度分类</strong>
                    <ul class="score-list">
                      <li v-for="item in monitoringView.visibilityClasses" :key="`vis-${item.class_id ?? item.class}`">
                        <span>{{ item.class || '未知' }}</span>
                        <div class="score-bar"><i :style="{ width: `${Math.max(0, Math.min(100, Number(item.confidence || 0) * 100))}%` }" /></div>
                        <em>{{ formatConfidence(item.confidence) }}</em>
                      </li>
                      <li v-if="!monitoringView.visibilityClasses.length" class="muted">暂无分类结果</li>
                    </ul>
                  </section>
                </div>
              </div>

              <section class="result-block">
                <strong>船只检测</strong>
                <div v-if="monitoringView.boats.length" class="boat-table-wrap">
                  <table class="boat-table">
                    <thead>
                      <tr>
                        <th>#</th>
                        <th>类别</th>
                        <th>置信度</th>
                        <th>中心点</th>
                        <th>尺寸</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(boat, index) in monitoringView.boats" :key="`boat-${index}`">
                        <td>{{ index + 1 }}</td>
                        <td>{{ boat.class || boat.label || 'boat' }}</td>
                        <td>{{ formatConfidence(boat.confidence) }}</td>
                        <td>{{ Number.isFinite(boat.x) && Number.isFinite(boat.y) ? `${Math.round(boat.x)}, ${Math.round(boat.y)}` : '—' }}</td>
                        <td>{{ Number.isFinite(boat.width) && Number.isFinite(boat.height) ? `${Math.round(boat.width)} × ${Math.round(boat.height)}` : '—' }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <p v-else class="muted">未检测到船只</p>
              </section>

              <section v-if="monitoringView.tracks.length" class="result-block">
                <strong>船只轨迹</strong>
                <div class="boat-table-wrap">
                  <table class="boat-table">
                    <thead>
                      <tr>
                        <th>#</th>
                        <th>轨迹 ID</th>
                        <th>类别</th>
                        <th>置信度</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(track, index) in monitoringView.tracks" :key="`track-${index}`">
                        <td>{{ index + 1 }}</td>
                        <td>{{ track.tracker_id ?? track.track_id ?? track.id ?? '—' }}</td>
                        <td>{{ track.class || track.label || 'boat' }}</td>
                        <td>{{ formatConfidence(track.confidence) }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </section>
            </div>
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane name="warnings">
        <template #label><span><el-icon><CircleCloseFilled /></el-icon>预警记录 <b v-if="warnings.length">{{ warnings.length }}</b></span></template>
        <el-card shadow="never" class="panel record-panel">
          <div class="record-head"><div><h3>预警记录</h3><p>该设备接入智能分析或告警服务后，预警事件将在这里展示。</p></div></div>
          <div v-if="!warnings.length" class="empty-state">
            <div class="empty-icon warning"><el-icon><CircleCloseFilled /></el-icon></div>
            <strong>暂无预警记录</strong>
            <span>设备接入告警服务后，异常事件会在这里展示。</span>
          </div>
          <div v-else class="warning-list">
            <article v-for="item in warnings" :key="item.id">
              <el-icon><CircleCloseFilled /></el-icon>
              <div><strong>{{ item.level }}</strong><p>{{ item.content }}</p></div>
              <time>{{ item.occurredAt }}</time>
            </article>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.camera-detail-page { flex: 1; min-height: 0; gap: 18px; }
.detail-heading { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 12px 24px; padding: 4px 2px; }
.detail-heading > .el-button { grid-column: 1 / -1; justify-self: start; padding: 0; }
.detail-title { display: flex; align-items: center; gap: 14px; }
.device-icon { display: grid; width: 48px; height: 48px; place-items: center; color: #78edff; border: 1px solid #45d7ef55; border-radius: 14px; background: linear-gradient(135deg, #1b5c7666, #14233d); font-size: 23px; }
.detail-title h2 { margin: 0; color: #eff9ff; font-size: 24px; }
.detail-title p:last-child { margin: 5px 0 0; color: #8faac1; font-size: 13px; }
.detail-title .eyebrow { margin-bottom: 4px; color: #70dcef; font-size: 10px; }
.device-status { display: inline-flex; align-items: center; gap: 8px; padding: 8px 12px; border: 1px solid; border-radius: 999px; font-size: 13px; }
.device-status span, .status-dot, .live-dot { display: inline-block; width: 7px; height: 7px; border-radius: 50%; }
.device-status.online { color: #7be7c9; border-color: #52d9b344; background: #1fb98d12; }.device-status.online span, .status-dot.online, .live-dot { background: #55dab9; box-shadow: 0 0 9px #55dab9aa; }
.device-status.offline { color: #9baabb; border-color: #72849a44; background: #72849a12; }.device-status.offline span, .status-dot.offline { background: #7b8c99; }
.camera-tabs { display: flex; min-height: 0; flex: 1; flex-direction: column; }.camera-tabs :deep(.el-tabs__header) { flex: 0 0 auto; margin: 0 0 16px; }.camera-tabs :deep(.el-tabs__content), .camera-tabs :deep(.el-tab-pane) { display: flex; min-height: 0; flex: 1; flex-direction: column; }.camera-tabs :deep(.el-tabs__content) { overflow: hidden; }.camera-tabs :deep(.el-tabs__nav-wrap::after) { background: #24384f; }.camera-tabs :deep(.el-tabs__item) { color: #8fa8c2; }.camera-tabs :deep(.el-tabs__item.is-active) { color: #76e9ff; }.camera-tabs :deep(.el-tabs__active-bar) { background: #40d8ff; }.camera-tabs :deep(.el-tabs__item span) { display: inline-flex; align-items: center; gap: 6px; }.camera-tabs b { display: inline-grid; min-width: 16px; height: 16px; place-items: center; color: #bff7ff; border-radius: 9px; background: #2abfd533; font-size: 10px; }
.live-layout { display: grid; height: 100%; min-height: 0; grid-template-columns: minmax(0, 1fr) 280px; gap: 16px; }.video-card, .device-card { overflow: hidden; border: 1px solid #5ccbe634; border-radius: 16px; background: linear-gradient(145deg, #122137, #0c1828); box-shadow: 0 14px 32px #02081342; }.video-card { display: flex; min-height: 0; flex-direction: column; }.video-header, .video-footer { display: flex; flex: 0 0 auto; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 16px; color: #c7e9f1; font-size: 13px; }.video-header { border-bottom: 1px solid #48748732; }.video-header > div { display: flex; align-items: center; gap: 8px; }.video-header small { color: #6fbfc8; font: 700 10px/1 ui-monospace, monospace; letter-spacing: .08em; }.video-footer { color: #7f9daf; border-top: 1px solid #48748732; font-size: 12px; }.video-stage { position: relative; min-height: 0; flex: 1; background: #02080f; }.video-stage video { display: block; width: 100%; height: 100%; object-fit: contain; }.video-error { position: absolute; inset: 0; display: grid; place-content: center; gap: 12px; padding: 28px; color: #ffd4c7; text-align: center; background: #030b14d9; }.video-error p { margin: 0; }.video-empty { position: absolute; inset: 0; display: grid; place-content: center; gap: 10px; color: #95a9bb; text-align: center; }.video-empty .el-icon { color: #6f8294; font-size: 34px; }.video-empty strong { color: #d8e5ef; }.video-empty span { font-size: 13px; }
.device-card { padding: 18px; }.device-card h3, .record-head h3 { margin: 0; color: #e9f6ff; font-size: 16px; }.device-card dl { margin: 16px 0 0; }.device-card dl > div { padding: 12px 0; border-bottom: 1px solid #4060782b; }.device-card dl > div:last-child { border-bottom: 0; }.device-card dt { margin-bottom: 6px; color: #7892aa; font-size: 12px; }.device-card dd { display: flex; align-items: center; gap: 7px; margin: 0; color: #d8e8f5; font-size: 13px; word-break: break-all; }
.record-panel { display: flex; height: 100%; min-height: 0; flex-direction: column; }
.record-panel :deep(.el-card__body) {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}
.record-head { display: flex; flex: 0 0 auto; justify-content: space-between; gap: 16px; }.record-head p { margin: 7px 0 0; color: #839bb2; font-size: 13px; }.empty-state { display: grid; flex: 1; place-content: center; align-content: center; gap: 10px; min-height: 0; color: #8ca6bd; text-align: center; }.empty-icon { display: grid; width: 54px; height: 54px; place-items: center; justify-self: center; color: #6ae5f7; border: 1px solid #4bd7e04d; border-radius: 16px; background: linear-gradient(145deg, #16445b8a, #102339b3); box-shadow: inset 0 1px #c8faff1a, 0 0 22px #36d2ed1f; font-size: 25px; }.empty-icon.warning { color: #ffc56a; border-color: #edb96152; background: linear-gradient(145deg, #5d431f7a, #2d241a9e); }.empty-state strong { color: #dbeefa; font-size: 15px; }.empty-state span { font-size: 12px; }.snapshot-grid { display: grid; flex: 1; min-height: 0; align-content: start; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 14px; margin-top: 20px; overflow: auto; }.snapshot-item { overflow: hidden; border: 1px solid #4c718638; border-radius: 12px; background: #0b1726; }.snapshot-item.clickable { cursor: zoom-in; transition: border-color 160ms ease, transform 160ms ease, box-shadow 160ms ease; }.snapshot-item.clickable:hover { border-color: #4bd7e088; transform: translateY(-2px); box-shadow: 0 10px 22px #02101866; }.snapshot-item img, .snapshot-placeholder { display: block; width: 100%; aspect-ratio: 16 / 9; object-fit: cover; }.snapshot-placeholder { display: grid; place-content: center; gap: 8px; padding: 18px; color: #7f9aad; text-align: center; background: #09121e; font-size: 12px; }.snapshot-placeholder .el-icon { margin: auto; color: #4b7187; font-size: 28px; }.snapshot-item > div:last-child { display: grid; gap: 4px; padding: 10px 12px; }.snapshot-item strong { color: #d8eafa; font-size: 12px; }.snapshot-item span { color: #7892aa; font-size: 11px; }
.snapshot-pager {
  display: flex;
  flex: 0 0 auto;
  justify-content: flex-end;
  margin-top: auto;
  padding-top: 16px;
}
.snapshot-pager :deep(.el-pagination) {
  flex-wrap: wrap;
  justify-content: flex-end;
  row-gap: 8px;
}
.warning-list { display: grid; gap: 10px; margin-top: 20px; }.warning-list article { display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 12px; padding: 14px; border: 1px solid #d99e6040; border-radius: 10px; background: #3b271614; }.warning-list article > .el-icon { color: #ffc56a; font-size: 18px; }.warning-list strong { color: #ffe4bd; font-size: 13px; }.warning-list p { margin: 5px 0 0; color: #aebdca; font-size: 12px; }.warning-list time { color: #8094a7; font-size: 12px; white-space: nowrap; }
.monitoring-workspace {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
  flex: 1;
  min-height: 0;
  margin-top: 16px;
}
.monitoring-sidebar {
  display: flex;
  flex-direction: column;
  min-height: 0;
  gap: 10px;
}
.monitoring-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  overflow: auto;
  padding: 2px 8px 2px 2px;
  scrollbar-width: thin;
  scrollbar-color: #3d6280 transparent;
}
.monitoring-pager {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 32px;
  padding: 4px 6px;
  border: 1px solid rgba(74, 125, 152, 0.22);
  border-radius: 8px;
  background: rgba(10, 26, 42, 0.55);
}
.monitoring-pager-index {
  min-width: 42px;
  color: #8fadc3;
  font-size: 11px;
  text-align: center;
  letter-spacing: 0.04em;
}
.monitoring-pager-btn {
  width: 24px !important;
  min-width: 24px !important;
  height: 24px !important;
  padding: 0 !important;
  color: #9fd7ea !important;
  font-size: 16px !important;
  line-height: 1 !important;
  border-radius: 6px !important;
}
.monitoring-pager-btn:hover:not(.is-disabled) {
  color: #e8fbff !important;
  background: rgba(56, 160, 190, 0.18) !important;
}
.monitoring-pager-btn.is-disabled {
  color: #4f6b7e !important;
}
.monitoring-list::-webkit-scrollbar,
.monitoring-result::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
.monitoring-list::-webkit-scrollbar-track,
.monitoring-result::-webkit-scrollbar-track {
  background: transparent;
}
.monitoring-list::-webkit-scrollbar-thumb,
.monitoring-result::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #3d6280aa;
}
.monitoring-list::-webkit-scrollbar-thumb:hover,
.monitoring-result::-webkit-scrollbar-thumb:hover {
  background: #5ecfe0aa;
}
.monitoring-list-item {
  position: relative;
  display: grid;
  gap: 6px;
  width: 100%;
  min-height: 82px;
  padding: 13px 12px 12px 15px;
  text-align: left;
  color: #c7e3f4;
  background: linear-gradient(135deg, #0d1e31, #0a1727);
  border: 1px solid #3c68804a;
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 160ms ease, background 160ms ease, transform 160ms ease, box-shadow 160ms ease;
}
.monitoring-list-item::before { position: absolute; top: 14px; bottom: 14px; left: -1px; width: 3px; border-radius: 0 4px 4px 0; background: transparent; content: ''; }
.monitoring-list-item:hover,
.monitoring-list-item.active {
  border-color: #46d9efaa;
  background: linear-gradient(135deg, #123b55, #10273d);
  box-shadow: 0 8px 20px #03111c66, inset 0 1px #b9f8ff12;
}
.monitoring-list-item:hover { transform: translateX(2px); }
.monitoring-list-item.active::before { background: linear-gradient(#6af4e3, #32bce7); box-shadow: 0 0 14px #45dded99; }
.monitoring-list-item strong {
  color: #e7f6ff;
  font-size: 12px;
}
.monitoring-list-item span {
  color: #8fadc3;
  font-size: 11px;
  line-height: 1.4;
}
.monitoring-list-item .status-pill {
  justify-self: start;
  font-size: 10px;
}
.monitoring-result {
  display: grid;
  gap: 14px;
  overflow: auto;
  min-height: 0;
  padding: 2px 8px 8px 2px;
  scrollbar-width: thin;
  scrollbar-color: #3d6280 transparent;
}
.monitoring-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 14px; padding: 9px 12px; color: #8fadc3; border: 1px solid #3c688038; border-radius: 10px; background: #0a1929aa; font-size: 12px; }
.status-pill { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 999px; font-weight: 700; letter-spacing: 0.04em; }
.status-pill.ok { color: #7be7c9; background: #1fb98d18; border: 1px solid #52d9b344; }
.status-pill.bad { color: #ffb4a8; background: #b84a3a18; border: 1px solid #e07a6844; }
.result-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.result-grid > div:not(.metric-card), .result-block { padding: 15px; border: 1px solid #3c68804a; border-radius: 12px; background: linear-gradient(145deg, #0e2033, #0b1929); box-shadow: inset 0 1px #c7f7ff0b; }
.metric-card {
  position: relative;
  display: flex;
  align-items: flex-end;
  overflow: hidden;
  isolation: isolate;
  min-height: 112px;
  padding: 16px;
  border: 1px solid #4a7d9840;
  border-radius: 12px;
  background-color: transparent;
  background-image:
    linear-gradient(105deg, rgba(3, 12, 22, 0.72) 0%, rgba(3, 12, 22, 0.28) 46%, rgba(3, 12, 22, 0.08) 100%),
    var(--metric-atmosphere);
  background-position: center;
  background-size: cover;
  background-repeat: no-repeat;
  box-shadow: inset 0 1px rgba(199, 247, 255, 0.08), 0 10px 22px rgba(2, 10, 18, 0.24);
}
.metric-card::before {
  display: none;
}
.metric-copy {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 6px;
  max-width: min(100%, 220px);
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
}
.metric-label,
.metric-value,
.metric-meta {
  display: block;
  margin: 0;
  max-width: 100%;
  overflow-wrap: anywhere;
}
.metric-label {
  color: rgba(174, 210, 228, 0.88);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-shadow: 0 1px 8px rgba(2, 10, 18, 0.75);
}
.metric-value {
  color: #f5fcff;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: 0.01em;
  text-shadow: 0 2px 12px rgba(2, 10, 18, 0.7);
}
.metric-meta {
  color: #c6e8f6;
  font-size: 12px;
  font-style: normal;
  letter-spacing: 0.02em;
  text-shadow: 0 1px 8px rgba(2, 10, 18, 0.7);
}
.metric-weather,
.metric-visibility,
.metric-boats {
  background-image:
    linear-gradient(105deg, rgba(3, 12, 22, 0.72) 0%, rgba(3, 12, 22, 0.28) 46%, rgba(3, 12, 22, 0.08) 100%),
    var(--metric-atmosphere);
}
.result-block > strong { display: block; margin-bottom: 10px; color: #d7edff; font-size: 13px; }
.monitoring-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(310px, 0.85fr);
  gap: 12px;
  align-items: stretch;
}
.monitoring-image-block {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.monitoring-image-block .snapshot-placeholder {
  flex: 1;
  min-height: 220px;
}
.monitoring-side {
  display: grid;
  grid-template-rows: 1fr 1fr;
  gap: 12px;
  min-height: 0;
  height: 100%;
}
.monitoring-side > .result-block {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  border-color: #4a7d9840;
  background-image:
    linear-gradient(145deg, rgba(7, 22, 36, 0.28), rgba(8, 24, 40, 0.16)),
    radial-gradient(circle at 80% 20%, rgba(70, 210, 230, 0.1), transparent 42%),
    var(--classification-atmosphere);
  background-position: center;
  background-size: cover;
  background-repeat: no-repeat;
  box-shadow: inset 0 1px rgba(199, 247, 255, 0.08), 0 8px 20px rgba(2, 10, 18, 0.22);
}
.monitoring-side > .result-block:nth-child(2) {
  background-position: 72% center;
  background-image:
    linear-gradient(155deg, rgba(8, 24, 38, 0.26), rgba(10, 28, 44, 0.14)),
    radial-gradient(circle at 18% 80%, rgba(110, 180, 220, 0.12), transparent 46%),
    var(--classification-atmosphere);
}
.monitoring-side > .result-block > strong {
  text-shadow: 0 1px 8px rgba(2, 10, 18, 0.7);
}
.monitoring-side .score-list li {
  padding: 4px 6px;
  border-radius: 8px;
  background: rgba(5, 16, 28, 0.5);
  backdrop-filter: blur(2px);
}
.monitoring-side .score-bar {
  background: rgba(8, 28, 44, 0.72);
}
.monitoring-side .score-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  align-content: start;
  scrollbar-width: thin;
  scrollbar-color: #3d6280 transparent;
}
.monitoring-image {
  display: block;
  flex: 1;
  width: 100%;
  min-height: 240px;
  max-height: 430px;
  aspect-ratio: 16 / 9;
  object-fit: contain;
  border-radius: 8px;
  border: 1px solid #4b79924d;
  background: #07121d;
}
.score-list { display: grid; gap: 11px; margin: 0; padding: 0; list-style: none; }
.score-list li { display: grid; grid-template-columns: 94px minmax(0, 1fr) 42px; align-items: center; gap: 9px; color: #c7e3f4; font-size: 12px; }
.score-list .muted, .muted { color: #7f9aad; font-size: 12px; }
.score-bar { height: 7px; overflow: hidden; border-radius: 999px; background: #17324a; box-shadow: inset 0 1px 2px #02091288; }
.score-bar > i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #2bb7d4, #6ef0d4); }
.score-list em { color: #9ec7dc; font-style: normal; text-align: right; }
.boat-table-wrap { overflow: auto; }
.boat-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.boat-table th, .boat-table td { padding: 8px 10px; border-bottom: 1px solid #33566d3d; text-align: left; color: #c5e0f1; white-space: nowrap; }
.boat-table th { color: #86a9c0; font-weight: 600; }
@media (max-width: 900px) {
  .live-layout { grid-template-columns: 1fr; }
  .device-card { order: -1; }
  .detail-heading { grid-template-columns: 1fr; }
  .device-status { justify-self: start; }
  .monitoring-layout { grid-template-columns: 1fr; }
  .monitoring-workspace { grid-template-columns: 1fr; }
}
@media (max-width: 600px) { .result-grid { grid-template-columns: 1fr; } }
</style>
