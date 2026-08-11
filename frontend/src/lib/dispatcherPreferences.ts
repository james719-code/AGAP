export const DISPATCHER_PREFERENCES_KEY = 'agap_dispatcher_settings';
export const DISPATCHER_PREFERENCES_EVENT = 'agap:dispatcher-preferences-changed';

export interface DispatcherPreferences {
  soundEnabled: boolean;
  sidebarCollapsed: boolean;
}

export const DEFAULT_DISPATCHER_PREFERENCES: DispatcherPreferences = {
  soundEnabled: false,
  sidebarCollapsed: false,
};

export function loadDispatcherPreferences(): DispatcherPreferences {
  try {
    const raw = localStorage.getItem(DISPATCHER_PREFERENCES_KEY);
    if (!raw) return DEFAULT_DISPATCHER_PREFERENCES;
    const parsed = JSON.parse(raw) as Partial<DispatcherPreferences> & { autoSiren?: boolean };
    return {
      soundEnabled:
        typeof parsed.soundEnabled === 'boolean'
          ? parsed.soundEnabled
          : typeof parsed.autoSiren === 'boolean'
            ? parsed.autoSiren
            : DEFAULT_DISPATCHER_PREFERENCES.soundEnabled,
      sidebarCollapsed:
        typeof parsed.sidebarCollapsed === 'boolean'
          ? parsed.sidebarCollapsed
          : DEFAULT_DISPATCHER_PREFERENCES.sidebarCollapsed,
    };
  } catch {
    return DEFAULT_DISPATCHER_PREFERENCES;
  }
}

export function saveDispatcherPreferences(preferences: DispatcherPreferences): void {
  localStorage.setItem(DISPATCHER_PREFERENCES_KEY, JSON.stringify(preferences));
  window.dispatchEvent(
    new CustomEvent<DispatcherPreferences>(DISPATCHER_PREFERENCES_EVENT, { detail: preferences }),
  );
}
