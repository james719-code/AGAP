import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const source = fileURLToPath(new URL('../', import.meta.url));
const bash = process.platform === 'win32' ? 'C:/Program Files/Git/bin/bash.exe' : 'bash';

function fixture(t, flags = '') {
  const root = mkdtempSync(join(tmpdir(), 'agap-self-host-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const deployment = join(root, 'deploy');
  const bin = join(root, 'bin');
  mkdirSync(deployment);
  mkdirSync(bin);
  for (const name of ['setup-server.sh', 'deploy.sh', 'nginx.conf', 'agap-backend.service', 'tailscale-funnel.service']) {
    copyFileSync(join(source, name), join(deployment, name));
  }
  writeFileSync(join(deployment, 'deploy.conf'), `DEPLOY_HOST=fixture.invalid\nDEPLOY_USER=fixture\nREMOTE_DIR=/home/fixture/agap\n${flags}\n`);
  writeFileSync(join(deployment, 'config.env'), 'DB_FILE_PATH=/fixture.db\n');
  // These commands record all requested remote actions without connecting to a server.
  writeFileSync(join(bin, 'ssh'), '#!/usr/bin/env bash\nset -eu\nfor arg in "$@"; do last="$arg"; done\nprintf "%s\\n" "$last" >> "$AGAP_TEST_SSH_LOG"\nbash -n -c "$last"\n', { mode: 0o755 });
  writeFileSync(join(bin, 'scp'), '#!/usr/bin/env bash\nset -eu\nprevious=""\nlast=""\nfor arg in "$@"; do previous="$last"; last="$arg"; done\ntest -f "$previous"\nprintf "%s\\n" "$last" >> "$AGAP_TEST_SCP_LOG"\n', { mode: 0o755 });
  const sshLog = join(root, 'ssh.log');
  const scpLog = join(root, 'scp.log');
  const shellPath = value => value.replaceAll('\\', '/');
  const run = (script, ...args) => spawnSync(bash, ['-c',
    'if command -v cygpath >/dev/null 2>&1; then bin=$(cygpath -u "$1"); else bin="$1"; fi; export PATH="$bin:$PATH"; test "$(command -v ssh)" = "$bin/ssh" && test "$(command -v scp)" = "$bin/scp" || exit 90; shift; bash "$@"',
    '--', shellPath(bin), shellPath(join(deployment, script)), ...args], {
    cwd: root, encoding: 'utf8', env: { ...process.env, AGAP_TEST_SSH_LOG: shellPath(sshLog), AGAP_TEST_SCP_LOG: shellPath(scpLog) },
  });
  return { run, ssh: () => existsSync(sshLog) ? readFileSync(sshLog, 'utf8') : '', scp: () => existsSync(scpLog) ? readFileSync(scpLog, 'utf8') : '' };
}

test('shared-server setup stages nginx, uploads backend service, and leaves Funnel alone', t => {
  const f = fixture(t);
  const result = f.run('setup-server.sh');
  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(f.ssh(), /if \[ 'false' = true \]; then/);
  assert.doesNotMatch(f.ssh(), /rm \/etc\/nginx\/sites-enabled\/default/);
  assert.match(f.scp(), /:\/tmp\/agap-backend\.service/);
  assert.doesNotMatch(f.scp(), /tailscale-funnel\.service/);
  const deploy = f.run('deploy.sh', '--restart');
  assert.equal(deploy.status, 0, deploy.stderr || deploy.stdout);
  assert.doesNotMatch(f.ssh(), /systemctl restart tailscale-funnel\.service/);
});

test('explicit activation can manage the AGAP site and Funnel', t => {
  const f = fixture(t, 'NGINX_ACTIVATE=true\nTAILSCALE_FUNNEL_ENABLE=true');
  const result = f.run('setup-server.sh');
  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(f.ssh(), /if \[ 'true' = true \]; then/);
  assert.match(f.scp(), /:\/tmp\/tailscale-funnel\.service/);
});

test('Funnel activation without the AGAP nginx site fails before SSH', t => {
  const f = fixture(t, 'TAILSCALE_FUNNEL_ENABLE=true');
  const result = f.run('setup-server.sh');
  assert.notEqual(result.status, 0);
  assert.match(result.stdout, /Activate the AGAP nginx site/);
  assert.equal(f.ssh(), '');
  assert.equal(f.scp(), '');
});
