import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: '/orth-admin/',
  server: {
    port: 5173,
    proxy: {
      '/orth-admin/api': {
        target: 'http://localhost:18080',
        changeOrigin: true,
      },
    },
  },
});
