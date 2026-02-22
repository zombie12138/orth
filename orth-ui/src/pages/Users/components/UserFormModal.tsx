import { useEffect } from 'react';
import { Modal, Form, Input, Select, message } from 'antd';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { createUser, updateUser } from '../../../api/users';
import { fetchPermittedGroups } from '../../../api/groups';
import { useIsMobile } from '../../../hooks/useIsMobile';
import type { JobUser } from '../../../types/user';

interface Props {
    open: boolean;
    user: JobUser | null;
    onClose: () => void;
    onSuccess: () => void;
}

export default function UserFormModal({ open, user, onClose, onSuccess }: Props) {
    const isMobile = useIsMobile();
    const [form] = Form.useForm();
    const isEdit = !!user;
    const { t } = useTranslation('user');
    const { t: tc } = useTranslation('common');

    const { data: groups = [] } = useQuery({
        queryKey: ['permitted-groups'],
        queryFn: fetchPermittedGroups,
        enabled: open,
    });

    useEffect(() => {
        if (open && user) {
            form.setFieldsValue({
                ...user,
                permissionList: user.permission
                    ? user.permission.split(',').filter(Boolean).map(Number)
                    : [],
            });
        } else if (open) {
            form.resetFields();
        }
    }, [open, user, form]);

    const saveMutation = useMutation({
        mutationFn: (values: Record<string, unknown>) => {
            const permissionList = (values.permissionList as number[]) ?? [];
            const payload: Partial<JobUser> = {
                username: values.username as string,
                role: values.role as number,
                permission: permissionList.join(','),
            };
            if (values.password) {
                payload.password = values.password as string;
            }
            return isEdit ? updateUser(user!.id, payload) : createUser(payload);
        },
        onSuccess: () => {
            message.success(
                isEdit ? t('messages.userUpdated') : t('messages.userCreated'),
            );
            onClose();
            onSuccess();
        },
        onError: (e) => message.error(e.message),
    });

    const handleOk = async () => {
        const values = await form.validateFields();
        saveMutation.mutate(values);
    };

    return (
        <Modal
            title={isEdit ? t('editUser') : t('createUser')}
            open={open}
            onOk={handleOk}
            onCancel={onClose}
            confirmLoading={saveMutation.isPending}
            width={isMobile ? '95vw' : undefined}
            destroyOnClose
        >
            <Form form={form} layout="vertical" initialValues={{ role: 0 }}>
                <Form.Item
                    name="username"
                    label={t('form.username')}
                    rules={[
                        { required: true },
                        { min: 4, message: tc('minChars', { count: 4 }) },
                        { max: 20, message: tc('maxChars', { count: 20 }) },
                    ]}
                >
                    <Input disabled={isEdit} />
                </Form.Item>
                <Form.Item
                    name="password"
                    label={isEdit ? t('form.newPasswordEdit') : t('form.password')}
                    rules={
                        isEdit
                            ? []
                            : [
                                  { required: true },
                                  {
                                      min: 4,
                                      message: tc('minChars', { count: 4 }),
                                  },
                                  {
                                      max: 20,
                                      message: tc('maxChars', { count: 20 }),
                                  },
                              ]
                    }
                >
                    <Input.Password />
                </Form.Item>
                <Form.Item
                    name="role"
                    label={t('form.role')}
                    rules={[{ required: true }]}
                >
                    <Select
                        options={[
                            { value: 0, label: t('roles.user') },
                            { value: 1, label: t('roles.admin') },
                        ]}
                    />
                </Form.Item>
                <Form.Item name="permissionList" label={t('form.permissions')}>
                    <Select
                        mode="multiple"
                        placeholder={t('form.permissionsPlaceholder')}
                        options={groups.map((g) => ({
                            value: g.id,
                            label: g.title,
                        }))}
                    />
                </Form.Item>
            </Form>
        </Modal>
    );
}
