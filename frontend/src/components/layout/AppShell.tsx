import React, { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useNavigate, Link } from 'react-router-dom';
import {
  LayoutDashboard,
  MapPin,
  BarChart3,
  MessageSquare,
  AlertTriangle,
  Building2,
  Users,
  Archive,
  Sun,
  Moon,
  Volume2,
  VolumeX,
  Bell,
  Settings,
  HelpCircle,
  LogOut,
  ChevronLeft,
  ChevronRight,
  Clock,
  BookOpenText,
  ClipboardList,
  ShieldCheck,
  CheckCheck,
} from 'lucide-react';
import { apiService } from '@/services/api';
import type { Notification } from '@/types/api';
import { useWebSocket } from '@/context/WebSocketContext';
import { useTheme } from '@/context/ThemeContext';
import { useAuth } from '@/context/AuthContext';
import { audioAlert } from '@/lib/audioAlert';
import { Button } from '@/components/ui/button';
import { Tooltip } from '@/components/ui/tooltip';
import { SettingsModal } from '@/components/settings/SettingsModal';
import agapLogo from "../../assets/logos/logo-name-nobg.png";

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
  const [apiOnline, setApiOnline] = useState<boolean | null>(null);
  const [soundEnabled, setSoundEnabled] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [currentTime, setCurrentTime] = useState<string>('');
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [notificationsLoading, setNotificationsLoading] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const { isConnected: wsConnected, lastIncident } = useWebSocket();
  const { theme, toggleTheme } = useTheme();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const notificationsRef = useRef<HTMLDivElement>(null);

  const unreadCount = notifications.filter((n) => !n.is_read).length;

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setCurrentTime(now.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' }));
    };
    updateTime();
    const timer = setInterval(updateTime, 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    const checkHealth = async () => {
      try {
        await apiService.getHealth();
        setApiOnline(true);
      } catch {
        setApiOnline(false);
      }
    };
    checkHealth();
    const interval = setInterval(checkHealth, 10000);
    return () => clearInterval(interval);
  }, []);

  // Fetch notifications from the backend (source of truth), not local state.
  const refreshNotifications = async () => {
    try {
      setNotificationsLoading(true);
      const data = await apiService.getNotifications();
      const sorted = [...data].sort((a, b) => new Date(b.sent_at).getTime() - new Date(a.sent_at).getTime());
      setNotifications(sorted);
    } catch {
      // Keep whatever we already have on failure; don't wipe the list.
    } finally {
      setNotificationsLoading(false);
    }
  };

  // Initial load after login/session restoration.
  useEffect(() => {
    if (user) {
      refreshNotifications();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  // Revalidate whenever a new incident arrives over WebSocket, since the
  // backend generates a persisted notification for it.
  useEffect(() => {
    if (lastIncident) {
      refreshNotifications();
      if (soundEnabled) {
        audioAlert.playEmergencySiren();
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lastIncident]);

  // Close the dropdown when clicking outside it.
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (notificationsRef.current && !notificationsRef.current.contains(e.target as Node)) {
        setNotificationsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const toggleSound = () => {
    const nextState = !soundEnabled;
    setSoundEnabled(nextState);
    audioAlert.setEnabled(nextState);
    if (nextState) {
      audioAlert.playEmergencySiren();
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
    logout();
    navigate('/login');
  };

  // Grouped navigation structure based on operational usage
  const sidebarNavGroups = [
    {
      group: 'MAIN DISPATCH',
      items: [
        { to: '/dashboard', label: 'Overview', icon: LayoutDashboard, exact: true },
        { to: '/dashboard/map', label: 'Live GIS Map', icon: MapPin, exact: false },
        { to: '/dashboard/chat', label: 'P2P Dispatch Chat', icon: MessageSquare, exact: false },
      ],
    },
    {
      group: 'OPERATIONS',
      items: [
        { to: '/dashboard/incidents', label: 'Incident Triage', icon: AlertTriangle, exact: false },
        { to: '/dashboard/team', label: 'Responder Fleet', icon: Users, exact: false },
        { to: '/dashboard/agencies', label: 'Agency Network', icon: Building2, exact: false },
        { to: '/dashboard/emergency-types', label: 'Emergency Types', icon: AlertTriangle, exact: false },
        { to: '/dashboard/messages', label: 'Message Catalog', icon: BookOpenText, exact: false },
      ],
    },
    {
      group: 'INTELLIGENCE',
      items: [
        { to: '/dashboard/insights', label: 'Telemetry Insights', icon: BarChart3, exact: false },
        { to: '/dashboard/reports', label: 'Reports', icon: ClipboardList, exact: false },
        { to: '/dashboard/audit-logs', label: 'Audit Logs', icon: ShieldCheck, exact: false },
        { to: '/dashboard/archive', label: 'Distress Archive', icon: Archive, exact: false },
      ],
    },
  ];

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col font-sans transition-colors">
      {/* Landing-page signature terracotta letterhead rule */}
      <div className="h-1 w-full bg-primary shrink-0" />

      {/* Top Navbar */}
      <header className="h-16 border-b border-border bg-card/95 backdrop-blur px-4 sm:px-6 flex items-center justify-between sticky top-0 z-40 shrink-0">
        <div className="flex items-center gap-4">
          <Link to="/" className="flex items-center hover:opacity-90 transition-opacity">
            <img src={agapLogo} alt="AGAP" className="h-9 w-auto object-contain" />
          </Link>
          <span className="hidden sm:inline-flex text-xs font-semibold px-3 py-1.5 rounded-md bg-muted text-muted-foreground border border-border">
            DRRMO Command Dispatch
          </span>
        </div>

        {/* Right Header Action Icons & User Profile */}
        <div className="flex items-center gap-2.5 text-xs">
          {/* Real-Time Clock */}
          <div className="hidden xl:flex items-center gap-2 bg-muted px-3 py-1.5 rounded-md border border-border text-muted-foreground">
            <Clock className="h-3.5 w-3.5" />
            <span className="tabular-nums font-medium">{currentTime || '00:00:00'}</span>
          </div>

          {/* Health Badges */}
          <div className="hidden lg:flex items-center gap-2 bg-muted px-2.5 py-1.5 rounded-md border border-border text-[11px] font-medium">
            <span className="text-muted-foreground">API:</span>
            {apiOnline ? (
              <span className="text-emerald-600 dark:text-emerald-400 font-semibold">ONLINE</span>
            ) : (
              <span className="text-red-600 dark:text-red-400 font-semibold">OFFLINE</span>
            )}
            <span className="text-muted-foreground ml-1">WS:</span>
            {wsConnected ? (
              <span className="text-[#1F91A8] dark:text-[#4FB4C9] font-semibold">CONNECTED</span>
            ) : (
              <span className="text-muted-foreground">CONNECTING</span>
            )}
          </div>

          {/* Audio Siren Alert */}
          <Tooltip content={soundEnabled ? 'Disable Siren Alert' : 'Enable Siren Alert'} side="bottom">
            <Button
              onClick={toggleSound}
              variant="outline"
              size="sm"
              className={`h-9 px-2.5 flex items-center gap-1.5 rounded-md ${
                soundEnabled
                  ? 'bg-[#F2A540]/10 text-[#F2A540] border-[#F2A540]/40'
                  : 'text-muted-foreground'
              }`}
            >
              {soundEnabled ? <Volume2 className="h-4 w-4 text-[#F2A540]" /> : <VolumeX className="h-4 w-4" />}
            </Button>
          </Tooltip>

          {/* Theme Toggle Button */}
          <Tooltip content={theme === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'} side="bottom">
            <Button
              onClick={toggleTheme}
              variant="outline"
              size="sm"
              className="h-9 w-9 p-0 rounded-md text-muted-foreground"
            >
              {theme === 'dark' ? <Sun className="h-4 w-4 text-[#F2A540]" /> : <Moon className="h-4 w-4 text-[#1F91A8]" />}
            </Button>
          </Tooltip>

          {/* Notifications Bell — backed by persistent /notifications API */}
          <div className="relative" ref={notificationsRef}>
            <Tooltip content={unreadCount > 0 ? `${unreadCount} New Alerts` : 'System Notifications'} side="bottom">
              <Button
                onClick={handleBellClick}
                variant="outline"
                size="sm"
                className="h-9 w-9 p-0 rounded-md text-muted-foreground relative"
              >
                <Bell className="h-4 w-4" />
                {unreadCount > 0 && (
                  <span className="absolute top-1.5 right-1.5 h-2.5 w-2.5 rounded-full bg-primary animate-ping" />
                )}
                {unreadCount > 0 && (
                  <span className="absolute -top-1 -right-1 min-w-[16px] h-4 px-1 rounded-full bg-primary text-primary-foreground text-[9px] font-bold flex items-center justify-center">
                    {unreadCount > 9 ? '9+' : unreadCount}
                  </span>
                )}
              </Button>
            </Tooltip>

            {notificationsOpen && (
              <div className="absolute right-0 mt-2 w-80 max-h-[28rem] overflow-hidden flex flex-col rounded-md border border-border bg-card shadow-lg z-50">
                <div className="flex items-center justify-between px-3 py-2 border-b border-border">
                  <span className="text-xs font-bold uppercase tracking-wide text-muted-foreground">Notifications</span>
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
                  {notificationsLoading && notifications.length === 0 ? (
                    <div className="p-4 text-xs text-muted-foreground text-center">Loading…</div>
                  ) : notifications.length === 0 ? (
                    <div className="p-4 text-xs text-muted-foreground text-center">No notifications yet.</div>
                  ) : (
                    notifications.map((n) => (
                      <button
                        key={n.id}
                        onClick={() => handleNotificationClick(n)}
                        className={`w-full text-left px-3 py-2.5 border-b border-border last:border-b-0 hover:bg-muted transition-colors ${
                          !n.is_read ? 'bg-primary/5' : ''
                        }`}
                      >
                        <div className="flex items-start gap-2">
                          {!n.is_read && <span className="h-1.5 w-1.5 rounded-full bg-primary mt-1.5 shrink-0" />}
                          <div className={`flex-1 ${n.is_read ? 'pl-3.5' : ''}`}>
                            <div className="text-xs font-medium text-card-foreground capitalize">
                              {notificationLabel(n)}
                            </div>
                            {n.incident_id && (
                              <div className="text-[10px] text-muted-foreground mt-0.5">Incident #{n.incident_id}</div>
                            )}
                            <div className="text-[10px] text-muted-foreground mt-0.5">{timeAgo(n.sent_at)}</div>
                          </div>
                        </div>
                      </button>
                    ))
                  )}
                </div>
              </div>
            )}
          </div>

          {/* Settings Gear Icon */}
          <Tooltip content="Admin Settings" side="bottom">
            <Button
              onClick={() => setIsSettingsOpen(true)}
              variant="outline"
              size="sm"
              className="h-9 w-9 p-0 rounded-md text-muted-foreground"
            >
              <Settings className="h-4 w-4" />
            </Button>
          </Tooltip>
        </div>
      </header>

      {/* Main Layout */}
      <div className="flex flex-1">
        {/* Left Sidebar */}
        <aside
          className={`${sidebarCollapsed ? 'w-16' : 'w-60'} transition-all duration-300 border-r border-border bg-card/95 flex flex-col justify-between sticky top-[4.25rem] h-[calc(100vh-4.25rem)] shrink-0 z-30 overflow-y-auto
            [scrollbar-width:thin] [scrollbar-color:hsl(var(--border))_transparent]
            [&::-webkit-scrollbar]:w-1.5
            [&::-webkit-scrollbar-track]:bg-transparent
            [&::-webkit-scrollbar-thumb]:bg-border
            [&::-webkit-scrollbar-thumb]:rounded-full
            [&::-webkit-scrollbar-thumb:hover]:bg-muted-foreground/40`}
        >
          <div className="space-y-4 p-3">
            {/* User Profile Card */}
            {!sidebarCollapsed ? (
              <div className="p-3 bg-muted rounded-lg border border-border flex items-center gap-3">
                <div className="w-9 h-9 rounded-full bg-primary text-primary-foreground flex items-center justify-center font-bold text-xs shrink-0">
                  {user?.name ? user.name.charAt(0) : 'A'}
                </div>
                <div className="overflow-hidden">
                  <div className="text-xs font-bold text-card-foreground truncate">{user?.name || 'AgapUser'}</div>
                  <div className="text-[10px] text-muted-foreground truncate">Agap User Position</div>
                </div>
              </div>
            ) : (
              <div className="flex justify-center py-1">
                <div className="w-9 h-9 rounded-full bg-primary text-primary-foreground flex items-center justify-center font-bold text-xs">
                  {user?.name ? user.name.charAt(0) : 'A'}
                </div>
              </div>
            )}

            {/* Sidebar Toggle */}
            <div className="flex items-center justify-between px-2">
              {!sidebarCollapsed && (
                <span className="text-[10px] uppercase font-bold tracking-widest text-muted-foreground">
                  Command System
                </span>
              )}
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
                className="h-6 w-6 p-0 text-muted-foreground hover:text-foreground ml-auto"
              >
                {sidebarCollapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
              </Button>
            </div>

            {/* Grouped Nav Items */}
            <div className="space-y-4">
              {sidebarNavGroups.map((group) => (
                <div key={group.group} className="space-y-1">
                  {!sidebarCollapsed && (
                    <div className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground px-2 py-0.5">
                      {group.group}
                    </div>
                  )}
                  <nav className="flex flex-col space-y-1 w-full">
                    {group.items.map((item) => {
                      const Icon = item.icon;
                      return (
                        <Tooltip key={item.to} content={sidebarCollapsed ? item.label : ''} side="right" wrapperClassName="w-full block">
                          <NavLink
                            to={item.to}
                            end={item.exact}
                            className={({ isActive }) =>
                              `w-full flex items-center ${sidebarCollapsed ? 'justify-center px-0' : 'gap-3 px-3'} py-2 rounded-md text-xs font-semibold transition-all ${
                                isActive
                                  ? 'bg-primary/10 text-primary dark:text-[#E98A6E] border border-primary/25'
                                  : 'text-muted-foreground hover:text-foreground hover:bg-muted border border-transparent'
                              }`
                            }
                          >
                            <Icon className="h-4 w-4 shrink-0" />
                            {!sidebarCollapsed && <span className="truncate">{item.label}</span>}
                          </NavLink>
                        </Tooltip>
                      );
                    })}
                  </nav>
                </div>
              ))}
            </div>
          </div>

          {/* Bottom Actions: Support & Sign Out */}
          <div className="space-y-1.5 p-3 pt-3 border-t border-border text-xs mt-auto shrink-0">
            <button
              onClick={() => alert('AGAP DRRMO Support Desk: +63 917 123 4567')}
              className={`w-full flex items-center ${sidebarCollapsed ? 'justify-center px-0' : 'gap-3 px-3'} py-2 text-muted-foreground hover:text-foreground text-xs font-medium rounded-md hover:bg-muted`}
            >
              <HelpCircle className="h-4 w-4 shrink-0" />
              {!sidebarCollapsed && <span>Support</span>}
            </button>

            <button
              onClick={handleSignOut}
              className={`w-full flex items-center ${sidebarCollapsed ? 'justify-center px-0' : 'gap-3 px-3'} py-2 text-red-600 dark:text-red-400 hover:bg-red-500/10 text-xs font-medium rounded-md`}
            >
              <LogOut className="h-4 w-4 shrink-0" />
              {!sidebarCollapsed && <span>Sign Out</span>}
            </button>
          </div>
        </aside>

        {/* Dynamic Page Content */}
        <main className="flex-1 overflow-auto bg-muted/40 dark:bg-black/20 p-6 lg:p-8">
          <div className="mx-auto max-w-[1400px]">
            <Outlet />
          </div>
        </main>
      </div>

      {/* Admin Settings Modal */}
      <SettingsModal isOpen={isSettingsOpen} onClose={() => setIsSettingsOpen(false)} />
    </div>
  );
};

export const AppShell: React.FC = () => {
  return <AppShellContent />;
};