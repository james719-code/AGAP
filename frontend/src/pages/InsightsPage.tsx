// The live backend exposes operational incident/report inputs, but no readable
// mesh-analytics endpoint. Keep the legacy page name as an alias so old imports
// resolve to the supported report view without issuing nonexistent requests.
export { TelemetryReportsPage as InsightsPage } from '@/pages/TelemetryReportsPage';
