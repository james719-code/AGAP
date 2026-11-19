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

export function run(env = process.env, cwd = process.cwd()) {
  const event = JSON.parse(readFileSync(env.GITHUB_EVENT_PATH, 'utf8'));
  const isDispatch = env.GITHUB_EVENT_NAME === 'workflow_dispatch';
  const isDefault = env.GITHUB_REF === `refs/heads/${event.repository?.default_branch}`;
  const result = isDispatch ? {
    android: isDefault && event.inputs?.target === 'android',
    web: isDefault && event.inputs?.target === 'web',
  } : classifyPaths(changedPaths(env.GITHUB_EVENT_NAME, event, cwd));
  const output = `android=${result.android}\nweb=${result.web}\n`;
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
