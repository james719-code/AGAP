const providers = ['go', 'cloudflare', 'supabase', 'firebase'] as const;
type BackendProvider = typeof providers[number];

const provider = (import.meta.env.VITE_API_PROVIDER || 'go') as BackendProvider;
if (!providers.includes(provider)) {
  throw new Error('VITE_API_PROVIDER must be go, cloudflare, supabase, or firebase.');
}

export const usesHostedApi = provider !== 'go';
export const hostedRefreshMs = 15_000;

const configuredBase = import.meta.env.VITE_API_BASE_URL?.trim();
if (usesHostedApi && !configuredBase) {
  throw new Error('Set VITE_API_BASE_URL to the deployed function URL, including /api/v1.');
}

export const API_BASE_URL = (configuredBase || '/agap/api/v1').replace(/\/+$/, '');
export const API_HEALTH_URL = `${API_BASE_URL.replace(/\/api\/v1$/, '')}/health`;
const sessionSuffix = usesHostedApi ? `:${provider}:${API_BASE_URL}` : '';
export const sessionKeys = {
  access: `agap_auth_token${sessionSuffix}`,
  refresh: `agap_refresh_token${sessionSuffix}`,
  user: `agap_user${sessionSuffix}`,
};

if (usesHostedApi && !API_BASE_URL.endsWith('/api/v1')) {
  throw new Error('The hosted API URL must end in /api/v1.');
}

export function apiHeaders(token?: string | null, extra?: HeadersInit) {
  const headers = new Headers(extra);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const publishableKey = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY?.trim();
  if (provider === 'supabase' && publishableKey) headers.set('apikey', publishableKey);
  return headers;
}
