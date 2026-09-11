import http from './http'

export function getAiStatus() {
  return http.get('/ai/status')
}

export function chatWithAi(payload) {
  return http.post('/ai/chat', payload, { timeout: 120000 })
}

export function runAgent(payload) {
  return http.post('/ai/run', payload, { timeout: 120000 })
}

export async function runAgentStream(payload, { onDelta, signal } = {}) {
  const token = localStorage.getItem('wh_token')
  const response = await fetch('/api/ai/run/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
    signal,
  })
  if (!response.ok) {
    let message = 'Agent 执行失败'
    try {
      const body = await response.json()
      message = body.message || message
    } catch {
      // ignore non-json error bodies
    }
    if (response.status === 401) {
      localStorage.removeItem('wh_token')
      localStorage.removeItem('wh_user')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    throw new Error(message)
  }
  if (!response.body) {
    throw new Error('浏览器不支持流式输出')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let doneEvent = null
  let reply = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const frames = buffer.split('\n\n')
    buffer = frames.pop() || ''
    for (const frame of frames) {
      const parsed = parseSseFrame(frame)
      if (!parsed) {
        continue
      }
      if (parsed.type === 'error' && parsed.message) {
        throw new Error(parsed.message)
      }
      if (parsed.type === 'delta' && parsed.text) {
        reply += parsed.text
        await onDelta?.(parsed.text)
      }
      if (parsed.type === 'end' || (parsed.type === 'done' && parsed.run)) {
        if (parsed.run) {
          doneEvent = parsed.run
        }
        try {
          await reader.cancel()
        } catch {
          // stream already closing
        }
        return (
          doneEvent || {
            sessionId: payload.sessionId,
            title: '',
            reply,
            mode: '',
            crew: [],
            plan: [],
            usedTools: [],
            ragSources: [],
            trace: [],
            memories: [],
          }
        )
      }
    }
  }
  if (buffer.trim()) {
    const parsed = parseSseFrame(buffer)
    if (parsed?.type === 'done' && parsed.run) {
      return parsed.run
    }
    if (parsed?.type === 'delta' && parsed.text) {
      reply += parsed.text
      await onDelta?.(parsed.text)
    }
  }
  if (doneEvent) {
    return doneEvent
  }
  if (reply) {
    return {
      sessionId: payload.sessionId,
      title: '',
      reply,
      mode: '',
      crew: [],
      plan: [],
      usedTools: [],
      ragSources: [],
      trace: [],
      memories: [],
    }
  }
  throw new Error('流式回答未完成')
}

function parseSseFrame(frame) {
  const dataLines = frame
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trim())
  if (!dataLines.length) {
    return null
  }
  try {
    return JSON.parse(dataLines.join('\n'))
  } catch {
    return null
  }
}

export function listAgentSessions() {
  return http.get('/ai/sessions')
}

export function createAgentSession() {
  return http.post('/ai/sessions')
}

export function getAgentSession(id) {
  return http.get(`/ai/sessions/${id}`)
}

export function deleteAgentSession(id) {
  return http.delete(`/ai/sessions/${id}`)
}

export function callMcp(payload) {
  return http.post('/ai/mcp', payload, { timeout: 60000 })
}
