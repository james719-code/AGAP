import pg from 'npm:pg@8.16.3';
import { compare, hash } from 'npm:bcryptjs@3.0.3';
import { createApi } from '../_shared/api.mjs';
import { connectPostgres, postgresFactory } from '../_shared/postgres.mjs';

const getDatabase = postgresFactory((connection: string) => {
  const url = new URL(connection);
  if (!url.searchParams.has('sslmode')) url.searchParams.set('sslmode', 'verify-full');
  return connectPostgres(pg, url.href, Deno.env.get('AGAP_POSTGRES_CA_PEM'));
}, Deno.env.get('AGAP_POSTGRES_URL'));

Deno.serve(createApi({
  provider: 'supabase',
  databaseType: 'postgres',
  getDatabase,
  gatewaySecret: Deno.env.get('AGAP_GATEWAY_SECRET') ?? '',
  verifyPassword: compare,
  hashPassword: hash,
  routingPackage: Deno.env.get('AGAP_ROUTING_PACKAGE'),
  mounts: ['/functions/v1/agap-postgres', '/agap-postgres'],
}));
