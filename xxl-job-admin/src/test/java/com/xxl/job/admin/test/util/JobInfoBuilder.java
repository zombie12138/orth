package com.xxl.job.admin.test.util;

import java.util.Date;

import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.scheduler.route.ExecutorRouteStrategyEnum;
import com.xxl.job.core.constant.ExecutorBlockStrategyEnum;
import com.xxl.job.core.glue.GlueTypeEnum;

/**
 * Builder for creating XxlJobInfo test data.
 *
 * <p>Provides fluent API for constructing job info objects with sensible defaults.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * XxlJobInfo job = JobInfoBuilder.aJob()
 *     .withJobGroup(1)
 *     .withJobDesc("Test Job")
 *     .withScheduleType("CRON")
 *     .withScheduleConf("0 0 * * * ?")
 *     .build();
 * }</pre>
 */
public class JobInfoBuilder {

    private final XxlJobInfo jobInfo;

    private JobInfoBuilder() {
        this.jobInfo = new XxlJobInfo();
        // Set sensible defaults
        this.jobInfo.setJobGroup(1);
        this.jobInfo.setJobDesc("Test Job");
        this.jobInfo.setAuthor("test");
        this.jobInfo.setScheduleType("CRON");
        this.jobInfo.setScheduleConf("0 0 * * * ?");
        this.jobInfo.setGlueType(GlueTypeEnum.BEAN.getDesc());
        this.jobInfo.setExecutorHandler("testJobHandler");
        this.jobInfo.setExecutorParam("");
        this.jobInfo.setExecutorRouteStrategy(ExecutorRouteStrategyEnum.FIRST.name());
        this.jobInfo.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.name());
        this.jobInfo.setExecutorTimeout(0);
        this.jobInfo.setExecutorFailRetryCount(0);
        this.jobInfo.setGlueRemark("GLUE代码初始化");
        this.jobInfo.setTriggerStatus(1); // Started
        this.jobInfo.setAddTime(new Date());
        this.jobInfo.setUpdateTime(new Date());
    }

    public static JobInfoBuilder aJob() {
        return new JobInfoBuilder();
    }

    public JobInfoBuilder withId(int id) {
        jobInfo.setId(id);
        return this;
    }

    public JobInfoBuilder withJobGroup(int jobGroup) {
        jobInfo.setJobGroup(jobGroup);
        return this;
    }

    public JobInfoBuilder withJobDesc(String jobDesc) {
        jobInfo.setJobDesc(jobDesc);
        return this;
    }

    public JobInfoBuilder withAuthor(String author) {
        jobInfo.setAuthor(author);
        return this;
    }

    public JobInfoBuilder withScheduleType(String scheduleType) {
        jobInfo.setScheduleType(scheduleType);
        return this;
    }

    public JobInfoBuilder withScheduleConf(String scheduleConf) {
        jobInfo.setScheduleConf(scheduleConf);
        return this;
    }

    public JobInfoBuilder withGlueType(String glueType) {
        jobInfo.setGlueType(glueType);
        return this;
    }

    public JobInfoBuilder withExecutorHandler(String executorHandler) {
        jobInfo.setExecutorHandler(executorHandler);
        return this;
    }

    public JobInfoBuilder withExecutorParam(String executorParam) {
        jobInfo.setExecutorParam(executorParam);
        return this;
    }

    public JobInfoBuilder withExecutorRouteStrategy(String routeStrategy) {
        jobInfo.setExecutorRouteStrategy(routeStrategy);
        return this;
    }

    public JobInfoBuilder withExecutorBlockStrategy(String blockStrategy) {
        jobInfo.setExecutorBlockStrategy(blockStrategy);
        return this;
    }

    public JobInfoBuilder withExecutorTimeout(int timeout) {
        jobInfo.setExecutorTimeout(timeout);
        return this;
    }

    public JobInfoBuilder withExecutorFailRetryCount(int retryCount) {
        jobInfo.setExecutorFailRetryCount(retryCount);
        return this;
    }

    public JobInfoBuilder withTriggerStatus(int triggerStatus) {
        jobInfo.setTriggerStatus(triggerStatus);
        return this;
    }

    public JobInfoBuilder withTriggerLastTime(long triggerLastTime) {
        jobInfo.setTriggerLastTime(triggerLastTime);
        return this;
    }

    public JobInfoBuilder withTriggerNextTime(long triggerNextTime) {
        jobInfo.setTriggerNextTime(triggerNextTime);
        return this;
    }

    public JobInfoBuilder withMisfireStrategy(String misfireStrategy) {
        jobInfo.setMisfireStrategy(misfireStrategy);
        return this;
    }

    public XxlJobInfo build() {
        return jobInfo;
    }
}
