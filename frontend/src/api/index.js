import http from './http'

export function getHealth() {
  return http.get('/health')
}

export function getOverview() {
  return http.get('/dashboard/overview')
}
