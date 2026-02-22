import { useEffect } from 'react';
import { Modal, Form, Input, Radio, message } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { createGroup, updateGroup } from '../../../api/groups';
import { useIsMobile } from '../../../hooks/useIsMobile';
import type { JobGroup } from '../../../types/group';

interface Props {
    open: boolean;
    group: JobGroup | null;
    onClose: () => void;
    onSuccess: () => void;
}

export default function GroupFormModal({ open, group, onClose, onSuccess }: Props) {
    const isMobile = useIsMobile();
    const [form] = Form.useForm();
    const isEdit = !!group;
    const addressType = Form.useWatch('addressType', form);
    const { t } = useTranslation('executor');
    const { t: tc } = useTranslation('common');

    useEffect(() => {
        if (open && group) {
            form.setFieldsValue(group);
        } else if (open) {
            form.resetFields();
        }
    }, [open, group, form]);

    const saveMutation = useMutation({
        mutationFn: (values: Partial<JobGroup>) =>
            isEdit ? updateGroup(group!.id, values) : createGroup(values),
        onSuccess: () => {
            message.success(
                isEdit ? t('messages.groupUpdated') : t('messages.groupCreated'),
            );
            onClose();
            onSuccess();
        },
        onError: (e) => message.error(e.message),
    });

    const handleOk = async () => {
        const values = await form.validateFields();
        saveMutation.mutate(values);
    };

    return (
        <Modal
            title={isEdit ? t('editGroup') : t('createGroup')}
            open={open}
            onOk={handleOk}
            onCancel={onClose}
            confirmLoading={saveMutation.isPending}
            width={isMobile ? '95vw' : undefined}
            destroyOnClose
        >
            <Form form={form} layout="vertical" initialValues={{ addressType: 0 }}>
                <Form.Item
                    name="appname"
                    label={t('form.appName')}
                    rules={[
                        { required: true },
                        { min: 4, message: tc('minChars', { count: 4 }) },
                        { max: 64, message: tc('maxChars', { count: 64 }) },
                    ]}
                >
                    <Input disabled={isEdit} />
                </Form.Item>
                <Form.Item
                    name="title"
                    label={t('form.title')}
                    rules={[{ required: true }]}
                >
                    <Input />
                </Form.Item>
                <Form.Item name="addressType" label={t('form.addressType')}>
                    <Radio.Group>
                        <Radio value={0}>{t('form.autoDiscovery')}</Radio>
                        <Radio value={1}>{t('form.manual')}</Radio>
                    </Radio.Group>
                </Form.Item>
                {addressType === 1 && (
                    <Form.Item
                        name="addressList"
                        label={t('form.addresses')}
                        rules={[
                            {
                                required: true,
                                message: t('form.addressesRequired'),
                            },
                        ]}
                    >
                        <Input.TextArea
                            rows={3}
                            placeholder={t('form.addressesPlaceholder')}
                        />
                    </Form.Item>
                )}
            </Form>
        </Modal>
    );
}
