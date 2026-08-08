import React, { createContext, useContext, useState, useEffect } from 'react';
import { apiService } from '@/services/api';
import type { UserAccount } from '@/types/api';

interface AuthContextType {
  user: UserAccount | null;
  isAuthenticated: boolean;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserAccount | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('agap_auth_token');
    const savedUser = localStorage.getItem('agap_user');
    if (token && savedUser) {
      try {
        setUser(JSON.parse(savedUser));
      } catch {
        localStorage.removeItem('agap_auth_token');
        localStorage.removeItem('agap_user');
      }
    }
    setLoading(false);
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

  const logout = () => {
    localStorage.removeItem('agap_auth_token');
    localStorage.removeItem('agap_refresh_token');
    localStorage.removeItem('agap_user');
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};