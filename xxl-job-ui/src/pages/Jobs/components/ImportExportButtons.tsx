import { useState } from 'react';
import { App, Button, Space, Modal, Input, Upload } from 'antd';
import { ExportOutlined, ImportOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { exportJobs, importJobs } from '../../../api/jobs';
import { useIsMobile } from '../../../hooks/useIsMobile';

interface Props {
    selectedIds: number[];
    onImportSuccess: () => void;
}

export default function ImportExportButtons({ selectedIds, onImportSuccess }: Props) {
    const { message } = App.useApp();
    const isMobile = useIsMobile();
    const { t } = useTranslation('job');
    const { t: tc } = useTranslation('common');
    const [importModalOpen, setImportModalOpen] = useState(false);
    const [importJson, setImportJson] = useState('');
    const [importing, setImporting] = useState(false);

    const downloadJson = (json: string) => {
        const blob = new Blob([json], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `xxl-job-export-${Date.now()}.json`;
        a.click();
        URL.revokeObjectURL(url);
    };

    const copyToClipboard = (text: string): boolean => {
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        const ok = document.execCommand('copy');
        document.body.removeChild(textarea);
        return ok;
    };

    const handleExport = async () => {
        if (selectedIds.length === 0) {
            message.warning(t('importExport.selectToExport'));
            return;
        }
        try {
            const json = await exportJobs(selectedIds);
            if (copyToClipboard(json)) {
                message.success(
                    <span>
                        {t('importExport.copiedToClipboard')}{' '}
                        <a onClick={() => downloadJson(json)}>
                            {t('importExport.download')}
                        </a>
                    </span>,
                    5,
                );
            } else {
                downloadJson(json);
                message.success(t('importExport.exportedSuccess'));
            }
        } catch (e: unknown) {
            message.error(
                e instanceof Error ? e.message : t('importExport.exportFailed'),
            );
        }
    };

    const handleImport = async () => {
        if (!importJson.trim()) {
            message.warning(t('importExport.pasteOrUpload'));
            return;
        }
        setImporting(true);
        try {
            await importJobs(importJson);
            message.success(t('importExport.importedSuccess'));
            setImportJson('');
            setImportModalOpen(false);
            onImportSuccess();
        } catch (e: unknown) {
            message.error(
                e instanceof Error ? e.message : t('importExport.importFailed'),
            );
        } finally {
            setImporting(false);
        }
    };

    const handleFileUpload = (file: File) => {
        const reader = new FileReader();
        reader.onload = (e) => {
            setImportJson(e.target?.result as string);
        };
        reader.readAsText(file);
        return false; // prevent auto upload
    };

    return (
        <>
            <Space>
                <Button icon={<ExportOutlined />} onClick={handleExport}>
                    {tc('export')}
                </Button>
                <Button
                    icon={<ImportOutlined />}
                    onClick={() => setImportModalOpen(true)}
                >
                    {tc('import')}
                </Button>
            </Space>
            <Modal
                title={t('importExport.importTitle')}
                open={importModalOpen}
                onOk={handleImport}
                onCancel={() => setImportModalOpen(false)}
                confirmLoading={importing}
                width={isMobile ? '95vw' : undefined}
            >
                <Upload.Dragger
                    accept=".json"
                    showUploadList={false}
                    beforeUpload={handleFileUpload}
                    style={{ marginBottom: 16 }}
                >
                    <p>{t('importExport.dragFile')}</p>
                </Upload.Dragger>
                <Input.TextArea
                    rows={8}
                    value={importJson}
                    onChange={(e) => setImportJson(e.target.value)}
                    placeholder={t('importExport.pasteJson')}
                />
            </Modal>
        </>
    );
}
