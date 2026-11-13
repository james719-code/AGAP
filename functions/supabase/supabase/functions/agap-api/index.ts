import { createClient } from 'npm:@libsql/client@0.15.15/web';
import { compare, hash } from 'npm:bcryptjs@3.0.3';
import { createApi } from '../_shared/api.mjs';
import { databaseFactory } from '../_shared/database.mjs';

Deno.serve(createApi({
  provider: 'supabase',
  getDatabase: databaseFactory(
    createClient,
    Deno.env.get('AGAP_DATABASE_URL'),
    Deno.env.get('AGAP_DATABASE_TOKEN'),
  ),
  verifyPassword: compare,
  hashPassword: hash,
  origins: Deno.env.get('AGAP_ALLOWED_ORIGINS'),
  routingPackage: Deno.env.get('AGAP_ROUTING_PACKAGE'),
  mounts: ['/functions/v1/agap-api', '/agap-api'],
}));
