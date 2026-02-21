import { useState, useMemo, useCallback } from 'react';
import {
  Card,
  Table,
  Select,
  DatePicker,
  Space,
  Tag,
  Button,
  Form,
  Tooltip,
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
import { searchJobs } from '../../api/jobs';
import { fetchPermittedGroups } from '../../api/groups';
import { usePagination } from '../../hooks/usePagination';
import { useIsMobile } from '../../hooks/useIsMobile';
import dayjs from 'dayjs';
import { formatDate, formatDateRange } from '../../utils/date';
import { getLogStatus } from '../../utils/constants';
import type { XxlJobLog } from '../../types/log';
import type { XxlJobGroup } from '../../types/group';
import type { XxlJobInfo } from '../../types/job';
import LogDrawer from './components/LogDrawer';
import ClearLogsModal from './components/ClearLogsModal';
import { message, Popconfirm } from 'antd';
import debounce from '../_utils/debounce';

export default function LogsPage() {
  const queryClient = useQueryClient();
  const isMobile = useIsMobile();
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
  const [jobSearchQuery, setJobSearchQuery] = useState('');
  const [jobOptions, setJobOptions] = useState<{ value: number; label: string }[]>([]);

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
        jobGroup: filters.jobGroup || undefined,
        jobId: filters.jobId || undefined,
        logStatus: filters.logStatus !== -1 ? filters.logStatus : undefined,
        filterTime: filters.filterTime || undefined,
      }),
  });

  const killMutation = useMutation({
    mutationFn: killJob,
    onSuccess: () => {
      message.success('Kill signal sent');
      queryClient.invalidateQueries({ queryKey: ['logs'] });
    },
    onError: (e) => message.error(e.message),
  });

  const handleJobSearch = useMemo(
    () =>
      debounce(async (query: string) => {
        if (!query) {
          setJobOptions([]);
          return;
        }
        try {
          const jobs = await searchJobs(query, filters.jobGroup || undefined);
          setJobOptions(
            jobs.map((j: XxlJobInfo) => ({
              value: j.id,
              label: `#${j.id} ${j.jobDesc}`,
            })),
          );
        } catch {
          setJobOptions([]);
        }
      }, 400),
    [filters.jobGroup],
  );

  const handleGroupChange = useCallback((v: number | undefined) => {
    setFilters((p) => ({ ...p, jobGroup: v ?? 0, jobId: 0 }));
    setJobOptions([]);
    setJobSearchQuery('');
  }, []);

  const handleJobChange = useCallback((v: number | undefined) => {
    setFilters((p) => ({ ...p, jobId: v ?? 0 }));
    if (!v) {
      setJobSearchQuery('');
    }
  }, []);

  const groupMap = new Map(groups.map((g: XxlJobGroup) => [g.id, g.title]));

  const columns: ColumnsType<XxlJobLog> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    ...(isMobile ? [] : [{
      title: 'Group',
      dataIndex: 'jobGroup',
      width: 110,
      render: (v: number) => groupMap.get(v) ?? v,
    } as const]),
    { title: 'JID', dataIndex: 'jobId', width: 70 },
    ...(isMobile ? [] : [
      { title: 'Handler', dataIndex: 'executorHandler', width: 130, ellipsis: { showTitle: false }, render: (v: string) => <Tooltip title={v}><span>{v}</span></Tooltip> } as const,
    ]),
    ...(isMobile ? [] : [{
      title: 'Schedule Time',
      dataIndex: 'scheduleTime',
      width: 160,
      render: (v: string | null) => formatDate(v),
    } as const]),
    {
      title: 'Trigger Time',
      dataIndex: 'triggerTime',
      width: 160,
      render: (v: string) => formatDate(v),
    },
    ...(isMobile ? [] : [{
      title: 'Execution',
      width: 200,
      render: (_: unknown, r: XxlJobLog) => {
        if (!r.handleTime) return '-';
        const handleMs = dayjs(r.handleTime).valueOf();
        const triggerMs = dayjs(r.triggerTime).valueOf();
        const diffMs = handleMs - triggerMs;
        let duration: string;
        if (diffMs < 1000) duration = `${diffMs}ms`;
        else if (diffMs < 60000) duration = `${(diffMs / 1000).toFixed(1)}s`;
        else duration = `${(diffMs / 60000).toFixed(1)}m`;
        return `${formatDate(r.handleTime)} (${duration})`;
      },
    } as const]),
    {
      title: 'Status',
      width: 110,
      render: (_: unknown, r: XxlJobLog) => {
        const t = getLogStatus(r.triggerCode, r.handleCode);
        return <Tag color={t.color}>{t.text}</Tag>;
      },
    },
    {
      title: 'Detail',
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
        <Form layout={isMobile ? 'vertical' : 'inline'} style={{ marginBottom: 16, flexWrap: 'wrap', gap: 8 }}>
          <Form.Item>
            <Select
              style={{ width: isMobile ? '100%' : 160 }}
              placeholder="Executor Group"
              allowClear
              value={filters.jobGroup || undefined}
              onChange={handleGroupChange}
              options={groups.map((g: XxlJobGroup) => ({
                value: g.id,
                label: g.title,
              }))}
            />
          </Form.Item>
          <Form.Item>
            <Select
              style={{ width: isMobile ? '100%' : 200 }}
              placeholder="Search Job ID / Desc"
              showSearch
              allowClear
              filterOption={false}
              value={filters.jobId || undefined}
              searchValue={jobSearchQuery}
              onSearch={(v) => {
                setJobSearchQuery(v);
                handleJobSearch(v);
              }}
              onChange={handleJobChange}
              options={jobOptions}
              notFoundContent={jobSearchQuery ? 'No matches' : null}
            />
          </Form.Item>
          <Form.Item>
            <Select
              style={{ width: isMobile ? '100%' : 120 }}
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
              disabled={!filters.jobGroup || !filters.jobId}
            >
              Clear Logs
            </Button>
          </Form.Item>
        </Form>

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
