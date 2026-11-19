import { appendFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

function requireSetting(env, key) {
  if (!env[key]?.trim()) throw new Error(`Missing Actions setting ${key}`);
  return env[key];
}

function httpsUrl(value, key) {
  const url = new URL(value);
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) throw new Error(`Invalid ${key}`);
  return url;
}

export function productionConfig(env) {
  const api = httpsUrl(requireSetting(env, 'AGAP_API_URL'), 'AGAP_API_URL');
  if (api.pathname !== '/api/v1') throw new Error('AGAP_API_URL must end in /api/v1');
  const origins = requireSetting(env, 'AGAP_ALLOWED_ORIGINS').split(',').map(s => s.trim());
  for (const origin of origins) {
    if (httpsUrl(origin, 'AGAP_ALLOWED_ORIGINS').origin !== origin) throw new Error('Allowed origins must not have a path or trailing slash');
  }
  const backend = env.AGAP_BACKEND || 'supabase';
  if (!['supabase', 'firestore', 'maintenance'].includes(backend)) throw new Error('Invalid AGAP_BACKEND');
  if (backend === 'firestore') httpsUrl(requireSetting(env, 'AGAP_FIRESTORE_ORIGIN'), 'AGAP_FIRESTORE_ORIGIN');
  const project = requireSetting(env, 'SUPABASE_PROJECT_ID');
  if (!/^[a-z0-9]{20}$/.test(project)) throw new Error('Invalid SUPABASE_PROJECT_ID');
  return { api, origins, backend, project, firestore: env.AGAP_FIRESTORE_ORIGIN || '' };
}

export function validate(env) {
  const config = productionConfig(env);
  if (env.DEPLOY_SUPABASE === 'true') {
    for (const key of ['SUPABASE_ACCESS_TOKEN', 'SUPABASE_DB_PASSWORD', 'AGAP_GATEWAY_SECRET', 'AGAP_POSTGRES_URL', 'AGAP_POSTGRES_CA_PEM']) requireSetting(env, key);
    const db = new URL(env.AGAP_POSTGRES_URL);
    if (!['postgres:', 'postgresql:'].includes(db.protocol) || !db.password || db.username !== `agap_api.${config.project}` || db.port !== '6543' || db.pathname !== '/postgres') {
      throw new Error('AGAP_POSTGRES_URL must use the dedicated agap_api transaction pooler login for this project');
    }
    if (!env.AGAP_POSTGRES_CA_PEM.includes('-----BEGIN CERTIFICATE-----')) throw new Error('Invalid AGAP_POSTGRES_CA_PEM');
  }
  if (env.DEPLOY_CLOUDFLARE === 'true') {
    for (const key of ['CLOUDFLARE_API_TOKEN', 'CLOUDFLARE_ACCOUNT_ID', 'AGAP_GATEWAY_SECRET']) requireSetting(env, key);
    if (!/^[a-f0-9]{32}$/i.test(env.CLOUDFLARE_ACCOUNT_ID)) throw new Error('Invalid CLOUDFLARE_ACCOUNT_ID');
  }
  if ((env.DEPLOY_SUPABASE === 'true' || env.DEPLOY_CLOUDFLARE === 'true') && env.AGAP_GATEWAY_SECRET.length < 32) {
    throw new Error('AGAP_GATEWAY_SECRET must be at least 32 characters');
  }
  if (env.DEPLOY_WEB === 'true') {
    for (const key of ['VERCEL_TOKEN', 'VERCEL_ORG_ID']) requireSetting(env, key);
    if (env.AGAP_API_PROVIDER && env.AGAP_API_PROVIDER !== 'cloudflare') throw new Error('Production web must use Cloudflare');
    vercelName(env, config);
  }
  return config;
}

export function gatewayConfig(env) {
  const config = productionConfig(env);
  const base = readFileSync('wrangler.gateway.toml', 'utf8').split(/^\[vars\]/m)[0];
  const vars = {
    AGAP_BACKEND: config.backend,
    AGAP_ALLOWED_ORIGINS: config.origins.join(','),
    AGAP_SUPABASE_ORIGIN: `https://${config.project}.supabase.co/functions/v1/agap-postgres`,
    AGAP_FIRESTORE_ORIGIN: config.firestore,
  };
  writeFileSync('wrangler.gateway.local.toml', base + '[vars]\n' + Object.entries(vars).map(([key, value]) => `${key} = ${JSON.stringify(value)}\n`).join(''));
  writeFileSync(join(requireSetting(env, 'RUNNER_TEMP'), 'agap-worker-secrets.json'), JSON.stringify({ AGAP_GATEWAY_SECRET: requireSetting(env, 'AGAP_GATEWAY_SECRET') }), { mode: 0o600 });
}

export function supabaseSecrets(env) {
  const keys = ['AGAP_GATEWAY_SECRET', 'AGAP_POSTGRES_URL', 'AGAP_POSTGRES_CA_PEM'];
  const data = keys.map(key => `${key}=${JSON.stringify(requireSetting(env, key))}`).join('\n') + '\n';
  writeFileSync(join(requireSetting(env, 'RUNNER_TEMP'), 'agap-supabase.env'), data, { mode: 0o600 });
}

export function vercelName(env, config = productionConfig(env)) {
  const origin = config.origins.find(s => s.endsWith('.vercel.app'));
  const name = env.VERCEL_PROJECT_NAME || (origin && new URL(origin).hostname.replace(/\.vercel\.app$/, ''));
  if (!name || !/^[a-z0-9][a-z0-9-]{0,99}$/.test(name)) throw new Error('Set VERCEL_PROJECT_NAME or a valid Vercel origin');
  return name;
}

export async function resolveVercelProject(env, request = fetch) {
  const name = vercelName(env);
  const org = requireSetting(env, 'VERCEL_ORG_ID');
  const headers = { Authorization: `Bearer ${requireSetting(env, 'VERCEL_TOKEN')}`, 'Content-Type': 'application/json' };
  const endpoint = (path) => `https://api.vercel.com${path}?teamId=${encodeURIComponent(org)}`;
  const idOrName = env.VERCEL_PROJECT_ID || name;
  let response = await request(endpoint(`/v9/projects/${encodeURIComponent(idOrName)}`), { headers });
  if (response.status === 404 && !env.VERCEL_PROJECT_ID) {
    response = await request(endpoint('/v11/projects'), { method: 'POST', headers, body: JSON.stringify({ name, framework: 'vite' }) });
  }
  if (!response.ok) throw new Error(`Vercel project lookup/create failed (HTTP ${response.status})`);
  const project = await response.json();
  if (!/^prj_[a-zA-Z0-9]+$/.test(project.id) || project.name !== name) throw new Error('Vercel project does not match expected project');
  return project;
}

export async function packageVercel(env) {
  const project = await resolveVercelProject(env);
  mkdirSync('.vercel/output', { recursive: true });
  writeFileSync('.vercel/project.json', JSON.stringify({ projectId: project.id, orgId: env.VERCEL_ORG_ID, projectName: project.name }));
  writeFileSync('.vercel/output/config.json', JSON.stringify({ version: 3, routes: [{ handle: 'filesystem' }, { src: '/(.*)', dest: '/index.html' }] }));
  if (env.GITHUB_STEP_SUMMARY) appendFileSync(env.GITHUB_STEP_SUMMARY, `### Vercel project\n\nProject ID: \`${project.id}\`\n\nExpected frontend: https://${project.name}.vercel.app\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    switch (process.argv[2]) {
      case 'validate': validate(process.env); break;
      case 'gateway': gatewayConfig(process.env); break;
      case 'supabase': supabaseSecrets(process.env); break;
      case 'vercel': await packageVercel(process.env); break;
      default: throw new Error('Unknown deployment configuration command');
    }
  } catch (error) {
    console.error(`::error::${error.message}`);
    process.exitCode = 1;
  }
}
