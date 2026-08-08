import React, { useEffect, useState } from 'react';
import { NavLink, Outlet, useNavigate, Link } from 'react-router-dom';
import {
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
  Clock,
  BookOpenText,
  ClipboardList,
  ShieldCheck
} from 'lucide-react';
import { apiService } from '@/services/api';
import { useWebSocket } from '@/context/WebSocketContext';
import { useTheme } from '@/context/ThemeContext';
import { useAuth } from '@/context/AuthContext';
import { audioAlert } from '@/lib/audioAlert';
import { Button } from '@/components/ui/button';
import { Tooltip } from '@/components/ui/tooltip';
import { SettingsModal } from '@/components/settings/SettingsModal';
import agapLogo from "../../assets/logos/logo-name-nobg.png";

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

          {/* Notifications Bell Icon — Only Blinks when unreadCount > 0 */}
          <Tooltip content={unreadCount > 0 ? `${unreadCount} New Alerts` : 'System Notifications'} side="bottom">
            <Button
              onClick={handleNotificationsClick}
              variant="outline"
              size="sm"
              className="h-9 w-9 p-0 rounded-md text-muted-foreground relative"
            >
              <Bell className="h-4 w-4" />
              {unreadCount > 0 && (
                <span className="absolute top-1.5 right-1.5 h-2.5 w-2.5 rounded-full bg-primary animate-ping" />
              )}
            </Button>
          </Tooltip>

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