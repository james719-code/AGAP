# Build, test, and deploy

The `Build, test, and deploy` GitHub Actions workflow runs on branch pushes and
pull requests. Pushes containing multiple commits are compared from the old tip
to the new tip; PRs are compared from their merge base. Both sides of a rename
and deleted files count as changes.

| Changed paths | Automatic work |
| --- | --- |
| `android/**` | Android unit tests, then an installable debug APK; successful default-branch pushes publish a GitHub release |
| `frontend/**` or root `vercel.json` | Frontend tests, TypeScript checks, and Vite production build |
| Both applications | Both jobs |
| Anything else, including root docs, backend, functions, or CI files | Routing tests only; no app build or deployment |

After web checks pass, branch pushes deploy to Vercel: the repository's default
branch (`main`) goes to production; other branches get preview deployments.
Pull requests run checks and produce artifacts without deploying. Fork PRs do
not receive Vercel credentials. Tags and branch deletions do not build or deploy.
Application builds are not automatically cancelled, so an unrelated commit
cannot cancel a pending application build. A push tests/builds the final tree
once, even when that push contains several commits. Vercel deployments are
serialized per branch. During rapid web pushes, GitHub keeps the newest pending
deployment while the active deployment completes; every push still gets its
application checks and build.

## Vercel setup (one time)

1. Create or link a Vercel project with this repository's **root directory `.`**,
   not `frontend`. The root `vercel.json` installs and builds the frontend and
   publishes `frontend/dist`. Select Node.js **22.x** in the project settings.
2. Add these repository **Actions secrets** in GitHub Settings → Secrets and
   variables → Actions:
   - `VERCEL_TOKEN`: a Vercel access token with access to the project.
   - `VERCEL_ORG_ID`: the project's team/account ID (`orgId`).
   - `VERCEL_PROJECT_ID`: the project's ID (`projectId`).
   The IDs are available in `.vercel/project.json` after running `vercel link`
   from the repository root. Do not commit that directory or the token.
3. In Vercel, configure the frontend's public `VITE_*` environment variables
   for **Production and Preview** using `frontend/.env.example` as a reference.
   Set `VITE_API_BASE_URL` to the deployed HTTPS API URL ending in `/api/v1`.
   For the Go provider, also set an externally reachable `VITE_WS_URL` using
   `wss://`. For a hosted provider, set `VITE_API_PROVIDER` and, where needed,
   `VITE_SUPABASE_PUBLISHABLE_KEY`. These values are included in the browser
   bundle; never use private API keys here. The API must allow the website's
   origin through CORS. This workflow deploys the web client, not its backend.
4. Commit and push these workflow files. Web pushes will fail the deployment
   job with a clear message until all three secrets exist; web tests/builds and
   Android builds still run independently.

The deployment job uses Vercel's `pull` → `build` → `deploy --prebuilt` flow,
so it builds with the target's Vercel environment variables after CI passes.
The Vercel build overrides the Vite base to `/`; local and existing `/agap/`
hosting keep their current settings. The SPA rewrite supports direct links
and refreshed browser routes. `git.deploymentEnabled: false` disables Vercel's
separate Git-triggered deployments so unrelated commits cannot bypass the
workflow's change filter or test gate.

## Android configuration and downloads

The runner uses JDK 21, Android API 36, the checked-in Gradle wrapper, and a
cached Gradle user home. Its command overrides the local Windows Java path.
Optional repository **Actions variables** configure the APK:

| Variable | Default |
| --- | --- |
| `AGAP_API_PROVIDER` | `go` |
| `AGAP_API_URL` | `http://10.0.2.2:8080/api/v1` (local emulator) |
| `AGAP_WS_URL` | `ws://10.0.2.2:8080/ws` (local emulator) |

Set externally reachable endpoints for builds used on real phones. Hosted
providers require an HTTPS API URL ending in `/api/v1`.

Open GitHub **Actions → run → Artifacts** to download `agap-debug-<commit>`.
Unzip it to get the APK. These are debug builds for testing; Play Store release
signing and publication are not configured. Artifacts are retained for 14 days.
The same run includes Android test reports (including on test failures) and,
for web changes, `web-dist-<commit>`. Deployment URLs appear in the job summary.

### Public APK download and GitHub Releases

After the Android job succeeds on a push to `main`, `Publish Android GitHub
release` downloads that run's APK and publishes a release tagged
`android-<workflow run number>-<commit prefix>`. Its APK asset is always named
`AGAP.apk`. Each release is uploaded as a draft first and made public only after
the upload succeeds. The release job has `contents: write` permission and uses
GitHub's built-in token; no additional release secret is required.

The frontend's **Get Android app → Download Android APK** link points to
`https://github.com/james719-code/AGAP/releases/latest/download/AGAP.apk`.
The repository and asset name are shared between the frontend and publisher in
`frontend/src/config/android-release.json`. Update the repository there if this
project is moved or forked. Release downloads require a public repository for
visitors who are not signed into GitHub.

The first download becomes available after the first successful Android-changing
push to `main`. Android-only pushes update the GitHub download without rebuilding
or redeploying the frontend. Failed, cancelled, or skipped Android jobs never
create a release. An upload failure leaves only an unpublished draft, and the
last successful public release stays downloadable. Rerunning the same build
resumes a draft or leaves an already published release unchanged. An older run
finishing late cannot replace a newer latest release.

PRs and other branches retain their Actions APK artifacts but never publish a
public release or change the website's download. GitHub release assets do not
use the Actions artifacts' 14-day expiry. These remain debug APKs for field
testing; signing a production Android release is separate from creating a
GitHub release. Debug signing keys are generated on the runner, so builds from
different runners may require uninstalling the previous test app before
installation. Use a persistent release signing key before distributing production
updates.

## Verify locally

From the repository root:

```sh
node --test scripts/ci/tests/*.test.mjs
npm --prefix frontend test
npm --prefix frontend run build -- --base=/
```

From `android`, using JDK 21 and an installed Android SDK:

```sh
# Windows: use .\gradlew.bat instead of bash ./gradlew
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Routing tests cover platform-only and mixed changes, unrelated files, multiple
commits, PR merge bases, branch creation, deletion, renames, force pushes, event
validation, and the actual GitHub output-file interface. Routing tests run on
every workflow invocation, including changes to CI itself. The same test suite
also verifies release failure gates, draft publication, upload failures, safe
retries, and protection of the latest successful APK. Frontend tests render the
download dialog and check its actual APK and release-history links.

## Rollback and failures

Build/test errors stop the affected application job; failed web checks prevent
deployment. Android failures do not block otherwise successful web checks.
Android test/build failures also prevent GitHub release publication and leave
the website's last successful APK download intact.
If the old commit cannot be fetched after a force push, detection fails rather
than guessing. Inspect the Actions logs, fix the failure, and rerun the job.
GitHub's normal Actions failure notifications apply.

To roll back web production, revert the frontend/config change and push to
`main`, or use Vercel's dashboard rollback to a known-good deployment. Reverting
a workflow-only change will not redeploy the website.

References: [Vercel's GitHub Actions guide](https://vercel.com/kb/guide/how-can-i-use-github-actions-with-vercel),
[Vite SPA routing on Vercel](https://vercel.com/docs/frameworks/frontend/vite), and
[disabling Vercel Git deployments](https://vercel.com/docs/project-configuration/git-configuration).
