import type { IncidentFilter, UserRole } from '@/types/api';
import {
  agencies, auditLogs, catalog, demoAdministrator, dispatches, emergencyTypes, incidentReports,
  incidents, messages, notifications, report, responders, users,
} from './fixtures';

export const API_BASE_URL = 'Synthetic fixtures (no backend connection)';
export const apiUnavailable = () => false;
export const subscribeApiAvailability = () => () => {};
const copy = <T>(value: T): T => structuredClone(value);
const blocked = async (): Promise<never> => { throw new Error('This is a read-only synthetic preview. No server or real records are connected.'); };

const readOnlyApi = {
  getCurrentUser: async () => copy(demoAdministrator),
  getUsers: async (role?: UserRole) => copy(users.filter((user) => !role || user.role === role)),
  getIncidents: async (filter: IncidentFilter = {}) => {
    const result = incidents.filter((item) => (!filter.type || item.incident_type === filter.type)
      && (!filter.status || item.status === filter.status) && (!filter.region || item.target_id.region === filter.region)
      && (!filter.closed_only || ['RESOLVED', 'CANCELLED', 'FALSE_ALARM'].includes(item.status)));
    const limit = filter.limit ?? 50;
    const offset = filter.offset ?? 0;
    const rows = result.slice(offset, offset + limit);
    return copy({ total: result.length, count: rows.length, limit, offset, incidents: rows });
  },
  getIncidentById: async (id: number) => copy(incidents.find((item) => item.id === id)),
  getIncidentMessages: async (id: number) => copy(messages(id)),
  getIncidentDispatches: async (id: number) => copy(dispatches.filter((item) => item.incident_id === id)),
  getIncidentDispatchesBulk: async (ids: number[]) => copy(Object.fromEntries(ids.map((id) => [id, dispatches.filter((item) => item.incident_id === id)]))),
  getIncidentStatusHistory: async (id: number) => copy([{ id: id * 100, incident_id: id, status: 'RECEIVED', changed_by: 9000, notes: 'Example incident received', created_at: incidents.find((item) => item.id === id)?.created_at }]),
  getIncidentReports: async (id: number) => copy({ success: true, reports: incidentReports(id) }),
  getAgencies: async () => copy(agencies),
  getResponders: async (agencyId?: number) => copy(responders.filter((unit) => agencyId == null || unit.agency_id === agencyId)),
  getRescueUnitMembers: async () => [],
  getRescueUnitDispatches: async (id: number) => copy(dispatches.filter((item) => item.unit_id === id)),
  getEmergencyTypes: async (includeInactive = false) => copy(emergencyTypes.filter((item) => includeInactive || item.is_active)),
  getMessageCatalog: async (includeInactive = false) => copy(catalog.filter((item) => includeInactive || item.is_active)),
  getOperationalReport: async () => copy(report),
  getAuditLogs: async (type?: string, entityId?: number | string, limit = 20, action?: string, from?: string, to?: string) => copy(auditLogs.filter((item) => (!type || item.entity_type === type)
    && (!entityId || String(item.entity_id) === String(entityId)) && (!action || item.action === action)
    && (!from || item.created_at.slice(0, 10) >= from) && (!to || item.created_at.slice(0, 10) <= to)).slice(0, limit)),
  getNotifications: async () => copy(notifications),
  getProvinces: async () => [{ id: 1, code: '05', name: 'Sample Province' }],
  getMunicipalities: async () => [{ id: 1, province_id: 1, code: '12', name: 'Sample Municipality' }],
  getBarangays: async () => [{ id: 1, municipality_id: 1, code: '01', name: 'Sample Barangay' }],
  getAddresses: async () => [],
  getHealth: async () => ({ status: 'preview', db: 'fixtures', uptime: 'not connected' }),
  getSystemInfo: async () => ({ status: 'preview', api: { name: 'Synthetic UI preview', version: 'fixture', environment: 'local visual test', server_time: new Date().toISOString(), uptime: 'not connected', websocket_endpoint: 'none', rate_limiting: {} }, database: { type: 'in-memory fixtures', status: 'not connected', ping: 'n/a', statistics: {} } }),
  getDatabaseTables: async () => [{ name: 'incidents (fixtures)', count: incidents.length }, { name: 'responders (fixtures)', count: responders.length }],
  getUserDevices: async () => [],
  getEmergencyContacts: async () => [],
};

// Unknown methods and all mutations fail closed. No fetch, token storage,
// backend forwarding, session creation, or WebSocket exists in this module.
export const apiService = new Proxy(readOnlyApi, {
  get(target, key) {
    return typeof key === 'string' && Object.hasOwn(target, key) ? Reflect.get(target, key) : blocked;
  },
});
