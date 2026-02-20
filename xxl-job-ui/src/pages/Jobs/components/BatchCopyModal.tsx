import { useState, useEffect } from 'react';
import { App, Modal, Tag, Typography, theme } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { fetchJob, batchCopy } from '../../../api/jobs';
import type { BatchCopyRequest, BatchCopyResult } from '../../../types/batch';

const { Text } = Typography;

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
  const { message } = App.useApp();
  const { token } = theme.useToken();
  const [jsonText, setJsonText] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<BatchCopyResult | null>(null);

  const { data: jobInfo } = useQuery({
    queryKey: ['job', jobId],
    queryFn: () => fetchJob(jobId),
    enabled: open && jobId > 0,
  });

  // Generate template JSON when job info loads
  useEffect(() => {
    if (jobInfo && open) {
      const template = {
        templateJobId: jobId,
        mode: 'advanced',
        tasks: [
          {
            jobDesc: `${jobInfo.jobDesc} - SubTask 1`,
            executorParam: 'param1',
          },
          {
            jobDesc: `${jobInfo.jobDesc} - SubTask 2`,
            executorParam: 'param2',
          },
          {
            jobDesc: `${jobInfo.jobDesc} - SubTask 3`,
            executorParam: 'param3',
          },
        ],
      };
      setJsonText(JSON.stringify(template, null, 2));
    }
  }, [jobInfo, jobId, open]);

  const handleOk = async () => {
    if (result) {
      handleClose();
      return;
    }

    let request: BatchCopyRequest;
    try {
      request = JSON.parse(jsonText);
    } catch {
      message.error('Invalid JSON format');
      return;
    }

    setLoading(true);
    try {
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
    setJsonText('');
    onClose();
  };

  return (
    <Modal
      title="Fork SuperTask - Batch Create SubTasks"
      open={open}
      onOk={handleOk}
      onCancel={handleClose}
      okText={result ? 'Done' : 'Create SubTasks'}
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
                <li key={i} style={{ color: token.colorError }}>
                  {e}
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : (
        <div>
          <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
            Edit the JSON below to configure SubTasks. Each task in the{' '}
            <code>tasks</code> array can override:{' '}
            <code>jobDesc</code>, <code>executorParam</code>,{' '}
            <code>scheduleType</code>, <code>scheduleConf</code>,{' '}
            <code>author</code>, <code>alarmEmail</code>.
          </Text>
          <textarea
            value={jsonText}
            onChange={(e) => setJsonText(e.target.value)}
            spellCheck={false}
            style={{
              width: '100%',
              height: 450,
              fontFamily: 'monospace',
              fontSize: 13,
              lineHeight: 1.5,
              padding: 12,
              border: `1px solid ${token.colorBorder}`,
              borderRadius: 6,
              resize: 'vertical',
              tabSize: 2,
              background: token.colorBgContainer,
              color: token.colorText,
            }}
          />
        </div>
      )}
    </Modal>
  );
}
