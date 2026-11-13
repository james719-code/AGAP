import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { createServer } from 'vite';

test('the Android download dialog links to the shared release APK and release history', async () => {
  const config = JSON.parse(readFileSync(new URL('../src/config/android-release.json', import.meta.url), 'utf8'));
  const server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } });
  try {
    const { DownloadDialog } = await server.ssrLoadModule('/src/components/landing/DownloadDialog.tsx');
    const html = renderToStaticMarkup(createElement(MemoryRouter, null,
      createElement(DownloadDialog, { open: true, onOpenChange() {} })));
    const base = `https://github.com/${config.repository}/releases`;
    assert.ok(html.includes(`href="${base}/latest/download/${config.assetName}"`));
    assert.ok(html.includes('Download Android APK'));
    assert.ok(html.includes(`href="${base}"`));
    assert.ok(html.includes('Android test build'));
  } finally {
    await server.close();
  }
});
