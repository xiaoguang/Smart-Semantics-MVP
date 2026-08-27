import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'node:path';

export default defineConfig({
  root: resolve(import.meta.dirname, 'cp8-harness'),
  base: '/cp8/',
  plugins: [react()],
  build: {
    outDir: resolve(import.meta.dirname, '../../dist/cp8'),
    emptyOutDir: false,
  },
});
