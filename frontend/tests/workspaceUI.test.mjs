import assert from 'node:assert/strict';
import test from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

test('workspace controls have explicit clear and pagination affordances', async () => {
  const server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } });
  try {
    const { RegistrySearch } = await server.ssrLoadModule('/src/components/common/RegistrySearch.tsx');
    const { RegistryPagination } = await server.ssrLoadModule('/src/components/common/RegistryPagination.tsx');
    const { DataTableViewport } = await server.ssrLoadModule('/src/components/common/DataTableViewport.tsx');
    const search = renderToStaticMarkup(createElement(RegistrySearch, { label: 'Search agencies', placeholder: 'Search', value: 'test', onChange() {} }));
    assert.match(search, /aria-label="Clear search agencies"/);
    const page = renderToStaticMarkup(createElement(RegistryPagination, { total: 53, page: 3, pageSize: 25, onPageChange() {} }));
    assert.match(page, /51–53/);
    assert.match(page, /Page 3 of 3/);
    assert.match(page, /aria-label="Next page"[^>]*disabled|disabled[^>]*aria-label="Next page"/);
    const viewport = renderToStaticMarkup(createElement(DataTableViewport, { label: 'Agency records' }, createElement('table')));
    assert.match(viewport, /role="region" aria-label="Agency records"/);
  } finally {
    await server.close();
  }
});

test('dispatch unit selection is never silently reassigned when records refresh', async () => {
  const server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null } });
  try {
    const { retainUnitSelection, messagesForIncident } = await server.ssrLoadModule('/src/lib/workspaceSelection.ts');
    assert.equal(retainUnitSelection('2', [{ id: 1 }, { id: 2 }]), '2');
    assert.equal(retainUnitSelection('2', [{ id: 1 }, { id: 3 }]), '');
    assert.equal(retainUnitSelection('', [{ id: 1 }]), '');
    const messages = [{ id: 1, incident_id: 11 }, { id: 2, incident_id: 12 }, { id: 3, incident_id: 11 }];
    assert.deepEqual(messagesForIncident(messages, 11).map((message) => message.id), [1, 3]);
    assert.deepEqual(messagesForIncident(messages, undefined), []);
  } finally {
    await server.close();
  }
});
