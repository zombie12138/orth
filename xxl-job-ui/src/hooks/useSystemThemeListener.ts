import { useEffect } from 'react';
import { useThemeStore } from '../store/themeStore';

export function useSystemThemeListener() {
  const mode = useThemeStore((s) => s.mode);
  const setResolved = useThemeStore((s) => s.setResolved);

  useEffect(() => {
    if (mode !== 'system') return;

    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = (e: MediaQueryListEvent) => {
      setResolved(e.matches ? 'dark' : 'light');
    };

    mql.addEventListener('change', handler);
    return () => mql.removeEventListener('change', handler);
  }, [mode, setResolved]);
}
