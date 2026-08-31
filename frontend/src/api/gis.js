import http from './http'

export function listGisFeatures() {
  return http.get('/gis/features')
}

export function createGisFeature(data) {
  return http.post('/gis/features', data)
}

export function deleteGisFeature(id) {
  return http.delete(`/gis/features/${id}`)
}
