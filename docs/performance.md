# Performance improvements — October 28, 2026

These changes target repeated work during incident loading and live updates. Measurements below use local synthetic data; they do not represent end-to-end latency, device frame timing, or battery measurements.

## Web incident processing

`deduplicateIncidents` now indexes incident UUIDs, packet UUIDs, and numeric IDs instead of scanning all preceding results for each row. Processing grows linearly with the number of records. It preserves input order, earlier-field precedence, and the earliest matching result when identities overlap. Only identities retained after a merge are indexed.

Local Node.js 22.15.0 benchmark, median of three runs per implementation:

| Unique incidents | Previous implementation | Indexed implementation |
| --- | ---: | ---: |
| 1,000 | 549.06 ms | 5.64 ms |
| 5,000 | 13,814.75 ms | 5.28 ms |

Run times vary with JIT warmup and other work on the machine. The regression suite also counts identity reads to catch a return to quadratic work without relying on timing thresholds.

Map icons are reused by visual appearance. This gives React Leaflet stable icon references during GPS updates, avoiding unnecessary calls to `setIcon` for unchanged marker styles. The cache is bounded by the finite color, selection, resolved, and online/offline variants.

From `frontend/`:

```powershell
npm.cmd test
npm.cmd run benchmark:incidents
npm.cmd run build
node node_modules/eslint/bin/eslint.js src/lib/incidentIdentity.ts src/components/map/IncidentMap.tsx
```

All six regression tests, the production build, and lint checks on changed source files pass. Full-project `npm.cmd run lint` still reports 14 existing errors and six warnings in untouched files. The existing large Three.js chunk warning also remains.

## Android snapshot caching

Incident caching now batches logical-ID lookups as well as primary-key lookups, keeping each query below 400 parameters. Mesh placeholders keep their local UUID when matched to a numeric server ID. Comparison occurs after this reconciliation, so unchanged mesh records no longer trigger writes and Room observer updates on every poll.

For an unchanged snapshot of 1,001 records, regression tests verify:

| Snapshot | Previous database reads | Current database reads | Current writes |
| --- | ---: | ---: | ---: |
| Cloud records | 1,004 | 3 | 0 |
| Reconciled mesh records | 1,004 | 6 | 0 |

Tests also check that real status/dispatch changes still write, mesh UUIDs remain stable, and large inserts use bounded batches.

For legacy duplicate logical IDs, reconciliation retains the first matching stored alias, preserving the prior lookup behavior.

From `android/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --console=plain
```

The full suite passed all 76 unit tests across 22 suites, including the initial five cache regression tests. After the final duplicate-ID compatibility refinement, all six cache tests passed with:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.travelingsalesman.agap.data.repository.IncidentCacheTest --offline --console=plain
```

Room-generated DAO and Kotlin compilation pass.

## Server incident timelines

Migration `035_incident_history_lookup_index.sql` adds an index on `incident_status_history(incident_id, created_at, id)`. Single and bulk timeline queries can select only the requested incidents in chronological order, with deterministic ID ordering for equal timestamps. Query-plan regression checks require the index and reject an unnecessary temporary sort.

The migration runs through the existing database migration mechanism when the updated backend opens its database. It adds an index without modifying incident records. Local verification uses temporary databases.

From `backend/`:

```powershell
go test ./... -count=1 -timeout=120s
go test ./internal/repository/sqlite -run '^$' -bench '^BenchmarkIncidentHistoryLookup$' -benchtime=400ms -count=1 -timeout=90s
```

The full backend test suite passes, including timeline ordering and query-plan checks. The benchmark fixture contains 100,000 history entries across 10,000 incidents and compares the same repository reads with and without the index.

| Timeline query | Without index | With index |
| --- | ---: | ---: |
| One incident, ten events | 9.114 ms | 0.0519 ms |
| Five incidents, fifty events | 11.875 ms | 0.1838 ms |

These are local repository query timings, excluding network latency and request handling.
