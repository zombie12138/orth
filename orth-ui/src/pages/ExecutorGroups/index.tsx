import { useState } from 'react';
import {
    Card,
    Table,
    Button,
    Space,
    Input,
    Tag,
    message,
    Popconfirm,
    Form,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { fetchGroups, deleteGroup } from '../../api/groups';
import { showError } from '../../api/client';
import { usePagination } from '../../hooks/usePagination';
import { useIsMobile } from '../../hooks/useIsMobile';
import { formatDate } from '../../utils/date';
import type { JobGroup } from '../../types/group';
import GroupFormModal from './components/GroupFormModal';

export default function ExecutorGroupsPage() {
    const queryClient = useQueryClient();
    const isMobile = useIsMobile();
    const { current, pageSize, offset, onChange } = usePagination();
    const { t } = useTranslation('executor');
    const { t: tc } = useTranslation('common');
    const [appname, setAppname] = useState('');
    const [title, setTitle] = useState('');

    const [formOpen, setFormOpen] = useState(false);
    const [editingGroup, setEditingGroup] = useState<JobGroup | null>(null);

    const { data, isLoading } = useQuery({
        queryKey: ['groups', offset, pageSize, appname, title],
        queryFn: () =>
            fetchGroups({
                offset,
                pagesize: pageSize,
                appname: appname || undefined,
                title: title || undefined,
            }),
    });

    const deleteMutation = useMutation({
        mutationFn: deleteGroup,
        onSuccess: () => {
            message.success(t('messages.groupDeleted'));
            queryClient.invalidateQueries({ queryKey: ['groups'] });
        },
        onError: (e) => showError(e),
    });

    const columns: ColumnsType<JobGroup> = [
        { title: t('columns.id'), dataIndex: 'id', width: 60 },
        { title: t('columns.appName'), dataIndex: 'appname', width: 150 },
        { title: t('columns.title'), dataIndex: 'title', width: 150 },
        {
            title: t('columns.type'),
            dataIndex: 'addressType',
            width: 90,
            render: (v: number) => (
                <Tag color={v === 0 ? 'gold' : 'orange'}>
                    {v === 0 ? t('type.auto') : t('type.manual')}
                </Tag>
            ),
        },
        {
            title: t('columns.onlineMachines'),
            width: 200,
            render: (_: unknown, r: JobGroup) => {
                const list =
                    r.addressType === 0
                        ? (r.registryList ?? [])
                        : (r.addressList ?? '').split(',').filter(Boolean);
                return list.length > 0 ? list.join(', ') : '-';
            },
        },
        ...(isMobile
            ? []
            : [
                  {
                      title: t('columns.updated'),
                      dataIndex: 'updateTime',
                      width: 160,
                      render: (v: string) => formatDate(v),
                  } as const,
              ]),
        {
            title: t('columns.actions'),
            width: 100,
            render: (_: unknown, record: JobGroup) => (
                <Space size="small">
                    <Button
                        type="link"
                        size="small"
                        icon={<EditOutlined />}
                        onClick={() => {
                            setEditingGroup(record);
                            setFormOpen(true);
                        }}
                    />
                    <Popconfirm
                        title={t('deleteConfirm')}
                        onConfirm={() => deleteMutation.mutate(record.id)}
                    >
                        <Button
                            type="link"
                            size="small"
                            danger
                            icon={<DeleteOutlined />}
                        />
                    </Popconfirm>
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
                        <Input
                            placeholder={t('filters.appName')}
                            allowClear
                            onChange={(e) => setAppname(e.target.value)}
                        />
                    </Form.Item>
                    <Form.Item>
                        <Input
                            placeholder={t('filters.title')}
                            allowClear
                            onChange={(e) => setTitle(e.target.value)}
                        />
                    </Form.Item>
                    <Form.Item>
                        <Button
                            type="primary"
                            icon={<PlusOutlined />}
                            onClick={() => {
                                setEditingGroup(null);
                                setFormOpen(true);
                            }}
                        >
                            {t('addGroup')}
                        </Button>
                    </Form.Item>
                </Form>

                <Table<JobGroup>
                    rowKey="id"
                    columns={columns}
                    dataSource={data?.data}
                    loading={isLoading}
                    scroll={{ x: 800 }}
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

            <GroupFormModal
                open={formOpen}
                group={editingGroup}
                onClose={() => {
                    setFormOpen(false);
                    setEditingGroup(null);
                }}
                onSuccess={() => queryClient.invalidateQueries({ queryKey: ['groups'] })}
            />
        </>
    );
}
