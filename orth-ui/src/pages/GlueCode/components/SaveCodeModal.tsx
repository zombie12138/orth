import { useState } from 'react';
import { Modal, Form, Input, message } from 'antd';
import { useTranslation } from 'react-i18next';
import { showError } from '../../../api/client';
import { saveGlueCode } from '../../../api/glue';
import { useIsMobile } from '../../../hooks/useIsMobile';

interface Props {
    open: boolean;
    jobId: number;
    code: string;
    onClose: () => void;
    onSuccess: () => void;
}

export default function SaveCodeModal({ open, jobId, code, onClose, onSuccess }: Props) {
    const isMobile = useIsMobile();
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(false);
    const { t } = useTranslation('glue');

    const handleOk = async () => {
        const values = await form.validateFields();
        setLoading(true);
        try {
            await saveGlueCode(jobId, code, values.glueRemark as string);
            message.success(t('saveModal.saved'));
            form.resetFields();
            onClose();
            onSuccess();
        } catch (e: unknown) {
            showError(e);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal
            title={t('saveModal.title')}
            open={open}
            onOk={handleOk}
            onCancel={onClose}
            confirmLoading={loading}
            width={isMobile ? '95vw' : undefined}
            destroyOnClose
        >
            <Form form={form} layout="vertical">
                <Form.Item
                    name="glueRemark"
                    label={t('saveModal.remark')}
                    rules={[
                        { required: true, message: t('saveModal.remarkRequired') },
                        { min: 4, message: t('saveModal.remarkMin') },
                        { max: 100, message: t('saveModal.remarkMax') },
                    ]}
                >
                    <Input placeholder={t('saveModal.remarkPlaceholder')} />
                </Form.Item>
            </Form>
        </Modal>
    );
}
