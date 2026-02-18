import { useState } from 'react';
import { Modal, Form, Input, message } from 'antd';
import { triggerJob } from '../../../api/jobs';

interface Props {
  open: boolean;
  jobId: number;
  onClose: () => void;
}

export default function TriggerModal({ open, jobId, onClose }: Props) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleOk = async () => {
    const values = await form.validateFields();
    setLoading(true);
    try {
      await triggerJob(
        jobId,
        values.executorParam as string | undefined,
        values.addressList as string | undefined,
      );
      message.success('Job triggered');
      form.resetFields();
      onClose();
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : 'Trigger failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title="Trigger Job"
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      confirmLoading={loading}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item name="executorParam" label="Executor Parameters">
          <Input.TextArea rows={3} placeholder="Optional override" />
        </Form.Item>
        <Form.Item name="addressList" label="Address Override">
          <Input placeholder="Optional: comma-separated executor addresses" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
