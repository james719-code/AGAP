import React, { useEffect, useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { Shield, LayoutDashboard, AlertTriangle, Building2, Users, Radio, Activity, Sun, Moon, Volume2, VolumeX } from 'lucide-react';
import { apiService } from '@/services/api';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useTheme } from '@/context/ThemeContext';
import { audioAlert } from '@/lib/audioAlert';
import { Button } from '@/components/ui/button';

export const AppShellContent: React.FC = () => {
  const [apiOnline, setApiOnline] = useState<boolean | null>(null);
  const [soundEnabled, setSoundEnabled] = useState(false);
  const { isConnected: wsConnected, lastIncident } = useWebSocket();
  const { theme, toggleTheme } = useTheme();

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

  // Trigger audio siren alert when new incident arrives via WebSocket
  useEffect(() => {
    if (lastIncident && soundEnabled) {
      audioAlert.playEmergencySiren();
    }
  }, [lastIncident, soundEnabled]);

  const toggleSound = () => {
    const nextState = !soundEnabled;
    setSoundEnabled(nextState);
    audioAlert.setEnabled(nextState);
    if (nextState) {
      audioAlert.playEmergencySiren(); // Test tone
    }
  };

  const navItems = [
    { to: '/', label: 'Executive Overview', icon: LayoutDashboard },
    { to: '/incidents', label: 'Incident Triage & Dispatch', icon: AlertTriangle },
    { to: '/agencies', label: 'Agency Network', icon: Building2 },
    { to: '/responders', label: 'Responder Fleet', icon: Users },
  ];

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans transition-colors duration-200">
      {/* Top Navbar */}
      <header className="h-16 border-b border-slate-800 bg-slate-900/90 backdrop-blur px-6 flex items-center justify-between sticky top-0 z-40">
        <div className="flex items-center gap-3">
          <div className="bg-emerald-500/10 p-2 rounded-lg border border-emerald-500/30">
            <Shield className="h-6 w-6 text-emerald-400" />
          </div>
          <div>
            <h1 className="text-lg font-bold tracking-wider text-slate-100 flex items-center gap-2">
              AGAP <span className="text-xs px-2 py-0.5 rounded bg-red-950 text-red-400 border border-red-800 font-mono font-bold">ADMIN COMMAND CENTER</span>
            </h1>
            <p className="text-xs text-slate-400">Master Emergency Dispatch & Mesh P2P GIS Operations</p>
          </div>
        </div>

        {/* System Health & Action Controls */}
        <div className="flex items-center gap-3 text-xs font-mono">
          {/* Admin Role Tag */}
          <div className="hidden sm:flex items-center gap-1.5 bg-slate-800/80 px-2.5 py-1 rounded border border-slate-700 text-slate-300">
            <span className="h-2 w-2 rounded-full bg-emerald-400"></span>
            <span>ROLE: ADMIN OPERATOR</span>
          </div>

          {/* Audio Alert Toggle */}
          <Button
            onClick={toggleSound}
            variant="outline"
            size="sm"
            className={`h-8 px-2.5 flex items-center gap-1.5 border-slate-800 ${
              soundEnabled ? 'bg-amber-500/10 text-amber-400 border-amber-500/30' : 'text-slate-400'
            }`}
            title="Toggle Audio Siren Alert on incoming SOS"
          >
            {soundEnabled ? <Volume2 className="h-4 w-4 text-amber-400 animate-pulse" /> : <VolumeX className="h-4 w-4 text-slate-500" />}
            <span className="hidden sm:inline">{soundEnabled ? 'SIREN ON' : 'SIREN MUTED'}</span>
          </Button>

          {/* Theme Toggle Button */}
          <Button
            onClick={toggleTheme}
            variant="outline"
            size="sm"
            className="h-8 w-8 p-0 border-slate-800 text-slate-300 hover:text-slate-100"
            title="Toggle Dark / Light Mode"
          >
            {theme === 'dark' ? <Sun className="h-4 w-4 text-amber-400" /> : <Moon className="h-4 w-4 text-sky-400" />}
          </Button>

          {/* Health Badges */}
          <div className="hidden md:flex items-center gap-2 bg-slate-950 px-3 py-1.5 rounded-md border border-slate-800">
            <span className="text-slate-400">API:</span>
            {apiOnline === null ? (
              <span className="text-amber-400">Checking...</span>
            ) : apiOnline ? (
              <span className="text-emerald-400 flex items-center gap-1">
                <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse"></span> ONLINE
              </span>
            ) : (
              <span className="text-red-400 flex items-center gap-1">
                <span className="h-2 w-2 rounded-full bg-red-400"></span> OFFLINE
              </span>
            )}
          </div>

          <div className="hidden lg:flex items-center gap-2 bg-slate-950 px-3 py-1.5 rounded-md border border-slate-800">
            <Radio className={`h-3.5 w-3.5 ${wsConnected ? 'text-sky-400 animate-pulse' : 'text-slate-500'}`} />
            <span className="text-slate-400">WS HUB:</span>
            {wsConnected ? (
              <span className="text-sky-400 font-semibold">CONNECTED</span>
            ) : (
              <span className="text-slate-500">RECONNECTING</span>
            )}
          </div>
        </div>
      </header>

      {/* Main Layout */}
      <div className="flex flex-1">
        {/* Left Sidebar */}
        <aside className="w-64 border-r border-slate-800 bg-slate-900/60 p-4 flex flex-col justify-between">
          <nav className="space-y-1">
            <div className="text-[10px] uppercase font-bold tracking-widest text-slate-500 px-3 mb-2">
              Admin Operations
            </div>
            {navItems.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/'}
                  className={({ isActive }) =>
                    `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all ${
                      isActive
                        ? 'bg-red-500/10 text-red-400 border border-red-500/30 font-semibold'
                        : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/60'
                    }`
                  }
                >
                  <Icon className="h-4 w-4" />
                  {item.label}
                </NavLink>
              );
            })}
          </nav>

          <div className="p-3 bg-slate-950/80 rounded-lg border border-slate-800 text-xs text-slate-400 space-y-1">
            <div className="flex items-center gap-2 text-slate-300 font-semibold">
              <Activity className="h-4 w-4 text-emerald-400" />
              Mesh Dispatch Server
            </div>
            <p className="text-[11px] font-mono text-slate-500 truncate">
              05-05-12-01-04::LegazpiHQ
            </p>
          </div>
        </aside>

        {/* Dynamic Page Content */}
        <main className="flex-1 p-6 overflow-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export const AppShell: React.FC = () => {
  return <AppShellContent />;
};
