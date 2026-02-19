import { useState } from 'react';
import { App, Button, Space, Modal, Input, Upload } from 'antd';
import { ExportOutlined, ImportOutlined } from '@ant-design/icons';
import { exportJobs, importJobs } from '../../../api/jobs';

interface Props {
  selectedIds: number[];
  onImportSuccess: () => void;
}

export default function ImportExportButtons({
  selectedIds,
  onImportSuccess,
}: Props) {
  const { message } = App.useApp();
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
      message.warning('Select jobs to export');
      return;
    }
    try {
      const json = await exportJobs(selectedIds);
      if (copyToClipboard(json)) {
        message.success(
          <span>
            Copied to clipboard{' '}
            <a onClick={() => downloadJson(json)}>Download</a>
          </span>,
          5,
        );
      } else {
        downloadJson(json);
        message.success('Exported successfully');
      }
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : 'Export failed');
    }
  };

  const handleImport = async () => {
    if (!importJson.trim()) {
      message.warning('Paste or upload job JSON');
      return;
    }
    setImporting(true);
    try {
      await importJobs(importJson);
      message.success('Imported successfully');
      setImportJson('');
      setImportModalOpen(false);
      onImportSuccess();
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : 'Import failed');
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
          Export
        </Button>
        <Button
          icon={<ImportOutlined />}
          onClick={() => setImportModalOpen(true)}
        >
          Import
        </Button>
      </Space>
      <Modal
        title="Import Jobs"
        open={importModalOpen}
        onOk={handleImport}
        onCancel={() => setImportModalOpen(false)}
        confirmLoading={importing}
      >
        <Upload.Dragger
          accept=".json"
          showUploadList={false}
          beforeUpload={handleFileUpload}
          style={{ marginBottom: 16 }}
        >
          <p>Click or drag JSON file here</p>
        </Upload.Dragger>
        <Input.TextArea
          rows={8}
          value={importJson}
          onChange={(e) => setImportJson(e.target.value)}
          placeholder="Or paste JSON here..."
        />
      </Modal>
    </>
  );
}
