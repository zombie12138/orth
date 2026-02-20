import { Card, Col, Row, Statistic, Spin } from 'antd';
import {
  ScheduleOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  ClusterOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import ReactECharts from 'echarts-for-react';
import dayjs from 'dayjs';
import { fetchDashboard, fetchChart } from '../../api/dashboard';
import { useThemeStore } from '../../store/themeStore';
import { useIsMobile } from '../../hooks/useIsMobile';
import { CHART_COLORS, STAT_COLORS } from '../../theme/themeConfig';

export default function DashboardPage() {
  const isMobile = useIsMobile();
  const resolved = useThemeStore((s) => s.resolved);
  const colors = CHART_COLORS[resolved];

  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: fetchDashboard,
  });

  const startDate = dayjs().subtract(6, 'day').format('YYYY-MM-DD');
  const endDate = dayjs().format('YYYY-MM-DD');

  const { data: chart, isLoading: chartLoading } = useQuery({
    queryKey: ['dashboard-chart', startDate, endDate],
    queryFn: () => fetchChart(startDate, endDate),
  });

  const chartOption = chart
    ? {
        tooltip: {
          trigger: 'axis' as const,
          backgroundColor: colors.tooltipBg,
          borderColor: colors.tooltipBorder,
          textStyle: { color: colors.tooltipText },
        },
        legend: {
          data: ['Running', 'Success', 'Failed'],
          textStyle: { color: colors.legend },
        },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: {
          type: 'category' as const,
          boundaryGap: false,
          data: chart.triggerDayList,
          axisLabel: { color: colors.axisLabel },
          axisLine: { lineStyle: { color: colors.axisLine } },
        },
        yAxis: {
          type: 'value' as const,
          axisLabel: { color: colors.axisLabel },
          axisLine: { lineStyle: { color: colors.axisLine } },
          splitLine: { lineStyle: { color: colors.splitLine } },
        },
        series: [
          {
            name: 'Running',
            type: 'line' as const,
            data: chart.triggerDayCountRunningList,
            smooth: true,
            itemStyle: { color: colors.running },
          },
          {
            name: 'Success',
            type: 'line' as const,
            data: chart.triggerDayCountSucList,
            smooth: true,
            itemStyle: { color: colors.success },
          },
          {
            name: 'Failed',
            type: 'line' as const,
            data: chart.triggerDayCountFailList,
            smooth: true,
            itemStyle: { color: colors.failed },
          },
        ],
      }
    : {};

  return (
    <div>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Jobs Count"
              value={stats?.jobInfoCount ?? 0}
              loading={statsLoading}
              prefix={<ScheduleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Total Executions"
              value={stats?.jobLogCount ?? 0}
              loading={statsLoading}
              prefix={<PlayCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Successful"
              value={stats?.jobLogSuccessCount ?? 0}
              loading={statsLoading}
              prefix={<CheckCircleOutlined />}
              valueStyle={{ color: STAT_COLORS.success }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Online Executors"
              value={stats?.executorCount ?? 0}
              loading={statsLoading}
              prefix={<ClusterOutlined />}
              valueStyle={{ color: STAT_COLORS.primary }}
            />
          </Card>
        </Col>
      </Row>
      <Card title="Execution Trend (Last 7 Days)" style={{ marginTop: 16 }}>
        {chartLoading ? (
          <div style={{ textAlign: 'center', padding: 60 }}>
            <Spin />
          </div>
        ) : (
          <ReactECharts option={chartOption} style={{ height: isMobile ? 250 : 350 }} />
        )}
      </Card>
    </div>
  );
}
