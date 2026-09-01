<script setup>
import 'cesium/Build/Cesium/Widgets/widgets.css'

import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
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
import { createGisFeature, deleteGisFeature, listGisFeatures } from '@/api/gis'
import { useAuthStore } from '@/stores/auth'

Ion.defaultAccessToken = ''

const TIANDITU_TOKEN = '38ca5876c8ba7b71eb08803d408b6184'

const authStore = useAuthStore()
const mapEl = ref(null)
const features = ref([])
const activeTool = ref('point')
const drawingPoints = ref([])
const cursorCoordinate = ref(null)
const saving = ref(false)
const loading = ref(false)
const form = reactive({ name: '' })
const showUavLayer = ref(true)

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

let viewer
let handler
let draftSource
let savedSource
let suppressClickUntil = 0
let resizeObserver
let removeTileListener
let uavLayer
let flightSeq = 0

const POINT_MARKER = createPointMarkerImage()

const canWrite = computed(() => authStore.hasPermission('gis:write'))
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
const drawingPolygon = computed(() => activeTool.value === 'polygon')
const canSaveDraft = computed(() => {
  if (!canWrite.value || saving.value || !drawingPolygon.value) return false
  return drawingPoints.value.length >= 3 && !polygonInvalid.value
})
const toolHint = computed(() => {
  if (!canWrite.value) return '当前账号只有查看权限'
  if (activeTool.value === 'point') return '单击地图即可打点保存'
  if (polygonInvalid.value) return '多边形自相交，请撤销上一点后重画'
  if (drawingPoints.value.length >= 3) {
    return `再点起点闭合。面积 ${draftAreaText.value}，周长 ${draftLengthText.value}`
  }
  return `单击地形加点，圈地贴着山坡走。右键撤销。已选 ${drawingPoints.value.length} 个点`
})

function toLonLat(cartesian) {
  const [lon, lat] = toLonLatHeight(cartesian)
  return [lon, lat]
}

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
  if (feature.type === 'POINT') return '点位'
  const area = Number(parseFeatureProperties(feature).area)
  if (Number.isFinite(area) && area > 0) return `区域 · ${formatArea(area)}`
  return '区域'
}

function clearDraft() {
  drawingPoints.value = []
  cursorCoordinate.value = null
  draftSource?.entities.removeAll()
}

function undoLastVertex() {
  if (!drawingPoints.value.length) return
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
  if (activeTool.value === 'point') {
    points.forEach((item) => drawDraftPoint(item))
    return
  }
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

function createPointMarkerImage() {
  const ratio = 3
  const size = 32
  const canvas = document.createElement('canvas')
  canvas.width = size * ratio
  canvas.height = size * ratio
  const ctx = canvas.getContext('2d')
  ctx.scale(ratio, ratio)
  const c = size / 2

  const glow = ctx.createRadialGradient(c, c, 4, c, c, 16)
  glow.addColorStop(0, 'rgba(34, 211, 238, 0.38)')
  glow.addColorStop(0.5, 'rgba(56, 189, 248, 0.12)')
  glow.addColorStop(1, 'rgba(34, 211, 238, 0)')
  ctx.fillStyle = glow
  ctx.beginPath()
  ctx.arc(c, c, 16, 0, Math.PI * 2)
  ctx.fill()

  ctx.beginPath()
  ctx.arc(c, c, 9.6, 0, Math.PI * 2)
  ctx.fillStyle = 'rgba(6, 16, 32, 0.9)'
  ctx.fill()

  ctx.beginPath()
  ctx.arc(c, c, 10.4, 0, Math.PI * 2)
  ctx.strokeStyle = '#22d3ee'
  ctx.lineWidth = 1.5
  ctx.stroke()

  ctx.beginPath()
  ctx.arc(c, c, 6.4, 0, Math.PI * 2)
  ctx.strokeStyle = 'rgba(125, 211, 252, 0.75)'
  ctx.lineWidth = 0.9
  ctx.stroke()

  ctx.strokeStyle = 'rgba(34, 211, 238, 0.9)'
  ctx.lineWidth = 1.1
  ctx.lineCap = 'round'
  ctx.beginPath()
  ctx.moveTo(c, 3.8)
  ctx.lineTo(c, 7.6)
  ctx.moveTo(c, 24.4)
  ctx.lineTo(c, 28.2)
  ctx.moveTo(3.8, c)
  ctx.lineTo(7.6, c)
  ctx.moveTo(24.4, c)
  ctx.lineTo(28.2, c)
  ctx.stroke()

  ctx.beginPath()
  ctx.moveTo(c, c - 2.6)
  ctx.lineTo(c + 2.6, c)
  ctx.lineTo(c, c + 2.6)
  ctx.lineTo(c - 2.6, c)
  ctx.closePath()
  ctx.fillStyle = '#67e8f9'
  ctx.shadowColor = '#22d3ee'
  ctx.shadowBlur = 8
  ctx.fill()
  ctx.shadowBlur = 0
  ctx.lineWidth = 0.8
  ctx.strokeStyle = '#ffffff'
  ctx.stroke()

  return canvas
}

function pointLabel(text) {
  return {
    text,
    font: '12px Consolas, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
    fillColor: Color.fromCssColorString('#7dd3fc'),
    showBackground: true,
    backgroundColor: Color.fromCssColorString('#061020').withAlpha(0.86),
    backgroundPadding: new Cartesian2(8, 4),
    pixelOffset: new Cartesian2(0, -34),
    horizontalOrigin: HorizontalOrigin.CENTER,
    verticalOrigin: VerticalOrigin.BOTTOM,
    disableDepthTestDistance: Number.POSITIVE_INFINITY,
  }
}

function pointBillboard() {
  return {
    image: POINT_MARKER,
    width: 38,
    height: 38,
    verticalOrigin: VerticalOrigin.CENTER,
    horizontalOrigin: HorizontalOrigin.CENTER,
    sizeInMeters: false,
    disableDepthTestDistance: Number.POSITIVE_INFINITY,
  }
}

function addSavedFeature(feature) {
  const geometry = parseFeatureGeometry(feature)
  if (!geometry) return
  if (geometry.type === 'Point') {
    const [lon, lat] = geometry.coordinates
    savedSource.entities.add({
      name: feature.name,
      properties: { featureId: feature.id, name: feature.name, type: feature.type },
      position: Cartesian3.fromDegrees(lon, lat),
      billboard: { ...pointBillboard(), heightReference: HeightReference.CLAMP_TO_GROUND },
      label: { ...pointLabel(feature.name), heightReference: HeightReference.CLAMP_TO_GROUND },
    })
    return
  }
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

async function saveFeature(type, geojson) {
  if (!canWrite.value) {
    ElMessage.warning('没有编辑 GIS 标注的权限')
    return
  }
  saving.value = true
  try {
    const defaultName = type === 'POINT' ? '未命名点位' : '未命名区域'
    const properties = type === 'POLYGON' && draftPolygon.value
      ? {
          source: 'cesium',
          area: turf.area(draftPolygon.value),
          perimeterKm: turf.length(turf.polygonToLine(draftPolygon.value), { units: 'kilometers' }),
        }
      : { source: 'cesium' }
    const saved = await createGisFeature({
      name: form.name.trim() || defaultName,
      type,
      geojson: JSON.stringify(geojson),
      properties: JSON.stringify(properties),
    })
    features.value = [saved, ...features.value]
    addSavedFeature(saved)
    if (type === 'POLYGON') flyToFeature(saved)
    clearDraft()
    form.name = ''
    ElMessage.success('已保存到服务器')
  } finally {
    saving.value = false
  }
}

async function savePolygon() {
  const polygon = buildPolygonFeature(drawingPoints.value)
  if (!polygon) {
    ElMessage.warning('至少需要 3 个点才能圈地')
    return
  }
  if (turf.kinks(polygon).features.length) {
    ElMessage.warning('多边形自相交，请调整后再保存')
    return
  }
  await saveFeature('POLYGON', polygon)
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
const HOME_DESTINATION = Cartesian3.fromDegrees(104.1954, 35.8617, 9800000)
const NADIR = {
  heading: 0,
  pitch: CesiumMath.toRadians(-90),
  roll: 0,
}

function beginFlight() {
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

function flyHome() {
  if (!viewer) return
  beginFlight()
  viewer.camera.cancelFlight()
  const controller = viewer.scene.screenSpaceCameraController
  const prevCollision = controller.enableCollisionDetection
  controller.enableCollisionDetection = false
  applyHomeView()
  controller.enableCollisionDetection = prevCollision
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
  if (geometry.type === 'Point') {
    const [lon, lat] = geometry.coordinates
    flyCameraTo(Cartesian3.fromDegrees(lon, lat, 4500))
    return
  }
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
  if (!canWrite.value || saving.value || Date.now() < suppressClickUntil) return
  const position = pickOnGlobe(event.position)
  if (!position) return
  if (activeTool.value === 'point') {
    saveFeature('POINT', turf.point(toLonLat(position)))
    return
  }
  const coordinate = toLonLatHeight(position)
  addPolygonVertex(coordinate, event.position)
}

function handleMapMove(event) {
  if (!canWrite.value || !drawingPolygon.value || !drawingPoints.value.length) return
  const position = pickOnGlobe(event.endPosition)
  cursorCoordinate.value = position ? toLonLatHeight(position) : null
}

function handleDoubleClick(event) {
  if (!canWrite.value || saving.value || !drawingPolygon.value) return
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
  if (!canWrite.value || saving.value) return
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
  if (event.key === 'Enter' && drawingPolygon.value) savePolygon()
}

function bindMapEvents() {
  viewer.screenSpaceEventHandler.removeInputAction(ScreenSpaceEventType.LEFT_DOUBLE_CLICK)
  handler = new ScreenSpaceEventHandler(viewer.scene.canvas)
  handler.setInputAction(handleMapClick, ScreenSpaceEventType.LEFT_CLICK)
  handler.setInputAction(handleMapMove, ScreenSpaceEventType.MOUSE_MOVE)
  handler.setInputAction(handleDoubleClick, ScreenSpaceEventType.LEFT_DOUBLE_CLICK)
  handler.setInputAction(() => {
    if (drawingPolygon.value) undoLastVertex()
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
    tilingScheme: new WebMercatorTilingScheme(),
    enablePickFeatures: false,
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
  viewer.imageryLayers.addImageryProvider(tiandituProvider('cia_w'))
  addUavLayer()
  viewer.scene.fog.enabled = false
  viewer.scene.globe.enableLighting = false
  viewer.scene.globe.dynamicAtmosphereLighting = false
  viewer.scene.globe.showGroundAtmosphere = false
  viewer.scene.globe.depthTestAgainstTerrain = true
  viewer.scene.globe.maximumScreenSpaceError = 2
  viewer.scene.globe.tileCacheSize = 2000
  viewer.scene.globe.preloadSiblings = false
  viewer.scene.globe.preloadAncestors = true
  viewer.scene.globe.loadingDescendantLimit = 8
  viewer.scene.globe.baseColor = Color.fromCssColorString('#0b1d33')
  viewer.scene.highDynamicRange = false
  viewer.scene.skyAtmosphere.show = false
  viewer.scene.sun.show = false
  viewer.scene.moon.show = false
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
  applyHomeView()
}

async function enableTerrain() {
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
}

function bindResize() {
  resizeObserver = new ResizeObserver(() => viewer?.resize())
  if (mapEl.value) resizeObserver.observe(mapEl.value)
}

watch(activeTool, () => {
  clearDraft()
})

watch(showUavLayer, (visible) => {
  if (uavLayer) uavLayer.show = visible
})

onMounted(async () => {
  await nextTick()
  createViewer()
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
  resizeObserver?.disconnect()
  removeTileListener?.()
  handler?.destroy()
  viewer?.destroy()
})
</script>

<template>
  <section class="gis-page">
    <div class="gis-toolbar">
      <el-segmented
        v-model="activeTool"
        :disabled="!canWrite"
        :options="[
          { label: '打点', value: 'point' },
          { label: '圈地', value: 'polygon' },
        ]"
      />
      <el-input
        v-model="form.name"
        class="name-input"
        maxlength="128"
        placeholder="标注名称，可留空"
        :disabled="!canWrite"
        clearable
      />
      <el-button v-if="drawingPolygon" :disabled="!canSaveDraft" :loading="saving" type="primary" @click="savePolygon">
        保存圈地
      </el-button>
      <el-button v-if="drawingPolygon" :disabled="!drawingPoints.length" @click="undoLastVertex">撤销</el-button>
      <el-button v-if="drawingPolygon" :disabled="!drawingPoints.length" @click="clearDraft">清空草稿</el-button>
      <el-switch v-model="showUavLayer" active-text="无人机影像" />
      <el-button @click="flyToUav">定位影像</el-button>
      <span class="tool-hint">{{ toolHint }}</span>
    </div>
    <div class="gis-workbench">
      <div ref="mapEl" class="cesium-map" />
      <aside class="feature-panel">
        <div class="panel-head">
          <strong>服务器标注</strong>
          <el-button :loading="loading" text type="primary" @click="fetchFeatures">刷新</el-button>
        </div>
        <el-empty v-if="!features.length" description="暂无标注，单击地图打点或切换圈地绘制" />
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

.name-input {
  width: 240px;
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

.cesium-map {
  position: relative;
  min-height: 0;
  overflow: hidden;
  cursor: crosshair;
  background: #071018;
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

  .cesium-map {
    height: 520px;
  }
}
</style>
