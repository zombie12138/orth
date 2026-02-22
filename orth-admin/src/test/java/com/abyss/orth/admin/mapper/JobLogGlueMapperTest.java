package com.abyss.orth.admin.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.abyss.orth.admin.model.JobLogGlue;

import jakarta.annotation.Resource;

/**
 * Integration tests for {@link JobLogGlueMapper}.
 *
 * <p>Tests GLUE code version history persistence for dynamic script jobs in the Orth distributed
 * task scheduling system.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class JobLogGlueMapperTest {

    // Test data constants
    private static final int TEST_JOB_ID = 1;
    private static final String TEST_GLUE_TYPE = "GLUE_GROOVY";
    private static final String TEST_GLUE_SOURCE =
            "import com.abyss.orth.core.handler.IJobHandler\n"
                    + "class TestJob extends IJobHandler {\n"
                    + "    void execute() { println('test') }\n"
                    + "}";
    private static final String TEST_GLUE_REMARK = "Initial GLUE version for testing";
    private static final int TEST_VERSION_KEEP_COUNT = 1;

    @Resource private JobLogGlueMapper jobLogGlueMapper;

    /**
     * Tests GLUE code version history lifecycle operations.
     *
     * <p>Verifies:
     *
     * <ul>
     *   <li>Saving GLUE code version
     *   <li>Querying version history by job ID
     *   <li>Removing old versions (retention policy)
     *   <li>Deleting all versions for a job
     * </ul>
     */
    @Test
    public void testGlueVersionHistoryCrudOperations() {
        // Create GLUE code version
        JobLogGlue logGlue = createTestGlueLog();
        int saveResult = jobLogGlueMapper.save(logGlue);
        assertEquals(1, saveResult, "Save should affect 1 row");

        // Query version history
        List<JobLogGlue> versionHistory = jobLogGlueMapper.findByJobId(TEST_JOB_ID);
        assertNotNull(versionHistory, "Version history should not be null");

        // Remove old versions (keep only N latest)
        int removeOldResult = jobLogGlueMapper.removeOld(TEST_JOB_ID, TEST_VERSION_KEEP_COUNT);
        assertEquals(0, removeOldResult, "Should not remove any versions in this test");

        // Delete all versions for job
        int deleteResult = jobLogGlueMapper.deleteByJobId(TEST_JOB_ID);
        assertEquals(1, deleteResult, "Delete should affect 1 row");
    }

    /**
     * Creates a test GLUE log entry with sample Groovy script.
     *
     * @return configured test GLUE log instance
     */
    private JobLogGlue createTestGlueLog() {
        JobLogGlue logGlue = new JobLogGlue();
        logGlue.setJobId(TEST_JOB_ID);
        logGlue.setGlueType(TEST_GLUE_TYPE);
        logGlue.setGlueSource(TEST_GLUE_SOURCE);
        logGlue.setGlueRemark(TEST_GLUE_REMARK);

        Date now = new Date();
        logGlue.setAddTime(now);
        logGlue.setUpdateTime(now);

        return logGlue;
    }
}
