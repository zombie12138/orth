import dayjs from 'dayjs';

export const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss';

export function formatDate(value: string | number | null | undefined): string {
  if (!value) return '-';
  return dayjs(value).format(DATE_FORMAT);
}

export function formatDateRange(
  start: dayjs.Dayjs,
  end: dayjs.Dayjs,
): string {
  return `${start.format(DATE_FORMAT)} - ${end.format(DATE_FORMAT)}`;
}
