import http from './http'
import { readAgentStream } from '@/utils/agentStream'

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
  return readAgentStream(response.body, onDelta)
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
