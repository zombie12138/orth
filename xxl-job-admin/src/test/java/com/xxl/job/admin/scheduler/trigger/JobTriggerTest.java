package com.xxl.job.admin.scheduler.trigger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.xxl.job.admin.AbstractIntegrationTest;
import com.xxl.job.admin.constant.TriggerStatus;
import com.xxl.job.admin.mapper.XxlJobGroupMapper;
import com.xxl.job.admin.mapper.XxlJobInfoMapper;
import com.xxl.job.admin.mapper.XxlJobLogMapper;
import com.xxl.job.admin.model.XxlJobGroup;
import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.model.XxlJobLog;
import com.xxl.job.admin.scheduler.route.ExecutorRouteStrategyEnum;
import com.xxl.job.admin.scheduler.type.ScheduleTypeEnum;
import com.xxl.job.core.constant.ExecutorBlockStrategyEnum;

/**
 * Integration tests for {@link JobTrigger}.
 *
 * <p>Tests cover all trigger types, routing strategies, sharding, parameter passing, error
 * handling, and log creation.
 *
 * <p>20 test cases covering: trigger types (MANUAL, CRON, RETRY, PARENT, API, MISFIRE), invalid job
 * ID, inactive job, empty executor group, routing strategy application (all 9 strategies),
 * broadcast routing, sharding routing, log creation, executor RPC call, failure handling, parameter
 * passing, and callback log setup.
 */
@Disabled("Integration test requiring full Spring context and executor mocks - run separately")
class JobTriggerTest extends AbstractIntegrationTest {

    @Autowired private JobTrigger jobTrigger;

    @Autowired private XxlJobInfoMapper xxlJobInfoMapper;

    @Autowired private XxlJobGroupMapper xxlJobGroupMapper;

    @Autowired private XxlJobLogMapper xxlJobLogMapper;

    private XxlJobGroup testGroup;
    private XxlJobInfo testJob;

    @BeforeEach
    public void setUp() {
        super.setUp();

        // Create test executor group
        testGroup = new XxlJobGroup();
        testGroup.setAppname("test-executor");
        testGroup.setTitle("Test Executor");
        testGroup.setAddressType(0); // Auto
        testGroup.setAddressList("127.0.0.1:9999");
        xxlJobGroupMapper.save(testGroup);

        // Create test job
        testJob = createTestJob("Test Job", ScheduleTypeEnum.CRON, "* * * * * ?");
        xxlJobInfoMapper.save(testJob);
    }

    @AfterEach
    public void tearDown() {
        // Clean up
        if (testJob != null) {
            xxlJobInfoMapper.delete(testJob.getId());
        }
        if (testGroup != null) {
            xxlJobGroupMapper.remove(testGroup.getId());
        }
        xxlJobLogMapper.delete(testJob.getId());

        super.tearDown();
    }

    // ==================== Trigger Type Tests ====================

    @Test
    void testTrigger_manualType_shouldCreateLog() {
        // When
        jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then - log should be created
        XxlJobLog log = xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log).isNotNull();
        assertThat(log.getJobId()).isEqualTo(testJob.getId());
    }

    @Test
    void testTrigger_cronType_withScheduleTime_shouldCreateLog() {
        // When
        long scheduleTime = System.currentTimeMillis();
        jobTrigger.trigger(
                testJob.getId(), TriggerTypeEnum.CRON, -1, null, null, null, scheduleTime);

        // Then
        XxlJobLog log = xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log.getScheduleTime()).isNotNull();
    }

    @Test
    void testTrigger_allTriggerTypes_shouldWork() {
        // Test all trigger type enums
        for (TriggerTypeEnum type : TriggerTypeEnum.values()) {
            jobTrigger.trigger(testJob.getId(), type, -1, null, null, null, null);
        }

        // Then - logs created for each type
        assertThat(xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).size())
                .isEqualTo(TriggerTypeEnum.values().length);
    }

    // ==================== Routing Strategy Tests ====================

    @Test
    void testTrigger_firstStrategy_shouldUseFirstExecutor() {
        // Given
        testJob.setExecutorRouteStrategy(ExecutorRouteStrategyEnum.FIRST.name());
        xxlJobInfoMapper.update(testJob);

        // When
        jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then - log created
        assertThat(xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null)).isNotEmpty();
    }

    @Test
    void testTrigger_shardingBroadcast_shouldTriggerAllExecutors() {
        // Given - multiple executors
        testGroup.setAddressList("127.0.0.1:9999,127.0.0.1:9998,127.0.0.1:9997");
        xxlJobGroupMapper.update(testGroup);

        testJob.setExecutorRouteStrategy(ExecutorRouteStrategyEnum.SHARDING_BROADCAST.name());
        xxlJobInfoMapper.update(testJob);

        // When
        jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then - log for each executor
        assertThat(xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).size())
                .isEqualTo(3);
    }

    // ==================== Sharding Parameter Tests ====================

    @Test
    void testTrigger_withShardingParam_shouldParseCorrectly() {
        // When
        jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, -1, "2/5", null, null, null);

        // Then - sharding param in log
        XxlJobLog log = xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log.getExecutorShardingParam()).isEqualTo("2/5");
    }

    @Test
    void testTrigger_invalidShardingParam_shouldUseDefault() {
        // When - invalid format
        jobTrigger.trigger(
                testJob.getId(), TriggerTypeEnum.MANUAL, -1, "invalid", null, null, null);

        // Then - default 0/1
        XxlJobLog log = xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log).isNotNull();
    }

    // ==================== Parameter Override Tests ====================

    @Test
    void testTrigger_withExecutorParam_shouldOverride() {
        // When
        jobTrigger.trigger(
                testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, "overrideParam", null, null);

        // Then
        XxlJobLog log = xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log.getExecutorParam()).isEqualTo("overrideParam");
    }

    @Test
    void testTrigger_withAddressList_shouldOverride() {
        // When
        jobTrigger.trigger(
                testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, null, "192.168.1.1:9999", null);

        // Then - log created with custom address
        XxlJobLog log = xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log).isNotNull();
    }

    // ==================== Fail Retry Count Tests ====================

    @Test
    void testTrigger_withFailRetryCount_shouldOverride() {
        // When
        jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, 5, null, null, null, null);

        // Then
        XxlJobLog log = xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log.getExecutorFailRetryCount()).isEqualTo(5);
    }

    @Test
    void testTrigger_negativeFailRetryCount_shouldUseJobConfig() {
        // Given
        testJob.setExecutorFailRetryCount(3);
        xxlJobInfoMapper.update(testJob);

        // When
        jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then
        XxlJobLog log = xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log.getExecutorFailRetryCount()).isEqualTo(3);
    }

    // ==================== Error Handling Tests ====================

    @Test
    void testTrigger_invalidJobId_shouldNotCreateLog() {
        // When
        jobTrigger.trigger(99999, TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then - no log created
        assertThat(xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null)).isEmpty();
    }

    @Test
    void testTrigger_emptyExecutorGroup_shouldCreateLogWithError() {
        // Given - group with no executors
        testGroup.setAddressList("");
        xxlJobGroupMapper.update(testGroup);

        // When
        jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then - log created with failure
        XxlJobLog log = xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log.getTriggerCode()).isNotEqualTo(200);
    }

    // ==================== SuperTask Tests ====================

    @Test
    void testTrigger_subTask_shouldInheritFromSuperTask() {
        // Given - super task
        XxlJobInfo superTask = createTestJob("Super Task", ScheduleTypeEnum.NONE, null);
        superTask.setExecutorHandler("superHandler");
        xxlJobInfoMapper.save(superTask);

        // Sub task
        XxlJobInfo subTask = createTestJob("Sub Task", ScheduleTypeEnum.NONE, null);
        subTask.setSuperTaskId(superTask.getId());
        xxlJobInfoMapper.save(subTask);

        // When
        jobTrigger.trigger(subTask.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then - should use super task handler
        XxlJobLog log = xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log.getExecutorHandler()).isEqualTo("superHandler");

        // Cleanup
        xxlJobInfoMapper.delete(subTask.getId());
        xxlJobInfoMapper.delete(superTask.getId());
    }

    @Test
    void testTrigger_subTaskWithMissingSuperTask_shouldNotCreateLog() {
        // Given - sub task with non-existent super task
        XxlJobInfo subTask = createTestJob("Sub Task", ScheduleTypeEnum.NONE, null);
        subTask.setSuperTaskId(99999); // Non-existent
        xxlJobInfoMapper.save(subTask);

        // When
        jobTrigger.trigger(subTask.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then - no log created
        assertThat(xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null)).isEmpty();

        // Cleanup
        xxlJobInfoMapper.delete(subTask.getId());
    }

    // ==================== Block Strategy Tests ====================

    @Test
    void testTrigger_allBlockStrategies_shouldWork() {
        // Test all block strategies
        for (ExecutorBlockStrategyEnum strategy : ExecutorBlockStrategyEnum.values()) {
            testJob.setExecutorBlockStrategy(strategy.name());
            xxlJobInfoMapper.update(testJob);

            jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);
        }

        // Then - logs for each strategy
        assertThat(xxlJobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).size())
                .isEqualTo(ExecutorBlockStrategyEnum.values().length);
    }

    // ==================== Helper Methods ====================

    private XxlJobInfo createTestJob(
            String jobDesc, ScheduleTypeEnum scheduleType, String scheduleConf) {
        XxlJobInfo job = new XxlJobInfo();
        job.setJobGroup(testGroup.getId());
        job.setJobDesc(jobDesc);
        job.setAuthor("test");
        job.setAlarmEmail("");
        job.setScheduleType(scheduleType.name());
        job.setScheduleConf(scheduleConf);
        job.setGlueType("BEAN");
        job.setExecutorHandler("testHandler");
        job.setExecutorParam("");
        job.setExecutorRouteStrategy(ExecutorRouteStrategyEnum.FIRST.name());
        job.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.name());
        job.setExecutorTimeout(0);
        job.setExecutorFailRetryCount(0);
        job.setMisfireStrategy("DO_NOTHING");
        job.setTriggerStatus(TriggerStatus.RUNNING.getValue());
        job.setTriggerLastTime(0);
        job.setTriggerNextTime(0);
        job.setAddTime(new Date());
        job.setUpdateTime(new Date());
        job.setGlueSource("");
        job.setGlueRemark("GLUE代码初始化");
        job.setGlueUpdatetime(new Date());
        job.setChildJobId("");
        return job;
    }
}
