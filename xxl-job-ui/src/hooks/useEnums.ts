import { useMemo } from 'react';
import { useConfigStore } from '../store/configStore';

export function useEnumOptions(enumKey: string) {
  const enums = useConfigStore((s) => s.enums);

  return useMemo(() => {
    const map = enums[enumKey];
    if (!map) return [];
    return Object.entries(map).map(([value, label]) => ({ value, label }));
  }, [enums, enumKey]);
}

export function useEnumLabel(enumKey: string, value: string | undefined) {
  const enums = useConfigStore((s) => s.enums);
  if (!value) return '';
  return enums[enumKey]?.[value] ?? value;
}
