import path from 'node:path';
import { defineConfig, type UserConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// This independent development entry renders UI against fictional records.
// It never loads the real authentication provider, backend, or WebSocket client.
const root = path.resolve(import.meta.dirname, 'visual-preview');
const src = path.resolve(import.meta.dirname, 'src');
const forbidden = [
  'context/AuthContext.tsx', 'context/WebSocketContext.tsx',
  'services/api.ts', 'services/backend.ts', 'main.tsx', 'App.tsx',
  'context/ThemeContext.tsx', 'lib/dispatcherPreferences.ts',
].map((file) => path.join(src, file).replaceAll('\\', '/'));

export function visualPreviewConfig(base = '/'): UserConfig {
  return {
    root,
    base,
    publicDir: false,
    envDir: false,
    envPrefix: 'AGAP_VISUAL_PREVIEW_',
    plugins: [react(), tailwindcss(), {
      name: 'visual-preview-isolation',
      enforce: 'pre',
      load(id) {
        if (forbidden.includes(id.split('?')[0].replaceAll('\\', '/'))) {
          throw new Error('Real session and API modules are prohibited in the synthetic preview.');
        }
      },
    }],
    resolve: { alias: [
      { find: '@/context/AuthContext', replacement: path.join(root, 'identity.ts') },
      { find: '@/context/WebSocketContext', replacement: path.join(root, 'realtime.ts') },
      { find: '@/services/api', replacement: path.join(root, 'api.ts') },
      { find: '@/services/backend', replacement: path.join(root, 'backend.ts') },
      { find: '@/context/ThemeContext', replacement: path.join(root, 'theme.tsx') },
      { find: '@/lib/dispatcherPreferences', replacement: path.join(root, 'preferences.ts') },
      { find: '@', replacement: src },
    ] },
    server: {
      host: 'localhost',
      port: 4173,
      strictPort: true,
      fs: { allow: [import.meta.dirname] },
      headers: {
        'Content-Security-Policy': "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: https:; connect-src 'self' ws://localhost:4173 ws://127.0.0.1:4173; object-src 'none'; base-uri 'none'",
      },
    },
  };
}

export default defineConfig(({ command }) => {
  if (command !== 'serve') throw new Error('The synthetic visual preview is development-only.');
  return visualPreviewConfig();
});
