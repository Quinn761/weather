<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { getOfficialAlerts, getTropicalCyclones } from '@/api/officialAlerts'

// 省级边界经离线简化，保留交互精度的同时降低缩放时的矢量重绘开销。
const CHINA_BOUNDARY_URL = `${import.meta.env.BASE_URL}china-100000-optimized.json`
const OUTSIDE_MASK_URL = `${import.meta.env.BASE_URL}china-outside-mask.json`
const ADMIN_BOUNDARY_URL = 'https://geo.datav.aliyun.com/areas_v3/bound'
const TIANDITU_TOKEN = import.meta.env.VITE_TIANDITU_TOKEN || '38ca5876c8ba7b71eb08803d408b6184'
const mapEl = ref(null)
const loading = ref(true)
const loadError = ref('')
const mapMode = ref('alerts')
const selectedRegion = ref('全国')
const alertOverview = ref(null)
const tropicalOverview = ref(null)
const selectedStormId = ref('')
const warningScrollOffset = ref(0)
const drilldownRegion = ref(null)
const radarEnabled = ref(true)
const radarFrames = ref([])
const radarFrameIndex = ref(0)
const radarHost = ref('')
const radarPlaying = ref(false)
const warningByRegion = new Map()
let warningScrollTimer
let radarRefreshTimer
let radarPlayTimer
let map
let boundaryLayer
let boundaryDepthLayer
let boundaryGlowLayer
let outsideMaskLayer
let drilldownLayer
let drilldownDepthLayer
let drilldownGlowLayer
let drilldownMaskLayer
let nationalBoundaryData
let tropicalLayer
let radarLayer
let suppressTropicalFocus = false

const radarTimeLabel = computed(() => {
  const frame = radarFrames.value[radarFrameIndex.value]
  if (!frame?.time) return '暂无雷达帧'
  return new Date(frame.time * 1000).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
})

function tileUrl(layer) {
  return `https://t{s}.tianditu.gov.cn/${layer}/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=${layer.slice(0, -2)}&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${TIANDITU_TOKEN}`
}

function boundaryStyle(feature) {
  const name = feature?.properties?.name
  const active = name === selectedRegion.value
  const warnings = warningByRegion.get(name) || []
  const warning = mostSevereWarning(warnings)
  const palette = warningPalette(warning?.level)
  return {
    color: active ? '#ffffff' : palette.border,
    weight: active ? 3.6 : warning ? 3.4 : 2.05,
    opacity: 1,
    fillColor: active && !warning ? '#248ec2' : palette.fill,
    fillOpacity: active ? 0.58 : warning ? 0.74 : 0.36,
  }
}

function warningPalette(level) {
  const palettes = {
    Red: { border: '#ffd0d0', fill: '#ee3d4b' },
    '红色': { border: '#ffd0d0', fill: '#ee3d4b' },
    Orange: { border: '#ffe0a3', fill: '#ff8a24' },
    '橙色': { border: '#ffe0a3', fill: '#ff8a24' },
    Yellow: { border: '#fff5a1', fill: '#e5be17' },
    '黄色': { border: '#fff5a1', fill: '#e5be17' },
    Blue: { border: '#d7edff', fill: '#1677ff' },
    '蓝色': { border: '#d7edff', fill: '#1677ff' },
  }
  return palettes[level] || { border: '#4ab5cf', fill: '#0d536f' }
}

function warningLevelTone(level) {
  if (!level) return 'tone-default'
  if (/红|Red/i.test(level)) return 'tone-red'
  if (/橙|Orange/i.test(level)) return 'tone-orange'
  if (/黄|Yellow/i.test(level)) return 'tone-yellow'
  if (/蓝|Blue/i.test(level)) return 'tone-blue'
  return 'tone-default'
}

function warningSeverity(level) {
  return { Red: 4, '红色': 4, Orange: 3, '橙色': 3, Yellow: 2, '黄色': 2, Blue: 1, '蓝色': 1 }[level] || 0
}

function mostSevereWarning(warnings) {
  return warnings.reduce((mostSevere, warning) => (
    !mostSevere || warningSeverity(warning.level) > warningSeverity(mostSevere.level) ? warning : mostSevere
  ), null)
}

const selectedWarnings = computed(() => {
  // Depend on the response ref so the panel updates after the asynchronous provider request.
  void alertOverview.value
  if (selectedRegion.value === '全国') {
    return (alertOverview.value?.regions || []).flatMap((region) => region.warnings || [])
  }
  return warningByRegion.get(selectedRegion.value) || []
})
const displayWarnings = computed(() => {
  if (selectedRegion.value === '全国') {
    return (alertOverview.value?.regions || []).flatMap((region) => (
      (region.warnings || []).map((warning) => ({ ...warning, regionName: region.regionName }))
    ))
  }
  return selectedWarnings.value.map((warning) => ({ ...warning, regionName: selectedRegion.value }))
})
const visibleWarnings = computed(() => {
  const warnings = displayWarnings.value
  if (warnings.length <= 5) return warnings
  return Array.from({ length: 5 }, (_, index) => warnings[(warningScrollOffset.value + index) % warnings.length])
})
const warningCount = computed(() => (alertOverview.value?.regions || []).reduce((total, region) => total + (region.warnings?.length || 0), 0))
const tropicalStorms = computed(() => tropicalOverview.value?.storms || [])
const selectedTropicalStorm = computed(() => tropicalStorms.value.find((storm) => storm.id === selectedStormId.value) || tropicalStorms.value[0] || null)

watch(selectedRegion, () => { warningScrollOffset.value = 0 })
function focusTropicalStorm() {
  const storm = selectedTropicalStorm.value
  if (!storm || !map) return
  const lat = Number(storm.lat)
  const lon = Number(storm.lon)
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return
  const forecast = toLatLngs(storm.forecast)
  const points = [[lat, lon], ...toLatLngs(storm.track).slice(-6), ...forecast]
  if (points.length > 1) {
    map.fitBounds(L.latLngBounds(points), { padding: [80, 80], maxZoom: 6.5, animate: true })
  } else {
    map.panTo([lat, lon], { animate: true })
  }
}

watch(selectedStormId, () => {
  if (mapMode.value !== 'tropical') return
  renderTropicalCyclones()
  if (!suppressTropicalFocus) focusTropicalStorm()
})

function setAlertMapOverlaysVisible(visible) {
  if (!map) return
  if (visible) {
    outsideMaskLayer?.addTo(map)
    boundaryDepthLayer?.addTo(map)
    boundaryGlowLayer?.addTo(map)
    boundaryLayer?.addTo(map)
    map.setMaxZoom(9)
  } else {
    removeDrilldownLayers()
    drilldownRegion.value = null
    outsideMaskLayer?.remove()
    boundaryDepthLayer?.remove()
    boundaryGlowLayer?.remove()
    boundaryLayer?.remove()
    map.setMaxZoom(12)
  }
}

async function setMapMode(mode) {
  if (mapMode.value === mode) return
  mapMode.value = mode
  if (mode === 'tropical') {
    setAlertMapOverlaysVisible(false)
    suppressTropicalFocus = true
    await Promise.all([loadTropicalCyclones(), ensureRadarLayer()])
    suppressTropicalFocus = false
    if (selectedTropicalStorm.value) focusTropicalStorm()
    else map?.setView([20.5, 132], 4.2, { animate: true })
    requestAnimationFrame(() => map?.invalidateSize())
    return
  }
  clearTropicalCyclones()
  clearRadarLayer()
  stopRadarPlayback()
  window.clearInterval(radarRefreshTimer)
  radarRefreshTimer = undefined
  setAlertMapOverlaysVisible(true)
  boundaryLayer?.setStyle(boundaryStyle)
  map?.setView([35.6, 104.2], 4, { animate: true })
  requestAnimationFrame(() => map?.invalidateSize())
}

function clearRadarLayer() {
  radarLayer?.remove()
  radarLayer = null
}

function stopRadarPlayback() {
  radarPlaying.value = false
  window.clearInterval(radarPlayTimer)
  radarPlayTimer = undefined
}

function radarTileUrl(framePath) {
  // color=2 Universal Blue；1_1 = 平滑 + 显示降雪色
  return `${radarHost.value}${framePath}/256/{z}/{x}/{y}/2/1_1.png`
}

function applyRadarFrame() {
  if (!map || mapMode.value !== 'tropical' || !radarEnabled.value) {
    clearRadarLayer()
    return
  }
  const frame = radarFrames.value[radarFrameIndex.value]
  if (!frame?.path || !radarHost.value) {
    clearRadarLayer()
    return
  }
  const next = L.tileLayer(radarTileUrl(frame.path), {
    pane: 'radar-pane',
    opacity: 0.62,
    zIndex: 350,
    maxZoom: 12,
    maxNativeZoom: 7,
    tileSize: 256,
    updateWhenIdle: true,
    updateWhenZooming: false,
    className: 'rainviewer-radar',
  })
  next.addTo(map)
  const previous = radarLayer
  radarLayer = next
  // 短暂双层交叉，减少换帧闪烁。
  window.setTimeout(() => previous?.remove(), 180)
}

async function loadRadarFrames() {
  try {
    const response = await fetch('https://api.rainviewer.com/public/weather-maps.json')
    if (!response.ok) throw new Error('radar meta failed')
    const data = await response.json()
    radarHost.value = data.host || 'https://tilecache.rainviewer.com'
    const frames = [...(data.radar?.past || []), ...(data.radar?.nowcast || [])]
    radarFrames.value = frames
    radarFrameIndex.value = Math.max(0, frames.length - 1)
    applyRadarFrame()
  } catch {
    radarFrames.value = []
    clearRadarLayer()
  }
}

async function ensureRadarLayer() {
  if (!radarEnabled.value || mapMode.value !== 'tropical') {
    clearRadarLayer()
    return
  }
  if (!map.getPane('radar-pane')) {
    const pane = map.createPane('radar-pane')
    pane.style.zIndex = '350'
    pane.style.pointerEvents = 'none'
  }
  await loadRadarFrames()
  window.clearInterval(radarRefreshTimer)
  radarRefreshTimer = window.setInterval(() => {
    if (mapMode.value === 'tropical' && radarEnabled.value) void loadRadarFrames()
  }, 5 * 60 * 1000)
}

function toggleRadarLayer() {
  radarEnabled.value = !radarEnabled.value
  if (radarEnabled.value) void ensureRadarLayer()
  else {
    stopRadarPlayback()
    clearRadarLayer()
  }
}

function toggleRadarPlayback() {
  if (!radarEnabled.value || radarFrames.value.length < 2) return
  if (radarPlaying.value) {
    stopRadarPlayback()
    return
  }
  radarPlaying.value = true
  radarPlayTimer = window.setInterval(() => {
    if (!radarFrames.value.length) return
    radarFrameIndex.value = (radarFrameIndex.value + 1) % radarFrames.value.length
    applyRadarFrame()
  }, 700)
}

function stepRadarFrame(delta) {
  if (!radarFrames.value.length) return
  stopRadarPlayback()
  const total = radarFrames.value.length
  radarFrameIndex.value = (radarFrameIndex.value + delta + total) % total
  applyRadarFrame()
}

function clearTropicalCyclones() {
  tropicalLayer?.remove()
  tropicalLayer = null
}

function toLatLngs(points = []) {
  return points
    .map((point) => [Number(point.lat), Number(point.lon)])
    .filter(([lat, lon]) => Number.isFinite(lat) && Number.isFinite(lon))
}

function destinationPoint([lat, lon], distanceKm, bearingDeg) {
  const radius = 6371
  const δ = distanceKm / radius
  const θ = (bearingDeg * Math.PI) / 180
  const φ1 = (lat * Math.PI) / 180
  const λ1 = (lon * Math.PI) / 180
  const sinφ1 = Math.sin(φ1)
  const cosφ1 = Math.cos(φ1)
  const sinδ = Math.sin(δ)
  const cosδ = Math.cos(δ)
  const sinφ2 = sinφ1 * cosδ + cosφ1 * sinδ * Math.cos(θ)
  const φ2 = Math.asin(sinφ2)
  const λ2 = λ1 + Math.atan2(Math.sin(θ) * sinδ * cosφ1, cosδ - sinφ1 * sinφ2)
  return [(φ2 * 180) / Math.PI, ((((λ2 * 180) / Math.PI) + 540) % 360) - 180]
}

function bearingBetween([lat1, lon1], [lat2, lon2]) {
  const φ1 = (lat1 * Math.PI) / 180
  const φ2 = (lat2 * Math.PI) / 180
  const Δλ = ((lon2 - lon1) * Math.PI) / 180
  const y = Math.sin(Δλ) * Math.cos(φ2)
  const x = Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ)
  return ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360
}

function buildForecastCone(path) {
  if (path.length < 2) return null
  const left = []
  const right = []
  for (let index = 0; index < path.length; index += 1) {
    const point = path[index]
    const next = path[Math.min(index + 1, path.length - 1)]
    const prev = path[Math.max(index - 1, 0)]
    const bearing = index < path.length - 1 ? bearingBetween(point, next) : bearingBetween(prev, point)
    // 预报越远不确定性越大：起点约 50km，逐步扩到约 220km。
    const radiusKm = 50 + index * 28
    left.push(destinationPoint(point, radiusKm, bearing - 90))
    right.push(destinationPoint(point, radiusKm, bearing + 90))
  }
  return [...left, ...right.reverse()]
}

function averageRadiusKm(radius) {
  if (!radius) return null
  const values = [radius.neRadius, radius.seRadius, radius.swRadius, radius.nwRadius]
    .map(Number)
    .filter((value) => Number.isFinite(value) && value > 0)
  if (!values.length) return null
  return values.reduce((sum, value) => sum + value, 0) / values.length
}

function renderTropicalCyclones() {
  clearTropicalCyclones()
  if (!map || mapMode.value !== 'tropical') return
  tropicalLayer = L.layerGroup().addTo(map)
  const selectedId = selectedTropicalStorm.value?.id

  for (const storm of tropicalStorms.value) {
    const active = storm.id === selectedId
    const track = toLatLngs(storm.track)
    const forecast = toLatLngs(storm.forecast)
    const now = [Number(storm.lat), Number(storm.lon)]
    const hasNow = Number.isFinite(now[0]) && Number.isFinite(now[1])
    const forecastPath = hasNow && forecast.length ? [now, ...forecast] : forecast

    if (active && forecastPath.length > 1) {
      const cone = buildForecastCone(forecastPath)
      if (cone) {
        L.polygon(cone, {
          color: '#ffe08a',
          weight: 1.2,
          opacity: 0.55,
          fillColor: '#ffb347',
          fillOpacity: 0.16,
          interactive: false,
        }).addTo(tropicalLayer)
      }
    }

    if (track.length > 1) {
      L.polyline(track, {
        color: active ? '#ffd27a' : '#c9884a',
        weight: active ? 3.2 : 2,
        opacity: active ? 0.95 : 0.55,
        lineCap: 'round',
        interactive: false,
      }).addTo(tropicalLayer)
    }

    if (forecastPath.length > 1) {
      L.polyline(forecastPath, {
        color: active ? '#fff4c8' : '#ffc36d',
        weight: active ? 2.8 : 1.8,
        opacity: active ? 0.95 : 0.5,
        dashArray: '7 8',
        lineCap: 'round',
        interactive: false,
      }).addTo(tropicalLayer)

      forecast.forEach((point, index) => {
        L.circleMarker(point, {
          radius: active ? 4.5 : 3,
          color: '#fff7df',
          weight: 1.4,
          fillColor: active ? '#ff9a3d' : '#d88945',
          fillOpacity: active ? 0.95 : 0.55,
          interactive: false,
        })
          .bindTooltip(`预报 T+${index + 1}`, { direction: 'top', className: 'storm-label' })
          .addTo(tropicalLayer)
      })
    }

    if (active && hasNow) {
      const rings = [
        { km: averageRadiusKm(storm.windRadius30), color: '#5ad0ff55', fill: '#5ad0ff18' },
        { km: averageRadiusKm(storm.windRadius50), color: '#ffd45a55', fill: '#ffd45a16' },
        { km: averageRadiusKm(storm.windRadius64), color: '#ff6b6b55', fill: '#ff6b6b14' },
      ]
      rings.forEach((ring) => {
        if (!ring.km) return
        L.circle(now, {
          radius: ring.km * 1000,
          color: ring.color,
          weight: 1.2,
          fillColor: ring.fill,
          fillOpacity: 1,
          interactive: false,
        }).addTo(tropicalLayer)
      })
    }

    if (!hasNow) continue
    L.circleMarker(now, {
      radius: active ? 12 : 8,
      color: active ? '#fff7df' : '#ffe4b0',
      weight: active ? 2.6 : 1.6,
      fillColor: active ? '#ff4f24' : '#ff7a3d',
      fillOpacity: 0.96,
    })
      .bindTooltip(`${storm.name || '热带气旋'} · ${storm.type || '—'}`, {
        permanent: true,
        direction: 'top',
        offset: [0, -10],
        className: active ? 'storm-label storm-label-active' : 'storm-label',
      })
      .on('click', () => { selectedStormId.value = storm.id })
      .addTo(tropicalLayer)
  }
}

async function loadTropicalCyclones() {
  try {
    tropicalOverview.value = await getTropicalCyclones()
    if (!tropicalStorms.value.some((storm) => storm.id === selectedStormId.value)) selectedStormId.value = tropicalStorms.value[0]?.id || ''
    renderTropicalCyclones()
  } catch {
    tropicalOverview.value = null
    clearTropicalCyclones()
  }
}

function attachBoundaryEvents(feature, layer) {
  const name = feature.properties?.name || '未知区域'
  layer.bindTooltip(name, { permanent: true, direction: 'center', className: 'admin-label', interactive: false })
  layer.on({
    mouseover() {
      if (name === selectedRegion.value) {
        layer.setStyle({
          color: '#ffffff',
          weight: 6,
          opacity: 1,
          fillColor: boundaryStyle(feature).fillColor,
          fillOpacity: Math.min(boundaryStyle(feature).fillOpacity + 0.14, 0.97),
        })
      } else {
        const style = boundaryStyle(feature)
        layer.setStyle({ ...style, weight: Math.max(style.weight + 2.4, 4.4), color: '#f0fdff', opacity: 1, fillOpacity: Math.min(style.fillOpacity + 0.18, 0.94) })
      }
      layer.bringToFront()
    },
    mouseout() {
      boundaryLayer?.resetStyle(layer)
      // Keep selected province on top after hover ends.
      boundaryLayer?.eachLayer((item) => {
        if (item.feature?.properties?.name === selectedRegion.value) item.bringToFront()
      })
    },
    click() {
      selectedRegion.value = name
      boundaryLayer?.setStyle(boundaryStyle)
      layer.setStyle(boundaryStyle(feature))
      layer.bringToFront()
      void drillDown(feature, layer)
    },
  })
}

function drilldownStyle() {
  const warning = mostSevereWarning(warningByRegion.get(drilldownRegion.value?.name) || [])
  const palette = warningPalette(warning?.level)
  return {
    color: warning ? palette.border : '#5ae6ff',
    weight: 1.4,
    opacity: 0.95,
    fillColor: warning ? palette.fill : '#0b6f91',
    fillOpacity: warning ? 0.74 : 0.46,
  }
}

function hideNationalLayers() {
  boundaryDepthLayer?.remove()
  boundaryGlowLayer?.remove()
  boundaryLayer?.remove()
}

function showNationalLayers() {
  if (mapMode.value !== 'alerts') return
  boundaryDepthLayer?.addTo(map)
  boundaryGlowLayer?.addTo(map)
  boundaryLayer?.addTo(map)
}

function removeDrilldownLayers() {
  drilldownLayer?.remove()
  drilldownDepthLayer?.remove()
  drilldownGlowLayer?.remove()
  drilldownMaskLayer?.remove()
  drilldownLayer = null
  drilldownDepthLayer = null
  drilldownGlowLayer = null
  drilldownMaskLayer = null
}

function createDrilldownLayers(data) {
  drilldownDepthLayer = L.geoJSON(data, {
    pane: 'drilldown-depth',
    interactive: false,
    renderer: L.canvas({ pane: 'drilldown-depth', padding: 0.25 }),
    style: { color: '#063851', weight: 4.8, opacity: 0.9, fillColor: '#06334e', fillOpacity: 0.48 },
  }).addTo(map)
  drilldownGlowLayer = L.geoJSON(data, {
    pane: 'drilldown-glow',
    interactive: false,
    renderer: L.canvas({ pane: 'drilldown-glow', padding: 0.2 }),
    style: { color: '#18dfff', weight: 6.5, opacity: 0.3, fillOpacity: 0 },
  }).addTo(map)
  drilldownLayer = L.geoJSON(data, {
    pane: 'drilldown-main',
    renderer: L.canvas({ pane: 'drilldown-main', padding: 0.2 }),
    style: drilldownStyle,
    onEachFeature(city, cityLayer) {
      cityLayer.bindTooltip(city.properties?.name || '行政区', { permanent: true, direction: 'center', className: 'admin-label', interactive: false })
    },
  }).addTo(map)
}

async function drillDown(feature, provinceLayer) {
  const { name, adcode } = feature.properties || {}
  if (!name || !adcode || !map) return
  drilldownRegion.value = { name, adcode }
  hideNationalLayers()
  removeDrilldownLayers()
  const maskedFeatures = (nationalBoundaryData?.features || []).filter((item) => item.properties?.name !== name)
  drilldownMaskLayer = L.geoJSON({ type: 'FeatureCollection', features: maskedFeatures }, {
    pane: 'drilldown-mask',
    interactive: false,
    renderer: L.canvas({ pane: 'drilldown-mask', padding: 0.2 }),
    style: { stroke: false, fillColor: '#020b12', fillOpacity: 0.78 },
  }).addTo(map)
  createDrilldownLayers(feature)
  if (provinceLayer.getBounds().isValid()) map.fitBounds(provinceLayer.getBounds(), { padding: [36, 36], maxZoom: 7.5, animate: true })
  try {
    const response = await fetch(`${ADMIN_BOUNDARY_URL}/${adcode}_full.json`)
    if (!response.ok || drilldownRegion.value?.adcode !== adcode) return
    const data = await response.json()
    if (drilldownRegion.value?.adcode !== adcode) return
    drilldownLayer?.remove()
    drilldownDepthLayer?.remove()
    drilldownGlowLayer?.remove()
    createDrilldownLayers(data)
  } catch {
    // The selected province remains visible when the optional city boundary request fails.
  }
}

function returnToNational() {
  drilldownRegion.value = null
  removeDrilldownLayers()
  selectedRegion.value = '全国'
  showNationalLayers()
  boundaryLayer?.setStyle(boundaryStyle)
  map?.setView([35.6, 104.2], 4, { animate: true })
}

async function loadOfficialAlerts() {
  try {
    const overview = await getOfficialAlerts()
    alertOverview.value = overview
    warningByRegion.clear()
    for (const region of overview?.regions || []) {
      if (region.warnings?.length) warningByRegion.set(region.regionName, region.warnings)
    }
    boundaryLayer?.setStyle(boundaryStyle)
  } catch {
    // The local map remains usable when the provider is temporarily unavailable.
  }
}

async function initializeMap() {
  map = L.map(mapEl.value, {
    attributionControl: false,
    zoomControl: false,
    preferCanvas: true,
    renderer: L.canvas({ padding: 0.2 }),
    minZoom: 3,
    maxZoom: 9,
    zoomSnap: 0.25,
    zoomAnimation: true,
    fadeAnimation: true,
    markerZoomAnimation: true,
  }).setView([35.6, 104.2], 4)

  L.control.zoom({ position: 'bottomright' }).addTo(map)
  const options = {
    subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
    maxZoom: 18,
    crossOrigin: true,
    updateWhenZooming: false,
    updateWhenIdle: true,
    keepBuffer: 1,
  }
  L.tileLayer(tileUrl('img_w'), options).addTo(map)
  L.tileLayer(tileUrl('cia_w'), { ...options, opacity: 0.82 }).addTo(map)

  const [boundaryResponse, maskResponse] = await Promise.all([fetch(CHINA_BOUNDARY_URL), fetch(OUTSIDE_MASK_URL)])
  if (!boundaryResponse.ok || !maskResponse.ok) throw new Error('边界请求失败')
  const [boundaryData, outsideMaskData] = await Promise.all([boundaryResponse.json(), maskResponse.json()])
  nationalBoundaryData = boundaryData
  const maskPane = map.createPane('outside-mask')
  maskPane.style.zIndex = '350'
  maskPane.style.pointerEvents = 'none'
  outsideMaskLayer = L.geoJSON(outsideMaskData, {
    pane: 'outside-mask',
    interactive: false,
    renderer: L.canvas({ pane: 'outside-mask', padding: 0.2 }),
    style: { stroke: false, fillColor: '#01080f', fillOpacity: 0.72 },
  }).addTo(map)
  const depthPane = map.createPane('boundary-depth')
  depthPane.style.zIndex = '401'
  depthPane.style.transform = 'translate3d(0, 9px, 0)'
  depthPane.style.pointerEvents = 'none'
  const glowPane = map.createPane('boundary-glow')
  glowPane.style.zIndex = '402'
  glowPane.style.pointerEvents = 'none'
  const mainPane = map.createPane('boundary-main')
  mainPane.style.zIndex = '403'
  const drilldownMaskPane = map.createPane('drilldown-mask')
  drilldownMaskPane.style.zIndex = '400'
  drilldownMaskPane.style.pointerEvents = 'none'
  const drilldownDepthPane = map.createPane('drilldown-depth')
  drilldownDepthPane.style.zIndex = '401'
  drilldownDepthPane.style.transform = 'translate3d(0, 8px, 0)'
  drilldownDepthPane.style.pointerEvents = 'none'
  const drilldownGlowPane = map.createPane('drilldown-glow')
  drilldownGlowPane.style.zIndex = '402'
  drilldownGlowPane.style.pointerEvents = 'none'
  const drilldownMainPane = map.createPane('drilldown-main')
  drilldownMainPane.style.zIndex = '403'
  drilldownMainPane.style.pointerEvents = 'none'
  boundaryDepthLayer = L.geoJSON(boundaryData, {
    pane: 'boundary-depth',
    interactive: false,
    renderer: L.canvas({ pane: 'boundary-depth', padding: 0.2 }),
    style: { color: '#063851', weight: 5.4, opacity: 0.88, fillColor: '#06334e', fillOpacity: 0.38 },
  }).addTo(map)
  boundaryGlowLayer = L.geoJSON(boundaryData, {
    pane: 'boundary-glow',
    interactive: false,
    renderer: L.canvas({ pane: 'boundary-glow', padding: 0.2 }),
    style: { color: '#18dfff', weight: 7.5, opacity: 0.34, fillOpacity: 0 },
  }).addTo(map)
  boundaryLayer = L.geoJSON(boundaryData, {
    pane: 'boundary-main',
    renderer: L.canvas({ pane: 'boundary-main', padding: 0.2 }),
    style: boundaryStyle,
    onEachFeature: attachBoundaryEvents,
  }).addTo(map)
}

onMounted(async () => {
  try {
    await initializeMap()
    void loadOfficialAlerts()
    requestAnimationFrame(() => map?.invalidateSize())
  } catch {
    loadError.value = '行政区边界加载失败，请刷新页面后重试。'
  } finally {
    loading.value = false
    requestAnimationFrame(() => map?.invalidateSize())
  }
  warningScrollTimer = window.setInterval(() => {
    if (displayWarnings.value.length > 4) warningScrollOffset.value = (warningScrollOffset.value + 1) % displayWarnings.value.length
  }, 3000)
})

onBeforeUnmount(() => {
  window.clearInterval(warningScrollTimer)
  window.clearInterval(radarRefreshTimer)
  stopRadarPlayback()
  map?.remove()
})
</script>

<template>
  <div class="official-alerts-page">
    <section v-loading="loading" class="china-command-map" :class="{ 'is-tropical': mapMode === 'tropical' }">
      <div ref="mapEl" class="china-map" :aria-label="mapMode === 'tropical' ? '热带气旋天地图' : '全国行政区预警地图'" />
      <div v-if="mapMode === 'alerts'" class="map-vignette" aria-hidden="true" />
      <div v-if="mapMode === 'alerts'" class="map-grid" aria-hidden="true" />
      <div v-if="mapMode === 'alerts'" class="map-scanline" aria-hidden="true" />

      <nav class="map-mode" aria-label="地图模式">
        <button type="button" :class="{ active: mapMode === 'alerts' }" @click="setMapMode('alerts')">预警态势</button>
        <button type="button" :class="{ active: mapMode === 'tropical' }" @click="setMapMode('tropical')">热带气旋</button>
      </nav>
      <aside v-if="mapMode === 'alerts'" class="map-console">
        <p>区域定位</p><strong>{{ selectedRegion }}</strong><span>省级行政区</span>
        <hr>
        <p>预警状态 · {{ selectedRegion }}</p>
        <div v-if="visibleWarnings.length" class="warning-list">
          <article
            v-for="warning in visibleWarnings"
            :key="warning.id"
            class="warning-item"
            :class="warningLevelTone(warning.level)"
          >
            <div class="warning-item-head">
              <span class="warning-level">{{ warning.level || '预警' }}</span>
              <span class="warning-region">{{ warning.regionName }}</span>
            </div>
            <strong class="warning-type">{{ warning.typeName || '灾害' }}预警</strong>
            <p class="warning-title">{{ warning.title }}</p>
          </article>
        </div>
        <b v-else>{{ alertOverview?.configured ? '暂无预警' : '接口未配置' }}</b>
      </aside>
      <template v-else>
        <div class="radar-toolbar">
          <button type="button" class="radar-toggle" :class="{ active: radarEnabled }" @click="toggleRadarLayer">
            <i />雷达层
          </button>
          <div v-if="radarEnabled" class="radar-controls">
            <button type="button" :disabled="!radarFrames.length" @click="stepRadarFrame(-1)">‹</button>
            <button type="button" :disabled="radarFrames.length < 2" @click="toggleRadarPlayback">{{ radarPlaying ? '暂停' : '回放' }}</button>
            <button type="button" :disabled="!radarFrames.length" @click="stepRadarFrame(1)">›</button>
            <span>{{ radarTimeLabel }}</span>
          </div>
        </div>
        <aside class="storm-list-panel">
          <header class="storm-panel-head">
            <p>活跃台风</p>
            <span>{{ tropicalStorms.length }}</span>
          </header>
          <button
            v-for="storm in tropicalStorms"
            :key="storm.id"
            type="button"
            :class="{ active: storm.id === selectedTropicalStorm?.id }"
            @click="selectedStormId = storm.id"
          >
            <span class="storm-dot" aria-hidden="true" />
            <div class="storm-meta">
              <b>{{ storm.name || '未命名台风' }}</b>
              <em>{{ storm.type || '—' }}</em>
            </div>
            <small>{{ storm.windSpeed || '—' }} m/s</small>
          </button>
          <span v-if="!tropicalOverview?.configured" class="empty-state">台风接口未配置</span>
          <span v-else-if="!tropicalStorms.length" class="empty-state">当前无活跃台风</span>
        </aside>
        <aside v-if="selectedTropicalStorm" class="storm-detail-panel">
          <p>台风实况</p>
          <div class="storm-identity">
            <strong>{{ selectedTropicalStorm.name || '暂无活跃台风' }}</strong>
            <em>{{ selectedTropicalStorm.type || '—' }}</em>
          </div>
          <dl>
            <div><dt>中心风速</dt><dd>{{ selectedTropicalStorm.windSpeed || '—' }} <small>m/s</small></dd></div>
            <div><dt>中心气压</dt><dd>{{ selectedTropicalStorm.pressure || '—' }} <small>hPa</small></dd></div>
            <div><dt>移动方向</dt><dd>{{ selectedTropicalStorm.moveDir || '—' }}</dd></div>
            <div><dt>移动速度</dt><dd>{{ selectedTropicalStorm.moveSpeed || '—' }} <small>km/h</small></dd></div>
          </dl>
          <div class="storm-track-meta">
            <div><span>实况路径</span><strong>{{ selectedTropicalStorm.track?.length || 0 }}</strong></div>
            <div><span>预报点</span><strong>{{ selectedTropicalStorm.forecast?.length || 0 }}</strong></div>
          </div>
          <div class="storm-legend">
            <i class="lg-track" /><span>历史路径</span>
            <i class="lg-forecast" /><span>预报路径</span>
            <i class="lg-cone" /><span>不确定性锥</span>
          </div>
        </aside>
      </template>
      <button v-if="mapMode === 'alerts' && drilldownRegion" type="button" class="map-back" @click="returnToNational">← 返回全国</button>
      <div v-if="mapMode === 'alerts'" class="map-caption">
        <span>全国预警态势 · {{ warningCount }} 条</span>
        <small>和风天气 · 拖拽缩放 · 点击行政区选中</small>
      </div>
      <div v-else class="map-caption tropical-caption">
        <span>天地图 · RainViewer 雷达</span>
        <small>实线历史 · 虚线预报 · 锥形不确定性</small>
      </div>
      <el-alert v-if="loadError" class="map-error" :title="loadError" type="error" :closable="false" show-icon />
    </section>
  </div>
</template>

<style scoped>
.official-alerts-page {
  display: flex;
  flex: 1;
  width: 100%;
  height: 100%;
  min-height: 0;
}
.china-command-map {
  position: relative;
  flex: 1;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: #071824;
}
.china-map { position: absolute; inset: 0; z-index: 1; background: #071824; }
.map-vignette, .map-grid, .map-scanline { position: absolute; inset: 0; z-index: 2; pointer-events: none; }
.map-vignette { background: radial-gradient(ellipse at center, transparent 48%, #020c14c8 100%); }
.map-grid {
  opacity: .18;
  background-image:
    linear-gradient(#3ee1f028 1px, transparent 1px),
    linear-gradient(90deg, #3ee1f028 1px, transparent 1px);
  background-size: 42px 42px;
}
.map-scanline { top: auto; height: 32%; background: linear-gradient(transparent, #16cfff24); }
.map-mode { position: absolute; z-index: 5; top: 22px; left: 26px; display: flex; overflow: hidden; border: 1px solid #4bc8f066; border-radius: 7px; background: #061a2be8; box-shadow: 0 10px 24px #00101d88; }.map-mode button { padding: 9px 14px; border: 0; border-right: 1px solid #4bc8f033; background: transparent; color: #8fb7cf; cursor: pointer; font-size: 12px; transition: background .15s ease, color .15s ease; }.map-mode button:last-child { border-right: 0; }.map-mode button:hover { color: #e4f9ff; }.map-mode button.active { background: #0d5373; color: #e4f9ff; box-shadow: inset 0 -2px #38dfff; }.china-command-map.is-tropical .map-mode button.active { background: #4a2a12; box-shadow: inset 0 -2px #ff9a45; }
.map-console {
  position: absolute;
  z-index: 4;
  top: 72px;
  left: 26px;
  display: grid;
  gap: 6px;
  width: 286px;
  padding: 16px;
  border: 1px solid #4bc8f066;
  border-radius: 8px;
  background: linear-gradient(165deg, #071d30f0, #061825f2 55%, #05141fe8);
  box-shadow: 0 12px 32px #00101d99, inset 0 1px #9fe9ff18;
}
.china-command-map.is-tropical :deep(.leaflet-tile-pane) {
  filter: none;
}
.china-command-map.is-tropical :deep(.rainviewer-radar) {
  mix-blend-mode: screen;
}
.radar-toolbar {
  position: absolute;
  z-index: 5;
  top: 22px;
  left: 214px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.radar-toggle {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 9px 12px;
  border: 1px solid #4bc8f066;
  border-radius: 7px;
  background: #061a2be8;
  color: #9fc4d8;
  cursor: pointer;
  font-size: 12px;
  box-shadow: 0 10px 24px #00101d88;
}
.radar-toggle i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #5f7f93;
  box-shadow: inset 0 0 0 1px #9fc4d855;
}
.radar-toggle.active {
  border-color: #38dfff88;
  color: #e4f9ff;
  background: #0d5373e8;
}
.radar-toggle.active i {
  background: #38dfff;
  box-shadow: 0 0 10px #38dfff;
}
.radar-controls {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border: 1px solid #ffffff1f;
  border-radius: 7px;
  background: #061018d8;
  backdrop-filter: blur(8px);
}
.radar-controls button {
  min-width: 34px;
  padding: 6px 8px;
  border: 0;
  border-radius: 5px;
  background: #0d2a3c;
  color: #d7f3ff;
  cursor: pointer;
  font-size: 11px;
}
.radar-controls button:disabled {
  opacity: .45;
  cursor: not-allowed;
}
.radar-controls span {
  padding: 0 8px;
  color: #9fc0d3;
  font: 600 11px/1 ui-monospace, monospace;
  white-space: nowrap;
}
.storm-list-panel,
.storm-detail-panel {
  position: absolute;
  z-index: 4;
  border: 1px solid #ffffff1f;
  border-radius: 10px;
  background: #061018d4;
  backdrop-filter: blur(10px);
  box-shadow: 0 14px 36px #00000066;
}
.storm-list-panel::before, .storm-detail-panel::before { content: ''; position: absolute; top: 0; right: 18px; left: 18px; height: 1px; background: linear-gradient(90deg, transparent, #6de7ff99, transparent); }
.storm-list-panel {
  top: 72px;
  bottom: 26px;
  left: 26px;
  width: 268px;
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 14px;
  overflow: auto;
}
.storm-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.storm-panel-head p,
.storm-detail-panel > p {
  margin: 0;
  color: #9ec3d6;
  font-size: 10px;
  letter-spacing: .14em;
  text-transform: uppercase;
}
.storm-panel-head span {
  min-width: 22px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #ff7a3d33;
  color: #ffc08a;
  font: 700 11px/1.4 ui-monospace, monospace;
  text-align: center;
}
.storm-list-panel button {
  display: grid;
  grid-template-columns: 14px 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 11px 10px;
  border: 1px solid #ffffff14;
  border-radius: 8px;
  background: #0a1c28a8;
  color: #e8f6ff;
  cursor: pointer;
  text-align: left;
  transition: border-color .15s ease, background .15s ease;
}
.storm-list-panel button:hover { border-color: #5ad8f080; background: #123247e8; transform: translateX(2px); }
.storm-list-panel button.active {
  border-color: #ff9b4599;
  background: linear-gradient(100deg, #3a1f0ed4, #1a2834d0);
  box-shadow: inset 3px 0 #ff803d;
}
.storm-meta { display: grid; gap: 3px; min-width: 0; }
.storm-meta b { overflow: hidden; color: #f4fbff; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.storm-meta em { color: #ffc177; font-size: 10px; font-style: normal; }
.storm-list-panel button small { color: #9fc0d3; font-size: 10px; white-space: nowrap; }
.storm-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff8a3d;
  box-shadow: 0 0 10px #ff7a3d99;
}
.storm-list-panel button.active .storm-dot { background: #ffd27a; box-shadow: 0 0 12px #ffb24a; }
.empty-state { padding: 18px 4px; color: #8eafc2; font-size: 12px; }
.storm-detail-panel {
  top: 72px;
  right: 26px;
  width: 268px;
  padding: 14px;
}
.storm-identity {
  display: grid;
  gap: 4px;
  margin: 12px 0 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ffffff18;
}
.storm-identity strong { color: #f5fbff; font-size: 18px; letter-spacing: .02em; }
.storm-identity em { color: #ffbd70; font-size: 12px; font-style: normal; }
.storm-detail-panel dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin: 0;
}
.storm-detail-panel dl div {
  padding: 10px;
  border: 1px solid #ffffff14;
  border-radius: 7px;
  background: linear-gradient(145deg, #0e2a3ba6, #081924b8);
}
.storm-detail-panel dt { color: #8fb3c7; font-size: 10px; }
.storm-detail-panel dd { margin: 6px 0 0; color: #eaf8ff; font-size: 15px; }
.storm-detail-panel dd small { color: #8fb3c7; font-size: 9px; }
.storm-track-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #ffffff14;
}
.storm-track-meta > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border: 1px solid #ffffff12;
  border-radius: 7px;
  background: #0a2030a6;
  color: #9fc0d3;
  font-size: 11px;
}
.storm-track-meta strong { color: #ffe0b0; font-size: 14px; }
.storm-legend {
  display: grid;
  grid-template-columns: 14px 1fr 14px 1fr 14px 1fr;
  align-items: center;
  gap: 6px 8px;
  margin-top: 12px;
  color: #9bb8c9;
  font-size: 10px;
}
.storm-legend i {
  display: block;
  height: 2px;
  border-radius: 2px;
}
.storm-legend .lg-track { background: #ffd27a; }
.storm-legend .lg-forecast {
  background: linear-gradient(90deg, #fff4c8 40%, transparent 40%, transparent 60%, #fff4c8 60%);
  background-size: 10px 2px;
  height: 2px;
}
.storm-legend .lg-cone {
  height: 10px;
  border: 1px solid #ffe08a88;
  border-radius: 3px;
  background: #ffb34733;
}
.tropical-caption span { border-color: #ff9e4880; }
:deep(.storm-label) {
  padding: 4px 8px !important;
  border: 1px solid #ffb86a88 !important;
  border-radius: 5px !important;
  background: #1a120adb !important;
  color: #ffe8c4 !important;
  box-shadow: 0 6px 16px #0009 !important;
  font: 600 11px/1.2 'Microsoft YaHei', sans-serif !important;
}
:deep(.storm-label::before) { display: none !important; }
:deep(.storm-label-active) {
  border-color: #ffd27acc !important;
  background: #2c1809ef !important;
  color: #fff4df !important;
}
.map-console p { margin: 0; color: #75a8c6; font-size: 10px; letter-spacing: .1em; }
.map-console > strong { color: #e6f7ff; font-size: 17px; }
.map-console > span { color: #8daec3; font-size: 11px; }
.map-console hr { width: 100%; margin: 4px 0 6px; border: 0; border-top: 1px solid #4bc8f02e; }
.map-console > b { color: #ffbd70; font-size: 12px; }

.warning-list {
  display: grid;
  gap: 12px;
  max-height: 360px;
  overflow: hidden;
}
.warning-item {
  position: relative;
  display: grid;
  gap: 8px;
  padding: 14px 14px 14px 16px;
  border: 1px solid #3d8fb84d;
  border-radius: 8px;
  background: linear-gradient(110deg, #0a2740d4, #0b1f3399);
  box-shadow: inset 0 1px #ffffff0d;
  overflow: hidden;
}
.warning-item::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
  background: var(--tone, #62a0ff);
  box-shadow: 0 0 10px color-mix(in srgb, var(--tone, #62a0ff) 55%, transparent);
}
.warning-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.warning-level {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border: 1px solid color-mix(in srgb, var(--tone, #62a0ff) 70%, #fff 10%);
  border-radius: 4px;
  background: color-mix(in srgb, var(--tone, #62a0ff) 22%, transparent);
  color: var(--tone-text, #d5e6ff);
  font: 700 10px/1.2 ui-monospace, 'Cascadia Code', monospace;
  letter-spacing: .06em;
}
.warning-region {
  color: #8fb7cf;
  font-size: 11px;
  letter-spacing: .04em;
  white-space: nowrap;
}
.warning-type {
  color: #e8f4ff;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: .02em;
  line-height: 1.35;
}
.warning-title {
  margin: 0;
  overflow: hidden;
  color: #94b6cb;
  font-size: 11px;
  line-height: 1.45;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  white-space: normal;
}
.warning-item.tone-red { --tone: #ff6b6b; --tone-text: #ffd0d0; }
.warning-item.tone-orange { --tone: #ffb04a; --tone-text: #ffe0b0; }
.warning-item.tone-yellow { --tone: #f0d45a; --tone-text: #fff3b8; }
.warning-item.tone-blue { --tone: #6b8fff; --tone-text: #d4deff; }
.warning-item.tone-default { --tone: #4ec8ef; --tone-text: #c8f0ff; }
.map-back { position: absolute; z-index: 5; top: 22px; left: 342px; padding: 8px 11px; border: 1px solid #5bdcffaa; border-radius: 6px; background: #061a2be8; color: #d8f7ff; cursor: pointer; font-size: 12px; }.map-back:hover { background: #0e4968; }.map-caption { position: absolute; z-index: 4; top: 22px; right: 26px; display: grid; justify-items: end; gap: 5px; color: #d9f6ff; }.map-caption span { padding: 7px 10px; border: 1px solid #32d5ff8c; border-radius: 6px; background: #061a2be8; font: 700 11px/1 ui-monospace, monospace; letter-spacing: .09em; }.map-caption small { color: #9bc1d8; font-size: 10px; }
.map-error { position: absolute; z-index: 5; right: 22px; bottom: 22px; left: 22px; width: auto; }
:deep(.leaflet-tile-pane) { filter: brightness(1.15) saturate(1.12) contrast(1.03); }:deep(.leaflet-control-zoom) { border: 1px solid #45c8ed82 !important; border-radius: 6px !important; overflow: hidden; box-shadow: 0 8px 20px #00101dbb !important; }:deep(.leaflet-control-zoom a) { width: 31px; height: 31px; border-color: #45c8ed5e !important; background: #061a2be8 !important; color: #c8f4ff !important; line-height: 31px; }:deep(.leaflet-control-zoom a:hover) { background: #0d4264 !important; }:deep(.leaflet-control-attribution) { display: none; }:deep(.admin-tooltip) { padding: 5px 8px; border: 1px solid #4ad4ff; border-radius: 4px; background: #051a2cf2; color: #e4f9ff; box-shadow: 0 4px 13px #00101dcc; font-size: 12px; }:deep(.admin-label) { padding: 2px 5px; border: 0; border-radius: 3px; background: #031521a8; color: #d9f8ff; box-shadow: none; font: 600 11px/1.2 'Microsoft YaHei', sans-serif; letter-spacing: .03em; text-align: center; white-space: nowrap; }:deep(.admin-label::before) { display: none; }
@media (max-width: 760px) { .map-console { top: 12px; left: 12px; transform: scale(.9); transform-origin: top left; }.map-caption { display: none; } }
</style>
