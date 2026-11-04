import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Lock, User, ArrowRight, Eye, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAuth } from '@/context/AuthContext';
import agapLogo from '../assets/logos/logo-name-nobg.png';

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

  return (
    <main className="light min-h-dvh bg-background px-5 py-6 text-foreground sm:px-10 sm:py-10 lg:py-16">
      <div className="mx-auto grid w-full max-w-5xl gap-7 lg:grid-cols-2 lg:items-center lg:gap-12">
        <section aria-labelledby="login-heading" className="space-y-4">
          <Link to="/" className="inline-flex min-h-11 items-center text-sm font-semibold text-muted-foreground hover:text-primary">
            Back to AGAP
          </Link>
          <div>
            <img src={agapLogo} alt="AGAP PH" className="h-9 w-auto object-contain" />
            <p className="mt-2 text-sm font-medium text-muted-foreground">Assistance &amp; protection</p>
          </div>
          <div className="border-l-2 border-primary pl-5">
            <p className="mb-2 text-sm font-semibold text-primary">AGAP Command Center</p>
            <h1 id="login-heading" className="text-4xl font-bold leading-tight tracking-tight sm:text-5xl">
              Closer to help.<br />Wherever you are.
            </h1>
          </div>
          <p className="max-w-sm leading-relaxed text-muted-foreground">
            Monitor incoming alerts, coordinate responders, and track incident updates.
          </p>
        </section>

        <section className="surface-panel p-5 sm:p-6" aria-labelledby="form-heading">
          <h2 id="form-heading" className="text-2xl font-bold tracking-tight">Welcome back</h2>
          <p className="mt-1 text-sm text-muted-foreground">Sign in to the Command Center.</p>

          <form onSubmit={handleLogin} className="mt-5 space-y-4">
            <div className="space-y-2">
              <label htmlFor="login-username" className="text-sm font-semibold">Username</label>
              <div className="relative">
                <User aria-hidden="true" className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  id="login-username"
                  required
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  className="h-12 bg-card pl-10"
                  autoComplete="username"
                  autoCapitalize="none"
                  spellCheck={false}
                  disabled={isLoading}
                />
              </div>
            </div>

            <div className="space-y-2">
              <label htmlFor="login-password" className="text-sm font-semibold">Password</label>
              <div className="relative">
                <Lock aria-hidden="true" className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  id="login-password"
                  type={showPassword ? 'text' : 'password'}
                  required
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  className="h-12 bg-card pl-10 pr-12"
                  autoComplete="current-password"
                  disabled={isLoading}
                  onKeyUp={(event) => setCapsLock(event.getModifierState('CapsLock'))}
                  onKeyDown={(event) => setCapsLock(event.getModifierState('CapsLock'))}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((value) => !value)}
                  className="absolute inset-y-0 right-0 grid w-12 place-items-center rounded-md text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff aria-hidden="true" className="h-4 w-4" /> : <Eye aria-hidden="true" className="h-4 w-4" />}
                </button>
              </div>
              {capsLock && <p className="text-xs text-muted-foreground">Caps Lock is on</p>}
            </div>

            {error && (
              <p role="alert" className="rounded-md border border-destructive/25 bg-destructive/8 px-3 py-2 text-sm text-destructive">
                {error}
              </p>
            )}

            <Button type="submit" disabled={isLoading || !username.trim() || !password} className="h-12 w-full justify-between px-4">
              {isLoading ? 'Signing in…' : 'Sign in'}
              {!isLoading && <ArrowRight aria-hidden="true" className="h-4 w-4" />}
            </Button>
          </form>
        </section>
      </div>
    </main>
  );
};
