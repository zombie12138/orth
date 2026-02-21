import type { TFunction } from 'i18next';

export function getTriggerStatus(t: TFunction) {
    return {
        0: { text: t('common:stopped'), color: 'default' },
        1: { text: t('common:running'), color: 'green' },
    } as const;
}

export function getResultTag(code: number, t: TFunction) {
    if (code === 200) return { text: t('common:success'), color: 'green' };
    if (code === 0) return { text: t('common:pending'), color: 'default' };
    return { text: `${t('common:failed')} (${code})`, color: 'red' };
}

export function getLogStatus(triggerCode: number, handleCode: number, t: TFunction) {
    if (triggerCode === 0) return { text: t('common:init'), color: 'default' };
    if (triggerCode === 200 && handleCode === 0)
        return { text: t('common:pending'), color: 'processing' };
    if (handleCode === 200) return { text: t('common:success'), color: 'green' };
    if (handleCode === 502) return { text: t('common:timeout'), color: 'orange' };
    if (triggerCode !== 200) return { text: t('common:triggerFailed'), color: 'red' };
    return { text: t('common:failed'), color: 'red' };
}

export function getClearLogTypes(t: TFunction) {
    return [
        { value: 1, label: t('log:clearTypes.oneMonth') },
        { value: 2, label: t('log:clearTypes.threeMonths') },
        { value: 3, label: t('log:clearTypes.sixMonths') },
        { value: 4, label: t('log:clearTypes.oneYear') },
        { value: 5, label: t('log:clearTypes.tenThousand') },
        { value: 6, label: t('log:clearTypes.hundredThousand') },
        { value: 7, label: t('log:clearTypes.all') },
        { value: 8, label: t('log:clearTypes.oneWeek') },
        { value: 9, label: t('log:clearTypes.oneDay') },
    ];
}

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
