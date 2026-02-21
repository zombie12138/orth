export const TRIGGER_STATUS = {
  0: { text: 'Stopped', color: 'default' },
  1: { text: 'Running', color: 'green' },
} as const;

export const RESULT_CODE = {
  200: { text: 'Success', color: 'green' },
  0: { text: 'Pending', color: 'default' },
} as const;

export function getResultTag(code: number) {
  if (code === 200) return { text: 'Success', color: 'green' };
  if (code === 0) return { text: 'Pending', color: 'default' };
  return { text: `Failed (${code})`, color: 'red' };
}

export function getLogStatus(triggerCode: number, handleCode: number) {
  if (triggerCode === 0) return { text: 'Init', color: 'default' };
  if (triggerCode === 200 && handleCode === 0) return { text: 'Pending', color: 'processing' };
  if (handleCode === 200) return { text: 'Success', color: 'green' };
  if (handleCode === 502) return { text: 'Timeout', color: 'orange' };
  if (triggerCode !== 200) return { text: 'Trigger Failed', color: 'red' };
  return { text: 'Failed', color: 'red' };
}

export const CLEAR_LOG_TYPES = [
  { value: 1, label: 'Clear logs older than 1 month' },
  { value: 2, label: 'Clear logs older than 3 months' },
  { value: 3, label: 'Clear logs older than 6 months' },
  { value: 4, label: 'Clear logs older than 1 year' },
  { value: 5, label: 'Clear logs older than 10000 records' },
  { value: 6, label: 'Clear logs older than 100000 records' },
  { value: 7, label: 'Clear all logs' },
  { value: 8, label: 'Clear logs older than 1 week' },
  { value: 9, label: 'Clear logs older than 1 day' },
];

// Map backend menu URLs to frontend routes
export const MENU_URL_MAP: Record<string, string> = {
  '/jobinfo': '/jobs',
  '/joblog': '/logs',
  '/jobgroup': '/executor-groups',
  '/user': '/users',
};

export function mapMenuUrl(backendUrl: string): string {
  return MENU_URL_MAP[backendUrl] ?? backendUrl;
}
