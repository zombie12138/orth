import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router';
import { Card, Spin, Button, Space, Row, Col, Alert, theme } from 'antd';
import { ArrowLeftOutlined, SaveOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchGlueCode } from '../../api/glue';
import { useIsMobile } from '../../hooks/useIsMobile';
import CodeEditor from './components/CodeEditor';
import VersionHistory from './components/VersionHistory';
import SaveCodeModal from './components/SaveCodeModal';

export default function GlueCodePage() {
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { token } = theme.useToken();
  const isMobile = useIsMobile();
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
  const isSubTask = data.jobInfo.superTaskId != null && data.jobInfo.superTaskId > 0;

  return (
    <>
      {isSubTask && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={
            <span>
              This is a sub-task. Edit code on the SuperTask instead.{' '}
              <Link to={`/jobs/${data.jobInfo.superTaskId}/code`}>
                Go to SuperTask #{data.jobInfo.superTaskId}
              </Link>
            </span>
          }
        />
      )}
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
            disabled={isSubTask}
          >
            Save
          </Button>
        }
        bodyStyle={{ padding: 0 }}
      >
        <Row style={isMobile ? { flexDirection: 'column' } : undefined}>
          <Col flex={isMobile ? 'auto' : '1'} style={{ minWidth: 0 }}>
            <CodeEditor
              value={code}
              onChange={isSubTask ? () => {} : setCode}
              glueType={glueType}
              readOnly={isSubTask}
              height={isMobile ? '50vh' : undefined}
            />
          </Col>
          <Col
            flex={isMobile ? 'auto' : '280px'}
            style={isMobile
              ? { borderTop: `1px solid ${token.colorBorderSecondary}` }
              : { borderLeft: `1px solid ${token.colorBorderSecondary}` }
            }
          >
            <VersionHistory
              versions={data.jobLogGlues}
              onRestore={(source) => setCode(source)}
              maxHeight={isMobile ? '40vh' : undefined}
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
