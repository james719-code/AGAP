import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { performance } from 'node:perf_hooks';
import test from 'node:test';
import ts from 'typescript';

// Exercise the shipped TypeScript without adding a separate test runtime.
const source = await readFile(new URL('../src/lib/incidentIdentity.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2023 },
});
const { deduplicateIncidents } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

// Previous implementation kept as a behavior oracle and benchmark baseline.
function identities(incident) {
  return [
    incident.incident_uuid ? `incident:${incident.incident_uuid}` : '',
    incident.packet_uuid ? `packet:${incident.packet_uuid}` : '',
    Number.isFinite(Number(incident.id)) ? `id:${incident.id}` : '',
  ].filter(Boolean);
}

function referenceDeduplicate(incidents) {
  const result = [];
  for (const incident of incidents) {
    const index = result.findIndex((current) => {
      const leftParts = new Set(identities(current));
      return identities(incident).some((part) => leftParts.has(part));
    });
    if (index === -1) result.push(incident);
    else result[index] = { ...incident, ...result[index] };
  }
  return result;
}

test('merges online and mesh identities in order while preserving earlier fields', () => {
  const first = Object.freeze({ id: 1, incident_uuid: 'incident-a', status: 'RECEIVED' });
  const packet = Object.freeze({ id: 2, incident_uuid: 'incident-a', packet_uuid: 'packet-a', status: 'QUEUED' });
  const later = Object.freeze({ id: 3, packet_uuid: 'packet-a', notes: 'more detail' });
  const input = Object.freeze([first, packet, later]);
  assert.deepEqual(deduplicateIncidents(input), [{
    id: 1, incident_uuid: 'incident-a', packet_uuid: 'packet-a', status: 'RECEIVED', notes: 'more detail',
  }]);
});

test('chooses the first result when a row bridges multiple identities', () => {
  const input = [
    { id: 1, incident_uuid: 'first' },
    { id: 2, packet_uuid: 'second' },
    { id: 2, incident_uuid: 'first', packet_uuid: 'second' },
    { id: 2, packet_uuid: 'second', status: 'RESOLVED' },
  ];
  assert.deepEqual(deduplicateIncidents(input), referenceDeduplicate(input));
  assert.equal(deduplicateIncidents(input).length, 2);
  assert.equal(deduplicateIncidents(input)[0].status, 'RESOLVED');
});

test('does not retain aliases that were overwritten by earlier fields', () => {
  const input = [
    { id: 1, packet_uuid: 'original', incident_uuid: undefined },
    { id: 1, packet_uuid: 'discarded', incident_uuid: 'also-discarded' },
    { id: 2, packet_uuid: 'discarded', incident_uuid: 'also-discarded' },
  ];
  assert.deepEqual(deduplicateIncidents(input), referenceDeduplicate(input));
  assert.equal(deduplicateIncidents(input).length, 2);
});

test('keeps records without usable identities and handles empty input', () => {
  assert.deepEqual(deduplicateIncidents([]), []);
  const input = [{ id: NaN }, { id: Infinity }, {}, { id: 0 }, { id: 0, status: 'RECEIVED' }];
  assert.deepEqual(deduplicateIncidents(input), referenceDeduplicate(input));
});

test('matches prior behavior across deterministic mixed identity sequences', () => {
  let seed = 20261028;
  const random = (max) => {
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
    return Math.floor(seed / 0x100000000 * max);
  };
  for (let trial = 0; trial < 300; trial++) {
    const input = Array.from({ length: 60 }, () => {
      const row = { status: random(2) ? 'RECEIVED' : 'RESOLVED' };
      if (random(4)) row.id = random(15);
      if (random(4)) row.incident_uuid = random(4) ? `incident-${random(15)}` : undefined;
      if (random(4)) row.packet_uuid = random(4) ? `packet-${random(15)}` : '';
      return row;
    });
    assert.deepEqual(deduplicateIncidents(input), referenceDeduplicate(input), `trial ${trial}`);
  }
});

test('identity reads grow linearly for a large incident refresh', () => {
  let reads = 0;
  const input = Array.from({ length: 2000 }, (_, id) => ({
    get id() { reads++; return id; },
    get incident_uuid() { reads++; return `incident-${id}`; },
    get packet_uuid() { reads++; return `packet-${id}`; },
  }));
  assert.equal(deduplicateIncidents(input).length, input.length);
  assert.ok(reads <= input.length * 20, `Too many identity reads: ${reads}`);
});

if (process.argv.includes('--benchmark')) {
  for (const count of [1000, 5000]) {
    const input = Array.from({ length: count }, (_, id) => ({
      id, incident_uuid: `incident-${id}`, packet_uuid: `packet-${id}`, status: 'RECEIVED',
    }));
    const measure = (fn) => {
      const samples = Array.from({ length: 3 }, () => {
        const start = performance.now();
        assert.equal(fn(input).length, count);
        return performance.now() - start;
      });
      return samples.sort((a, b) => a - b)[1];
    };
    const beforeMs = measure(referenceDeduplicate);
    const afterMs = measure(deduplicateIncidents);
    console.log(JSON.stringify({ count, beforeMs: +beforeMs.toFixed(2), afterMs: +afterMs.toFixed(2), speedup: +(beforeMs / afterMs).toFixed(1) }));
  }
}
