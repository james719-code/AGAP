import { createClient } from '@libsql/client/web';
import { compare, hash } from 'bcryptjs';
import { createApi } from '../shared/api.mjs';
import { databaseFactory } from '../shared/database.mjs';

export default {
  fetch(request, env) {
    const handler = createApi({
      provider: 'cloudflare',
      getDatabase: databaseFactory(createClient, env.AGAP_DATABASE_URL, env.AGAP_DATABASE_TOKEN),
      verifyPassword: compare,
      hashPassword: hash,
      origins: env.AGAP_ALLOWED_ORIGINS,
      routingPackage: env.AGAP_ROUTING_PACKAGE,
    });
    return handler(request);
  },
};
