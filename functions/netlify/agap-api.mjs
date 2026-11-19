import { Firestore } from '@google-cloud/firestore';
import { compare, hash } from 'bcryptjs';
import { createFirestoreApi } from '../shared/firestore-api.mjs';
import { activeFirestoreStore } from '../shared/firestore-store.mjs';
import { HttpError } from '../shared/http.mjs';

let database;
function getStore() {
  if (database) return activeFirestoreStore(database);
  const projectId = process.env.FIREBASE_PROJECT_ID;
  const clientEmail = process.env.FIREBASE_CLIENT_EMAIL;
  const privateKey = process.env.FIREBASE_PRIVATE_KEY?.replaceAll('\\n', '\n');
  if (!projectId || !clientEmail || !privateKey) throw new HttpError(503, 'Firestore standby configuration is missing');
  database = new Firestore({ projectId, credentials: { client_email: clientEmail, private_key: privateKey } });
  return activeFirestoreStore(database);
}

export default createFirestoreApi({
  getStore, verifyPassword: compare, hashPassword: hash,
  gatewaySecret: process.env.AGAP_GATEWAY_SECRET ?? '',
  routingPackage: process.env.AGAP_ROUTING_PACKAGE,
  mounts: ['/.netlify/functions/agap-api'],
});
