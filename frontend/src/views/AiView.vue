<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { Delete, Plus, Position } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAgentSession,
  deleteAgentSession,
  getAgentSession,
  getAiStatus,
  listAgentSessions,
  runAgentStream,
} from '@/api/ai'

const CREW = [
  { id: 'planner', label: '规划官', hint: '拆任务' },
  { id: 'knowledge', label: '知识官', hint: '查 RAG' },
  { id: 'operator', label: '执行官', hint: '调工具' },
  { id: 'reviewer', label: '质检官', hint: '交卷' },
]

const status = ref(null)
const sessions = ref([])
const sessionId = ref(null)
const messages = ref([])
const mode = ref('')
const sending = ref(false)
const loading = ref(false)
const deletingId = ref(null)
const draft = ref('')
const board = ref(null)
let streamAbort = null

const hints = [
  '无锡明天会不会下雨？',
  '上海空气质量怎么样？',
  '无锡现在天气怎么样？',
  '现在系统有多少用户？',
]

const lastCrew = computed(() => {
  const used = new Set()
  for (const item of messages.value) {
    for (const step of item.plan || []) {
      used.add(step.agent)
    }
  }
  return used
})

const currentTitle = computed(() => {
  return sessions.value.find((item) => item.id === sessionId.value)?.title || '新任务'
})

onMounted(async () => {
  loading.value = true
  try {
    status.value = await getAiStatus()
    await refreshSessions()
    if (sessions.value.length) {
      await openSession(sessions.value[0].id)
    }
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  streamAbort?.abort()
})

async function refreshSessions() {
  sessions.value = (await listAgentSessions()) || []
}

function resetBoard() {
  sessionId.value = null
  messages.value = []
  mode.value = ''
}

async function openSession(id) {
  streamAbort?.abort()
  sessionId.value = id
  const detail = await getAgentSession(id)
  messages.value = (detail.messages || []).map((item) => ({
    role: item.role,
    content: item.content,
    agent: item.agent,
    plan: item.plan || [],
    mode: item.mode,
    usedTools: item.usedTools || [],
    ragSources: item.ragSources || [],
    trace: item.trace || [],
  }))
  const last = [...messages.value].reverse().find((item) => item.role === 'assistant')
  mode.value = last?.mode || ''
  scrollBottom()
}

async function newSession() {
  const created = await createAgentSession()
  sessionId.value = created.id
  messages.value = []
  mode.value = ''
  await refreshSessions()
}

async function removeSession(item) {
  try {
    await ElMessageBox.confirm(`确认删除会话「${item.title}」？其中的消息会一并删掉。`, '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  deletingId.value = item.id
  try {
    await deleteAgentSession(item.id)
    const remaining = sessions.value.filter((row) => row.id !== item.id)
    sessions.value = remaining
    ElMessage.success('会话已删除')
    if (sessionId.value === item.id) {
      if (remaining.length) {
        await openSession(remaining[0].id)
      } else {
        resetBoard()
      }
    }
  } finally {
    deletingId.value = null
  }
}

function scrollBottom() {
  nextTick(() => {
    if (board.value) {
      board.value.scrollTop = board.value.scrollHeight
    }
  })
}

async function send(text) {
  const content = (text ?? draft.value).trim()
  if (!content || sending.value) {
    return
  }
  draft.value = ''
  messages.value.push({ role: 'user', content, plan: [] })
  messages.value.push({
    role: 'assistant',
    content: '',
    pending: true,
    streaming: true,
    agent: 'reviewer',
    plan: [],
    usedTools: [],
    ragSources: [],
    trace: [],
    mode: '',
  })
  const bubble = messages.value[messages.value.length - 1]
  sending.value = true
  scrollBottom()
  streamAbort?.abort()
  const abort = new AbortController()
  streamAbort = abort
  try {
    const data = await runAgentStream(
      { sessionId: sessionId.value, message: content },
      {
        signal: abort.signal,
        onDelta: (chunk) => revealText(bubble, chunk, abort.signal),
      },
    )
    applyRunResult(bubble, data)
    sessionId.value = data.sessionId
    mode.value = data.mode
    if (!bubble.content && data.reply) {
      await revealText(bubble, data.reply, abort.signal)
    } else if (data.reply && bubble.content !== data.reply) {
      bubble.content = data.reply
    }
    bubble.pending = false
    bubble.streaming = false
    await refreshSessions()
  } catch (error) {
    if (error.name === 'AbortError') {
      return
    }
    bubble.pending = false
    bubble.streaming = false
    if (!bubble.content) {
      bubble.content = error.message || 'Agent 执行失败'
    }
    bubble.plan = bubble.plan || []
  } finally {
    sending.value = false
    if (streamAbort === abort) {
      streamAbort = null
    }
    scrollBottom()
  }
}

function applyRunResult(bubble, data) {
  bubble.agent = 'reviewer'
  bubble.plan = data.plan || []
  bubble.usedTools = data.usedTools || []
  bubble.ragSources = data.ragSources || []
  bubble.trace = data.trace || []
  bubble.mode = data.mode
}

async function revealText(bubble, text, signal) {
  const chunk = text || ''
  if (!chunk) {
    return
  }
  if (bubble.pending) {
    bubble.content = ''
    bubble.pending = false
  }
  const chars = Array.from(chunk)
  if (chars.length <= 24) {
    bubble.content += chunk
    scrollBottom()
    return
  }
  const started = bubble.content
  const duration = Math.min(2400, Math.max(280, chars.length * 16))
  const start = performance.now()
  await new Promise((resolve) => {
    function tick() {
      if (signal?.aborted) {
        bubble.content = started + chunk
        resolve()
        return
      }
      const ratio = Math.min(1, (performance.now() - start) / duration)
      bubble.content = started + chars.slice(0, Math.ceil(chars.length * ratio)).join('')
      scrollBottom()
      if (ratio >= 1) {
        resolve()
        return
      }
      requestAnimationFrame(tick)
    }
    requestAnimationFrame(tick)
  })
}

function modeLabel(value) {
  if (value === 'llm' || value === 'python-llm') {
    return '大模型质检'
  }
  if (value === 'local' || value === 'python-local') {
    return '本地交卷'
  }
  return '未执行'
}

function modeChip(value) {
  if (value === 'llm' || value === 'python-llm') {
    return 'llm'
  }
  if (value === 'local' || value === 'python-local') {
    return 'local'
  }
  return 'idle'
}

function crewLabel(id) {
  return CREW.find((item) => item.id === id)?.label || id
}

function toDate(value) {
  if (!value) {
    return null
  }
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value
    return new Date(year, month - 1, day, hour, minute, Math.floor(second))
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function formatWhen(value) {
  const date = toDate(value)
  if (!date) {
    return ''
  }
  const minutes = Math.max(0, Math.floor((Date.now() - date.getTime()) / 60000))
  if (minutes < 1) {
    return '刚刚'
  }
  if (minutes < 60) {
    return `${minutes} 分钟前`
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return `${hours} 小时前`
  }
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${month}/${day} ${hh}:${mm}`
}
</script>

<template>
  <div class="page ai-page" v-loading="loading">
    <section class="hero-panel ai-hero">
      <div>
        <p class="eyebrow">Planner · Knowledge · Operator · Reviewer</p>
        <h2>Agent 工作台</h2>
        <p class="hero-desc">四位角色按计划执行。有余额时质检官用大模型润色，否则用真实工具结果本地交卷。</p>
      </div>
      <div v-if="status" class="hero-meta">
        <span>{{ status.configured ? status.model : '本地交卷' }}</span>
        <span>{{ (status.tools || []).length }} 个工具</span>
        <span>{{ status.ragDocuments }} 篇知识</span>
      </div>
    </section>

    <ol class="pipeline">
      <li v-for="(role, index) in CREW" :key="role.id" :class="{ hot: lastCrew.has(role.id) }">
        <em>{{ index + 1 }}</em>
        <div>
          <strong>{{ role.label }}</strong>
          <span>{{ role.hint }}</span>
        </div>
      </li>
    </ol>

    <el-alert
      v-if="status && !status.configured"
      type="warning"
      show-icon
      :closable="false"
      title="大模型 Key 未配置，工作台走本地交卷"
      description="规划、检索、工具调用仍然执行。给 DeepSeek 充值并设置 AI_API_KEY 后，质检官会改用大模型写最终回答。"
    />

    <div class="studio">
      <aside class="session-col">
        <div class="session-head">
          <div>
            <strong>任务会话</strong>
            <p>{{ sessions.length }} 个</p>
          </div>
          <el-button size="small" type="primary" plain :icon="Plus" @click="newSession">新建</el-button>
        </div>
        <div class="session-list">
          <div
            v-for="item in sessions"
            :key="item.id"
            class="session-item"
            :class="{ active: sessionId === item.id }"
          >
            <button class="session-main" type="button" @click="openSession(item.id)">
              <strong>{{ item.title }}</strong>
              <span>{{ formatWhen(item.updatedAt) }}</span>
            </button>
            <el-button
              class="session-del"
              text
              type="danger"
              :icon="Delete"
              :loading="deletingId === item.id"
              @click.stop="removeSession(item)"
            />
          </div>
          <p v-if="!sessions.length" class="muted session-empty">还没有会话。发送第一条目标后会自动创建。</p>
        </div>
      </aside>

      <section class="ai-chat">
        <header class="chat-head">
          <div>
            <strong>{{ currentTitle }}</strong>
            <p>{{ messages.length ? `${messages.length} 条消息` : '等待目标' }}</p>
          </div>
          <span class="mode-chip" :class="modeChip(mode)">
            {{ modeLabel(mode) }}
          </span>
        </header>
        <div ref="board" class="ai-board">
          <div v-if="!messages.length" class="ai-empty">
            <strong>给 Agent 一个业务目标</strong>
            <p>例如「现在系统有多少用户」。规划官会拆步骤，其余角色按计划执行。</p>
          </div>
          <article
            v-for="(item, index) in messages"
            :key="index"
            class="bubble"
            :class="[item.role, { streaming: item.streaming }]"
          >
            <span v-if="item.role === 'assistant'" class="bubble-role">{{ crewLabel(item.agent || 'reviewer') }}</span>
            <p>{{ item.pending && item.streaming ? '正在规划并收集证据…' : item.content }}</p>
            <div v-if="item.role === 'assistant' && !item.streaming" class="bubble-meta">
              <span v-if="item.mode">{{ item.mode === 'local' || item.mode === 'python-local' ? '本地质检官' : '大模型质检官' }}</span>
              <span v-if="item.ragSources?.length">RAG {{ item.ragSources.join('、') }}</span>
              <span v-if="item.usedTools?.length">Tool {{ item.usedTools.join('、') }}</span>
            </div>
          </article>
        </div>
        <div class="ai-hints">
          <button v-for="hint in hints" :key="hint" type="button" class="hint" @click="send(hint)">{{ hint }}</button>
        </div>
        <form class="ai-input" @submit.prevent="send()">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="2"
            maxlength="4000"
            show-word-limit
            placeholder="给 Agent 一个目标，例如：GIS 里有哪些标注？"
            @keydown.enter.exact.prevent="send()"
          />
          <el-button type="primary" :loading="sending" :icon="Position" native-type="submit">执行</el-button>
        </form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.ai-page {
  height: calc(100dvh - 150px);
  min-height: 0;
  overflow: hidden;
}

.ai-hero,
.pipeline,
.ai-page > .el-alert {
  flex-shrink: 0;
}

.ai-hero {
  padding: 22px 24px;
}

.ai-hero .hero-desc {
  max-width: 560px;
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  max-width: 280px;
}

.hero-meta span {
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  white-space: nowrap;
}

.pipeline {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.pipeline li {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border-radius: 14px;
  padding: 12px 14px;
  color: #64748b;
  border: 1px solid #e2e8f0;
}

.pipeline em {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  font-style: normal;
  font-size: 12px;
  font-weight: 700;
  background: #f1f5f9;
  color: #64748b;
  flex-shrink: 0;
}

.pipeline strong {
  display: block;
  color: #0f172a;
  font-size: 13px;
}

.pipeline span {
  display: block;
  margin-top: 2px;
  font-size: 12px;
}

.pipeline li.hot {
  border-color: #7dd3fc;
  background: #ecfeff;
  box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.08);
}

.pipeline li.hot em {
  background: #0ea5e9;
  color: #fff;
}

.studio {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  gap: 14px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  align-items: stretch;
}

.session-col,
.ai-chat {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.session-col {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.session-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 16px 12px;
  border-bottom: 1px solid #f1f5f9;
  flex-shrink: 0;
}

.session-head p {
  margin: 4px 0 0;
  color: #94a3b8;
  font-size: 12px;
}

.session-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
  padding: 8px;
}

.session-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 32px;
  align-items: center;
  border-radius: 12px;
  margin-bottom: 4px;
}

.session-item:hover,
.session-item.active {
  background: #ecfeff;
}

.session-main {
  min-width: 0;
  text-align: left;
  border: 0;
  background: transparent;
  padding: 10px 8px 10px 10px;
  cursor: pointer;
}

.session-main strong {
  display: block;
  color: #0f172a;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-main span {
  display: block;
  margin-top: 4px;
  color: #94a3b8;
  font-size: 12px;
}

.session-item.active .session-main strong {
  color: #0e7490;
}

.session-del {
  opacity: 0;
}

.session-item:hover .session-del,
.session-item.active .session-del {
  opacity: 1;
}

.session-empty {
  padding: 18px 10px;
}

.ai-chat {
  padding: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.chat-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid #f1f5f9;
  flex-shrink: 0;
}

.chat-head p {
  margin: 4px 0 0;
  color: #94a3b8;
  font-size: 12px;
}

.mode-chip {
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  background: #f8fafc;
  color: #64748b;
  border: 1px solid #e2e8f0;
  white-space: nowrap;
}

.mode-chip.llm {
  background: #ecfeff;
  color: #0e7490;
  border-color: #a5f3fc;
}

.mode-chip.local {
  background: #fff7ed;
  color: #c2410c;
  border-color: #fed7aa;
}

.ai-board {
  flex: 1;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px;
  background:
    linear-gradient(#f8fafc, #f8fafc),
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.08), transparent 42%);
}

.ai-empty {
  margin: auto;
  text-align: center;
  max-width: 360px;
  color: #64748b;
  line-height: 1.7;
}

.ai-empty strong {
  display: block;
  margin-bottom: 6px;
  color: #0f172a;
  font-size: 16px;
}

.muted {
  color: #64748b;
  line-height: 1.7;
  font-size: 13px;
}

.bubble {
  max-width: 86%;
  padding: 12px 14px;
  border-radius: 16px;
  line-height: 1.7;
  min-width: 0;
  overflow-wrap: anywhere;
}

.bubble p {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.bubble.user {
  margin-left: auto;
  background: #0ea5e9;
  color: #fff;
  border-bottom-right-radius: 6px;
}

.bubble.assistant {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-bottom-left-radius: 6px;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.04);
}

.bubble.streaming p::after {
  content: '▍';
  margin-left: 2px;
  color: #0ea5e9;
  animation: caret 1s step-end infinite;
}

@keyframes caret {
  50% {
    opacity: 0;
  }
}

.bubble-role {
  display: inline-block;
  margin-bottom: 6px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #ecfeff;
  color: #0e7490;
  font-size: 11px;
}

.bubble-meta {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
}

.bubble-meta span {
  background: #f8fafc;
  border-radius: 999px;
  padding: 2px 8px;
}

.ai-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 18px 0;
  flex-shrink: 0;
}

.hint {
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #334155;
  border-radius: 999px;
  padding: 6px 10px;
  cursor: pointer;
  font-size: 12px;
}

.hint:hover {
  border-color: #7dd3fc;
  background: #ecfeff;
  color: #0e7490;
}

.ai-input {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: end;
  padding: 12px 18px 16px;
  flex-shrink: 0;
}

.session-list,
.ai-board {
  scrollbar-width: thin;
  scrollbar-color: #94a3b8 transparent;
}

.session-list::-webkit-scrollbar,
.ai-board::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.session-list::-webkit-scrollbar-track,
.ai-board::-webkit-scrollbar-track {
  background: transparent;
  margin: 8px 0;
}

.session-list::-webkit-scrollbar-thumb,
.ai-board::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 999px;
}

.session-list::-webkit-scrollbar-thumb:hover,
.ai-board::-webkit-scrollbar-thumb:hover {
  background: #7dd3fc;
}

@media (max-width: 1180px) {
  .ai-page {
    height: auto;
    max-height: none;
    overflow: visible;
  }

  .pipeline,
  .studio,
  .hero-meta {
    grid-template-columns: 1fr;
    max-width: none;
    justify-content: flex-start;
  }

  .studio {
    height: auto;
    overflow: visible;
    grid-template-rows: none;
  }

  .session-col {
    max-height: 280px;
  }

  .ai-chat {
    height: 520px;
  }
}
</style>
