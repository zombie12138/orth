package com.xxl.job.admin.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import com.xxl.job.admin.model.XxlJobLogReport;
import com.xxl.tool.core.DateTool;

import jakarta.annotation.Resource;

/**
 * Integration tests for {@link XxlJobLogReportMapper}.
 *
 * <p>Tests daily execution statistics persistence in the Orth distributed task scheduling system.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class XxlJobLogReportMapperTest {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobLogReportMapperTest.class);

    // Test data constants
    private static final String TEST_REPORT_DATE = "2025-10-01";
    private static final int TEST_RUNNING_COUNT = 444;
    private static final int TEST_SUCCESS_COUNT = 555;
    private static final int TEST_FAILURE_COUNT = 666;

    @Resource private XxlJobLogReportMapper xxlJobLogReportMapper;

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

        XxlJobLogReport report = createTestReport(reportDate);

        int affectedRows = xxlJobLogReportMapper.saveOrUpdate(report);
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
    private XxlJobLogReport createTestReport(Date reportDate) {
        XxlJobLogReport report = new XxlJobLogReport();
        report.setTriggerDay(reportDate);
        report.setRunningCount(TEST_RUNNING_COUNT);
        report.setSuccessCount(TEST_SUCCESS_COUNT);
        report.setFailCount(TEST_FAILURE_COUNT);
        return report;
    }
}
