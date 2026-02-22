import { create } from 'zustand';
import type { EnumConfig } from '../types/config';
import type { MenuItem } from '../types/menu';
import { fetchEnums, fetchMenus } from '../api/config';

interface ConfigState {
    enums: EnumConfig;
    menus: MenuItem[];
    loaded: boolean;
    loadConfig: () => Promise<void>;
}

export const useConfigStore = create<ConfigState>((set) => ({
    enums: {},
    menus: [],
    loaded: false,
    loadConfig: async () => {
        const [enums, menus] = await Promise.all([fetchEnums(), fetchMenus()]);
        set({ enums, menus, loaded: true });
    },
}));
