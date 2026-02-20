package com.xxl.job.admin.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.xxl.job.admin.model.XxlJobLog;

import jakarta.annotation.Resource;

/**
 * Integration tests for {@link XxlJobLogMapper}.
 *
 * <p>Tests job execution log persistence and query operations in the Orth distributed task
 * scheduling system.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class XxlJobLogMapperTest {

    // Test data constants
    private static final int TEST_JOB_GROUP_ID = 1;
    private static final int TEST_JOB_ID = 1;
    private static final int TEST_TRIGGER_STATUS_SUCCESS = 1;

    private static final int TEST_TRIGGER_CODE_SUCCESS = 200;
    private static final String TEST_TRIGGER_MSG = "Trigger success";
    private static final String TEST_EXECUTOR_ADDRESS = "http://localhost:9999";
    private static final String TEST_EXECUTOR_HANDLER = "testJobHandler";
    private static final String TEST_EXECUTOR_PARAM = "param1=value1";

    private static final int TEST_HANDLE_CODE_SUCCESS = 200;
    private static final String TEST_HANDLE_MSG = "Job executed successfully";

    private static final int PAGE_OFFSET = 0;
    private static final int PAGE_SIZE = 10;
    private static final int LOG_RETENTION_DAYS = 100;
    private static final int LOG_CLEAR_LIMIT = 100;

    @Resource private XxlJobLogMapper xxlJobLogMapper;

    /**
     * Tests complete lifecycle of job execution logs.
     *
     * <p>Verifies:
     *
     * <ul>
     *   <li>Paginated log queries with filtering
     *   <li>Creating new execution log entry
     *   <li>Updating trigger information (scheduler-side data)
     *   <li>Updating handle information (executor-side results)
     *   <li>Finding logs eligible for cleanup
     *   <li>Deleting logs by job ID
     * </ul>
     */
    @Test
    public void testJobLogLifecycleOperations() {
        // Query paginated logs
        List<XxlJobLog> pageResult =
                xxlJobLogMapper.pageList(
                        PAGE_OFFSET,
                        PAGE_SIZE,
                        TEST_JOB_GROUP_ID,
                        TEST_JOB_ID,
                        null,
                        null,
                        TEST_TRIGGER_STATUS_SUCCESS,
                        null);
        int totalCount =
                xxlJobLogMapper.pageListCount(
                        PAGE_OFFSET,
                        PAGE_SIZE,
                        TEST_JOB_GROUP_ID,
                        TEST_JOB_ID,
                        null,
                        null,
                        TEST_TRIGGER_STATUS_SUCCESS,
                        null);
        assertNotNull(pageResult, "Page result should not be null");

        // Create new execution log
        XxlJobLog log = createTestJobLog();
        long saveResult = xxlJobLogMapper.save(log);
        assertEquals(1, saveResult, "Save should affect 1 row");
        assertNotNull(log.getId(), "Log ID should be generated");

        // Load and verify
        XxlJobLog loadedLog = xxlJobLogMapper.load(log.getId());
        assertNotNull(loadedLog, "Loaded log should not be null");

        // Update trigger info (admin-side scheduling data)
        updateTriggerInfo(log);
        long updateTriggerResult = xxlJobLogMapper.updateTriggerInfo(log);
        assertEquals(1, updateTriggerResult, "Update trigger info should affect 1 row");

        loadedLog = xxlJobLogMapper.load(log.getId());
        assertNotNull(loadedLog.getTriggerTime(), "Trigger time should be set");

        // Update handle info (executor-side execution results)
        updateHandleInfo(log);
        long updateHandleResult = xxlJobLogMapper.updateHandleInfo(log);
        assertEquals(1, updateHandleResult, "Update handle info should affect 1 row");

        loadedLog = xxlJobLogMapper.load(log.getId());
        assertNotNull(loadedLog.getHandleTime(), "Handle time should be set");

        // Find logs eligible for cleanup
        List<Long> clearLogIds =
                xxlJobLogMapper.findClearLogIds(
                        TEST_JOB_GROUP_ID,
                        TEST_JOB_ID,
                        new Date(),
                        LOG_RETENTION_DAYS,
                        LOG_CLEAR_LIMIT);
        assertNotNull(clearLogIds, "Clear log IDs should not be null");

        // Delete logs
        int deleteResult = xxlJobLogMapper.delete(log.getJobId());
        assertEquals(1, deleteResult, "Delete should affect 1 row");
    }

    /**
     * Creates a test job log entry with minimal initial data.
     *
     * @return configured test job log instance
     */
    private XxlJobLog createTestJobLog() {
        XxlJobLog log = new XxlJobLog();
        log.setJobGroup(TEST_JOB_GROUP_ID);
        log.setJobId(TEST_JOB_ID);
        return log;
    }

    /**
     * Updates log with trigger information from scheduler side.
     *
     * @param log the log to update
     */
    private void updateTriggerInfo(XxlJobLog log) {
        log.setTriggerTime(new Date());
        log.setTriggerCode(TEST_TRIGGER_CODE_SUCCESS);
        log.setTriggerMsg(TEST_TRIGGER_MSG);
        log.setExecutorAddress(TEST_EXECUTOR_ADDRESS);
        log.setExecutorHandler(TEST_EXECUTOR_HANDLER);
        log.setExecutorParam(TEST_EXECUTOR_PARAM);
    }

    /**
     * Updates log with handle information from executor side.
     *
     * @param log the log to update
     */
    private void updateHandleInfo(XxlJobLog log) {
        log.setHandleTime(new Date());
        log.setHandleCode(TEST_HANDLE_CODE_SUCCESS);
        log.setHandleMsg(TEST_HANDLE_MSG);
    }
}
