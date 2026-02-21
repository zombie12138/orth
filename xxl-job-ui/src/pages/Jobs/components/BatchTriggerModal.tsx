import { useState } from 'react';
import { Modal, Form, Input, DatePicker, Button, List, message } from 'antd';
import dayjs from 'dayjs';
import { useTranslation } from 'react-i18next';
import { triggerBatch, previewTriggerBatch } from '../../../api/jobs';
import { DATE_FORMAT } from '../../../utils/date';
import { useIsMobile } from '../../../hooks/useIsMobile';

interface Props {
    open: boolean;
    jobId: number;
    onClose: () => void;
}

export default function BatchTriggerModal({ open, jobId, onClose }: Props) {
    const isMobile = useIsMobile();
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(false);
    const [preview, setPreview] = useState<string[]>([]);
    const { t } = useTranslation('job');

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
            message.error(
                e instanceof Error ? e.message : t('batchTrigger.messages.previewFailed'),
            );
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
            message.success(t('batchTrigger.messages.submitted'));
            form.resetFields();
            setPreview([]);
            onClose();
        } catch (e: unknown) {
            message.error(
                e instanceof Error ? e.message : t('batchTrigger.messages.triggerFailed'),
            );
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal
            title={t('batchTrigger.title')}
            open={open}
            onOk={handleOk}
            onCancel={() => {
                setPreview([]);
                onClose();
            }}
            confirmLoading={loading}
            width={isMobile ? '95vw' : 600}
            destroyOnClose
        >
            <Form form={form} layout="vertical">
                <Form.Item
                    name="timeRange"
                    label={t('batchTrigger.timeRange')}
                    rules={[
                        { required: true, message: t('batchTrigger.timeRangeRequired') },
                    ]}
                >
                    <DatePicker.RangePicker showTime style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item>
                    <Button onClick={handlePreview}>
                        {t('batchTrigger.previewButton')}
                    </Button>
                </Form.Item>
                {preview.length > 0 && (
                    <List
                        size="small"
                        header={t('batchTrigger.triggerTimesFound', {
                            count: preview.length,
                        })}
                        dataSource={preview}
                        renderItem={(item) => <List.Item>{item}</List.Item>}
                        style={{ marginBottom: 16, maxHeight: 200, overflow: 'auto' }}
                    />
                )}
                <Form.Item
                    name="executorParam"
                    label={t('batchTrigger.executorParams')}
                >
                    <Input.TextArea rows={2} placeholder={t('batchTrigger.optional')} />
                </Form.Item>
                <Form.Item
                    name="addressList"
                    label={t('batchTrigger.addressOverride')}
                >
                    <Input placeholder={t('batchTrigger.optional')} />
                </Form.Item>
            </Form>
        </Modal>
    );
}
