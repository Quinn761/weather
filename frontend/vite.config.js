import { fileURLToPath, URL } from 'node:url'
import { cpSync, existsSync, mkdirSync, rmSync } from 'node:fs'
import { resolve } from 'node:path'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

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

export default defineConfig(({ mode }) => ({
  define: {
    CESIUM_BASE_URL: JSON.stringify(mode === 'development' ? '/node_modules/cesium/Build/Cesium/' : '/cesium/'),
  },
  plugins: [vue(), ...(mode === 'development' ? [vueDevTools()] : []), copyCesiumAssets()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/uav-tiles': {
        target: 'http://60.205.211.104:8888',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/uav-tiles/, '/uav'),
      },
    },
  },
}))
