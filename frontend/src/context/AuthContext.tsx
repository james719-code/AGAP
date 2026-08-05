import React, { createContext, useContext, useState, useEffect } from 'react';

export interface User {
  id: string;
  name: string;
  email: string;
  role: string;
  station: string;
}

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  login: (email: string, role?: string) => void;
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
    // Default logged in user for seamless demo experience
    return {
      id: 'usr-admin-01',
      name: 'AgapUser',
      email: 'admin@agap.gov.ph',
      role: 'ADMIN OPERATOR',
      station: 'Legazpi HQ Command Center',
    };
  });

  useEffect(() => {
    if (user) {
      localStorage.setItem('agap_user', JSON.stringify(user));
    } else {
      localStorage.removeItem('agap_user');
    }
  }, [user]);

  const login = (email: string, role = 'ADMIN OPERATOR') => {
    const name = email.split('@')[0] || 'AgapUser';
    const newUser: User = {
      id: `usr-${Date.now()}`,
      name: name.charAt(0).toUpperCase() + name.slice(1),
      email,
      role,
      station: 'Legazpi HQ Command Center',
    };
    setUser(newUser);
  };

  const logout = () => {
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
