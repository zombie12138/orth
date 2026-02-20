import { useState } from 'react';
import { Modal, Select, message } from 'antd';
import { clearLogs } from '../../../api/logs';
import { CLEAR_LOG_TYPES } from '../../../utils/constants';
import { useIsMobile } from '../../../hooks/useIsMobile';

interface Props {
  open: boolean;
  jobGroup: number;
  jobId: number;
  onClose: () => void;
  onSuccess: () => void;
}

export default function ClearLogsModal({
  open,
  jobGroup,
  jobId,
  onClose,
  onSuccess,
}: Props) {
  const isMobile = useIsMobile();
  const [type, setType] = useState<number>(1);
  const [loading, setLoading] = useState(false);

  const handleOk = async () => {
    setLoading(true);
    try {
      await clearLogs(jobGroup, jobId, type);
      message.success('Logs cleared');
      onClose();
      onSuccess();
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : 'Failed to clear logs');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title="Clear Logs"
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      confirmLoading={loading}
      width={isMobile ? '95vw' : undefined}
    >
      <Select
        style={{ width: '100%' }}
        value={type}
        onChange={setType}
        options={CLEAR_LOG_TYPES}
      />
    </Modal>
  );
}
