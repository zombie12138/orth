import { useState } from 'react';
import {
  Card,
  Table,
  Select,
  InputNumber,
  DatePicker,
  Space,
  Tag,
  Button,
  Form,
} from 'antd';
import {
  EyeOutlined,
  StopOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';
import type { ColumnsType } from 'antd/es/table';
import { fetchLogs, killJob } from '../../api/logs';
import { fetchPermittedGroups } from '../../api/groups';
import { usePagination } from '../../hooks/usePagination';
import { formatDate, formatDateRange } from '../../utils/date';
import { getResultTag } from '../../utils/constants';
import type { XxlJobLog } from '../../types/log';
import type { XxlJobGroup } from '../../types/group';
import LogDrawer from './components/LogDrawer';
import ClearLogsModal from './components/ClearLogsModal';
import { message, Popconfirm } from 'antd';

export default function LogsPage() {
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const { current, pageSize, offset, onChange } = usePagination();

  const [filters, setFilters] = useState({
    jobGroup: Number(searchParams.get('jobGroup')) || 0,
    jobId: Number(searchParams.get('jobId')) || 0,
    logStatus: -1,
    filterTime: '',
  });

  const [drawerLogId, setDrawerLogId] = useState<number | null>(null);
  const [clearOpen, setClearOpen] = useState(false);

  const { data: groups = [] } = useQuery({
    queryKey: ['permitted-groups'],
    queryFn: fetchPermittedGroups,
  });

  const { data, isLoading } = useQuery({
    queryKey: ['logs', offset, pageSize, filters],
    queryFn: () =>
      fetchLogs({
        offset,
        pagesize: pageSize,
        jobGroup: filters.jobGroup,
        jobId: filters.jobId || undefined,
        logStatus: filters.logStatus !== -1 ? filters.logStatus : undefined,
        filterTime: filters.filterTime || undefined,
      }),
    enabled: filters.jobGroup > 0,
  });

  const killMutation = useMutation({
    mutationFn: killJob,
    onSuccess: () => {
      message.success('Kill signal sent');
      queryClient.invalidateQueries({ queryKey: ['logs'] });
    },
    onError: (e) => message.error(e.message),
  });

  const groupMap = new Map(groups.map((g: XxlJobGroup) => [g.id, g.title]));

  const columns: ColumnsType<XxlJobLog> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    {
      title: 'Group',
      dataIndex: 'jobGroup',
      width: 110,
      render: (v: number) => groupMap.get(v) ?? v,
    },
    { title: 'Job ID', dataIndex: 'jobId', width: 70 },
    { title: 'Handler', dataIndex: 'executorHandler', width: 130, ellipsis: true },
    {
      title: 'Schedule Time',
      dataIndex: 'scheduleTime',
      width: 160,
      render: (v: string | null) => formatDate(v),
    },
    {
      title: 'Trigger Time',
      dataIndex: 'triggerTime',
      width: 160,
      render: (v: string) => formatDate(v),
    },
    {
      title: 'Trigger',
      dataIndex: 'triggerCode',
      width: 90,
      render: (v: number) => {
        const t = getResultTag(v);
        return <Tag color={t.color}>{t.text}</Tag>;
      },
    },
    {
      title: 'Handle',
      dataIndex: 'handleCode',
      width: 90,
      render: (v: number) => {
        const t = getResultTag(v);
        return <Tag color={t.color}>{t.text}</Tag>;
      },
    },
    {
      title: 'Actions',
      width: 100,
      fixed: 'right',
      render: (_: unknown, record: XxlJobLog) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => setDrawerLogId(record.id)}
          />
          {record.triggerCode === 200 && record.handleCode === 0 && (
            <Popconfirm
              title="Kill this job?"
              onConfirm={() => killMutation.mutate(record.id)}
            >
              <Button type="link" size="small" danger icon={<StopOutlined />} />
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Card>
        <Form layout="inline" style={{ marginBottom: 16, flexWrap: 'wrap', gap: 8 }}>
          <Form.Item>
            <Select
              style={{ width: 160 }}
              placeholder="Executor Group"
              value={filters.jobGroup || undefined}
              onChange={(v) => setFilters((p) => ({ ...p, jobGroup: v ?? 0 }))}
              options={groups.map((g: XxlJobGroup) => ({
                value: g.id,
                label: g.title,
              }))}
            />
          </Form.Item>
          <Form.Item>
            <InputNumber
              style={{ width: 100 }}
              placeholder="Job ID"
              value={filters.jobId || undefined}
              onChange={(v) => setFilters((p) => ({ ...p, jobId: v ?? 0 }))}
            />
          </Form.Item>
          <Form.Item>
            <Select
              style={{ width: 120 }}
              placeholder="Status"
              allowClear
              onChange={(v) =>
                setFilters((p) => ({ ...p, logStatus: v ?? -1 }))
              }
              options={[
                { value: 1, label: 'Success' },
                { value: 2, label: 'Failed' },
                { value: 3, label: 'Running' },
              ]}
            />
          </Form.Item>
          <Form.Item>
            <DatePicker.RangePicker
              showTime
              onChange={(dates) => {
                if (dates && dates[0] && dates[1]) {
                  setFilters((p) => ({
                    ...p,
                    filterTime: formatDateRange(dates[0]!, dates[1]!),
                  }));
                } else {
                  setFilters((p) => ({ ...p, filterTime: '' }));
                }
              }}
            />
          </Form.Item>
          <Form.Item>
            <Button
              icon={<DeleteOutlined />}
              onClick={() => setClearOpen(true)}
              disabled={!filters.jobGroup}
            >
              Clear Logs
            </Button>
          </Form.Item>
        </Form>

        {!filters.jobGroup ? (
          <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
            Select an executor group to view logs
          </div>
        ) : (
          <Table<XxlJobLog>
            rowKey="id"
            columns={columns}
            dataSource={data?.data}
            loading={isLoading}
            scroll={{ x: 1000 }}
            pagination={{
              current,
              pageSize,
              total: data?.total ?? 0,
              showSizeChanger: true,
              showTotal: (total) => `Total ${total}`,
              onChange,
            }}
          />
        )}
      </Card>

      <LogDrawer
        logId={drawerLogId}
        onClose={() => setDrawerLogId(null)}
      />
      <ClearLogsModal
        open={clearOpen}
        jobGroup={filters.jobGroup}
        jobId={filters.jobId}
        onClose={() => setClearOpen(false)}
        onSuccess={() => queryClient.invalidateQueries({ queryKey: ['logs'] })}
      />
    </>
  );
}
