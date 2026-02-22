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
    Popover,
    Typography,
} from 'antd';
import { EyeOutlined, StopOutlined, DeleteOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { fetchLogs, killJob } from '../../api/logs';
import { showError } from '../../api/client';
import { searchJobs } from '../../api/jobs';
import { fetchPermittedGroups } from '../../api/groups';
import { usePagination } from '../../hooks/usePagination';
import { useIsMobile } from '../../hooks/useIsMobile';
import dayjs from 'dayjs';
import { formatDate, formatDateRange } from '../../utils/date';
import { getLogStatus } from '../../utils/constants';
import type { JobLog } from '../../types/log';
import type { JobGroup } from '../../types/group';
import type { JobInfo } from '../../types/job';
import LogDrawer from './components/LogDrawer';
import ClearLogsModal from './components/ClearLogsModal';
import { message, Popconfirm } from 'antd';
import debounce from '../_utils/debounce';

function stripHtml(html: string) {
    return html
        .replace(/<br\s*\/?>/gi, '\n')
        .replace(/<[^>]+>/g, '')
        .trim();
}

function StatusPopoverContent({ record }: { record: JobLog }) {
    const { t } = useTranslation('log');
    const triggerText = record.triggerMsg ? stripHtml(record.triggerMsg) : '';
    const handleText = record.handleMsg ? stripHtml(record.handleMsg) : '';
    if (!triggerText && !handleText) {
        return <Typography.Text type="secondary">{t('noDetails')}</Typography.Text>;
    }
    return (
        <div style={{ maxWidth: 420, maxHeight: 300, overflow: 'auto' }}>
            {triggerText && (
                <>
                    <Typography.Text strong>{t('triggerMessage')}</Typography.Text>
                    <pre
                        style={{
                            margin: '4px 0 8px',
                            whiteSpace: 'pre-wrap',
                            fontSize: 12,
                        }}
                    >
                        {triggerText}
                    </pre>
                </>
            )}
            {handleText && (
                <>
                    <Typography.Text strong>{t('handleMessage')}</Typography.Text>
                    <pre
                        style={{
                            margin: '4px 0 0',
                            whiteSpace: 'pre-wrap',
                            fontSize: 12,
                        }}
                    >
                        {handleText}
                    </pre>
                </>
            )}
        </div>
    );
}

export default function LogsPage() {
    const queryClient = useQueryClient();
    const isMobile = useIsMobile();
    const [searchParams] = useSearchParams();
    const { current, pageSize, offset, onChange } = usePagination();
    const { t } = useTranslation('log');
    const { t: tc } = useTranslation('common');

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
            message.success(t('killSent'));
            queryClient.invalidateQueries({ queryKey: ['logs'] });
        },
        onError: (e) => showError(e),
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
                        jobs.map((j: JobInfo) => ({
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

    const groupMap = new Map(groups.map((g: JobGroup) => [g.id, g.title]));

    const columns: ColumnsType<JobLog> = [
        { title: t('columns.id'), dataIndex: 'id', width: 70 },
        ...(isMobile
            ? []
            : [
                  {
                      title: t('columns.group'),
                      dataIndex: 'jobGroup',
                      width: 110,
                      render: (v: number) => groupMap.get(v) ?? v,
                  } as const,
              ]),
        { title: t('columns.jid'), dataIndex: 'jobId', width: 70 },
        ...(isMobile
            ? []
            : [
                  {
                      title: t('columns.handler'),
                      dataIndex: 'executorHandler',
                      width: 130,
                      ellipsis: { showTitle: false },
                      render: (v: string) => (
                          <Tooltip title={v}>
                              <span>{v}</span>
                          </Tooltip>
                      ),
                  } as const,
              ]),
        ...(isMobile
            ? []
            : [
                  {
                      title: t('columns.scheduleTime'),
                      dataIndex: 'scheduleTime',
                      width: 160,
                      render: (v: string | null) => formatDate(v),
                  } as const,
              ]),
        {
            title: t('columns.triggerTime'),
            dataIndex: 'triggerTime',
            width: 160,
            render: (v: string) => formatDate(v),
        },
        ...(isMobile
            ? []
            : [
                  {
                      title: t('columns.execution'),
                      width: 200,
                      render: (_: unknown, r: JobLog) => {
                          if (!r.handleTime) return '-';
                          const handleMs = dayjs(r.handleTime).valueOf();
                          const triggerMs = dayjs(r.triggerTime).valueOf();
                          const diffMs = handleMs - triggerMs;
                          let duration: string;
                          if (diffMs < 1000) duration = `${diffMs}ms`;
                          else if (diffMs < 60000)
                              duration = `${(diffMs / 1000).toFixed(1)}s`;
                          else duration = `${(diffMs / 60000).toFixed(1)}m`;
                          return `${formatDate(r.handleTime)} (${duration})`;
                      },
                  } as const,
              ]),
        {
            title: t('columns.status'),
            width: 140,
            render: (_: unknown, r: JobLog) => {
                const s = getLogStatus(r.triggerCode, r.handleCode, tc);
                return (
                    <Popover
                        content={<StatusPopoverContent record={r} />}
                        title={`${s.text} (${r.triggerCode}/${r.handleCode})`}
                        trigger="click"
                    >
                        <Tag color={s.color} style={{ cursor: 'pointer' }}>
                            {s.text} {r.triggerCode}/{r.handleCode}
                        </Tag>
                    </Popover>
                );
            },
        },
        {
            title: t('columns.detail'),
            width: 100,
            fixed: 'right',
            render: (_: unknown, record: JobLog) => (
                <Space size="small">
                    <Button
                        type="link"
                        size="small"
                        icon={<EyeOutlined />}
                        onClick={() => setDrawerLogId(record.id)}
                    />
                    {record.triggerCode === 200 && record.handleCode === 0 && (
                        <Popconfirm
                            title={t('killConfirm')}
                            onConfirm={() => killMutation.mutate(record.id)}
                        >
                            <Button
                                type="link"
                                size="small"
                                danger
                                icon={<StopOutlined />}
                            />
                        </Popconfirm>
                    )}
                </Space>
            ),
        },
    ];

    return (
        <>
            <Card>
                <Form
                    layout={isMobile ? 'vertical' : 'inline'}
                    style={{ marginBottom: 16, flexWrap: 'wrap', gap: 8 }}
                >
                    <Form.Item>
                        <Select
                            style={{ width: isMobile ? '100%' : 160 }}
                            placeholder={t('filters.executorGroup')}
                            allowClear
                            value={filters.jobGroup || undefined}
                            onChange={handleGroupChange}
                            options={groups.map((g: JobGroup) => ({
                                value: g.id,
                                label: g.title,
                            }))}
                        />
                    </Form.Item>
                    <Form.Item>
                        <Select
                            style={{ width: isMobile ? '100%' : 200 }}
                            placeholder={t('filters.searchJob')}
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
                            notFoundContent={
                                jobSearchQuery ? t('filters.noMatches') : null
                            }
                        />
                    </Form.Item>
                    <Form.Item>
                        <Select
                            style={{ width: isMobile ? '100%' : 120 }}
                            placeholder={t('filters.status')}
                            allowClear
                            onChange={(v) =>
                                setFilters((p) => ({ ...p, logStatus: v ?? -1 }))
                            }
                            options={[
                                { value: 1, label: t('statusOptions.success') },
                                { value: 2, label: t('statusOptions.failed') },
                                { value: 3, label: t('statusOptions.running') },
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
                            {t('clearLogs')}
                        </Button>
                    </Form.Item>
                </Form>

                <Table<JobLog>
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
                        showTotal: (total) => tc('total', { total }),
                        onChange,
                    }}
                />
            </Card>

            <LogDrawer logId={drawerLogId} onClose={() => setDrawerLogId(null)} />
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
