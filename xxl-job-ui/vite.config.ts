import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: '/xxl-job-admin/',
  server: {
    port: 5173,
    proxy: {
      '/xxl-job-admin/api': {
        target: 'http://localhost:18080',
        changeOrigin: true,
      },
    },
  },
});
