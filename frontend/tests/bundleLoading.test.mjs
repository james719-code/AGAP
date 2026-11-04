import assert from 'node:assert/strict';
import { gzipSync } from 'node:zlib';
import test from 'node:test';
import { build } from 'vite';

test('public routes keep maps and optional 3D code behind lazy boundaries', async () => {
  const result = await build({
    logLevel: 'silent',
    build: { write: false, reportCompressedSize: false },
  });
  const chunks = result.output.filter((item) => item.type === 'chunk');
  const byFile = new Map(chunks.map((chunk) => [chunk.fileName, chunk]));
  const entry = chunks.find((chunk) => chunk.isEntry);
  assert.ok(entry, 'Missing application entry');

  const staticDependencies = (root) => {
    const visited = new Set();
    const visit = (chunk) => {
      if (visited.has(chunk)) return;
      visited.add(chunk);
      for (const file of chunk.imports) {
        const dependency = byFile.get(file);
        if (dependency) visit(dependency);
      }
    };
    visit(root);
    return [...visited];
  };
  const modules = (dependencies) => dependencies.flatMap((chunk) => Object.keys(chunk.modules));
  const entryDependencies = staticDependencies(entry);
  console.log(JSON.stringify({
    entryJavaScriptBytes: entryDependencies.reduce((sum, chunk) => sum + Buffer.byteLength(chunk.code), 0),
    entryGzipBytes: entryDependencies.reduce((sum, chunk) => sum + gzipSync(chunk.code).length, 0),
  }));

  const isMapModule = (id) => /node_modules\/(?:leaflet|react-leaflet|@react-leaflet)\//.test(id.replaceAll('\\', '/'));
  const is3DModule = (id) => /node_modules\/(?:three|@react-three)\//.test(id.replaceAll('\\', '/'));
  assert.equal(modules(entryDependencies).some(isMapModule), false, 'Maps loaded before opening a map');
  assert.equal(modules(entryDependencies).some(is3DModule), false, '3D code loaded with the application entry');

  for (const suffix of ['/pages/LandingPage.tsx', '/pages/LoginPage.tsx', '/components/Beams/Beams.tsx']) {
    const route = chunks.find((chunk) => Object.keys(chunk.modules).some((id) => id.replaceAll('\\', '/').endsWith(suffix)));
    assert.ok(route, `Missing chunk for ${suffix}`);
    assert.equal(modules(staticDependencies(route)).some(isMapModule), false, `Maps loaded by ${suffix}`);
    assert.equal(modules(staticDependencies(route)).some(is3DModule), false, `Optional 3D code loaded by ${suffix}`);
  }
});
