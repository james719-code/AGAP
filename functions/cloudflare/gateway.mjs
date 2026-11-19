const methods = ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'];

export function createGateway(upstreamFetch = fetch) {
  return async (request, env) => {
    const url = new URL(request.url);
    const origin = request.headers.get('origin');
    const allowedOrigins = new Set((env.AGAP_ALLOWED_ORIGINS ?? '').split(',').map((value) => value.trim()));
    const headers = new Headers({ 'Cache-Control': 'no-store', Vary: 'Origin', 'X-Content-Type-Options': 'nosniff' });
    const error = (status, message) => Response.json({ error: message }, { status, headers });
    if (origin) {
      if (!allowedOrigins.has(origin)) return error(403, 'Origin is not allowed');
      headers.set('Access-Control-Allow-Origin', origin);
      headers.set('Access-Control-Allow-Methods', methods.join(', '));
      headers.set('Access-Control-Allow-Headers', 'Authorization, Content-Type');
      headers.set('Access-Control-Max-Age', '600');
    }
    if (url.pathname !== '/health' && !url.pathname.startsWith('/api/v1/')) return error(404, 'Endpoint not found');
    if (!methods.includes(request.method)) return error(405, 'Method not allowed');
    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers });

    if (env.AGAP_BACKEND === 'maintenance') {
      headers.set('Retry-After', '60');
      return error(503, 'AGAP is undergoing maintenance. Try again shortly.');
    }
    const configured = { supabase: env.AGAP_SUPABASE_ORIGIN, firestore: env.AGAP_FIRESTORE_ORIGIN }[env.AGAP_BACKEND];
    if (!configured || !env.AGAP_GATEWAY_SECRET) return error(503, 'AGAP backend configuration is missing');

    try {
      const target = new URL(configured);
      if (target.protocol !== 'https:' || target.username || target.password || target.search || target.hash) {
        return error(503, 'AGAP backend configuration is invalid');
      }
      target.pathname = target.pathname.replace(/\/$/, '') + url.pathname;
      target.search = url.search;
      const forwarded = new Headers({ 'X-AGAP-Gateway': env.AGAP_GATEWAY_SECRET });
      for (const name of ['authorization', 'content-type']) {
        if (request.headers.has(name)) forwarded.set(name, request.headers.get(name));
      }
      if (env.AGAP_BACKEND === 'supabase') forwarded.set('x-region', 'ap-southeast-1');
      const result = await upstreamFetch(target, {
        method: request.method, body: request.body, headers: forwarded, redirect: 'manual',
        ...(request.body ? { duplex: 'half' } : {}),
      });
      for (const name of ['content-type', 'location', 'retry-after']) {
        if (result.headers.has(name)) headers.set(name, result.headers.get(name));
      }
      return new Response(result.body, { status: result.status, headers });
    } catch {
      headers.set('Retry-After', '30');
      return error(503, 'AGAP is temporarily unavailable');
    }
  };
}

export default { fetch: createGateway() };
