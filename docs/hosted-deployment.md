# AGAP free-tier deployment guide

**Netlify serves the Command Center. Cloudflare gives the web and Android apps one public API URL. Supabase runs the primary API and stores its data.** The optional standby runs the API on a separate Netlify site with Firebase Firestore as its database. GitHub Actions builds Android and publishes `AGAP.apk` to GitHub Releases.

Start with synthetic incidents on a controlled pilot. Free-tier quotas and pauses can interrupt service. Use PowerShell 7.2 or newer and Node.js 22. Run the commands from the repository root unless the command block changes directories. Replace example values with your own. Links in each section point to the current provider instructions.

## 1. Create the accounts and record the public settings

1. Create a **Supabase Free** project in **Singapore**. In the project dashboard, record the project reference from your URL and open **Connect**. You will use both the **Session pooler** and **Transaction pooler** connection strings.
2. Create or sign in to a **Cloudflare Free** account. Enable its `workers.dev` subdomain in **Workers & Pages** when prompted. The gateway deployment will print the full hostname.
3. Create a **Netlify Free** project for the Command Center. Use a manual deployment project so the GitHub workflow controls when a production deploy spends credits. Record its `https://…netlify.app` origin and **Project ID** from **Project configuration → General → Project information**. If you use the CLI, sign in with `npx --yes netlify-cli@23 login`, then use `npx --yes netlify-cli@23 sites:create --disable-linking` and select your Free team when prompted.
4. Enable Actions on the GitHub repository. A public repository lets people download the APK without a GitHub sign-in. If you move or fork AGAP, update `frontend/src/config/android-release.json` to match the repository publishing the APK.
5. Generate one random gateway secret and save it in your password manager. For example, run `node -p "require('node:crypto').randomBytes(32).toString('hex')"` in a private terminal. Use the **same value** in Supabase, Cloudflare, and the optional standby. Keep runtime credentials and database snapshots outside the public repository.

[Supabase Free](https://supabase.com/pricing) · [Wrangler setup](https://developers.cloudflare.com/workers/get-started/guide/) · [Netlify CLI and project IDs](https://docs.netlify.com/api-and-cli-guides/cli-guides/get-started-with-cli/) · [GitHub Actions billing](https://docs.github.com/en/actions/concepts/billing-and-usage)

## 2. Set up Supabase and load the primary database

Install AGAP’s runtime dependencies and stage the checked-in Edge Function. These examples pin Supabase CLI 2.122.1, the version used to verify the flags.

```powershell
npm --prefix functions ci
npm --prefix functions run prepare:deploy

$projectRef = 'YOUR_SUPABASE_PROJECT_REF'
Push-Location .\functions\supabase
npx --yes supabase@2.122.1 login
npx --yes supabase@2.122.1 link --project-ref $projectRef
npx --yes supabase@2.122.1 db push --linked --dry-run
Pop-Location
```

Review the dry run and apply the migration. It creates the private `agap` schema.

```powershell
Push-Location .\functions\supabase
npx --yes supabase@2.122.1 db push --linked
Pop-Location
```

In the Supabase **SQL Editor**, create the dedicated application login. Use a new password reserved for this database role. A randomly generated hexadecimal password is easy to paste into SQL and a connection URL.

```sql
CREATE ROLE agap_api LOGIN PASSWORD 'REPLACE_WITH_A_RANDOM_PASSWORD';
GRANT USAGE ON SCHEMA agap TO agap_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA agap TO agap_api;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA agap TO agap_api;
```

Use these connections. Copy the exact pooler hostname from **Connect**. URL-encode the password if it contains reserved characters. The `agap_api` password, the project database password, and the AGAP administrator password are three separate credentials.

| Used by | Connection string format |
|---|---|
| Supabase Edge Function | `postgresql://agap_api.PROJECT_REF:APP_ROLE_PASSWORD@POOLER_HOST:6543/postgres` |
| Local data import and export | `postgresql://postgres.PROJECT_REF:PROJECT_DB_PASSWORD@POOLER_HOST:5432/postgres` |

In **Supabase → Database Settings → SSL Configuration**, download the CA certificate. For this setup, give the driver the PEM text beginning with `-----BEGIN CERTIFICATE-----` in `AGAP_POSTGRES_CA_PEM`. The Edge Function and the local migration tool both accept it. The application connection verifies TLS against that CA. [Supabase connection strings](https://supabase.com/docs/guides/database/connecting-to-postgres) · [Supabase TLS setup](https://supabase.com/docs/guides/platform/ssl-enforcement)

### Create a fresh database or use a Turso snapshot

Set `$privateDir` to an existing private directory on an encrypted volume. Save the downloaded certificate at `$caPath`. The snapshot tool expects absolute paths because npm runs it inside `functions`.

```powershell
$privateDir = 'C:\AGAP-private'
$seedDb = Join-Path $privateDir 'agap-seed.db'
$snapshot = Join-Path $privateDir 'agap-initial.agap-snapshot.json'
$caPath = Join-Path $privateDir 'supabase-root.crt'
```

For a fresh pilot, create the first AGAP administrator and a SQLite seed. The preparation script requires a unique password of at least 12 characters and at most 72 UTF-8 bytes.

```powershell
$env:AGAP_ADMIN_USERNAME = Read-Host 'New AGAP administrator username'
$env:AGAP_ADMIN_PASSWORD = Read-Host 'New AGAP administrator password' -MaskInput
npm --prefix functions run prepare:db -- $seedDb
Remove-Item Env:AGAP_ADMIN_PASSWORD
Remove-Item Env:AGAP_ADMIN_USERNAME
```

For existing Turso data, sign in with `turso auth login` and make sure `sqlite3` is on your PATH. Pause writes before the final export. Rebuild a local snapshot in a new file and confirm the source's latest records and counts before importing. Compare with a synthetic rehearsal first. [Turso database dump](https://docs.turso.tech/cli/db/shell#database-dump)

```powershell
$tursoDb = 'YOUR_TURSO_DATABASE_NAME'
$dump = Join-Path $privateDir 'source.agap-turso.sql'
$fromTurso = Join-Path $privateDir 'from-turso.db'

# Use new file paths for each export.
if ((Test-Path -LiteralPath $dump) -or (Test-Path -LiteralPath $fromTurso)) {
    throw 'Choose new paths for the Turso dump and SQLite file'
}
turso db shell $tursoDb .dump | Set-Content -LiteralPath $dump -Encoding utf8NoBOM
if ($LASTEXITCODE -ne 0) { throw 'Turso export failed' }
$sqliteDump = $dump.Replace('\', '/')
sqlite3 -bail $fromTurso ".read ""$sqliteDump"""
if ($LASTEXITCODE -ne 0) { throw 'SQLite restore failed' }
```

The restored database must have the current AGAP schema. Set `$seedDb = $fromTurso` if you will import it as-is, or pass `$fromTurso` after `$seedDb` in the `prepare:db` command above to prepare a new copy with administrator provisioning.

To populate an empty Supabase schema, run the following. `inspect` prints counts and the anticipated Firestore document write volume. An existing Supabase installation should first be backed up and reviewed before any replacement.

```powershell
$env:AGAP_MIGRATION_POSTGRES_URL = Read-Host 'Supabase Session pooler owner URL' -MaskInput
$env:AGAP_POSTGRES_CA_PEM = Get-Content -LiteralPath $caPath -Raw

npm --prefix functions run migrate:data -- export-sqlite --input $seedDb --output $snapshot
npm --prefix functions run migrate:data -- inspect --input $snapshot
```

After inspecting the snapshot, import it into the primary database.

```powershell
npm --prefix functions run migrate:data -- import-postgres --input $snapshot
```

In **Supabase → Edge Functions → Secrets**, add `AGAP_POSTGRES_URL` with the application role's transaction pooler URL, `AGAP_POSTGRES_CA_PEM` with the PEM contents, and `AGAP_GATEWAY_SECRET` with the shared value. Deploy only the Postgres function. [Supabase secrets](https://supabase.com/docs/guides/functions/secrets) · [Edge Function deployment](https://supabase.com/docs/guides/functions/deploy)

```powershell
Push-Location .\functions\supabase
npx --yes supabase@2.122.1 functions deploy agap-postgres --project-ref $projectRef --use-api
Pop-Location
```

The function base URL is `https://PROJECT_REF.supabase.co/functions/v1/agap-postgres`. The Worker uses this upstream URL without adding `/api/v1` to the configuration value.

## 3. Deploy the Cloudflare gateway

Copy the gateway config once. Edit `functions/cloudflare/wrangler.gateway.local.toml` with the values below. This local config is ignored by Git. The first deploy in maintenance mode creates the Worker so you can add its secret before opening it to clients.

```toml
[vars]
AGAP_BACKEND = "maintenance"
AGAP_ALLOWED_ORIGINS = "https://YOUR-WEB-SITE.netlify.app"
AGAP_SUPABASE_ORIGIN = "https://PROJECT_REF.supabase.co/functions/v1/agap-postgres"
AGAP_FIRESTORE_ORIGIN = ""
```

For `AGAP_ALLOWED_ORIGINS` enter the exact browser origin, with its scheme and hostname and without a path or trailing slash. Add `http://localhost:5173` separated by a comma only if you also need access from the local web dev server.

```powershell
Push-Location .\functions\cloudflare
Copy-Item .\wrangler.gateway.toml .\wrangler.gateway.local.toml
# Edit wrangler.gateway.local.toml before deploying.
npx --yes wrangler@4 login
npx --yes wrangler@4 deploy --config wrangler.gateway.local.toml
npx --yes wrangler@4 secret put AGAP_GATEWAY_SECRET --config wrangler.gateway.local.toml
# Change AGAP_BACKEND from maintenance to supabase, then deploy the same Worker.
npx --yes wrangler@4 deploy --config wrangler.gateway.local.toml
Pop-Location

$workerBase = 'https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev'
Invoke-RestMethod "$workerBase/health"
```

Expect `status: ok` and `db: postgres`. Set the client API URL to `$workerBase/api/v1`. The gateway only checks and forwards requests. Waiting on Supabase or Netlify is outside Cloudflare Free’s 10 ms CPU limit. Check deployed Worker logs for CPU time or Error 1102 during the smoke test. [Cloudflare secrets](https://developers.cloudflare.com/workers/configuration/secrets/) · [Worker CPU and request limits](https://developers.cloudflare.com/workers/platform/limits/#cpu-time)

## 4. Publish the web app and Android APK through GitHub

Open **GitHub repository → Settings → Secrets and variables → Actions**. Add these before publishing a hosted build.

| Tab | Name | Value |
|---|---|---|
| Variables | `AGAP_API_PROVIDER` | `cloudflare` |
| Variables | `AGAP_API_URL` | `https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev/api/v1` |
| Variables | `NETLIFY_SITE_ID` | Project ID of the Command Center site |
| Secrets | `NETLIFY_AUTH_TOKEN` | A Netlify personal access token with access to the Command Center project |

Create the Netlify token under your Netlify user settings at **Applications → Personal access tokens**. The web workflow maps the repository settings into Vite at build time. The APK release job uses GitHub’s built-in token. [Netlify token setup](https://docs.netlify.com/api-and-cli-guides/cli-guides/get-started-with-cli/#authentication) · [GitHub Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)

When the workflow is present on the default branch, open **Actions → Build, test, and deploy → Run workflow**. Choose the default branch, run **web** to publish the tested build to Netlify, and run **android** to publish `AGAP.apk`. The web landing page already links to the latest release asset. The current workflow publishes a debug APK for pilot testing. [Run a GitHub workflow](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow) · [AGAP release workflow](ci-cd.md)

For a manual web build, put these public values in `frontend/.env.local`. GitHub-driven deployments already supply them from the Actions variables above.

```dotenv
VITE_API_PROVIDER=cloudflare
VITE_API_BASE_URL=https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev/api/v1
```

Build with `npm --prefix frontend ci` and `npm --prefix frontend run build -- --base=/`. The output is `frontend/dist`. For a local Android build aimed at the hosted backend, use the following in `android/local.properties` or pass equivalent Gradle properties. The gateway configuration uses HTTP polling and sync. Supabase and Firebase browser keys are unnecessary.

```properties
AGAP_API_PROVIDER=cloudflare
AGAP_API_URL=https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev/api/v1
```

## 5. Add the optional Firebase standby

1. Create a **Firebase Spark** project and one default **Firestore Standard** database in **Production mode**. Use `us-east5` (Columbus, Ohio) for the synthetic standby pilot because Netlify’s default function region is Ohio. Deploy the repository’s deny-by-default Firestore rules and indexes, then wait for the indexes to finish building. [Firestore setup](https://firebase.google.com/docs/firestore/manage-databases) · [Firestore locations](https://firebase.google.com/docs/firestore/locations) · [Netlify function regions](https://docs.netlify.com/build/functions/configuration/)
2. In the matching **Google Cloud → IAM & Admin → Service Accounts**, create a dedicated service account for the standby and grant it **Cloud Datastore User** (`roles/datastore.user`) on that project. Open the account’s **Keys** tab and create a JSON key if your account policy permits it. Use its `project_id`, `client_email`, and `private_key` fields for the environment variables below. [Firestore IAM](https://firebase.google.com/docs/firestore/security/iam) · [Service account keys](https://docs.cloud.google.com/iam/docs/keys-create-delete)
3. Create a second **manual Netlify site** for the standby. In its **Project configuration → Environment variables**, enter the values below for the production deploy. Use the key text itself for `FIREBASE_PRIVATE_KEY`, including the PEM delimiters. The function accepts real line breaks or literal `\n` separators. Deploy or redeploy after setting the variables. [Netlify function environment variables](https://docs.netlify.com/build/functions/environment-variables/)

| Variable on the standby Netlify site | Value |
|---|---|
| `FIREBASE_PROJECT_ID` | Firebase project ID, the JSON `project_id` |
| `FIREBASE_CLIENT_EMAIL` | Service account email, the JSON `client_email` |
| `FIREBASE_PRIVATE_KEY` | Private PEM text, the JSON `private_key` |
| `AGAP_GATEWAY_SECRET` | Same private value used in Cloudflare and Supabase |

```powershell
$firebaseProject = 'YOUR_FIREBASE_PROJECT_ID'
$standbySiteId = 'YOUR_STANDBY_NETLIFY_PROJECT_ID'

Push-Location .\functions\firebase
npx --yes firebase-tools@15.15.0 login
npx --yes firebase-tools@15.15.0 deploy --only firestore --project $firebaseProject
Pop-Location

Push-Location .\functions
npx --yes netlify-cli@23 login
npx --yes netlify-cli@23 deploy --prod --no-build --dir=netlify-public --functions=netlify --site $standbySiteId
Pop-Location
```

In the local Wrangler config, set `AGAP_FIRESTORE_ORIGIN = "https://YOUR-STANDBY-SITE.netlify.app/.netlify/functions/agap-api"`. Keep `AGAP_BACKEND = "supabase"` while preparing the standby. Firebase serves as the standby database on Spark. The running function is on Netlify and `AGAP.apk` distribution stays on GitHub. [Firebase pricing](https://firebase.google.com/pricing) · [Firestore quotas](https://firebase.google.com/docs/firestore/quotas)

### Import and switch

Use the SQLite snapshot from the Turso export if Turso is your current source. Once Supabase is the source, export it with `migrate:data -- export-postgres`. That command uses the local `AGAP_MIGRATION_POSTGRES_URL` and `AGAP_POSTGRES_CA_PEM` settings from step 2.

Authenticate the local migration tool using Google Application Default Credentials. Run `gcloud auth application-default login` with an account authorized for the Firestore project, or set `GOOGLE_APPLICATION_CREDENTIALS` to an absolute path to an authorized service-account JSON file. The Firebase CLI login above authenticates rules and index deployment. The importer uses Google credentials separately. For an emulator rehearsal, set `FIRESTORE_EMULATOR_HOST` to the emulator address, for example `127.0.0.1:8087`, and use the emulator’s test project ID. Clear that environment setting before targeting Spark. [Google local authentication](https://docs.cloud.google.com/docs/authentication/set-up-adc-local-dev-environment)

1. Set `AGAP_BACKEND = "maintenance"` in the local Wrangler config and deploy the Worker. Wait for in-flight requests to finish and stop writes through any legacy origins. For a synthetic rehearsal, you can practice with a separate test snapshot.
2. Export a fresh snapshot of the current source if needed. The following example uses the already configured Supabase owner connection.

   ```powershell
   $switchSnapshot = Join-Path $privateDir 'agap-switch.agap-snapshot.json'
   npm --prefix functions run migrate:data -- export-postgres --output $switchSnapshot
   npm --prefix functions run migrate:data -- inspect --input $switchSnapshot
   npm --prefix functions run migrate:data -- import-firestore --project $firebaseProject --input $switchSnapshot
   ```

   For a direct Turso-to-Firestore switch, use `export-sqlite --input $fromTurso --output $switchSnapshot` as the export step. The importer preserves record IDs and password hashes. Users sign in again after the switch. If import exits with code `2`, resume with the same snapshot after checking the write allowance. The importer budgets 10,000 Firestore writes per Pacific calendar day across its runs. Activate only after the import reports completion. Activation verifies the copied data before switching the active Firestore generation.

   ```powershell
   npm --prefix functions run migrate:data -- activate-firestore --project $firebaseProject --input $switchSnapshot
   ```

3. Set `AGAP_BACKEND = "firestore"` and redeploy the same Worker. `/health` should report `db: firestore`. The client API URL remains the same. Re-sign in and confirm Android can sync a queued synthetic incident and the Command Center displays its dispatch history.
4. To return to Supabase before Firestore accepts writes, set `AGAP_BACKEND = "supabase"` and redeploy. Once Firestore has accepted writes, enter maintenance again before returning. Export Firestore and back up Postgres with the owner connection from step 2.

   ```powershell
   $returnSnapshot = Join-Path $privateDir 'agap-return.agap-snapshot.json'
   $postgresBackup = Join-Path $privateDir 'agap-before-return.agap-snapshot.json'
   npm --prefix functions run migrate:data -- export-firestore --project $firebaseProject --output $returnSnapshot
   npm --prefix functions run migrate:data -- export-postgres --output $postgresBackup
   npm --prefix functions run migrate:data -- inspect --input $postgresBackup
   ```

   After checking the backup, run `npm --prefix functions run migrate:data -- import-postgres --input $returnSnapshot --replace`. This replaces the AGAP data in Postgres. Confirm the restored counts and representative incident histories before redeploying the gateway with `AGAP_BACKEND = "supabase"`.

To redeploy the gateway after each config edit, run this from the repository root.

```powershell
Push-Location .\functions\cloudflare
npx --yes wrangler@4 deploy --config wrangler.gateway.local.toml
Pop-Location
```

## Environment variable checklist

| Where it belongs | Required settings |
|---|---|
| Local terminal during initial setup | `AGAP_ADMIN_USERNAME` and `AGAP_ADMIN_PASSWORD` for a fresh seed or an administrator reset |
| Local terminal for Postgres data migration | `AGAP_MIGRATION_POSTGRES_URL` with the owner Session pooler URL and `AGAP_POSTGRES_CA_PEM` with the downloaded root CA |
| Supabase Edge Function secrets | `AGAP_POSTGRES_URL` with the application Transaction pooler URL, `AGAP_POSTGRES_CA_PEM`, and `AGAP_GATEWAY_SECRET` |
| Cloudflare local Wrangler config | `AGAP_BACKEND`, `AGAP_ALLOWED_ORIGINS`, `AGAP_SUPABASE_ORIGIN`, and `AGAP_FIRESTORE_ORIGIN` when using the standby |
| Cloudflare Worker secret | `AGAP_GATEWAY_SECRET` |
| GitHub Actions repository variables | `AGAP_API_PROVIDER=cloudflare`, `AGAP_API_URL`, and `NETLIFY_SITE_ID` |
| GitHub Actions repository secret | `NETLIFY_AUTH_TOKEN` |
| Netlify standby site only | `FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, `FIREBASE_PRIVATE_KEY`, and `AGAP_GATEWAY_SECRET` |
| Local Firestore migration | Google Application Default Credentials. Set `GOOGLE_APPLICATION_CREDENTIALS` only when using a key file, or `FIRESTORE_EMULATOR_HOST` when using the local emulator. |
| Local or manual client builds | For web, `VITE_API_PROVIDER` and `VITE_API_BASE_URL`. For Android, `AGAP_API_PROVIDER` and `AGAP_API_URL`. |

Optional `AGAP_ROUTING_PACKAGE` belongs in the Supabase function secrets and standby Netlify environment only if you have a responder offline routing graph to publish. Its value is a JSON object with `url` (HTTPS), `file_name`, `size_bytes`, `sha256`, and `modified_at`. Hosted clients do not use `VITE_WS_URL`, `AGAP_WS_URL`, or `VITE_SUPABASE_PUBLISHABLE_KEY` with this gateway.

## Verify before inviting testers

- Check the Worker’s `/health`. A `503` in maintenance is expected. Outside maintenance, check the active backend and its configuration, including the shared secret and Supabase CA. If Netlify works on the home page but a deep link fails, confirm the web build used `--base=/` and included `_redirects`.
- Use the Netlify web URL to sign in as your new AGAP administrator. Register a pilot account on Android, approve it in the web app, submit a synthetic incident, and confirm both clients see the same incident. Download `AGAP.apk` from the public GitHub release to verify the published build.
- Review live usage under synthetic load. Cloudflare Workers Free allows 100,000 requests/day, Supabase Free includes 500,000 Edge Function invocations/month and 5 GB/month uncached egress, and Netlify Free shares 300 monthly credits between the sites. Netlify counts compute when the standby runs. Use the pilot targets of 8,000 API requests/day, 3 GB/month of uncached Supabase egress, and 200 Netlify credits/month. [Cloudflare limits](https://developers.cloudflare.com/workers/platform/limits/) · [Supabase limits](https://supabase.com/pricing) · [Netlify credits](https://docs.netlify.com/manage/accounts-and-billing/billing/billing-for-credit-based-plans/how-credits-work/)
