import { useEffect, useState, useMemo } from 'react';
import { Modal, Form, Input, Select, InputNumber, message, Tabs, List } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { showError } from '../../../api/client';
import { useTranslation } from 'react-i18next';
import debounce from '../../_utils/debounce';
import { createJob, updateJob, nextTriggerTime, searchSuperTask } from '../../../api/jobs';
import { useEnumOptions } from '../../../hooks/useEnums';
import { useIsMobile } from '../../../hooks/useIsMobile';
import CronInput from './CronInput';
import { validateQuartzCron } from '../../../utils/cronValidator';
import type { JobInfo } from '../../../types/job';
import type { JobGroup } from '../../../types/group';

interface Props {
    open: boolean;
    job: JobInfo | null;
    groups: JobGroup[];
    onClose: () => void;
    onSuccess: () => void;
}

export default function JobFormModal({ open, job, groups, onClose, onSuccess }: Props) {
    const isMobile = useIsMobile();
    const [form] = Form.useForm();
    const isEdit = job != null && job.id > 0;
    const { t } = useTranslation('job');

    const glueTypeOptions = useEnumOptions('GlueTypeEnum');
    const routeStrategyOptions = useEnumOptions('ExecutorRouteStrategyEnum');
    const blockStrategyOptions = useEnumOptions('ExecutorBlockStrategyEnum');
    const scheduleTypeOptions = useEnumOptions('ScheduleTypeEnum');
    const misfireOptions = useEnumOptions('MisfireStrategyEnum');

    const [scheduleType, setScheduleType] = useState('NONE');
    const [blockStrategy, setBlockStrategy] = useState('SERIAL_EXECUTION');
    const [nextTimes, setNextTimes] = useState<string[]>([]);
    const [superTaskOptions, setSuperTaskOptions] = useState<
        { value: number; label: string }[]
    >([]);

    useEffect(() => {
        if (open && job) {
            form.setFieldsValue(job);
            setScheduleType(job.scheduleType);
            setBlockStrategy(job.executorBlockStrategy || 'SERIAL_EXECUTION');
            if (job.superTaskId && job.superTaskId > 0) {
                setSuperTaskOptions([
                    {
                        value: job.superTaskId,
                        label: `#${job.superTaskId} ${job.superTaskName || ''}`,
                    },
                ]);
            } else {
                setSuperTaskOptions([]);
            }
        } else if (open) {
            form.resetFields();
            setScheduleType('NONE');
            setBlockStrategy('SERIAL_EXECUTION');
            setNextTimes([]);
            setSuperTaskOptions([]);
        }
    }, [open, job, form]);

    const saveMutation = useMutation({
        mutationFn: (values: Partial<JobInfo>) =>
            isEdit ? updateJob(job!.id, values) : createJob(values),
        onSuccess: () => {
            message.success(isEdit ? t('messages.jobUpdated') : t('messages.jobCreated'));
            onClose();
            onSuccess();
        },
        onError: (e) => showError(e),
    });

    const handleOk = async () => {
        const values = await form.validateFields();
        // InputNumber returns number; backend expects string for scheduleConf
        if (values.scheduleConf != null) {
            values.scheduleConf = String(values.scheduleConf);
        }
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
            showError(e);
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
                        tasks.map((tsk) => ({
                            value: tsk.id,
                            label: `#${tsk.id} ${tsk.jobDesc}`,
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
            title={isEdit ? t('editJob') : t('createJob')}
            open={open}
            onOk={handleOk}
            onCancel={onClose}
            confirmLoading={saveMutation.isPending}
            width={isMobile ? '95vw' : 700}
            destroyOnClose
        >
            <Form
                form={form}
                layout="vertical"
                initialValues={{
                    glueType: 'BEAN',
                    scheduleType: 'NONE',
                    misfireStrategy: 'DO_NOTHING',
                    executorRouteStrategy: 'FIRST',
                    executorBlockStrategy: 'SERIAL_EXECUTION',
                    executorConcurrency: 1,
                    executorTimeout: 0,
                    executorFailRetryCount: 0,
                }}
            >
                <Tabs
                    items={[
                        {
                            key: 'basic',
                            label: t('form.tabs.basic'),
                            forceRender: true,
                            children: (
                                <>
                                    <Form.Item
                                        name="jobGroup"
                                        label={t('form.labels.executorGroup')}
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
                                        label={t('form.labels.description')}
                                        rules={[{ required: true }]}
                                    >
                                        <Input />
                                    </Form.Item>
                                    <Form.Item
                                        name="author"
                                        label={t('form.labels.author')}
                                        rules={[{ required: true }]}
                                    >
                                        <Input />
                                    </Form.Item>
                                    <Form.Item
                                        name="alarmEmail"
                                        label={t('form.labels.alarmEmail')}
                                    >
                                        <Input
                                            placeholder={t(
                                                'form.labels.alarmEmailPlaceholder',
                                            )}
                                        />
                                    </Form.Item>
                                </>
                            ),
                        },
                        {
                            key: 'schedule',
                            label: t('form.tabs.schedule'),
                            forceRender: true,
                            children: (
                                <>
                                    <Form.Item
                                        name="scheduleType"
                                        label={t('form.labels.scheduleType')}
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
                                            label={t('form.labels.cronExpression')}
                                            rules={[
                                                { required: true },
                                                {
                                                    validator: (_, val) => {
                                                        if (!val) return Promise.resolve();
                                                        const err = validateQuartzCron(val);
                                                        if (err)
                                                            return Promise.reject(
                                                                t(
                                                                    `form.cron.validation.${err}`,
                                                                ),
                                                            );
                                                        return Promise.resolve();
                                                    },
                                                },
                                            ]}
                                        >
                                            <CronInput
                                                onPreview={handlePreviewTrigger}
                                            />
                                        </Form.Item>
                                    )}
                                    {scheduleType === 'FIX_RATE' && (
                                        <Form.Item
                                            name="scheduleConf"
                                            label={t('form.labels.intervalSeconds')}
                                            rules={[{ required: true }]}
                                        >
                                            <InputNumber
                                                min={1}
                                                style={{ width: '100%' }}
                                            />
                                        </Form.Item>
                                    )}
                                    {nextTimes.length > 0 && (
                                        <List
                                            size="small"
                                            header={t('form.labels.nextTriggerTimes')}
                                            dataSource={nextTimes}
                                            renderItem={(item) => (
                                                <List.Item>{item}</List.Item>
                                            )}
                                            style={{ marginBottom: 16 }}
                                        />
                                    )}
                                    <Form.Item
                                        name="misfireStrategy"
                                        label={t('form.labels.misfireStrategy')}
                                        rules={[{ required: true }]}
                                    >
                                        <Select options={misfireOptions} />
                                    </Form.Item>
                                    <Form.Item
                                        name="executorBlockStrategy"
                                        label={t('form.labels.blockStrategy')}
                                        rules={[{ required: true }]}
                                    >
                                        <Select
                                            options={blockStrategyOptions}
                                            onChange={(v) => setBlockStrategy(v as string)}
                                        />
                                    </Form.Item>
                                    {blockStrategy === 'CONCURRENT' && (
                                        <Form.Item
                                            name="executorConcurrency"
                                            label={t('form.labels.concurrency')}
                                            rules={[{ required: true }]}
                                        >
                                            <InputNumber
                                                min={1}
                                                max={64}
                                                style={{ width: '100%' }}
                                            />
                                        </Form.Item>
                                    )}
                                </>
                            ),
                        },
                        {
                            key: 'execution',
                            label: t('form.tabs.execution'),
                            forceRender: true,
                            children: (
                                <>
                                    <Form.Item
                                        name="glueType"
                                        label={t('form.labels.glueType')}
                                        rules={[{ required: true }]}
                                    >
                                        <Select options={glueTypeOptions} />
                                    </Form.Item>
                                    <Form.Item
                                        name="executorHandler"
                                        label={t('form.labels.handler')}
                                    >
                                        <Input />
                                    </Form.Item>
                                    <Form.Item
                                        name="executorParam"
                                        label={t('form.labels.parameters')}
                                    >
                                        <Input.TextArea rows={2} />
                                    </Form.Item>
                                    <Form.Item
                                        name="executorRouteStrategy"
                                        label={t('form.labels.routeStrategy')}
                                        rules={[{ required: true }]}
                                    >
                                        <Select options={routeStrategyOptions} />
                                    </Form.Item>
                                    <Form.Item
                                        name="executorTimeout"
                                        label={t('form.labels.timeoutSeconds')}
                                    >
                                        <InputNumber
                                            min={0}
                                            style={{ width: '100%' }}
                                        />
                                    </Form.Item>
                                    <Form.Item
                                        name="executorFailRetryCount"
                                        label={t('form.labels.retryCount')}
                                    >
                                        <InputNumber
                                            min={0}
                                            style={{ width: '100%' }}
                                        />
                                    </Form.Item>
                                </>
                            ),
                        },
                        {
                            key: 'advanced',
                            label: t('form.tabs.advanced'),
                            forceRender: true,
                            children: (
                                <>
                                    <Form.Item
                                        name="childJobId"
                                        label={t('form.labels.childJobIds')}
                                    >
                                        <Input
                                            placeholder={t(
                                                'form.labels.childJobIdsPlaceholder',
                                            )}
                                        />
                                    </Form.Item>
                                    <Form.Item
                                        name="superTaskId"
                                        label={t('form.labels.superTask')}
                                    >
                                        <Select
                                            allowClear
                                            showSearch
                                            filterOption={false}
                                            onSearch={handleSuperTaskSearch}
                                            options={superTaskOptions}
                                            placeholder={t(
                                                'form.labels.superTaskPlaceholder',
                                            )}
                                        />
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
