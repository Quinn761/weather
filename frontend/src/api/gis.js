import http from './http'

const DEFAULT_WORKFLOW_URL = 'https://serverless.roboflow.com/666s-workspace-l0hrj/workflows/1789032615331'

export function listGisFeatures() {
  return http.get('/gis/features')
}

export function createGisFeature(data) {
  return http.post('/gis/features', data)
}

export function deleteGisFeature(id) {
  return http.delete(`/gis/features/${id}`)
}

export function runSam2Delineation(data) {
  return http.post('/gis/delineate/sam2', data, { timeout: 170000 })
}

export async function runRoboflowWorkflow({ imageBase64, corners }) {
  const apiKey = roboflowApiKey()
  if (!apiKey) {
    throw new Error('未配置 Roboflow API Key：本地写入 .env 的 VITE_ROBOFLOW_API_KEY，线上在 GitHub Secrets 设置 ROBOFLOW_API_KEY')
  }
  if (!imageBase64) {
    throw new Error('缺少瓦片图片')
  }
  if (!corners) {
    throw new Error('缺少瓦片四角坐标')
  }
  const response = await fetch(workflowFetchUrl(), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      api_key: apiKey,
      inputs: {
        image: { type: 'base64', value: imageBase64 },
        top_left_lon: fmtCoord(corners.topLeft[0]),
        top_left_lat: fmtCoord(corners.topLeft[1]),
        top_right_lon: fmtCoord(corners.topRight[0]),
        top_right_lat: fmtCoord(corners.topRight[1]),
        bottom_right_lon: fmtCoord(corners.bottomRight[0]),
        bottom_right_lat: fmtCoord(corners.bottomRight[1]),
        bottom_left_lon: fmtCoord(corners.bottomLeft[0]),
        bottom_left_lat: fmtCoord(corners.bottomLeft[1]),
      },
    }),
  }).catch(() => {
    throw new Error('无法连接 Roboflow')
  })
  const text = await response.text()
  if (!response.ok) {
    throw new Error(describeRoboflowError(response.status, text, {
      cfRay: response.headers.get('x-roboflow-cf-ray'),
      upstreamStatus: response.headers.get('x-roboflow-upstream-status'),
      occurredAt: response.headers.get('x-roboflow-occurred-at'),
    }))
  }
  try {
    return JSON.parse(text)
  } catch {
    throw new Error('Roboflow 返回了无法解析的结果')
  }
}

function roboflowApiKey() {
  const runtime = typeof window !== 'undefined'
    ? String(window.__APP_CONFIG__?.roboflowApiKey || '').trim()
    : ''
  return runtime || String(import.meta.env.VITE_ROBOFLOW_API_KEY || '').trim()
}

function workflowFetchUrl() {
  const raw = DEFAULT_WORKFLOW_URL
  try {
    const parsed = new URL(raw)
    if (parsed.hostname === 'serverless.roboflow.com') {
      return `/rf-api${parsed.pathname}`
    }
  } catch {
    return raw
  }
  return raw
}

function describeRoboflowError(status, text, diagnostics = {}) {
  const trace = [
    diagnostics.upstreamStatus ? `上游状态 ${diagnostics.upstreamStatus}` : '',
    diagnostics.cfRay ? `CF-Ray: ${diagnostics.cfRay}` : '',
    diagnostics.occurredAt ? `发生时间（UTC）: ${diagnostics.occurredAt}` : '',
  ].filter(Boolean).join('；')
  const traceSuffix = trace ? `（${trace}）` : ''
  if (text?.startsWith('Roboflow 代理')) return text
  try {
    const json = JSON.parse(text)
    return `${json.message || json.error || `HTTP ${status}`}${traceSuffix}`
  } catch {
    if (/<!doctype html|<html/i.test(text || '')) {
      if (status === 403 && /cloudflare|you have been blocked/i.test(text)) {
        return `Roboflow 的 Cloudflare 安全策略拦截了服务器请求（HTTP 403）${traceSuffix}。请将 CF-Ray 和报错时间提供给 Roboflow 支持，以核查具体拦截规则。`
      }
      return `Roboflow 返回了网页而不是识别结果（HTTP ${status}）${traceSuffix}`
    }
    return `HTTP ${status}${traceSuffix}`
  }
}

function fmtCoord(value) {
  return Number(value).toFixed(8)
}
