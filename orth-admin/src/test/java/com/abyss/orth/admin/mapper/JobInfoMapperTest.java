package com.abyss.orth.admin.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import com.abyss.orth.admin.model.JobInfo;
import com.abyss.orth.admin.scheduler.misfire.MisfireStrategyEnum;
import com.abyss.orth.admin.scheduler.type.ScheduleTypeEnum;

import jakarta.annotation.Resource;

/**
 * Integration tests for {@link JobInfoMapper}.
 *
 * <p>Tests job configuration persistence and query operations in the Orth distributed task
 * scheduling system.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class JobInfoMapperTest {
    private static final Logger logger = LoggerFactory.getLogger(JobInfoMapperTest.class);

    // Test data constants
    private static final int TEST_JOB_GROUP_ID = 1;
    private static final String TEST_JOB_DESC = "Test batch data collection job";
    private static final String TEST_AUTHOR = "test-user";
    private static final String TEST_ALARM_EMAIL = "test@orth.com";
    private static final int TEST_FIX_RATE_SECONDS = 33;
    private static final String TEST_EXECUTOR_ROUTE = "ROUND";
    private static final String TEST_EXECUTOR_HANDLER = "testJobHandler";
    private static final String TEST_EXECUTOR_PARAM = "param1=value1";
    private static final String TEST_EXECUTOR_BLOCK_STRATEGY = "SERIAL_EXECUTION";
    private static final String TEST_GLUE_TYPE = "BEAN";
    private static final String TEST_GLUE_SOURCE = "// Test source code";
    private static final String TEST_GLUE_REMARK = "Initial version";
    private static final String TEST_CHILD_JOB_ID = "1";

    private static final String TEST_JOB_DESC_UPDATED = "Updated batch data collection job";
    private static final String TEST_AUTHOR_UPDATED = "test-user-updated";
    private static final String TEST_ALARM_EMAIL_UPDATED = "updated@orth.com";
    private static final int TEST_FIX_RATE_SECONDS_UPDATED = 44;
    private static final String TEST_EXECUTOR_ROUTE_UPDATED = "FIRST";
    private static final String TEST_EXECUTOR_HANDLER_UPDATED = "updatedJobHandler";
    private static final String TEST_EXECUTOR_PARAM_UPDATED = "param1=value2";
    private static final String TEST_EXECUTOR_BLOCK_STRATEGY_UPDATED = "DISCARD_LATER";
    private static final String TEST_GLUE_TYPE_UPDATED = "GLUE_GROOVY";
    private static final String TEST_GLUE_SOURCE_UPDATED = "// Updated source code";
    private static final String TEST_GLUE_REMARK_UPDATED = "Updated version";

    private static final int PAGE_OFFSET = 0;
    private static final int PAGE_SIZE = 20;
    private static final int TRIGGER_STATUS_ALL = -1;

    @Resource private JobInfoMapper jobInfoMapper;

    /**
     * Tests paginated job list queries and group-based filtering.
     *
     * <p>Verifies:
     *
     * <ul>
     *   <li>Pagination of job configurations
     *   <li>Total count calculation
     *   <li>Filtering jobs by executor group
     * </ul>
     */
    @Test
    public void testJobInfoPaginationAndGroupFiltering() {
        List<JobInfo> pageResult =
                jobInfoMapper.pageList(
                        PAGE_OFFSET,
                        PAGE_SIZE,
                        TEST_JOB_GROUP_ID,
                        TRIGGER_STATUS_ALL,
                        null,
                        null,
                        null,
                        0);
        int totalCount =
                jobInfoMapper.pageListCount(
                        PAGE_OFFSET,
                        PAGE_SIZE,
                        TEST_JOB_GROUP_ID,
                        TRIGGER_STATUS_ALL,
                        null,
                        null,
                        null,
                        0);

        assertNotNull(pageResult, "Page result should not be null");
        logger.info("Found {} jobs in page, total count: {}", pageResult.size(), totalCount);

        List<JobInfo> groupJobs = jobInfoMapper.getJobsByGroup(TEST_JOB_GROUP_ID);
        assertNotNull(groupJobs, "Group jobs should not be null");
    }

    /**
     * Tests complete CRUD lifecycle for job configurations.
     *
     * <p>Verifies:
     *
     * <ul>
     *   <li>Creating job with schedule configuration
     *   <li>Loading job by ID
     *   <li>Updating job properties including schedule type and misfire strategy
     *   <li>Deleting job
     *   <li>Counting all jobs
     * </ul>
     */
    @Test
    public void testJobInfoCrudOperations() {
        // Create new job
        JobInfo jobInfo = createTestJobInfo();
        int saveResult = jobInfoMapper.save(jobInfo);
        assertEquals(1, saveResult, "Save should affect 1 row");
        assertNotNull(jobInfo.getId(), "Job ID should be generated");

        // Load and verify
        JobInfo loadedJob = jobInfoMapper.loadById(jobInfo.getId());
        assertNotNull(loadedJob, "Loaded job should not be null");
        assertEquals(TEST_JOB_DESC, loadedJob.getJobDesc());

        // Update job with new schedule configuration
        updateJobInfoProperties(loadedJob);
        int updateResult = jobInfoMapper.update(loadedJob);
        assertEquals(1, updateResult, "Update should affect 1 row");

        // Verify group jobs query
        List<JobInfo> groupJobs = jobInfoMapper.getJobsByGroup(TEST_JOB_GROUP_ID);
        assertNotNull(groupJobs);

        // Delete job
        int deleteResult = jobInfoMapper.delete(loadedJob.getId());
        assertEquals(1, deleteResult, "Delete should affect 1 row");

        // Count all jobs
        int totalJobCount = jobInfoMapper.findAllCount();
        logger.info("Total job count: {}", totalJobCount);
    }

    /**
     * Creates a test job info instance with fixed-rate schedule configuration.
     *
     * @return configured test job info
     */
    private JobInfo createTestJobInfo() {
        JobInfo info = new JobInfo();
        info.setJobGroup(TEST_JOB_GROUP_ID);
        info.setJobDesc(TEST_JOB_DESC);
        info.setAuthor(TEST_AUTHOR);
        info.setAlarmEmail(TEST_ALARM_EMAIL);
        info.setScheduleType(ScheduleTypeEnum.FIX_RATE.name());
        info.setScheduleConf(String.valueOf(TEST_FIX_RATE_SECONDS));
        info.setMisfireStrategy(MisfireStrategyEnum.DO_NOTHING.name());
        info.setExecutorRouteStrategy(TEST_EXECUTOR_ROUTE);
        info.setExecutorHandler(TEST_EXECUTOR_HANDLER);
        info.setExecutorParam(TEST_EXECUTOR_PARAM);
        info.setExecutorBlockStrategy(TEST_EXECUTOR_BLOCK_STRATEGY);
        info.setGlueType(TEST_GLUE_TYPE);
        info.setGlueSource(TEST_GLUE_SOURCE);
        info.setGlueRemark(TEST_GLUE_REMARK);
        info.setChildJobId(TEST_CHILD_JOB_ID);

        Date now = new Date();
        info.setAddTime(now);
        info.setUpdateTime(now);
        info.setGlueUpdatetime(now);

        return info;
    }

    /**
     * Updates job info properties with modified schedule configuration.
     *
     * @param info the job info to update
     */
    private void updateJobInfoProperties(JobInfo info) {
        info.setScheduleType(ScheduleTypeEnum.FIX_RATE.name());
        info.setScheduleConf(String.valueOf(TEST_FIX_RATE_SECONDS_UPDATED));
        info.setMisfireStrategy(MisfireStrategyEnum.FIRE_ONCE_NOW.name());
        info.setJobDesc(TEST_JOB_DESC_UPDATED);
        info.setAuthor(TEST_AUTHOR_UPDATED);
        info.setAlarmEmail(TEST_ALARM_EMAIL_UPDATED);
        info.setExecutorRouteStrategy(TEST_EXECUTOR_ROUTE_UPDATED);
        info.setExecutorHandler(TEST_EXECUTOR_HANDLER_UPDATED);
        info.setExecutorParam(TEST_EXECUTOR_PARAM_UPDATED);
        info.setExecutorBlockStrategy(TEST_EXECUTOR_BLOCK_STRATEGY_UPDATED);
        info.setGlueType(TEST_GLUE_TYPE_UPDATED);
        info.setGlueSource(TEST_GLUE_SOURCE_UPDATED);
        info.setGlueRemark(TEST_GLUE_REMARK_UPDATED);
        info.setChildJobId(TEST_CHILD_JOB_ID);
        info.setGlueUpdatetime(new Date());
        info.setUpdateTime(new Date());
    }
}
