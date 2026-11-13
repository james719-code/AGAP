import { useEffect } from 'react';

export function useVisibleRefresh(refresh: (silent: boolean) => Promise<void>, intervalMs: number) {
  useEffect(() => {
    if (intervalMs <= 0) return;
    let pending = false;
    const timer = window.setInterval(async () => {
      if (pending || document.visibilityState !== 'visible') return;
      pending = true;
      try {
        await refresh(true);
      } finally {
        pending = false;
      }
    }, intervalMs);
    return () => window.clearInterval(timer);
  }, [refresh, intervalMs]);
}
