import client, { unwrap } from './client';
import type { DashboardStats, ChartData } from '../types/dashboard';

export function fetchDashboard() {
  return unwrap<DashboardStats>(client.get('/api/v1/dashboard'));
}

export function fetchChart(startDate: string, endDate: string) {
  return unwrap<ChartData>(
    client.get('/api/v1/dashboard/chart', {
      params: { startDate, endDate },
    }),
  );
}
