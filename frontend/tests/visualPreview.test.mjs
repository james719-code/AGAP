import assert from 'node:assert/strict';
import test from 'node:test';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { readFile } from 'node:fs/promises';

test('visual preview serves fictional records without authentication or mutations', async () => {
  const server = await createServer({
    configFile: fileURLToPath(new URL('../vite.visual.config.ts', import.meta.url)),
    server: { middlewareMode: true, hmr: false, watch: null },
  });
  try {
    const { apiService } = await server.ssrLoadModule('/api.ts');
    const { useAuth } = await server.ssrLoadModule('/identity.ts');
    assert.equal(useAuth().isAuthenticated, false);
    assert.equal(useAuth().user.email, 'dispatcher@example.invalid');
    const result = await apiService.getIncidents({ limit: 10, offset: 10 });
    assert.equal(result.total, 29);
    assert.equal(result.incidents.length, 10);
    assert.ok(result.incidents.every((item) => item.incident_uuid.startsWith('fixture-')));
    await assert.rejects(apiService.createDispatch(801, 501), /read-only synthetic preview/);
    await assert.rejects(apiService.updateUserStatus(9500, 'APPROVED'), /read-only synthetic preview/);
    await assert.rejects(useAuth().login('demo', 'demo'), /not part of this independent/);
    assert.ok(await server.transformRequest('/main.tsx'));
    assert.equal(server.config.envDir, false);
    assert.equal(server.config.server.host, 'localhost');
  } finally {
    await server.close();
  }
  const app = await readFile(new URL('../src/App.tsx', import.meta.url), 'utf8');
  assert.match(app, /if \(!isAuthenticated\)/);
  assert.match(app, /<ProtectedRoute>/);
});
