import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { changedPaths, classifyPaths, classifyDeploymentPaths } from '../detect-changes.mjs';

const zero = '0'.repeat(40);
const detector = fileURLToPath(new URL('../detect-changes.mjs', import.meta.url));

function repository(t) {
  const cwd = mkdtempSync(path.join(os.tmpdir(), 'agap-ci-'));
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  const git = (...args) => execFileSync('git', args, { cwd, encoding: 'utf8' }).trim();
  git('init', '--initial-branch=main');
  git('config', 'user.name', 'CI Test');
  git('config', 'user.email', 'ci@example.invalid');
  git('config', 'commit.gpgsign', 'false');
  const write = (name, content = name) => {
    const target = path.join(cwd, name);
    mkdirSync(path.dirname(target), { recursive: true });
    writeFileSync(target, content);
  };
  const commit = () => {
    git('add', '--all');
    git('commit', '--quiet', '-m', 'fixture');
    return git('rev-parse', 'HEAD');
  };
  write('README.md');
  const initial = commit();
  const push = (before, after, extra = {}) => ({
    before, after, ref: 'refs/heads/main', repository: { default_branch: 'main' }, ...extra,
  });
  return { cwd, git, write, commit, initial, push };
}

for (const [name, paths, expected] of [
  ['Android only', ['android/app/src/main/Main.kt', 'android/gradle/libs.versions.toml'], { android: true, web: false }],
  ['web only', ['frontend/src/App.tsx', 'frontend/package-lock.json'], { android: false, web: true }],
  ['both applications', ['android/gradlew', 'frontend/public/favicon.svg'], { android: true, web: true }],
  ['Vercel settings', ['vercel.json'], { android: false, web: true }],
  ['unrelated changes', ['README.md', 'docs/data-sync.md', 'backend/main.go', 'functions/index.ts', '.github/workflows/ci.yml', 'scripts/ci/detect-changes.mjs'], { android: false, web: false }],
  ['similar directory names', ['android-other/file', 'frontend-backup/file', 'docs/android/file'], { android: false, web: false }],
  ['no changes', [], { android: false, web: false }],
]) {
  test(`routing: ${name}`, () => assert.deepEqual(classifyPaths(paths), expected));
}

test('a push includes every commit in the pushed range', (t) => {
  const r = repository(t);
  r.write('android/app/first.kt');
  r.commit();
  r.write('frontend/src/second.ts');
  const head = r.commit();
  assert.deepEqual(classifyPaths(changedPaths('push', r.push(r.initial, head), r.cwd)),
    { android: true, web: true });
});

test('unrelated pushes and empty commits skip both applications', (t) => {
  const r = repository(t);
  r.write('docs/guide.md');
  const head = r.commit();
  for (const before of [r.initial, head]) {
    assert.deepEqual(classifyPaths(changedPaths('push', r.push(before, head), r.cwd)),
      { android: false, web: false });
  }
});

test('deletions and moves across applications trigger the affected builds', (t) => {
  const r = repository(t);
  r.write('android/app/old file.kt');
  const before = r.commit();
  r.git('mv', 'android/app/old file.kt', 'moved-file');
  r.write('frontend/src/new file.ts');
  const head = r.commit();
  const paths = changedPaths('push', r.push(before, head), r.cwd);
  assert.ok(paths.includes('android/app/old file.kt'));
  assert.deepEqual(classifyPaths(paths), { android: true, web: true });
  r.git('rm', 'frontend/src/new file.ts');
  assert.deepEqual(classifyPaths(changedPaths('push', r.push(head, r.commit()), r.cwd)),
    { android: false, web: true });
});

test('an exact rename from Android to web counts both the old and new path', (t) => {
  const r = repository(t);
  r.write('android/shared.json', '{}');
  const before = r.commit();
  r.git('mv', 'android', 'frontend');
  assert.deepEqual(classifyPaths(changedPaths('push', r.push(before, r.commit()), r.cwd)),
    { android: true, web: true });
});

test('PR routing excludes changes made only on the base branch', (t) => {
  const r = repository(t);
  r.git('checkout', '--quiet', '-b', 'feature');
  r.write('frontend/src/change.ts');
  const head = r.commit();
  r.git('checkout', '--quiet', 'main');
  r.write('android/app/base-only.kt');
  const base = r.commit();
  const event = { pull_request: { base: { sha: base }, head: { sha: head } } };
  assert.deepEqual(classifyPaths(changedPaths('pull_request', event, r.cwd)),
    { android: false, web: true });
});

test('first default-branch push checks all files in its tree', (t) => {
  const r = repository(t);
  r.write('android/app/source.kt');
  r.write('frontend/src/source.ts');
  assert.deepEqual(classifyPaths(changedPaths('push', r.push(zero, r.commit()), r.cwd)),
    { android: true, web: true });
});

test('first feature-branch push compares with the default branch', (t) => {
  const r = repository(t);
  r.write('android/app/existing.kt');
  const base = r.commit();
  r.git('update-ref', 'refs/remotes/origin/main', base);
  r.git('checkout', '--quiet', '-b', 'feature');
  r.write('frontend/src/new.ts');
  const event = r.push(zero, r.commit(), { ref: 'refs/heads/feature' });
  assert.deepEqual(classifyPaths(changedPaths('push', event, r.cwd)),
    { android: false, web: true });
});

test('force pushes compare the old and new trees even if history diverged', (t) => {
  const r = repository(t);
  r.write('android/app/removed.kt');
  const before = r.commit();
  r.git('checkout', '--quiet', '-b', 'replacement', r.initial);
  r.write('frontend/src/added.ts');
  assert.deepEqual(classifyPaths(changedPaths('push', r.push(before, r.commit()), r.cwd)),
    { android: true, web: true });
});

test('CLI writes explicit GitHub outputs and summary', (t) => {
  const r = repository(t);
  r.write('frontend/src/name with spaces.ts');
  const head = r.commit();
  const eventPath = path.join(r.cwd, 'event.json');
  const outputPath = path.join(r.cwd, 'output.txt');
  const summaryPath = path.join(r.cwd, 'summary.md');
  writeFileSync(eventPath, JSON.stringify(r.push(r.initial, head)));
  execFileSync(process.execPath, [detector], {
    cwd: r.cwd,
    env: {
      ...process.env,
      GITHUB_EVENT_NAME: 'push', GITHUB_EVENT_PATH: eventPath,
      GITHUB_OUTPUT: outputPath, GITHUB_STEP_SUMMARY: summaryPath,
    },
  });
  assert.equal(readFileSync(outputPath, 'utf8'), 'android=false\nweb=true\ndatabase=false\nsupabase=false\ncloudflare=false\nhosted=false\n');
  assert.match(readFileSync(summaryPath, 'utf8'), /Web: true/);
});

test('branch deletion skips both builds without inspecting a missing commit', () => {
  assert.deepEqual(changedPaths('push', { deleted: true }), []);
});

test('unsupported events and malformed SHAs fail instead of silently skipping', () => {
  assert.throws(() => changedPaths('workflow_dispatch', {}), /Unsupported GitHub event/);
  assert.throws(() => changedPaths('push', { after: '--all' }), /full commit SHA/);
});

test('hosted deployment routes are selective', () => {
  assert.deepEqual(classifyDeploymentPaths(['functions/cloudflare/gateway.mjs']), { database: false, supabase: false, cloudflare: true, hosted: true, pipeline: false });
  assert.deepEqual(classifyDeploymentPaths(['functions/shared/api.mjs']), { database: false, supabase: true, cloudflare: false, hosted: true, pipeline: false });
  assert.deepEqual(classifyDeploymentPaths(['functions/supabase/supabase/migrations/20261119000200_agap_api_grants.sql']), { database: true, supabase: true, cloudflare: false, hosted: true, pipeline: false });
  assert.deepEqual(classifyDeploymentPaths(['frontend/src/App.tsx']), { database: false, supabase: false, cloudflare: false, hosted: false, pipeline: false });
  assert.deepEqual(classifyDeploymentPaths(['.github/workflows/ci.yml']), { database: true, supabase: true, cloudflare: true, hosted: true, pipeline: true });
});
