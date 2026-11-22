import { build, type Plugin } from 'vite';
import { visualPreviewConfig } from '../vite.visual.config.ts';

export const VISUAL_PREVIEW_PATH = '/agap/__visual-preview/';
const policy = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: blob: https:; connect-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'";
type PreviewAsset = { body: string | Uint8Array; type: string };
const mime: Record<string, string> = { html: 'text/html; charset=utf-8', js: 'text/javascript; charset=utf-8', css: 'text/css; charset=utf-8', svg: 'image/svg+xml', png: 'image/png', webp: 'image/webp', jpg: 'image/jpeg', woff2: 'font/woff2' };

// Build in memory only. The ordinary production output and real session/API
// modules are never part of this independent fixture's module graph.
export async function bundleVisualPreview(): Promise<Map<string, PreviewAsset>> {
  const config = visualPreviewConfig(VISUAL_PREVIEW_PATH);
  const result = await build({ ...config, configFile: false, build: { write: false, sourcemap: false, chunkSizeWarningLimit: 2000 }, logLevel: 'warn' });
  const outputs = Array.isArray(result) ? result : [result];
  const assets = new Map<string, PreviewAsset>();
  for (const output of outputs) {
    if (!('output' in output)) throw new Error('Visual preview did not produce a bundle.');
    for (const item of output.output) {
      assets.set(item.fileName, { body: item.type === 'chunk' ? item.code : item.source, type: mime[item.fileName.split('.').pop() ?? ''] ?? 'application/octet-stream' });
    }
  }
  if (!assets.has('index.html')) throw new Error('Visual preview has no HTML entry.');
  return assets;
}

export function syntheticVisualPreview(): Plugin {
  return {
    name: 'agap-synthetic-visual-preview',
    apply: 'serve',
    configureServer(server) {
      let bundle: Promise<Map<string, PreviewAsset>> | undefined;
      server.watcher.on('change', () => { bundle = undefined; });
      server.middlewares.use(async (req, res, next) => {
        const pathname = new URL(req.url || '/', 'http://localhost').pathname;
        if (!pathname.startsWith(VISUAL_PREVIEW_PATH)) return next();
        const remote = req.socket.remoteAddress;
        if (remote !== '127.0.0.1' && remote !== '::1' && remote !== '::ffff:127.0.0.1') {
          res.statusCode = 403;
          return res.end('The synthetic preview is restricted to the local development machine.');
        }
        if (req.method !== 'GET' && req.method !== 'HEAD') {
          res.statusCode = 405;
          return res.end('The synthetic preview is read-only.');
        }
        try {
          const assets = await (bundle ??= bundleVisualPreview());
          const relative = pathname.slice(VISUAL_PREVIEW_PATH.length);
          const asset = assets.get(relative) ?? (!relative.includes('.') ? assets.get('index.html') : undefined);
          if (!asset) { res.statusCode = 404; return res.end('Preview asset not found.'); }
          res.setHeader('Content-Type', asset.type);
          res.setHeader('Cache-Control', 'no-store');
          res.setHeader('Content-Security-Policy', policy);
          res.setHeader('X-Content-Type-Options', 'nosniff');
          res.end(req.method === 'HEAD' ? undefined : asset.body);
        } catch (error) {
          bundle = undefined;
          console.error('Synthetic preview build failed:', error);
          res.statusCode = 500;
          res.end('Unable to generate the synthetic visual preview. Check the Vite terminal.');
        }
      });
    },
  };
}
