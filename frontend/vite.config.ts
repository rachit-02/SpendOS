/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

// The dev proxy target is configurable because port 8080 may be taken on developer machines.
const apiTarget = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [react()],

  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },

  server: {
    port: 3000,
    strictPort: false,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
      },
    },
  },

  esbuild: mode === 'production' ? { drop: ['console', 'debugger'] } : undefined,

  build: {
    // Also the outputDirectory in vercel.json and what frontend/Dockerfile copies into nginx.
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'chart-vendor': ['recharts'],
          'query-vendor': ['@tanstack/react-query'],
        },
      },
    },
  },

  preview: {
    port: 3000,
    strictPort: false,
  },

  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.test.{ts,tsx}'],
    testTimeout: 15000,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/test/**', 'src/**/*.test.{ts,tsx}', 'src/main.tsx', 'src/vite-env.d.ts'],
      // DEVELOPMENT_PLAN.md Phase 16: frontend coverage 70%+
      thresholds: { lines: 70, statements: 70 },
    },
  },
}))
