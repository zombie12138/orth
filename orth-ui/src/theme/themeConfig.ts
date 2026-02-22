import type { ThemeConfig } from 'antd';
import { theme as antTheme } from 'antd';

/* ── Dark algorithm wrapper ────────────────────────────────────────── */
// antd's darkAlgorithm forcibly desaturates colorPrimary (#FBBF24 → #d8a522).
// This wrapper restores the original seed value after the algorithm runs.
export function fixedDarkAlgorithm(
  ...args: Parameters<typeof antTheme.darkAlgorithm>
) {
  const result = antTheme.darkAlgorithm(...args);
  return { ...result, colorPrimary: args[0].colorPrimary };
}

/* ── Shared accent colors ──────────────────────────────────────────── */

const LIGHT_ACCENTS = {
  colorPrimary: '#F59E0B',
  colorSuccess: '#22C55E',
  colorError: '#DC2626',
  colorWarning: '#F97316',
  colorInfo: '#14B8A6',
};

const DARK_ACCENTS = {
  colorPrimary: '#FBBF24',
  colorSuccess: '#22C55E',
  colorError: '#DC2626',
  colorWarning: '#F97316',
  colorInfo: '#14B8A6',
};

/* ── Ant Design ThemeConfig objects ────────────────────────────────── */

export const lightTheme: ThemeConfig = {
  token: {
    ...LIGHT_ACCENTS,
    colorBgBase: '#FFFFFF',
    colorBgLayout: '#F5F5F4',
    colorBgContainer: '#FFFFFF',
    colorBorder: '#E7E5E4',
    colorBorderSecondary: '#D6D3D1',
    colorText: '#1C1917',
    colorTextSecondary: '#78716C',
    colorLink: '#D97706',
  },
  components: {
    Layout: {
      siderBg: '#FFFFFF',
      headerBg: '#FFFFFF',
      triggerBg: '#F5F5F4',
      triggerColor: '#78716C',
    },
    Menu: {
      itemBg: '#FFFFFF',
      itemSelectedBg: '#FEF3C7',
      itemSelectedColor: '#B45309',
      itemHoverBg: '#FFFBEB',
    },
  },
};

export const darkTheme: ThemeConfig = {
  token: {
    ...DARK_ACCENTS,
    colorPrimaryBg: '#302010',
    colorBgBase: '#1C1917',
    colorBgLayout: '#1C1917',
    colorBgContainer: '#292524',
    colorBgElevated: '#44403C',
    colorBorder: '#44403C',
    colorBorderSecondary: '#44403C',
    colorText: '#FAFAF9',
    colorTextSecondary: '#A8A29E',
    colorLink: '#FBBF24',
  },
  components: {
    Layout: {
      siderBg: '#1C1917',
      headerBg: '#292524',
      triggerBg: '#292524',
      triggerColor: '#A8A29E',
    },
    Menu: {
      itemBg: '#1C1917',
      itemSelectedBg: '#451A03',
      itemSelectedColor: '#FBBF24',
      itemHoverBg: '#33302B',
    },
  },
};

/* ── ECharts colors per resolved theme ─────────────────────────────── */

export const CHART_COLORS = {
  light: {
    running: '#F59E0B',
    success: '#22C55E',
    failed: '#DC2626',
    axisLabel: '#78716C',
    axisLine: '#E7E5E4',
    splitLine: '#E7E5E4',
    tooltipBg: '#FFFFFF',
    tooltipBorder: '#E7E5E4',
    tooltipText: '#1C1917',
    legend: '#1C1917',
  },
  dark: {
    running: '#FBBF24',
    success: '#22C55E',
    failed: '#EF4444',
    axisLabel: '#A8A29E',
    axisLine: '#44403C',
    splitLine: '#44403C',
    tooltipBg: '#292524',
    tooltipBorder: '#44403C',
    tooltipText: '#FAFAF9',
    legend: '#FAFAF9',
  },
} as const;

/* ── Dashboard Statistic colors ────────────────────────────────────── */

export const STAT_COLORS = {
  success: '#22C55E',
  primary: '#F59E0B',
} as const;
