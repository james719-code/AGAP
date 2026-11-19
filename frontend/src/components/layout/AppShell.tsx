import React, { useCallback, useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { NavLink, Outlet, useLocation, useNavigate, Link } from 'react-router-dom';
import {
  LayoutDashboard,
  MapPin,
  BarChart3,
  MessageSquare,
  AlertTriangle,
  Building2,
  Users,
  Archive,
  Volume2,
  VolumeX,
  Bell,
  Settings,
  HelpCircle,
  LogOut,
  BookOpenText,
  ShieldCheck,
  CheckCheck,
  ChevronDown,
  ChevronRight,
  ClipboardList,
  ListFilter,
  Menu,
  Moon,
  Sun,
  Radio,
} from 'lucide-react';
import { apiService, apiUnavailable, subscribeApiAvailability } from '@/services/api';
import { hostedSecondaryRefreshMs, usesHostedApi } from '@/services/backend';
import { useVisibleRefresh } from '@/hooks/useVisibleRefresh';
import type { Notification } from '@/types/api';
import { useWebSocket } from '@/context/WebSocketContext';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import { audioAlert } from '@/lib/audioAlert';
import {
  DISPATCHER_PREFERENCES_EVENT,
  loadDispatcherPreferences,
  saveDispatcherPreferences,
  type DispatcherPreferences,
} from '@/lib/dispatcherPreferences';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { SettingsModal } from '@/components/settings/SettingsModal';
import agapLogo from '../../assets/logos/logo-name-nobg.png';

const navigationGroups = [
  {
    group: 'Dispatch',
    items: [
      { to: '/dashboard', label: 'Overview', icon: LayoutDashboard, exact: true },
      { to: '/dashboard/map', label: 'Live Map', icon: MapPin, exact: false },
      { to: '/dashboard/incidents', label: 'Incidents', icon: AlertTriangle, exact: false },
      { to: '/dashboard/chat', label: 'Incident Chat', icon: MessageSquare, exact: false },
    ],
  },
  {
    group: 'Administration',
    items: [
      { to: '/dashboard/team', label: 'Responders', icon: Users, exact: false },
      { to: '/dashboard/users', label: 'Users', icon: ShieldCheck, exact: false },
      { to: '/dashboard/agencies', label: 'Agencies', icon: Building2, exact: false },
      { to: '/dashboard/emergency-types', label: 'Emergency Types', icon: ListFilter, exact: false },
      { to: '/dashboard/messages', label: 'Message Catalog', icon: BookOpenText, exact: false },
    ],
  },
  {
    group: 'Records',
    items: [
      { to: '/dashboard/telemetry-reports', label: 'Reports', icon: BarChart3, exact: false },
      { to: '/dashboard/audit-logs', label: 'Audit Logs', icon: ClipboardList, exact: false },
      { to: '/dashboard/archive', label: 'Incident Archive', icon: Archive, exact: false },
    ],
  },
];

function WorkspaceNavigation({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav aria-label="Command center" className="space-y-4">
      {navigationGroups.map(({ group, items }) => (
        <div key={group}>
          <p className="workspace-group-label mb-1.5 px-3">{group}</p>
          <ul className="space-y-0.5">
            {items.map(({ to, label, icon: Icon, exact }) => (
              <li key={to}>
                <NavLink
                  to={to}
                  end={exact}
                  onClick={onNavigate}
                  className={({ isActive }) => cn('workspace-link', isActive && 'workspace-link-active')}
                >
                  <Icon className="size-4 shrink-0" aria-hidden="true" />
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );
}

function timeAgo(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diffMs / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  return `${days}d ago`;
}

function notificationLabel(n: Notification): string {
  switch (n.type) {
    case 'NEW_INCIDENT':
      return 'New distress incident received';
    case 'INCIDENT_STATUS_UPDATED':
      return 'Incident status updated';
    case 'DISPATCH_ASSIGNED':
      return 'A responder unit was dispatched';
    case 'RESPONDER_LOCATION_UPDATED':
      return 'Responder location updated';
    default:
      return n.type.replace(/_/g, ' ').toLowerCase();
  }
}

export const AppShellContent: React.FC = () => {
  const serviceUnavailable = useSyncExternalStore(subscribeApiAvailability, apiUnavailable, () => false);
  const [soundEnabled, setSoundEnabled] = useState(() => loadDispatcherPreferences().soundEnabled);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [notificationsLoading, setNotificationsLoading] = useState(false);
  const [notificationsError, setNotificationsError] = useState<string | null>(null);
  const [soundError, setSoundError] = useState<string | null>(null);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [signOutConfirmOpen, setSignOutConfirmOpen] = useState(false);
  const [navigationOpen, setNavigationOpen] = useState(false);
  const { lastIncident, isConnected } = useWebSocket();
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const notificationsRef = useRef<HTMLDivElement>(null);
  const userMenuRef = useRef<HTMLDivElement>(null);
  const lastHostedAlertRef = useRef<number | null>(null);

  const unreadCount = notifications.filter((n) => !n.is_read).length;

  useEffect(() => {
    audioAlert.setEnabled(soundEnabled);
  }, [soundEnabled]);

  useEffect(() => {
    const handlePreferencesChanged = (event: Event) => {
      const preferences = (event as CustomEvent<DispatcherPreferences>).detail;
      setSoundEnabled(preferences.soundEnabled);
    };
    window.addEventListener(DISPATCHER_PREFERENCES_EVENT, handlePreferencesChanged);
    return () => window.removeEventListener(DISPATCHER_PREFERENCES_EVENT, handlePreferencesChanged);
  }, []);

  const refreshNotifications = useCallback(async (silent = false) => {
    try {
      if (!silent) {
        setNotificationsLoading(true);
        setNotificationsError(null);
      }
      const data = await apiService.getNotifications();
      const sorted = [...data].sort((a, b) => new Date(b.sent_at).getTime() - new Date(a.sent_at).getTime());
      if (usesHostedApi && lastHostedAlertRef.current === null) {
        lastHostedAlertRef.current = sorted.find((notification) => notification.type === 'NEW_INCIDENT')?.id ?? 0;
      }
      setNotifications(sorted);
    } catch (error) {
      if (!silent) setNotificationsError(error instanceof Error ? error.message : 'Could not load notifications.');
      return false;
    } finally {
      if (!silent) setNotificationsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (user) {
      queueMicrotask(() => void refreshNotifications());
    }
  }, [user, refreshNotifications]);
  useVisibleRefresh(refreshNotifications, usesHostedApi && user ? hostedSecondaryRefreshMs : 0);

  useEffect(() => {
    if (!usesHostedApi) return;
    const newestAlert = notifications.find((notification) => notification.type === 'NEW_INCIDENT')?.id;
    const previous = lastHostedAlertRef.current;
    if (newestAlert == null || previous === null || newestAlert <= previous) return;
    lastHostedAlertRef.current = newestAlert;
    if (soundEnabled) {
      void audioAlert.playEmergencySiren().then((played) => {
        if (!played) setSoundError('Browser audio is blocked. Turn sound off and on again.');
      });
    }
  }, [notifications, soundEnabled]);

  useEffect(() => {
    if (lastIncident) {
      queueMicrotask(() => void refreshNotifications());
      if (soundEnabled) {
        void audioAlert.playEmergencySiren().then((played) => {
          if (!played) setSoundError('Browser audio is blocked. Turn sound off and on again.');
        });
      }
    }
  }, [lastIncident, refreshNotifications, soundEnabled]);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (notificationsRef.current && !notificationsRef.current.contains(e.target as Node)) {
        setNotificationsOpen(false);
      }
      if (userMenuRef.current && !userMenuRef.current.contains(e.target as Node)) {
        setUserMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const toggleSound = async () => {
    const nextState = !soundEnabled;
    audioAlert.setEnabled(nextState);
    setSoundEnabled(nextState);
    saveDispatcherPreferences({ ...loadDispatcherPreferences(), soundEnabled: nextState });
    setSoundError(null);
    if (nextState) {
      const played = await audioAlert.playEmergencySiren();
      if (!played) setSoundError('Browser audio is blocked. Check this site’s sound permission.');
    }
  };

  const handleBellClick = () => {
    setNotificationsOpen((prev) => {
      const next = !prev;
      if (next) refreshNotifications();
      return next;
    });
  };

  const handleMarkRead = async (n: Notification) => {
    if (n.is_read) return;
    setNotifications((prev) => prev.map((x) => (x.id === n.id ? { ...x, is_read: true, read_at: new Date().toISOString() } : x)));
    try {
      await apiService.markNotificationRead(n.id);
    } catch {
      setNotifications((prev) => prev.map((x) => (x.id === n.id ? { ...x, is_read: false, read_at: undefined } : x)));
    }
  };

  const handleMarkAllRead = async () => {
    const unread = notifications.filter((n) => !n.is_read);
    if (unread.length === 0) return;
    setNotifications((prev) => prev.map((n) => ({ ...n, is_read: true, read_at: n.read_at || new Date().toISOString() })));
    try {
      await Promise.all(unread.map((n) => apiService.markNotificationRead(n.id)));
    } catch {
      refreshNotifications();
    }
  };

  const handleNotificationClick = (n: Notification) => {
    handleMarkRead(n);
    if (n.incident_id) {
      setNotificationsOpen(false);
      navigate(`/dashboard/incidents?incidentId=${n.incident_id}`);
    }
  };

  const handleSignOut = () => {
    setSignOutConfirmOpen(true);
  };

  const confirmSignOut = () => {
    setSignOutConfirmOpen(false);
    logout();
    navigate('/login');
  };

  const activeGroup = navigationGroups.find((group) => group.items.some((item) => item.to === pathname));
  const activePage = activeGroup?.items.find((item) => item.to === pathname);
  const feedLabel = serviceUnavailable ? 'Service unavailable' : usesHostedApi ? 'Auto-refresh enabled' : isConnected ? 'Feed connected' : 'Feed disconnected';

  return (
    <div className="workspace flex min-h-dvh flex-col bg-background font-sans text-foreground antialiased">
      <a href="#workspace-content" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-2 focus:z-50 focus:rounded-md focus:bg-card focus:px-4 focus:py-2">
        Skip to main content
      </a>
      <header className="sticky top-0 z-40 flex h-16 shrink-0 items-center justify-between gap-2 border-b border-border bg-card px-3 sm:px-5">
        <div className="flex min-w-0 items-center gap-2.5">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label="Open workspace navigation"
            aria-haspopup="dialog"
            onClick={() => setNavigationOpen(true)}
            className="shrink-0 lg:hidden"
          >
            <Menu className="size-5" />
          </Button>
          <Link to="/dashboard" aria-label="AGAP Command Center overview" className="flex shrink-0 items-center gap-2.5 rounded-sm">
            <span className="grid h-9 place-items-center rounded-lg bg-white px-2"><img src={agapLogo} alt="" className="h-5 w-auto object-contain" /></span>
            <span className="hidden text-sm font-bold sm:inline">Command Center</span>
          </Link>
          {activePage && (
            <div className="ml-3 hidden min-w-0 items-center gap-2 border-l border-border pl-5 text-sm text-muted-foreground lg:flex">
              <span>{activeGroup?.group}</span>
              <ChevronRight className="size-3.5" aria-hidden="true" />
              <span className="truncate font-semibold text-foreground">{activePage.label}</span>
            </div>
          )}
        </div>

        <div className="flex items-center gap-1.5 text-xs sm:gap-2.5">
          <span className={cn('status-pill hidden xl:inline-flex', serviceUnavailable || (!usesHostedApi && !isConnected) ? 'tone-warning' : 'tone-success')} role="status">
            {feedLabel}
          </span>
          <Button type="button" variant="outline" size="icon" className="shrink-0 bg-card" onClick={toggleTheme} aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'} title={theme === 'dark' ? 'Light theme' : 'Dark theme'}>
            {theme === 'dark' ? <Sun className="size-4" /> : <Moon className="size-4" />}
          </Button>
          {/* Sound toggle */}
          <Button
            onClick={() => void toggleSound()}
            aria-label="Emergency siren"
            aria-pressed={soundEnabled}
            title={soundError ?? (soundEnabled ? 'Emergency siren enabled' : 'Enable emergency siren')}
            variant="outline"
            size="sm"
            className={`h-9 px-2.5 flex items-center gap-1.5 rounded-md border ${
              soundEnabled
                ? 'tone-warning border-border'
                : 'border-border bg-card text-muted-foreground'
            }`}
          >
            {soundEnabled ? <Volume2 className="h-4 w-4" /> : <VolumeX className="h-4 w-4" />}
          </Button>

          {/* Notifications */}
          <div className="relative" ref={notificationsRef}>
            <Button
              onClick={handleBellClick}
              aria-label={unreadCount ? `Notifications, ${unreadCount} unread` : 'Notifications'}
              aria-expanded={notificationsOpen}
              variant="outline"
              size="sm"
              className="h-9 w-9 p-0 rounded-md border-border bg-card text-muted-foreground relative"
            >
              <Bell className="h-4 w-4" />
              {unreadCount > 0 && (
                <span className="absolute -top-1 -right-1 min-w-[16px] h-4 px-1 rounded-md bg-primary text-primary-foreground text-[9px] font-bold flex items-center justify-center">
                  {unreadCount > 9 ? '9+' : unreadCount}
                </span>
              )}
            </Button>

            {notificationsOpen && (
              <div className="fixed inset-x-4 top-[4.25rem] z-50 flex max-h-[28rem] flex-col overflow-hidden rounded-lg border border-border bg-popover shadow-lg sm:absolute sm:inset-x-auto sm:right-0 sm:top-full sm:mt-2 sm:w-80">
                <div className="flex items-center justify-between px-3 py-2 border-b border-border">
                  <span className="text-xs font-bold uppercase tracking-wide text-muted-foreground">
                    Notifications
                  </span>
                  {unreadCount > 0 && (
                    <button
                      onClick={handleMarkAllRead}
                      className="text-[11px] font-medium text-primary hover:underline flex items-center gap-1"
                    >
                      <CheckCheck className="h-3 w-3" />
                      Mark all read
                    </button>
                  )}
                </div>

                <div className="overflow-y-auto flex-1">
                  {notificationsError && (
                    <div className="border-b border-red-500/20 bg-red-500/10 px-3 py-2 text-xs text-red-600 dark:text-red-400">
                      {notificationsError}
                    </div>
                  )}
                  {notificationsLoading && notifications.length === 0 ? (
                    <div className="p-4 text-xs text-muted-foreground text-center">Loading…</div>
                  ) : notifications.length === 0 ? (
                    <div className="p-4 text-xs text-muted-foreground text-center">No notifications yet.</div>
                  ) : (
                    notifications.map((n) => (
                      <button
                        key={n.id}
                        onClick={() => handleNotificationClick(n)}
                        className={`w-full text-left px-3 py-2.5 border-b border-border last:border-b-0 hover:bg-accent transition-colors ${
                          !n.is_read ? 'bg-[#D15033]/5' : ''
                        }`}
                      >
                        <div className="flex items-start gap-2">
                          {!n.is_read && <span className="h-1.5 w-1.5 rounded-full bg-[#D15033] mt-1.5 shrink-0" />}
                          <div className={`flex-1 ${n.is_read ? 'pl-3.5' : ''}`}>
                            <div className="text-xs font-medium text-foreground capitalize">
                              {notificationLabel(n)}
                            </div>
                            {n.incident_id && (
                              <div className="text-[10px] text-muted-foreground mt-0.5">
                                Incident #{n.incident_id}
                              </div>
                            )}
                            <div className="text-[10px] text-muted-foreground mt-0.5">
                              {timeAgo(n.sent_at)}
                            </div>
                          </div>
                        </div>
                      </button>
                    ))
                  )}
                </div>
              </div>
            )}
          </div>

          {/* User Profile in Header */}
          <div className="relative" ref={userMenuRef}>
            <button
              onClick={() => setUserMenuOpen(!userMenuOpen)}
              aria-label="Account options"
              aria-expanded={userMenuOpen}
              className="flex items-center gap-2 pl-2 pr-1 py-1 rounded-md hover:bg-accent transition-colors border border-transparent hover:border-border"
            >
              <div className="h-8 w-8 rounded-md bg-secondary text-secondary-foreground flex items-center justify-center font-bold text-xs shrink-0">
                {user?.name ? user.name.charAt(0) : 'A'}
              </div>
              <div className="hidden sm:block text-left">
                <div className="text-xs font-medium text-foreground leading-tight">
                  {user?.name || 'AgapUser'}
                </div>
                <div className="text-[10px] text-muted-foreground leading-tight">Administrator</div>
              </div>
              <ChevronDown className={`h-3.5 w-3.5 text-muted-foreground transition-transform ${userMenuOpen ? 'rotate-180' : ''}`} />
            </button>

            {userMenuOpen && (
              <div className="absolute right-0 mt-2 w-48 rounded-md border border-border bg-card shadow-[0_1px_2px_rgba(10,10,10,0.04)] z-50 py-1">
                <button
                  onClick={() => { setUserMenuOpen(false); setIsSettingsOpen(true); }}
                  className="w-full text-left px-3 py-2 text-xs text-foreground hover:bg-accent flex items-center gap-2"
                >
                  <Settings className="h-3.5 w-3.5" />
                  Settings
                </button>
                <button
                  onClick={() => alert('For AGAP account and dispatch support, contact your municipal DRRMO coordinator.')}
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs hover:bg-accent"
                >
                  <HelpCircle className="h-3.5 w-3.5" />
                  Support
                </button>
                <hr className="my-1 border-border" />
                <button
                  onClick={() => { setUserMenuOpen(false); handleSignOut(); }}
                  className="w-full text-left px-3 py-2 text-xs text-red-600 dark:text-red-400 hover:bg-red-500/10 flex items-center gap-2"
                >
                  <LogOut className="h-3.5 w-3.5" />
                  Sign Out
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      <Dialog open={navigationOpen} onOpenChange={setNavigationOpen} contentClassName="max-w-sm">
        <DialogHeader>
          <DialogTitle>Workspace</DialogTitle>
          <DialogDescription>AGAP Command Center</DialogDescription>
        </DialogHeader>
        <WorkspaceNavigation onNavigate={() => setNavigationOpen(false)} />
      </Dialog>

      <div className="flex min-w-0 flex-1">
        <aside className="workspace-rail sticky top-16 hidden h-[calc(100dvh-4rem)] w-60 shrink-0 flex-col border-r border-border lg:flex">
          <div className="workspace-brand mx-3.5 mt-4 flex items-center gap-3 rounded-xl p-3.5">
            <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-white/10"><ShieldCheck className="size-5" aria-hidden="true" /></span>
            <div className="min-w-0">
              <p className="text-xs font-medium text-white/70">AGAP workspace</p>
              <p className="mt-0.5 text-sm font-bold">Operations console</p>
            </div>
          </div>
          <div className="flex-1 overflow-y-auto px-3.5 py-4">
            <WorkspaceNavigation />
          </div>
          <div className="border-t border-border px-4 py-4">
            <div className="flex items-center gap-2.5 text-xs font-semibold">
              <Radio className="size-4 text-muted-foreground" aria-hidden="true" />
              {feedLabel}
            </div>
            <p className="mt-1 pl-6 text-xs leading-relaxed text-muted-foreground">{serviceUnavailable ? 'Information may be out of date.' : 'Authorized admin workspace'}</p>
          </div>
        </aside>

        <main id="workspace-content" tabIndex={-1} className="min-w-0 flex-1 p-4 outline-none sm:p-6 lg:px-7 lg:py-8">
          <div className="mx-auto max-w-[1440px]">
            {serviceUnavailable && (
              <p role="status" className="mb-4 rounded-md border border-amber-500/40 bg-amber-500/10 px-4 py-3 text-sm text-foreground">
                AGAP is temporarily unavailable. Information shown may be out of date.
              </p>
            )}
            <React.Suspense fallback={<p className="state-message" role="status">Loading workspace…</p>}>
              <Outlet />
            </React.Suspense>
          </div>
        </main>
      </div>

      <SettingsModal isOpen={isSettingsOpen} onClose={() => setIsSettingsOpen(false)} />

      {/* Sign Out Confirmation */}
      {signOutConfirmOpen && (
        <Dialog open={signOutConfirmOpen} onOpenChange={(open) => !open && setSignOutConfirmOpen(false)} role="alertdialog" contentClassName="max-w-sm">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base font-bold text-foreground">
              <LogOut className="h-4 w-4 text-muted-foreground" />
              Sign out?
            </DialogTitle>
            <DialogDescription className="text-sm text-muted-foreground">
              You'll need to sign in again to access the dashboard.
            </DialogDescription>
          </DialogHeader>

          <DialogFooter className="mt-5 flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setSignOutConfirmOpen(false)}
              className="rounded-md border-border bg-card text-muted-foreground hover:bg-accent"
            >
              Cancel
            </Button>
            <Button
              type="button"
              size="sm"
              onClick={confirmSignOut}
              variant="destructive"
            >
              Sign Out
            </Button>
          </DialogFooter>
        </Dialog>
      )}
    </div>
  );
};

export const AppShell: React.FC = () => {
  return <AppShellContent />;
};
