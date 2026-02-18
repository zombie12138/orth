import { useState, useCallback } from 'react';
import {
  Card,
  Table,
  Button,
  Select,
  Input,
  Space,
  Tag,
  Dropdown,
  message,
  Popconfirm,
  Row,
  Form,
} from 'antd';
import {
  PlusOutlined,
  SearchOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  EditOutlined,
  DeleteOutlined,
  ThunderboltOutlined,
  MoreOutlined,
  FieldTimeOutlined,
  CopyOutlined,
  CodeOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import type { ColumnsType } from 'antd/es/table';
import { fetchJobs, deleteJob, startJob, stopJob } from '../../api/jobs';
import { fetchPermittedGroups } from '../../api/groups';
import { usePagination } from '../../hooks/usePagination';
import { TRIGGER_STATUS } from '../../utils/constants';
import type { XxlJobInfo } from '../../types/job';
import type { XxlJobGroup } from '../../types/group';
import JobFormModal from './components/JobFormModal';
import TriggerModal from './components/TriggerModal';
import BatchTriggerModal from './components/BatchTriggerModal';
import BatchCopyModal from './components/BatchCopyModal';
import ImportExportButtons from './components/ImportExportButtons';

export default function JobsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { current, pageSize, offset, onChange } = usePagination();

  const [filters, setFilters] = useState({
    jobGroup: 0,
    triggerStatus: -1,
    jobDesc: '',
    executorHandler: '',
    author: '',
  });

  const [formModalOpen, setFormModalOpen] = useState(false);
  const [editingJob, setEditingJob] = useState<XxlJobInfo | null>(null);
  const [triggerModalOpen, setTriggerModalOpen] = useState(false);
  const [triggerJobId, setTriggerJobId] = useState(0);
  const [batchTriggerOpen, setBatchTriggerOpen] = useState(false);
  const [batchTriggerJobId, setBatchTriggerJobId] = useState(0);
  const [batchCopyOpen, setBatchCopyOpen] = useState(false);
  const [batchCopyJobId, setBatchCopyJobId] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);

  const { data: groups = [] } = useQuery({
    queryKey: ['permitted-groups'],
    queryFn: fetchPermittedGroups,
  });

  const { data, isLoading } = useQuery({
    queryKey: ['jobs', offset, pageSize, filters],
    queryFn: () =>
      fetchJobs({
        offset,
        pagesize: pageSize,
        ...filters,
      }),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteJob,
    onSuccess: () => {
      message.success('Job deleted');
      queryClient.invalidateQueries({ queryKey: ['jobs'] });
    },
    onError: (e) => message.error(e.message),
  });

  const startMutation = useMutation({
    mutationFn: startJob,
    onSuccess: () => {
      message.success('Job started');
      queryClient.invalidateQueries({ queryKey: ['jobs'] });
    },
    onError: (e) => message.error(e.message),
  });

  const stopMutation = useMutation({
    mutationFn: stopJob,
    onSuccess: () => {
      message.success('Job stopped');
      queryClient.invalidateQueries({ queryKey: ['jobs'] });
    },
    onError: (e) => message.error(e.message),
  });

  const handleSearch = useCallback(
    (values: Record<string, string | number | undefined>) => {
      setFilters((prev) => ({
        ...prev,
        ...Object.fromEntries(
          Object.entries(values).map(([k, v]) => [k, v ?? (typeof prev[k as keyof typeof prev] === 'number' ? 0 : '')]),
        ),
      }));
    },
    [],
  );

  const groupMap = new Map(groups.map((g: XxlJobGroup) => [g.id, g.title]));

  const columns: ColumnsType<XxlJobInfo> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    {
      title: 'Group',
      dataIndex: 'jobGroup',
      width: 120,
      render: (v: number) => groupMap.get(v) ?? v,
    },
    { title: 'Description', dataIndex: 'jobDesc', ellipsis: true },
    {
      title: 'Schedule',
      width: 140,
      render: (_: unknown, r: XxlJobInfo) =>
        r.scheduleType === 'NONE' ? '-' : `${r.scheduleType}: ${r.scheduleConf}`,
    },
    { title: 'GLUE Type', dataIndex: 'glueType', width: 110 },
    { title: 'Handler', dataIndex: 'executorHandler', width: 140, ellipsis: true },
    { title: 'Author', dataIndex: 'author', width: 100 },
    {
      title: 'Status',
      dataIndex: 'triggerStatus',
      width: 90,
      render: (v: number) => {
        const s = TRIGGER_STATUS[v as keyof typeof TRIGGER_STATUS];
        return s ? <Tag color={s.color}>{s.text}</Tag> : v;
      },
    },
    {
      title: 'Actions',
      width: 160,
      fixed: 'right',
      render: (_: unknown, record: XxlJobInfo) => {
        const items = [
          {
            key: 'trigger',
            icon: <ThunderboltOutlined />,
            label: 'Trigger',
            onClick: () => {
              setTriggerJobId(record.id);
              setTriggerModalOpen(true);
            },
          },
          {
            key: 'batchTrigger',
            icon: <FieldTimeOutlined />,
            label: 'Batch Trigger',
            onClick: () => {
              setBatchTriggerJobId(record.id);
              setBatchTriggerOpen(true);
            },
          },
          {
            key: 'logs',
            icon: <SearchOutlined />,
            label: 'View Logs',
            onClick: () =>
              navigate(`/logs?jobGroup=${record.jobGroup}&jobId=${record.id}`),
          },
          record.glueType !== 'BEAN'
            ? {
                key: 'code',
                icon: <CodeOutlined />,
                label: 'GLUE Code',
                onClick: () => navigate(`/jobs/${record.id}/code`),
              }
            : null,
          record.superTaskId == null
            ? {
                key: 'batchCopy',
                icon: <CopyOutlined />,
                label: 'Batch Copy',
                onClick: () => {
                  setBatchCopyJobId(record.id);
                  setBatchCopyOpen(true);
                },
              }
            : null,
        ].filter(Boolean);

        return (
          <Space size="small">
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => {
                setEditingJob(record);
                setFormModalOpen(true);
              }}
            />
            {record.triggerStatus === 0 ? (
              <Button
                type="link"
                size="small"
                icon={<PlayCircleOutlined />}
                onClick={() => startMutation.mutate(record.id)}
              />
            ) : (
              <Button
                type="link"
                size="small"
                icon={<PauseCircleOutlined />}
                onClick={() => stopMutation.mutate(record.id)}
              />
            )}
            <Popconfirm
              title="Delete this job?"
              onConfirm={() => deleteMutation.mutate(record.id)}
            >
              <Button type="link" size="small" danger icon={<DeleteOutlined />} />
            </Popconfirm>
            <Dropdown menu={{ items: items as any }} trigger={['click']}>
              <Button type="link" size="small" icon={<MoreOutlined />} />
            </Dropdown>
          </Space>
        );
      },
    },
  ];

  return (
    <>
      <Card>
        <Form layout="inline" onFinish={handleSearch} style={{ marginBottom: 16, flexWrap: 'wrap', gap: 8 }}>
          <Form.Item name="jobGroup">
            <Select
              style={{ width: 160 }}
              placeholder="Executor Group"
              allowClear
              value={filters.jobGroup || undefined}
              onChange={(v) => setFilters((p) => ({ ...p, jobGroup: v ?? 0 }))}
              options={groups.map((g: XxlJobGroup) => ({
                value: g.id,
                label: g.title,
              }))}
            />
          </Form.Item>
          <Form.Item name="triggerStatus">
            <Select
              style={{ width: 120 }}
              placeholder="Status"
              allowClear
              onChange={(v) =>
                setFilters((p) => ({ ...p, triggerStatus: v ?? -1 }))
              }
              options={[
                { value: 0, label: 'Stopped' },
                { value: 1, label: 'Running' },
              ]}
            />
          </Form.Item>
          <Form.Item name="jobDesc">
            <Input
              placeholder="Job Description"
              allowClear
              onChange={(e) =>
                setFilters((p) => ({ ...p, jobDesc: e.target.value }))
              }
            />
          </Form.Item>
          <Form.Item name="executorHandler">
            <Input
              placeholder="Handler"
              allowClear
              onChange={(e) =>
                setFilters((p) => ({ ...p, executorHandler: e.target.value }))
              }
            />
          </Form.Item>
          <Form.Item name="author">
            <Input
              placeholder="Author"
              allowClear
              onChange={(e) =>
                setFilters((p) => ({ ...p, author: e.target.value }))
              }
            />
          </Form.Item>
          <Form.Item>
            <Button icon={<SearchOutlined />} type="primary" htmlType="submit">
              Search
            </Button>
          </Form.Item>
        </Form>

        <Row justify="space-between" style={{ marginBottom: 16 }}>
          <Space>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditingJob(null);
                setFormModalOpen(true);
              }}
            >
              Add Job
            </Button>
            <ImportExportButtons
              selectedIds={selectedRowKeys}
              onImportSuccess={() =>
                queryClient.invalidateQueries({ queryKey: ['jobs'] })
              }
            />
          </Space>
        </Row>

        <Table<XxlJobInfo>
          rowKey="id"
          columns={columns}
          dataSource={data?.data}
          loading={isLoading}
          scroll={{ x: 1100 }}
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys as number[]),
          }}
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

      <JobFormModal
        open={formModalOpen}
        job={editingJob}
        groups={groups}
        onClose={() => {
          setFormModalOpen(false);
          setEditingJob(null);
        }}
        onSuccess={() => queryClient.invalidateQueries({ queryKey: ['jobs'] })}
      />
      <TriggerModal
        open={triggerModalOpen}
        jobId={triggerJobId}
        onClose={() => setTriggerModalOpen(false)}
      />
      <BatchTriggerModal
        open={batchTriggerOpen}
        jobId={batchTriggerJobId}
        onClose={() => setBatchTriggerOpen(false)}
      />
      <BatchCopyModal
        open={batchCopyOpen}
        jobId={batchCopyJobId}
        onClose={() => setBatchCopyOpen(false)}
        onSuccess={() => queryClient.invalidateQueries({ queryKey: ['jobs'] })}
      />
    </>
  );
}
