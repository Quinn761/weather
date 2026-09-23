<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, EditPen, Location, Monitor, Plus, Timer, WarningFilled, WindPower } from '@element-plus/icons-vue'
import { createEvent, listEvents } from '@/api/operations'
import { useAuthStore } from '@/stores/auth'
import eventCenterBg from '@/assets/operations/event-center-bg.png'

const auth = useAuthStore()
const loading = ref(false)
const dialog = ref(false)
const source = ref('')
const events = ref([])
const canWrite = computed(() => auth.hasPermission('ops:event:write'))
const form = reactive({ sourceType: 'MANUAL', sourceKey: '', title: '', content: '', level: 'YELLOW' })
const sourceText = {
  OFFICIAL_ALERT: '官方预警',
  TROPICAL_CYCLONE: '热带气旋',
  CAMERA_ALERT: '摄像头告警',
  MANUAL: '人工上报',
}
const sourceIcon = {
  OFFICIAL_ALERT: Bell,
  TROPICAL_CYCLONE: WindPower,
  CAMERA_ALERT: Monitor,
  MANUAL: EditPen,
}
const levelText = { RED: '红色', ORANGE: '橙色', YELLOW: '黄色', BLUE: '蓝色' }
const sourceCount = computed(() => Object.keys(sourceText).map((key) => ({
  key,
  label: sourceText[key],
  icon: sourceIcon[key],
  count: events.value.filter((item) => item.sourceType === key).length,
})))
const activeCount = computed(() => events.value.filter((item) => item.status === 'OPEN').length)
const time = (value) => String(value || '').replace('T', ' ').slice(0, 16)

async function load() {
  loading.value = true
  try {
    events.value = await listEvents(source.value ? { sourceType: source.value } : {})
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { sourceType: 'MANUAL', sourceKey: '', title: '', content: '', level: 'YELLOW' })
  dialog.value = true
}

async function save() {
  if (!form.title.trim()) return ElMessage.warning('请填写事件标题')
  await createEvent({ ...form, title: form.title.trim(), sourceKey: form.sourceKey.trim() || null })
  dialog.value = false
  ElMessage.success('事件已进入处置中心')
  await load()
}

function toggleSource(key) {
  source.value = source.value === key ? '' : key
  load()
}

onMounted(async () => {
  // Menus and permissions can be seeded after an existing browser session started.
  // Refresh here so the event registration control reflects the current authority.
  try { await auth.fetchMe() } catch { /* the router already handles an expired session */ }
  await load()
})
</script>

<template>
  <div class="page event-command-page" :style="{ '--event-bg': `url(${eventCenterBg})` }">
    <section class="event-header">
      <div class="event-header-copy">
        <p class="eyebrow">Incident command center</p>
        <h2>统一事件中心</h2>
        <p>将预警、台风、视频识别和人工上报收拢到一条可追踪的风险事件流。</p>
      </div>
      <div class="event-actions">
        <div class="live-state"><i /><span>实时归集已开启</span></div>
        <el-button v-if="canWrite" class="cmd-btn" type="primary" :icon="Plus" @click="openCreate">登记事件</el-button>
      </div>
    </section>

    <section class="event-overview">
      <article class="overview-hero">
        <div class="hero-count">
          <strong>{{ activeCount }}</strong>
          <span>OPEN</span>
        </div>
        <div class="hero-copy">
          <p>当前开放事件</p>
          <small>持续同步官方预警、台风与摄像头异常</small>
        </div>
      </article>
      <div class="source-grid">
        <button
          v-for="item in sourceCount"
          :key="item.key"
          type="button"
          class="source-card"
          :class="[`src-${item.key.toLowerCase()}`, { selected: source === item.key }]"
          @click="toggleSource(item.key)"
        >
          <span class="source-icon"><el-icon><component :is="item.icon" /></el-icon></span>
          <div class="source-copy">
            <span>{{ item.label }}</span>
            <strong>{{ item.count }}</strong>
          </div>
        </button>
      </div>
    </section>

    <section class="event-controls">
      <div>
        <p>事件流</p>
        <span>{{ source ? sourceText[source] : '全部来源' }} · {{ events.length }} 条记录</span>
      </div>
      <el-select v-model="source" clearable placeholder="按来源筛选" @change="load">
        <el-option v-for="(label, key) in sourceText" :key="key" :label="label" :value="key" />
      </el-select>
    </section>

    <section v-loading="loading" class="event-stream">
      <article
        v-for="(event, index) in events"
        :key="event.id"
        class="event-row"
        :class="[
          `level-${event.level?.toLowerCase() || 'yellow'}`,
          { 'is-first': index === 0, 'is-last': index === events.length - 1 },
        ]"
      >
        <div class="event-rail" aria-hidden="true">
          <span class="event-line event-line-top" />
          <span class="event-dot">
            <i class="event-dot-core" />
            <el-icon><WarningFilled /></el-icon>
          </span>
          <span class="event-line event-line-bottom" />
        </div>
        <div class="event-main">
          <div class="event-meta">
            <span>{{ sourceText[event.sourceType] || event.sourceType }}</span>
            <span>·</span>
            <time>{{ time(event.occurredAt) }}</time>
          </div>
          <h3>{{ event.title }}</h3>
          <p>{{ event.content || '暂未提供事件补充说明。' }}</p>
          <div class="event-details">
            <span><el-icon><Location /></el-icon>{{ event.regionName || '未标注区域' }}</span>
            <span><el-icon><Timer /></el-icon>开放中</span>
          </div>
        </div>
        <div class="event-level">
          <span class="level-badge">{{ levelText[event.level] || event.level || '预警' }}</span>
          <span class="event-id">EVENT #{{ event.id }}</span>
        </div>
      </article>

      <div v-if="!loading && !events.length" class="event-empty">
        <el-icon><Bell /></el-icon>
        <strong>等待风险信号</strong>
        <p>系统会自动同步外部预警与台风；也可以手动登记事件。</p>
      </div>
    </section>

    <el-dialog
      v-model="dialog"
      title="登记事件"
      width="560px"
      destroy-on-close
      append-to-body
      align-center
    >
      <el-form label-position="top">
        <el-form-item label="来源">
          <el-select v-model="form.sourceType" disabled>
            <el-option v-for="(label, key) in sourceText" v-if="key === 'MANUAL'" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="事件标题" required>
          <el-input v-model="form.title" maxlength="160" placeholder="例如：东港码头附近出现路面积水" />
        </el-form-item>
        <el-form-item label="预警等级">
          <el-select v-model="form.level" placeholder="请选择事件等级">
            <el-option label="红色" value="RED" />
            <el-option label="橙色" value="ORANGE" />
            <el-option label="黄色" value="YELLOW" />
            <el-option label="蓝色" value="BLUE" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.content" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="请描述发现时间、影响范围和建议处置措施" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="cmd-btn-ghost" @click="dialog = false">取消</el-button>
        <el-button class="cmd-btn" type="primary" @click="save">登记并通知</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.event-command-page {
  position: relative;
  isolation: isolate;
  max-width: 1320px;
  margin: 0 auto;
  padding: 8px 4px 28px;
  overflow: hidden;
  border: 1px solid #6fcaed22;
  border-radius: 18px;
  background: #0a1624e8;
  box-shadow: 0 18px 48px #02081455;
}

.event-command-page::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  background:
    linear-gradient(165deg, #071421f5 0%, #0a1726d8 42%, #08131fd0 100%),
    var(--event-bg) center / cover no-repeat;
  pointer-events: none;
}

.event-command-page::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  background:
    radial-gradient(ellipse at 18% 0%, #45d6ee14, transparent 42%),
    radial-gradient(ellipse at 88% 18%, #ff9a3d10, transparent 36%);
  pointer-events: none;
}

.event-command-page > * {
  position: relative;
  z-index: 1;
}

.event-header {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 28px;
  padding: 18px 22px 24px;
  border-bottom: 1px solid #76dfff1c;
}

.event-header-copy .eyebrow {
  margin: 0 0 10px;
  color: #57dff5;
  font-size: 11px;
  letter-spacing: .16em;
  text-transform: uppercase;
}

.event-header-copy h2 {
  margin: 0;
  color: #f0f8ff;
  font-size: 30px;
  letter-spacing: -.03em;
}

.event-header-copy > p:last-child {
  max-width: 620px;
  margin: 11px 0 0;
  color: #91a9bf;
  line-height: 1.7;
}

.event-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.live-state {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid #3d7f6a55;
  border-radius: 999px;
  background: #12302899;
  color: #9fc4b4;
  font-size: 12px;
  white-space: nowrap;
}

.live-state i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #5ee2a1;
  box-shadow: 0 0 12px #5ee2a1;
}

.event-overview {
  display: grid;
  grid-template-columns: minmax(220px, 0.95fr) minmax(0, 2.2fr);
  gap: 12px;
  margin: 20px 18px 0;
}

.overview-hero {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 118px;
  padding: 18px 20px;
  overflow: hidden;
  border: 1px solid #4bd7ee44;
  border-radius: 14px;
  background:
    radial-gradient(circle at 12% 100%, #35d8ed28, transparent 42%),
    linear-gradient(135deg, #143e58e8, #10273ad4 58%, #0c1c2ccc);
  box-shadow: inset 3px 0 #48dff0, 0 12px 28px #00101d40;
}

.hero-count {
  display: grid;
  justify-items: center;
  gap: 4px;
  min-width: 72px;
  padding: 12px 10px;
  border: 1px solid #76e7f655;
  border-radius: 14px;
  background: #071b2bcc;
  box-shadow: inset 0 0 20px #42dfff22;
}

.hero-count strong {
  color: #effcff;
  font-size: 34px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -.04em;
}

.hero-count span {
  color: #6fd8ec;
  font: 700 10px/1 ui-monospace, monospace;
  letter-spacing: .14em;
}

.hero-copy {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.hero-copy p {
  margin: 0;
  color: #eaf6ff;
  font-size: 15px;
  font-weight: 600;
}

.hero-copy small {
  color: #8eabbf;
  font-size: 12px;
  line-height: 1.5;
}

.source-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.source-card {
  display: grid;
  gap: 14px;
  min-height: 118px;
  padding: 16px;
  border: 1px solid #70c9ec22;
  border-radius: 14px;
  background: linear-gradient(165deg, #0f2436d4, #0b1a29c8);
  color: #a9c0d3;
  font: inherit;
  text-align: left;
  cursor: pointer;
  appearance: none;
  transition: border-color .18s ease, background .18s ease, transform .18s ease, box-shadow .18s ease;
}

.source-card:hover {
  border-color: #51dff355;
  background: linear-gradient(165deg, #163b50e0, #102538d0);
  color: #dff9ff;
  transform: translateY(-2px);
}

.source-card.selected {
  border-color: color-mix(in srgb, var(--src-tone, #48dff0) 70%, #fff 10%);
  background: linear-gradient(165deg, color-mix(in srgb, var(--src-tone, #48dff0) 18%, #123246), #0f2234e8);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--src-tone, #48dff0) 25%, transparent), 0 10px 24px #00101d44;
  color: #eaf8ff;
}

.source-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid color-mix(in srgb, var(--src-tone, #6ec8e0) 45%, transparent);
  border-radius: 10px;
  background: color-mix(in srgb, var(--src-tone, #48dff0) 14%, #0a1824);
  color: var(--src-tone, #7fe4f7);
  font-size: 16px;
}

.source-copy {
  display: grid;
  gap: 6px;
  margin-top: auto;
}

.source-copy span {
  color: inherit;
  font-size: 12px;
  opacity: .88;
}

.source-copy strong {
  color: #eef8ff;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -.03em;
  line-height: 1;
}

.source-card.src-official_alert { --src-tone: #ffb04a; }
.source-card.src-tropical_cyclone { --src-tone: #5ad0ff; }
.source-card.src-camera_alert { --src-tone: #ff7a88; }
.source-card.src-manual { --src-tone: #7ee0b0; }

.event-controls {
  display: flex;
  align-items: end;
  justify-content: space-between;
  margin: 28px 22px 12px;
}

.event-controls p {
  margin: 0;
  color: #e5f5ff;
  font-size: 16px;
  font-weight: 650;
}

.event-controls span {
  display: block;
  margin-top: 5px;
  color: #7892a9;
  font-size: 12px;
}

.event-controls .el-select {
  width: 180px;
}

.event-stream {
  margin: 0 10px 8px;
  border-top: 1px solid #5c92ae2b;
}

.event-row {
  --rail-tone: #eac14f;
  --rail-soft: #eac14f33;
  --rail-glow: #f4d05d44;
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) 120px;
  gap: 14px;
  min-height: 142px;
  padding: 18px 14px 16px;
  border-bottom: 1px solid #5c92ae20;
  border-radius: 10px;
  transition: background .18s ease;
}

.event-row:hover {
  background: linear-gradient(90deg, #2e9cba12, transparent 72%);
}

.event-rail {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 100%;
}

.event-line {
  width: 2px;
  background: linear-gradient(180deg, #6da5bc00, #6da5bc55 30%, #6da5bc55 70%, #6da5bc00);
}

.event-line-top {
  height: 10px;
  flex: 0 0 auto;
  background: linear-gradient(180deg, transparent, color-mix(in srgb, var(--rail-tone) 45%, #6da5bc));
}

.event-line-bottom {
  flex: 1 1 auto;
  min-height: 28px;
  margin-top: 2px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--rail-tone) 45%, #6da5bc), #6da5bc33 55%, #6da5bc00);
}

.event-row.is-first .event-line-top {
  opacity: 0;
}

.event-row.is-last .event-line-bottom {
  background: linear-gradient(180deg, color-mix(in srgb, var(--rail-tone) 40%, #6da5bc), transparent);
}

.event-dot {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid color-mix(in srgb, var(--rail-tone) 80%, #fff);
  border-radius: 50%;
  background:
    radial-gradient(circle at 35% 30%, #ffffff22, transparent 55%),
    color-mix(in srgb, var(--rail-tone) 16%, #0b1724);
  color: var(--rail-tone);
  box-shadow:
    0 0 0 4px var(--rail-soft),
    0 0 16px var(--rail-glow);
}

.event-dot-core {
  position: absolute;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--rail-tone);
  box-shadow: 0 0 8px var(--rail-tone);
  opacity: .55;
}

.event-dot .el-icon {
  position: relative;
  z-index: 1;
  font-size: 13px;
}

.event-row:hover .event-dot {
  box-shadow:
    0 0 0 5px var(--rail-soft),
    0 0 20px var(--rail-glow);
}

.level-yellow {
  --rail-tone: #f0d45a;
  --rail-soft: #f0d45a28;
  --rail-glow: #f0d45a40;
}

.level-red {
  --rail-tone: #ff7a88;
  --rail-soft: #ff596722;
  --rail-glow: #ff657544;
}

.level-orange {
  --rail-tone: #ffb04a;
  --rail-soft: #ff9a3220;
  --rail-glow: #ffaf5640;
}

.level-blue {
  --rail-tone: #6fc2ff;
  --rail-soft: #3b9bff1f;
  --rail-glow: #52b4ff40;
}

.event-meta {
  display: flex;
  gap: 8px;
  color: #72b4cc;
  font-size: 12px;
}

.event-main h3 {
  margin: 8px 0 6px;
  color: #eaf6ff;
  font-size: 17px;
}

.event-main p {
  max-width: 800px;
  margin: 0;
  color: #91aabd;
  font-size: 13px;
  line-height: 1.65;
}

.event-details {
  display: flex;
  gap: 18px;
  margin-top: 13px;
  color: #718ca5;
  font-size: 12px;
}

.event-details span {
  display: flex;
  align-items: center;
  gap: 5px;
}

.event-level {
  display: flex;
  flex-direction: column;
  align-items: end;
  gap: 10px;
  padding-top: 2px;
}

.level-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 48px;
  padding: 5px 10px;
  border: 1px solid color-mix(in srgb, var(--rail-tone) 70%, #fff 8%);
  border-radius: 6px;
  background:
    linear-gradient(160deg, color-mix(in srgb, var(--rail-tone) 22%, transparent), transparent 70%),
    color-mix(in srgb, var(--rail-tone) 12%, #0b1724);
  color: color-mix(in srgb, var(--rail-tone) 88%, #fff);
  font: 700 11px/1 ui-monospace, 'Cascadia Code', monospace;
  letter-spacing: .08em;
  box-shadow: inset 0 1px #ffffff14;
}

.event-id {
  color: #58748d;
  font-size: 10px;
  letter-spacing: .08em;
}

.event-empty {
  display: grid;
  justify-items: center;
  gap: 9px;
  margin: 24px auto 16px;
  max-width: 420px;
  padding: 48px 24px;
  border: 1px dashed #4d7f9655;
  border-radius: 14px;
  background: #07152199;
  color: #84a1b7;
  text-align: center;
}

.event-empty .el-icon {
  font-size: 30px;
  color: #4ed7ed;
}

.event-empty strong {
  color: #dbeeff;
}

.event-empty p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}

.region-field {
  display: none;
}

@media (max-width: 1100px) {
  .event-overview {
    grid-template-columns: 1fr;
  }

  .source-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .event-header {
    align-items: start;
    flex-direction: column;
  }

  .source-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .source-card {
    min-height: 96px;
  }

  .event-row {
    grid-template-columns: 42px minmax(0, 1fr);
  }

  .event-level {
    grid-column: 2;
    align-items: start;
    flex-direction: row;
  }

  .event-line-top,
  .event-line-bottom {
    width: 2px;
  }
}

@media (max-width: 560px) {
  .event-header-copy h2 {
    font-size: 27px;
  }

  .event-actions {
    width: 100%;
    justify-content: space-between;
  }

  .event-controls {
    align-items: start;
    gap: 14px;
    flex-direction: column;
  }

  .event-controls .el-select {
    width: 100%;
  }

  .event-overview {
    margin-inline: 12px;
  }

  .overview-hero {
    min-height: 96px;
    padding: 14px 16px;
  }

  .hero-count strong {
    font-size: 28px;
  }

  .source-card {
    padding: 14px;
  }

  .source-copy strong {
    font-size: 22px;
  }
}
</style>
