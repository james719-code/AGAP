import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Lock, User, ArrowRight, KeyRound, Radio, CheckCircle2, Sun, Moon } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Tooltip } from '@/components/ui/tooltip';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import agapLogo from '../assets/logos/logo-name-nobg.png';

export const LoginPage: React.FC = () => {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('admin123');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const navigate = useNavigate();

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);
    try {
      await login(username, password);
      navigate('/dashboard');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen w-full flex flex-col bg-white dark:bg-[#0F1214] text-[#0A0A0A] dark:text-[#F2F0EC] font-sans">
      {/* Letterhead accent rule — matches landing page */}
      <div className="h-1 w-full bg-[#D15033] shrink-0" />

      <div className="flex-1 w-full flex">
        {/* LEFT HALF: Brand / mesh dispatch panel */}
        <div className="hidden lg:flex lg:w-1/2 bg-[#0F1214] p-12 text-white flex-col justify-between relative overflow-hidden border-r border-[#26292C]">
          <div className="absolute inset-0 pointer-events-none opacity-[0.35]">
            <svg className="w-full h-full" xmlns="http://www.w3.org/2000/svg">
              <defs>
                <pattern id="dotGrid" width="32" height="32" patternUnits="userSpaceOnUse">
                  <circle cx="2" cy="2" r="1.5" fill="#26292C" />
                </pattern>
              </defs>
              <rect width="100%" height="100%" fill="url(#dotGrid)" />
            </svg>
          </div>

          <div className="relative z-10">
            <Link to="/" className="flex items-center gap-3 w-fit">
              <img
                src={agapLogo}
                alt="AGAP"
                className="h-16 w-auto object-contain shrink-0"
              />
              <p className="text-xs text-[#9C9994] font-medium hidden sm:block tracking-wide leading-tight max-w-[9rem]">
                Accelerated Geolocation Assistance &amp; Protection
              </p>
            </Link>
          </div>

          <div className="relative z-10 space-y-6 my-auto max-w-lg">

            <h1 className="text-4xl font-bold text-white tracking-tight leading-[1.15]">
              Off-Grid Telemetry &amp; Crisis Mesh Dispatch
            </h1>

            <p className="text-sm text-[#B9B6B0] leading-relaxed">
              Command headquarters portal for real-time spatial GIS triage, multi-hop Bluetooth advertising
              packet inspection, and emergency fleet dispatch during catastrophic infrastructure blackouts.
            </p>

            <div className="space-y-3 pt-2">
              <div className="flex items-center gap-3 text-xs text-[#CCC9C3]">
                <div className="h-6 w-6 rounded-md bg-[#1F91A8]/15 text-[#4FB4C9] flex items-center justify-center shrink-0">
                  <CheckCircle2 className="h-3.5 w-3.5" />
                </div>
                <span>Dual-channel Extended Advertising (255B) &amp; GATT fallback</span>
              </div>

              <div className="flex items-center gap-3 text-xs text-[#CCC9C3]">
                <div className="h-6 w-6 rounded-md bg-[#1F91A8]/15 text-[#4FB4C9] flex items-center justify-center shrink-0">
                  <CheckCircle2 className="h-3.5 w-3.5" />
                </div>
                <span>Sub-second target ID resolution across Region V Albay landmarks</span>
              </div>

              <div className="flex items-center gap-3 text-xs text-[#CCC9C3]">
                <div className="h-6 w-6 rounded-md bg-[#1F91A8]/15 text-[#4FB4C9] flex items-center justify-center shrink-0">
                  <CheckCircle2 className="h-3.5 w-3.5" />
                </div>
                <span>Zero cellular internet dependency with cloud shortcut relays</span>
              </div>
            </div>
          </div>
        </div>

        {/* RIGHT HALF: Sign in form */}
        <div className="w-full lg:w-1/2 flex flex-col justify-between p-6 sm:p-12 bg-[#F7F5F2] dark:bg-[#141719] relative">
          <div className="flex items-center justify-between lg:justify-end">
            <Link to="/" className="flex items-center gap-2 lg:hidden">
              <img src={agapLogo} alt="AGAP" className="h-14 w-auto object-contain" />
            </Link>
            <Tooltip content={theme === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'}>
              <Button
                onClick={toggleTheme}
                variant="outline"
                size="sm"
                className="h-9 w-9 p-0 rounded-md border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-transparent text-[#3A3936] dark:text-[#CCC9C3]"
              >
                {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
              </Button>
            </Tooltip>
          </div>

          <div className="max-w-md w-full mx-auto my-auto p-8 bg-white dark:bg-[#181C1F] border border-[#E7E3DC] dark:border-[#26292C] rounded-2xl shadow-sm space-y-6">
            <div className="space-y-2 text-center sm:text-left">
              <div className="inline-flex p-3 rounded-md bg-[#D15033]/10 dark:bg-[#D15033]/15 text-[#D15033] dark:text-[#E9835F] mb-1">
                <KeyRound className="h-5 w-5" />
              </div>
              <h2 className="text-2xl font-bold text-[#0A0A0A] dark:text-white tracking-tight">
                Dispatcher Sign In
              </h2>
              <p className="text-xs text-[#6B6864] dark:text-[#9C9994]">
                Enter your credentials to access Legazpi Command Headquarters
              </p>
            </div>

            <form onSubmit={handleLogin} className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-[#3A3936] dark:text-[#CCC9C3]">Username</label>
                <div className="relative">
                  <User className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-[#9C9994]" />
                  <Input
                    type="text"
                    required
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    className="pl-10 h-11 rounded-md border-[#E7E3DC] dark:border-[#26292C] text-sm bg-[#FAF8F5] dark:bg-[#0F1214] text-[#0A0A0A] dark:text-[#F2F0EC]"
                    placeholder="admin"
                    autoComplete="username"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-[#3A3936] dark:text-[#CCC9C3]">Password</label>
                <div className="relative">
                  <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-[#9C9994]" />
                  <Input
                    type="password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="pl-10 h-11 rounded-md border-[#E7E3DC] dark:border-[#26292C] text-sm bg-[#FAF8F5] dark:bg-[#0F1214] text-[#0A0A0A] dark:text-[#F2F0EC]"
                    placeholder="••••••••••••"
                    autoComplete="current-password"
                  />
                </div>
              </div>

              {error && (
                <p className="text-xs text-[#D15033] font-medium bg-[#D15033]/10 border border-[#D15033]/20 rounded-md px-3 py-2">
                  {error}
                </p>
              )}

              <Button
                type="submit"
                disabled={isLoading}
                className="w-full h-11 rounded-md bg-[#D15033] hover:bg-[#B5432A] text-white font-semibold flex items-center justify-center gap-2 text-sm mt-2 disabled:opacity-50"
              >
                {isLoading ? 'Signing In...' : 'Sign In to Console'}
                {!isLoading && <ArrowRight className="h-4 w-4" />}
              </Button>
            </form>
          </div>

          <div className="pt-6 text-center text-xs text-[#6B6864] dark:text-[#9C9994]">
            AGAP Security Portal · Region V Legazpi City DRRMO Engine
          </div>
        </div>
      </div>
    </div>
  );
};