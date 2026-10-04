import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 后端接口代理：前端统一请求 /api，转发到 Spring Boot
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // 大依赖单独分包，减小首屏 chunk
        manualChunks: {
          echarts: ['echarts'],
          xlsx: ['xlsx'],
          jspdf: ['jspdf'],
        },
      },
    },
  },
})
