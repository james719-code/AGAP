import React, { useCallback, useEffect, useRef, useState } from 'react';
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
} from 'lucide-react';
import { apiService } from '@/services/api';
import type { Notification } from '@/types/api';
import { useWebSocket } from '@/context/WebSocketContext';
import { useAuth } from '@/context/AuthContext';
import { audioAlert } from '@/lib/audioAlert';
import {
  DISPATCHER_PREFERENCES_EVENT,
  loadDispatcherPreferences,
  saveDispatcherPreferences,
  type DispatcherPreferences,
} from '@/lib/dispatcherPreferences';
import { Button } from '@/components/ui/button';
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { SettingsModal } from '@/components/settings/SettingsModal';
import agapLogo from '../../assets/logos/logo-name-nobg.png';

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
  const [soundEnabled, setSoundEnabled] = useState(() => loadDispatcherPreferences().soundEnabled);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [notificationsLoading, setNotificationsLoading] = useState(false);
  const [notificationsError, setNotificationsError] = useState<string | null>(null);
  const [soundError, setSoundError] = useState<string | null>(null);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [signOutConfirmOpen, setSignOutConfirmOpen] = useState(false);
  const { lastIncident } = useWebSocket();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const notificationsRef = useRef<HTMLDivElement>(null);
  const userMenuRef = useRef<HTMLDivElement>(null);

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

  const refreshNotifications = useCallback(async () => {
    try {
      setNotificationsLoading(true);
      setNotificationsError(null);
      const data = await apiService.getNotifications();
      const sorted = [...data].sort((a, b) => new Date(b.sent_at).getTime() - new Date(a.sent_at).getTime());
      setNotifications(sorted);
    } catch (error) {
      setNotificationsError(error instanceof Error ? error.message : 'Could not load notifications.');
    } finally {
      setNotificationsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (user) {
      queueMicrotask(() => void refreshNotifications());
    }
  }, [user, refreshNotifications]);

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

  const sidebarNavGroups = [
    {
      group: 'Dispatch',
      items: [
        { to: '/dashboard', label: 'Overview', icon: LayoutDashboard, exact: true },
        { to: '/dashboard/map', label: 'Live Map', icon: MapPin, exact: false },
        { to: '/dashboard/chat', label: 'Incident Chat', icon: MessageSquare, exact: false },
      ],
    },
    {
      group: 'Operations',
      items: [
        { to: '/dashboard/incidents', label: 'Incidents', icon: AlertTriangle, exact: false },
        { to: '/dashboard/team', label: 'Responders', icon: Users, exact: false },
        { to: '/dashboard/users', label: 'Users', icon: ShieldCheck, exact: false },
        { to: '/dashboard/agencies', label: 'Agencies', icon: Building2, exact: false },
        { to: '/dashboard/emergency-types', label: 'Emergency Types', icon: AlertTriangle, exact: false },
        { to: '/dashboard/messages', label: 'Message Catalog', icon: BookOpenText, exact: false },
      ],
    },
    {
      group: 'Intelligence',
      items: [
        { to: '/dashboard/telemetry-reports', label: 'Reports', icon: BarChart3, exact: false },
        { to: '/dashboard/audit-logs', label: 'Audit Logs', icon: ShieldCheck, exact: false },
        { to: '/dashboard/archive', label: 'Incident Archive', icon: Archive, exact: false },
      ],
    },
  ];

  return (
    <div className="min-h-dvh bg-background text-foreground font-sans antialiased flex flex-col">
      {/* Header */}
      <header className="h-16 border-b border-border bg-card px-4 sm:px-6 flex items-center justify-between sticky top-0 z-40 shrink-0">
        <div className="flex items-center gap-4">
          <Link to="/dashboard" className="flex items-center gap-3 hover:opacity-90 transition-opacity">
            <img src={agapLogo} alt="AGAP PH" className="h-6 w-auto object-contain shrink-0" />
            <span className="hidden border-l border-border pl-3 text-sm font-semibold sm:block">Command Center</span>
          </Link>
        </div>

        <div className="flex items-center gap-2.5 text-xs">
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
                ? 'bg-[#F2A540]/10 text-[#F2A540] border-[#F2A540]/40'
                : 'border-border bg-card text-muted-foreground'
            }`}
          >
            {soundEnabled ? <Volume2 className="h-4 w-4 text-[#F2A540]" /> : <VolumeX className="h-4 w-4" />}
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
                <span className="absolute top-1.5 right-1.5 h-2.5 w-2.5 rounded-full bg-[#D15033] animate-ping" />
              )}
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
                  onClick={() => alert('AGAP DRRMO Support Desk: +63 917 123 4567')}
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs hover:bg-accent lg:hidden"
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

      <div className="sticky top-16 z-30 flex items-center gap-3 border-b border-border bg-card px-4 py-2 lg:hidden">
        <label htmlFor="command-page" className="text-xs font-semibold text-muted-foreground">Workspace</label>
        <select
          id="command-page"
          value={pathname}
          onChange={(event) => navigate(event.target.value)}
          className="h-control min-w-0 flex-1 rounded-md border border-input bg-background px-3 text-sm font-medium"
        >
          {sidebarNavGroups.map((group) => (
            <optgroup key={group.group} label={group.group}>
              {group.items.map((item) => <option key={item.to} value={item.to}>{item.label}</option>)}
            </optgroup>
          ))}
        </select>
      </div>

      <div className="flex min-w-0 flex-1">
        <aside
          className="sticky top-[5rem] m-4 hidden h-[calc(100dvh-6rem)] w-56 shrink-0 flex-col justify-between overflow-y-auto rounded-lg border border-border bg-card lg:flex"
        >
          {/* Navigation */}
          <div className="flex-1 flex flex-col p-3 space-y-4">
            <div className="flex-1 flex flex-col space-y-4">
              {sidebarNavGroups.map((group) => (
                <div key={group.group} className="space-y-1">
                  <div className="px-2 py-0.5 text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
                    {group.group}
                  </div>
                  <nav className="flex flex-col space-y-1 w-full">
                    {group.items.map((item) => {
                      const Icon = item.icon;
                      return (
                        <NavLink
                          key={item.to}
                          to={item.to}
                          end={item.exact}
                          className={({ isActive }) =>
                            `w-full flex items-center gap-3 px-3 py-2 rounded-md text-xs font-semibold transition-all ${
                              isActive
                                ? 'border border-primary/20 bg-primary/10 text-primary'
                                : 'text-muted-foreground hover:text-foreground hover:bg-accent border border-transparent'
                            }`
                          }
                        >
                          <Icon className="h-4 w-4 shrink-0" />
                          <span className="truncate">{item.label}</span>
                        </NavLink>
                      );
                    })}
                  </nav>
                </div>
              ))}
            </div>
          </div>

          {/* Bottom: Support & Sign Out */}
          <div className="p-3 pt-3 border-t border-border flex flex-col space-y-1 shrink-0">
            <button
              onClick={() => alert('AGAP DRRMO Support Desk: +63 917 123 4567')}
              className="w-full flex items-center gap-3 px-3 py-2 rounded-md text-xs font-medium text-muted-foreground hover:text-foreground hover:bg-accent transition-colors"
            >
              <HelpCircle className="h-4 w-4 shrink-0" />
              <span>Support</span>
            </button>

            <button
              onClick={handleSignOut}
              className="w-full flex items-center gap-3 px-3 py-2 rounded-md text-xs font-medium text-red-600 dark:text-red-400 hover:bg-red-500/10 transition-colors"
            >
              <LogOut className="h-4 w-4 shrink-0" />
              <span>Sign Out</span>
            </button>
          </div>
        </aside>

        {/* Main content */}
        <main className="min-w-0 flex-1 bg-background p-4 sm:p-6 lg:pl-3 lg:pr-8">
          <div className="mx-auto max-w-[1400px]">
            <Outlet />
          </div>
        </main>
      </div>

      <SettingsModal isOpen={isSettingsOpen} onClose={() => setIsSettingsOpen(false)} />

      {/* Sign Out Confirmation */}
      {signOutConfirmOpen && (
        <Dialog open={signOutConfirmOpen} onOpenChange={(open) => !open && setSignOutConfirmOpen(false)}>
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/20 backdrop-blur-sm">
            <div className="max-w-sm w-full mx-4 rounded-md border border-border bg-card shadow-[0_1px_2px_rgba(10,10,10,0.04)] p-6">
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
                  className="rounded-md bg-red-600 hover:bg-red-700 text-white"
                >
                  Sign Out
                </Button>
              </DialogFooter>
            </div>
          </div>
        </Dialog>
      )}
    </div>
  );
};

export const AppShell: React.FC = () => {
  return <AppShellContent />;
};
