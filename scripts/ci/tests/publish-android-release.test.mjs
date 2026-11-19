import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { publishAndroidRelease } from '../publish-android-release.mjs';

const config = JSON.parse(readFileSync(new URL('../../../frontend/src/config/android-release.json', import.meta.url), 'utf8'));
const options = {
  eventName: 'push', ref: 'refs/heads/main', defaultBranch: 'main', buildResult: 'success',
  repository: config.repository, sha: 'a'.repeat(40), runNumber: '42',
};

function fixture(t, overrides = {}) {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'agap-release-'));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const apkPath = path.join(dir, config.assetName);
  writeFileSync(apkPath, 'apk-fixture');
  const calls = [];
  const gh = (args) => {
    calls.push(args);
    if (args[1] === 'view') {
      if (args.includes('isDraft,url')) {
        if (overrides.existing) return JSON.stringify(overrides.existing);
        throw new Error('release not found');
      }
      return JSON.stringify({ tagName: overrides.latest ?? 'android-40-bbbbbbbbbbbb' });
    }
    if (overrides.uploadFails && ['create', 'upload'].includes(args[1])) throw new Error('Upload failed');
    return '';
  };
  return { args: { ...options, apkPath }, calls, gh };
}

for (const buildResult of ['failure', 'cancelled', 'skipped', undefined]) {
  test(`${buildResult} Android builds never create or update a release`, () => {
    assert.equal(publishAndroidRelease({ ...options, buildResult }, () => assert.fail('GitHub must not be called')), null);
  });
}

test('PRs and non-default branches never publish a release', () => {
  for (const override of [{ eventName: 'pull_request' }, { ref: 'refs/heads/feature' }]) {
    assert.equal(publishAndroidRelease({ ...options, ...override }, () => assert.fail('GitHub must not be called')), null);
  }
});

test('only a successful default-branch Android dispatch can publish manually', (t) => {
  const { args, gh } = fixture(t);
  assert.equal(publishAndroidRelease({ ...args, eventName: 'workflow_dispatch', manualTarget: 'web' }, () => assert.fail()), null);
  assert.equal(publishAndroidRelease({ ...args, eventName: 'workflow_dispatch', manualTarget: 'android', ref: 'refs/heads/feature' }, () => assert.fail()), null);
  assert.match(publishAndroidRelease({ ...args, eventName: 'workflow_dispatch', manualTarget: 'android' }, gh), /\/releases\/tag\/android-42-/);
});

test('successful builds upload to a draft before marking it public and latest', (t) => {
  const f = fixture(t);
  const url = publishAndroidRelease(f.args, f.gh);
  const mutations = f.calls.filter((args) => args[1] !== 'view');
  assert.equal(mutations.length, 2);
  assert.equal(mutations[0][1], 'create');
  assert.ok(mutations[0].includes('--draft'));
  assert.ok(mutations[0].includes(f.args.apkPath));
  assert.equal(mutations[0][mutations[0].indexOf('--target') + 1], options.sha);
  assert.ok(mutations[1].includes('--draft=false'));
  assert.ok(mutations[1].includes('--latest=true'));
  assert.equal(url, `https://github.com/${config.repository}/releases/tag/android-42-aaaaaaaaaaaa`);
});

test('upload failures never publish or replace the previous release', (t) => {
  const f = fixture(t, { uploadFails: true });
  assert.throws(() => publishAndroidRelease(f.args, f.gh), /Upload failed/);
  assert.equal(f.calls.some((args) => args[1] === 'edit'), false);
});

test('retrying a partial upload resumes its draft', (t) => {
  const f = fixture(t, { existing: { isDraft: true } });
  publishAndroidRelease(f.args, f.gh);
  assert.equal(f.calls.some((args) => args[1] === 'create'), false);
  assert.ok(f.calls.some((args) => args[1] === 'upload' && args.includes('--clobber')));
  assert.ok(f.calls.some((args) => args[1] === 'edit'));
});

test('retrying a published build leaves its release and assets unchanged', (t) => {
  const f = fixture(t, { existing: { isDraft: false, url: 'existing-release' } });
  assert.equal(publishAndroidRelease(f.args, f.gh), 'existing-release');
  assert.equal(f.calls.length, 1);
});

test('an older finishing build cannot replace a newer latest APK', (t) => {
  const f = fixture(t, { latest: 'android-43-bbbbbbbbbbbb' });
  publishAndroidRelease(f.args, f.gh);
  assert.ok(f.calls.at(-1).includes('--latest=false'));
});

test('first successful build can become latest when no release exists', (t) => {
  const f = fixture(t);
  publishAndroidRelease(f.args, (args) => {
    if (args[1] === 'view') throw new Error('release not found');
    return f.gh(args);
  });
  assert.ok(f.calls.at(-1).includes('--latest=true'));
});

test('missing or empty APKs cannot create a release', (t) => {
  const f = fixture(t);
  writeFileSync(f.args.apkPath, '');
  assert.throws(() => publishAndroidRelease(f.args, f.gh), /nonempty/);
  assert.equal(f.calls.length, 0);
});

test('GitHub authentication errors stop publishing instead of treating a release as absent', (t) => {
  const f = fixture(t);
  assert.throws(() => publishAndroidRelease(f.args, () => { throw new Error('HTTP 401: Bad credentials'); }), /401/);
});
