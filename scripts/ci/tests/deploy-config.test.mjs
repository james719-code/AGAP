import assert from 'node:assert/strict';
import test from 'node:test';
import { productionConfig, resolveVercelProject, validate, vercelName } from '../deploy-config.mjs';

const env = {
  AGAP_API_URL: 'https://agap-gateway.example.workers.dev/api/v1',
  AGAP_ALLOWED_ORIGINS: 'https://agap-bicol.vercel.app',
  SUPABASE_PROJECT_ID: 'abcdefghijklmnopqrst',
  VERCEL_TOKEN: 'test-only', VERCEL_ORG_ID: 'team_test',
};

test('production config derives Vercel project and validates gateway URL', () => {
  assert.equal(vercelName(env), 'agap-bicol');
  assert.equal(productionConfig(env).backend, 'supabase');
  assert.throws(() => productionConfig({ ...env, AGAP_API_URL: 'https://example.com' }), /api\/v1/);
  assert.throws(() => productionConfig({ ...env, AGAP_ALLOWED_ORIGINS: 'https://agap-bicol.vercel.app/' }), /trailing slash/);
});

test('deploy preflight rejects missing secrets only for affected provider', () => {
  assert.doesNotThrow(() => validate({ ...env, DEPLOY_WEB: 'true' }));
  assert.throws(() => validate({ ...env, DEPLOY_SUPABASE: 'true' }), /SUPABASE_ACCESS_TOKEN/);
  assert.throws(() => validate({ ...env, DEPLOY_CLOUDFLARE: 'true' }), /CLOUDFLARE_API_TOKEN/);
});

test('Supabase deploy requires matching project, application role, TLS CA, and gateway secret', () => {
  const hosted = {
    ...env, DEPLOY_SUPABASE: 'true', SUPABASE_ACCESS_TOKEN: 'test', SUPABASE_DB_PASSWORD: 'test',
    AGAP_GATEWAY_SECRET: 'a'.repeat(64), AGAP_POSTGRES_CA_PEM: '-----BEGIN CERTIFICATE-----\nexample\n-----END CERTIFICATE-----',
    AGAP_POSTGRES_URL: `postgresql://agap_api.${env.SUPABASE_PROJECT_ID}:test@pooler.example:6543/postgres`,
  };
  assert.doesNotThrow(() => validate(hosted));
  assert.throws(() => validate({ ...hosted, AGAP_POSTGRES_URL: hosted.AGAP_POSTGRES_URL.replace('agap_api.', 'postgres.') }), /dedicated agap_api/);
  assert.throws(() => validate({ ...hosted, AGAP_GATEWAY_SECRET: 'short' }), /at least 32/);
});

test('Vercel first deploy creates an unlinked project after a 404; retry reuses it', async () => {
  const calls = [];
  const project = { id: 'prj_test123', name: 'agap-bicol' };
  const request = async (url, init) => {
    calls.push({ url, init });
    return calls.length === 1 ? new Response(null, { status: 404 }) : Response.json(project);
  };
  assert.deepEqual(await resolveVercelProject(env, request), project);
  assert.equal(calls[1].init.method, 'POST');
  assert.deepEqual(JSON.parse(calls[1].init.body), { name: 'agap-bicol', framework: 'vite' });
  assert.deepEqual(await resolveVercelProject(env, async () => Response.json(project)), project);
});

test('Vercel authorization errors and missing configured IDs never create projects', async () => {
  for (const [settings, status] of [[env, 401], [{ ...env, VERCEL_PROJECT_ID: 'prj_missing' }, 404]]) {
    const calls = [];
    await assert.rejects(resolveVercelProject(settings, async (_url, init) => {
      calls.push(init);
      return new Response(null, { status });
    }), /lookup\/create failed/);
    assert.equal(calls.length, 1);
  }
});
