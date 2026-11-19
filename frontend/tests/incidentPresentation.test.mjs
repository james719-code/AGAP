import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/incidentPresentation.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2023 },
});
const { incidentQueueRank, incidentStatusTone, incidentStatusLabel, NEEDS_ASSIGNMENT_STATUSES } =
  await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

test('unassigned received incidents appear before in-progress, queued and closed records', () => {
  const statuses = ['RESOLVED', 'ON_SCENE', 'QUEUED', 'RECEIVED', 'FALSE_ALARM', 'ACKNOWLEDGED', 'EN_ROUTE'];
  assert.deepEqual([...statuses].sort((a, b) => incidentQueueRank(a) - incidentQueueRank(b)),
    ['RECEIVED', 'ACKNOWLEDGED', 'ON_SCENE', 'EN_ROUTE', 'QUEUED', 'RESOLVED', 'FALSE_ALARM']);
  assert.deepEqual([...NEEDS_ASSIGNMENT_STATUSES], ['RECEIVED', 'ACKNOWLEDGED']);
});

test('incident status tones distinguish awaiting response from progress and closure', () => {
  assert.equal(incidentStatusTone('RECEIVED'), 'tone-danger');
  assert.equal(incidentStatusTone('ON_SCENE'), 'tone-info');
  assert.equal(incidentStatusTone('QUEUED'), 'tone-warning');
  assert.equal(incidentStatusTone('RESOLVED'), 'tone-success');
  assert.equal(incidentStatusTone('CANCELLED'), '');
  assert.equal(incidentStatusTone('FALSE_ALARM'), '');
  assert.equal(incidentStatusLabel('EN_ROUTE'), 'En route');
});
