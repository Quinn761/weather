<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ArrowDown, Close, Delete, DocumentCopy, Plus, Position, Search } from '@element-plus/icons-vue'
import AgentAnswer from '@/components/AgentAnswer.vue'
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
const input = ref(null)
const search = ref('')
const sessionLoading = ref(false)
const sessionError = ref(false)
const followOutput = ref(true)
const elapsed = ref(0)
const showHistory = ref(false)
const drafts = new Map()
const filteredSessions = computed(() => sessions.value.filter((item) => (item.title || '').toLowerCase().includes(search.value.trim().toLowerCase())))
const busy = computed(() => sending.value || loading.value || sessionLoading.value || deletingId.value !== null)
const canSend = computed(() => draft.value.trim() && !busy.value && status.value?.enabled !== false)
let sessionRevision = 0
let disposed = false
let streamAbort = null

const hints = [
  '无锡明天会不会下雨？',
  '帮我写一封简短的感谢信',
  '用简单的例子解释递归',
  '帮我安排一周的学习计划',
]

const currentTitle = computed(() => {
  return sessions.value.find((item) => item.id === sessionId.value)?.title || '新任务'
})

onMounted(async () => {
  loading.value = true
  try {
    await Promise.allSettled([
      getAiStatus().then((value) => { status.value = value }),
      refreshSessions(),
    ])
    if (sessions.value.length) {
      await openSession(sessions.value[0].id)
    }
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  disposed = true
  sessionRevision += 1
  streamAbort?.abort()
})

async function refreshSessions() {
  try {
    sessions.value = (await listAgentSessions()) || []
    sessionError.value = false
  } catch { sessionError.value = true }
}

function resetBoard() {
  sessionId.value = null
  messages.value = []
  mode.value = ''
}

async function openSession(id) {
  if (sending.value || deletingId.value !== null) return
  const revision = ++sessionRevision
  sessionLoading.value = true
  try {
  const detail = await getAgentSession(id)
  if (disposed || revision !== sessionRevision) return
  drafts.set(sessionId.value, draft.value)
  sessionId.value = id
  draft.value = drafts.get(id) || ''
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
  showHistory.value = false
  scrollBottom(true)
  } catch { /* HTTP client displays the error; keep the current conversation. */ }
  finally { if (revision === sessionRevision) sessionLoading.value = false }
}

async function newSession() {
  if (busy.value) return
  drafts.set(sessionId.value, draft.value)
  resetBoard()
  draft.value = drafts.get(null) || ''
  showHistory.value = false
  nextTick(() => input.value?.focus())
}

async function removeSession(item) {
  if (busy.value) return
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
    drafts.delete(item.id)
    ElMessage.success('会话已删除')
    if (sessionId.value === item.id) {
      if (remaining.length) {
        deletingId.value = null
        await openSession(remaining[0].id)
      } else {
        resetBoard()
        draft.value = drafts.get(null) || ''
      }
    }
  } finally {
    deletingId.value = null
  }
}

function scrollBottom(force = false) {
  if (force) followOutput.value = true
  nextTick(() => {
    if (board.value && followOutput.value) {
      board.value.scrollTop = board.value.scrollHeight
    }
  })
}

function handleInputKey(event) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  send()
}

function trackScroll() {
  const el = board.value
  if (el) followOutput.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

function chooseHint(hint) {
  if (busy.value) return
  draft.value = hint
  nextTick(() => input.value?.focus())
}

function stopReceiving() { streamAbort?.abort() }

async function copyAnswer(content) {
  try { await navigator.clipboard.writeText(content); ElMessage.success('回答已复制') }
  catch { ElMessage.info('当前浏览器不支持复制，请选中回答文本复制') }
}

async function send(text) {
  const content = (typeof text === 'string' ? text : draft.value).trim()
  if (!content || content.length > 4000 || busy.value || status.value?.enabled === false) return
  const abort = new AbortController()
  streamAbort = abort
  sending.value = true
  draft.value = ''
  drafts.delete(sessionId.value)
  elapsed.value = 0
  const started = Date.now()
  const timer = setInterval(() => { elapsed.value = Math.floor((Date.now() - started) / 1000) }, 1000)
  let timedOut = false
  const timeout = setTimeout(() => { timedOut = true; abort.abort() }, 180000)
  messages.value.push({ role: 'user', content, plan: [] })
  messages.value.push({ role: 'assistant', content: '', streaming: true, prompt: content,
    plan: [], usedTools: [], ragSources: [], trace: [], mode: '' })
  const bubble = messages.value[messages.value.length - 1]
  scrollBottom(true)
  try {
    if (!sessionId.value) {
      const created = await createAgentSession()
      if (disposed) return
      sessionId.value = created.id
      sessions.value = [{ ...created, title: content.slice(0, 40) }, ...sessions.value.filter((item) => item.id !== created.id)]
    }
    abort.signal.throwIfAborted()
    const data = await runAgentStream({ sessionId: sessionId.value, message: content }, {
      signal: abort.signal,
      onDelta(chunk) {
        if (disposed || abort.signal.aborted) return
        bubble.content += chunk
        scrollBottom()
      },
    })
    if (disposed || abort.signal.aborted) return
    Object.assign(bubble, { content: data.reply || bubble.content, mode: data.mode,
      plan: data.plan || [], usedTools: data.usedTools || [], ragSources: data.ragSources || [], trace: data.trace || [] })
    sessionId.value = data.sessionId ?? sessionId.value
    mode.value = data.mode
    await refreshSessions()
  } catch (error) {
    if (disposed) return
    if (abort.signal.aborted && !timedOut) bubble.stopped = true
    else bubble.error = timedOut ? '响应超时，已保留收到的内容。请稍后重试。' : error.message || '执行失败，请重试'
  } finally {
    clearInterval(timer)
    clearTimeout(timeout)
    bubble.streaming = false
    bubble.duration = Math.max(1, Math.round((Date.now() - started) / 1000))
    if (streamAbort === abort) { sending.value = false; streamAbort = null }
    if (!disposed) { scrollBottom(); nextTick(() => input.value?.focus()) }
  }
}

function modeLabel(value) {
  if (value === 'llm' || value === 'python-llm') {
    return '模型回答'
  }
  if (value === 'local' || value === 'python-local') {
    return '工具结果汇总'
  }
  return '准备就绪'
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
    <div class="studio">
      <aside class="session-col" :class="{ 'mobile-open': showHistory }" aria-label="会话记录">
        <div class="session-head">
          <div>
            <strong>会话记录</strong>
            <p>{{ sessions.length }} 个</p>
          </div>
          <el-button size="small" type="primary" plain :icon="Plus" :disabled="busy" @click="newSession">新建</el-button>
        </div>
        <el-input v-model="search" class="session-search" :prefix-icon="Search" clearable placeholder="搜索会话" aria-label="搜索会话" />
        <div class="session-list">
          <div
            v-for="item in filteredSessions"
            :key="item.id"
            class="session-item"
            :class="{ active: sessionId === item.id }"
          >
            <button class="session-main" type="button" :disabled="sending || deletingId !== null" :title="sending ? '先停止接收或等待完成' : item.title" @click="openSession(item.id)">
              <strong>{{ item.title }}</strong>
              <span>{{ formatWhen(item.updatedAt) }}</span>
            </button>
            <el-button
              class="session-del"
              text
              type="danger"
              :icon="Delete"
              :loading="deletingId === item.id"
              :disabled="busy"
              :aria-label="`删除会话 ${item.title}`"
              @click.stop="removeSession(item)"
            />
          </div>
          <p v-if="sessionError" class="session-empty">会话加载失败。<button class="text-action" @click="refreshSessions">重新加载</button></p>
          <p v-else-if="!filteredSessions.length" class="muted session-empty">{{ search ? '没有匹配的会话，换个关键词试试。' : '发送第一个问题后，会话会保存在这里。' }}</p>
        </div>
      </aside>

      <section class="ai-chat">
        <header class="chat-head">
          <button class="history-toggle text-action" @click="showHistory = !showHistory">{{ showHistory ? '收起记录' : '会话记录' }}</button>
          <div>
            <strong>{{ currentTitle }}</strong>
            <p>{{ messages.length ? `${messages.length} 条消息` : '开始对话' }}</p>
          </div>
          <span class="mode-chip" :class="modeChip(mode)">
            {{ modeLabel(mode) }}
          </span>
        </header>
        <div v-if="status && !status.configured" class="mode-notice">当前使用工具模式，可查询数据与知识，回答将直接汇总检索结果。</div>
        <div v-if="status?.enabled === false" class="mode-notice">Agent 服务未启用，请联系管理员。</div>
        <div ref="board" v-loading="sessionLoading" class="ai-board" :aria-busy="sending || sessionLoading" @scroll="trackScroll">
          <div v-if="!messages.length" class="ai-empty">
            <span class="welcome-mark">✧</span>
            <span class="welcome-kicker">你的 AI 助手</span>
            <strong>今天，有什么想了解的？</strong>
            <p>聊聊日常、写作、编程或学习，也可以查询天气和系统数据。</p>
            <div class="prompt-grid"><button v-for="(hint, hintIndex) in hints" :key="hint" :disabled="busy" @click="chooseHint(hint)"><span>{{ ['天气预报', '写作帮助', '编程解答', '学习计划'][hintIndex] }}</span><b>{{ hint }}</b><small>点击编辑问题 <span>↗</span></small></button></div>
          </div>
          <article
            v-for="(item, index) in messages"
            :key="index"
            class="bubble"
            :class="[item.role, { streaming: item.streaming }]"
          >
            <span v-if="item.role === 'assistant'" class="bubble-role">AI 助手</span>
            <p v-if="item.role === 'user'">{{ item.content }}</p>
            <AgentAnswer v-else-if="item.content" :content="item.content" />
            <div v-if="item.streaming" class="run-progress" role="status"><span class="pulse-dot" />{{ item.content ? '正在生成回答' : elapsed > 20 ? '仍在处理，请稍候' : '正在思考你的问题' }} <span>{{ elapsed }}s</span></div>
            <div v-if="item.error" class="message-error" role="alert"><strong>这次未能完成</strong><p>{{ item.error }}</p><button class="text-action" :disabled="busy" @click="send(item.prompt)">重试这个问题</button><button class="text-action" :disabled="busy" @click="chooseHint(item.prompt)">编辑后重发</button></div>
            <p v-if="item.stopped" class="stopped-note">已停止接收。服务器可能仍在处理，稍后可重新打开会话查看保存结果。</p>
            <div v-if="item.role === 'assistant' && !item.streaming && item.content" class="answer-actions"><button class="text-action" @click="copyAnswer(item.content)"><el-icon><DocumentCopy /></el-icon>复制回答</button><span>{{ modeLabel(item.mode) }}</span><span v-if="item.duration">用时 {{ item.duration }} 秒</span></div>
            <details v-if="item.role === 'assistant' && !item.streaming && (item.plan?.length || item.trace?.length || item.usedTools?.length || item.ragSources?.length)" class="execution-details">
              <summary>查看执行记录与依据 <span>{{ item.usedTools?.length || 0 }} 个工具 · {{ item.ragSources?.length || 0 }} 条来源</span></summary>
              <div v-if="item.usedTools?.length" class="evidence-tags"><span v-for="tool in item.usedTools" :key="tool">{{ tool }}</span></div>
              <ul v-if="item.ragSources?.length"><li v-for="(source, sourceIndex) in item.ragSources" :key="sourceIndex">{{ source }}</li></ul>
              <ol v-if="item.plan?.length"><li v-for="(step, stepIndex) in item.plan" :key="stepIndex"><strong>{{ crewLabel(step.agent) }} · {{ step.title }}</strong><p>{{ step.detail }}</p></li></ol>
              <details v-if="item.trace?.length" class="trace-details"><summary>详细记录（{{ item.trace.length }}）</summary><p v-for="(step, stepIndex) in item.trace" :key="stepIndex"><b>{{ step.stage }}</b> {{ step.detail }}</p></details>
            </details>
          </article>
        </div>
        <button v-if="!followOutput && messages.length" class="jump-bottom" @click="scrollBottom(true)"><el-icon><ArrowDown /></el-icon>回到最新回答</button>
        <form class="ai-input" @submit.prevent="send()">
          <el-input
            ref="input"
            v-model="draft"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 5 }"
            resize="none"
            :disabled="sessionLoading || status?.enabled === false"
            aria-label="输入给 Agent 的问题"
            maxlength="4000"
            placeholder="输入任何问题，或接着聊聊……"
            @keydown="handleInputKey"
          />
          <div class="composer-toolbar"><span>Enter 发送 · Shift + Enter 换行</span><small>{{ draft.length }}/4000</small><el-button v-if="sending" :icon="Close" @click="stopReceiving">停止接收</el-button><el-button v-else type="primary" :disabled="!canSend" :icon="Position" native-type="submit">发送</el-button></div>
        </form>
        <p class="composer-note">回答可能存在错误，重要信息请核实。</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.ai-page { height: calc(100dvh - 146px); min-height: 520px; overflow: hidden; gap: 20px; color: #183246; }
.studio { flex: 1; min-height: 0; display: grid; grid-template-columns: 236px minmax(0,1fr); grid-template-rows: minmax(0,1fr); border: 1px solid #dce5eb; border-radius: 16px; overflow: hidden; background: #fff; box-shadow: 0 8px 28px #163b4d08; position: relative; }
.session-col { display: flex; flex-direction: column; min-height: 0; background: #f7f9fb; border-right: 1px solid #e4ebef; }
.session-head { display: flex; justify-content: space-between; align-items: center; padding: 20px 16px 14px; font-size: 13px; }
.session-head p { color: #98a6b0; font-size: 11px; margin: 5px 0 0; }
.session-head :deep(.el-button) { background: #e8f4f4; border-color: #cbe4e6; color: #087780; border-radius: 7px; }
.session-search { width: auto; margin: 0 12px 12px; }
.session-search :deep(.el-input__wrapper) { box-shadow: 0 0 0 1px #e5ebef inset; border-radius: 8px; }
.session-list { flex: 1; min-height: 0; overflow: auto; padding: 0 10px 12px; }
.session-item { display: grid; grid-template-columns: minmax(0,1fr) 30px; align-items: center; border-radius: 9px; margin-bottom: 5px; border: 1px solid transparent; }
.session-item:hover { background: #edf2f5; }
.session-item.active { background: #fff; border-color: #dbe7eb; box-shadow: 0 2px 5px #153b5005; }
.session-main { border: 0; background: transparent; text-align: left; min-width: 0; padding: 13px 10px; cursor: pointer; }
.session-main strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; font-weight: 500; color: #365063; }
.session-main span { display: block; font-size: 10px; margin-top: 7px; color: #93a3ae; }
.active .session-main strong { color: #087e88; }
.session-del { opacity: 0; padding: 4px; width: 26px; }
.session-item:hover .session-del, .session-item:focus-within .session-del { opacity: 1; }
.session-empty { color: #8193a0; font-size: 12px; padding: 18px 8px; line-height: 1.8; }
.ai-chat { display: flex; flex-direction: column; min-height: 0; min-width: 0; position: relative; }
.chat-head { display: flex; align-items: center; justify-content: space-between; padding: 17px 26px; border-bottom: 1px solid #edf1f4; gap: 12px; flex-shrink: 0; }
.chat-head > div { min-width: 0; flex: 1; }
.chat-head strong { font-size: 14px; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-head p { color: #96a4ae; font-size: 11px; margin: 5px 0 0; }
.mode-chip { border: 1px solid #e0eaee; background: #f8fbfc; color: #79909d; padding: 5px 10px; font-size: 11px; border-radius: 20px; white-space: nowrap; }
.mode-chip.llm { color: #087c85; background: #edf7f7; border-color: #d7eaea; }
.mode-chip.local { color: #997b41; background: #fffbf2; }
.mode-notice { background: #fffbf2; color: #997b41; font-size: 12px; line-height: 1.6; padding: 8px 26px; border-bottom: 1px solid #f6efdf; }
.ai-board { flex: 1; min-height: 0; overflow: auto; overscroll-behavior: contain; display: flex; flex-direction: column; gap: 26px; padding: 26px 32px; background: #fff; }
.ai-empty { margin: auto; width: 100%; max-width: 620px; padding: 16px 0; text-align: center; }
.welcome-mark { display: grid; place-items: center; width: 48px; height: 48px; margin: 0 auto 17px; border-radius: 15px; color: #087c87; background: #edf7f7; border: 1px solid #d6ecec; font-size: 30px; }
.welcome-kicker { display: block; font-size: 11px; color: #77919d; letter-spacing: 1.4px; margin-bottom: 10px; }
.ai-empty > strong { display: block; font-size: clamp(22px,2.2vw,30px); font-weight: 600; letter-spacing: -.7px; }
.ai-empty > p { color: #8b9ca7; font-size: 13px; margin: 12px 0 25px; line-height: 1.7; }
.prompt-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10px; max-width: 560px; margin: auto; }
.prompt-grid button { border: 1px solid #e2eaf0; border-radius: 11px; padding: 17px 18px; background: #fff; color: inherit; cursor: pointer; text-align: left; transition: background .15s, border-color .15s; }
.prompt-grid button:hover { border-color: #9acace; background: #f6fbfb; }
.prompt-grid button > span { color: #16838a; font-size: 10px; }
.prompt-grid b { display: block; font-weight: 500; font-size: 13px; margin: 9px 0; }
.prompt-grid small { display: flex; justify-content: space-between; color: #98a6b0; font-size: 11px; }
.bubble { min-width: 0; max-width: 100%; line-height: 1.8; overflow-wrap: anywhere; flex-shrink: 0; }
.bubble.user { margin-left: auto; max-width: 85%; background: #edf5f7; border: 1px solid #e3edf0; border-radius: 12px 3px 12px 12px; padding: 12px 17px; font-size: 14px; }
.bubble p { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.bubble.assistant { padding: 0 0 10px; width: 100%; }
.bubble-role { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; color: #547788; font-size: 12px; font-weight: 600; }
.bubble-role::before { content: '✧'; display: grid; place-items: center; background: #e9f5f4; color: #14818a; height: 27px; width: 27px; border-radius: 8px; font-size: 19px; }
.run-progress { display: flex; align-items: center; gap: 9px; color: #64868e; font-size: 12px; padding: 10px 0; }
.run-progress > span:last-child { color: #91a5ad; font-size: 11px; }
.pulse-dot { width: 6px; height: 6px; border-radius: 50%; background: #169ba0; animation: pulse 1.5s ease-in-out infinite; }
@keyframes pulse { 50% { opacity: .3; } }
.message-error { background: #fff7f5; border: 1px solid #f4ded9; border-radius: 9px; padding: 12px; margin: 10px 0; color: #af5148; font-size: 12px; }
.message-error p { line-height: 1.6; margin: 7px 0; }
.message-error .text-action { margin-right: 15px; color: #a4483f; }
.bubble .stopped-note { font-size: 12px; color: #8b7b5e; line-height: 1.7; }
.answer-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 16px; margin-top: 12px; font-size: 11px; color: #9aaaae; }
.text-action { display: inline-flex; align-items: center; gap: 5px; border: 0; background: none; padding: 3px 0; color: #6e8794; font-size: 12px; cursor: pointer; }
.text-action:hover { color: #087e88; }
.execution-details { border: 1px solid #e7eef1; border-radius: 8px; margin-top: 12px; color: #6f8591; padding: 10px 12px; font-size: 12px; }
.execution-details summary { cursor: pointer; line-height: 1.7; }
.execution-details summary > span { color: #9aabb4; font-size: 10px; margin-left: 10px; }
.execution-details ol, .execution-details ul { padding-left: 18px; margin: 14px 0; }
.execution-details li { margin: 10px 0; }
.execution-details li strong { color: #536d7b; font-weight: 500; }
.execution-details p { margin: 5px 0; line-height: 1.7; }
.evidence-tags { display: flex; flex-wrap: wrap; gap: 6px; margin: 12px 0; }
.evidence-tags span { background: #f0f6f8; border-radius: 4px; padding: 3px 7px; overflow-wrap: anywhere; }
.trace-details { border-top: 1px solid #e7eef1; padding-top: 8px; }
.trace-details b { color: #506f80; font-weight: 500; }
.jump-bottom { align-self: center; margin-top: -35px; z-index: 2; position: relative; display: flex; align-items: center; gap: 6px; border-radius: 20px; border: 1px solid #d6e4e9; background: #fff; color: #4d7b89; box-shadow: 0 3px 12px #26465415; padding: 7px 14px; cursor: pointer; font-size: 12px; }
.ai-input { flex-shrink: 0; display: block; margin: 10px 26px 0; border: 1px solid #d9e4e9; border-radius: 12px; padding: 5px; box-shadow: 0 3px 12px #163b4d04; }
.ai-input:focus-within { border-color: #63aeb6; box-shadow: 0 0 0 3px #128e9610; }
.ai-input :deep(.el-textarea__inner) { box-shadow: none; padding: 11px 12px 6px; font-size: 14px; line-height: 1.7; color: #28485b; background: transparent; }
.ai-input :deep(.el-textarea__inner::placeholder) { color: #a0afb8; }
.composer-toolbar { display: flex; align-items: center; gap: 12px; padding: 6px 7px; }
.composer-toolbar > span { font-size: 10px; color: #99a9b4; flex: 1; }
.composer-toolbar > small { font-size: 10px; color: #9babb6; }
.composer-toolbar :deep(.el-button--primary) { --el-button-bg-color: #087f8b; --el-button-border-color: #087f8b; --el-button-hover-bg-color: #0b929b; --el-button-hover-border-color: #0b929b; --el-button-disabled-bg-color: #c0d9dd; --el-button-disabled-border-color: #c0d9dd; border-radius: 8px; }
.composer-note { margin: 9px 10px 12px; color: #a2afb8; text-align: center; font-size: 10px; }
button:disabled { cursor: not-allowed; opacity: .55; }
button:focus-visible, summary:focus-visible { outline: 2px solid #188b95; outline-offset: 3px; }
.history-toggle { display: none; }
.session-list, .ai-board { scrollbar-width: thin; scrollbar-color: #d3dfe5 transparent; }
@media (min-width: 1500px) { .ai-board { padding-left: max(32px,calc((100% - 900px)/2)); padding-right: max(32px,calc((100% - 900px)/2)); } }
@media (max-width: 1100px) { .studio { grid-template-columns: 205px minmax(0,1fr); } .ai-board { padding: 22px; } }
@media (max-width: 760px) {
  .ai-page { height: calc(100dvh - 122px); min-height: 520px; gap: 12px; }
  .studio { grid-template-columns: minmax(0,1fr); }
  .session-col { display: none; }
  .session-col.mobile-open { display: flex; position: absolute; inset: 60px auto 0 0; width: min(300px,90%); z-index: 10; box-shadow: 10px 0 40px #183b4c25; }
  .history-toggle { display: inline-flex; flex-shrink: 0; } .session-del { opacity: 1; }
  .chat-head { padding: 13px; } .mode-chip { font-size: 10px; }
  .mode-notice { padding: 7px 14px; }
  .ai-board { padding: 18px 14px; } .ai-input { margin: 8px 10px 0; }
  .composer-toolbar > span { display: none; } .composer-toolbar > small { margin-right: auto; }
  .ai-empty > strong { font-size: 22px; } .ai-empty > p { font-size: 12px; }
  .prompt-grid { gap: 8px; } .prompt-grid button { padding: 12px; } .prompt-grid b { font-size: 12px; }
  .execution-details summary > span { display: block; margin: 4px 0 0; }
}
@media (prefers-reduced-motion: reduce) { .pulse-dot { animation: none; } }
</style>
