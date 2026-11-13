# Hosted AGAP API

Choose Cloudflare Workers, Supabase Edge Functions, or Firebase Cloud Functions for the Command Center and Android API. All three run the same handlers and use a managed Turso **libSQL** database. Deploy one provider or point several at the same database to share records. Serve the React build separately on any static host.

The hosted API covers account approval and sessions, incident reports, BLE gateway uplinks, mobile sync, dispatch, chat, registries, user administration, and notifications. The web console and Android app refresh over HTTP. The cloud database and an existing Go SQLite file are separate after import. Point both clients at the hosted API to see Android reports in the web console.

## Prepare the database

From the repository root, install the locked dependencies with Node.js 22 and prepare a database snapshot. For a fresh database, set `AGAP_ADMIN_USERNAME` and `AGAP_ADMIN_PASSWORD` in your local environment before running the second command. Use a unique password of at least 12 characters and at most 72 bytes.

```sh
npm --prefix functions ci
npm --prefix functions run prepare:db -- ../.tmp/agap-hosted.db
```

To copy an existing AGAP database instead, pass its absolute path as the second argument. The script requires the existing database to have the current AGAP schema and makes a consistent copy, including committed WAL data. Approved account credentials carry over, and you can supply the admin variables to create or reset an administrator in the snapshot. The hosted API rejects the shipped demo passwords. Sign in again after the switch.

```sh
npm --prefix functions run prepare:db -- ../.tmp/agap-hosted.db <existing-database-path>
turso db create agap --from-file .tmp/agap-hosted.db
turso db show agap --url
turso db tokens create agap
```

Import the prepared file into a **libSQL** database. Keep the snapshot private. Give the URL and token to the chosen function using `AGAP_DATABASE_URL` and `AGAP_DATABASE_TOKEN`. Set `AGAP_ALLOWED_ORIGINS` to a comma-separated list of allowed web origins, such as `https://agap.example.com,http://localhost:5173`. An origin consists of the scheme, hostname, and optional port. [Turso database import](https://docs.turso.tech/cli/db/create)

## Deploy the function

### Cloudflare Workers

Set the allowed origins in `cloudflare/wrangler.toml`. Use a Workers plan with enough CPU time for bcrypt password verification. Run from `functions/cloudflare` with Wrangler authenticated.

```sh
npx wrangler secret put AGAP_DATABASE_URL
npx wrangler secret put AGAP_DATABASE_TOKEN
npx wrangler deploy
```

The function root is the Worker URL printed by Wrangler. [Workers CLI](https://developers.cloudflare.com/workers/wrangler/commands/) · [Workers limits](https://developers.cloudflare.com/workers/platform/limits/)

### Supabase Edge Functions

Run `npm --prefix functions run prepare:deploy` from the repository root to stage the shared API. Then run the following from `functions/supabase`. Use a private environment file containing the database URL, database token, and allowed origins. The Edge Function handles AGAP session authentication inside the function.

```sh
supabase secrets set --project-ref <project-ref> --env-file <private-env-file>
supabase functions deploy agap-api --project-ref <project-ref> --use-api
```

The function root is `https://<project-ref>.supabase.co/functions/v1/agap-api`. [Supabase function deployment](https://supabase.com/docs/guides/functions/deploy)

### Firebase Cloud Functions

Run `npm --prefix functions run prepare:deploy` from the repository root to stage the shared API. Then run the following from `functions/firebase` with the Firebase CLI authenticated. Supply the database URL and allowed origins when Firebase prompts for parameters, or set them in `runtime/.env.<project-id>`.

```sh
npm --prefix runtime ci
firebase functions:secrets:set AGAP_DATABASE_TOKEN --project <project-id>
firebase deploy --only functions:agapApi --project <project-id>
```

Use the HTTPS function URL printed by Firebase. [Firebase HTTP functions](https://firebase.google.com/docs/functions/http-events) · [Firebase secrets and parameters](https://firebase.google.com/docs/functions/config-env)

## Connect the frontend

Set these values in `frontend/.env.local` or in the frontend host's build environment. Replace the example URL with the chosen function root followed by `/api/v1`.

```dotenv
VITE_API_PROVIDER=cloudflare
VITE_API_BASE_URL=https://agap-api.example.workers.dev/api/v1
```

Use `VITE_API_PROVIDER=supabase` or `VITE_API_PROVIDER=firebase` for the other deployments. If supplying a Supabase project key through `VITE_SUPABASE_PUBLISHABLE_KEY`, use the public publishable key. All `VITE_` values are visible to the browser.

Run `npm --prefix frontend run build`. Publish `frontend/dist` under `/agap/` and route requests for pages such as `/agap/dashboard/map` to the built `index.html`. For local development, run `npm --prefix frontend run dev` and open `http://localhost:5173/agap/`.

Verify the deployment by requesting `<function-root>/health` and signing in to the console. `npm --prefix functions test` exercises the shared API with the AGAP SQLite schema.

## Connect Android

In `android/local.properties`, set `AGAP_API_PROVIDER` and `AGAP_API_URL` to the selected provider and its HTTPS function root followed by `/api/v1`. Rebuild and sign in. For a smoke test, register a citizen, approve the account in the web console, and submit an incident from Android. Confirm the incident appears in the web console. See the [Android setup](../android/README.md#hosted-api).

For responder offline routing downloads, set `AGAP_ROUTING_PACKAGE` on the function to a JSON object with `url`, `file_name`, `size_bytes`, `sha256`, and `modified_at`. Host the prepared SQLite routing graph at that HTTPS URL. Android verifies the checksum before installing it. Without a configured package, the app can use its existing offline map and routing sources.

## Send a new online incident

Call `POST <function-root>/api/v1/incidents` with an AGAP admin bearer token and a JSON body. Use a stable incident UUID for safe retries. The first request returns 201 and adds a notification. A retry with the same UUID returns the existing incident.

```json
{
  "incident_uuid": "01d7da90-0f95-4653-9a16-419803290fec",
  "target_id": { "raw": "05-05-12-01::01d7da90-0f95-4653-9a16-419803290fec" },
  "incident_type": "MEDICAL",
  "latitude": 13.1391,
  "longitude": 123.7438,
  "sender_device_id": "reporting-integration"
}
```
