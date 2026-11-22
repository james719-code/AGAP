import { deny, isCitizen } from './access.mjs';
import { json } from './http.mjs';
import { terminal } from './incidents.mjs';

const operational = 'i.is_demo = 0';
const dispatchJoin = `FROM incident_dispatches d JOIN incidents i ON i.id = d.incident_id
  JOIN rescue_units u ON u.id = d.unit_id`;
const breakdown = (label, from, group) => ({
  sql: `SELECT ${label} AS label, COUNT(*) AS count ${from} WHERE ${operational}
    GROUP BY ${group} ORDER BY count DESC, label`,
  args: [],
});

const minutesBetween = (from, to) => {
  if (!from || !to) return null;
  const elapsed = Date.parse(to) - Date.parse(from);
  return Number.isFinite(elapsed) && elapsed >= 0 ? elapsed / 60_000 : null;
};

const average = (values) => {
  const valid = values.filter((value) => value !== null);
  return valid.length ? valid.reduce((sum, value) => sum + value, 0) / valid.length : null;
};

const labelled = (result) => result.rows.map(({ label, count }) => ({ label: String(label ?? ''), count: Number(count) }));

// Same shape as the Go backend's GET /reports/operations. Counts and breakdowns
// are aggregated in SQL; timestamps are stored as text on every hosted
// database, so the four averages are computed here from one narrow query.
export async function operationalReport(db) {
  const activeSince = new Date(Date.now() - 15 * 60_000).toISOString();
  const [totals, responders, statuses, types, barangays, agencies, assignments, units, timings, arrivals] = await db.batch([
    { sql: `SELECT COUNT(*) AS total,
        COUNT(CASE WHEN i.status NOT IN (${terminal}) THEN 1 END) AS active,
        COUNT(CASE WHEN i.status = 'RESOLVED' THEN 1 END) AS resolved,
        COUNT(CASE WHEN i.status = 'FALSE_ALARM' OR i.false_positive = 1 THEN 1 END) AS false_alarms
        FROM incidents i WHERE ${operational}`, args: [] },
    { sql: `SELECT COUNT(*) AS total,
        COUNT(CASE WHEN status <> 'OFFLINE' AND last_location_at >= ? THEN 1 END) AS active
        FROM rescue_units`, args: [activeSince] },
    breakdown('i.status', 'FROM incidents i', 'i.status'),
    breakdown('i.incident_type', 'FROM incidents i', 'i.incident_type'),
    breakdown("i.municipality_code || ' / ' || i.barangay_code", 'FROM incidents i', 'i.municipality_code, i.barangay_code'),
    breakdown('a.name', `${dispatchJoin} JOIN agencies a ON a.id = u.agency_id`, 'a.id, a.name'),
    breakdown("COALESCE(NULLIF(u.responder_name, ''), u.unit_code)", dispatchJoin, 'u.id, u.responder_name, u.unit_code'),
    breakdown('u.unit_code', dispatchJoin, 'u.id, u.unit_code'),
    { sql: `SELECT i.received_at, i.resolved_at,
        (SELECT MIN(h.created_at) FROM incident_status_history h
          WHERE h.incident_id = i.id AND h.status = 'ACKNOWLEDGED') AS acknowledged_at,
        (SELECT MIN(d.dispatched_at) FROM incident_dispatches d WHERE d.incident_id = i.id) AS dispatched_at
        FROM incidents i WHERE ${operational}`, args: [] },
    { sql: `SELECT d.dispatched_at, d.arrived_at FROM incident_dispatches d JOIN incidents i ON i.id = d.incident_id
        WHERE ${operational} AND d.arrived_at IS NOT NULL`, args: [] },
  ], 'read');

  const counts = totals.rows[0];
  const total = Number(counts.total);
  const falseAlarms = Number(counts.false_alarms);
  return {
    generated_at: new Date().toISOString(),
    total_incidents: total,
    active_incidents: Number(counts.active),
    resolved_incidents: Number(counts.resolved),
    false_alarms: falseAlarms,
    false_alarm_rate: total ? (falseAlarms * 100) / total : null,
    average_ack_minutes: average(timings.rows.map((row) => minutesBetween(row.received_at, row.acknowledged_at))),
    average_dispatch_minutes: average(timings.rows.map((row) => minutesBetween(row.received_at, row.dispatched_at))),
    average_arrival_minutes: average(arrivals.rows.map((row) => minutesBetween(row.dispatched_at, row.arrived_at))),
    average_response_minutes: average(timings.rows.map((row) => minutesBetween(row.received_at, row.resolved_at))),
    active_responders: Number(responders.rows[0].active),
    total_responders: Number(responders.rows[0].total),
    status_distribution: labelled(statuses),
    incident_type_distribution: labelled(types),
    barangay_distribution: labelled(barangays),
    agency_dispatches: labelled(agencies),
    responder_assignments: labelled(assignments),
    unit_deployments: labelled(units),
  };
}

export async function reportRoute(path, request, db, user) {
  if (path !== '/reports/operations' || request.method !== 'GET') return;
  // Matches the Go backend: responders and administrators only.
  if (isCitizen(user)) deny('Responder access is required');
  return json(await operationalReport(db));
}
