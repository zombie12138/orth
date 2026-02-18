import { useState } from 'react';
import { Modal, Form, Input, message } from 'antd';
import { updatePassword } from '../api/auth';

interface Props {
  open: boolean;
  onClose: () => void;
}

export default function PasswordChangeModal({ open, onClose }: Props) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleOk = async () => {
    const values = await form.validateFields();
    setLoading(true);
    try {
      await updatePassword({
        oldPassword: values.oldPassword as string,
        newPassword: values.newPassword as string,
      });
      message.success('Password updated successfully');
      form.resetFields();
      onClose();
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : 'Failed to update password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title="Change Password"
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      confirmLoading={loading}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="oldPassword"
          label="Current Password"
          rules={[{ required: true, message: 'Please enter current password' }]}
        >
          <Input.Password />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label="New Password"
          rules={[
            { required: true, message: 'Please enter new password' },
            { min: 4, message: 'Password must be at least 4 characters' },
            { max: 20, message: 'Password must be at most 20 characters' },
          ]}
        >
          <Input.Password />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label="Confirm Password"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: 'Please confirm new password' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('newPassword') === value) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error('Passwords do not match'));
              },
            }),
          ]}
        >
          <Input.Password />
        </Form.Item>
      </Form>
    </Modal>
  );
}
