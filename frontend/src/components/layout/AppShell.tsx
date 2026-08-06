import React, { useEffect, useState } from 'react';
import { NavLink, Outlet, useNavigate, Link } from 'react-router-dom';
import {
  Shield,
  LayoutDashboard,
  MapPin,
  BarChart3,
  MessageSquare,
  AlertTriangle,
  Building2,
  Users,
  UserCheck,
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
  Clock
} from 'lucide-react';
import { apiService } from '@/services/api';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useTheme } from '@/context/ThemeContext';
import { useAuth } from '@/context/AuthContext';
import { audioAlert } from '@/lib/audioAlert';
import { Button } from '@/components/ui/button';
import { Tooltip } from '@/components/ui/tooltip';
import { SettingsModal } from '@/components/settings/SettingsModal';

export const AppShellContent: React.FC = () => {
  const [apiOnline, setApiOnline] = useState<boolean | null>(null);
  const [soundEnabled, setSoundEnabled] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [currentTime, setCurrentTime] = useState<string>('');
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const { isConnected: wsConnected, lastIncident } = useWebSocket();
  const { theme, toggleTheme } = useTheme();
  const { user, logout } = useAuth();
  const navigate = useNavigate();

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

  useEffect(() => {
    if (lastIncident) {
      setUnreadCount((prev) => prev + 1);
      if (soundEnabled) {
        audioAlert.playEmergencySiren();
      }
    }
  }, [lastIncident, soundEnabled]);

  const toggleSound = () => {
    const nextState = !soundEnabled;
    setSoundEnabled(nextState);
    audioAlert.setEnabled(nextState);
    if (nextState) {
      audioAlert.playEmergencySiren();
    }
  };

  const handleNotificationsClick = () => {
    if (unreadCount > 0) {
      alert(`System Notifications: ${unreadCount} new distress alerts received via BLE mesh.`);
      setUnreadCount(0);
    } else {
      alert('System Notifications: All clear. No new distress alerts.');
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
        { to: '/dashboard/users', label: 'User Verification', icon: UserCheck, exact: false },
        { to: '/dashboard/agencies', label: 'Agency Network', icon: Building2, exact: false },
      ],
    },
    {
      group: 'INTELLIGENCE',
      items: [
        { to: '/dashboard/insights', label: 'Telemetry Insights', icon: BarChart3, exact: false },
        { to: '/dashboard/archive', label: 'Distress Archive', icon: Archive, exact: false },
      ],
    },
  ];

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 flex flex-col font-sans transition-colors duration-200">
      {/* Top Navbar */}
      <header className="h-16 border-b border-slate-200 dark:border-slate-800 bg-white/90 dark:bg-slate-900/90 backdrop-blur px-4 sm:px-6 flex items-center justify-between sticky top-0 z-40">
        <div className="flex items-center gap-4">
          <Link to="/" className="flex items-center gap-2.5 hover:opacity-90 transition-opacity">
            <div className="bg-red-500/10 p-2 rounded-xl border border-red-500/30 shadow-md">
              <Shield className="h-5 w-5 text-red-500" />
            </div>
            <span className="text-lg font-black tracking-wider text-slate-900 dark:text-slate-100">
              AGAP
            </span>
          </Link>
          <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700">
            DRRMO Command Dispatch
          </span>
        </div>

        {/* Right Header Action Icons & User Profile */}
        <div className="flex items-center gap-3 text-xs font-mono">
          {/* Real-Time Clock */}
          <div className="hidden xl:flex items-center gap-1.5 bg-slate-100 dark:bg-slate-950 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-300">
            <Clock className="h-3.5 w-3.5 text-slate-400" />
            <span>{currentTime || '00:00:00'}</span>
          </div>

          {/* Health Badges */}
          <div className="hidden lg:flex items-center gap-2 bg-slate-100 dark:bg-slate-950 px-2.5 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 text-[11px]">
            <span className="text-slate-400">API:</span>
            {apiOnline ? <span className="text-emerald-500 font-bold">ONLINE</span> : <span className="text-red-500 font-bold">OFFLINE</span>}
            <span className="text-slate-400 ml-1">WS:</span>
            {wsConnected ? <span className="text-sky-500 font-bold">CONNECTED</span> : <span className="text-slate-400">CONNECTING</span>}
          </div>

          {/* Audio Siren Alert */}
          <Tooltip content={soundEnabled ? 'Disable Siren Alert' : 'Enable Siren Alert'} side="bottom">
            <Button
              onClick={toggleSound}
              variant="outline"
              size="sm"
              className={`h-9 px-2.5 flex items-center gap-1.5 border-slate-300 dark:border-slate-800 rounded-lg ${
                soundEnabled ? 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/30' : 'text-slate-500 dark:text-slate-400'
              }`}
            >
              {soundEnabled ? <Volume2 className="h-4 w-4 text-amber-500 animate-pulse" /> : <VolumeX className="h-4 w-4 text-slate-400" />}
            </Button>
          </Tooltip>

          {/* Theme Toggle Button */}
          <Tooltip content={theme === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'} side="bottom">
            <Button
              onClick={toggleTheme}
              variant="outline"
              size="sm"
              className="h-9 w-9 p-0 rounded-lg border-slate-300 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:text-slate-900 dark:hover:text-slate-100"
            >
              {theme === 'dark' ? <Sun className="h-4 w-4 text-amber-400" /> : <Moon className="h-4 w-4 text-sky-500" />}
            </Button>
          </Tooltip>

          {/* Notifications Bell Icon — Only Blinks when unreadCount > 0 */}
          <Tooltip content={unreadCount > 0 ? `${unreadCount} New Alerts` : 'System Notifications'} side="bottom">
            <Button
              onClick={handleNotificationsClick}
              variant="outline"
              size="sm"
              className="h-9 w-9 p-0 rounded-lg border-slate-300 dark:border-slate-800 text-slate-600 dark:text-slate-300 relative"
            >
              <Bell className="h-4 w-4" />
              {unreadCount > 0 && (
                <span className="absolute top-1.5 right-1.5 h-2.5 w-2.5 rounded-full bg-red-500 animate-ping" />
              )}
            </Button>
          </Tooltip>

          {/* Settings Gear Icon */}
          <Tooltip content="Dispatcher Settings" side="bottom">
            <Button
              onClick={() => setIsSettingsOpen(true)}
              variant="outline"
              size="sm"
              className="h-9 w-9 p-0 rounded-lg border-slate-300 dark:border-slate-800 text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-slate-100"
            >
              <Settings className="h-4 w-4" />
            </Button>
          </Tooltip>
        </div>
      </header>

      {/* Main Layout */}
      <div className="flex flex-1">
        {/* Left Sidebar */}
        <aside className={`${sidebarCollapsed ? 'w-16' : 'w-60'} transition-all duration-300 border-r border-slate-200 dark:border-slate-800 bg-white/90 dark:bg-slate-900/90 p-3 flex flex-col justify-between sticky top-16 h-[calc(100vh-4rem)] shrink-0 z-30 overflow-y-auto`}>
          <div className="space-y-4">
            {/* User Profile Card */}
            {!sidebarCollapsed ? (
              <div className="p-3 bg-slate-50 dark:bg-slate-950 rounded-xl border border-slate-200 dark:border-slate-800 flex items-center gap-3">
                <div className="w-9 h-9 rounded-full bg-teal-600 text-white flex items-center justify-center font-bold text-xs shrink-0">
                  {user?.name ? user.name.charAt(0) : 'A'}
                </div>
                <div className="overflow-hidden">
                  <div className="text-xs font-bold text-slate-900 dark:text-slate-100 truncate">{user?.name || 'AgapUser'}</div>
                  <div className="text-[10px] text-slate-500 dark:text-slate-400 truncate">Agap User Position</div>
                </div>
              </div>
            ) : (
              <div className="flex justify-center py-1">
                <div className="w-9 h-9 rounded-full bg-teal-600 text-white flex items-center justify-center font-bold text-xs">
                  {user?.name ? user.name.charAt(0) : 'A'}
                </div>
              </div>
            )}

            {/* Sidebar Toggle */}
            <div className="flex items-center justify-between px-1">
              {!sidebarCollapsed && (
                <span className="text-[10px] uppercase font-bold tracking-widest text-slate-400">
                  COMMAND SYSTEM
                </span>
              )}
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
                className="h-6 w-6 p-0 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 ml-auto"
              >
                {sidebarCollapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
              </Button>
            </div>

            {/* Grouped Nav Items */}
            <div className="space-y-4">
              {sidebarNavGroups.map((group) => (
                <div key={group.group} className="space-y-1">
                  {!sidebarCollapsed && (
                    <div className="text-[9px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500 px-2 py-0.5">
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
                              `w-full flex items-center ${sidebarCollapsed ? 'justify-center px-0' : 'gap-3 px-3'} py-2 rounded-xl text-xs font-semibold transition-all ${
                                isActive
                                  ? 'bg-teal-600/10 text-teal-600 dark:text-teal-400 border border-teal-500/30 font-bold shadow-sm'
                                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-800/60'
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
          <div className="space-y-1.5 pt-3 border-t border-slate-200 dark:border-slate-800 text-xs mt-auto shrink-0">
            <button
              onClick={() => alert('AGAP DRRMO Support Desk: +63 917 123 4567')}
              className={`w-full flex items-center ${sidebarCollapsed ? 'justify-center px-0' : 'gap-3 px-3'} py-2 text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 text-xs font-medium rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800/50`}
            >
              <HelpCircle className="h-4 w-4 shrink-0" />
              {!sidebarCollapsed && <span>Support</span>}
            </button>

            <button
              onClick={handleSignOut}
              className={`w-full flex items-center ${sidebarCollapsed ? 'justify-center px-0' : 'gap-3 px-3'} py-2 text-red-500 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/30 text-xs font-medium rounded-lg`}
            >
              <LogOut className="h-4 w-4 shrink-0" />
              {!sidebarCollapsed && <span>Sign Out</span>}
            </button>
          </div>
        </aside>

        {/* Dynamic Page Content */}
        <main className="flex-1 p-6 overflow-auto bg-slate-50 dark:bg-slate-950">
          <Outlet />
        </main>
      </div>

      {/* Dispatcher Settings Modal */}
      <SettingsModal isOpen={isSettingsOpen} onClose={() => setIsSettingsOpen(false)} />
    </div>
  );
};

export const AppShell: React.FC = () => {
  return <AppShellContent />;
};
