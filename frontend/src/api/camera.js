import http from './http'

export function listCameraDevices() {
  return http.get('/cameras')
}

export function createCameraDevice(data) {
  return http.post('/cameras', data)
}

export function updateCameraDevice(id, data) {
  return http.put(`/cameras/${id}`, data)
}

export function deleteCameraDevice(id) {
  return http.delete(`/cameras/${id}`)
}

export function listCameraSnapshots(id, current = 1, size = 10) {
  return http.get(`/cameras/${id}/snapshots`, { params: { current, size } })
}

export function getCameraSnapshotImage(id) {
  return http.get(`/cameras/snapshots/${id}/image`, { responseType: 'blob' })
}

export function monitorCameraSnapshot(id) {
  return http.post(`/cameras/snapshots/${id}/monitoring`)
}

export function listCameraMonitoringRecords(id, current = 1, size = 10) {
  return http.get(`/cameras/${id}/monitoring-records`, { params: { current, size } })
}

export function getCameraMonitoringRecord(id) {
  return http.get(`/cameras/monitoring-records/${id}`)
}
