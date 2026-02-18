import { useState } from 'react';
import {
  Modal,
  Form,
  Input,
  Tabs,
  Table,
  Button,
  message,
  Tag,
} from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { batchCopy } from '../../../api/jobs';
import type { BatchCopyRequest, BatchCopyResult } from '../../../types/batch';

interface Props {
  open: boolean;
  jobId: number;
  onClose: () => void;
  onSuccess: () => void;
}

export default function BatchCopyModal({
  open,
  jobId,
  onClose,
  onSuccess,
}: Props) {
  const [form] = Form.useForm();
  const [mode, setMode] = useState<'simple' | 'advanced'>('simple');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<BatchCopyResult | null>(null);

  // Simple mode: params as newline-separated text
  const [paramsText, setParamsText] = useState('');

  // Advanced mode: task configs
  const [tasks, setTasks] = useState<
    { key: number; superTaskParam: string; jobDesc: string }[]
  >([]);
  const [nextKey, setNextKey] = useState(0);

  const addTask = () => {
    setTasks((prev) => [
      ...prev,
      { key: nextKey, superTaskParam: '', jobDesc: '' },
    ]);
    setNextKey((k) => k + 1);
  };

  const removeTask = (key: number) => {
    setTasks((prev) => prev.filter((t) => t.key !== key));
  };

  const handleOk = async () => {
    const values = await form.validateFields();
    setLoading(true);
    try {
      const request: BatchCopyRequest = {
        templateJobId: jobId,
        mode,
      };
      if (mode === 'simple') {
        request.params = paramsText
          .split('\n')
          .map((s) => s.trim())
          .filter(Boolean);
        request.nameTemplate = values.nameTemplate as string | undefined;
      } else {
        request.tasks = tasks.map((t) => ({
          superTaskParam: t.superTaskParam,
          jobDesc: t.jobDesc || undefined,
        }));
      }
      // Common overrides
      if (values.jobDesc) request.jobDesc = values.jobDesc as string;
      if (values.author) request.author = values.author as string;

      const res = await batchCopy(request);
      setResult(res);
      message.success(`Created ${res.successCount} tasks`);
      onSuccess();
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : 'Batch copy failed');
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    setResult(null);
    setParamsText('');
    setTasks([]);
    form.resetFields();
    onClose();
  };

  return (
    <Modal
      title="Batch Copy SubTasks"
      open={open}
      onOk={result ? handleClose : handleOk}
      onCancel={handleClose}
      okText={result ? 'Done' : 'Create'}
      confirmLoading={loading}
      width={700}
      destroyOnClose
    >
      {result ? (
        <div>
          <p>
            <Tag color="green">Success: {result.successCount}</Tag>
            {result.failCount > 0 && (
              <Tag color="red">Failed: {result.failCount}</Tag>
            )}
          </p>
          {result.createdJobIds.length > 0 && (
            <p>Created IDs: {result.createdJobIds.join(', ')}</p>
          )}
          {result.errors.length > 0 && (
            <ul>
              {result.errors.map((e, i) => (
                <li key={i} style={{ color: 'red' }}>
                  {e}
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : (
        <Form form={form} layout="vertical">
          <Tabs
            activeKey={mode}
            onChange={(k) => setMode(k as 'simple' | 'advanced')}
            items={[
              {
                key: 'simple',
                label: 'Simple',
                children: (
                  <>
                    <Form.Item name="nameTemplate" label="Name Template">
                      <Input placeholder="{origin}_{index}" />
                    </Form.Item>
                    <Form.Item label="Parameters (one per line)" required>
                      <Input.TextArea
                        rows={6}
                        value={paramsText}
                        onChange={(e) => setParamsText(e.target.value)}
                        placeholder="param1&#10;param2&#10;param3"
                      />
                    </Form.Item>
                  </>
                ),
              },
              {
                key: 'advanced',
                label: 'Advanced',
                children: (
                  <>
                    <Button
                      icon={<PlusOutlined />}
                      onClick={addTask}
                      style={{ marginBottom: 8 }}
                    >
                      Add Task
                    </Button>
                    <Table
                      size="small"
                      dataSource={tasks}
                      rowKey="key"
                      pagination={false}
                      columns={[
                        {
                          title: 'Parameter',
                          dataIndex: 'superTaskParam',
                          render: (_: string, record: (typeof tasks)[0]) => (
                            <Input
                              value={record.superTaskParam}
                              onChange={(e) =>
                                setTasks((prev) =>
                                  prev.map((t) =>
                                    t.key === record.key
                                      ? { ...t, superTaskParam: e.target.value }
                                      : t,
                                  ),
                                )
                              }
                            />
                          ),
                        },
                        {
                          title: 'Description',
                          dataIndex: 'jobDesc',
                          render: (_: string, record: (typeof tasks)[0]) => (
                            <Input
                              value={record.jobDesc}
                              onChange={(e) =>
                                setTasks((prev) =>
                                  prev.map((t) =>
                                    t.key === record.key
                                      ? { ...t, jobDesc: e.target.value }
                                      : t,
                                  ),
                                )
                              }
                            />
                          ),
                        },
                        {
                          title: '',
                          width: 40,
                          render: (_: unknown, record: (typeof tasks)[0]) => (
                            <Button
                              type="link"
                              danger
                              size="small"
                              icon={<DeleteOutlined />}
                              onClick={() => removeTask(record.key)}
                            />
                          ),
                        },
                      ]}
                    />
                  </>
                ),
              },
            ]}
          />
          <Form.Item name="jobDesc" label="Description Override">
            <Input placeholder="Optional" />
          </Form.Item>
          <Form.Item name="author" label="Author Override">
            <Input placeholder="Optional" />
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
}
