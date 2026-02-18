import { useEffect } from 'react';
import { Modal, Form, Input, Radio, message } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { createGroup, updateGroup } from '../../../api/groups';
import type { XxlJobGroup } from '../../../types/group';

interface Props {
  open: boolean;
  group: XxlJobGroup | null;
  onClose: () => void;
  onSuccess: () => void;
}

export default function GroupFormModal({
  open,
  group,
  onClose,
  onSuccess,
}: Props) {
  const [form] = Form.useForm();
  const isEdit = !!group;
  const addressType = Form.useWatch('addressType', form);

  useEffect(() => {
    if (open && group) {
      form.setFieldsValue(group);
    } else if (open) {
      form.resetFields();
    }
  }, [open, group, form]);

  const saveMutation = useMutation({
    mutationFn: (values: Partial<XxlJobGroup>) =>
      isEdit ? updateGroup(group!.id, values) : createGroup(values),
    onSuccess: () => {
      message.success(isEdit ? 'Group updated' : 'Group created');
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
      title={isEdit ? 'Edit Executor Group' : 'Create Executor Group'}
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      confirmLoading={saveMutation.isPending}
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ addressType: 0 }}
      >
        <Form.Item
          name="appname"
          label="AppName"
          rules={[
            { required: true },
            { min: 4, message: 'Min 4 characters' },
            { max: 64, message: 'Max 64 characters' },
          ]}
        >
          <Input disabled={isEdit} />
        </Form.Item>
        <Form.Item
          name="title"
          label="Title"
          rules={[{ required: true }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="addressType" label="Address Type">
          <Radio.Group>
            <Radio value={0}>Auto Discovery</Radio>
            <Radio value={1}>Manual</Radio>
          </Radio.Group>
        </Form.Item>
        {addressType === 1 && (
          <Form.Item
            name="addressList"
            label="Addresses"
            rules={[{ required: true, message: 'Enter executor addresses' }]}
          >
            <Input.TextArea
              rows={3}
              placeholder="Comma-separated URLs (e.g., http://host:9999)"
            />
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
}
