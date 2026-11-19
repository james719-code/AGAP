import { useEffect } from 'react';

export function scheduleVisibleRefresh(refresh: () => Promise<boolean | void>, intervalMs: number) {
  let stopped = false;
  let timer: number;
  let delay = intervalMs;
  const run = async () => {
    if (document.visibilityState === 'visible') {
      const result = await refresh().catch(() => false);
      delay = result === false ? Math.min(delay * 2, Math.max(intervalMs, 300_000)) : intervalMs;
    }
    if (!stopped) timer = window.setTimeout(run, delay);
  };
  if (intervalMs > 0) timer = window.setTimeout(run, delay);
  return () => {
    stopped = true;
    window.clearTimeout(timer);
  };
}

export function useVisibleRefresh(refresh: (silent: boolean) => Promise<boolean | void>, intervalMs: number) {
  useEffect(() => scheduleVisibleRefresh(() => refresh(true), intervalMs), [refresh, intervalMs]);
}
