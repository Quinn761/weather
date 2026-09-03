import http from './http'

export function listKnowledge(keyword) {
  return http.get('/kb/articles', { params: { keyword } })
}

export function getKnowledge(id) {
  return http.get(`/kb/articles/${id}`)
}

export function createKnowledge(payload) {
  return http.post('/kb/articles', payload)
}

export function updateKnowledge(id, payload) {
  return http.put(`/kb/articles/${id}`, payload)
}

export function deleteKnowledge(id) {
  return http.delete(`/kb/articles/${id}`)
}

export function searchKnowledge(q) {
  return http.get('/kb/search', { params: { q } })
}
