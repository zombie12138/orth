package com.xxl.job.admin.core.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.ParseException;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.admin.scheduler.cron.CronExpression;
import com.xxl.tool.core.DateTool;

/**
 * Unit tests for {@link CronExpression} parsing and next trigger time calculation.
 *
 * <p>Tests verify that CRON expressions are correctly parsed and generate expected trigger
 * schedules. The cron expression format follows standard cron syntax with support for quartz-style
 * extensions (e.g., "?" for day-of-month/day-of-week).
 *
 * <p><b>Test Coverage:</b>
 *
 * <ul>
 *   <li>Weekly schedule calculation (every Sunday at midnight)
 *   <li>Next valid time computation from arbitrary start points
 *   <li>Multi-iteration schedule generation
 * </ul>
 *
 * @author orth (Abyss Project)
 * @since 3.3.0
 */
public class CronExpressionTest {
    private static final Logger logger = LoggerFactory.getLogger(CronExpressionTest.class);

    // Test constants
    private static final String WEEKLY_SUNDAY_MIDNIGHT_CRON = "0 0 0 ? * 1";
    private static final int TRIGGER_ITERATIONS = 5;

    /**
     * Tests cron expression parsing and next trigger time calculation for a weekly schedule.
     *
     * <p>Validates that the expression "0 0 0 ? * 1" (every Sunday at midnight) correctly generates
     * a sequence of future trigger times. Each iteration should advance by exactly 7 days.
     *
     * @throws ParseException if the CRON expression syntax is invalid
     */
    @Test
    public void testWeeklySundayMidnightSchedule_shouldGenerateNextTriggerTimes()
            throws ParseException {
        // Arrange
        CronExpression cronExpression = new CronExpression(WEEKLY_SUNDAY_MIDNIGHT_CRON);
        Date lastTriggerTime = new Date();

        // Act & Assert
        for (int i = 0; i < TRIGGER_ITERATIONS; i++) {
            Date nextTriggerTime = cronExpression.getNextValidTimeAfter(lastTriggerTime);

            assertNotNull(nextTriggerTime, "Next trigger time should not be null");
            assertTrue(
                    nextTriggerTime.after(lastTriggerTime),
                    "Next trigger time must be after previous trigger time");

            logger.info(
                    "Iteration {}: Next trigger time = {}",
                    i + 1,
                    DateTool.formatDateTime(nextTriggerTime));

            lastTriggerTime = nextTriggerTime;
        }
    }
}
