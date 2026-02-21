import { useState } from 'react';
import { Modal, Select, message } from 'antd';
import { useTranslation } from 'react-i18next';
import { clearLogs } from '../../../api/logs';
import { getClearLogTypes } from '../../../utils/constants';
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
    const { t } = useTranslation('log');
    const [type, setType] = useState<number>(1);
    const [loading, setLoading] = useState(false);

    const handleOk = async () => {
        setLoading(true);
        try {
            await clearLogs(jobGroup, jobId, type);
            message.success(t('logsCleared'));
            onClose();
            onSuccess();
        } catch (e: unknown) {
            message.error(e instanceof Error ? e.message : t('clearFailed'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal
            title={t('clearLogsTitle')}
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
                options={getClearLogTypes(t)}
            />
        </Modal>
    );
}
