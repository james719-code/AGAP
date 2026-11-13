import { HttpError } from './http.mjs';

export function databaseFactory(createClient, url, authToken) {
  let client;
  return () => {
    if (!url || !authToken) throw new HttpError(503, 'Hosted database configuration is missing');
    // Use stateless HTTPS so the client works in all three runtimes.
    const httpsUrl = url.replace(/^libsql:\/\//, 'https://');
    if (!httpsUrl.startsWith('https://')) throw new HttpError(503, 'Hosted database URL must use HTTPS');
    client ??= createClient({ url: httpsUrl, authToken });
    return client;
  };
}
