import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Shield, Lock, User, ArrowRight, Sun, Moon, Key, Activity, Radio, CheckCircle2, Globe } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Tooltip } from '@/components/ui/tooltip';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';

export const LoginPage: React.FC = () => {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('admin123');
  const [error, setError] = useState<string | null>(null);
  const { login } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const navigate = useNavigate();

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await login(username, password);
      navigate('/dashboard');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    }
  };

  const handleDemoLogin = async () => {
    setUsername('admin');
    setPassword('admin123');
    setError(null);
    try {
      await login('admin', 'admin123');
      navigate('/dashboard');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Demo login failed');
    }
  };

  return (
    <div className="min-h-screen w-full flex font-sans bg-slate-100 dark:bg-slate-950 text-slate-900 dark:text-slate-100 transition-colors duration-200">
      {/* LEFT HALF: P2P Mesh Topological Visual Panel */}
      <div className="hidden lg:flex lg:w-1/2 bg-slate-900 p-12 text-white flex-col justify-between relative overflow-hidden border-r border-slate-800">
        {/* Clean Minimalist Dot Grid Matrix Background */}
        <div className="absolute inset-0 pointer-events-none opacity-40">
          <svg className="w-full h-full" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <pattern id="dotGrid" width="32" height="32" patternUnits="userSpaceOnUse">
                <circle cx="2" cy="2" r="1.5" fill="#334155" />
              </pattern>
            </defs>
            <rect width="100%" height="100%" fill="url(#dotGrid)" />
          </svg>
        </div>

        {/* Brand Header */}
        <div className="relative z-10">
          <Link to="/" className="flex items-center gap-3 w-fit">
            <div className="bg-red-500/15 p-2.5 rounded-xl border border-red-500/30">
              <Shield className="h-6 w-6 text-red-500" />
            </div>
            <div>
              <span className="text-2xl font-black tracking-wider text-white">AGAP</span>
              <p className="text-xs text-slate-400 font-mono">Region V DRRMO Emergency Engine</p>
            </div>
          </Link>
        </div>

        {/* Middle Content — Clean Text without Card Box Wrapper */}
        <div className="relative z-10 space-y-6 my-auto max-w-lg">
          <Badge variant="outline" className="text-xs font-mono border-teal-500/50 text-teal-400 bg-teal-950/60 px-3 py-1">
            <Radio className="h-3.5 w-3.5 mr-1.5 text-teal-400 animate-pulse" /> P2P MESH DISPATCH CONSOLE
          </Badge>

          <h1 className="text-4xl font-black text-white tracking-tight leading-tight">
            Off-Grid Telemetry & Crisis Mesh Dispatch
          </h1>

          <p className="text-sm text-slate-300 leading-relaxed font-normal">
            Command headquarters portal for real-time spatial GIS triage, multi-hop Bluetooth advertising packet inspection, and emergency fleet dispatch during catastrophic infrastructure blackouts.
          </p>

          <div className="space-y-3 pt-2">
            <div className="flex items-center gap-3 text-xs text-slate-200">
              <div className="w-6 h-6 rounded-full bg-teal-500/20 text-teal-400 flex items-center justify-center shrink-0">
                <CheckCircle2 className="h-4 w-4" />
              </div>
              <span>Dual-channel Extended Advertising (255B) & GATT fallback</span>
            </div>

            <div className="flex items-center gap-3 text-xs text-slate-200">
              <div className="w-6 h-6 rounded-full bg-teal-500/20 text-teal-400 flex items-center justify-center shrink-0">
                <CheckCircle2 className="h-4 w-4" />
              </div>
              <span>Sub-second target ID resolution across Region V Albay landmarks</span>
            </div>

            <div className="flex items-center gap-3 text-xs text-slate-200">
              <div className="w-6 h-6 rounded-full bg-teal-500/20 text-teal-400 flex items-center justify-center shrink-0">
                <CheckCircle2 className="h-4 w-4" />
              </div>
              <span>Zero cellular internet dependency with cloud shortcut relays</span>
            </div>
          </div>
        </div>

        {/* Bottom Metrics Bar */}
        <div className="relative z-10 pt-6 border-t border-slate-800 grid grid-cols-3 gap-4 text-center font-mono">
          <div className="bg-slate-800/50 p-3 rounded-xl border border-slate-700/50">
            <div className="text-base font-bold text-teal-400">255 Bytes</div>
            <div className="text-[10px] text-slate-400">BLE ADV Payload</div>
          </div>
          <div className="bg-slate-800/50 p-3 rounded-xl border border-slate-700/50">
            <div className="text-base font-bold text-emerald-400">0.8 s</div>
            <div className="text-[10px] text-slate-400">Mesh Hop Latency</div>
          </div>
          <div className="bg-slate-800/50 p-3 rounded-xl border border-slate-700/50">
            <div className="text-base font-bold text-sky-400">100%</div>
            <div className="text-[10px] text-slate-400">Off-Grid Resilience</div>
          </div>
        </div>
      </div>

      {/* RIGHT HALF: Form Sign In Section with Crisp High-Contrast Container */}
      <div className="w-full lg:w-1/2 flex flex-col justify-between p-6 sm:p-12 bg-slate-50 dark:bg-slate-950 relative">
        {/* Top Header Controls */}
        <div className="flex items-center justify-between w-full pb-6">
          <div className="lg:hidden flex items-center gap-2">
            <Shield className="h-6 w-6 text-red-500" />
            <span className="text-lg font-bold text-slate-900 dark:text-slate-100">AGAP</span>
          </div>

          <div className="flex items-center gap-3 ml-auto">
            <Tooltip content={theme === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'} side="bottom">
              <Button
                onClick={toggleTheme}
                variant="outline"
                size="sm"
                className="h-9 w-9 p-0 rounded-lg border-slate-300 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-300 hover:text-slate-900 dark:hover:text-slate-100 shadow-sm"
              >
                {theme === 'dark' ? <Sun className="h-4 w-4 text-amber-400" /> : <Moon className="h-4 w-4 text-sky-500" />}
              </Button>
            </Tooltip>

            <Link to="/">
              <Button variant="outline" size="sm" className="h-9 border-slate-300 dark:border-slate-800 bg-white dark:bg-slate-900 text-xs font-medium gap-1.5 shadow-sm">
                <Globe className="h-3.5 w-3.5" />
                Landing Page
              </Button>
            </Link>
          </div>
        </div>

        {/* Center Form Card Container */}
        <div className="max-w-md w-full mx-auto my-auto p-8 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800/80 rounded-2xl shadow-xl space-y-6">
          <div className="space-y-2 text-center sm:text-left">
            <div className="inline-flex p-3 rounded-2xl bg-red-500/10 border border-red-500/20 text-red-500 dark:text-red-400 mb-1">
              <Key className="h-6 w-6" />
            </div>
            <h2 className="text-2xl font-extrabold text-slate-900 dark:text-slate-100 tracking-tight">
              Admin Sign In
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Enter your credentials to access Legazpi Command Headquarters
            </p>
          </div>

          <form onSubmit={handleLogin} className="space-y-4">
            <div className="space-y-1.5">
              <label className="text-xs font-bold text-slate-700 dark:text-slate-300">Operator Username</label>
              <div className="relative">
                <User className="absolute left-3.5 top-3 h-4 w-4 text-slate-400" />
                <Input
                  type="text"
                  required
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="pl-10 h-11 border-slate-200 dark:border-slate-700 text-xs bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100"
                  placeholder="admin"
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-bold text-slate-700 dark:text-slate-300">Password</label>
              <div className="relative">
                <Lock className="absolute left-3.5 top-3 h-4 w-4 text-slate-400" />
                <Input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="pl-10 h-11 border-slate-200 dark:border-slate-700 text-xs bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100"
                  placeholder="••••••••••••"
                />
              </div>
            </div>

            {error && (
              <div className="rounded-lg border border-red-500/30 bg-red-50 dark:bg-red-950/40 px-3 py-2 text-xs font-semibold text-red-700 dark:text-red-300">
                {error}
              </div>
            )}

            <Button type="submit" className="w-full h-11 bg-red-600 hover:bg-red-500 text-white font-bold flex items-center justify-center gap-2 rounded-xl text-xs mt-2 shadow-md">
              Sign In to Console
              <ArrowRight className="h-4 w-4" />
            </Button>
          </form>

          <div className="relative flex items-center justify-center py-1">
            <div className="border-t border-slate-200 dark:border-slate-800 w-full" />
            <span className="bg-white dark:bg-slate-900 px-3 text-[10px] uppercase text-slate-400 font-mono absolute">quick access</span>
          </div>

          {/* Quick Demo Button */}
          <Button
            type="button"
            onClick={handleDemoLogin}
            variant="outline"
            className="w-full h-11 border-emerald-500/30 bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 hover:bg-emerald-100 dark:hover:bg-emerald-950/70 font-bold flex items-center justify-center gap-2 rounded-xl text-xs"
          >
            <Activity className="h-4 w-4 text-emerald-500" />
            Quick Demo Operator Login
          </Button>
        </div>

        {/* Security Footer */}
        <div className="pt-6 text-center text-xs text-slate-400">
          AGAP Security Portal · Region V Legazpi City DRRMO Engine
        </div>
      </div>
    </div>
  );
};
