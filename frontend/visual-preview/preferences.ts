export const DISPATCHER_PREFERENCES_EVENT = 'agap:visual-preview-preferences';
export interface DispatcherPreferences { soundEnabled: boolean; sidebarCollapsed: boolean }

let preferences: DispatcherPreferences = { soundEnabled: false, sidebarCollapsed: false };
export function loadDispatcherPreferences() { return { ...preferences }; }
export function saveDispatcherPreferences(next: DispatcherPreferences) {
  preferences = { ...next };
  window.dispatchEvent(new CustomEvent(DISPATCHER_PREFERENCES_EVENT, { detail: preferences }));
}
