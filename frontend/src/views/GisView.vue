<script setup>
import 'cesium/Build/Cesium/Widgets/widgets.css'

import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Hls from 'hls.js'
import {
  Camera,
  Cpu,
  Delete,
  EditPen,
  Location,
  MagicStick,
  Refresh,
  RefreshLeft,
} from '@element-plus/icons-vue'
import * as turf from '@turf/turf'
import {
  ArcGISTiledElevationTerrainProvider,
  BoundingSphere,
  CallbackProperty,
  Cartesian2,
  Cartesian3,
  Cartographic,
  CesiumTerrainProvider,
  ClassificationType,
  Color,
  CustomDataSource,
  EasingFunction,
  HeadingPitchRange,
  HeightReference,
  HorizontalOrigin,
  ImageryLayer,
  Ion,
  Math as CesiumMath,
  Matrix4,
  Rectangle,
  RequestScheduler,
  Resource,
  SceneMode,
  ScreenSpaceEventHandler,
  ScreenSpaceEventType,
  EllipsoidTerrainProvider,
  sampleTerrain,
  sampleTerrainMostDetailed,
  UrlTemplateImageryProvider,
  VerticalOrigin,
  Viewer,
  WebMercatorTilingScheme,
} from 'cesium'
import { createGisFeature, deleteGisFeature, listGisFeatures, runRoboflowWorkflow, runSam2Delineation } from '@/api/gis'
import { listRoboflowPlots } from '@/utils/roboflowPlot'
import { useAuthStore } from '@/stores/auth'
import { cameraDevices, loadCameraDevices } from '@/stores/cameraDevices'
import annotationPin from '@/assets/gis/annotation-pin-terrain.png'
import panelLandscape from '@/assets/gis/annotation-panel-landscape.png'
import toolbarHudButton from '@/assets/gis/toolbar-hud-button.png'
import toolbarContainerBackdrop from '@/assets/gis/toolbar-container-backdrop.png'
import cameraMarker from '@/assets/gis/camera-marker-tech.png'

Ion.defaultAccessToken = ''

const TIANDITU_TOKEN = '38ca5876c8ba7b71eb08803d408b6184'
const GIS_PREVIEW_STREAM_URL = 'https://s9.nysdot.skyvdn.com/rtplive/R11_305/playlist.m3u8'

const authStore = useAuthStore()
const mapEl = ref(null)
const features = ref([])
const activeFeatureId = ref(null)
const drawingPoints = ref([])
const cursorCoordinate = ref(null)
const cameraStatus = ref({ lon: null, lat: null, height: null, heading: 0 })
const saving = ref(false)
const loading = ref(false)
const showUavLayer = ref(true)
const clickedTiandituTile = ref(null)
const manualDrawMode = ref(false)
const aiDelineateMode = ref(false)
const aiProvider = ref('roboflow')
const aiProviderName = computed(() => aiProvider.value === 'sam2.1' ? 'SAM 2.1' : 'Roboflow')
const aiDelineating = ref(false)
const aiDraftLocked = ref(false)
const pendingAiPolygons = ref([])
const cameraVideoEl = ref(null)
const activeCameraId = ref(null)
const showCameraPlayer = ref(false)
const cameraPlayerError = ref('')
const cameras = cameraDevices

const UAV_TILES = {
  url: 'http://60.205.211.104:8888/uav/{z}/{x}/{y}.png',
  minZoom: 14,
  maxZoom: 22,
  west: 114.009338,
  south: 23.184288,
  east: 114.017142,
  north: 23.189267,
  lon: 114.01324,
  lat: 23.186778,
}

const tiandituTilingScheme = new WebMercatorTilingScheme()

let viewer
let handler
let draftSource
let savedSource
let cameraSource
let cameraMarkersReady = false
let cameraRevealTimer = 0
let suppressClickUntil = 0
let resizeObserver
let removeTileListener
let removeMorphListener
let uavLayer
let labelLayer
let flightSeq = 0
let introTimer = 0
let introRunning = false
let introMaxZoom = 2.5e7
let terrainStarted = false
let removeCameraListener
let removeSpinTickListener
let cameraFlying = false
let userCameraActive = false
let spinResumeTimer = 0
let spinInputBound = false
let pageAlive = false
let mountGeneration = 0
let hlsPlayer

function isViewerLive() {
  return pageAlive && viewer && !viewer.isDestroyed()
}

function destroyCameraPlayer() {
  hlsPlayer?.destroy()
  hlsPlayer = undefined
  const video = cameraVideoEl.value
  if (video) {
    video.pause()
    video.removeAttribute('src')
    video.load()
  }
}

async function openCamera(camera) {
  activeCameraId.value = camera.id
  flyToCameraLocation(camera)
  showCameraPlayer.value = true
  cameraPlayerError.value = ''
  destroyCameraPlayer()
  await nextTick()

  const video = cameraVideoEl.value
  if (!video) return
  const startPlayback = () => video.play().catch(() => {
    cameraPlayerError.value = '浏览器阻止了自动播放，请点击视频中央的播放按钮'
  })

  if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = GIS_PREVIEW_STREAM_URL
    video.addEventListener('loadedmetadata', startPlayback, { once: true })
  } else if (Hls.isSupported()) {
    hlsPlayer = new Hls({
      enableWorker: true,
      lowLatencyMode: true,
      backBufferLength: 30,
    })
    hlsPlayer.on(Hls.Events.ERROR, (_, data) => {
      if (!data.fatal) return
      if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
        cameraPlayerError.value = '视频流无法连接或播放地址已过期'
        hlsPlayer?.startLoad()
      } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
        cameraPlayerError.value = ''
        hlsPlayer?.recoverMediaError()
      } else {
        cameraPlayerError.value = '视频播放器发生错误，请重新打开设备'
      }
    })
    hlsPlayer.loadSource(GIS_PREVIEW_STREAM_URL)
    hlsPlayer.attachMedia(video)
    hlsPlayer.on(Hls.Events.MANIFEST_PARSED, startPlayback)
  } else {
    cameraPlayerError.value = '当前浏览器不支持 HLS 播放'
    return
  }

}

function renderCameraMarkers() {
  if (!isViewerLive() || !cameraSource || !cameraMarkersReady) return
  cameraSource.entities.removeAll()
  cameras.value.forEach((camera) => {
    if (!Number.isFinite(camera.longitude) || !Number.isFinite(camera.latitude)) return
    cameraSource.entities.add({
      id: `camera-${camera.id}`,
      position: Cartesian3.fromDegrees(camera.longitude, camera.latitude, 0),
      billboard: {
        image: cameraMarker,
        width: 42,
        height: 42,
        verticalOrigin: VerticalOrigin.BOTTOM,
        heightReference: HeightReference.CLAMP_TO_GROUND,
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
      },
      label: {
        text: camera.name,
        font: '600 13px sans-serif',
        fillColor: Color.fromCssColorString('#e8ffff'),
        outlineColor: Color.fromCssColorString('#06212d'),
        outlineWidth: 3,
        verticalOrigin: VerticalOrigin.BOTTOM,
        pixelOffset: new Cartesian2(0, -40),
        heightReference: HeightReference.CLAMP_TO_GROUND,
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
      },
    })
  })
  cameraSource.show = true
  viewer.scene.requestRender()
}

function queueCameraMarkersReveal() {
  if (!isViewerLive() || !cameraSource) return
  if (cameraRevealTimer) {
    window.clearTimeout(cameraRevealTimer)
    cameraRevealTimer = 0
  }
  const generation = mountGeneration
  const tryReveal = () => {
    cameraRevealTimer = 0
    if (!isViewerLive() || generation !== mountGeneration || !cameraSource) return
    if (introRunning) {
      cameraRevealTimer = window.setTimeout(tryReveal, 120)
      return
    }
    cameraMarkersReady = true
    renderCameraMarkers()
  }
  // 入场结束后稍等，让地球底图先落稳，再显示标识
  cameraRevealTimer = window.setTimeout(tryReveal, 350)
}

function flyToCameraLocation(camera) {
  if (!isViewerLive() || !Number.isFinite(camera.longitude) || !Number.isFinite(camera.latitude)) return
  viewer.camera.flyTo({
    destination: Cartesian3.fromDegrees(camera.longitude, camera.latitude, 900),
    duration: 0.8,
  })
}

function closeCameraPlayer() {
  showCameraPlayer.value = false
  destroyCameraPlayer()
}

const canWrite = computed(() => authStore.hasPermission('gis:write'))
const mapBusy = computed(() => aiDelineating.value || saving.value)
const mapBusyTitle = computed(() => (aiDelineating.value ? 'AI 地块扫描中' : '地块数据写入中'))
const mapBusySub = computed(() =>
  aiDelineating.value ? 'SATELLITE TILE · FIELD SEGMENTATION' : 'UPLINK · SYNCING PARCELS',
)
const draftPolygon = computed(() => buildPolygonFeature(drawingPoints.value))
const polygonInvalid = computed(() => {
  if (!draftPolygon.value) return false
  return turf.kinks(draftPolygon.value).features.length > 0
})
const draftAreaText = computed(() => {
  if (!draftPolygon.value) return ''
  return formatArea(turf.area(draftPolygon.value))
})
const draftLengthText = computed(() => {
  if (!draftPolygon.value) return ''
  return formatLength(turf.length(turf.polygonToLine(draftPolygon.value), { units: 'kilometers' }))
})
const canSaveDraft = computed(() => {
  if (!canWrite.value || saving.value || aiDelineateMode.value || aiDelineating.value) return false
  if (pendingAiPolygons.value.length) return true
  return drawingPoints.value.length >= 3 && !polygonInvalid.value
})

function toLonLatHeight(cartesian) {
  const cartographic = Cartographic.fromCartesian(cartesian)
  return [
    Number(CesiumMath.toDegrees(cartographic.longitude).toFixed(8)),
    Number(CesiumMath.toDegrees(cartographic.latitude).toFixed(8)),
    Number(cartographic.height.toFixed(2)),
  ]
}

function isSketchEntity(picked) {
  const entity = picked?.id
  return Boolean(entity && (entity.polygon || entity.polyline || entity.point || entity.billboard))
}

function isReasonableGlobePoint(cartesian) {
  try {
    const cartographic = Cartographic.fromCartesian(cartesian)
    return Number.isFinite(cartographic.height) && cartographic.height > -1200 && cartographic.height < 12000
  } catch {
    return false
  }
}

function pickOnGlobe(windowPosition) {
  const picked = viewer.scene.pick(windowPosition)
  if (viewer.scene.pickPositionSupported && !isSketchEntity(picked)) {
    const depthHit = viewer.scene.pickPosition(windowPosition)
    if (depthHit && isReasonableGlobePoint(depthHit)) return depthHit
  }
  const ray = viewer.camera.getPickRay(windowPosition)
  const globeHit = ray ? viewer.scene.globe.pick(ray, viewer.scene) : undefined
  if (globeHit) return globeHit
  return viewer.camera.pickEllipsoid(windowPosition, viewer.scene.globe.ellipsoid)
}

function terrainHeightAt(lon, lat, fallback = 0) {
  const cartographic = Cartographic.fromDegrees(lon, lat)
  const height = viewer?.scene?.globe?.getHeight(cartographic)
  return Number.isFinite(height) ? height : fallback
}

async function sampleGroundHeight(lon, lat, fallback = 0) {
  const cached = terrainHeightAt(lon, lat, Number.NaN)
  if (Number.isFinite(cached)) return cached
  const provider = viewer?.terrainProvider
  if (!viewer || !provider || provider instanceof EllipsoidTerrainProvider) return fallback
  const carto = Cartographic.fromDegrees(lon, lat)
  try {
    await sampleTerrainMostDetailed(provider, [carto])
    if (Number.isFinite(carto.height)) return carto.height
  } catch {
    try {
      await sampleTerrain(provider, 11, [carto])
      if (Number.isFinite(carto.height)) return carto.height
    } catch {
      // keep fallback
    }
  }
  return fallback
}

function clampTileLevel(level) {
  const value = Number(level)
  if (!Number.isFinite(value)) return 17
  return Math.min(17, Math.max(1, Math.round(value)))
}

function estimatedTiandituLevel() {
  const cameraHeight = Cartographic.fromCartesian(viewer.camera.positionWC)?.height
  if (!Number.isFinite(cameraHeight) || cameraHeight <= 0) return 17
  return clampTileLevel(18 - Math.log2(cameraHeight / 600))
}

function renderedTileLevelAt(cartographic) {
  const tiles = viewer?.scene?.globe?._surface?._tilesToRender || []
  const matched = tiles
    .filter((tile) => tile?.rectangle && Rectangle.contains(tile.rectangle, cartographic))
    .sort((a, b) => b.level - a.level)
  return clampTileLevel(matched[0]?.level ?? estimatedTiandituLevel())
}

function tiandituTileUrl(layer, level, x, y) {
  const subdomain = Math.abs(x + y + level) % 8
  return `https://t${subdomain}.tianditu.gov.cn/DataServer?T=${layer}&x=${x}&y=${y}&l=${level}&tk=${TIANDITU_TOKEN}`
}

function tileBounds(level, x, y) {
  const rectangle = tiandituTilingScheme.tileXYToRectangle(x, y, level)
  return {
    west: CesiumMath.toDegrees(rectangle.west),
    south: CesiumMath.toDegrees(rectangle.south),
    east: CesiumMath.toDegrees(rectangle.east),
    north: CesiumMath.toDegrees(rectangle.north),
  }
}

function formatLonLat(lon, lat) {
  return `${Number(lon).toFixed(8)}, ${Number(lat).toFixed(8)}`
}

function tileCornersWgs84(bounds) {
  return {
    topLeft: [bounds.west, bounds.north],
    topRight: [bounds.east, bounds.north],
    bottomRight: [bounds.east, bounds.south],
    bottomLeft: [bounds.west, bounds.south],
  }
}

function tileCornersText(corners) {
  return [
    `左上: ${formatLonLat(corners.topLeft[0], corners.topLeft[1])}`,
    `右上: ${formatLonLat(corners.topRight[0], corners.topRight[1])}`,
    `右下: ${formatLonLat(corners.bottomRight[0], corners.bottomRight[1])}`,
    `左下: ${formatLonLat(corners.bottomLeft[0], corners.bottomLeft[1])}`,
  ].join('\n')
}

function clickPixelInTile(bounds, lon, lat) {
  const x = Math.round(((lon - bounds.west) / (bounds.east - bounds.west)) * 255)
  const y = Math.round(((bounds.north - lat) / (bounds.north - bounds.south)) * 255)
  return {
    x: Math.min(255, Math.max(0, x)),
    y: Math.min(255, Math.max(0, y)),
  }
}

function updateClickedTiandituTile(coordinate) {
  const [lon, lat] = coordinate
  const cartographic = Cartographic.fromDegrees(lon, lat)
  const level = renderedTileLevelAt(cartographic)
  const tile = tiandituTilingScheme.positionToTileXY(cartographic, level)
  if (!tile) {
    clickedTiandituTile.value = null
    return
  }
  const bounds = tileBounds(level, tile.x, tile.y)
  const corners = tileCornersWgs84(bounds)
  clickedTiandituTile.value = {
    lon,
    lat,
    level,
    x: tile.x,
    y: tile.y,
    bounds,
    corners,
    cornersText: tileCornersText(corners),
    clickPixel: clickPixelInTile(bounds, lon, lat),
    imageUrl: tiandituTileUrl('img_w', level, tile.x, tile.y),
    labelUrl: tiandituTileUrl('cia_w', level, tile.x, tile.y),
  }
  console.info('Tianditu tile WGS84 四角\n' + clickedTiandituTile.value.cornersText)
  viewer?.scene?.requestRender?.()
}

async function copyTileCorners() {
  const text = clickedTiandituTile.value?.cornersText
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制瓦片四角坐标')
  } catch {
    ElMessage.warning('复制失败，请从面板中手动选择')
  }
}

function toggleManualDraw() {
  if (!canWrite.value || mapBusy.value) return
  manualDrawMode.value = !manualDrawMode.value
  if (manualDrawMode.value) {
    aiDelineateMode.value = false
    ElMessage.info('手动圈地已开启：单击加点，双击或闭合完成')
  }
}

function handleAiDelineateClick(provider = 'roboflow') {
  if (mapBusy.value) return
  aiDelineateMode.value = !(aiDelineateMode.value && aiProvider.value === provider)
  aiProvider.value = provider
  if (aiDelineateMode.value) {
    manualDrawMode.value = false
    clearDraft()
    ElMessage.info(provider === 'sam2.1' ? 'SAM 2.1 已开启，请点击田块内部' : 'AI 圈地已开启，请点击田块获取瓦片并调用识别')
  }
}

async function runAiDelineate(tile) {
  if (!tile?.imageUrl || aiDelineating.value) return
  aiDelineating.value = true
  let polygons
  try {
    const imageBase64 = await loadTileBase64(tile.imageUrl)
    if (aiProvider.value === 'sam2.1') {
      const result = await runSam2Delineation({
        imageBase64, bounds: tile.bounds, longitude: tile.lon, latitude: tile.lat,
      })
      polygons = (result?.features || []).filter((feature) => feature?.geometry?.type === 'Polygon')
        .map((feature) => turf.rewind(turf.cleanCoords(feature)))
      if (polygons.some((feature) => feature.properties?.touchesImageEdge)) {
        ElMessage.warning('地块到达影像边缘，边界可能不完整，请检查预览后再提交')
      }
    } else {
      const result = await runRoboflowWorkflow({ imageBase64, corners: tile.corners })
      const output = result?.outputs?.[0]
      console.info('Roboflow parcels', {
        parcel_count: output?.parcel_count,
        geographic: output?.parcel_geographic_polygons?.length,
        geojson: output?.parcel_geojson?.features?.length,
      })
      polygons = listRoboflowPlots(result)
        .map((plot) => buildPolygonFeature(plotToCoordinates(plot, tile.bounds)))
        .filter(Boolean)
    }
    if (!polygons.length) {
      ElMessage.warning('未识别出地块边界，请换个位置再试')
      return
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || `调用 ${aiProviderName.value} 失败`)
    return
  } finally {
    aiDelineating.value = false
  }
  cursorCoordinate.value = null
  aiDelineateMode.value = false
  aiDraftLocked.value = true
  drawingPoints.value = []
  pendingAiPolygons.value = polygons
  drawPreviewPolygons(polygons)
  ElMessage.success(`已识别 ${polygons.length} 块地，请点击上方「提交地块」保存`)
}

function plotToCoordinates(plot, bounds) {
  if (plot?.coordinates?.length >= 3) {
    return plot.coordinates.map(([lon, lat]) => [
      Number(Number(lon).toFixed(8)),
      Number(Number(lat).toFixed(8)),
      terrainHeightAt(lon, lat, 0),
    ])
  }
  if (!plot?.ring?.length || !bounds) return []
  const width = Math.max(1, plot.imageWidth - 1)
  const height = Math.max(1, plot.imageHeight - 1)
  return plot.ring.map(([px, py]) => {
    const lon = bounds.west + (px / width) * (bounds.east - bounds.west)
    const lat = bounds.north - (py / height) * (bounds.north - bounds.south)
    return [Number(lon.toFixed(8)), Number(lat.toFixed(8)), terrainHeightAt(lon, lat, 0)]
  })
}

function blobToBase64(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const result = String(reader.result || '')
      const comma = result.indexOf(',')
      resolve(comma >= 0 ? result.slice(comma + 1) : result)
    }
    reader.onerror = () => reject(new Error('瓦片读取失败'))
    reader.readAsDataURL(blob)
  })
}

async function loadTileBase64(imageUrl) {
  try {
    const blob = await Resource.fetchBlob({ url: imageUrl })
    if (blob?.size) return blobToBase64(blob)
  } catch {
    // 天地图常拦 XHR，改用图片元素读取
  }
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.crossOrigin = 'anonymous'
    image.onload = () => {
      try {
        const canvas = document.createElement('canvas')
        canvas.width = image.naturalWidth || 256
        canvas.height = image.naturalHeight || 256
        const context = canvas.getContext('2d')
        if (!context) {
          reject(new Error('无法读取瓦片像素'))
          return
        }
        context.drawImage(image, 0, 0, canvas.width, canvas.height)
        resolve(canvas.toDataURL('image/jpeg', 0.92).replace(/^data:image\/[a-zA-Z0-9+.-]+;base64,/, ''))
      } catch {
        reject(new Error('瓦片受跨域限制，无法转成图片数据'))
      }
    }
    image.onerror = () => reject(new Error('瓦片图片加载失败'))
    image.src = imageUrl
  })
}

function sameCoordinate(a, b, meters = 1.5) {
  if (!a || !b) return false
  return turf.distance(turf.point(a), turf.point(b), { units: 'meters' }) < meters
}

function formatArea(squareMeters) {
  if (squareMeters >= 1_000_000) return `${(squareMeters / 1_000_000).toFixed(2)} km²`
  if (squareMeters >= 10_000) return `${(squareMeters / 10_000).toFixed(2)} 公顷`
  return `${Math.round(squareMeters)} m²`
}

function formatLength(kilometers) {
  if (kilometers >= 1) return `${kilometers.toFixed(2)} km`
  return `${Math.round(kilometers * 1000)} m`
}

function buildPolygonFeature(coordinates) {
  if (!coordinates || coordinates.length < 3) return null
  const ring = coordinates.map((item) => [item[0], item[1]])
  const first = ring[0]
  const last = ring[ring.length - 1]
  if (first[0] !== last[0] || first[1] !== last[1]) ring.push([...first])
  try {
    return turf.rewind(turf.cleanCoords(turf.polygon([ring])))
  } catch {
    return null
  }
}

function lonLatCartesian(item) {
  return Cartesian3.fromDegrees(item[0], item[1])
}

function vertexHeight(item, fallback = 0) {
  const value = Number(item?.[2])
  return Number.isFinite(value) ? value : fallback
}

function featureTypeLabel(feature) {
  const area = Number(parseFeatureProperties(feature).area)
  if (Number.isFinite(area) && area > 0) return `区域 · ${formatArea(area)}`
  return '区域'
}

function clearDraft() {
  drawingPoints.value = []
  cursorCoordinate.value = null
  aiDraftLocked.value = false
  pendingAiPolygons.value = []
  draftSource?.entities.removeAll()
}

function drawPreviewPolygons(polygons) {
  if (!draftSource) return
  draftSource.entities.removeAll()
  polygons.forEach((polygon) => {
    const ring = polygon?.geometry?.coordinates?.[0]
    if (!Array.isArray(ring) || ring.length < 3) return
    const positions = ring.map((item) => lonLatCartesian(item))
    draftSource.entities.add({
      polygon: {
        hierarchy: positions,
        material: Color.fromCssColorString('#22d3ee').withAlpha(0.28),
        classificationType: ClassificationType.TERRAIN,
      },
      polyline: {
        positions,
        width: 3,
        clampToGround: true,
        classificationType: ClassificationType.TERRAIN,
        material: Color.fromCssColorString('#22d3ee'),
      },
    })
  })
  viewer?.scene?.requestRender?.()
}

function undoLastVertex() {
  if (aiDraftLocked.value || !drawingPoints.value.length) return
  drawingPoints.value = drawingPoints.value.slice(0, -1)
  if (!drawingPoints.value.length) cursorCoordinate.value = null
  redrawDraft()
}

function drawDraftPoint(coordinate, color = '#22d3ee', options = {}) {
  const height = vertexHeight(coordinate, options.fallbackHeight || 0)
  draftSource.entities.add({
    name: options.name,
    position: Cartesian3.fromDegrees(coordinate[0], coordinate[1], height),
    point: {
      color: Color.fromCssColorString(color),
      outlineColor: Color.WHITE,
      outlineWidth: 2,
      pixelSize: options.pixelSize || 11,
      heightReference: options.clampToGround ? HeightReference.CLAMP_TO_GROUND : HeightReference.NONE,
      disableDepthTestDistance: Number.POSITIVE_INFINITY,
    },
    label: options.labelText
      ? {
          text: options.labelText,
          font: '11px Consolas, "Segoe UI", sans-serif',
          fillColor: Color.fromCssColorString('#7dd3fc'),
          showBackground: true,
          backgroundColor: Color.fromCssColorString('#061020').withAlpha(0.82),
          backgroundPadding: new Cartesian2(6, 3),
          pixelOffset: new Cartesian2(0, -16),
          horizontalOrigin: HorizontalOrigin.CENTER,
          verticalOrigin: VerticalOrigin.BOTTOM,
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
        }
      : undefined,
  })
}

function redrawDraft() {
  if (!draftSource) return
  draftSource.entities.removeAll()
  const points = drawingPoints.value
  const lineColor = polygonInvalid.value ? '#ef4444' : '#22d3ee'
  points.forEach((item, index) => {
    const isStart = index === 0 && points.length >= 3
    drawDraftPoint(item, isStart ? '#67e8f9' : lineColor, {
      name: isStart ? 'draft-start' : undefined,
      pixelSize: isStart ? 6 : 6,
      clampToGround: true,
    })
  })
  const committed = points.map((item) => lonLatCartesian(item))
  if (committed.length >= 3 && !polygonInvalid.value) {
    draftSource.entities.add({
      polygon: {
        hierarchy: committed,
        material: Color.fromCssColorString('#22d3ee').withAlpha(0.28),
        classificationType: ClassificationType.TERRAIN,
      },
    })
  }
  if (committed.length >= 2) {
    draftSource.entities.add({
      polyline: {
        positions: committed,
        width: 3,
        clampToGround: true,
        classificationType: ClassificationType.TERRAIN,
        material: Color.fromCssColorString(lineColor),
      },
    })
  }
  draftSource.entities.add({
    polyline: {
      positions: new CallbackProperty(() => {
        const vertices = drawingPoints.value
        const cursor = cursorCoordinate.value
        if (!cursor || !vertices.length) return []
        return [lonLatCartesian(vertices[vertices.length - 1]), lonLatCartesian(cursor)]
      }, false),
      width: 3,
      clampToGround: true,
      classificationType: ClassificationType.TERRAIN,
      material: Color.fromCssColorString(lineColor),
    },
  })
}

function parseFeatureProperties(feature) {
  try {
    const raw = feature?.properties
    if (!raw) return {}
    return typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch {
    return {}
  }
}

function parseFeatureGeometry(feature) {
  try {
    const root = JSON.parse(feature.geojson)
    if (root?.type === 'Feature') return root.geometry
    if (root?.coordinates) return root
  } catch {
    return null
  }
  return null
}

function addSavedFeature(feature) {
  const geometry = parseFeatureGeometry(feature)
  if (!geometry) return
  if (geometry.type === 'Polygon') {
    const ring = geometry.coordinates[0] || []
    if (ring.length < 3) return
    const positions = ring.map((item) => lonLatCartesian(item))
    const active = activeFeatureId.value === feature.id
    savedSource.entities.add({
      name: feature.name,
      properties: { featureId: feature.id, name: feature.name, type: feature.type },
      polygon: {
        hierarchy: positions,
        material: Color.fromCssColorString(active ? '#67e8f9' : '#22d3ee').withAlpha(active ? 0.48 : 0.36),
        classificationType: ClassificationType.BOTH,
      },
      polyline: {
        positions,
        width: active ? 4 : 3,
        clampToGround: true,
        material: Color.fromCssColorString(active ? '#ecfeff' : '#67e8f9'),
      },
    })
  }
}

function restyleSavedFeatures() {
  if (!savedSource) return
  savedSource.entities.values.forEach((entity) => {
    const featureId = entity.properties?.featureId?.getValue?.() ?? entity.properties?.featureId
    const active = featureId === activeFeatureId.value
    if (entity.polygon) {
      entity.polygon.material = Color.fromCssColorString(active ? '#67e8f9' : '#22d3ee').withAlpha(active ? 0.48 : 0.36)
    }
    if (entity.polyline) {
      entity.polyline.width = active ? 4 : 3
      entity.polyline.material = Color.fromCssColorString(active ? '#ecfeff' : '#67e8f9')
    }
  })
}

async function fetchFeatures() {
  loading.value = true
  try {
    const rows = (await listGisFeatures()) || []
    if (!pageAlive) return
    features.value = rows
    if (!isViewerLive() || !savedSource) return
    savedSource.entities.removeAll()
    features.value.forEach(addSavedFeature)
  } catch {
    if (pageAlive) features.value = []
  } finally {
    if (pageAlive) loading.value = false
  }
}

async function saveFeature(geojson) {
  if (!canWrite.value) {
    ElMessage.warning('没有编辑 GIS 标注的权限')
    return
  }
  saving.value = true
  try {
    const properties = draftPolygon.value
      ? {
          source: 'cesium',
          area: turf.area(draftPolygon.value),
          perimeterKm: turf.length(turf.polygonToLine(draftPolygon.value), { units: 'kilometers' }),
        }
      : { source: 'cesium' }
    const saved = await createGisFeature({
      name: fallbackLandName(),
      type: 'POLYGON',
      geojson: JSON.stringify(geojson),
      properties: JSON.stringify(properties),
    })
    features.value = [saved, ...features.value]
    addSavedFeature(saved)
    flyToFeature(saved)
    clearDraft()
    ElMessage.success('已保存到服务器')
  } finally {
    saving.value = false
  }
}


function fallbackLandName() {
  const now = new Date()
  const pad = (value, size = 2) => String(value).padStart(size, '0')
  return `地块-${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}${pad(now.getMilliseconds(), 3)}`
}

async function savePolygon() {
  if (pendingAiPolygons.value.length) {
    await savePolygons(pendingAiPolygons.value)
    return
  }
  const polygon = buildPolygonFeature(drawingPoints.value)
  if (!polygon) {
    ElMessage.warning('至少需要 3 个点才能圈地')
    return
  }
  if (turf.kinks(polygon).features.length) {
    ElMessage.warning('多边形自相交，请调整后再保存')
    return
  }
  await saveFeature(polygon)
}

async function savePolygons(polygons) {
  if (!canWrite.value) {
    ElMessage.warning('没有编辑 GIS 标注的权限')
    return
  }
  saving.value = true
  let savedCount = 0
  let skipped = 0
  try {
    for (const polygon of polygons) {
      if (turf.kinks(polygon).features.length) {
        skipped += 1
        continue
      }
      const properties = {
        ...polygon.properties,
        source: polygon.properties?.source || 'roboflow',
        area: turf.area(polygon),
        perimeterKm: turf.length(turf.polygonToLine(polygon), { units: 'kilometers' }),
      }
      const saved = await createGisFeature({
        name: fallbackLandName(),
        type: 'POLYGON',
        geojson: JSON.stringify(polygon),
        properties: JSON.stringify(properties),
      })
      features.value = [saved, ...features.value]
      addSavedFeature(saved)
      savedCount += 1
    }
    clearDraft()
    if (!savedCount) {
      ElMessage.warning(skipped ? `识别到 ${polygons.length} 块，但都自相交，未保存` : '没有可保存的地块')
      return
    }
    ElMessage.success(`已保存 ${savedCount} 块地${skipped ? `，跳过 ${skipped} 块自相交` : ''}`)
  } finally {
    saving.value = false
  }
}

async function removeFeature(feature) {
  await ElMessageBox.confirm(`确认删除「${feature.name}」？`, '删除标注', { type: 'warning' })
  await deleteGisFeature(feature.id)
  features.value = features.value.filter((item) => item.id !== feature.id)
  savedSource.entities.values
    .filter((entity) => entity.properties?.featureId?.getValue?.() === feature.id)
    .forEach((entity) => savedSource.entities.remove(entity))
  ElMessage.success('已删除')
}

const FLY_DURATION = 2.0
const HOME_DESTINATION = Cartesian3.fromDegrees(104.1954, 32.5, 15800000)
const SPACE_LOOK_TARGET = Cartesian3.fromDegrees(108.5, 22.5, 0)
const GLOBE_SPIN_MIN_HEIGHT = 3.2e6
const GLOBE_SPIN_RAD_PER_SEC = CesiumMath.toRadians(3.2)
const NADIR = {
  heading: 0,
  pitch: CesiumMath.toRadians(-90),
  roll: 0,
}
/** 地块定位：斜角 2.5D（非正射），能看出地形起伏与空间层次 */
const FEATURE_OBLIQUE = {
  heading: CesiumMath.toRadians(38),
  pitch: CesiumMath.toRadians(-42),
}

function beginFlight() {
  stopEarthIntro(true)
  cameraFlying = true
  flightSeq += 1
  return flightSeq
}

function isCurrentFlight(token) {
  return token === flightSeq
}

function endFlight() {
  cameraFlying = false
}

function noteUserCameraInput() {
  userCameraActive = true
  if (spinResumeTimer) {
    window.clearTimeout(spinResumeTimer)
    spinResumeTimer = 0
  }
  spinResumeTimer = window.setTimeout(() => {
    userCameraActive = false
    spinResumeTimer = 0
  }, 2800)
}

function canGlobeSpin() {
  if (!isViewerLive()) return false
  if (cameraFlying || introRunning || userCameraActive) return false
  if (viewer.scene.mode !== SceneMode.SCENE3D) return false
  if (viewer.scene.mode === SceneMode.MORPHING) return false
  try {
    const height = Cartographic.fromCartesian(viewer.camera.positionWC)?.height
    return Number.isFinite(height) && height >= GLOBE_SPIN_MIN_HEIGHT
  } catch {
    return false
  }
}

function startGlobeSpin() {
  if (!isViewerLive() || removeSpinTickListener) return
  viewer.clock.shouldAnimate = true
  removeSpinTickListener = viewer.clock.onTick.addEventListener((clock) => {
    if (!canGlobeSpin()) return
    try {
      const dt = Number(clock?.deltaTime)
      const step = Number.isFinite(dt) && dt > 0 ? dt : 1 / 60
      const angle = -GLOBE_SPIN_RAD_PER_SEC * step
      if (!Number.isFinite(angle)) return
      viewer.camera.rotate(Cartesian3.UNIT_Z, angle)
    } catch {
      // 姿态异常时跳过本帧，不让 Cesium 停渲染
    }
  })
}

function stopGlobeSpin() {
  if (spinResumeTimer) {
    window.clearTimeout(spinResumeTimer)
    spinResumeTimer = 0
  }
  removeSpinTickListener?.()
  removeSpinTickListener = undefined
}

function bindGlobeSpinInputs() {
  if (!viewer || spinInputBound) return
  spinInputBound = true
  const canvas = viewer.scene.canvas
  const onInput = () => noteUserCameraInput()
  canvas.addEventListener('pointerdown', onInput)
  canvas.addEventListener('wheel', onInput, { passive: true })
  canvas.addEventListener('touchstart', onInput, { passive: true })
  viewer.__globeSpinInputCleanup = () => {
    canvas.removeEventListener('pointerdown', onInput)
    canvas.removeEventListener('wheel', onInput)
    canvas.removeEventListener('touchstart', onInput)
  }
}

function unlockCamera() {
  if (!isViewerLive()) return
  viewer.trackedEntity = undefined
  const camera = viewer.camera
  if (Matrix4.equalsEpsilon(camera.transform, Matrix4.IDENTITY, CesiumMath.EPSILON8)) {
    camera.lookAtTransform(Matrix4.IDENTITY)
    return
  }
  const destination = Cartesian3.clone(camera.positionWC)
  const orientation = {
    direction: Cartesian3.clone(camera.directionWC),
    up: Cartesian3.clone(camera.upWC),
  }
  camera.lookAtTransform(Matrix4.IDENTITY)
  camera.setView({ destination, orientation })
}

function flyCameraTo(destination, orientation = NADIR) {
  if (!viewer) return
  const token = beginFlight()
  viewer.camera.cancelFlight()
  unlockCamera()
  const height = Cartographic.fromCartesian(destination)?.height
  viewer.camera.flyTo({
    destination,
    orientation: viewer.scene.mode === SceneMode.SCENE3D ? orientation : undefined,
    duration: FLY_DURATION,
    easingFunction: EasingFunction.CUBIC_IN_OUT,
    pitchAdjustHeight: Number.isFinite(height) && height > 200000 ? 80000 : undefined,
    endTransform: Matrix4.IDENTITY,
    complete: () => {
      if (!isCurrentFlight(token)) return
      unlockCamera()
      endFlight()
    },
    cancel: () => {
      if (!isCurrentFlight(token)) return
      endFlight()
    },
  })
}

function applyHomeView() {
  if (!viewer) return
  viewer.trackedEntity = undefined
  viewer.camera.lookAtTransform(Matrix4.IDENTITY)
  viewer.camera.setView({
    destination: Cartesian3.clone(HOME_DESTINATION),
    orientation: NADIR,
  })
}

function applySpaceView() {
  viewer.camera.lookAt(
    SPACE_LOOK_TARGET,
    new HeadingPitchRange(CesiumMath.toRadians(-12), CesiumMath.toRadians(-24), 2.2e7),
  )
  unlockCamera()
}

function restoreAfterIntro() {
  if (introTimer) {
    window.clearTimeout(introTimer)
    introTimer = 0
  }
  introRunning = false
  endFlight()
  if (!isViewerLive()) return
  const controller = viewer.scene.screenSpaceCameraController
  controller.maximumZoomDistance = introMaxZoom
  controller.enableInputs = true
  viewer.scene.requestRender()
  startGlobeSpin()
  queueCameraMarkersReveal()
}

function stopEarthIntro(restoreControls = true) {
  if (introTimer) {
    window.clearTimeout(introTimer)
    introTimer = 0
  }
  if (!introRunning) return
  if (restoreControls) restoreAfterIntro()
  else {
    introRunning = false
    endFlight()
  }
}

function playEarthIntro() {
  if (!viewer) return
  if (viewer.scene.mode !== SceneMode.SCENE3D) {
    applyHomeView()
    startGlobeSpin()
    queueCameraMarkersReveal()
    return
  }
  const token = beginFlight()
  viewer.camera.cancelFlight()
  unlockCamera()
  introRunning = true
  const controller = viewer.scene.screenSpaceCameraController
  introMaxZoom = controller.maximumZoomDistance
  controller.maximumZoomDistance = 2.0e8
  controller.enableInputs = false
  applySpaceView()
  viewer.camera.flyTo({
    destination: Cartesian3.clone(HOME_DESTINATION),
    orientation: NADIR,
    duration: 2.4,
    easingFunction: EasingFunction.CUBIC_IN_OUT,
    endTransform: Matrix4.IDENTITY,
    complete: () => {
      if (!isCurrentFlight(token)) return
      restoreAfterIntro()
    },
    cancel: () => {
      if (!isCurrentFlight(token)) return
      restoreAfterIntro()
    },
  })
}

function updateCameraStatus() {
  if (!isViewerLive()) return
  try {
    const carto = Cartographic.fromCartesian(viewer.camera.positionWC)
    if (!carto || !Number.isFinite(carto.longitude) || !Number.isFinite(carto.latitude)) return
    const heading = viewer.camera.heading
    cameraStatus.value = {
      lon: CesiumMath.toDegrees(carto.longitude),
      lat: CesiumMath.toDegrees(carto.latitude),
      height: carto.height,
      heading: Number.isFinite(heading) ? CesiumMath.toDegrees(heading) : (cameraStatus.value.heading || 0),
    }
  } catch {
    // 场景模式切换 / 自转瞬时姿态不完整时跳过，避免打断渲染
  }
}

function zoomBy(direction) {
  if (!isViewerLive()) return
  try {
    const height = Cartographic.fromCartesian(viewer.camera.positionWC)?.height
    if (!Number.isFinite(height) || height <= 0) return
    const amount = height * 0.32
    if (direction < 0) viewer.camera.zoomIn(amount)
    else viewer.camera.zoomOut(amount)
  } catch {
    // ignore
  }
}

const cameraLonText = computed(() => (
  Number.isFinite(cameraStatus.value.lon) ? `${cameraStatus.value.lon.toFixed(2)}°` : '—'
))
const cameraLatText = computed(() => (
  Number.isFinite(cameraStatus.value.lat) ? `${cameraStatus.value.lat.toFixed(2)}°` : '—'
))
const cameraHeightText = computed(() => {
  const height = cameraStatus.value.height
  if (!Number.isFinite(height)) return '—'
  if (height >= 1000) return `${(height / 1000).toFixed(1)} km`
  return `${Math.round(height)} m`
})

function polygonViewRange(geometry, sphere) {
  const ring = geometry.coordinates[0] || []
  let span = Number.isFinite(sphere?.radius) ? sphere.radius * 2 : 0
  if (ring.length >= 2) {
    try {
      const rect = Rectangle.fromCartographicArray(ring.map((item) => Cartographic.fromDegrees(item[0], item[1])))
      const midLat = (rect.south + rect.north) / 2
      const midLon = (rect.west + rect.east) / 2
      const width = Cartesian3.distance(
        Cartesian3.fromRadians(rect.west, midLat, 0),
        Cartesian3.fromRadians(rect.east, midLat, 0),
      )
      const height = Cartesian3.distance(
        Cartesian3.fromRadians(midLon, rect.south, 0),
        Cartesian3.fromRadians(midLon, rect.north, 0),
      )
      span = Math.max(span, width, height)
    } catch {
      // keep sphere-based span
    }
  }
  // 斜角观察需要更远视距，才能看清地块与周边地形
  return Math.max(span * 3.4, 3200)
}

function applyFeatureObliqueView(focusLon, focusLat, focusHeight, range) {
  if (!viewer || viewer.isDestroyed()) return
  const center = Cartesian3.fromDegrees(focusLon, focusLat, focusHeight)
  viewer.camera.lookAt(
    center,
    new HeadingPitchRange(FEATURE_OBLIQUE.heading, FEATURE_OBLIQUE.pitch, range),
  )
  unlockCamera()
  updateCameraStatus()
}

async function flyToPolygon(geometry) {
  const ring = geometry.coordinates[0] || []
  if (!viewer || !ring.length) return
  const positions = ring.map((item) => Cartesian3.fromDegrees(item[0], item[1], 0))
  const sphere = BoundingSphere.fromPoints(positions)
  if (!sphere || !Number.isFinite(sphere.radius)) return

  // 用包围矩形中心，比 centerOfMass 更稳，避免凹多边形首飞重心偏移
  let focusLon
  let focusLat
  try {
    const rect = Rectangle.fromCartographicArray(ring.map((item) => Cartographic.fromDegrees(item[0], item[1])))
    focusLon = CesiumMath.toDegrees((rect.west + rect.east) / 2)
    focusLat = CesiumMath.toDegrees((rect.south + rect.north) / 2)
  } catch {
    const fallbackCenter = Cartographic.fromCartesian(sphere.center)
    if (!fallbackCenter) return
    focusLon = CesiumMath.toDegrees(fallbackCenter.longitude)
    focusLat = CesiumMath.toDegrees(fallbackCenter.latitude)
  }

  // 首次从全球点选时本地尚无地形缓存，先异步采样，避免斜视角看向高程 0 造成位置偏移
  const focusHeight = await sampleGroundHeight(focusLon, focusLat, 0)
  const range = polygonViewRange(geometry, sphere)
  const offset = new HeadingPitchRange(FEATURE_OBLIQUE.heading, FEATURE_OBLIQUE.pitch, range)
  const viewSphere = new BoundingSphere(
    Cartesian3.fromDegrees(focusLon, focusLat, focusHeight),
    Math.max(sphere.radius, 40),
  )

  if (viewer.scene.mode !== SceneMode.SCENE3D) {
    flyCameraTo(Cartesian3.fromDegrees(focusLon, focusLat, focusHeight + range))
    return
  }

  const token = beginFlight()
  viewer.camera.cancelFlight()
  unlockCamera()
  const currentHeight = Cartographic.fromCartesian(viewer.camera.positionWC)?.height || 0
  const finish = async () => {
    if (!isCurrentFlight(token)) return
    const refined = await sampleGroundHeight(focusLon, focusLat, focusHeight)
    if (!isCurrentFlight(token)) return
    if (Math.abs(refined - focusHeight) > 40) {
      applyFeatureObliqueView(focusLon, focusLat, refined, range)
    } else {
      unlockCamera()
      updateCameraStatus()
    }
    endFlight()
  }

  viewer.camera.flyToBoundingSphere(viewSphere, {
    offset,
    duration: currentHeight > 5e5 ? 2.0 : currentHeight > 2e5 ? 1.5 : 1.2,
    easingFunction: EasingFunction.CUBIC_IN_OUT,
    complete: () => {
      finish()
    },
    cancel: () => {
      if (isCurrentFlight(token)) endFlight()
    },
  })
}

async function flyToFeature(feature) {
  const geometry = parseFeatureGeometry(feature)
  if (!viewer || !geometry) return
  activeFeatureId.value = feature.id
  restyleSavedFeatures()
  if (geometry.type === 'Polygon') {
    await flyToPolygon(geometry)
  }
}

function isClosingClick(screenPosition) {
  if (drawingPoints.value.length < 3) return false
  const picked = viewer.scene.pick(screenPosition)
  if (picked?.id?.name === 'draft-start') return true
  const first = drawingPoints.value[0]
  const cartesian = Cartesian3.fromDegrees(
    first[0],
    first[1],
    terrainHeightAt(first[0], first[1], vertexHeight(first, 0)),
  )
  const windowPos = viewer.scene.cartesianToCanvasCoordinates(cartesian)
  if (!windowPos) return false
  return Cartesian2.distance(screenPosition, windowPos) <= 22
}

function addPolygonVertex(coordinate, screenPosition) {
  if (aiDraftLocked.value) return
  if (isClosingClick(screenPosition)) {
    savePolygon()
    return
  }
  const last = drawingPoints.value[drawingPoints.value.length - 1]
  if (sameCoordinate(last, coordinate)) return
  drawingPoints.value = [...drawingPoints.value, coordinate]
  redrawDraft()
}

function pickCameraAt(screenPosition) {
  if (!isViewerLive()) return null
  const picked = viewer.scene.pick(screenPosition)
  const entityId = picked?.id?.id != null ? String(picked.id.id) : ''
  if (!entityId.startsWith('camera-')) return null
  const cameraId = entityId.slice('camera-'.length)
  return cameras.value.find((item) => String(item.id) === cameraId) || null
}

async function handleMapClick(event) {
  if (saving.value || aiDelineating.value || Date.now() < suppressClickUntil) return
  const camera = pickCameraAt(event.position)
  if (camera) {
    openCamera(camera)
    return
  }
  if (!canWrite.value) return
  const position = pickOnGlobe(event.position)
  if (!position) return
  const coordinate = toLonLatHeight(position)
  updateClickedTiandituTile(coordinate)
  if (aiDelineateMode.value) {
    await runAiDelineate(clickedTiandituTile.value)
    return
  }
  if (aiDraftLocked.value || !manualDrawMode.value) return
  addPolygonVertex(coordinate, event.position)
}

function handleMapMove(event) {
  if (!canWrite.value || !manualDrawMode.value || aiDelineateMode.value || aiDraftLocked.value || !drawingPoints.value.length) return
  const position = pickOnGlobe(event.endPosition)
  cursorCoordinate.value = position ? toLonLatHeight(position) : null
}

function handleDoubleClick(event) {
  if (!canWrite.value || saving.value || aiDelineateMode.value || aiDraftLocked.value) return
  if (!manualDrawMode.value && !drawingPoints.value.length) return
  suppressClickUntil = Date.now() + 300
  if (drawingPoints.value.length >= 4) {
    const last = drawingPoints.value[drawingPoints.value.length - 1]
    const prev = drawingPoints.value[drawingPoints.value.length - 2]
    if (sameCoordinate(last, prev, 8)) undoLastVertex()
  }
  if (drawingPoints.value.length >= 3) savePolygon()
  event?.preventDefault?.()
}

function onDrawKeydown(event) {
  if (!canWrite.value || saving.value || aiDelineateMode.value || aiDraftLocked.value) return
  if (!manualDrawMode.value && !drawingPoints.value.length) return
  const tag = event.target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA') return
  if (event.key === 'Escape') {
    clearDraft()
    manualDrawMode.value = false
    return
  }
  if (event.key === 'Backspace' || event.key === 'Delete') {
    event.preventDefault()
    undoLastVertex()
    return
  }
  if (event.key === 'Enter') savePolygon()
}

function bindMapEvents() {
  viewer.screenSpaceEventHandler.removeInputAction(ScreenSpaceEventType.LEFT_DOUBLE_CLICK)
  handler = new ScreenSpaceEventHandler(viewer.scene.canvas)
  handler.setInputAction(handleMapClick, ScreenSpaceEventType.LEFT_CLICK)
  handler.setInputAction(handleMapMove, ScreenSpaceEventType.MOUSE_MOVE)
  handler.setInputAction(handleDoubleClick, ScreenSpaceEventType.LEFT_DOUBLE_CLICK)
  handler.setInputAction(() => {
    if (aiDelineateMode.value || aiDraftLocked.value) return
    if (!manualDrawMode.value && !drawingPoints.value.length) return
    undoLastVertex()
  }, ScreenSpaceEventType.RIGHT_CLICK)
  viewer.cesiumWidget.canvas.addEventListener('contextmenu', (event) => event.preventDefault())
}

function retryTianditu(_resource, error) {
  const status = error?.statusCode
  if (status !== 429 && status !== 503) return false
  const wait = 800 + Math.floor(Math.random() * 1200)
  return new Promise((resolve) => {
    setTimeout(() => resolve(true), wait)
  })
}

function throttleTiandituRequests() {
  RequestScheduler.throttleRequests = true
  RequestScheduler.maximumRequests = 10
  RequestScheduler.maximumRequestsPerServer = 2
  for (let i = 0; i <= 7; i += 1) {
    RequestScheduler.requestsByServer[`t${i}.tianditu.gov.cn:443`] = 2
  }
}

function uavProvider() {
  return new UrlTemplateImageryProvider({
    url: UAV_TILES.url,
    minimumLevel: UAV_TILES.minZoom,
    maximumLevel: UAV_TILES.maxZoom,
    rectangle: Rectangle.fromDegrees(UAV_TILES.west, UAV_TILES.south, UAV_TILES.east, UAV_TILES.north),
    tilingScheme: new WebMercatorTilingScheme(),
    enablePickFeatures: false,
    hasAlphaChannel: true,
  })
}

function addUavLayer() {
  uavLayer = viewer.imageryLayers.addImageryProvider(uavProvider())
  uavLayer.show = showUavLayer.value
}

function flyToUav() {
  flyCameraTo(Cartesian3.fromDegrees(UAV_TILES.lon, UAV_TILES.lat, 900), {
    heading: 0,
    pitch: CesiumMath.toRadians(-75),
    roll: 0,
  })
}

function tiandituProvider(layer) {
  return new UrlTemplateImageryProvider({
    url: new Resource({
      url: `https://t{s}.tianditu.gov.cn/DataServer?T=${layer}&x={x}&y={y}&l={z}&tk=${TIANDITU_TOKEN}`,
      retryAttempts: 4,
      retryCallback: retryTianditu,
    }),
    subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
    maximumLevel: 17,
    minimumLevel: 1,
    tilingScheme: new WebMercatorTilingScheme(),
    enablePickFeatures: false,
    hasAlphaChannel: layer !== 'img_w',
  })
}

function createViewer() {
  throttleTiandituRequests()
  const creditContainer = document.createElement('div')
  viewer = new Viewer(mapEl.value, {
    animation: false,
    baseLayer: new ImageryLayer(tiandituProvider('img_w')),
    baseLayerPicker: false,
    contextOptions: {
      webgl: {
        antialias: false,
        preserveDrawingBuffer: true,
      },
    },
    creditContainer,
    fullscreenButton: false,
    geocoder: false,
    homeButton: false,
    infoBox: false,
    msaaSamples: 1,
    navigationHelpButton: false,
    sceneMode: SceneMode.SCENE3D,
    sceneModePicker: true,
    selectionIndicator: false,
    timeline: false,
  })
  labelLayer = viewer.imageryLayers.addImageryProvider(tiandituProvider('cia_w'))
  addUavLayer()
  viewer.scene.fog.enabled = false
  viewer.scene.globe.enableLighting = false
  viewer.scene.globe.dynamicAtmosphereLighting = false
  viewer.scene.globe.showGroundAtmosphere = true
  viewer.scene.globe.depthTestAgainstTerrain = false
  viewer.scene.globe.maximumScreenSpaceError = 2
  viewer.scene.globe.tileCacheSize = 2000
  viewer.scene.globe.preloadSiblings = true
  viewer.scene.globe.preloadAncestors = true
  viewer.scene.globe.loadingDescendantLimit = 20
  viewer.scene.globe.baseColor = Color.fromCssColorString('#0b1d33')
  viewer.scene.highDynamicRange = false
  viewer.scene.skyBox.show = true
  viewer.scene.skyAtmosphere.show = true
  viewer.scene.sun.show = true
  viewer.scene.moon.show = true
  viewer.scene.backgroundColor = Color.BLACK
  viewer.scene.postProcessStages.fxaa.enabled = false
  viewer.scene.screenSpaceCameraController.minimumZoomDistance = 20
  viewer.scene.screenSpaceCameraController.maximumZoomDistance = 2.5e7
  viewer.scene.screenSpaceCameraController.constrainedAxis = Cartesian3.UNIT_Z
  if (viewer.sceneModePicker) {
    viewer.sceneModePicker.viewModel.duration = 0.4
  }
  removeMorphListener = viewer.scene.morphComplete.addEventListener(unlockCamera)
  viewer.camera.percentageChanged = 0.01
  removeCameraListener = viewer.camera.changed.addEventListener(updateCameraStatus)
  updateCameraStatus()
  bindGlobeSpinInputs()
}

async function enableTerrain(generation) {
  if (!isViewerLive() || terrainStarted) return
  terrainStarted = true
  const loaders = [
    () => CesiumTerrainProvider.fromUrl('https://data.mars3d.cn/terrain', {
      requestVertexNormals: false,
      requestWaterMask: false,
    }),
    () => ArcGISTiledElevationTerrainProvider.fromUrl(
      'https://elevation3d.arcgis.com/arcgis/rest/services/WorldElevation3D/Terrain3D/ImageServer',
    ),
  ]
  for (const load of loaders) {
    try {
      const provider = await load()
      if (!isViewerLive() || generation !== mountGeneration) return
      viewer.terrainProvider = provider
      viewer.scene.globe.depthTestAgainstTerrain = true
      let seenTiles = false
      let redrawn = false
      removeTileListener = viewer.scene.globe.tileLoadProgressEvent.addEventListener((queued) => {
        if (!isViewerLive()) return
        if (queued > 0) seenTiles = true
        if (!seenTiles || queued !== 0 || redrawn) return
        redrawn = true
        savedSource?.entities.removeAll()
        features.value.forEach(addSavedFeature)
      })
      return
    } catch {
      // try next terrain source
    }
  }
  if (generation === mountGeneration) terrainStarted = false
}

function bindResize() {
  resizeObserver = new ResizeObserver(() => {
    if (!isViewerLive()) return
    viewer.resize()
  })
  if (mapEl.value) resizeObserver.observe(mapEl.value)
}

watch(showUavLayer, (visible) => {
  if (!isViewerLive() || !uavLayer) return
  uavLayer.show = visible
  viewer.scene.requestRender()
})

watch(cameras, () => {
  if (cameraMarkersReady) renderCameraMarkers()
})

function teardownGisPage() {
  pageAlive = false
  destroyCameraPlayer()
  mountGeneration += 1
  cameraMarkersReady = false
  if (cameraRevealTimer) {
    window.clearTimeout(cameraRevealTimer)
    cameraRevealTimer = 0
  }
  window.removeEventListener('keydown', onDrawKeydown)
  introRunning = false
  if (introTimer) {
    window.clearTimeout(introTimer)
    introTimer = 0
  }
  cameraFlying = false
  userCameraActive = false
  stopGlobeSpin()
  try {
    viewer?.__globeSpinInputCleanup?.()
  } catch {
    // ignore
  }
  spinInputBound = false
  try {
    resizeObserver?.disconnect()
  } catch {
    // ignore
  }
  resizeObserver = undefined
  try {
    removeTileListener?.()
  } catch {
    // ignore
  }
  removeTileListener = undefined
  try {
    removeCameraListener?.()
  } catch {
    // ignore
  }
  removeCameraListener = undefined
  try {
    removeMorphListener?.()
  } catch {
    // ignore
  }
  removeMorphListener = undefined
  try {
    handler?.destroy()
  } catch {
    // ignore
  }
  handler = undefined
  try {
    if (viewer && !viewer.isDestroyed()) {
      viewer.camera.cancelFlight()
      viewer.clock.shouldAnimate = false
      viewer.destroy()
    }
  } catch {
    // Cesium 销毁中途抛错时仍继续复位，避免路由切换卡死
  }
  viewer = undefined
  draftSource = undefined
  savedSource = undefined
  cameraSource = undefined
  uavLayer = undefined
  labelLayer = undefined
  terrainStarted = false
  clickedTiandituTile.value = null
  activeFeatureId.value = null
  drawingPoints.value = []
  pendingAiPolygons.value = []
  manualDrawMode.value = false
  aiDelineateMode.value = false
  aiDelineating.value = false
}

onMounted(async () => {
  loadCameraDevices()
  const generation = ++mountGeneration
  pageAlive = true
  await nextTick()
  if (!pageAlive || generation !== mountGeneration) return
  try {
    createViewer()
    if (!isViewerLive() || generation !== mountGeneration) return
    viewer.resize()
    playEarthIntro()
    draftSource = new CustomDataSource('draft-features')
    savedSource = new CustomDataSource('saved-features')
    cameraSource = new CustomDataSource('camera-devices')
    cameraSource.show = false
    cameraMarkersReady = false
    await viewer.dataSources.add(savedSource)
    if (!isViewerLive() || generation !== mountGeneration) return
    await viewer.dataSources.add(draftSource)
    if (!isViewerLive() || generation !== mountGeneration) return
    await viewer.dataSources.add(cameraSource)
    if (!isViewerLive() || generation !== mountGeneration) return
    bindMapEvents()
    bindResize()
    window.addEventListener('keydown', onDrawKeydown)
    enableTerrain(generation)
    fetchFeatures()
    // 地球瓦片与入场动画完成后再显示摄像头标识
    if (!introRunning) queueCameraMarkersReveal()
  } catch (error) {
    if (pageAlive && generation === mountGeneration) {
      console.warn('[GisView] 初始化中断', error)
    }
  }
})

onBeforeUnmount(() => {
  teardownGisPage()
})
</script>

<template>
  <section class="gis-page">
    <header class="gis-page-head">
      <div>
        <p class="gis-kicker">GEO · INTELLIGENCE · WORKSPACE</p>
        <h2>GIS 标注工作台</h2>
        <span>在影像底图上创建、识别与管理空间标注</span>
      </div>
      <div class="gis-head-actions">
        <span class="gis-live-dot" />
        <span>空间服务在线</span>
      </div>
    </header>
    <div class="gis-toolbars" :style="{ '--toolbar-button-image': `url(${toolbarHudButton})`, '--toolbar-container-backdrop': `url(${toolbarContainerBackdrop})` }">
      <div class="gis-toolbar gis-toolbar-ai">
      <div class="toolbar-actions">
        <div class="toolbar-group toolbar-group-ai">
          <el-button
            :disabled="!canWrite || mapBusy"
            :loading="aiDelineating && aiProvider === 'roboflow'"
            :type="aiDelineateMode && aiProvider === 'roboflow' ? 'success' : 'primary'"
            :class="{ 'is-active-tech': aiDelineateMode && aiProvider === 'roboflow' }"
            :aria-pressed="aiDelineateMode && aiProvider === 'roboflow'"
            @click="handleAiDelineateClick('roboflow')"
          >
            <el-icon><MagicStick /></el-icon>
            AI 圈地
          </el-button>
          <el-button
            :disabled="!canWrite || mapBusy"
            :loading="aiDelineating && aiProvider === 'sam2.1'"
            :type="aiDelineateMode && aiProvider === 'sam2.1' ? 'success' : 'default'"
            :class="{ 'is-active-tech': aiDelineateMode && aiProvider === 'sam2.1' }"
            :aria-pressed="aiDelineateMode && aiProvider === 'sam2.1'"
            @click="handleAiDelineateClick('sam2.1')"
          >
            <el-icon><Cpu /></el-icon>
            SAM 2.1
          </el-button>
        </div>
      </div>
      </div>
      <div class="gis-toolbar gis-toolbar-operation">
        <div class="toolbar-actions">
        <div class="toolbar-group toolbar-group-draft">
          <el-button
            :disabled="!canWrite || mapBusy"
            :type="manualDrawMode ? 'success' : 'default'"
            :class="{ 'is-active-tech': manualDrawMode }"
            :aria-pressed="manualDrawMode"
            @click="toggleManualDraw"
          >
            <el-icon><EditPen /></el-icon>
            手动圈地
          </el-button>
          <el-button :disabled="!canSaveDraft" :loading="saving" type="primary" :class="{ 'is-ready-tech': canSaveDraft }" @click="savePolygon">
            <el-icon><Camera /></el-icon>
            {{ pendingAiPolygons.length ? `提交（${pendingAiPolygons.length}）` : '保存' }}
          </el-button>
          <el-button :disabled="aiDelineateMode || aiDraftLocked || !drawingPoints.length" @click="undoLastVertex">
            <el-icon><RefreshLeft /></el-icon>
            撤销
          </el-button>
          <el-button :disabled="aiDelineateMode || (!drawingPoints.length && !pendingAiPolygons.length)" @click="clearDraft">
            清空
          </el-button>
        </div>
      </div>
      </div>
    </div>
    <div class="gis-workbench">
      <aside class="camera-panel" aria-label="摄像头列表">
        <div class="camera-panel-head">
          <div>
            <strong>摄像头列表</strong>
            <span>{{ cameras.length }} 台设备</span>
          </div>
          <span class="camera-panel-signal">LIVE</span>
        </div>
        <div class="camera-list">
          <button
            v-for="camera in cameras"
            :key="camera.id"
            type="button"
            class="camera-list-item"
            :class="{ active: activeCameraId === camera.id }"
            @click="openCamera(camera)"
          >
            <span class="camera-status-dot" :class="camera.status === 'ONLINE' ? 'online' : 'offline'" />
            <span class="camera-list-copy">
              <strong>{{ camera.name }}</strong>
              <small>{{ camera.brand }} · {{ camera.serialNumber || '待配置序列号' }}</small>
            </span>
            <span class="camera-play-mark">▶</span>
          </button>
        </div>
        <p class="camera-panel-hint">选择设备后，在地图中打开实时预览</p>
      </aside>
      <div class="map-stage" :style="{ '--toolbar-button-image': `url(${toolbarHudButton})` }">
        <div
          ref="mapEl"
          class="cesium-map"
          :class="{ 'is-drawing': manualDrawMode || aiDelineateMode }"
        />
        <section v-if="showCameraPlayer" class="map-camera-player" aria-label="摄像头实时预览">
          <header>
            <div>
              <span class="camera-status-dot online" />
              <strong>{{ cameras.find((item) => item.id === activeCameraId)?.name }}</strong>
              <small>LIVE · HLS</small>
            </div>
            <button type="button" aria-label="关闭视频预览" @click="closeCameraPlayer">×</button>
          </header>
          <div class="map-camera-video-wrap">
            <video ref="cameraVideoEl" class="map-camera-video" controls autoplay muted playsinline />
            <p v-if="cameraPlayerError" class="camera-player-error">{{ cameraPlayerError }}</p>
          </div>
        </section>
        <div class="map-uav-tools" :class="{ 'is-active-tech': showUavLayer }">
          <el-switch v-model="showUavLayer" class="uav-switch" active-text="无人机影像" />
          <el-button @click="flyToUav">
            <el-icon><Location /></el-icon>
            定位
          </el-button>
        </div>
        <div class="map-compass" aria-hidden="true">
          <span :style="{ transform: `rotate(${-(cameraStatus.heading || 0)}deg)` }">
            <b>N</b>
          </span>
        </div>
        <div class="map-zoom">
          <button type="button" aria-label="放大" @click="zoomBy(-1)">+</button>
          <button type="button" aria-label="缩小" @click="zoomBy(1)">−</button>
        </div>
        <div class="coord-chip">
          <el-icon><Location /></el-icon>
          经度：{{ cameraLonText }}　纬度：{{ cameraLatText }}　高度：{{ cameraHeightText }}
        </div>
        <Transition name="hud-fade">
          <div v-if="mapBusy" class="map-hud" role="status" aria-live="polite">
            <div class="map-hud-grid" />
            <div class="map-hud-scan" />
            <div class="map-hud-vignette" />
            <div class="map-hud-radar" />
            <span class="map-hud-corner is-tl" />
            <span class="map-hud-corner is-tr" />
            <span class="map-hud-corner is-bl" />
            <span class="map-hud-corner is-br" />
            <div class="map-hud-core">
              <i class="ring ring-a" />
              <i class="ring ring-b" />
              <i class="ring ring-c" />
              <i class="hex" />
              <i class="pulse" />
            </div>
            <div class="map-hud-crosshair" />
            <div class="map-hud-telemetry is-left">
              <p>SYS.GIS.HUB</p>
              <p>MODE · DELINEATE</p>
              <p>CRS · EPSG:4326</p>
              <p>SENSOR · SAT-RGB</p>
            </div>
            <div class="map-hud-telemetry is-right">
              <p>PIPE · WORKFLOW</p>
              <p>LOCK · TILE</p>
              <p>SYNC · ACTIVE</p>
              <p>STAT · PROCESSING</p>
            </div>
            <div class="map-hud-copy">
              <em>{{ mapBusyTitle }}</em>
              <span>{{ mapBusySub }}</span>
              <b />
            </div>
          </div>
        </Transition>
      </div>
      <aside class="feature-panel" :style="{ '--panel-landscape-image': `url(${panelLandscape})` }">
        <div class="panel-head">
          <div>
            <strong>服务器标注</strong>
            <span>{{ features.length }} 个已保存地块</span>
          </div>
          <el-button :loading="loading" text type="primary" @click="fetchFeatures">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
        <div class="panel-body">
        <div v-if="clickedTiandituTile" class="tile-info">
          <strong>最近点击瓦片</strong>
          <img class="tile-preview" :src="clickedTiandituTile.imageUrl" crossorigin="anonymous" alt="天地图影像瓦片" />
          <span>层级 {{ clickedTiandituTile.level }} / X {{ clickedTiandituTile.x }} / Y {{ clickedTiandituTile.y }}</span>
          <span>{{ clickedTiandituTile.lon.toFixed(6) }}, {{ clickedTiandituTile.lat.toFixed(6) }}</span>
          <div class="tile-links">
            <a :href="clickedTiandituTile.imageUrl" target="_blank" rel="noreferrer">影像瓦片</a>
            <a :href="clickedTiandituTile.labelUrl" target="_blank" rel="noreferrer">注记瓦片</a>
          </div>
          <div class="tile-corners">
            <div class="tile-corners-head">
              <strong>WGS84 四角（经度, 纬度）</strong>
              <el-button text type="primary" @click="copyTileCorners">复制</el-button>
            </div>
            <pre>{{ clickedTiandituTile.cornersText }}</pre>
          </div>
        </div>
        <el-empty v-if="!features.length" description="暂无地块，可开启手动圈地或 AI 圈地" />
        <div v-else class="feature-list">
          <div v-for="feature in features" :key="feature.id" class="feature-item" :class="{ active: activeFeatureId === feature.id }">
            <img class="feature-pin" :src="annotationPin" alt="" />
            <button type="button" @click="flyToFeature(feature)">
              <strong>{{ feature.name }}</strong>
              <span>{{ featureTypeLabel(feature) }}</span>
            </button>
            <el-button v-if="canWrite" class="feature-delete" text aria-label="删除标注" title="删除标注" @click.stop="removeFeature(feature)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.gis-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
  height: 100%;
  min-height: 0;
}

.gis-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 10px;
  min-height: 0;
  padding: 8px 12px;
}

.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1 1 auto;
}

.toolbar-actions :deep(.el-button),
.toolbar-actions :deep(.el-switch) {
  flex: 0 0 auto;
}

.uav-switch :deep(.el-switch__label) {
  white-space: nowrap;
}

.gis-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.map-stage {
  position: relative;
  min-height: 0;
  height: 100%;
}

.cesium-map {
  position: relative;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  cursor: grab;
  background: #000;
  border: 1px solid #dfe7f1;
  border-radius: 8px;
}

.cesium-map.is-drawing {
  cursor: crosshair;
}

.cesium-map :deep(.cesium-viewer),
.cesium-map :deep(.cesium-viewer-cesiumWidgetContainer),
.cesium-map :deep(.cesium-widget) {
  width: 100%;
  height: 100%;
}

.cesium-map :deep(.cesium-viewer-bottom),
.cesium-map :deep(.cesium-credit-logoContainer),
.cesium-map :deep(.cesium-widget-credits) {
  display: none !important;
}

.map-hud {
  position: absolute;
  inset: 0;
  z-index: 20;
  overflow: hidden;
  border-radius: 22px;
  pointer-events: auto;
  background:
    radial-gradient(ellipse at center, rgba(8, 28, 48, 0.28) 0%, rgba(2, 8, 18, 0.78) 72%),
    rgba(1, 10, 22, 0.55);
  color: #67e8f9;
  font-family: ui-monospace, 'Cascadia Code', 'SF Mono', Menlo, Consolas, monospace;
}

.map-hud-grid {
  position: absolute;
  inset: -20%;
  background-image:
    linear-gradient(rgba(34, 211, 238, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(34, 211, 238, 0.08) 1px, transparent 1px);
  background-size: 42px 42px;
  transform: perspective(520px) rotateX(58deg) translateY(-8%);
  transform-origin: center top;
  animation: hud-grid 8s linear infinite;
  mask-image: linear-gradient(to bottom, transparent, #000 22%, #000 78%, transparent);
}

.map-hud-scan {
  position: absolute;
  left: 0;
  right: 0;
  height: 28%;
  background: linear-gradient(
    to bottom,
    transparent,
    rgba(34, 211, 238, 0.08),
    rgba(103, 232, 249, 0.28),
    rgba(34, 211, 238, 0.08),
    transparent
  );
  animation: hud-scan 2.4s ease-in-out infinite;
  mix-blend-mode: screen;
}

.map-hud-vignette {
  position: absolute;
  inset: 0;
  box-shadow: inset 0 0 120px rgba(0, 0, 0, 0.55);
  background: radial-gradient(circle at 50% 42%, transparent 18%, rgba(0, 12, 28, 0.45) 100%);
}

.map-hud-radar {
  position: absolute;
  left: 50%;
  top: 42%;
  width: min(72vmin, 560px);
  height: min(72vmin, 560px);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  opacity: 0.85;
  overflow: hidden;
}

.map-hud-radar::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: conic-gradient(from 0deg, transparent 0 72%, rgba(34, 211, 238, 0.28) 86%, transparent 100%);
  mask-image: radial-gradient(circle, transparent 28%, #000 29%, #000 62%, transparent 63%);
  animation: hud-spin 3.2s linear infinite;
}

.map-hud-corner {
  position: absolute;
  width: 54px;
  height: 54px;
  border: 2px solid rgba(103, 232, 249, 0.85);
  box-shadow: 0 0 12px rgba(34, 211, 238, 0.35);
}

.map-hud-corner.is-tl {
  top: 16px;
  left: 16px;
  border-right: 0;
  border-bottom: 0;
}

.map-hud-corner.is-tr {
  top: 16px;
  right: 16px;
  border-left: 0;
  border-bottom: 0;
}

.map-hud-corner.is-bl {
  bottom: 16px;
  left: 16px;
  border-right: 0;
  border-top: 0;
}

.map-hud-corner.is-br {
  right: 16px;
  bottom: 16px;
  border-left: 0;
  border-top: 0;
}

.map-hud-core {
  position: absolute;
  left: 50%;
  top: 42%;
  width: 168px;
  height: 168px;
  transform: translate(-50%, -50%);
}

.map-hud-core .ring,
.map-hud-core .hex,
.map-hud-core .pulse {
  position: absolute;
  inset: 0;
  margin: auto;
}

.map-hud-core .ring {
  border: 1px solid rgba(103, 232, 249, 0.35);
  border-radius: 50%;
  box-shadow: 0 0 18px rgba(34, 211, 238, 0.18);
}

.map-hud-core .ring-a {
  width: 168px;
  height: 168px;
  border-top-color: #67e8f9;
  border-right-color: transparent;
  animation: hud-spin 2.8s linear infinite;
}

.map-hud-core .ring-b {
  width: 128px;
  height: 128px;
  border-bottom-color: #22d3ee;
  border-left-color: transparent;
  animation: hud-spin 4.2s linear infinite reverse;
}

.map-hud-core .ring-c {
  width: 92px;
  height: 92px;
  border-style: dashed;
  animation: hud-spin 6s linear infinite;
}

.map-hud-core .hex {
  width: 52px;
  height: 52px;
  background: linear-gradient(135deg, rgba(34, 211, 238, 0.85), rgba(14, 165, 233, 0.35));
  clip-path: polygon(25% 6%, 75% 6%, 100% 50%, 75% 94%, 25% 94%, 0 50%);
  box-shadow: 0 0 24px rgba(34, 211, 238, 0.8);
  animation: hud-hex 1.6s ease-in-out infinite;
}

.map-hud-core .pulse {
  width: 52px;
  height: 52px;
  border: 1px solid rgba(103, 232, 249, 0.7);
  border-radius: 50%;
  animation: hud-pulse 1.8s ease-out infinite;
}

.map-hud-core i {
  display: block;
  font-style: normal;
}

.map-hud-crosshair::before,
.map-hud-crosshair::after {
  content: '';
  position: absolute;
  background: rgba(103, 232, 249, 0.22);
}

.map-hud-crosshair::before {
  top: 12%;
  bottom: 12%;
  left: 50%;
  width: 1px;
}

.map-hud-crosshair::after {
  left: 8%;
  right: 8%;
  top: 42%;
  height: 1px;
}

.map-hud-telemetry {
  position: absolute;
  top: 78px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: rgba(165, 243, 252, 0.72);
  text-shadow: 0 0 8px rgba(34, 211, 238, 0.45);
}

.map-hud-telemetry p {
  margin: 0;
  padding-left: 10px;
  border-left: 2px solid rgba(34, 211, 238, 0.55);
  animation: hud-blink 2.4s steps(1) infinite;
}

.map-hud-telemetry p:nth-child(2) { animation-delay: 0.3s; }
.map-hud-telemetry p:nth-child(3) { animation-delay: 0.6s; }
.map-hud-telemetry p:nth-child(4) { animation-delay: 0.9s; }

.map-hud-telemetry.is-left {
  left: 28px;
}

.map-hud-telemetry.is-right {
  right: 28px;
  text-align: right;
}

.map-hud-telemetry.is-right p {
  padding-left: 0;
  padding-right: 10px;
  border-left: 0;
  border-right: 2px solid rgba(34, 211, 238, 0.55);
}

.map-hud-copy {
  position: absolute;
  left: 50%;
  bottom: 48px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: min(86%, 420px);
  transform: translateX(-50%);
  text-align: center;
}

.map-hud-copy em {
  font-style: normal;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: #ecfeff;
  text-shadow: 0 0 18px rgba(34, 211, 238, 0.85);
}

.map-hud-copy span {
  color: rgba(165, 243, 252, 0.82);
  font-size: 11px;
  letter-spacing: 0.28em;
}

.map-hud-copy b {
  display: block;
  width: 100%;
  height: 3px;
  overflow: hidden;
  background: rgba(34, 211, 238, 0.18);
  border-radius: 999px;
}

.map-hud-copy b::after {
  content: '';
  display: block;
  width: 38%;
  height: 100%;
  background: linear-gradient(90deg, transparent, #67e8f9, #22d3ee, transparent);
  animation: hud-bar 1.4s ease-in-out infinite;
  box-shadow: 0 0 12px #22d3ee;
}

.hud-fade-enter-active,
.hud-fade-leave-active {
  transition: opacity 0.28s ease;
}

.hud-fade-enter-from,
.hud-fade-leave-to {
  opacity: 0;
}

@keyframes hud-scan {
  0% { top: -28%; }
  100% { top: 100%; }
}

@keyframes hud-spin {
  to { transform: rotate(360deg); }
}

@keyframes hud-hex {
  0%, 100% { transform: scale(0.92); filter: brightness(1); }
  50% { transform: scale(1.08); filter: brightness(1.35); }
}

@keyframes hud-pulse {
  0% { transform: scale(1); opacity: 0.7; }
  100% { transform: scale(2.4); opacity: 0; }
}

@keyframes hud-bar {
  0% { transform: translateX(-120%); }
  100% { transform: translateX(280%); }
}

@keyframes hud-grid {
  0% { background-position: 0 0, 0 0; }
  100% { background-position: 0 42px, 42px 0; }
}

@keyframes hud-blink {
  0%, 70% { opacity: 1; }
  71%, 78% { opacity: 0.25; }
  79%, 100% { opacity: 1; }
}

.feature-panel {
  overflow: auto;
  padding: 14px;
  background: #fff;
  border: 1px solid #dfe7f1;
  border-radius: 8px;
}

.panel-head,
.feature-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tile-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 12px 0;
  padding: 10px;
  color: #172033;
  background: #f8fafc;
  border: 1px solid #e4ebf5;
  border-radius: 8px;
  font-size: 12px;
}

.tile-info strong {
  font-size: 14px;
}

.tile-info span {
  overflow-wrap: anywhere;
  color: #526070;
}

.tile-preview {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  background: #0b1d33;
  border: 1px solid #dfe7f1;
  border-radius: 6px;
}

.tile-links {
  display: flex;
  gap: 12px;
}

.tile-links a {
  color: #1677ff;
  text-decoration: none;
}

.tile-corners {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tile-corners-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tile-corners pre {
  margin: 0;
  padding: 8px;
  color: #172033;
  background: #fff;
  border: 1px solid #e4ebf5;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.feature-item {
  padding: 10px;
  border: 1px solid #e4ebf5;
  border-radius: 8px;
}

.feature-item button {
  min-width: 0;
  padding: 0;
  text-align: left;
  color: #172033;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.feature-item strong,
.feature-item span {
  display: block;
}

.feature-item span {
  margin-top: 4px;
  color: #68758a;
  font-size: 12px;
}

@media (max-width: 1280px) {
  .gis-workbench {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(360px, 1fr) minmax(200px, 32vh);
  }

  .map-stage,
  .cesium-map {
    min-height: 360px;
  }

  .coord-chip {
    max-width: calc(100% - 24px);
  }
}

.feature-panel {
  background: linear-gradient(160deg, rgba(12, 28, 46, 0.78), rgba(8, 18, 32, 0.72));
  border: 1px solid rgba(127, 232, 203, 0.16);
  color: #dbeaff;
  border-radius: 22px;
  box-shadow: inset 0 1px rgba(227, 249, 255, 0.06), 0 10px 30px #020a174d;
  backdrop-filter: blur(16px);
}

.gis-toolbar {
  gap: 8px 10px;
  min-height: 0;
  padding: 8px 12px;
}

.gis-toolbar :deep(.el-button) {
  min-height: 38px;
  padding-inline: 14px;
  border-radius: 999px;
  font-weight: 600;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.gis-toolbar :deep(.el-button--default) {
  --el-button-bg-color: rgba(255, 255, 255, 0.04);
  --el-button-border-color: rgba(127, 232, 203, 0.22);
  --el-button-text-color: #d7eef4;
  --el-button-hover-bg-color: rgba(127, 232, 203, 0.12);
  --el-button-hover-border-color: rgba(127, 232, 203, 0.45);
  --el-button-hover-text-color: #e8fffb;
}

.gis-toolbar :deep(.el-button--primary),
.gis-toolbar :deep(.el-button--success) {
  --el-button-bg-color: #5fe0c8;
  --el-button-border-color: #7fe8cb;
  --el-button-hover-bg-color: #74ead4;
  --el-button-hover-border-color: #97f3dc;
  --el-button-text-color: #07342d;
  --el-button-hover-text-color: #041923;
  box-shadow: 0 0 18px rgba(95, 224, 200, 0.22);
}

.gis-toolbar :deep(.el-switch) {
  flex: 0 0 auto;
  height: 38px;
}

.gis-toolbar :deep(.el-switch__label) {
  color: #c5dce6;
  white-space: nowrap;
}

.gis-toolbar :deep(.el-switch.is-checked .el-switch__core) {
  background: #5fe0c8;
  border-color: #5fe0c8;
}

.gis-workbench {
  grid-template-columns: minmax(0, 1fr) minmax(260px, 320px);
  gap: 14px;
}

.map-stage {
  overflow: hidden;
  border: 1px solid rgba(50, 217, 237, 0.42);
  border-radius: 22px;
  background: #030d19;
  box-shadow: 0 0 0 1px #30d6ec16, 0 14px 34px #02081366, 0 0 28px #00ccff14;
}

.cesium-map {
  border: 0;
  border-radius: inherit;
}

.map-compass {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 8;
  width: 46px;
  height: 46px;
  pointer-events: none;
}

.map-compass span {
  position: relative;
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  border: 1px solid rgba(210, 232, 236, 0.55);
  border-radius: 50%;
  background: rgba(8, 18, 28, 0.72);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.28);
}

.map-compass b {
  font-size: 11px;
  font-weight: 700;
  color: #e8f6ff;
}

.map-compass span::before {
  content: '';
  position: absolute;
  top: 4px;
  width: 0;
  height: 0;
  border-left: 5px solid transparent;
  border-right: 5px solid transparent;
  border-bottom: 9px solid #ef4444;
}

.map-zoom {
  position: absolute;
  top: 72px;
  left: 20px;
  z-index: 8;
  display: grid;
  overflow: hidden;
  border: 1px solid rgba(210, 232, 236, 0.35);
  border-radius: 8px;
  background: rgba(8, 18, 28, 0.72);
}

.map-zoom button {
  width: 28px;
  height: 24px;
  border: 0;
  border-bottom: 1px solid rgba(210, 232, 236, 0.2);
  background: transparent;
  color: #e8f6ff;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}

.map-zoom button:last-child {
  border-bottom: 0;
}

.coord-chip {
  position: absolute;
  right: 16px;
  bottom: 16px;
  z-index: 8;
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  max-width: calc(100% - 32px);
  padding: 8px 12px;
  border: 1px solid rgba(127, 232, 203, 0.22);
  border-radius: 999px;
  background: rgba(8, 18, 28, 0.72);
  color: #d7eef4;
  font-size: 12px;
  backdrop-filter: blur(10px);
}

.cesium-map :deep(.cesium-viewer-toolbar) {
  top: 14px;
  right: 14px;
  display: flex;
  gap: 8px;
}

.cesium-map :deep(.cesium-button) {
  width: 36px;
  height: 36px;
  margin: 0;
  border: 1px solid rgba(210, 232, 236, 0.35);
  border-radius: 10px;
  background: rgba(8, 18, 28, 0.78);
  box-shadow: none;
}

.cesium-map :deep(.cesium-toolbar-button svg),
.cesium-map :deep(.cesium-svgPath-node) {
  fill: #d7eef4;
}

.feature-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding: 16px 16px 12px;
  scrollbar-color: #315674 transparent;
}

.panel-head {
  flex-shrink: 0;
  z-index: 2;
  margin: 0 0 12px;
  padding: 0 0 12px;
  background: transparent;
  border-bottom: 1px solid #5fc9ec24;
}

.panel-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 3px 2px 4px 0;
}

.panel-head strong {
  color: #e8f6ff;
  font-size: 16px;
  letter-spacing: 0.02em;
}

.panel-head :deep(.el-button) {
  font-weight: 600;
  color: #7fe8cb;
}

.tile-info {
  color: #d9e9fc;
  background: linear-gradient(145deg, #13243a, #0e1b2c);
  border: 1px solid #314a65;
  border-radius: 14px;
}

.tile-info span,
.feature-item span { color: #9bb0c8; }
.tile-preview { border-color: #3a5571; border-radius: 8px; }
.tile-links a { color: #70dfff; }
.tile-corners pre { color: #c8dcf1; background: #0a1524; border-color: #324b65; }

.feature-list {
  gap: 10px;
  flex: 1;
}

.feature-item {
  min-height: 72px;
  padding: 12px;
  border-color: rgba(80, 130, 150, 0.28);
  border-radius: 16px;
  background: linear-gradient(120deg, rgba(18, 36, 56, 0.9), rgba(12, 26, 42, 0.86));
  transition: border-color 160ms ease, transform 160ms ease, background 160ms ease;
}

.feature-item:hover {
  border-color: rgba(70, 216, 238, 0.54);
  background: linear-gradient(120deg, #15304a, #11243a);
}

.feature-pin {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  object-fit: contain;
  filter: drop-shadow(0 0 7px rgba(54, 224, 232, 0.28));
}

.feature-item button {
  flex: 1;
  min-width: 0;
  color: #d5e7fa;
}

.feature-item strong {
  overflow: hidden;
  color: #e6f4ff;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feature-item span {
  font-size: 11px;
}

.feature-delete {
  width: 28px;
  min-width: 28px !important;
  height: 28px;
  margin-left: 4px;
  padding: 0 !important;
  border: 1px solid transparent;
  border-radius: 8px !important;
  color: #87a5ba !important;
  transition: color 160ms ease, background 160ms ease, border-color 160ms ease, transform 160ms ease;
}

.feature-delete :deep(span) {
  margin: 0;
  width: 100%;
  justify-content: center;
}

.feature-delete :deep(.el-icon) {
  font-size: 14px;
}

.feature-delete:hover {
  color: #ff9b9b !important;
  border-color: rgba(255, 112, 112, 0.35);
  background: rgba(244, 83, 83, 0.12) !important;
  transform: scale(1.06);
}

.feature-panel :deep(.el-empty__description p) {
  color: #9bb0c8;
}

/* GIS workbench layout */
.gis-page {
  gap: 14px;
  min-width: 0;
}

.gis-page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  min-height: 72px;
  padding: 12px 18px;
  border: 1px solid rgba(59, 207, 230, 0.22);
  border-radius: 16px;
  background:
    linear-gradient(100deg, rgba(7, 24, 41, 0.94), rgba(8, 29, 47, 0.72)),
    radial-gradient(circle at 85% 0%, rgba(39, 201, 231, 0.17), transparent 32%);
  box-shadow: inset 0 1px rgba(221, 250, 255, 0.06), 0 10px 30px rgba(1, 11, 24, 0.3);
}

.gis-kicker {
  margin: 0 0 4px;
  color: #4ed3e9;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.17em;
}

.gis-page-head h2 {
  margin: 0;
  color: #eefaff;
  font-size: 22px;
  letter-spacing: 0.02em;
}

.gis-page-head > div > span {
  display: block;
  margin-top: 5px;
  color: #88a9be;
  font-size: 12px;
}

.gis-head-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  color: #a8c7d8;
  font-size: 12px;
}

.gis-live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #57e5bf;
  box-shadow: 0 0 10px #57e5bf;
}

.gis-toolbars {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
}

.gis-toolbar {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 0;
  min-height: 72px;
  padding: 8px 16px;
  overflow: hidden;
  border: 1px solid rgba(92, 226, 242, 0.42);
  border-radius: 16px;
  background:
    linear-gradient(110deg, rgba(27, 94, 119, 0.42), rgba(12, 57, 80, 0.34)),
    var(--toolbar-container-backdrop) center / 100% 100% no-repeat;
  background-blend-mode: screen, normal;
  box-shadow:
    inset 0 1px 0 rgba(214, 255, 255, 0.2),
    inset 0 0 0 1px rgba(4, 22, 34, 0.45),
    0 10px 26px rgba(1, 8, 16, 0.32);
}

.toolbar-actions {
  flex: 1 1 auto;
  flex-wrap: wrap;
  gap: 16px;
}

.gis-toolbar :deep(.el-button) {
  min-width: 140px;
  min-height: 56px;
  padding-inline: 22px;
  border: 0;
  border-radius: 0;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #f0ffff !important;
  text-shadow: 0 1px 3px #01080d, 0 0 8px rgba(143, 252, 255, 0.55);
  background:
    linear-gradient(rgba(2, 14, 21, 0.38), rgba(2, 14, 21, 0.38)),
    var(--toolbar-button-image) center / 100% 100% no-repeat;
  filter: saturate(0.72) brightness(0.82) drop-shadow(0 0 5px rgba(39, 205, 234, 0.1));
  transition: filter 180ms ease, transform 180ms ease;
}

.gis-toolbar :deep(.el-button .el-icon) {
  margin-right: 12px;
  font-size: 17px;
}

.gis-toolbar :deep(.el-button:hover:not(.is-disabled)) {
  filter: brightness(1.18) drop-shadow(0 0 12px rgba(70, 233, 242, 0.46));
  transform: translateY(-1px);
}

.gis-toolbar :deep(.el-button.is-active-tech) {
  color: #fff !important;
  background:
    linear-gradient(135deg, rgba(60, 183, 191, 0.18), rgba(7, 92, 118, 0.18)),
    var(--toolbar-button-image) center / 100% 100% no-repeat !important;
  filter: saturate(0.84) brightness(1.03) drop-shadow(0 0 8px rgba(75, 223, 225, 0.32));
  box-shadow: inset 0 0 0 1px rgba(116, 234, 233, 0.48), 0 0 7px rgba(52, 192, 206, 0.16);
}

.gis-toolbar :deep(.el-button.is-ready-tech:not(.is-disabled):not(.is-active-tech)) {
  box-shadow: inset 0 0 14px rgba(110, 255, 235, 0.22), 0 0 10px rgba(68, 232, 218, 0.24);
}

.toolbar-group :deep(.el-button--primary),
.toolbar-group :deep(.el-button--success) {
  --el-button-text-color: #f0ffff;
  --el-button-hover-text-color: #fff;
  background:
    linear-gradient(135deg, rgba(18, 116, 132, 0.36), rgba(5, 58, 78, 0.42)),
    var(--toolbar-button-image) center / 100% 100% no-repeat !important;
  color: #f0ffff !important;
  filter: saturate(0.76) brightness(0.9) drop-shadow(0 0 7px rgba(69, 222, 226, 0.2));
}

.toolbar-group {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 16px;
  white-space: nowrap;
}

.toolbar-group-ai :deep(.el-button--primary),
.toolbar-group-draft :deep(.el-button--primary) {
  box-shadow: 0 0 16px rgba(81, 221, 224, 0.2);
}

.gis-workbench {
  grid-template-columns: 254px minmax(0, 1fr) 308px;
  gap: 14px;
}

.camera-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding: 16px 14px 12px;
  border: 1px solid rgba(74, 204, 228, 0.36);
  border-radius: 16px;
  background:
    linear-gradient(142deg, rgba(18, 75, 96, 0.34), transparent 45%),
    linear-gradient(180deg, rgba(6, 25, 42, 0.96), rgba(5, 18, 33, 0.82));
  box-shadow: inset 0 1px 0 rgba(206, 253, 255, 0.12), 0 16px 36px rgba(2, 8, 19, 0.28);
}

.camera-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 0 2px 12px;
  border-bottom: 1px solid rgba(95, 201, 236, 0.14);
}

.camera-panel-head strong,
.camera-panel-head span {
  display: block;
}

.camera-panel-head strong {
  color: #edfaff;
  font-size: 16px;
}

.camera-panel-head span {
  margin-top: 4px;
  color: #91b8c9;
  font-size: 12px;
}

.camera-panel-head .camera-panel-signal {
  margin-top: 2px;
  color: #7eead4;
  font: 800 10px/1 ui-monospace, 'Cascadia Code', monospace;
  letter-spacing: 0.12em;
}

.camera-list {
  display: grid;
  gap: 9px;
  padding-top: 13px;
}

.camera-list-item {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 66px;
  gap: 10px;
  padding: 10px;
  cursor: pointer;
  text-align: left;
  color: #e9fbff;
  border: 1px solid rgba(93, 199, 223, 0.2);
  border-radius: 10px;
  background: linear-gradient(110deg, rgba(18, 76, 96, 0.28), rgba(4, 23, 39, 0.5));
  transition: border-color 160ms ease, background 160ms ease, transform 160ms ease;
}

.camera-list-item:hover,
.camera-list-item.active {
  border-color: rgba(109, 223, 226, 0.6);
  background: linear-gradient(110deg, rgba(29, 120, 139, 0.42), rgba(6, 50, 70, 0.58));
  transform: translateY(-1px);
}

.camera-status-dot {
  display: inline-block;
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.camera-status-dot.online {
  background: #55dab9;
  box-shadow: 0 0 8px rgba(85, 218, 185, 0.7);
}

.camera-status-dot.offline {
  background: #7b8c99;
}

.camera-list-copy {
  display: grid;
  min-width: 0;
  gap: 4px;
  flex: 1;
}

.camera-list-copy strong {
  overflow: hidden;
  color: #e9fbff;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.camera-list-copy small {
  color: #89afc0;
  font-size: 11px;
}

.camera-play-mark {
  color: #8debe8;
  font-size: 12px;
}

.camera-panel-hint {
  margin: auto 2px 2px;
  padding-top: 13px;
  border-top: 1px solid rgba(95, 201, 236, 0.12);
  color: #799bad;
  font-size: 11px;
  line-height: 1.5;
}

.map-stage {
  position: relative;
  border-radius: 16px;
  border-color: rgba(42, 213, 235, 0.65);
  box-shadow: 0 0 0 1px rgba(48, 214, 236, 0.1), 0 16px 36px rgba(2, 8, 19, 0.48);
}

.map-camera-player {
  position: absolute;
  z-index: 12;
  bottom: 16px;
  left: 16px;
  width: min(390px, calc(100% - 32px));
  overflow: hidden;
  border: 1px solid rgba(92, 226, 242, 0.52);
  border-radius: 12px;
  background: rgba(3, 14, 25, 0.94);
  box-shadow: 0 14px 30px rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(12px);
}

.map-camera-player header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 40px;
  padding: 0 8px 0 11px;
  border-bottom: 1px solid rgba(92, 226, 242, 0.18);
}

.map-camera-player header > div {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
}

.map-camera-player header strong {
  overflow: hidden;
  color: #edfaff;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.map-camera-player header small {
  color: #80c8cf;
  font: 700 10px/1 ui-monospace, 'Cascadia Code', monospace;
}

.map-camera-player header button {
  width: 26px;
  height: 26px;
  cursor: pointer;
  color: #c8edf1;
  border: 0;
  border-radius: 6px;
  background: transparent;
  font-size: 22px;
  line-height: 1;
}

.map-camera-player header button:hover {
  background: rgba(115, 227, 234, 0.14);
}

.map-camera-video-wrap {
  position: relative;
  aspect-ratio: 16 / 9;
  background: #02080f;
}

.map-camera-video {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.camera-player-error {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  margin: 0;
  padding: 20px;
  text-align: center;
  color: #ffd4c7;
  background: rgba(3, 11, 20, 0.82);
  font-size: 12px;
}

.map-uav-tools {
  position: absolute;
  top: 16px;
  right: 66px;
  z-index: 9;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  overflow: hidden;
  border: 1px solid rgba(92, 226, 242, 0.42);
  border-radius: 14px;
  background:
    linear-gradient(180deg, rgba(16, 48, 68, 0.28), transparent 48%),
    rgba(3, 15, 27, 0.86);
  box-shadow:
    inset 0 1px 0 rgba(214, 255, 255, 0.18),
    inset 0 0 0 1px rgba(4, 22, 34, 0.4),
    0 8px 22px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(10px);
}

.map-uav-tools.is-active-tech {
  border-color: rgba(98, 211, 217, 0.52);
  box-shadow:
    inset 0 0 10px rgba(85, 215, 220, 0.08),
    0 0 9px rgba(52, 190, 201, 0.14);
}

.map-uav-tools :deep(.el-switch__label) {
  color: #d9fbff;
  font-size: 12px;
}

.map-uav-tools :deep(.el-button) {
  min-width: 86px;
  min-height: 38px;
  padding-inline: 12px;
  border: 0;
  border-radius: 0;
  color: #f0ffff;
  font-weight: 700;
  background:
    linear-gradient(rgba(2, 14, 21, 0.28), rgba(2, 14, 21, 0.28)),
    var(--toolbar-button-image) center / 100% 100% no-repeat;
  text-shadow: 0 1px 3px #01080d;
}

.map-uav-tools :deep(.el-button .el-icon) {
  margin-right: 8px;
}

.feature-panel {
  position: relative;
  overflow: hidden;
  padding: 16px 14px 12px;
  border-radius: 16px;
  background-image:
    linear-gradient(180deg, rgba(7, 23, 40, 0.84) 0%, rgba(6, 19, 34, 0.5) 64%, rgba(6, 19, 34, 0.3) 100%),
    linear-gradient(180deg, rgba(5, 18, 32, 0.05), rgba(5, 18, 32, 0.22)),
    var(--panel-landscape-image);
  background-position: center, center, center bottom;
  background-repeat: no-repeat;
  background-size: cover, cover, auto 300px;
  border-color: rgba(53, 199, 225, 0.34);
}

.panel-head {
  flex-shrink: 0;
  margin: 0 0 12px;
  padding: 0 0 12px;
  background: transparent;
}

.panel-head strong {
  display: block;
  font-size: 16px;
}

.panel-head > div > span {
  display: block;
  margin-top: 4px;
  color: #7196ad;
  font-size: 11px;
}

.feature-list {
  gap: 9px;
}

.feature-item {
  min-height: 70px;
  border-radius: 12px;
  background: linear-gradient(120deg, rgba(18, 45, 69, 0.88), rgba(10, 28, 46, 0.88));
}

.feature-item.active {
  border-color: #39d6e8;
  background: linear-gradient(120deg, rgba(17, 67, 91, 0.95), rgba(9, 43, 66, 0.94));
  box-shadow: inset 3px 0 #54e6ed, 0 0 18px rgba(34, 203, 227, 0.13);
}

.feature-item.active .feature-pin {
  filter: drop-shadow(0 0 10px rgba(84, 239, 236, 0.72));
}

.feature-item:focus-within {
  border-color: rgba(89, 223, 235, 0.72);
}

@media (max-width: 1400px) {
  .gis-toolbar { min-height: 0; }
}

@media (max-width: 1120px) {
  .gis-page-head { align-items: flex-start; }
  .gis-workbench { grid-template-columns: 224px minmax(0, 1fr); }
  .feature-panel { grid-column: 1 / -1; }
  .gis-toolbars { grid-template-columns: 1fr; }
}

@media (max-width: 900px) {
  .gis-page-head { flex-direction: column; gap: 10px; }
  .gis-head-actions { width: 100%; justify-content: flex-end; }
  .gis-workbench { grid-template-columns: 1fr; grid-template-rows: minmax(420px, 1fr) auto; }
  .camera-panel { max-height: 190px; }
  .feature-panel { max-height: 290px; }
  .gis-toolbar { min-height: 0; }
}

@media (max-width: 600px) {
  .gis-page-head { padding: 13px; }
  .gis-page-head h2 { font-size: 19px; }
  .gis-head-actions { justify-content: space-between; }
  .toolbar-actions :deep(.el-button) { padding-inline: 10px; }
  .uav-switch { margin-inline: 2px; }
  .gis-toolbar :deep(.el-button) { min-width: 124px; }
  .map-uav-tools { top: 12px; right: 12px; gap: 6px; padding: 5px 7px; }
  .map-uav-tools :deep(.el-switch__label) { display: none; }
}

@media (max-width: 1280px) {
  .gis-toolbar {
    gap: 8px;
  }

  .gis-workbench {
    grid-template-columns: 224px minmax(0, 1fr);
    grid-template-rows: minmax(360px, 1fr) minmax(200px, 30vh);
  }

  .camera-panel { max-height: none; }

  .feature-panel {
    grid-column: 1 / -1;
    max-height: 280px;
  }

  .map-stage {
    border-radius: 16px;
    min-height: 360px;
  }

  .coord-chip {
    right: 10px;
    bottom: 10px;
    font-size: 11px;
  }
}

@media (min-width: 901px) and (max-width: 1280px) {
  .gis-workbench {
    grid-template-columns: 224px minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr) minmax(200px, 30vh);
  }

  .feature-panel {
    grid-column: 1 / -1;
    max-height: none;
  }
}

@media (max-width: 900px) {
  .gis-workbench {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(360px, 1fr) auto auto;
  }

  .camera-panel,
  .feature-panel {
    grid-column: auto;
    max-height: 260px;
  }

  .map-camera-player {
    bottom: 12px;
    left: 12px;
    width: min(360px, calc(100% - 24px));
  }
}
</style>
