import { useState } from 'react';
import { Modal, Form, Input, DatePicker, Button, List, message } from 'antd';
import dayjs from 'dayjs';
import { triggerBatch, previewTriggerBatch } from '../../../api/jobs';
import { DATE_FORMAT } from '../../../utils/date';

interface Props {
  open: boolean;
  jobId: number;
  onClose: () => void;
}

export default function BatchTriggerModal({ open, jobId, onClose }: Props) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [preview, setPreview] = useState<string[]>([]);

  const handlePreview = async () => {
    const values = await form.validateFields(['timeRange']);
    const [start, end] = values.timeRange as [dayjs.Dayjs, dayjs.Dayjs];
    try {
      const times = await previewTriggerBatch(
        jobId,
        start.format(DATE_FORMAT),
        end.format(DATE_FORMAT),
      );
      setPreview(times);
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : 'Preview failed');
    }
  };

  const handleOk = async () => {
    const values = await form.validateFields();
    const [start, end] = values.timeRange as [dayjs.Dayjs, dayjs.Dayjs];
    setLoading(true);
    try {
      await triggerBatch(
        jobId,
        start.format(DATE_FORMAT),
        end.format(DATE_FORMAT),
        values.executorParam as string | undefined,
        values.addressList as string | undefined,
      );
      message.success('Batch trigger submitted');
      form.resetFields();
      setPreview([]);
      onClose();
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : 'Batch trigger failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title="Batch Trigger"
      open={open}
      onOk={handleOk}
      onCancel={() => {
        setPreview([]);
        onClose();
      }}
      confirmLoading={loading}
      width={600}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="timeRange"
          label="Time Range"
          rules={[{ required: true, message: 'Select time range' }]}
        >
          <DatePicker.RangePicker showTime style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item>
          <Button onClick={handlePreview}>Preview Schedule Times</Button>
        </Form.Item>
        {preview.length > 0 && (
          <List
            size="small"
            header={`${preview.length} trigger time(s) found`}
            dataSource={preview}
            renderItem={(t) => <List.Item>{t}</List.Item>}
            style={{ marginBottom: 16, maxHeight: 200, overflow: 'auto' }}
          />
        )}
        <Form.Item name="executorParam" label="Executor Parameters">
          <Input.TextArea rows={2} placeholder="Optional" />
        </Form.Item>
        <Form.Item name="addressList" label="Address Override">
          <Input placeholder="Optional" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
