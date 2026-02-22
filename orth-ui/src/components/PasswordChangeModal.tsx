import { useState } from 'react';
import { Modal, Form, Input, message } from 'antd';
import { useTranslation } from 'react-i18next';
import { updatePassword } from '../api/auth';
import { useIsMobile } from '../hooks/useIsMobile';

interface Props {
    open: boolean;
    onClose: () => void;
}

export default function PasswordChangeModal({ open, onClose }: Props) {
    const isMobile = useIsMobile();
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(false);
    const { t } = useTranslation('user');

    const handleOk = async () => {
        const values = await form.validateFields();
        setLoading(true);
        try {
            await updatePassword({
                oldPassword: values.oldPassword as string,
                newPassword: values.newPassword as string,
            });
            message.success(t('passwordChange.updated'));
            form.resetFields();
            onClose();
        } catch (e: unknown) {
            const msg =
                e instanceof Error ? e.message : t('passwordChange.updateFailed');
            form.setFields([{ name: 'oldPassword', errors: [msg] }]);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal
            title={t('passwordChange.title')}
            open={open}
            onOk={handleOk}
            onCancel={onClose}
            confirmLoading={loading}
            width={isMobile ? '95vw' : undefined}
            destroyOnClose
        >
            <Form form={form} layout="vertical">
                <Form.Item
                    name="oldPassword"
                    label={t('passwordChange.currentPassword')}
                    rules={[
                        {
                            required: true,
                            message: t('passwordChange.currentRequired'),
                        },
                    ]}
                >
                    <Input.Password />
                </Form.Item>
                <Form.Item
                    name="newPassword"
                    label={t('passwordChange.newPassword')}
                    rules={[
                        {
                            required: true,
                            message: t('passwordChange.newRequired'),
                        },
                        { min: 4, message: t('passwordChange.minLength') },
                        { max: 20, message: t('passwordChange.maxLength') },
                    ]}
                >
                    <Input.Password />
                </Form.Item>
                <Form.Item
                    name="confirmPassword"
                    label={t('passwordChange.confirmPassword')}
                    dependencies={['newPassword']}
                    rules={[
                        {
                            required: true,
                            message: t('passwordChange.confirmRequired'),
                        },
                        ({ getFieldValue }) => ({
                            validator(_, value) {
                                if (!value || getFieldValue('newPassword') === value) {
                                    return Promise.resolve();
                                }
                                return Promise.reject(
                                    new Error(t('passwordChange.mismatch')),
                                );
                            },
                        }),
                    ]}
                >
                    <Input.Password />
                </Form.Item>
            </Form>
        </Modal>
    );
}
