import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    port: 5173,
    host: true,
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8076',
        changeOrigin: true,
        secure: false,
        ws: true,
        logLevel: 'debug'
      },
      // 2026-10-02（W3.2）：`/ws` 必须单独代理 —— `src/utils/stompClient.js` 连的是
      // `ws://${window.location.host}/ws`，开发态就是 `ws://localhost:5173/ws`。
      // 上面那条只代理 `/api`，所以**这个 WebSocket 从来没能连上过**：vite 自己
      // 接不住 `/ws`，请求也不会转到网关。症状是 STOMP 一直重连、页面永远收不到推送。
      // `ws: true` 是必须的（WebSocket 升级请求要走这条）。
      '/ws': {
        target: 'ws://localhost:8076',
        ws: true,
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets'
  }
})
