import { useState } from 'react';
import { useParams, useNavigate } from 'react-router';
import { Card, Spin, Button, Space, Row, Col } from 'antd';
import { ArrowLeftOutlined, SaveOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchGlueCode } from '../../api/glue';
import CodeEditor from './components/CodeEditor';
import VersionHistory from './components/VersionHistory';
import SaveCodeModal from './components/SaveCodeModal';

export default function GlueCodePage() {
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const id = Number(jobId);

  const { data, isLoading } = useQuery({
    queryKey: ['glue-code', id],
    queryFn: () => fetchGlueCode(id),
    enabled: id > 0,
  });

  const [code, setCode] = useState('');
  const [initialized, setInitialized] = useState(false);
  const [saveModalOpen, setSaveModalOpen] = useState(false);

  // Initialize code from fetched data
  if (data && !initialized) {
    setCode(data.jobInfo.glueSource ?? '');
    setInitialized(true);
  }

  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!data) {
    return <Card>Job not found</Card>;
  }

  const glueType = data.jobInfo.glueType;

  return (
    <>
      <Card
        title={
          <Space>
            <Button
              icon={<ArrowLeftOutlined />}
              type="text"
              onClick={() => navigate('/jobs')}
            />
            <span>
              GLUE Code - #{data.jobInfo.id} {data.jobInfo.jobDesc}
            </span>
          </Space>
        }
        extra={
          <Button
            type="primary"
            icon={<SaveOutlined />}
            onClick={() => setSaveModalOpen(true)}
          >
            Save
          </Button>
        }
        bodyStyle={{ padding: 0 }}
      >
        <Row>
          <Col flex="1" style={{ minWidth: 0 }}>
            <CodeEditor
              value={code}
              onChange={setCode}
              glueType={glueType}
            />
          </Col>
          <Col
            flex="280px"
            style={{ borderLeft: '1px solid #f0f0f0' }}
          >
            <VersionHistory
              versions={data.jobLogGlues}
              onRestore={(source) => setCode(source)}
            />
          </Col>
        </Row>
      </Card>

      <SaveCodeModal
        open={saveModalOpen}
        jobId={id}
        code={code}
        onClose={() => setSaveModalOpen(false)}
        onSuccess={() => {
          queryClient.invalidateQueries({ queryKey: ['glue-code', id] });
          setInitialized(false);
        }}
      />
    </>
  );
}
