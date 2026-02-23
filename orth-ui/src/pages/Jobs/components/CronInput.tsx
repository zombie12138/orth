import { useMemo } from 'react';
import { Input, Select, Typography, Button, Space } from 'antd';
import { useTranslation } from 'react-i18next';
import cronstrue from 'cronstrue/i18n';
import { groupPresets } from '../../../utils/cronPresets';

const { Text } = Typography;

interface CronInputProps {
    value?: string;
    onChange?: (value: string) => void;
    onPreview?: () => void;
}

/**
 * CRON expression input with preset dropdown, human-readable description,
 * and format hint. Designed as a controlled component for Ant Design Form.Item.
 */
export default function CronInput({ value, onChange, onPreview }: CronInputProps) {
    const { t, i18n } = useTranslation('job');

    const presetOptions = useMemo(() => {
        const grouped = groupPresets();
        return Array.from(grouped.entries()).map(([category, presets]) => ({
            label: t(`form.cron.categories.${category}`),
            options: presets.map((p) => ({
                label: `${t(`form.cron.presets.${p.labelKey}`)}  (${p.expression})`,
                value: p.expression,
            })),
        }));
    }, [t]);

    const description = useMemo(() => {
        if (!value?.trim()) return null;
        try {
            return cronstrue.toString(value, {
                locale: i18n.language === 'zh' ? 'zh_CN' : 'en',
                use24HourTimeFormat: true,
            });
        } catch {
            return null;
        }
    }, [value, i18n.language]);

    return (
        <Space direction="vertical" style={{ width: '100%' }} size={4}>
            <Select
                placeholder={t('form.cron.selectPreset')}
                options={presetOptions}
                value={null as unknown as string}
                onChange={(v) => onChange?.(v)}
                allowClear={false}
                style={{ width: '100%' }}
                popupMatchSelectWidth={false}
            />
            <Input
                value={value}
                onChange={(e) => onChange?.(e.target.value)}
                placeholder="0 0/5 * * * ?"
                addonAfter={
                    <Button size="small" type="link" onClick={onPreview}>
                        {t('form.labels.previewNext5')}
                    </Button>
                }
            />
            <Text type="secondary" style={{ fontSize: 12 }}>
                {t('form.cron.formatHint')}
            </Text>
            {description && (
                <Text type="success" style={{ fontSize: 12 }}>
                    {description}
                </Text>
            )}
        </Space>
    );
}
