import { useState } from 'react';
import { Modal, Form, Input, message } from 'antd';
import { useTranslation } from 'react-i18next';
import { showError } from '../../../api/client';
import { triggerJob } from '../../../api/jobs';
import { useIsMobile } from '../../../hooks/useIsMobile';

interface Props {
    open: boolean;
    jobId: number;
    onClose: () => void;
}

export default function TriggerModal({ open, jobId, onClose }: Props) {
    const isMobile = useIsMobile();
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(false);
    const { t } = useTranslation('job');

    const handleOk = async () => {
        const values = await form.validateFields();
        setLoading(true);
        try {
            await triggerJob(
                jobId,
                values.executorParam as string | undefined,
                values.addressList as string | undefined,
            );
            message.success(t('messages.jobTriggered'));
            form.resetFields();
            onClose();
        } catch (e: unknown) {
            showError(e);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal
            title={t('trigger.title')}
            open={open}
            onOk={handleOk}
            onCancel={onClose}
            confirmLoading={loading}
            width={isMobile ? '95vw' : undefined}
            destroyOnClose
        >
            <Form form={form} layout="vertical">
                <Form.Item name="executorParam" label={t('trigger.executorParams')}>
                    <Input.TextArea
                        rows={3}
                        placeholder={t('trigger.executorParamsPlaceholder')}
                    />
                </Form.Item>
                <Form.Item name="addressList" label={t('trigger.addressOverride')}>
                    <Input placeholder={t('trigger.addressOverridePlaceholder')} />
                </Form.Item>
            </Form>
        </Modal>
    );
}
