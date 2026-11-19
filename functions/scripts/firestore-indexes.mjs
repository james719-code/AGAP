const indexes = new Map();
const field = (name, order = 'ASCENDING') => order === 'CONTAINS'
  ? { fieldPath: name, arrayConfig: order } : { fieldPath: name, order };
function index(table, fields) {
  if (fields.length < 2) return;
  const record = { collectionGroup: `agap_${table}`, queryScope: 'COLLECTION', fields };
  indexes.set(JSON.stringify(record), record);
}
const asc = (...names) => names.map((name) => field(name));
const desc = (...names) => names.map((name) => field(name, 'DESCENDING'));
function subsets(fields) {
  return fields.reduce((result, next) => [...result, ...result.map((values) => [...values, next])], [[]]);
}

for (const filters of subsets(['status', 'incident_type', 'region_code', 'is_demo'])) {
  for (const citizen of [[], [field('visible_to', 'CONTAINS')]]) {
    index('incidents', [...citizen, ...asc(...filters), ...desc('received_at', 'id')]);
  }
}
index('incidents', asc('updated_at', 'id'));
index('incidents', [field('visible_to', 'CONTAINS'), ...asc('updated_at', 'id')]);
for (const columns of subsets(['entity_type', 'entity_id', 'action'])) {
  index('audit_logs', [...asc(...columns), ...desc('created_at', 'id')]);
}
for (const name of ['incident_id', 'unit_id']) {
  index('incident_dispatches', [...asc(name), ...desc('dispatched_at', 'id')]);
  index('incident_dispatches', asc(name, 'status'));
}
index('incident_dispatches', [...asc('unit_id'), ...desc('id')]);
index('incident_dispatches', asc('incident_id', 'unit_id', 'status'));
index('incident_status_history', asc('incident_id', 'created_at', 'id'));
index('incident_messages', asc('incident_id', 'sent_at', 'id'));
index('incident_reports', asc('incident_id', 'id'));
index('incident_reports', [...asc('reporter_user_id'), ...desc('id')]);
index('admin_alerts', [...asc('is_active'), ...desc('is_pinned', 'published_at', 'id')]);
index('admin_alerts', desc('is_pinned', 'published_at', 'id'));
index('admin_alerts', asc('is_active', 'expires_at'));
index('notifications', [...asc('user_id'), ...desc('sent_at', 'id')]);
index('notifications', [...asc('user_id', 'is_read'), ...desc('sent_at', 'id')]);
index('rescue_unit_members', [...asc('rescue_unit_id', 'removed_at'), ...desc('is_leader'), ...asc('id')]);
index('rescue_units', asc('agency_id', 'id'));
index('rescue_units', asc('user_id', 'id'));
index('users', asc('role', 'id'));
index('predefined_messages', asc('is_active', 'id'));
index('emergency_types', asc('is_active', 'id'));
index('user_devices', asc('user_id', 'id'));
index('user_emergency_contacts', asc('user_id', 'id'));
index('municipalities', asc('province_id', 'name'));
index('barangays', asc('municipality_id', 'name'));
index('addresses', asc('barangay_id', 'street_address'));
console.log(JSON.stringify({ indexes: [...indexes.values()], fieldOverrides: [] }, null, 2));
