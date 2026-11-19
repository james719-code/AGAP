import { execFileSync } from 'node:child_process';
import { appendFileSync, readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

function git(cwd, ...args) {
  return execFileSync('git', args, { cwd, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 });
}

function commit(value) {
  if (!/^(?:[a-f\d]{40}|[a-f\d]{64})$/i.test(value ?? '')) {
    throw new Error('The GitHub event must contain a full commit SHA.');
  }
  return value;
}

function ensureCommit(cwd, sha) {
  try {
    git(cwd, 'cat-file', '-e', `${sha}^{commit}`);
  } catch {
    // Full-history checkout may still omit the old tip after a force push.
    // Fail the job if GitHub cannot supply it; never guess which app changed.
    git(cwd, 'fetch', '--no-tags', 'origin', sha);
  }
}

export function changedPaths(eventName, event, cwd = process.cwd()) {
  if (eventName === 'push' && event.deleted) return [];

  let head;
  let base;
  if (eventName === 'pull_request') {
    head = commit(event.pull_request?.head?.sha);
    base = commit(event.pull_request?.base?.sha);
    ensureCommit(cwd, head);
    ensureCommit(cwd, base);
    // Ignore changes made only on the target branch since the PR split off.
    base = git(cwd, 'merge-base', base, head).trim();
  } else if (eventName === 'push') {
    head = commit(event.after);
    ensureCommit(cwd, head);
    const before = commit(event.before);
    if (!/^0+$/.test(before)) {
      base = before;
      ensureCommit(cwd, base);
    } else {
      const defaultBranch = event.repository?.default_branch;
      const defaultRef = `refs/heads/${defaultBranch}`;
      if (defaultBranch && event.ref !== defaultRef) {
        const remoteRef = `refs/remotes/origin/${defaultBranch}`;
        base = git(cwd, 'merge-base', remoteRef, head).trim();
      }
      // The first push of the default branch has no previous tree.
      if (!base) {
        return git(cwd, 'ls-tree', '-r', '--name-only', '-z', head).split('\0').filter(Boolean);
      }
    }
  } else {
    throw new Error(`Unsupported GitHub event: ${eventName}`);
  }

  // Disabling rename detection includes BOTH paths when a file moves between apps.
  // NUL separators preserve spaces, tabs, and newlines in file names.
  return git(cwd, 'diff', '--no-renames', '--name-only', '-z', base, head, '--')
    .split('\0').filter(Boolean);
}

export function classifyPaths(paths) {
  return {
    android: paths.some((path) => path.startsWith('android/')),
    web: paths.some((path) => path.startsWith('frontend/') || path === 'vercel.json' || path === 'netlify.toml'),
  };
}

export function classifyDeploymentPaths(paths) {
  const pipeline = paths.some(p => p.startsWith('.github/workflows/') || p.startsWith('scripts/ci/'));
  const shared = paths.some(p => p.startsWith('functions/shared/') || p === 'functions/package.json' || p === 'functions/package-lock.json' || p === 'functions/scripts/stage.mjs');
  const database = pipeline || paths.some(p => p.startsWith('functions/supabase/supabase/migrations/'));
  const supabase = pipeline || shared || database || paths.some(p => p.startsWith('functions/supabase/'));
  const cloudflare = pipeline || paths.some(p => p.startsWith('functions/cloudflare/'));
  const hosted = supabase || cloudflare || paths.some(p => p.startsWith('functions/') || p.startsWith('backend/internal/repository/sqlite/migrations/'));
  return { database, supabase, cloudflare, hosted, pipeline };
}

export function run(env = process.env, cwd = process.cwd()) {
  const event = JSON.parse(readFileSync(env.GITHUB_EVENT_PATH, 'utf8'));
  const isDispatch = env.GITHUB_EVENT_NAME === 'workflow_dispatch';
  const isDefault = env.GITHUB_REF === `refs/heads/${event.repository?.default_branch}`;
  const target = event.inputs?.target;
  // A pending GitHub run can be superseded by a newer push. Diff from the
  // last successful push so skipped/failed production changes aren't lost.
  let comparisonEvent = event;
  if (env.GITHUB_EVENT_NAME === 'push' && isDefault && env.GH_TOKEN && env.GITHUB_REPOSITORY) {
    const raw = execFileSync('gh', ['api', '--method', 'GET',
      `repos/${env.GITHUB_REPOSITORY}/actions/workflows/ci.yml/runs`,
      '-f', `branch=${event.repository.default_branch}`, '-f', 'event=push', '-f', 'status=success', '-F', 'per_page=20'],
    { cwd, encoding: 'utf8', env });
    const runs = JSON.parse(raw).workflow_runs;
    const previous = runs.find(run => run.run_number < Number(env.GITHUB_RUN_NUMBER));
    comparisonEvent = { ...event, before: previous?.head_sha ?? '0'.repeat(40) };
  }
  const paths = isDispatch ? [] : changedPaths(env.GITHUB_EVENT_NAME, comparisonEvent, cwd);
  const result = isDispatch ? {
    android: isDefault && ['android','all'].includes(target),
    web: isDefault && ['web','all'].includes(target),
    database: isDefault && ['supabase','all'].includes(target),
    supabase: isDefault && ['supabase','all'].includes(target),
    cloudflare: isDefault && ['cloudflare','all'].includes(target),
    hosted: isDefault && ['supabase','cloudflare','all'].includes(target),
  } : { ...classifyPaths(paths), ...classifyDeploymentPaths(paths) };
  if (result.pipeline) result.web = result.android = true;
  delete result.pipeline;
  const output = Object.entries(result).map(([key,value]) => `${key}=${value}\n`).join('');
  if (env.GITHUB_OUTPUT) appendFileSync(env.GITHUB_OUTPUT, output);
  if (env.GITHUB_STEP_SUMMARY) {
    appendFileSync(env.GITHUB_STEP_SUMMARY,
      `### Changed applications\n\nAndroid: ${result.android}\n\nWeb: ${result.web}\n`);
  }
  process.stdout.write(output);
  return result;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  run();
}
