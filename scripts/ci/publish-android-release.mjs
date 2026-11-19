import { spawnSync } from 'node:child_process';
import { appendFileSync, copyFileSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const config = JSON.parse(readFileSync(new URL('../../frontend/src/config/android-release.json', import.meta.url), 'utf8'));

function runGh(args) {
  const result = spawnSync('gh', args, { encoding: 'utf8' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stderr || `gh exited with ${result.status}`);
  return result.stdout.trim();
}

function viewRelease(gh, repository, tag, fields) {
  try {
    return JSON.parse(gh(['release', 'view', ...(tag ? [tag] : []), '--repo', repository, '--json', fields]));
  } catch (error) {
    if (/release not found|HTTP 404/i.test(error.message)) return null;
    throw error;
  }
}

export function publishAndroidRelease({
  eventName, ref, defaultBranch, buildResult, repository, sha, runNumber, apkPath, manualTarget,
}, gh = runGh) {
  // Keep the guard here as well as in the workflow so failed builds, PRs, and
  // feature branches can never publish, even if someone invokes this directly.
  if (!canPublish(eventName, manualTarget, ref, defaultBranch, buildResult)) return null;
  if (repository !== config.repository) throw new Error('Update android-release.json to match this repository.');
  if (!/^[a-f\d]{40}$/i.test(sha) || !/^[1-9]\d*$/.test(String(runNumber))) throw new Error('Invalid release commit or run number.');
  if (path.basename(apkPath) !== config.assetName || !statSync(apkPath).isFile() || statSync(apkPath).size === 0) {
    throw new Error(`A nonempty ${config.assetName} is required before creating a release.`);
  }

  const tag = `android-${runNumber}-${sha.slice(0, 12)}`;
  const existing = viewRelease(gh, repository, tag, 'isDraft,url');
  if (existing && !existing.isDraft) return existing.url;

  const latest = viewRelease(gh, repository, null, 'tagName');
  const latestRun = /^android-(\d+)-/.exec(latest?.tagName ?? '')?.[1];
  // A late-finishing older build or a rerun must not replace a newer download.
  const makeLatest = !latestRun || Number(runNumber) >= Number(latestRun);
  const repoArgs = ['--repo', repository];
  if (existing) {
    gh(['release', 'upload', tag, apkPath, '--clobber', ...repoArgs]);
  } else {
    gh(['release', 'create', tag, apkPath, ...repoArgs, '--draft', '--target', sha,
      '--title', `AGAP Android test build ${runNumber}`,
      '--notes', `Android unit tests and APK build passed for commit ${sha}.\n\nDownload ${config.assetName} below. This is a debug APK for field testing, not a Play Store release.\n\nAndroid may ask you to allow installation from your browser.`]);
  }
  // Upload failures leave an unpublished draft; the old public download stays intact.
  gh(['release', 'edit', tag, ...repoArgs, '--draft=false', `--latest=${makeLatest}`]);
  return `https://github.com/${repository}/releases/tag/${tag}`;
}

function canPublish(event, target, ref, branch, result) {
  return (event === 'push' || (event === 'workflow_dispatch' && target === 'android')) &&
    ref === `refs/heads/${branch}` && result === 'success';
}

export function run(env = process.env) {
  if (!canPublish(env.GITHUB_EVENT_NAME, env.MANUAL_TARGET, env.GITHUB_REF, env.DEFAULT_BRANCH, env.ANDROID_BUILD_RESULT)) return;
  const files = readdirSync('android-apk').filter((name) => name.endsWith('.apk'));
  if (files.length !== 1) throw new Error('Expected exactly one APK from the successful Android build.');
  copyFileSync(path.join('android-apk', files[0]), config.assetName);
  const url = publishAndroidRelease({
    eventName: env.GITHUB_EVENT_NAME, ref: env.GITHUB_REF, defaultBranch: env.DEFAULT_BRANCH,
    buildResult: env.ANDROID_BUILD_RESULT, repository: env.GITHUB_REPOSITORY,
    sha: env.GITHUB_SHA, runNumber: env.GITHUB_RUN_NUMBER, apkPath: config.assetName,
    manualTarget: env.MANUAL_TARGET,
  });
  if (env.GITHUB_STEP_SUMMARY && url) {
    appendFileSync(env.GITHUB_STEP_SUMMARY, `### Android release\n\n${url}\n\n[Download latest APK](https://github.com/${config.repository}/releases/latest/download/${config.assetName})\n`);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) run();
