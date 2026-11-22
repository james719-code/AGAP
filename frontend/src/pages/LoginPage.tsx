import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { AlertTriangle, ArrowLeft, ArrowRight, Eye, EyeOff, Lock, MapPin, Radio, ShieldCheck, User, Users } from 'lucide-react';
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
    <div className="flex min-h-dvh flex-col bg-background font-sans text-foreground lg:flex-row">
      <div className="relative flex flex-col justify-between overflow-hidden hero-ops px-5 py-5 text-[#f1f6f2] sm:px-10 lg:w-[45%] lg:p-12 xl:p-16">
        <img
          src={camsurMap}
          alt=""
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 hidden size-full select-none object-cover opacity-[0.15] invert lg:block"
        />

        {/* Top bar on Left Pane */}
        <div className="relative z-10 flex items-center justify-between gap-3">
          <Link
            to="/"
            aria-label="AGAP home"
            className="flex min-h-11 items-center rounded-xl p-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary motion-safe:active:scale-95 motion-safe:transition-transform motion-reduce:transition-none"
          >
            <span className="rounded-lg bg-white px-3 py-2"><img src={agapLogo} alt="AGAP" className="h-6 w-auto object-contain sm:h-7" /></span>
          </Link>

          <Link
            to="/"
            className="inline-flex min-h-11 items-center gap-1.5 rounded-lg border border-white/25 px-3.5 text-xs font-semibold text-white hover:bg-white/10 sm:text-sm"
          >
            <ArrowLeft className="size-3.5 shrink-0" aria-hidden="true" />
            <span>Back to AGAP</span>
          </Link>
        </div>

        <div className="relative z-10 hidden max-w-xl space-y-8 py-12 lg:block">
          <div className="inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-3.5 py-1.5 text-xs font-semibold text-white">
            <Radio className="size-3.5" aria-hidden="true" />
            <span>Command Center Gateway</span>
          </div>

          <div>
            <p className="max-w-xl text-4xl font-bold leading-[1.12] tracking-tight xl:text-5xl">
              Every alert matters.<br /><span className="bg-gradient-to-r from-[#ff7a63] to-[#f2a540] bg-clip-text text-transparent">Coordinate the response.</span>
            </p>
            <p className="mt-5 max-w-md text-base leading-relaxed text-[#a9b8bf]">
              Monitor incoming distress alerts, locate incidents, and coordinate responder assignments from one shared workspace.
            </p>
          </div>

          <div className="grid max-w-md gap-3">
            {[{ icon: Radio, label: 'Receive distress alerts' }, { icon: MapPin, label: 'See reported incident locations' }, { icon: Users, label: 'Coordinate assigned rescue units' }].map(({ icon: Icon, label }) => (
              <div key={label} className="flex items-center gap-3 rounded-lg border border-white/15 bg-white/[0.04] p-3">
                <span className="grid size-9 place-items-center rounded-md bg-white/10 text-[#ff9b84]"><Icon className="size-5" aria-hidden="true" /></span>
                <span className="text-sm font-semibold">{label}</span>
              </div>
            ))}
          </div>
        </div>

        <p className="relative z-10 hidden border-t border-white/20 pt-4 text-xs font-semibold tracking-widest text-[#a9b8bf] uppercase lg:block">Municipal emergency operations · AGAP</p>
      </div>

      {/* Right: Full-height Sign-in Form Pane */}
      <div className="flex flex-1 items-start justify-center bg-background px-4 py-8 sm:p-12 lg:items-center lg:p-16">
        <div className="surface-panel w-full max-w-lg space-y-7 p-5 sm:p-8 lg:p-10">
          <div className="flex items-center gap-3.5">
            <div className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary ring-1 ring-primary/20">
              <ShieldCheck className="size-6" aria-hidden="true" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
                Administrator sign in
              </h1>
              <p className="text-sm text-muted-foreground mt-1">
                Authorized municipal emergency operations access
              </p>
            </div>
          </div>

          <form onSubmit={handleLogin} className="space-y-4" aria-busy={isLoading}>
            <div>
              <label htmlFor="login-username" className="block text-sm font-semibold text-foreground">
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
                  placeholder="Enter your username"
                  onChange={(event) => setUsername(event.target.value)}
                  aria-invalid={Boolean(error)}
                  aria-describedby={error ? 'login-error' : undefined}
                  className="h-12 w-full rounded-lg border border-border bg-card pl-10 pr-4 text-base text-foreground placeholder:text-muted-foreground focus:ring-2 focus:ring-primary/20 focus:border-primary disabled:opacity-50"
                  autoComplete="username"
                  autoCapitalize="none"
                  spellCheck={false}
                  disabled={isLoading}
                />
              </div>
            </div>

            <div>
              <label htmlFor="login-password" className="block text-sm font-semibold text-foreground">
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
                  placeholder="Enter your password"
                  onChange={(event) => setPassword(event.target.value)}
                  aria-invalid={Boolean(error)}
                  aria-describedby={passwordDescribedBy}
                  className="h-12 w-full rounded-lg border border-border bg-card pl-10 pr-12 text-base text-foreground placeholder:text-muted-foreground focus:ring-2 focus:ring-primary/20 focus:border-primary disabled:opacity-50"
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
                  className="absolute inset-y-0 right-0 flex size-12 items-center justify-center text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded-lg"
                >
                  {showPassword ? <EyeOff aria-hidden="true" className="size-4" /> : <Eye aria-hidden="true" className="size-4" />}
                </button>
              </div>

              {capsLock && (
                <p
                  id="caps-lock-warning"
                  role="status"
                  className="tone-warning mt-1.5 inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium"
                >
                  <AlertTriangle className="size-3" aria-hidden="true" />
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
              className="mt-2 flex h-12 w-full items-center justify-center gap-2 rounded-lg bg-primary text-sm font-semibold text-primary-foreground shadow-sm hover:opacity-95 disabled:opacity-75"
            >
              <span>{isLoading ? 'Signing in…' : 'Sign in to Command Center'}</span>
              {!isLoading && <ArrowRight aria-hidden="true" className="size-4" />}
            </Button>
          </form>

          <div className="space-y-2 border-t border-border pt-5 text-sm leading-relaxed text-muted-foreground">
            <p>Need authorization assistance? Contact your municipal DRRMO disaster coordinator.</p>
            <p>Citizen or responder? Use the AGAP Android app. <Link to="/" className="font-semibold text-primary hover:underline">Return to the AGAP website.</Link></p>
          </div>
        </div>
      </div>
    </div>
  );
};
