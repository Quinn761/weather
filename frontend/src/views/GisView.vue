<script setup>
import 'cesium/Build/Cesium/Widgets/widgets.css'

import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Cartesian3,
  Cartographic,
  Color,
  CustomDataSource,
  GeoJsonDataSource,
  Math as CesiumMath,
  ScreenSpaceEventHandler,
  ScreenSpaceEventType,
  UrlTemplateImageryProvider,
  Viewer,
} from 'cesium'
import { createGisFeature, deleteGisFeature, listGisFeatures } from '@/api/gis'

const mapEl = ref(null)
const features = ref([])
const activeTool = ref('point')
const drawingPoints = ref([])
const saving = ref(false)
const loading = ref(false)
const form = reactive({ name: '' })

let viewer
let handler
let draftSource
let savedSource

const canSavePolygon = computed(() => activeTool.value === 'polygon' && drawingPoints.value.length >= 3)

function toLonLat(cartesian) {
  const cartographic = Cartographic.fromCartesian(cartesian)
  return [
    Number(CesiumMath.toDegrees(cartographic.longitude).toFixed(7)),
    Number(CesiumMath.toDegrees(cartographic.latitude).toFixed(7)),
  ]
}

function pickPosition(position) {
  return viewer.scene.pickPosition(position) || viewer.camera.pickEllipsoid(position, viewer.scene.globe.ellipsoid)
}

function pointGeojson(coordinate) {
  return {
    type: 'Feature',
    geometry: { type: 'Point', coordinates: coordinate },
    properties: {},
  }
}

function polygonGeojson(coordinates) {
  const ring = [...coordinates]
  const first = ring[0]
  const last = ring[ring.length - 1]
  if (first[0] !== last[0] || first[1] !== last[1]) {
    ring.push(first)
  }
  return {
    type: 'Feature',
    geometry: { type: 'Polygon', coordinates: [ring] },
    properties: {},
  }
}

function clearDraft() {
  drawingPoints.value = []
  draftSource?.entities.removeAll()
}

function drawDraftPoint(coordinate) {
  draftSource.entities.add({
    position: Cartesian3.fromDegrees(coordinate[0], coordinate[1]),
    point: {
      color: Color.CYAN,
      outlineColor: Color.WHITE,
      outlineWidth: 2,
      pixelSize: 12,
    },
  })
}

function redrawDraftPolygon() {
  draftSource.entities.removeAll()
  drawingPoints.value.forEach(drawDraftPoint)
  if (drawingPoints.value.length >= 2) {
    draftSource.entities.add({
      polyline: {
        positions: drawingPoints.value.map((item) => Cartesian3.fromDegrees(item[0], item[1])),
        width: 3,
        material: Color.CYAN,
      },
    })
  }
  if (drawingPoints.value.length >= 3) {
    draftSource.entities.add({
      polygon: {
        hierarchy: drawingPoints.value.map((item) => Cartesian3.fromDegrees(item[0], item[1])),
        material: Color.CYAN.withAlpha(0.22),
        outline: true,
        outlineColor: Color.CYAN,
      },
    })
  }
}

async function addSavedFeature(feature) {
  const dataSource = await GeoJsonDataSource.load(JSON.parse(feature.geojson), {
    markerColor: Color.DODGERBLUE,
    stroke: Color.ORANGE,
    fill: Color.ORANGE.withAlpha(0.28),
    strokeWidth: 3,
  })
  savedSource.entities.suspendEvents()
  for (const entity of dataSource.entities.values) {
    entity.properties = { featureId: feature.id, name: feature.name }
    savedSource.entities.add(entity)
  }
  savedSource.entities.resumeEvents()
}

async function fetchFeatures() {
  loading.value = true
  try {
    features.value = (await listGisFeatures()) || []
    savedSource?.entities.removeAll()
    for (const item of features.value) {
      await addSavedFeature(item)
    }
  } finally {
    loading.value = false
  }
}

async function saveFeature(type, geojson) {
  saving.value = true
  try {
    const saved = await createGisFeature({
      name: form.name.trim() || (type === 'POINT' ? '未命名点位' : '未命名区域'),
      type,
      geojson: JSON.stringify(geojson),
      properties: JSON.stringify({ source: 'cesium' }),
    })
    features.value = [saved, ...features.value]
    await addSavedFeature(saved)
    clearDraft()
    form.name = ''
    ElMessage.success('已上传到服务器')
  } finally {
    saving.value = false
  }
}

async function savePolygon() {
  if (!canSavePolygon.value) {
    ElMessage.warning('至少需要 3 个点才能圈地')
    return
  }
  await saveFeature('POLYGON', polygonGeojson(drawingPoints.value))
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

function flyToFeature(feature) {
  const entities = savedSource.entities.values.filter((entity) => entity.properties?.featureId?.getValue?.() === feature.id)
  if (entities.length) {
    viewer.flyTo(entities)
  }
}

function bindMapEvents() {
  handler = new ScreenSpaceEventHandler(viewer.scene.canvas)
  handler.setInputAction(async (event) => {
    const position = pickPosition(event.position)
    if (!position) return
    const coordinate = toLonLat(position)
    if (activeTool.value === 'point') {
      drawDraftPoint(coordinate)
      await saveFeature('POINT', pointGeojson(coordinate))
      return
    }
    drawingPoints.value.push(coordinate)
    redrawDraftPolygon()
  }, ScreenSpaceEventType.LEFT_CLICK)
  handler.setInputAction(() => {
    if (activeTool.value === 'polygon' && drawingPoints.value.length) {
      savePolygon()
    }
  }, ScreenSpaceEventType.RIGHT_CLICK)
}

onMounted(async () => {
  await nextTick()
  viewer = new Viewer(mapEl.value, {
    animation: false,
    baseLayerPicker: false,
    fullscreenButton: false,
    geocoder: false,
    homeButton: false,
    imageryProvider: new UrlTemplateImageryProvider({
      url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      credit: 'OpenStreetMap',
    }),
    infoBox: false,
    sceneModePicker: false,
    selectionIndicator: false,
    timeline: false,
  })
  viewer.camera.flyTo({ destination: Cartesian3.fromDegrees(116.3974, 39.9093, 1200000) })
  draftSource = new CustomDataSource('draft-features')
  savedSource = new CustomDataSource('saved-features')
  viewer.dataSources.add(savedSource)
  viewer.dataSources.add(draftSource)
  bindMapEvents()
  fetchFeatures()
})

onBeforeUnmount(() => {
  handler?.destroy()
  viewer?.destroy()
})
</script>

<template>
  <section class="gis-page">
    <div class="gis-toolbar">
      <el-segmented
        v-model="activeTool"
        :options="[
          { label: '打点', value: 'point' },
          { label: '圈地', value: 'polygon' },
        ]"
      />
      <el-input v-model="form.name" class="name-input" maxlength="128" placeholder="标注名称" clearable />
      <el-button :disabled="!canSavePolygon" :loading="saving" type="primary" @click="savePolygon">保存圈地</el-button>
      <el-button @click="clearDraft">清空草稿</el-button>
    </div>
    <div class="gis-workbench">
      <div ref="mapEl" class="cesium-map" />
      <aside class="feature-panel">
        <div class="panel-head">
          <strong>服务器标注</strong>
          <el-button :loading="loading" text type="primary" @click="fetchFeatures">刷新</el-button>
        </div>
        <el-empty v-if="!features.length" description="暂无标注" />
        <div v-else class="feature-list">
          <div v-for="feature in features" :key="feature.id" class="feature-item">
            <button type="button" @click="flyToFeature(feature)">
              <strong>{{ feature.name }}</strong>
              <span>{{ feature.type === 'POINT' ? '点位' : '区域' }}</span>
            </button>
            <el-button text type="danger" @click="removeFeature(feature)">删除</el-button>
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

.gis-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.cesium-map {
  min-height: 0;
  overflow: hidden;
  background: #101923;
  border: 1px solid #dfe7f1;
  border-radius: 8px;
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

  .cesium-map {
    height: 520px;
  }
}
</style>
