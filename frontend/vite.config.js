import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// El proxy evita configurar la URL del backend en el codigo del cliente.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
});
