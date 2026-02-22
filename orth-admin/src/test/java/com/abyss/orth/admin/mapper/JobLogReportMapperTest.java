package com.abyss.orth.admin.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import com.abyss.orth.admin.model.JobLogReport;
import com.xxl.tool.core.DateTool;

import jakarta.annotation.Resource;

/**
 * Integration tests for {@link JobLogReportMapper}.
 *
 * <p>Tests daily execution statistics persistence in the Orth distributed task scheduling system.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class JobLogReportMapperTest {
    private static final Logger logger = LoggerFactory.getLogger(JobLogReportMapperTest.class);

    // Test data constants
    private static final String TEST_REPORT_DATE = "2025-10-01";
    private static final int TEST_RUNNING_COUNT = 444;
    private static final int TEST_SUCCESS_COUNT = 555;
    private static final int TEST_FAILURE_COUNT = 666;

    @Resource private JobLogReportMapper jobLogReportMapper;

    /**
     * Tests save-or-update operation for daily job execution statistics.
     *
     * <p>Verifies:
     *
     * <ul>
     *   <li>Inserting new daily report (if not exists)
     *   <li>Updating existing daily report (if already exists)
     *   <li>Upsert operation success
     * </ul>
     */
    @Test
    public void testDailyReportSaveOrUpdate() {
        Date reportDate = DateTool.parseDate(TEST_REPORT_DATE);

        JobLogReport report = createTestReport(reportDate);

        int affectedRows = jobLogReportMapper.saveOrUpdate(report);
        assertTrue(affectedRows > 0, "SaveOrUpdate should affect at least 1 row");
        logger.info(
                "Daily report saved/updated: date={}, running={}, success={}, failure={}, "
                        + "affectedRows={}",
                TEST_REPORT_DATE,
                TEST_RUNNING_COUNT,
                TEST_SUCCESS_COUNT,
                TEST_FAILURE_COUNT,
                affectedRows);
    }

    /**
     * Creates a test daily report with sample execution statistics.
     *
     * @param reportDate the date for this report
     * @return configured test report instance
     */
    private JobLogReport createTestReport(Date reportDate) {
        JobLogReport report = new JobLogReport();
        report.setTriggerDay(reportDate);
        report.setRunningCount(TEST_RUNNING_COUNT);
        report.setSuccessCount(TEST_SUCCESS_COUNT);
        report.setFailCount(TEST_FAILURE_COUNT);
        return report;
    }
}
