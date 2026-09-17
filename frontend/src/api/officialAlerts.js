import http from './http'

export function getOfficialAlerts() {
  return http.get('/official-alerts', { timeout: 90000 })
}

export function getTropicalCyclones() {
  return http.get('/tropical-cyclones', { timeout: 45000 })
}
