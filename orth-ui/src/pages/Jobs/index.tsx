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
    Tooltip,
    Popover,
    Spin,
    List,
    theme,
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
    CalendarOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { fetchJobs, deleteJob, startJob, stopJob, nextTriggerTime } from '../../api/jobs';
import { fetchPermittedGroups } from '../../api/groups';
import { usePagination } from '../../hooks/usePagination';
import { useIsMobile } from '../../hooks/useIsMobile';
import { getTriggerStatus } from '../../utils/constants';
import type { JobInfo } from '../../types/job';
import type { JobGroup } from '../../types/group';
import JobFormModal from './components/JobFormModal';
import TriggerModal from './components/TriggerModal';
import BatchTriggerModal from './components/BatchTriggerModal';
import BatchCopyModal from './components/BatchCopyModal';
import ImportExportButtons from './components/ImportExportButtons';

function SchedulePreview({
    scheduleType,
    scheduleConf,
}: {
    scheduleType: string;
    scheduleConf: string;
}) {
    const { token } = theme.useToken();
    const { t } = useTranslation('job');
    const { data, isLoading, refetch, isFetched } = useQuery({
        queryKey: ['next-trigger-time', scheduleType, scheduleConf],
        queryFn: () => nextTriggerTime(scheduleType, scheduleConf),
        enabled: false,
    });

    const content = isLoading ? (
        <Spin size="small" />
    ) : (
        <List
            size="small"
            dataSource={data ?? []}
            renderItem={(item: string) => (
                <List.Item style={{ padding: '4px 0' }}>{item}</List.Item>
            )}
            locale={{ emptyText: t('noUpcomingTriggers') }}
        />
    );

    return (
        <Popover
            title={t('nextTriggerTimes')}
            content={<div style={{ maxHeight: 300, overflow: 'auto' }}>{content}</div>}
            trigger="click"
            onOpenChange={(open) => {
                if (open && !isFetched) refetch();
            }}
        >
            <CalendarOutlined style={{ cursor: 'pointer', color: token.colorPrimary }} />
        </Popover>
    );
}

export default function JobsPage() {
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const isMobile = useIsMobile();
    const { t } = useTranslation('job');
    const { t: tc } = useTranslation('common');
    const { current, pageSize, offset, onChange } = usePagination();

    const [filters, setFilters] = useState({
        jobGroup: 0,
        triggerStatus: -1,
        jobDesc: '',
        executorHandler: '',
        author: '',
        superTaskName: '',
    });

    const [formModalOpen, setFormModalOpen] = useState(false);
    const [editingJob, setEditingJob] = useState<JobInfo | null>(null);
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
            message.success(t('messages.jobDeleted'));
            queryClient.invalidateQueries({ queryKey: ['jobs'] });
        },
        onError: (e) => message.error(e.message),
    });

    const startMutation = useMutation({
        mutationFn: startJob,
        onSuccess: () => {
            message.success(t('messages.jobStarted'));
            queryClient.invalidateQueries({ queryKey: ['jobs'] });
        },
        onError: (e) => message.error(e.message),
    });

    const stopMutation = useMutation({
        mutationFn: stopJob,
        onSuccess: () => {
            message.success(t('messages.jobStopped'));
            queryClient.invalidateQueries({ queryKey: ['jobs'] });
        },
        onError: (e) => message.error(e.message),
    });

    const handleSearch = useCallback(
        (values: Record<string, string | number | undefined>) => {
            setFilters((prev) => ({
                ...prev,
                ...Object.fromEntries(
                    Object.entries(values).map(([k, v]) => [
                        k,
                        v ?? (typeof prev[k as keyof typeof prev] === 'number' ? 0 : ''),
                    ]),
                ),
            }));
        },
        [],
    );

    const triggerStatus = getTriggerStatus(tc);

    const columns: ColumnsType<JobInfo> = [
        { title: t('columns.id'), dataIndex: 'id', width: 50 },
        {
            title: t('columns.group'),
            dataIndex: 'jobGroup',
            width: 120,
            render: (v: number) => {
                const group = groups.find((g) => g.id === v);
                if (!group) return v;
                const addrs = group.addressList?.split(',').filter(Boolean) || [];
                return (
                    <Tooltip
                        title={addrs.length > 0 ? addrs.join('\n') : t('noOnlineExecutors')}
                    >
                        <span>
                            {group.title} ({addrs.length})
                        </span>
                    </Tooltip>
                );
            },
        },
        {
            title: t('columns.description'),
            dataIndex: 'jobDesc',
            ellipsis: { showTitle: false },
            render: (v: string) => (
                <Tooltip title={v}>
                    <span>{v}</span>
                </Tooltip>
            ),
        },
        {
            title: t('columns.schedule'),
            width: 173,
            ellipsis: { showTitle: false },
            render: (_: unknown, r: JobInfo) => {
                if (r.scheduleType === 'NONE') return '-';
                const text = `${r.scheduleType}: ${r.scheduleConf}`;
                return (
                    <Tooltip title={text}>
                        <Space size={4}>
                            <span>{text}</span>
                            <SchedulePreview
                                scheduleType={r.scheduleType}
                                scheduleConf={r.scheduleConf}
                            />
                        </Space>
                    </Tooltip>
                );
            },
        },
        { title: t('columns.glueType'), dataIndex: 'glueType', width: 110 },
        {
            title: t('columns.handler'),
            dataIndex: 'executorHandler',
            width: 140,
            ellipsis: { showTitle: false },
            render: (v: string) => (
                <Tooltip title={v}>
                    <span>{v}</span>
                </Tooltip>
            ),
        },
        {
            title: t('columns.author'),
            dataIndex: 'author',
            width: 100,
            ellipsis: { showTitle: false },
            render: (v: string) => (
                <Tooltip title={v}>
                    <span>{v}</span>
                </Tooltip>
            ),
        },
        {
            title: t('columns.superTask'),
            width: 140,
            ellipsis: { showTitle: false },
            render: (_: unknown, r: JobInfo) => {
                if (!r.superTaskId || r.superTaskId <= 0) return '-';
                const text = t('subTaskPrefix', { id: r.superTaskId, name: r.superTaskName });
                return (
                    <Tooltip title={text}>
                        <Tag>{text}</Tag>
                    </Tooltip>
                );
            },
        },
        {
            title: t('columns.status'),
            dataIndex: 'triggerStatus',
            width: 90,
            render: (v: number) => {
                const s = triggerStatus[v as keyof typeof triggerStatus];
                return s ? <Tag color={s.color}>{s.text}</Tag> : v;
            },
        },
        {
            title: t('columns.actions'),
            width: 160,
            fixed: 'right',
            render: (_: unknown, record: JobInfo) => {
                const items = [
                    {
                        key: 'trigger',
                        icon: <ThunderboltOutlined />,
                        label: t('actions.trigger'),
                        onClick: () => {
                            setTriggerJobId(record.id);
                            setTriggerModalOpen(true);
                        },
                    },
                    {
                        key: 'copy',
                        icon: <CopyOutlined />,
                        label: t('actions.copy'),
                        onClick: () => {
                            setEditingJob({
                                ...record,
                                id: 0,
                                jobDesc: record.jobDesc + ' ' + t('copySuffix'),
                                triggerStatus: 0,
                                triggerLastTime: 0,
                                triggerNextTime: 0,
                            } as JobInfo);
                            setFormModalOpen(true);
                        },
                    },
                    {
                        key: 'batchTrigger',
                        icon: <FieldTimeOutlined />,
                        label: t('actions.batchTrigger'),
                        onClick: () => {
                            setBatchTriggerJobId(record.id);
                            setBatchTriggerOpen(true);
                        },
                    },
                    {
                        key: 'logs',
                        icon: <SearchOutlined />,
                        label: t('actions.viewLogs'),
                        onClick: () =>
                            navigate(
                                `/logs?jobGroup=${record.jobGroup}&jobId=${record.id}`,
                            ),
                    },
                    record.glueType !== 'BEAN'
                        ? {
                              key: 'code',
                              icon: <CodeOutlined />,
                              label: t('actions.glueCode'),
                              onClick: () => navigate(`/jobs/${record.id}/code`),
                          }
                        : null,
                    !record.superTaskId
                        ? {
                              key: 'batchCopy',
                              icon: <CopyOutlined />,
                              label: t('actions.forkSuperTask'),
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
                            title={tc('deleteConfirm', { item: 'job' })}
                            onConfirm={() => deleteMutation.mutate(record.id)}
                        >
                            <Button
                                type="link"
                                size="small"
                                danger
                                icon={<DeleteOutlined />}
                            />
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
                <Form
                    layout={isMobile ? 'vertical' : 'inline'}
                    onFinish={handleSearch}
                    style={{ marginBottom: 16, flexWrap: 'wrap', gap: 8 }}
                >
                    <Form.Item name="jobGroup">
                        <Select
                            style={{ width: isMobile ? '100%' : 160 }}
                            placeholder={t('filters.executorGroup')}
                            allowClear
                            value={filters.jobGroup || undefined}
                            onChange={(v) => setFilters((p) => ({ ...p, jobGroup: v ?? 0 }))}
                            options={groups.map((g: JobGroup) => ({
                                value: g.id,
                                label: g.title,
                            }))}
                        />
                    </Form.Item>
                    <Form.Item name="triggerStatus">
                        <Select
                            style={{ width: isMobile ? '100%' : 120 }}
                            placeholder={t('filters.status')}
                            allowClear
                            onChange={(v) =>
                                setFilters((p) => ({ ...p, triggerStatus: v ?? -1 }))
                            }
                            options={[
                                { value: 0, label: tc('stopped') },
                                { value: 1, label: tc('running') },
                            ]}
                        />
                    </Form.Item>
                    <Form.Item name="jobDesc">
                        <Input
                            placeholder={t('filters.jobDescription')}
                            allowClear
                            onChange={(e) =>
                                setFilters((p) => ({ ...p, jobDesc: e.target.value }))
                            }
                        />
                    </Form.Item>
                    <Form.Item name="executorHandler">
                        <Input
                            placeholder={t('filters.handler')}
                            allowClear
                            onChange={(e) =>
                                setFilters((p) => ({
                                    ...p,
                                    executorHandler: e.target.value,
                                }))
                            }
                        />
                    </Form.Item>
                    <Form.Item name="author">
                        <Input
                            placeholder={t('filters.author')}
                            allowClear
                            onChange={(e) =>
                                setFilters((p) => ({ ...p, author: e.target.value }))
                            }
                        />
                    </Form.Item>
                    <Form.Item name="superTaskName">
                        <Input
                            placeholder={t('filters.superTask')}
                            allowClear
                            onChange={(e) =>
                                setFilters((p) => ({
                                    ...p,
                                    superTaskName: e.target.value,
                                }))
                            }
                        />
                    </Form.Item>
                    <Form.Item>
                        <Button icon={<SearchOutlined />} type="primary" htmlType="submit">
                            {tc('search')}
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
                            {t('addJob')}
                        </Button>
                        <ImportExportButtons
                            selectedIds={selectedRowKeys}
                            onImportSuccess={() =>
                                queryClient.invalidateQueries({ queryKey: ['jobs'] })
                            }
                        />
                    </Space>
                </Row>

                <Table<JobInfo>
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
                        showTotal: (total) => tc('total', { total }),
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
