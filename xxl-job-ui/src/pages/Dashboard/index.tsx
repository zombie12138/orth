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

export default function DashboardPage() {
  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: fetchDashboard,
  });

  const startDate = dayjs().subtract(7, 'day').format('YYYY-MM-DD');
  const endDate = dayjs().subtract(1, 'day').format('YYYY-MM-DD');

  const { data: chart, isLoading: chartLoading } = useQuery({
    queryKey: ['dashboard-chart', startDate, endDate],
    queryFn: () => fetchChart(startDate, endDate),
  });

  const chartOption = chart
    ? {
        tooltip: { trigger: 'axis' as const },
        legend: { data: ['Running', 'Success', 'Failed'] },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: {
          type: 'category' as const,
          boundaryGap: false,
          data: chart.triggerDayList,
        },
        yAxis: { type: 'value' as const },
        series: [
          {
            name: 'Running',
            type: 'line' as const,
            data: chart.triggerDayCountRunningList,
            smooth: true,
            itemStyle: { color: '#1890ff' },
          },
          {
            name: 'Success',
            type: 'line' as const,
            data: chart.triggerDayCountSucList,
            smooth: true,
            itemStyle: { color: '#52c41a' },
          },
          {
            name: 'Failed',
            type: 'line' as const,
            data: chart.triggerDayCountFailList,
            smooth: true,
            itemStyle: { color: '#ff4d4f' },
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
              valueStyle={{ color: '#52c41a' }}
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
              valueStyle={{ color: '#1890ff' }}
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
          <ReactECharts option={chartOption} style={{ height: 350 }} />
        )}
      </Card>
    </div>
  );
}
