# Build, test, and deploy

The `Build, test, and deploy` GitHub Actions workflow runs on branch pushes and
pull requests. Pushes containing multiple commits are compared from the old tip
to the new tip; PRs are compared from their merge base. Both sides of a rename
and deleted files count as changes.

| Changed paths | Automatic work |
| --- | --- |
| `android/**` | Android unit tests, then an installable debug APK; successful default-branch pushes publish a GitHub release |
| `frontend/**`, root `netlify.toml` or root `vercel.json` | Frontend tests, TypeScript checks, and Vite production build |
| Both applications | Both jobs |
| Hosted functions and data migrations | Separate PostgreSQL and Firestore contract tests |
| Other paths | Routing tests |

Web production deploys are promoted manually from the default branch. This
keeps Netlify's free-tier credits available for traffic. Pull requests and
feature branches run checks and produce build artifacts. Publication requires
a successful default-branch run.

## Netlify setup

Create a Netlify Free site and follow the [hosted deployment runbook](hosted-deployment.md).
Set `AGAP_API_PROVIDER` to `cloudflare`, `AGAP_API_URL` to the public HTTPS
Worker URL ending in `/api/v1`, and `NETLIFY_SITE_ID` to the site's project ID
as GitHub Actions variables. Store `NETLIFY_AUTH_TOKEN` in GitHub Actions secrets.
In **Actions → Build, test, and deploy → Run workflow**, select the default branch
and target **web**. GitHub uploads the tested Vite artifact to Netlify.

The web build uses Vite's root base for Netlify and includes the SPA rewrite for
direct links. Local and existing `/agap/` builds keep their configured base.

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

The first download becomes available after a successful Android-changing push
to `main` or an explicitly requested release. For the first hosted build or a
change to the API variables, choose target **android** from the default branch.
Android-only pushes update the GitHub download without rebuilding
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

To roll back the web site, restore a known-good deploy in Netlify, or revert
the change on the default branch and manually promote the resulting build.
