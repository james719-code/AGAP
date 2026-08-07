import React, { createContext, useContext, useState, useEffect } from 'react';
import { apiService } from '@/services/api';

export interface User {
  id: string | number;
  name: string;
  email: string;
  role: string;
  station: string;
  username?: string;
}

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(() => {
    const saved = localStorage.getItem('agap_user');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch {
        return null;
      }
    }
    return null;
  });

  useEffect(() => {
    if (user) {
      localStorage.setItem('agap_user', JSON.stringify(user));
    } else {
      localStorage.removeItem('agap_user');
    }
  }, [user]);

  const login = async (username: string, password: string) => {
    const response = await apiService.login(username, password);
    localStorage.setItem('agap_auth_token', response.token);
    if (response.refresh_token) {
      localStorage.setItem('agap_refresh_token', response.refresh_token);
    }

    const account = response.user;
    const newUser: User = {
      id: account.id,
      username: account.username,
      name: account.name || account.username,
      email: account.email || '',
      role: account.role,
      station: 'Legazpi HQ Command Center',
    };
    setUser(newUser);
  };

  const logout = () => {
    localStorage.removeItem('agap_auth_token');
    localStorage.removeItem('agap_refresh_token');
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        login,
        logout,
      }}
    >
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
