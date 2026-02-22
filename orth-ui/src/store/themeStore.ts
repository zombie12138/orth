import { create } from 'zustand';
import { persist } from 'zustand/middleware';

type ThemeMode = 'system' | 'light' | 'dark';
type Resolved = 'light' | 'dark';

interface ThemeState {
  mode: ThemeMode;
  resolved: Resolved;
  setMode: (mode: ThemeMode) => void;
  setResolved: (resolved: Resolved) => void;
}

function resolveTheme(mode: ThemeMode): Resolved {
  if (mode !== 'system') return mode;
  if (typeof window === 'undefined') return 'light';
  return window.matchMedia('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light';
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      mode: 'system',
      resolved: resolveTheme('system'),
      setMode: (mode) => set({ mode, resolved: resolveTheme(mode) }),
      setResolved: (resolved) => set({ resolved }),
    }),
    {
      name: 'theme-storage',
      partialize: (state) => ({ mode: state.mode }),
      onRehydrateStorage: () => (state) => {
        if (state) {
          state.resolved = resolveTheme(state.mode);
        }
      },
    },
  ),
);
