import React, { createContext, useContext, useState, useEffect } from 'react';
import { apiService, setUnauthorizedHandler } from '@/services/api';
import type { UserAccount } from '@/types/api';

interface AuthContextType {
  user: UserAccount | null;
  isAuthenticated: boolean;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserAccount | null>(null);
  const [loading, setLoading] = useState(true);

  // Register a single unauthorized handler for the lifetime of the
  // provider so api.ts's request() can clear React state the moment a
  // refresh fails or there's no token to refresh — otherwise a protected
  // route can keep rendering as "logged in" while every call 401s.
  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null));
    return () => setUnauthorizedHandler(null);
  }, []);

  useEffect(() => {
    queueMicrotask(() => {
      const token = localStorage.getItem('agap_auth_token');
      if (!token) {
        setLoading(false);
        return;
      }

      // Don't trust the cached user object on its own — an admin may have
      // suspended/edited this account elsewhere since the last login. Show
      // the cached copy immediately (avoids a blank flash) but always
      // revalidate against the server and correct it if it disagrees.
      const savedUser = localStorage.getItem('agap_user');
      if (savedUser) {
        try {
          setUser(JSON.parse(savedUser));
        } catch {
          localStorage.removeItem('agap_user');
        }
      }

      apiService
        .getCurrentUser()
        .then((freshUser) => {
          setUser(freshUser);
          localStorage.setItem('agap_user', JSON.stringify(freshUser));
        })
        .catch(() => {
          // request() already clears localStorage and fires the
          // unauthorized handler on a real 401; this catch just covers
          // network errors so we don't leave `loading` stuck true.
          setUser(null);
        })
        .finally(() => setLoading(false));
    });
  }, []);

  const login = async (username: string, password: string) => {
    const res = await apiService.login(username, password);
    localStorage.setItem('agap_auth_token', res.token);
    if (res.refresh_token) {
      localStorage.setItem('agap_refresh_token', res.refresh_token);
    }
    localStorage.setItem('agap_user', JSON.stringify(res.user));
    setUser(res.user);
  };

  const logout = async () => {
    try {
      // Revoke the refresh token server-side so it can't be replayed.
      await apiService.logout();
    } catch {
      // Best-effort — even if the network call fails, still log the
      // user out locally rather than trapping them in a broken session.
    } finally {
      localStorage.removeItem('agap_auth_token');
      localStorage.removeItem('agap_refresh_token');
      localStorage.removeItem('agap_user');
      setUser(null);
    }
  };

  const refreshUser = async () => {
    try {
      const freshUser = await apiService.getCurrentUser();
      setUser(freshUser);
      localStorage.setItem('agap_user', JSON.stringify(freshUser));
    } catch {
      // Best-effort
    }
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, loading, login, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};