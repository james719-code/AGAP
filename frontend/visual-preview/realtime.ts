const fixtureFeed = Object.freeze({
  isConnected: false,
  lastIncident: null,
  liveIncidents: [],
  clearLiveIncidents: () => {},
  lastResponderLocation: null,
  lastIncidentStatusChange: null,
  lastIncidentLocationUpdate: null,
  lastIncidentReportUpdate: null,
  lastResponderUnitUpdate: null,
  lastCatalogUpdate: null,
  lastTelemetryUpdateAt: null,
  lastIncidentMessage: null,
});

export const useWebSocket = () => fixtureFeed;
