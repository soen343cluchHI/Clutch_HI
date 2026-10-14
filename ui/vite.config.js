import { defineConfig } from 'vite';

export default defineConfig({
  esbuild: { jsx: 'automatic' },
  server: {
    proxy: {
      '/api': {
        target: process.env.SUGARRUSH_API_URL || 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
});