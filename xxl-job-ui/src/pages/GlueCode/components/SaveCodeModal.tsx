import { useState } from 'react';
import { Modal, Form, Input, message } from 'antd';
import { saveGlueCode } from '../../../api/glue';

interface Props {
  open: boolean;
  jobId: number;
  code: string;
  onClose: () => void;
  onSuccess: () => void;
}

export default function SaveCodeModal({
  open,
  jobId,
  code,
  onClose,
  onSuccess,
}: Props) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleOk = async () => {
    const values = await form.validateFields();
    setLoading(true);
    try {
      await saveGlueCode(jobId, code, values.glueRemark as string);
      message.success('Code saved');
      form.resetFields();
      onClose();
      onSuccess();
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : 'Save failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title="Save GLUE Code"
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      confirmLoading={loading}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="glueRemark"
          label="Remark"
          rules={[
            { required: true, message: 'Please enter a remark' },
            { min: 4, message: 'At least 4 characters' },
            { max: 100, message: 'At most 100 characters' },
          ]}
        >
          <Input placeholder="Describe what changed" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
