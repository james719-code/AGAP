import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Lock, User, ArrowRight, Eye, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAuth } from '@/context/AuthContext';
import agapLogo from '../assets/logos/logo-name-nobg.png';
import Beams from '@/components/Beams/Beams';

export const LoginPage: React.FC = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [capsLock, setCapsLock] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);
    try {
      await login(username.trim(), password);
      navigate('/dashboard');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="light min-h-screen w-full bg-white text-[#0A0A0A] font-sans antialiased">
      <div className="min-h-screen w-full">
        <div className="min-h-screen w-full flex flex-col lg:flex-row">
          {/* LEFT HALF: Brand panel – content pushed to bottom */}
          <div className="hidden lg:flex lg:w-1/2 bg-[#0F1214] px-12 xl:px-20 py-10 text-white flex-col relative overflow-hidden border-r border-[#26292C]">
            <div className="absolute inset-0 pointer-events-none">
              <Beams
                beamWidth={2}
                beamHeight={30}
                beamNumber={10}
                lightColor="#E9835F"
                speed={2}
                noiseIntensity={1.75}
                scale={0.15}
                rotation={30}
              />
            </div>
            <div className="absolute inset-0 pointer-events-none subtle-grid opacity-70" />
            <div className="absolute inset-0 bg-gradient-to-b from-[#0F1214]/25 via-transparent to-[#0F1214] pointer-events-none" />

            <div className="relative z-10 flex flex-col h-full">
              <Link
                to="/"
                className="inline-flex items-center gap-2 text-sm font-medium text-[#B9B6B0] hover:text-white transition-colors w-fit"
              >
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" className="h-4 w-4">
                  <path d="M12 2L4 8L12 14V2Z" fill="currentColor" />
                </svg>
                Back
              </Link>

              {/* Content pushed to bottom with mt-auto, left aligned */}
              <div className="mt-auto space-y-7 max-w-xl pb-16">
                <span className="text-xs font-semibold tracking-[0.14em] text-[#E9835F] uppercase">
                  Command Center
                </span>
                <h1 className="text-4xl xl:text-5xl font-bold text-white tracking-tight leading-[1.08]">
                  Emergency alerts that reach responders, even offline.
                </h1>
                <p className="max-w-lg text-base text-[#B9B6B0] leading-relaxed">
                  Sign in to monitor incoming alerts, dispatch responders, and keep the
                  relay moving across Legazpi City.
                </p>
                {/* Trust strip removed as requested */}
              </div>
            </div>
          </div>

          {/* RIGHT HALF: Login panel – light mode only */}
          <div className="w-full lg:w-1/2 flex flex-col justify-center px-6 py-10 sm:px-12 lg:px-16 xl:px-24 bg-white relative min-h-screen lg:min-h-0">
            <div className="max-w-sm w-full mx-auto space-y-8">
              <Link
                to="/"
                className="lg:hidden inline-flex items-center gap-2 text-sm font-medium text-[#6B6864] hover:text-[#0A0A0A] transition-colors w-fit"
              >
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" className="h-4 w-4">
                  <path d="M12 2L4 8L12 14V2Z" fill="currentColor" />
                </svg>
                Back to AGAP
              </Link>

              {/* Logo – bigger and centred */}
              <div className="space-y-5">
                <div className="flex justify-center">
                 <img src={agapLogo} alt="AGAP" className="h-10 w-auto object-contain mb-4" />
                </div>
                <div className="space-y-2.5 text-left">
                  <span className="text-xs font-semibold tracking-[0.14em] text-[#D15033] uppercase">
                    Dispatcher Sign In
                  </span>
                  <h2 className="text-3xl font-bold text-[#0A0A0A] tracking-tight">
                    Welcome back
                  </h2>
                  <p className="text-sm text-[#6B6864] leading-relaxed">
                    Enter your credentials to access Legazpi Command Headquarters
                  </p>
                </div>
              </div>

              <form onSubmit={handleLogin} className="space-y-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-[#3A3936]">Username</label>
                  <div className="relative group">
                    <User className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-[#9C9994] transition-colors group-focus-within:text-[#D15033]" />
                    <Input
                      type="text"
                      required
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      className="pl-10 h-12 rounded-[2px] border-[#E7E3DC] text-sm bg-[#F7F5F2] text-[#0A0A0A] shadow-none focus-visible:ring-1 focus-visible:ring-[#D15033] focus-visible:border-[#D15033]"
                      placeholder="admin"
                      autoComplete="username"
                      autoCapitalize="none"
                      spellCheck={false}
                      disabled={isLoading}
                      autoFocus
                    />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-[#3A3936]">Password</label>
                  <div className="relative group">
                    <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-[#9C9994] transition-colors group-focus-within:text-[#D15033]" />
                    <Input
                      type={showPassword ? 'text' : 'password'}
                      required
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      className="pl-10 pr-11 h-12 rounded-[2px] border-[#E7E3DC] text-sm bg-[#F7F5F2] text-[#0A0A0A] shadow-none focus-visible:ring-1 focus-visible:ring-[#D15033] focus-visible:border-[#D15033]"
                      placeholder="••••••••••••"
                      autoComplete="current-password"
                      disabled={isLoading}
                      onKeyUp={(event) => setCapsLock(event.getModifierState('CapsLock'))}
                      onKeyDown={(event) => setCapsLock(event.getModifierState('CapsLock'))}
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword((value) => !value)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 rounded-[2px] p-1 text-[#6B6864] hover:bg-black/5 hover:text-[#0A0A0A] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D15033]"
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  {capsLock && (
                    <p className="text-[11px] font-medium text-[#B5761C]">Caps Lock is on</p>
                  )}
                </div>

                {error && (
                  <p className="text-xs text-[#D15033] font-medium bg-[#D15033]/10 border border-[#D15033]/20 rounded-[2px] px-3 py-2">
                    {error}
                  </p>
                )}

                <Button
                  type="submit"
                  disabled={isLoading || !username.trim() || !password}
                  className="w-full h-12 rounded-[2px] bg-[#D15033] hover:bg-[#B5432A] text-white font-semibold flex items-center justify-center gap-2 text-sm mt-2 disabled:opacity-50 transition-all hover:shadow-[0_6px_18px_rgba(209,80,51,0.28)] active:scale-[0.99]"
                >
                  {isLoading ? 'Signing In...' : 'Sign In to Console'}
                  {!isLoading && <ArrowRight className="h-4 w-4" />}
                </Button>
              </form>

              <div className="pt-5 border-t border-[#E7E3DC] text-center text-xs text-[#6B6864]">
                AGAP · Legazpi City DRRMO
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};