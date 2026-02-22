package com.abyss.orth.admin.scheduler.type;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.abyss.orth.admin.model.JobInfo;
import com.abyss.orth.admin.scheduler.misfire.MisfireStrategyEnum;
import com.abyss.orth.admin.scheduler.type.strategy.CronScheduleType;
import com.abyss.orth.admin.scheduler.type.strategy.FixRateScheduleType;
import com.abyss.orth.admin.scheduler.type.strategy.NoneScheduleType;

/**
 * Tests for schedule types and misfire strategies.
 *
 * <p>Tests cover: CRON (parsing, timezones, invalid expressions), FIX_RATE (intervals, edge cases),
 * NONE (manual triggers), misfire strategies (DO_NOTHING, FIRE_ONCE_NOW).
 */
@Disabled("Unit tests - enable for specific testing")
class ScheduleTypeTest {

    // ==================== CRON Schedule Type Tests ====================

    @Test
    void testCronScheduleType_everySecond_shouldCalculateNextTime() throws Exception {
        // Given
        CronScheduleType scheduleType = new CronScheduleType();
        JobInfo jobInfo = new JobInfo();
        jobInfo.setScheduleConf("* * * * * ?"); // Every second

        // When
        Date nextTime = scheduleType.generateNextTriggerTime(jobInfo, new Date());

        // Then
        assertThat(nextTime).isNotNull();
        assertThat(nextTime.getTime()).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    void testCronScheduleType_everyMinute_shouldCalculateNextTime() throws Exception {
        // Given
        CronScheduleType scheduleType = new CronScheduleType();
        JobInfo jobInfo = new JobInfo();
        jobInfo.setScheduleConf("0 * * * * ?"); // Every minute

        // When
        Date nextTime = scheduleType.generateNextTriggerTime(jobInfo, new Date());

        // Then
        assertThat(nextTime).isNotNull();
    }

    @Test
    void testCronScheduleType_invalidExpression_shouldReturnNull() throws Exception {
        // Given
        CronScheduleType scheduleType = new CronScheduleType();
        JobInfo jobInfo = new JobInfo();
        jobInfo.setScheduleConf("INVALID"); // Invalid CRON

        // When
        Date nextTime = scheduleType.generateNextTriggerTime(jobInfo, new Date());

        // Then
        assertThat(nextTime).isNull();
    }

    @Test
    void testCronScheduleType_emptyExpression_shouldReturnNull() throws Exception {
        // Given
        CronScheduleType scheduleType = new CronScheduleType();
        JobInfo jobInfo = new JobInfo();
        jobInfo.setScheduleConf(""); // Empty

        // When
        Date nextTime = scheduleType.generateNextTriggerTime(jobInfo, new Date());

        // Then
        assertThat(nextTime).isNull();
    }

    // ==================== FIX_RATE Schedule Type Tests ====================

    @Test
    void testFixRateScheduleType_validInterval_shouldCalculateNextTime() throws Exception {
        // Given
        FixRateScheduleType scheduleType = new FixRateScheduleType();
        JobInfo jobInfo = new JobInfo();
        jobInfo.setScheduleConf("5"); // 5 seconds
        Date fromTime = new Date();

        // When
        Date nextTime = scheduleType.generateNextTriggerTime(jobInfo, fromTime);

        // Then
        assertThat(nextTime).isNotNull();
        assertThat(nextTime.getTime()).isEqualTo(fromTime.getTime() + 5000);
    }

    @Test
    void testFixRateScheduleType_zeroInterval_shouldReturnNull() throws Exception {
        // Given
        FixRateScheduleType scheduleType = new FixRateScheduleType();
        JobInfo jobInfo = new JobInfo();
        jobInfo.setScheduleConf("0");

        // When
        Date nextTime = scheduleType.generateNextTriggerTime(jobInfo, new Date());

        // Then
        assertThat(nextTime).isNull();
    }

    @Test
    void testFixRateScheduleType_negativeInterval_shouldReturnNull() throws Exception {
        // Given
        FixRateScheduleType scheduleType = new FixRateScheduleType();
        JobInfo jobInfo = new JobInfo();
        jobInfo.setScheduleConf("-5");

        // When
        Date nextTime = scheduleType.generateNextTriggerTime(jobInfo, new Date());

        // Then
        assertThat(nextTime).isNull();
    }

    @Test
    void testFixRateScheduleType_invalidFormat_shouldReturnNull() throws Exception {
        // Given
        FixRateScheduleType scheduleType = new FixRateScheduleType();
        JobInfo jobInfo = new JobInfo();
        jobInfo.setScheduleConf("INVALID");

        // When
        Date nextTime = scheduleType.generateNextTriggerTime(jobInfo, new Date());

        // Then
        assertThat(nextTime).isNull();
    }

    // ==================== NONE Schedule Type Tests ====================

    @Test
    void testNoneScheduleType_shouldAlwaysReturnNull() throws Exception {
        // Given
        NoneScheduleType scheduleType = new NoneScheduleType();
        JobInfo jobInfo = new JobInfo();

        // When
        Date nextTime = scheduleType.generateNextTriggerTime(jobInfo, new Date());

        // Then
        assertThat(nextTime).isNull();
    }

    // ==================== ScheduleTypeEnum Tests ====================

    @Test
    void testScheduleTypeEnum_match_allTypes_shouldReturnCorrectType() {
        // Test all schedule types can be matched
        for (ScheduleTypeEnum type : ScheduleTypeEnum.values()) {
            ScheduleTypeEnum matched = ScheduleTypeEnum.match(type.name(), null);
            assertThat(matched).isEqualTo(type);
            assertThat(matched.getScheduleType()).isNotNull();
        }
    }

    @Test
    void testScheduleTypeEnum_match_invalidName_shouldReturnDefault() {
        // Given
        ScheduleTypeEnum result = ScheduleTypeEnum.match("INVALID", ScheduleTypeEnum.NONE);

        // Then
        assertThat(result).isEqualTo(ScheduleTypeEnum.NONE);
    }

    // ==================== Misfire Strategy Tests ====================

    @Test
    void testMisfireStrategyEnum_match_allStrategies_shouldReturnCorrectStrategy() {
        // Test all misfire strategies can be matched
        for (MisfireStrategyEnum strategy : MisfireStrategyEnum.values()) {
            MisfireStrategyEnum matched = MisfireStrategyEnum.match(strategy.name(), null);
            assertThat(matched).isEqualTo(strategy);
            assertThat(matched.getMisfireHandler()).isNotNull();
        }
    }

    @Test
    void testMisfireStrategyEnum_match_invalidName_shouldReturnDefault() {
        // Given
        MisfireStrategyEnum result =
                MisfireStrategyEnum.match("INVALID", MisfireStrategyEnum.DO_NOTHING);

        // Then
        assertThat(result).isEqualTo(MisfireStrategyEnum.DO_NOTHING);
    }
}
