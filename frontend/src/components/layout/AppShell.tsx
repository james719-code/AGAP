import React, { useCallback, useEffect, useRef, useState } from 'react';
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
  BookOpenText,
  ShieldCheck,
  CheckCheck,
  User,
  ChevronDown,
} from 'lucide-react';
import { apiService } from '@/services/api';
import type { Notification } from '@/types/api';
import { useWebSocket } from '@/context/WebSocketContext';
import { useTheme } from '@/context/ThemeContext';
import { useAuth } from '@/context/AuthContext';
import { audioAlert } from '@/lib/audioAlert';
import {
  DISPATCHER_PREFERENCES_EVENT,
  loadDispatcherPreferences,
  saveDispatcherPreferences,
  type DispatcherPreferences,
} from '@/lib/dispatcherPreferences';
import { Button } from '@/components/ui/button';
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
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const { lastIncident } = useWebSocket();
  const { theme, toggleTheme } = useTheme();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
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
      const data = await apiService.getNotifications();
      const sorted = [...data].sort((a, b) => new Date(b.sent_at).getTime() - new Date(a.sent_at).getTime());
      setNotifications(sorted);
    } catch {
      // keep existing
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
        audioAlert.playEmergencySiren();
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

  const toggleSound = () => {
    const nextState = !soundEnabled;
    setSoundEnabled(nextState);
    saveDispatcherPreferences({ ...loadDispatcherPreferences(), soundEnabled: nextState });
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

  const sidebarNavGroups = [
    {
      group: 'MAIN DISPATCH',
      items: [
        { to: '/dashboard', label: 'Overview', icon: LayoutDashboard, exact: true },
        { to: '/dashboard/map', label: 'Live GIS Map', icon: MapPin, exact: false },
        { to: '/dashboard/chat', label: 'Incident Chat', icon: MessageSquare, exact: false },
      ],
    },
    {
      group: 'OPERATIONS',
      items: [
        { to: '/dashboard/incidents', label: 'Incident Triage', icon: AlertTriangle, exact: false },
        { to: '/dashboard/team', label: 'Responder Fleet', icon: Users, exact: false },
        { to: '/dashboard/users', label: 'User Roster & Accounts', icon: ShieldCheck, exact: false },
        { to: '/dashboard/agencies', label: 'Agency Network', icon: Building2, exact: false },
        { to: '/dashboard/emergency-types', label: 'Emergency Types', icon: AlertTriangle, exact: false },
        { to: '/dashboard/messages', label: 'Message Catalog', icon: BookOpenText, exact: false },
      ],
    },
    {
      group: 'INTELLIGENCE',
      items: [
        { to: '/dashboard/telemetry-reports', label: 'Telemetry & Reports', icon: BarChart3, exact: false },
        { to: '/dashboard/audit-logs', label: 'Audit Logs', icon: ShieldCheck, exact: false },
        { to: '/dashboard/archive', label: 'Distress Archive', icon: Archive, exact: false },
      ],
    },
  ];

  return (
    <div className="min-h-screen bg-white dark:bg-[#0F1214] text-[#0A0A0A] dark:text-[#F2F0EC] font-sans antialiased flex flex-col transition-colors">
      {/* Header */}
      <header className="h-16 border-b border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] px-4 sm:px-6 flex items-center justify-between sticky top-0 z-40 shrink-0">
        <div className="flex items-center gap-4">
          <Link to="/dashboard" className="flex items-center gap-3 hover:opacity-90 transition-opacity">
            <img src={agapLogo} alt="AGAP" className="h-16 sm:h-20 w-auto object-contain shrink-0" />
            <p className="text-[11px] sm:text-xs text-[#6B6864] dark:text-[#9C9994] hidden sm:block tracking-wide leading-none">
              Accelerated Geolocation Assistance &amp; Protection
            </p>
          </Link>
        </div>

        <div className="flex items-center gap-2.5 text-xs">
          {/* Sound toggle */}
          <Button
            onClick={toggleSound}
            variant="outline"
            size="sm"
            className={`h-9 px-2.5 flex items-center gap-1.5 rounded-[2px] border ${
              soundEnabled
                ? 'bg-[#F2A540]/10 text-[#F2A540] border-[#F2A540]/40'
                : 'border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#6B6864] dark:text-[#9C9994]'
            }`}
          >
            {soundEnabled ? <Volume2 className="h-4 w-4 text-[#F2A540]" /> : <VolumeX className="h-4 w-4" />}
          </Button>

          {/* Theme toggle */}
          <Button
            onClick={toggleTheme}
            variant="outline"
            size="sm"
            className="h-9 w-9 p-0 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#6B6864] dark:text-[#9C9994]"
          >
            {theme === 'dark' ? <Sun className="h-4 w-4 text-[#F2A540]" /> : <Moon className="h-4 w-4 text-[#1F91A8]" />}
          </Button>

          {/* Notifications */}
          <div className="relative" ref={notificationsRef}>
            <Button
              onClick={handleBellClick}
              variant="outline"
              size="sm"
              className="h-9 w-9 p-0 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#6B6864] dark:text-[#9C9994] relative"
            >
              <Bell className="h-4 w-4" />
              {unreadCount > 0 && (
                <span className="absolute top-1.5 right-1.5 h-2.5 w-2.5 rounded-full bg-[#D15033] animate-ping" />
              )}
              {unreadCount > 0 && (
                <span className="absolute -top-1 -right-1 min-w-[16px] h-4 px-1 rounded-[2px] bg-[#D15033] text-white text-[9px] font-bold flex items-center justify-center">
                  {unreadCount > 9 ? '9+' : unreadCount}
                </span>
              )}
            </Button>

            {notificationsOpen && (
              <div className="absolute right-0 mt-2 w-80 max-h-[28rem] overflow-hidden flex flex-col rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] z-50">
                <div className="flex items-center justify-between px-3 py-2 border-b border-[#E7E3DC] dark:border-[#26292C]">
                  <span className="text-xs font-bold uppercase tracking-wide text-[#6B6864] dark:text-[#9C9994]">
                    Notifications
                  </span>
                  {unreadCount > 0 && (
                    <button
                      onClick={handleMarkAllRead}
                      className="text-[11px] font-medium text-[#D15033] hover:underline flex items-center gap-1"
                    >
                      <CheckCheck className="h-3 w-3" />
                      Mark all read
                    </button>
                  )}
                </div>

                <div className="overflow-y-auto flex-1">
                  {notificationsLoading && notifications.length === 0 ? (
                    <div className="p-4 text-xs text-[#6B6864] dark:text-[#9C9994] text-center">Loading…</div>
                  ) : notifications.length === 0 ? (
                    <div className="p-4 text-xs text-[#6B6864] dark:text-[#9C9994] text-center">No notifications yet.</div>
                  ) : (
                    notifications.map((n) => (
                      <button
                        key={n.id}
                        onClick={() => handleNotificationClick(n)}
                        className={`w-full text-left px-3 py-2.5 border-b border-[#E7E3DC] dark:border-[#26292C] last:border-b-0 hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D] transition-colors ${
                          !n.is_read ? 'bg-[#D15033]/5' : ''
                        }`}
                      >
                        <div className="flex items-start gap-2">
                          {!n.is_read && <span className="h-1.5 w-1.5 rounded-full bg-[#D15033] mt-1.5 shrink-0" />}
                          <div className={`flex-1 ${n.is_read ? 'pl-3.5' : ''}`}>
                            <div className="text-xs font-medium text-[#0A0A0A] dark:text-white capitalize">
                              {notificationLabel(n)}
                            </div>
                            {n.incident_id && (
                              <div className="text-[10px] text-[#6B6864] dark:text-[#9C9994] mt-0.5">
                                Incident #{n.incident_id}
                              </div>
                            )}
                            <div className="text-[10px] text-[#6B6864] dark:text-[#9C9994] mt-0.5">
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
              className="flex items-center gap-2 pl-2 pr-1 py-1 rounded-[2px] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D] transition-colors border border-transparent hover:border-[#E7E3DC] dark:hover:border-[#26292C]"
            >
              <div className="h-8 w-8 rounded-full bg-[#D15033] text-white flex items-center justify-center font-bold text-xs shrink-0">
                {user?.name ? user.name.charAt(0) : 'A'}
              </div>
              <div className="hidden sm:block text-left">
                <div className="text-xs font-medium text-[#0A0A0A] dark:text-white leading-tight">
                  {user?.name || 'AgapUser'}
                </div>
                <div className="text-[10px] text-[#6B6864] dark:text-[#9C9994] leading-tight">Administrator</div>
              </div>
              <ChevronDown className={`h-3.5 w-3.5 text-[#6B6864] dark:text-[#9C9994] transition-transform ${userMenuOpen ? 'rotate-180' : ''}`} />
            </button>

            {userMenuOpen && (
              <div className="absolute right-0 mt-2 w-48 rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] z-50 py-1">
                <button
                  onClick={() => { setUserMenuOpen(false); /* navigate to profile? */ }}
                  className="w-full text-left px-3 py-2 text-xs text-[#0A0A0A] dark:text-white hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D] flex items-center gap-2"
                >
                  <User className="h-3.5 w-3.5" />
                  Profile
                </button>
                <button
                  onClick={() => { setUserMenuOpen(false); setIsSettingsOpen(true); }}
                  className="w-full text-left px-3 py-2 text-xs text-[#0A0A0A] dark:text-white hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D] flex items-center gap-2"
                >
                  <Settings className="h-3.5 w-3.5" />
                  Settings
                </button>
                <hr className="my-1 border-[#E7E3DC] dark:border-[#26292C]" />
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

      {/* Main Layout */}
      <div className="flex flex-1">
        {/* Sidebar – always expanded (w-64) with scrollable content */}
        <aside
          className={`w-64 transition-all duration-300 border-r border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] flex flex-col justify-between sticky top-[4.25rem] h-[calc(100vh-4.25rem)] shrink-0 z-30 overflow-y-auto
            [scrollbar-width:thin]
            [scrollbar-color:#E7E3DC_transparent]
            dark:[scrollbar-color:#26292C_transparent]
            [&::-webkit-scrollbar]:w-1.5
            [&::-webkit-scrollbar-track]:bg-transparent
            [&::-webkit-scrollbar-thumb]:bg-[#E7E3DC]
            dark:[&::-webkit-scrollbar-thumb]:bg-[#26292C]
            [&::-webkit-scrollbar-thumb]:rounded-full
            hover:[&::-webkit-scrollbar-thumb]:bg-[#D8D4CB]
            dark:hover:[&::-webkit-scrollbar-thumb]:bg-[#3A4145]`}
        >
          {/* Navigation */}
          <div className="flex-1 flex flex-col p-3 space-y-4">
            <div className="flex-1 flex flex-col space-y-4">
              {sidebarNavGroups.map((group) => (
                <div key={group.group} className="space-y-1">
                  <div className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#D15033] px-2 py-0.5">
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
                            `w-full flex items-center gap-3 px-3 py-2 rounded-[2px] text-xs font-semibold transition-all ${
                              isActive
                                ? 'bg-[#D15033]/10 dark:bg-[#D15033]/15 text-[#D15033] dark:text-[#E9835F] border border-[#D15033]/25'
                                : 'text-[#6B6864] dark:text-[#9C9994] hover:text-[#0A0A0A] dark:hover:text-white hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D] border border-transparent'
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
          <div className="p-3 pt-3 border-t border-[#E7E3DC] dark:border-[#26292C] flex flex-col space-y-1 shrink-0">
            <button
              onClick={() => alert('AGAP DRRMO Support Desk: +63 917 123 4567')}
              className="w-full flex items-center gap-3 px-3 py-2 rounded-[2px] text-xs font-medium text-[#6B6864] dark:text-[#9C9994] hover:text-[#0A0A0A] dark:hover:text-white hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D] transition-colors"
            >
              <HelpCircle className="h-4 w-4 shrink-0" />
              <span>Support</span>
            </button>

            <button
              onClick={handleSignOut}
              className="w-full flex items-center gap-3 px-3 py-2 rounded-[2px] text-xs font-medium text-red-600 dark:text-red-400 hover:bg-red-500/10 transition-colors"
            >
              <LogOut className="h-4 w-4 shrink-0" />
              <span>Sign Out</span>
            </button>
          </div>
        </aside>

        {/* Main content */}
        <main className="flex-1 overflow-auto bg-[#F7F5F2] dark:bg-[#0F1214] p-6 lg:p-8">
          <div className="mx-auto max-w-[1400px]">
            <Outlet />
          </div>
        </main>
      </div>

      <SettingsModal isOpen={isSettingsOpen} onClose={() => setIsSettingsOpen(false)} />
    </div>
  );
};

export const AppShell: React.FC = () => {
  return <AppShellContent />;
};
