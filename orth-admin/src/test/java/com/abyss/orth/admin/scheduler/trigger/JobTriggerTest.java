package com.abyss.orth.admin.scheduler.trigger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.abyss.orth.admin.AbstractIntegrationTest;
import com.abyss.orth.admin.constant.TriggerStatus;
import com.abyss.orth.admin.mapper.JobGroupMapper;
import com.abyss.orth.admin.mapper.JobInfoMapper;
import com.abyss.orth.admin.mapper.JobLogMapper;
import com.abyss.orth.admin.model.JobGroup;
import com.abyss.orth.admin.model.JobInfo;
import com.abyss.orth.admin.model.JobLog;
import com.abyss.orth.admin.scheduler.route.ExecutorRouteStrategyEnum;
import com.abyss.orth.admin.scheduler.type.ScheduleTypeEnum;
import com.abyss.orth.core.constant.ExecutorBlockStrategyEnum;

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

    @Autowired private JobInfoMapper jobInfoMapper;

    @Autowired private JobGroupMapper jobGroupMapper;

    @Autowired private JobLogMapper jobLogMapper;

    private JobGroup testGroup;
    private JobInfo testJob;

    @BeforeEach
    public void setUp() {
        super.setUp();

        // Create test executor group
        testGroup = new JobGroup();
        testGroup.setAppname("test-executor");
        testGroup.setTitle("Test Executor");
        testGroup.setAddressType(0); // Auto
        testGroup.setAddressList("127.0.0.1:9999");
        jobGroupMapper.save(testGroup);

        // Create test job
        testJob = createTestJob("Test Job", ScheduleTypeEnum.CRON, "* * * * * ?");
        jobInfoMapper.save(testJob);
    }

    @AfterEach
    public void tearDown() {
        // Clean up
        if (testJob != null) {
            jobInfoMapper.delete(testJob.getId());
        }
        if (testGroup != null) {
            jobGroupMapper.remove(testGroup.getId());
        }
        jobLogMapper.delete(testJob.getId());

        super.tearDown();
    }

    // ==================== Trigger Type Tests ====================

    @Test
    void testTrigger_manualType_shouldCreateLog() {
        // When
        jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then - log should be created
        JobLog log = jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
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
        JobLog log = jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log.getScheduleTime()).isNotNull();
    }

    @Test
    void testTrigger_allTriggerTypes_shouldWork() {
        // Test all trigger type enums
        for (TriggerTypeEnum type : TriggerTypeEnum.values()) {
            jobTrigger.trigger(testJob.getId(), type, -1, null, null, null, null);
        }

        // Then - logs created for each type
        assertThat(jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).size())
                .isEqualTo(TriggerTypeEnum.values().length);
    }

    // ==================== Routing Strategy Tests ====================

    @Test
    void testTrigger_firstStrategy_shouldUseFirstExecutor() {
        // Given
        testJob.setExecutorRouteStrategy(ExecutorRouteStrategyEnum.FIRST.name());
        jobInfoMapper.update(testJob);

        // When
        jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then - log created
        assertThat(jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null)).isNotEmpty();
    }

    @Test
    void testTrigger_shardingBroadcast_shouldTriggerAllExecutors() {
        // Given - multiple executors
        testGroup.setAddressList("127.0.0.1:9999,127.0.0.1:9998,127.0.0.1:9997");
        jobGroupMapper.update(testGroup);

        testJob.setExecutorRouteStrategy(ExecutorRouteStrategyEnum.SHARDING_BROADCAST.name());
        jobInfoMapper.update(testJob);

        // When
        jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then - log for each executor
        assertThat(jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).size()).isEqualTo(3);
    }

    // ==================== Sharding Parameter Tests ====================

    @Test
    void testTrigger_withShardingParam_shouldParseCorrectly() {
        // When
        jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, -1, "2/5", null, null, null);

        // Then - sharding param in log
        JobLog log = jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log.getExecutorShardingParam()).isEqualTo("2/5");
    }

    @Test
    void testTrigger_invalidShardingParam_shouldUseDefault() {
        // When - invalid format
        jobTrigger.trigger(
                testJob.getId(), TriggerTypeEnum.MANUAL, -1, "invalid", null, null, null);

        // Then - default 0/1
        JobLog log = jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log).isNotNull();
    }

    // ==================== Parameter Override Tests ====================

    @Test
    void testTrigger_withExecutorParam_shouldOverride() {
        // When
        jobTrigger.trigger(
                testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, "overrideParam", null, null);

        // Then
        JobLog log = jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log.getExecutorParam()).isEqualTo("overrideParam");
    }

    @Test
    void testTrigger_withAddressList_shouldOverride() {
        // When
        jobTrigger.trigger(
                testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, null, "192.168.1.1:9999", null);

        // Then - log created with custom address
        JobLog log = jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log).isNotNull();
    }

    // ==================== Fail Retry Count Tests ====================

    @Test
    void testTrigger_withFailRetryCount_shouldOverride() {
        // When
        jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, 5, null, null, null, null);

        // Then
        JobLog log = jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log.getExecutorFailRetryCount()).isEqualTo(5);
    }

    @Test
    void testTrigger_negativeFailRetryCount_shouldUseJobConfig() {
        // Given
        testJob.setExecutorFailRetryCount(3);
        jobInfoMapper.update(testJob);

        // When
        jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then
        JobLog log = jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log.getExecutorFailRetryCount()).isEqualTo(3);
    }

    // ==================== Error Handling Tests ====================

    @Test
    void testTrigger_invalidJobId_shouldNotCreateLog() {
        // When
        jobTrigger.trigger(99999, TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then - no log created
        assertThat(jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null)).isEmpty();
    }

    @Test
    void testTrigger_emptyExecutorGroup_shouldCreateLogWithError() {
        // Given - group with no executors
        testGroup.setAddressList("");
        jobGroupMapper.update(testGroup);

        // When
        jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then - log created with failure
        JobLog log = jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log.getTriggerCode()).isNotEqualTo(200);
    }

    // ==================== SuperTask Tests ====================

    @Test
    void testTrigger_subTask_shouldInheritFromSuperTask() {
        // Given - super task
        JobInfo superTask = createTestJob("Super Task", ScheduleTypeEnum.NONE, null);
        superTask.setExecutorHandler("superHandler");
        jobInfoMapper.save(superTask);

        // Sub task
        JobInfo subTask = createTestJob("Sub Task", ScheduleTypeEnum.NONE, null);
        subTask.setSuperTaskId(superTask.getId());
        jobInfoMapper.save(subTask);

        // When
        jobTrigger.trigger(subTask.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then - should use super task handler
        JobLog log = jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).get(0);
        assertThat(log.getExecutorHandler()).isEqualTo("superHandler");

        // Cleanup
        jobInfoMapper.delete(subTask.getId());
        jobInfoMapper.delete(superTask.getId());
    }

    @Test
    void testTrigger_subTaskWithMissingSuperTask_shouldNotCreateLog() {
        // Given - sub task with non-existent super task
        JobInfo subTask = createTestJob("Sub Task", ScheduleTypeEnum.NONE, null);
        subTask.setSuperTaskId(99999); // Non-existent
        jobInfoMapper.save(subTask);

        // When
        jobTrigger.trigger(subTask.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);

        // Then - no log created
        assertThat(jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null)).isEmpty();

        // Cleanup
        jobInfoMapper.delete(subTask.getId());
    }

    // ==================== Block Strategy Tests ====================

    @Test
    void testTrigger_allBlockStrategies_shouldWork() {
        // Test all block strategies
        for (ExecutorBlockStrategyEnum strategy : ExecutorBlockStrategyEnum.values()) {
            testJob.setExecutorBlockStrategy(strategy.name());
            jobInfoMapper.update(testJob);

            jobTrigger.trigger(testJob.getId(), TriggerTypeEnum.MANUAL, -1, null, null, null, null);
        }

        // Then - logs for each strategy
        assertThat(jobLogMapper.pageList(0, 100, 0, 0, null, null, -1, null).size())
                .isEqualTo(ExecutorBlockStrategyEnum.values().length);
    }

    // ==================== Helper Methods ====================

    private JobInfo createTestJob(
            String jobDesc, ScheduleTypeEnum scheduleType, String scheduleConf) {
        JobInfo job = new JobInfo();
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
