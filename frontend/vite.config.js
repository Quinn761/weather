import { execFileSync } from 'node:child_process'
import { fileURLToPath, URL } from 'node:url'
import { cpSync, existsSync, mkdirSync, rmSync } from 'node:fs'
import http from 'node:http'
import tls from 'node:tls'
import { resolve } from 'node:path'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

const ROBOFLOW_HOST = 'serverless.roboflow.com'

function envProxyUrl() {
  return process.env.HTTPS_PROXY || process.env.https_proxy || process.env.HTTP_PROXY || process.env.http_proxy || ''
}

function windowsSystemProxyUrl() {
  if (process.platform !== 'win32') return ''
  try {
    const enableOut = execFileSync(
      'reg',
      ['query', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings', '/v', 'ProxyEnable'],
      { encoding: 'utf8', timeout: 4000 },
    )
    if (!/ProxyEnable\s+REG_DWORD\s+0x1\b/i.test(enableOut)) return ''
    const serverOut = execFileSync(
      'reg',
      ['query', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings', '/v', 'ProxyServer'],
      { encoding: 'utf8', timeout: 4000 },
    )
    const match = serverOut.match(/ProxyServer\s+REG_SZ\s+(.+)/i)
    if (!match) return ''
    const raw = match[1].trim()
    const httpsPart = raw.split(';').find((item) => item.toLowerCase().startsWith('https='))
    const httpPart = raw.split(';').find((item) => item.toLowerCase().startsWith('http='))
    const picked = (httpsPart || httpPart || raw).replace(/^https?=/i, '')
    if (!picked || picked.toLowerCase().startsWith('socks')) return ''
    return picked.includes('://') ? picked : `http://${picked}`
  } catch {
    return ''
  }
}

function parseHttpProxy(proxyUrl) {
  if (!proxyUrl) return null
  try {
    const parsed = new URL(proxyUrl.includes('://') ? proxyUrl : `http://${proxyUrl}`)
    const port = Number(parsed.port || 80)
    return { hostname: parsed.hostname, port, label: `${parsed.hostname}:${port}` }
  } catch {
    return null
  }
}

function candidateProxies() {
  const list = []
  const seen = new Set()
  for (const url of [envProxyUrl(), windowsSystemProxyUrl(), process.platform === 'win32' ? 'http://127.0.0.1:7890' : '']) {
    const parsed = parseHttpProxy(url)
    if (!parsed || seen.has(parsed.label)) continue
    seen.add(parsed.label)
    list.push(parsed)
  }
  return list
}

function decodeChunked(buf) {
  const parts = []
  let offset = 0
  while (offset < buf.length) {
    const lineEnd = buf.indexOf('\r\n', offset)
    if (lineEnd < 0) break
    const size = parseInt(buf.subarray(offset, lineEnd).toString('ascii').split(';')[0], 16)
    if (!Number.isFinite(size) || size === 0) break
    const start = lineEnd + 2
    parts.push(buf.subarray(start, start + size))
    offset = start + size + 2
  }
  return Buffer.concat(parts)
}

function parseHttpResponse(buf) {
  const sep = buf.indexOf('\r\n\r\n')
  if (sep < 0) throw new Error('Roboflow 响应不完整')
  const headerText = buf.subarray(0, sep).toString('latin1')
  const status = Number(/^HTTP\/\d(?:\.\d)?\s+(\d{3})/i.exec(headerText)?.[1] || 502)
  const headers = {}
  for (const line of headerText.split('\r\n').slice(1).filter(Boolean)) {
    const index = line.indexOf(':')
    if (index < 0) continue
    headers[line.slice(0, index).trim().toLowerCase()] = line.slice(index + 1).trim()
  }
  let body = buf.subarray(sep + 4)
  if ((headers['transfer-encoding'] || '').toLowerCase().includes('chunked')) {
    body = decodeChunked(body)
  }
  return { statusCode: status, contentType: headers['content-type'] || 'application/json', body }
}

function postThroughHttpProxy(proxy, path, headers, body) {
  return new Promise((resolve, reject) => {
    const connectReq = http.request({
      host: proxy.hostname,
      port: proxy.port,
      method: 'CONNECT',
      path: `${ROBOFLOW_HOST}:443`,
      headers: { Host: `${ROBOFLOW_HOST}:443` },
      timeout: 8000,
    })
    connectReq.once('connect', (res, socket) => {
      if (res.statusCode !== 200) {
        socket.destroy()
        reject(new Error(`本机代理 CONNECT ${res.statusCode}`))
        return
      }
      const tlsSocket = tls.connect({ socket, servername: ROBOFLOW_HOST }, () => {
        const requestHeaders = {
          Host: ROBOFLOW_HOST,
          Accept: 'application/json',
          'Content-Type': headers['content-type'] || 'application/json',
          'Content-Length': String(body.length),
          Connection: 'close',
          'Accept-Encoding': 'identity',
          'User-Agent': headers['user-agent'] || 'Mozilla/5.0',
        }
        if (headers.authorization) requestHeaders.Authorization = headers.authorization
        const head =
          `POST ${path} HTTP/1.1\r\n` +
          Object.entries(requestHeaders)
            .map(([key, value]) => `${key}: ${value}`)
            .join('\r\n') +
          '\r\n\r\n'
        const chunks = []
        tlsSocket.on('data', (chunk) => chunks.push(chunk))
        tlsSocket.once('error', reject)
        tlsSocket.once('end', () => {
          try {
            resolve(parseHttpResponse(Buffer.concat(chunks)))
          } catch (error) {
            reject(error)
          }
        })
        tlsSocket.setTimeout(180000, () => {
          tlsSocket.destroy()
          reject(new Error('Roboflow 代理超时'))
        })
        tlsSocket.write(head)
        tlsSocket.write(body)
      })
      tlsSocket.once('error', reject)
    })
    connectReq.once('error', (error) => reject(new Error(`无法连接本机代理 ${proxy.label}：${error.message}`)))
    connectReq.once('timeout', () => {
      connectReq.destroy()
      reject(new Error(`本机代理 ${proxy.label} CONNECT 超时`))
    })
    connectReq.end()
  })
}

function roboflowProxyPlugin() {
  const proxies = candidateProxies()
  return {
    name: 'roboflow-proxy',
    configureServer(server) {
      if (proxies.length) {
        server.config.logger.info(`[roboflow-proxy] ${proxies.map((item) => item.label).join(', ')}`)
      }
      server.middlewares.use((req, res, next) => {
        if (!req.url?.startsWith('/rf-api') || req.method !== 'POST') {
          if (req.url?.startsWith('/rf-api') && req.method === 'OPTIONS') {
            res.statusCode = 204
            res.end()
            return
          }
          next()
          return
        }
        const chunks = []
        req.on('data', (chunk) => chunks.push(chunk))
        req.on('end', async () => {
          const body = Buffer.concat(chunks)
          const path = req.url.slice('/rf-api'.length) || '/'
          const headers = {
            'content-type': req.headers['content-type'] || 'application/json',
            authorization: req.headers.authorization,
            'user-agent': req.headers['user-agent'],
          }
          try {
            let lastError = new Error('未检测到系统代理，无法转发 Roboflow')
            for (const proxy of proxies) {
              try {
                const result = await postThroughHttpProxy(proxy, path, headers, body)
                res.statusCode = result.statusCode
                res.setHeader('Content-Type', result.contentType)
                res.end(result.body)
                return
              } catch (error) {
                lastError = error
              }
            }
            res.statusCode = 502
            res.setHeader('Content-Type', 'text/plain; charset=utf-8')
            res.end(`Roboflow 代理失败：${lastError.message}`)
          } catch (error) {
            res.statusCode = 502
            res.setHeader('Content-Type', 'text/plain; charset=utf-8')
            res.end(`Roboflow 代理失败：${error.message}`)
          }
        })
      })
    },
  }
}

function copyCesiumAssets() {
  return {
    name: 'copy-cesium-assets',
    closeBundle() {
      const cesiumRoot = resolve('node_modules/cesium/Build/Cesium')
      const targetRoot = resolve('dist/cesium')
      if (!existsSync(cesiumRoot)) return
      rmSync(targetRoot, { recursive: true, force: true })
      mkdirSync(targetRoot, { recursive: true })
      for (const dir of ['Assets', 'ThirdParty', 'Workers', 'Widgets']) {
        cpSync(resolve(cesiumRoot, dir), resolve(targetRoot, dir), { recursive: true })
      }
    },
  }
}

export default defineConfig(({ mode }) => {
  const envDir = fileURLToPath(new URL('..', import.meta.url))
  const env = loadEnv(mode, envDir, '')
  // Local Compose uses ROBOFLOW_API_KEY while browser code reads a Vite-style
  // variable. Map it only for the local development bundle; production uses
  // the runtime /env.js generated by the Nginx entrypoint.
  const roboflowApiKey = env.VITE_ROBOFLOW_API_KEY || env.ROBOFLOW_API_KEY || ''

  return {
  envDir,
  define: {
    'import.meta.env.VITE_ROBOFLOW_API_KEY': JSON.stringify(roboflowApiKey),
    CESIUM_BASE_URL: JSON.stringify(mode === 'development' ? '/node_modules/cesium/Build/Cesium/' : '/cesium/'),
  },
  plugins: [roboflowProxyPlugin(), vue(), ...(mode === 'development' ? [vueDevTools()] : []), copyCesiumAssets()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        timeout: 180000,
        proxyTimeout: 180000,
      },
      '/uav-tiles': {
        target: 'http://60.205.211.104:8888',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/uav-tiles/, '/uav'),
      },
    },
  },
  }
})
