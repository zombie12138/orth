export interface CronPreset {
    labelKey: string; // i18n key under job:form.cron.presets.*
    expression: string;
    category: string; // i18n key under job:form.cron.categories.*
}

export const CRON_PRESETS: CronPreset[] = [
    // Frequent (seconds)
    { labelKey: 'every5s', expression: '*/5 * * * * ?', category: 'frequent' },
    { labelKey: 'every10s', expression: '*/10 * * * * ?', category: 'frequent' },
    { labelKey: 'every30s', expression: '*/30 * * * * ?', category: 'frequent' },

    // Minutes
    { labelKey: 'every1m', expression: '0 * * * * ?', category: 'minutes' },
    { labelKey: 'every5m', expression: '0 */5 * * * ?', category: 'minutes' },
    { labelKey: 'every15m', expression: '0 */15 * * * ?', category: 'minutes' },
    { labelKey: 'every30m', expression: '0 */30 * * * ?', category: 'minutes' },

    // Hourly
    { labelKey: 'every1h', expression: '0 0 * * * ?', category: 'hourly' },
    { labelKey: 'every2h', expression: '0 0 */2 * * ?', category: 'hourly' },
    { labelKey: 'every6h', expression: '0 0 */6 * * ?', category: 'hourly' },

    // Daily
    { labelKey: 'midnight', expression: '0 0 0 * * ?', category: 'daily' },
    { labelKey: 'sixAM', expression: '0 0 6 * * ?', category: 'daily' },
    { labelKey: 'noon', expression: '0 0 12 * * ?', category: 'daily' },

    // Weekly
    { labelKey: 'weekdays', expression: '0 0 0 ? * MON-FRI', category: 'weekly' },
    { labelKey: 'everyMonday', expression: '0 0 0 ? * MON', category: 'weekly' },

    // Monthly
    { labelKey: 'firstDay', expression: '0 0 0 1 * ?', category: 'monthly' },
    { labelKey: 'lastDay', expression: '0 0 0 L * ?', category: 'monthly' },
];

/** Group presets by category for Select OptGroup rendering. */
export function groupPresets(): Map<string, CronPreset[]> {
    const map = new Map<string, CronPreset[]>();
    for (const p of CRON_PRESETS) {
        const list = map.get(p.category) ?? [];
        list.push(p);
        map.set(p.category, list);
    }
    return map;
}
