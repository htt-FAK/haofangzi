import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 演示口径：dev 时把 /api 转向本地后端；构建产物可由 Spring Boot 静态目录托管（同源部署，见 docs/03 §1.4）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
  build: {
    target: 'es2020',
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: { manualChunks: { three: ['three'], echarts: ['echarts'], element: ['element-plus'] } },
    },
  },
})
