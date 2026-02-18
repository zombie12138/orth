import { create } from 'zustand';
import type { EnumConfig, I18nConfig } from '../types/config';
import type { MenuItem } from '../types/menu';
import { fetchEnums, fetchI18n, fetchMenus } from '../api/config';

interface ConfigState {
  enums: EnumConfig;
  i18n: I18nConfig;
  menus: MenuItem[];
  loaded: boolean;
  loadConfig: () => Promise<void>;
}

export const useConfigStore = create<ConfigState>((set) => ({
  enums: {},
  i18n: {},
  menus: [],
  loaded: false,
  loadConfig: async () => {
    const [enums, i18n, menus] = await Promise.all([
      fetchEnums(),
      fetchI18n(),
      fetchMenus(),
    ]);
    set({ enums, i18n, menus, loaded: true });
  },
}));
