import { useEffect } from 'react';
import { Modal, Form, Input, Select, message } from 'antd';
import { useMutation, useQuery } from '@tanstack/react-query';
import { createUser, updateUser } from '../../../api/users';
import { fetchPermittedGroups } from '../../../api/groups';
import { useIsMobile } from '../../../hooks/useIsMobile';
import type { XxlJobUser } from '../../../types/user';

interface Props {
  open: boolean;
  user: XxlJobUser | null;
  onClose: () => void;
  onSuccess: () => void;
}

export default function UserFormModal({
  open,
  user,
  onClose,
  onSuccess,
}: Props) {
  const isMobile = useIsMobile();
  const [form] = Form.useForm();
  const isEdit = !!user;

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
      const payload: Partial<XxlJobUser> = {
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
      message.success(isEdit ? 'User updated' : 'User created');
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
      title={isEdit ? 'Edit User' : 'Create User'}
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      confirmLoading={saveMutation.isPending}
      width={isMobile ? '95vw' : undefined}
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ role: 0 }}
      >
        <Form.Item
          name="username"
          label="Username"
          rules={[
            { required: true },
            { min: 4, message: 'Min 4 characters' },
            { max: 20, message: 'Max 20 characters' },
          ]}
        >
          <Input disabled={isEdit} />
        </Form.Item>
        <Form.Item
          name="password"
          label={isEdit ? 'New Password (leave blank to keep)' : 'Password'}
          rules={
            isEdit
              ? []
              : [
                  { required: true },
                  { min: 4, message: 'Min 4 characters' },
                  { max: 20, message: 'Max 20 characters' },
                ]
          }
        >
          <Input.Password />
        </Form.Item>
        <Form.Item name="role" label="Role" rules={[{ required: true }]}>
          <Select
            options={[
              { value: 0, label: 'User' },
              { value: 1, label: 'Admin' },
            ]}
          />
        </Form.Item>
        <Form.Item name="permissionList" label="Permissions">
          <Select
            mode="multiple"
            placeholder="Select executor groups"
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
