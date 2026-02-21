import client, { unwrap } from './client';
import type { EnumConfig } from '../types/config';
import type { MenuItem } from '../types/menu';

export function fetchEnums() {
    return unwrap<EnumConfig>(client.get('/api/v1/config/enums'));
}

export function fetchMenus() {
    return unwrap<MenuItem[]>(client.get('/api/v1/menus'));
}
