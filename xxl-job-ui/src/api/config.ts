import client, { unwrap } from './client';
import type { EnumConfig, I18nConfig } from '../types/config';
import type { MenuItem } from '../types/menu';

export function fetchEnums() {
  return unwrap<EnumConfig>(client.get('/api/v1/config/enums'));
}

export function fetchI18n() {
  return unwrap<I18nConfig>(client.get('/api/v1/config/i18n'));
}

export function fetchMenus() {
  return unwrap<MenuItem[]>(client.get('/api/v1/menus'));
}
