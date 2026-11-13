import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { AlertTriangle, ArrowLeft, ArrowRight, Eye, EyeOff, Lock, Radio, ShieldCheck, User } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAuth } from '@/context/AuthContext';
import agapLogo from '../assets/logos/logo-name-nobg.png';
import camsurMap from '@/assets/maps/camsur.svg';

export const LoginPage: React.FC = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [capsLock, setCapsLock] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleLogin = async (event: React.FormEvent) => {
    event.preventDefault();
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

  const passwordDescribedBy = [
    capsLock ? 'caps-lock-warning' : '',
    error ? 'login-error' : '',
  ]
    .filter(Boolean)
    .join(' ') || undefined;

  return (
    <div className="flex min-h-dvh flex-col lg:flex-row bg-background text-foreground transition-colors font-sans">
      {/* Left: Full-screen Brand Presentation on Laptop with Edge-to-Edge Map Background */}
      <div className="relative flex flex-col justify-between overflow-hidden bg-slate-50/70 p-8 sm:p-12 lg:w-1/2 lg:p-14 xl:p-16 border-b border-border lg:border-b-0 lg:border-r">
        {/* Full-bleed Map Covering Entire Background Without Cutoff */}
        <img
          src={camsurMap}
          alt=""
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 size-full object-cover opacity-25 mix-blend-multiply select-none"
        />

        {/* Top bar on Left Pane */}
        <div className="relative z-10 flex items-center justify-between gap-3">
          <Link
            to="/"
            aria-label="AGAP home"
            className="flex min-h-11 items-center rounded-xl p-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary motion-safe:active:scale-95 motion-safe:transition-transform motion-reduce:transition-none"
          >
            <img src={agapLogo} alt="AGAP" className="h-7 w-auto object-contain sm:h-8" />
          </Link>

          <Link
            to="/"
            className="inline-flex min-h-9 items-center gap-1.5 rounded-full border border-border bg-white/90 px-3.5 text-xs font-semibold text-foreground shadow-2xs backdrop-blur-xs hover:bg-white motion-safe:active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary transition-colors sm:text-sm motion-reduce:transition-none"
          >
            <ArrowLeft className="size-3.5 shrink-0" aria-hidden="true" />
            <span>Back to AGAP</span>
          </Link>
        </div>

        {/* Middle Info on Left Pane */}
        <div className="relative z-10 my-10 space-y-6 max-w-xl">
          <div className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/10 px-3.5 py-1 text-xs font-semibold text-primary">
            <Radio className="size-3.5" aria-hidden="true" />
            <span>Command Center Gateway</span>
          </div>

          <div>
            <h1 className="text-3xl font-bold tracking-tight text-foreground sm:text-4xl lg:text-5xl leading-[1.08]">
              Coordinated Emergency Response
            </h1>
            <p className="mt-4 text-sm sm:text-base text-muted-foreground leading-relaxed max-w-lg">
              Real-time distress monitoring, responder geolocation dispatch, and incident coordination across Camarines Sur and Albay.
            </p>
          </div>

          {/* Key Metrics / Standards */}
          <div className="grid grid-cols-3 gap-3 pt-2 max-w-md">
            <div className="rounded-2xl border border-border bg-white/95 p-3.5 shadow-2xs backdrop-blur-xs">
              <span className="block text-[11px] font-medium text-muted-foreground">Security</span>
              <span className="mt-0.5 block font-mono text-base font-bold text-primary">Encrypted</span>
            </div>
            <div className="rounded-2xl border border-border bg-white/95 p-3.5 shadow-2xs backdrop-blur-xs">
              <span className="block text-[11px] font-medium text-muted-foreground">Max relay</span>
              <span className="mt-0.5 block font-mono text-base font-bold text-secondary">15 hops</span>
            </div>
            <div className="rounded-2xl border border-border bg-white/95 p-3.5 shadow-2xs backdrop-blur-xs">
              <span className="block text-[11px] font-medium text-muted-foreground">Active window</span>
              <span className="mt-0.5 block font-mono text-base font-bold text-accent">2 hours</span>
            </div>
          </div>
        </div>

        {/* Bottom Route Graphic on Left Pane */}
        <div className="relative z-10 pt-4" aria-hidden="true">
          <svg viewBox="0 0 640 100" className="h-auto w-full max-w-lg opacity-85">
            <path
              d="M20 50 C120 50 160 20 260 20 S380 80 460 60 540 30 600 30"
              fill="none"
              stroke="var(--color-brand-teal)"
              strokeWidth="2.5"
              strokeDasharray="4 6"
            />
            <circle cx="20" cy="50" r="5" fill="var(--color-brand-terracotta)" />
            <circle cx="260" cy="20" r="5" fill="var(--color-brand-teal)" />
            <circle cx="460" cy="60" r="5" fill="var(--color-brand-teal)" />
            <circle cx="600" cy="30" r="8" fill="var(--color-brand-terracotta)" />
            <circle cx="600" cy="30" r="16" fill="none" stroke="var(--color-brand-terracotta)" strokeWidth="1.5" opacity="0.35" />
          </svg>
        </div>
      </div>

      {/* Right: Full-height Sign-in Form Pane */}
      <div className="flex flex-1 items-center justify-center p-8 sm:p-12 lg:w-1/2 lg:p-16 bg-white">
        <div className="w-full max-w-md space-y-8">
          <div className="flex items-center gap-3.5">
            <div className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary ring-1 ring-primary/20">
              <ShieldCheck className="size-6" aria-hidden="true" />
            </div>
            <div>
              <h2 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
                Administrator Sign In
              </h2>
              <p className="text-xs sm:text-sm text-muted-foreground mt-0.5">
                Authorized municipal emergency operations access
              </p>
            </div>
          </div>

          <form onSubmit={handleLogin} className="space-y-4" aria-busy={isLoading}>
            <div>
              <label htmlFor="login-username" className="block text-xs font-semibold text-foreground">
                Username
              </label>
              <div className="relative mt-1.5">
                <User
                  aria-hidden="true"
                  className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
                />
                <Input
                  id="login-username"
                  required
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  aria-invalid={Boolean(error)}
                  aria-describedby={error ? 'login-error' : undefined}
                  className="h-11 w-full rounded-xl border border-border bg-slate-50/60 pl-10 pr-4 text-sm text-foreground placeholder:text-muted-foreground focus:bg-white focus:ring-2 focus:ring-primary/20 focus:border-primary disabled:opacity-50 transition-colors"
                  autoComplete="username"
                  autoCapitalize="none"
                  spellCheck={false}
                  disabled={isLoading}
                />
              </div>
            </div>

            <div>
              <label htmlFor="login-password" className="block text-xs font-semibold text-foreground">
                Password
              </label>
              <div className="relative mt-1.5">
                <Lock
                  aria-hidden="true"
                  className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
                />
                <Input
                  id="login-password"
                  type={showPassword ? 'text' : 'password'}
                  required
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  aria-invalid={Boolean(error)}
                  aria-describedby={passwordDescribedBy}
                  className="h-11 w-full rounded-xl border border-border bg-slate-50/60 pl-10 pr-11 text-sm text-foreground placeholder:text-muted-foreground focus:bg-white focus:ring-2 focus:ring-primary/20 focus:border-primary disabled:opacity-50 transition-colors"
                  autoComplete="current-password"
                  disabled={isLoading}
                  onKeyUp={(event) => setCapsLock(event.getModifierState('CapsLock'))}
                  onKeyDown={(event) => setCapsLock(event.getModifierState('CapsLock'))}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((value) => !value)}
                  aria-pressed={showPassword}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  disabled={isLoading}
                  className="absolute inset-y-0 right-0 flex size-11 items-center justify-center text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded-xl"
                >
                  {showPassword ? <EyeOff aria-hidden="true" className="size-4" /> : <Eye aria-hidden="true" className="size-4" />}
                </button>
              </div>

              {capsLock && (
                <p
                  id="caps-lock-warning"
                  role="status"
                  className="mt-1.5 inline-flex items-center gap-1.5 rounded-md bg-accent/15 px-2 py-0.5 text-xs font-medium text-foreground ring-1 ring-accent/30"
                >
                  <AlertTriangle className="size-3 text-accent" aria-hidden="true" />
                  <span>Caps Lock is on</span>
                </p>
              )}
            </div>

            {error && (
              <div
                id="login-error"
                role="alert"
                className="flex items-start gap-2.5 rounded-xl border border-destructive/30 bg-destructive/10 p-3 text-xs font-medium text-destructive"
              >
                <AlertTriangle aria-hidden="true" className="size-4 shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            )}

            <Button
              type="submit"
              disabled={isLoading || !username.trim() || !password}
              className="mt-2 flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-primary text-sm font-semibold text-primary-foreground shadow-sm hover:opacity-95 motion-safe:active:scale-98 disabled:opacity-75 transition-opacity focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
            >
              <span>{isLoading ? 'Signing in…' : 'Sign in to Command Center'}</span>
              {!isLoading && <ArrowRight aria-hidden="true" className="size-4" />}
            </Button>
          </form>

          <p className="border-t border-border pt-5 text-xs text-muted-foreground">
            Need authorization assistance? Contact your municipal DRRMO disaster coordinator.
          </p>
        </div>
      </div>
    </div>
  );
};
