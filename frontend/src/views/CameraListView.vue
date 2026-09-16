<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, VideoCamera } from '@element-plus/icons-vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useAuthStore } from '@/stores/auth'
import {
  addCameraDevice,
  cameraDevices,
  removeCameraDevice,
  updateCameraDevice,
} from '@/stores/cameraDevices'

const authStore = useAuthStore()
const canWrite = computed(() => authStore.hasPermission('gis:write'))
const dialogVisible = ref(false)
const editingId = ref(null)
const mapEl = ref(null)
const form = reactive({ name: '', brand: '海康', serialNumber: '', verificationCode: '', longitude: null, latitude: null, status: 'ONLINE' })
const TIANDITU_TOKEN = '38ca5876c8ba7b71eb08803d408b6184'
let coordinateMap
let coordinateMarker

function resetForm() {
  editingId.value = null
  form.name = ''
  form.brand = '海康'
  form.serialNumber = ''
  form.verificationCode = ''
  form.longitude = null
  form.latitude = null
  form.status = 'ONLINE'
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(camera) {
  editingId.value = camera.id
  form.name = camera.name
  form.brand = '海康'
  form.serialNumber = camera.serialNumber || ''
  form.verificationCode = camera.verificationCode || ''
  form.longitude = camera.longitude
  form.latitude = camera.latitude
  form.status = camera.status
  dialogVisible.value = true
}

function destroyCoordinateMap() {
  coordinateMap?.remove()
  coordinateMap = undefined
  coordinateMarker = undefined
}

function setCoordinate(latlng, { recenter = false } = {}) {
  form.longitude = Number(latlng.lng.toFixed(7))
  form.latitude = Number(latlng.lat.toFixed(7))
  if (!coordinateMap) return
  if (!coordinateMarker) {
    coordinateMarker = L.circleMarker([form.latitude, form.longitude], {
      radius: 8, color: '#0b7189', weight: 2, fillColor: '#59e1d1', fillOpacity: 0.92,
    }).addTo(coordinateMap)
  } else {
    coordinateMarker.setLatLng([form.latitude, form.longitude])
  }
  if (recenter) coordinateMap.setView([form.latitude, form.longitude], Math.max(coordinateMap.getZoom(), 15))
}

async function openCoordinateMap() {
  await nextTick()
  destroyCoordinateMap()
  if (!mapEl.value) return
  const center = Number.isFinite(form.latitude) && Number.isFinite(form.longitude)
    ? [form.latitude, form.longitude]
    : [23.186778, 114.01324]
  coordinateMap = L.map(mapEl.value, { zoomControl: true, attributionControl: false }).setView(center, 15)
  const mapOptions = { subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'], maxZoom: 18 }
  L.tileLayer(`https://t{s}.tianditu.gov.cn/img_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=img&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${TIANDITU_TOKEN}`, mapOptions).addTo(coordinateMap)
  L.tileLayer(`https://t{s}.tianditu.gov.cn/cia_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=cia&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${TIANDITU_TOKEN}`, mapOptions).addTo(coordinateMap)
  if (Number.isFinite(form.latitude) && Number.isFinite(form.longitude)) setCoordinate({ lat: form.latitude, lng: form.longitude })
  coordinateMap.on('click', (event) => setCoordinate(event.latlng))
  window.setTimeout(() => coordinateMap?.invalidateSize(), 80)
  window.setTimeout(() => coordinateMap?.invalidateSize(), 280)
}

function syncCoordinateMarker() {
  if (Number.isFinite(form.latitude) && Number.isFinite(form.longitude)) {
    setCoordinate({ lat: form.latitude, lng: form.longitude }, { recenter: true })
  }
}

onBeforeUnmount(destroyCoordinateMap)

function save() {
  if (!form.name.trim() || !form.serialNumber.trim() || !form.verificationCode.trim()) {
    ElMessage.warning('请填写摄像头名称、序列号和验证码')
    return
  }
  const payload = {
    ...form,
    name: form.name.trim(),
    brand: '海康',
    serialNumber: form.serialNumber.trim(),
    verificationCode: form.verificationCode.trim(),
  }
  if (editingId.value) updateCameraDevice(editingId.value, payload)
  else addCameraDevice(payload)
  dialogVisible.value = false
  ElMessage.success(editingId.value ? '摄像头配置已更新' : '摄像头已添加')
}

async function remove(camera) {
  await ElMessageBox.confirm(`确认删除“${camera.name}”吗？`, '删除摄像头', { type: 'warning' })
  removeCameraDevice(camera.id)
  ElMessage.success('摄像头已删除')
}
</script>

<template>
  <div class="page camera-page">
    <section class="hero-panel">
      <div>
        <p class="eyebrow">Video Device Registry</p>
        <h2>摄像头列表</h2>
        <p class="hero-desc">维护 GIS 工作台中的海康设备名称、序列号、验证码与在线状态。</p>
      </div>
      <el-button v-if="canWrite" type="primary" :icon="Plus" @click="openCreate">新增摄像头</el-button>
    </section>

    <el-card shadow="never" class="panel">
      <div class="table-scroll">
        <el-table :data="cameraDevices" empty-text="暂无摄像头，请先新增配置">
          <el-table-column label="设备名称" min-width="180">
            <template #default="{ row }">
              <div class="camera-name"><el-icon><VideoCamera /></el-icon><strong>{{ row.name }}</strong></div>
            </template>
          </el-table-column>
          <el-table-column prop="brand" label="品牌" width="110" />
          <el-table-column prop="serialNumber" label="设备序列号" min-width="200" show-overflow-tooltip />
          <el-table-column label="坐标" min-width="200">
            <template #default="{ row }">{{ Number.isFinite(row.longitude) ? `${row.longitude.toFixed(5)}, ${row.latitude.toFixed(5)}` : '未选择' }}</template>
          </el-table-column>
          <el-table-column label="验证码" width="120">
            <template #default="{ row }">{{ row.verificationCode ? '已配置' : '未配置' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag :type="row.status === 'ONLINE' ? 'success' : 'info'">{{ row.status === 'ONLINE' ? '在线' : '离线' }}</el-tag></template>
          </el-table-column>
          <el-table-column v-if="canWrite" label="操作" width="188" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button text type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
                <el-button text type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      class="camera-dialog"
      :title="editingId ? '编辑摄像头' : '新增摄像头'"
      width="560px"
      align-center
      destroy-on-close
      :close-on-click-modal="false"
      @open="openCoordinateMap"
      @closed="destroyCoordinateMap(); resetForm()"
    >
      <el-form class="camera-form" label-position="top">
        <el-form-item label="摄像头名称" required>
          <el-input v-model="form.name" maxlength="80" placeholder="例如：农田北侧监控" />
        </el-form-item>
        <el-form-item label="品牌">
          <el-input v-model="form.brand" disabled />
        </el-form-item>
        <el-form-item label="设备序列号" required>
          <el-input v-model="form.serialNumber" maxlength="80" placeholder="请输入海康设备序列号" />
        </el-form-item>
        <el-form-item label="验证码" required>
          <el-input v-model="form.verificationCode" maxlength="80" show-password placeholder="请输入设备验证码" />
        </el-form-item>
        <el-form-item label="地图位置">
          <div class="coordinate-inputs">
            <el-input-number
              v-model="form.longitude"
              :precision="7"
              :step="0.00001"
              :min="-180"
              :max="180"
              placeholder="经度"
              controls-position="right"
              @change="syncCoordinateMarker"
            />
            <el-input-number
              v-model="form.latitude"
              :precision="7"
              :step="0.00001"
              :min="-90"
              :max="90"
              placeholder="纬度"
              controls-position="right"
              @change="syncCoordinateMarker"
            />
          </div>
          <div ref="mapEl" class="coordinate-map" />
          <p class="coordinate-hint">在天地图影像上单击以选择摄像头位置，也可直接填写经纬度。</p>
        </el-form-item>
        <el-form-item class="status-item" label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ONLINE">在线</el-radio>
            <el-radio value="OFFLINE">离线</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.camera-page { display: grid; gap: 16px; }
.hero-panel { display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.camera-name { display: flex; align-items: center; gap: 8px; }
.camera-name .el-icon { color: #59e1d1; font-size: 18px; }
.camera-form :deep(.el-form-item) { margin-bottom: 10px; }
.camera-form :deep(.el-form-item__label) { margin-bottom: 4px !important; line-height: 1.3; }
.camera-form .status-item { margin-bottom: 0; }
.coordinate-inputs { display: flex; width: 100%; gap: 12px; margin-bottom: 8px; }
.coordinate-inputs :deep(.el-input-number) { flex: 1; width: auto; }
.coordinate-map {
  width: 100%;
  height: 168px;
  overflow: hidden;
  border: 1px solid #30445d;
  border-radius: 10px;
  background: #0b1726;
}
.coordinate-hint { margin: 6px 0 0; color: #8fa8c2; font-size: 12px; line-height: 1.45; }
</style>

<style>
.camera-dialog.el-dialog {
  margin: 0 auto !important;
  max-height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #2c425c;
  border-radius: 14px;
  background: #101d2e;
}
.camera-dialog .el-dialog__header {
  flex-shrink: 0;
  margin-right: 0;
  padding: 16px 20px 10px;
}
.camera-dialog .el-dialog__title { color: #e7f2ff; font-size: 16px; }
.camera-dialog .el-dialog__headerbtn .el-dialog__close { color: #9db3cc; }
.camera-dialog .el-dialog__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  padding: 8px 20px 4px;
}
.camera-dialog .el-dialog__footer {
  flex-shrink: 0;
  padding: 10px 20px 16px;
  border-top: 1px solid #24384f;
}
.el-overlay-dialog:has(.camera-dialog) {
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
