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
