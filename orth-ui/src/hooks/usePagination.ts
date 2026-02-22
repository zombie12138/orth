import { useCallback } from 'react';
import { useSearchParams } from 'react-router';

export function usePagination(defaultPageSize = 10) {
  const [searchParams, setSearchParams] = useSearchParams();

  const current = Number(searchParams.get('page')) || 1;
  const pageSize = Number(searchParams.get('size')) || defaultPageSize;
  const offset = (current - 1) * pageSize;

  const onChange = useCallback(
    (page: number, size: number) => {
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        next.set('page', String(page));
        next.set('size', String(size));
        return next;
      });
    },
    [setSearchParams],
  );

  const reset = useCallback(() => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.delete('page');
      next.delete('size');
      return next;
    });
  }, [setSearchParams]);

  return { current, pageSize, offset, onChange, reset };
}
