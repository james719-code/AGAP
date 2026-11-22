import { createContext, useContext, useLayoutEffect, useState, type ReactNode } from 'react';

type Theme = 'dark' | 'light';
const Context = createContext<{ theme: Theme; toggleTheme: () => void; setTheme: (theme: Theme) => void } | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>('dark');
  useLayoutEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark');
    document.documentElement.classList.toggle('light', theme === 'light');
    return () => document.documentElement.classList.remove('dark', 'light');
  }, [theme]);
  return <Context.Provider value={{ theme, setTheme, toggleTheme: () => setTheme((old) => old === 'dark' ? 'light' : 'dark') }}>{children}</Context.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useTheme() {
  const context = useContext(Context);
  if (!context) throw new Error('Preview theme provider is missing.');
  return context;
}
