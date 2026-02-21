import { List, Button, Typography, Tooltip } from 'antd';
import { HistoryOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { formatDate } from '../../../utils/date';
import type { XxlJobLogGlue } from '../../../types/glue';

const { Text } = Typography;

interface Props {
    versions: XxlJobLogGlue[];
    onRestore: (source: string) => void;
    maxHeight?: string;
}

export default function VersionHistory({ versions, onRestore, maxHeight }: Props) {
    const { t } = useTranslation('glue');

    return (
        <div style={{ padding: 16 }}>
            <Text strong style={{ display: 'block', marginBottom: 12 }}>
                <HistoryOutlined />{' '}
                {t('versionHistory.title', { count: versions.length })}
            </Text>
            <List
                size="small"
                dataSource={versions}
                style={{
                    maxHeight: maxHeight ?? 'calc(100vh - 260px)',
                    overflow: 'auto',
                }}
                renderItem={(item) => (
                    <List.Item
                        actions={[
                            <Tooltip
                                title={t('versionHistory.restoreTooltip')}
                                key="restore"
                            >
                                <Button
                                    type="link"
                                    size="small"
                                    onClick={() => onRestore(item.glueSource)}
                                >
                                    {t('versionHistory.restore')}
                                </Button>
                            </Tooltip>,
                        ]}
                    >
                        <List.Item.Meta
                            title={item.glueRemark}
                            description={formatDate(item.addTime)}
                        />
                    </List.Item>
                )}
            />
        </div>
    );
}
