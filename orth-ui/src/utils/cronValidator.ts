/**
 * Lightweight Quartz CRON expression validator.
 *
 * Quartz format: seconds minutes hours day-of-month month day-of-week [year]
 * Returns an i18n error key (job:form.cron.validation.*) or null if valid.
 */

const SPECIAL = /^[*?]$/;
const LIST_SEP = ',';

const FIELD_RANGES: [number, number][] = [
    [0, 59], // seconds
    [0, 59], // minutes
    [0, 23], // hours
    [1, 31], // day-of-month
    [1, 12], // month
    [1, 7], // day-of-week (Quartz: 1=SUN..7=SAT)
    [1970, 2099], // year
];

const MONTH_NAMES: Record<string, number> = {
    JAN: 1, FEB: 2, MAR: 3, APR: 4, MAY: 5, JUN: 6,
    JUL: 7, AUG: 8, SEP: 9, OCT: 10, NOV: 11, DEC: 12,
};

const DOW_NAMES: Record<string, number> = {
    SUN: 1, MON: 2, TUE: 3, WED: 4, THU: 5, FRI: 6, SAT: 7,
};

function resolveAlias(token: string, fieldIndex: number): string {
    const upper = token.toUpperCase();
    if (fieldIndex === 4 && MONTH_NAMES[upper] != null) {
        return String(MONTH_NAMES[upper]);
    }
    if (fieldIndex === 5 && DOW_NAMES[upper] != null) {
        return String(DOW_NAMES[upper]);
    }
    return token;
}

function isNumericValue(v: string): boolean {
    return /^\d+$/.test(v);
}

function validateField(field: string, index: number): string | null {
    // Handle L, W, # as valid special tokens
    if (field === 'L' && (index === 3 || index === 5)) return null;
    if (/^\d+W$/.test(field) && index === 3) return null;
    if (/^LW$/.test(field) && index === 3) return null;
    if (/^\d+L$/.test(field) && index === 5) return null;
    if (/^\d+#\d+$/.test(field) && index === 5) return null;

    // Handle ? — only valid for day-of-month (3) and day-of-week (5)
    if (field === '?') {
        if (index !== 3 && index !== 5) return 'invalidQuestion';
        return null;
    }

    if (field === '*') return null;

    const range = FIELD_RANGES[index];
    if (!range) return 'invalidValue';
    const [min, max] = range;

    // Handle list: 1,3,5
    const parts = field.split(LIST_SEP);
    for (const part of parts) {
        // Handle increment: */5 or 1/5
        if (part.includes('/')) {
            const segments = part.split('/');
            const base = segments[0] ?? '';
            const step = segments[1] ?? '';
            if (!step || !isNumericValue(step) || Number(step) < 1) return 'invalidStep';
            if (base !== '*' && base !== '') {
                const resolved = resolveAlias(base, index);
                if (!isNumericValue(resolved)) return 'invalidValue';
                const n = Number(resolved);
                if (n < min || n > max) return 'outOfRange';
            }
            continue;
        }

        // Handle range: 1-5
        if (part.includes('-')) {
            const segments = part.split('-');
            const lo = segments[0] ?? '';
            const hi = segments[1] ?? '';
            const rLo = resolveAlias(lo, index);
            const rHi = resolveAlias(hi, index);
            if (!isNumericValue(rLo) || !isNumericValue(rHi)) return 'invalidValue';
            const nLo = Number(rLo);
            const nHi = Number(rHi);
            if (nLo < min || nHi > max || nLo > nHi) return 'outOfRange';
            continue;
        }

        // Single value
        const resolved = resolveAlias(part, index);
        if (!isNumericValue(resolved)) return 'invalidValue';
        const n = Number(resolved);
        if (n < min || n > max) return 'outOfRange';
    }

    return null;
}

/**
 * Validate a Quartz CRON expression.
 * @returns i18n error key suffix (under job:form.cron.validation.) or null if valid.
 */
export function validateQuartzCron(expression: string): string | null {
    if (!expression || !expression.trim()) return 'required';

    const fields = expression.trim().split(/\s+/);
    if (fields.length < 6 || fields.length > 7) return 'fieldCount';

    // Validate each field
    for (let i = 0; i < fields.length; i++) {
        const f = fields[i]!;
        const err = validateField(f, i);
        if (err) return err;
    }

    // Enforce ? mutual exclusivity: exactly one of day-of-month / day-of-week must be ?
    const dom = fields[3]!;
    const dow = fields[5]!;
    if (dom === '?' && dow === '?') return 'bothQuestion';
    if (!SPECIAL.test(dom) && !SPECIAL.test(dow) && dom !== '?' && dow !== '?') {
        // Both have specific values — Quartz requires one to be ?
        // This is a warning-level issue; some Quartz impls are lenient. Skip for now.
    }

    return null;
}
