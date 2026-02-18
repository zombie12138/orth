import { useEffect, useState, useMemo } from 'react';
import {
  Modal,
  Form,
  Input,
  Select,
  InputNumber,
  message,
  Tabs,
  Button,
  List,
} from 'antd';
import { useMutation } from '@tanstack/react-query';
import debounce from '../../_utils/debounce';
import { createJob, updateJob, nextTriggerTime, searchSuperTask } from '../../../api/jobs';
import { useEnumOptions } from '../../../hooks/useEnums';
import type { XxlJobInfo } from '../../../types/job';
import type { XxlJobGroup } from '../../../types/group';

interface Props {
  open: boolean;
  job: XxlJobInfo | null;
  groups: XxlJobGroup[];
  onClose: () => void;
  onSuccess: () => void;
}

export default function JobFormModal({
  open,
  job,
  groups,
  onClose,
  onSuccess,
}: Props) {
  const [form] = Form.useForm();
  const isEdit = !!job;

  const glueTypeOptions = useEnumOptions('GlueTypeEnum');
  const routeStrategyOptions = useEnumOptions('ExecutorRouteStrategyEnum');
  const blockStrategyOptions = useEnumOptions('ExecutorBlockStrategyEnum');
  const scheduleTypeOptions = useEnumOptions('ScheduleTypeEnum');
  const misfireOptions = useEnumOptions('MisfireStrategyEnum');

  const [scheduleType, setScheduleType] = useState('NONE');
  const [nextTimes, setNextTimes] = useState<string[]>([]);
  const [superTaskOptions, setSuperTaskOptions] = useState<
    { value: number; label: string }[]
  >([]);

  useEffect(() => {
    if (open && job) {
      form.setFieldsValue(job);
      setScheduleType(job.scheduleType);
    } else if (open) {
      form.resetFields();
      setScheduleType('NONE');
      setNextTimes([]);
      setSuperTaskOptions([]);
    }
  }, [open, job, form]);

  const saveMutation = useMutation({
    mutationFn: (values: Partial<XxlJobInfo>) =>
      isEdit ? updateJob(job!.id, values) : createJob(values),
    onSuccess: () => {
      message.success(isEdit ? 'Job updated' : 'Job created');
      onClose();
      onSuccess();
    },
    onError: (e) => message.error(e.message),
  });

  const handleOk = async () => {
    const values = await form.validateFields();
    saveMutation.mutate(values);
  };

  const handlePreviewTrigger = async () => {
    const type = form.getFieldValue('scheduleType') as string;
    const conf = form.getFieldValue('scheduleConf') as string;
    if (!type || !conf) return;
    try {
      const times = await nextTriggerTime(type, conf);
      setNextTimes(times);
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : 'Failed to preview');
    }
  };

  const handleSuperTaskSearch = useMemo(
    () =>
      debounce(async (query: string) => {
        const group = form.getFieldValue('jobGroup') as number;
        if (!group || !query) return;
        try {
          const tasks = await searchSuperTask(group, query);
          setSuperTaskOptions(
            tasks.map((t) => ({
              value: t.id,
              label: `#${t.id} ${t.jobDesc}`,
            })),
          );
        } catch {
          // ignore search errors
        }
      }, 400),
    [form],
  );

  return (
    <Modal
      title={isEdit ? 'Edit Job' : 'Create Job'}
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      confirmLoading={saveMutation.isPending}
      width={700}
      destroyOnClose
    >
      <Form form={form} layout="vertical" initialValues={{
        glueType: 'BEAN',
        scheduleType: 'NONE',
        misfireStrategy: 'DO_NOTHING',
        executorRouteStrategy: 'FIRST',
        executorBlockStrategy: 'SERIAL_EXECUTION',
        executorTimeout: 0,
        executorFailRetryCount: 0,
      }}>
        <Tabs
          items={[
            {
              key: 'basic',
              label: 'Basic',
              children: (
                <>
                  <Form.Item
                    name="jobGroup"
                    label="Executor Group"
                    rules={[{ required: true }]}
                  >
                    <Select
                      options={groups.map((g) => ({
                        value: g.id,
                        label: g.title,
                      }))}
                    />
                  </Form.Item>
                  <Form.Item
                    name="jobDesc"
                    label="Description"
                    rules={[{ required: true }]}
                  >
                    <Input />
                  </Form.Item>
                  <Form.Item
                    name="author"
                    label="Author"
                    rules={[{ required: true }]}
                  >
                    <Input />
                  </Form.Item>
                  <Form.Item name="alarmEmail" label="Alarm Email">
                    <Input placeholder="Comma-separated emails" />
                  </Form.Item>
                </>
              ),
            },
            {
              key: 'schedule',
              label: 'Schedule',
              children: (
                <>
                  <Form.Item
                    name="scheduleType"
                    label="Schedule Type"
                    rules={[{ required: true }]}
                  >
                    <Select
                      options={scheduleTypeOptions}
                      onChange={(v) => setScheduleType(v as string)}
                    />
                  </Form.Item>
                  {scheduleType === 'CRON' && (
                    <Form.Item
                      name="scheduleConf"
                      label="CRON Expression"
                      rules={[{ required: true }]}
                    >
                      <Input
                        addonAfter={
                          <Button size="small" type="link" onClick={handlePreviewTrigger}>
                            Preview Next 5
                          </Button>
                        }
                      />
                    </Form.Item>
                  )}
                  {scheduleType === 'FIX_RATE' && (
                    <Form.Item
                      name="scheduleConf"
                      label="Interval (seconds)"
                      rules={[{ required: true }]}
                    >
                      <InputNumber min={1} style={{ width: '100%' }} />
                    </Form.Item>
                  )}
                  {nextTimes.length > 0 && (
                    <List
                      size="small"
                      header="Next trigger times"
                      dataSource={nextTimes}
                      renderItem={(t) => <List.Item>{t}</List.Item>}
                      style={{ marginBottom: 16 }}
                    />
                  )}
                  <Form.Item
                    name="misfireStrategy"
                    label="Misfire Strategy"
                    rules={[{ required: true }]}
                  >
                    <Select options={misfireOptions} />
                  </Form.Item>
                </>
              ),
            },
            {
              key: 'execution',
              label: 'Execution',
              children: (
                <>
                  <Form.Item
                    name="glueType"
                    label="GLUE Type"
                    rules={[{ required: true }]}
                  >
                    <Select options={glueTypeOptions} />
                  </Form.Item>
                  <Form.Item name="executorHandler" label="Handler">
                    <Input />
                  </Form.Item>
                  <Form.Item name="executorParam" label="Parameters">
                    <Input.TextArea rows={2} />
                  </Form.Item>
                  <Form.Item
                    name="executorRouteStrategy"
                    label="Route Strategy"
                    rules={[{ required: true }]}
                  >
                    <Select options={routeStrategyOptions} />
                  </Form.Item>
                  <Form.Item
                    name="executorBlockStrategy"
                    label="Block Strategy"
                    rules={[{ required: true }]}
                  >
                    <Select options={blockStrategyOptions} />
                  </Form.Item>
                  <Form.Item name="executorTimeout" label="Timeout (s)">
                    <InputNumber min={0} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="executorFailRetryCount" label="Retry Count">
                    <InputNumber min={0} style={{ width: '100%' }} />
                  </Form.Item>
                </>
              ),
            },
            {
              key: 'advanced',
              label: 'Advanced',
              children: (
                <>
                  <Form.Item name="childJobId" label="Child Job IDs">
                    <Input placeholder="Comma-separated job IDs" />
                  </Form.Item>
                  <Form.Item name="superTaskId" label="SuperTask">
                    <Select
                      allowClear
                      showSearch
                      filterOption={false}
                      onSearch={handleSuperTaskSearch}
                      options={superTaskOptions}
                      placeholder="Search by ID or description"
                    />
                  </Form.Item>
                  <Form.Item name="superTaskParam" label="SuperTask Param">
                    <Input />
                  </Form.Item>
                </>
              ),
            },
          ]}
        />
      </Form>
    </Modal>
  );
}
