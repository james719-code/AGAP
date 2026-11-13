import { createClient } from '@libsql/client/web';
import { compare, hash } from 'bcryptjs';
import { onRequest } from 'firebase-functions/v2/https';
import { defineSecret, defineString } from 'firebase-functions/params';
import { createApi } from './shared/api.mjs';
import { databaseFactory } from './shared/database.mjs';

const databaseUrl = defineString('AGAP_DATABASE_URL');
const databaseToken = defineSecret('AGAP_DATABASE_TOKEN');
const origins = defineString('AGAP_ALLOWED_ORIGINS', { default: 'http://localhost:5173' });
const routingPackage = defineString('AGAP_ROUTING_PACKAGE', { default: '' });

export const agapApi = onRequest({ cors: false, secrets: [databaseToken] }, async (req, res) => {
  const handler = createApi({
    provider: 'firebase',
    getDatabase: databaseFactory(createClient, databaseUrl.value(), databaseToken.value()),
    verifyPassword: compare,
    hashPassword: hash,
    origins: origins.value(),
    routingPackage: routingPackage.value(),
    mounts: ['/agapApi'],
  });
  const url = new URL(`https://agap.internal/${req.url.replace(/^\/+/, '')}`);
  const headers = new Headers();
  for (const [key, value] of Object.entries(req.headers)) {
    if (value !== undefined) headers.set(key, Array.isArray(value) ? value.join(', ') : value);
  }
  const body = ['GET', 'HEAD'].includes(req.method) || !req.rawBody?.length ? undefined : req.rawBody;
  const response = await handler(new Request(url, { method: req.method, headers, body }));
  res.status(response.status);
  response.headers.forEach((value, key) => res.set(key, value));
  res.send(Buffer.from(await response.arrayBuffer()));
});
