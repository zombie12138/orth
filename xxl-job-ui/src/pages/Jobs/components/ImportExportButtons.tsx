import { useState } from 'react';
import { Button, Space, Modal, Input, message, Upload } from 'antd';
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
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importJson, setImportJson] = useState('');
  const [importing, setImporting] = useState(false);

  const handleExport = async () => {
    if (selectedIds.length === 0) {
      message.warning('Select jobs to export');
      return;
    }
    try {
      const json = await exportJobs(selectedIds);
      const blob = new Blob([json], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `xxl-job-export-${Date.now()}.json`;
      a.click();
      URL.revokeObjectURL(url);
      message.success('Exported successfully');
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
