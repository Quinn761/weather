<script setup>
import 'cesium/Build/Cesium/Widgets/widgets.css'

import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
  UrlTemplateImageryProvider,
  VerticalOrigin,
  Viewer,
  WebMercatorTilingScheme,
} from 'cesium'
import { createGisFeature, deleteGisFeature, listGisFeatures, runRoboflowWorkflow, runSam2Delineation } from '@/api/gis'
import { listRoboflowPlots } from '@/utils/roboflowPlot'
import { useAuthStore } from '@/stores/auth'

Ion.defaultAccessToken = ''

const TIANDITU_TOKEN = '38ca5876c8ba7b71eb08803d408b6184'

const authStore = useAuthStore()
const mapEl = ref(null)
const features = ref([])
const drawingPoints = ref([])
const cursorCoordinate = ref(null)
const saving = ref(false)
const loading = ref(false)
const showUavLayer = ref(true)
const clickedTiandituTile = ref(null)
const aiDelineateMode = ref(false)
const aiProvider = ref('roboflow')
const aiProviderName = computed(() => aiProvider.value === 'sam2.1' ? 'SAM 2.1' : 'Roboflow')
const aiDelineating = ref(false)
const aiDraftLocked = ref(false)
const pendingAiPolygons = ref([])

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
let suppressClickUntil = 0
let resizeObserver
let removeTileListener
let uavLayer
let labelLayer
let flightSeq = 0
let introTimer = 0
let introRunning = false
let introMaxZoom = 2.5e7
let terrainStarted = false

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
const toolHint = computed(() => {
  if (!canWrite.value) return '当前账号只有查看权限'
  if (aiDelineating.value) return `正在调用 ${aiProviderName.value}…`
  if (aiDelineateMode.value) return aiProvider.value === 'sam2.1'
    ? 'SAM 2.1 已开启，请点击田块内部识别该地块（天地图影像）'
    : 'AI 圈地已开启，请点击田块获取瓦片并调用识别'
  if (pendingAiPolygons.value.length) {
    return `已识别 ${pendingAiPolygons.value.length} 块地，请点击上方「提交地块」保存`
  }
  if (aiDraftLocked.value) return polygonInvalid.value
    ? 'AI 结果已锁定且自相交，请清空后重试'
    : `AI 结果已锁定，请保存。面积 ${draftAreaText.value}，周长 ${draftLengthText.value}`
  if (polygonInvalid.value) return '多边形自相交，请撤销上一点后重画'
  if (drawingPoints.value.length >= 3) {
    return `再点起点闭合。面积 ${draftAreaText.value}，周长 ${draftLengthText.value}`
  }
  return `单击地图加点，右侧会显示当前天地图瓦片。右键撤销。已选 ${drawingPoints.value.length} 个点`
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

function handleAiDelineateClick(provider = 'roboflow') {
  if (mapBusy.value) return
  aiDelineateMode.value = !(aiDelineateMode.value && aiProvider.value === provider)
  aiProvider.value = provider
  if (aiDelineateMode.value) {
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
    savedSource.entities.add({
      name: feature.name,
      properties: { featureId: feature.id, name: feature.name, type: feature.type },
      polygon: {
        hierarchy: positions,
        material: Color.fromCssColorString('#22d3ee').withAlpha(0.4),
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
  }
}

async function fetchFeatures() {
  loading.value = true
  try {
    features.value = (await listGisFeatures()) || []
    savedSource?.entities.removeAll()
    features.value.forEach(addSavedFeature)
  } catch {
    features.value = []
  } finally {
    loading.value = false
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

const FLY_DURATION = 2.2
const HOME_DESTINATION = Cartesian3.fromDegrees(104.1954, 32.5, 10800000)
const SPACE_LOOK_TARGET = Cartesian3.fromDegrees(108.5, 22.5, 0)
const NADIR = {
  heading: 0,
  pitch: CesiumMath.toRadians(-90),
  roll: 0,
}

function beginFlight() {
  stopEarthIntro(true)
  flightSeq += 1
  return flightSeq
}

function isCurrentFlight(token) {
  return token === flightSeq
}

function unlockCamera() {
  if (!viewer) return
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
    pitchAdjustHeight: Number.isFinite(height) && height > 200000 ? 80000 : undefined,
    endTransform: Matrix4.IDENTITY,
    complete: () => {
      if (!isCurrentFlight(token)) return
      unlockCamera()
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
  if (!viewer) {
    introRunning = false
    return
  }
  const controller = viewer.scene.screenSpaceCameraController
  controller.maximumZoomDistance = introMaxZoom
  controller.enableInputs = true
  viewer.scene.requestRender()
  introRunning = false
}

function stopEarthIntro(restoreControls = true) {
  if (introTimer) {
    window.clearTimeout(introTimer)
    introTimer = 0
  }
  if (!introRunning) return
  if (restoreControls) restoreAfterIntro()
  else introRunning = false
}

function playEarthIntro() {
  if (!viewer) return
  if (viewer.scene.mode !== SceneMode.SCENE3D) {
    applyHomeView()
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
    duration: 1.6,
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

function flyHome() {
  if (!viewer) return
  beginFlight()
  viewer.camera.cancelFlight()
  applyHomeView()
}

function flyToPolygon(geometry) {
  const ring = geometry.coordinates[0] || []
  if (!ring.length) return
  const positions = ring.map((item) => {
    const height = terrainHeightAt(item[0], item[1], vertexHeight(item, 0))
    return Cartesian3.fromDegrees(item[0], item[1], height)
  })
  const sphere = BoundingSphere.fromPoints(positions)
  if (!sphere || !Number.isFinite(sphere.radius)) return
  const center = Cartographic.fromCartesian(sphere.center)
  if (!center) return

  if (viewer.scene.mode !== SceneMode.SCENE3D) {
    flyCameraTo(
      Cartesian3.fromRadians(center.longitude, center.latitude, Math.max(center.height + sphere.radius * 4.5, 800)),
    )
    return
  }

  const token = beginFlight()
  viewer.camera.cancelFlight()
  unlockCamera()
  viewer.camera.flyToBoundingSphere(sphere, {
    offset: new HeadingPitchRange(
      CesiumMath.toRadians(32),
      CesiumMath.toRadians(-28),
      Math.max(sphere.radius * 6.5, 900),
    ),
    duration: FLY_DURATION,
    complete: () => {
      if (!isCurrentFlight(token)) return
      unlockCamera()
    },
  })
}

function flyToFeature(feature) {
  const geometry = parseFeatureGeometry(feature)
  if (!viewer || !geometry) return
  if (geometry.type === 'Polygon') {
    flyToPolygon(geometry)
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

async function handleMapClick(event) {
  if (!canWrite.value || saving.value || aiDelineating.value || Date.now() < suppressClickUntil) return
  const position = pickOnGlobe(event.position)
  if (!position) return
  const coordinate = toLonLatHeight(position)
  updateClickedTiandituTile(coordinate)
  if (aiDelineateMode.value) {
    await runAiDelineate(clickedTiandituTile.value)
    return
  }
  if (aiDraftLocked.value) return
  addPolygonVertex(coordinate, event.position)
}

function handleMapMove(event) {
  if (!canWrite.value || aiDelineateMode.value || aiDraftLocked.value || !drawingPoints.value.length) return
  const position = pickOnGlobe(event.endPosition)
  cursorCoordinate.value = position ? toLonLatHeight(position) : null
}

function handleDoubleClick(event) {
  if (!canWrite.value || saving.value || aiDelineateMode.value || aiDraftLocked.value) return
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
  const tag = event.target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA') return
  if (event.key === 'Escape') {
    clearDraft()
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
    homeButton: true,
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
  viewer.scene.globe.preloadSiblings = false
  viewer.scene.globe.preloadAncestors = false
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
  viewer.homeButton.viewModel.command.beforeExecute.addEventListener((event) => {
    event.cancel = true
    flyHome()
  })
  viewer.scene.morphComplete.addEventListener(unlockCamera)
}

async function enableTerrain() {
  if (!viewer || viewer.isDestroyed() || terrainStarted) return
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
      viewer.terrainProvider = await load()
      viewer.scene.globe.depthTestAgainstTerrain = true
      let seenTiles = false
      let redrawn = false
      removeTileListener = viewer.scene.globe.tileLoadProgressEvent.addEventListener((queued) => {
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
  terrainStarted = false
}

function bindResize() {
  resizeObserver = new ResizeObserver(() => viewer?.resize())
  if (mapEl.value) resizeObserver.observe(mapEl.value)
}

watch(showUavLayer, (visible) => {
  if (uavLayer) uavLayer.show = visible
  viewer?.scene?.requestRender?.()
})

onMounted(async () => {
  await nextTick()
  createViewer()
  viewer?.resize()
  playEarthIntro()
  draftSource = new CustomDataSource('draft-features')
  savedSource = new CustomDataSource('saved-features')
  await viewer.dataSources.add(savedSource)
  await viewer.dataSources.add(draftSource)
  bindMapEvents()
  bindResize()
  window.addEventListener('keydown', onDrawKeydown)
  enableTerrain()
  fetchFeatures()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onDrawKeydown)
  stopEarthIntro(true)
  resizeObserver?.disconnect()
  removeTileListener?.()
  handler?.destroy()
  viewer?.destroy()
})
</script>

<template>
  <section class="gis-page">
    <div class="gis-toolbar">
      <el-button
        :disabled="!canWrite || mapBusy"
        :loading="aiDelineating && aiProvider === 'roboflow'"
        :type="aiDelineateMode && aiProvider === 'roboflow' ? 'success' : 'primary'"
        @click="handleAiDelineateClick('roboflow')"
      >
        AI 圈地
      </el-button>
      <el-button
        :disabled="!canWrite || mapBusy"
        :loading="aiDelineating && aiProvider === 'sam2.1'"
        :type="aiDelineateMode && aiProvider === 'sam2.1' ? 'success' : 'default'"
        @click="handleAiDelineateClick('sam2.1')"
      >
        SAM 2.1 圈地
      </el-button>
      <el-button :disabled="!canSaveDraft" :loading="saving" type="primary" @click="savePolygon">
        {{ pendingAiPolygons.length ? `提交地块（${pendingAiPolygons.length}）` : '保存圈地' }}
      </el-button>
      <el-button :disabled="aiDelineateMode || aiDraftLocked || !drawingPoints.length" @click="undoLastVertex">撤销</el-button>
      <el-button :disabled="aiDelineateMode || (!drawingPoints.length && !pendingAiPolygons.length)" @click="clearDraft">清空草稿</el-button>
      <el-switch v-model="showUavLayer" active-text="无人机影像" />
      <el-button @click="flyToUav">定位影像</el-button>
      <span class="tool-hint">{{ toolHint }}</span>
    </div>
    <div class="gis-workbench">
      <div class="map-stage">
        <div ref="mapEl" class="cesium-map" />
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
      <aside class="feature-panel">
        <div class="panel-head">
          <strong>服务器标注</strong>
          <el-button :loading="loading" text type="primary" @click="fetchFeatures">刷新</el-button>
        </div>
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
        <el-empty v-if="!features.length" description="暂无地块，单击地图开始圈地" />
        <div v-else class="feature-list">
          <div v-for="feature in features" :key="feature.id" class="feature-item">
            <button type="button" @click="flyToFeature(feature)">
              <strong>{{ feature.name }}</strong>
              <span>{{ featureTypeLabel(feature) }}</span>
            </button>
            <el-button v-if="canWrite" text type="danger" @click="removeFeature(feature)">删除</el-button>
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
  height: calc(100vh - 132px);
  min-height: 620px;
}

.gis-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #dfe7f1;
  border-radius: 8px;
}

.tool-hint {
  margin-left: auto;
  color: #68758a;
  font-size: 13px;
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
  cursor: crosshair;
  background: #000;
  border: 1px solid #dfe7f1;
  border-radius: 8px;
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
  border-radius: 8px;
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

@media (max-width: 1024px) {
  .gis-page {
    height: auto;
  }

  .gis-workbench {
    grid-template-columns: 1fr;
  }

  .gis-toolbar {
    flex-wrap: wrap;
  }

  .tool-hint {
    margin-left: 0;
    width: 100%;
  }

  .map-stage,
  .cesium-map {
    height: 520px;
  }
}
</style>
