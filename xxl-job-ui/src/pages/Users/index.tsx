import { useState } from 'react';
import {
    Card,
    Table,
    Button,
    Space,
    Input,
    Select,
    Tag,
    message,
    Popconfirm,
    Form,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { fetchUsers, deleteUser } from '../../api/users';
import { usePagination } from '../../hooks/usePagination';
import { useIsMobile } from '../../hooks/useIsMobile';
import { useAuthStore } from '../../store/authStore';
import type { XxlJobUser } from '../../types/user';
import UserFormModal from './components/UserFormModal';

export default function UsersPage() {
    const queryClient = useQueryClient();
    const isMobile = useIsMobile();
    const currentUserId = useAuthStore((s) => s.userInfo?.userId);
    const { current, pageSize, offset, onChange } = usePagination();
    const { t } = useTranslation('user');
    const { t: tc } = useTranslation('common');
    const [usernameFilter, setUsernameFilter] = useState('');
    const [roleFilter, setRoleFilter] = useState<number | undefined>(undefined);

    const [formOpen, setFormOpen] = useState(false);
    const [editingUser, setEditingUser] = useState<XxlJobUser | null>(null);

    const { data, isLoading } = useQuery({
        queryKey: ['users', offset, pageSize, usernameFilter, roleFilter],
        queryFn: () =>
            fetchUsers({
                offset,
                pagesize: pageSize,
                username: usernameFilter || undefined,
                role: roleFilter,
            }),
    });

    const deleteMutation = useMutation({
        mutationFn: deleteUser,
        onSuccess: () => {
            message.success(t('messages.userDeleted'));
            queryClient.invalidateQueries({ queryKey: ['users'] });
        },
        onError: (e) => message.error(e.message),
    });

    const columns: ColumnsType<XxlJobUser> = [
        { title: t('columns.id'), dataIndex: 'id', width: 60 },
        { title: t('columns.username'), dataIndex: 'username', width: 150 },
        {
            title: t('columns.role'),
            dataIndex: 'role',
            width: 100,
            render: (v: number) => (
                <Tag color={v === 1 ? 'gold' : 'default'}>
                    {v === 1 ? t('roles.admin') : t('roles.user')}
                </Tag>
            ),
        },
        {
            title: t('columns.permissions'),
            dataIndex: 'permission',
            ellipsis: true,
            render: (v: string) => v || '-',
        },
        {
            title: t('columns.actions'),
            width: 100,
            render: (_: unknown, record: XxlJobUser) => (
                <Space size="small">
                    <Button
                        type="link"
                        size="small"
                        icon={<EditOutlined />}
                        onClick={() => {
                            setEditingUser(record);
                            setFormOpen(true);
                        }}
                    />
                    <Popconfirm
                        title={t('deleteConfirm')}
                        onConfirm={() => deleteMutation.mutate(record.id)}
                        disabled={record.id === currentUserId}
                    >
                        <Button
                            type="link"
                            size="small"
                            danger
                            icon={<DeleteOutlined />}
                            disabled={record.id === currentUserId}
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
                            placeholder={t('filters.username')}
                            allowClear
                            onChange={(e) => setUsernameFilter(e.target.value)}
                        />
                    </Form.Item>
                    <Form.Item>
                        <Select
                            style={{ width: isMobile ? '100%' : 120 }}
                            placeholder={t('filters.role')}
                            allowClear
                            onChange={(v) => setRoleFilter(v)}
                            options={[
                                { value: 0, label: t('roles.user') },
                                { value: 1, label: t('roles.admin') },
                            ]}
                        />
                    </Form.Item>
                    <Form.Item>
                        <Button
                            type="primary"
                            icon={<PlusOutlined />}
                            onClick={() => {
                                setEditingUser(null);
                                setFormOpen(true);
                            }}
                        >
                            {t('addUser')}
                        </Button>
                    </Form.Item>
                </Form>

                <Table<XxlJobUser>
                    rowKey="id"
                    columns={columns}
                    dataSource={data?.data}
                    loading={isLoading}
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

            <UserFormModal
                open={formOpen}
                user={editingUser}
                onClose={() => {
                    setFormOpen(false);
                    setEditingUser(null);
                }}
                onSuccess={() => queryClient.invalidateQueries({ queryKey: ['users'] })}
            />
        </>
    );
}
