import { useEffect, useRef, useState } from 'react';
import { Drawer, Spin } from 'antd';
import { useTranslation } from 'react-i18next';
import { fetchLogContent } from '../../../api/logs';
import { useIsMobile } from '../../../hooks/useIsMobile';

interface Props {
    logId: number | null;
    onClose: () => void;
}

export default function LogDrawer({ logId, onClose }: Props) {
    const isMobile = useIsMobile();
    const { t } = useTranslation('log');
    const [content, setContent] = useState('');
    const [loading, setLoading] = useState(false);
    const fromLineRef = useRef(1);
    const timerRef = useRef<ReturnType<typeof setInterval>>(undefined);
    const preRef = useRef<HTMLPreElement>(null);

    useEffect(() => {
        if (!logId) {
            setContent('');
            fromLineRef.current = 1;
            return;
        }

        setContent('');
        fromLineRef.current = 1;
        setLoading(true);

        const poll = async () => {
            try {
                const result = await fetchLogContent(logId, fromLineRef.current);
                if (result.logContent) {
                    setContent((prev) => prev + result.logContent);
                }
                fromLineRef.current = result.toLineNum + 1;
                setLoading(false);

                // Auto-scroll to bottom
                if (preRef.current) {
                    preRef.current.scrollTop = preRef.current.scrollHeight;
                }

                if (result.isEnd) {
                    clearInterval(timerRef.current);
                }
            } catch {
                setLoading(false);
                clearInterval(timerRef.current);
            }
        };

        poll();
        timerRef.current = setInterval(poll, 3000);

        return () => {
            clearInterval(timerRef.current);
        };
    }, [logId]);

    return (
        <Drawer
            title={t('drawer.title', { id: logId ?? '' })}
            open={logId !== null}
            onClose={onClose}
            width={isMobile ? '100%' : 700}
            destroyOnClose
        >
            {loading && !content && (
                <div style={{ textAlign: 'center', padding: 40 }}>
                    <Spin />
                </div>
            )}
            <pre
                ref={preRef}
                style={{
                    background: '#1e1e1e',
                    color: '#d4d4d4',
                    padding: 16,
                    borderRadius: 4,
                    fontSize: 12,
                    lineHeight: 1.6,
                    maxHeight: 'calc(100vh - 150px)',
                    overflow: 'auto',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-all',
                }}
            >
                {content || (loading ? '' : t('drawer.noContent'))}
            </pre>
        </Drawer>
    );
}
