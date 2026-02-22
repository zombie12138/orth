package com.abyss.orth.admin.scheduler.type;

import java.util.Arrays;

import com.abyss.orth.admin.scheduler.type.strategy.CronScheduleType;
import com.abyss.orth.admin.scheduler.type.strategy.FixRateScheduleType;
import com.abyss.orth.admin.scheduler.type.strategy.NoneScheduleType;
import com.abyss.orth.admin.util.I18nUtil;

/**
 * Defines the scheduling types supported by the Orth scheduler.
 *
 * <p>Each schedule type encapsulates a different strategy for calculating the next trigger time for
 * a job. The strategy pattern is used to allow pluggable schedule calculation algorithms.
 *
 * <p>Available schedule types:
 *
 * <ul>
 *   <li><b>NONE</b>: No automatic scheduling; job must be triggered manually
 *   <li><b>CRON</b>: Schedule based on cron expression (standard Unix cron syntax)
 *   <li><b>FIX_RATE</b>: Schedule at fixed intervals (specified in seconds)
 * </ul>
 *
 * <p>Each enum constant holds both an internationalized title and a concrete strategy
 * implementation ({@link ScheduleType}) for calculating next trigger times.
 *
 * @author xuxueli 2020-10-29
 */
public enum ScheduleTypeEnum {
    /** No automatic scheduling; manual trigger only */
    NONE(I18nUtil.getString("schedule_type_none"), new NoneScheduleType()),

    /** Cron-based scheduling using standard cron expressions */
    CRON(I18nUtil.getString("schedule_type_cron"), new CronScheduleType()),

    /** Fixed-rate scheduling with interval in seconds */
    FIX_RATE(I18nUtil.getString("schedule_type_fix_rate"), new FixRateScheduleType());

    // Note: FIX_DELAY (fixed delay after completion) is not currently implemented
    // but reserved for future use

    private final String title;
    private final ScheduleType scheduleType;

    ScheduleTypeEnum(String title, ScheduleType scheduleType) {
        this.title = title;
        this.scheduleType = scheduleType;
    }

    /**
     * Returns the internationalized display title for this schedule type.
     *
     * @return the localized display name
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the strategy implementation for this schedule type.
     *
     * @return the schedule calculation strategy
     */
    public ScheduleType getScheduleType() {
        return scheduleType;
    }

    /**
     * Finds a schedule type by its enum name, with fallback to a default.
     *
     * <p>This is the primary method for safe enum lookup during job configuration parsing and
     * validation.
     *
     * @param name the enum constant name (e.g., "CRON", "FIX_RATE")
     * @param defaultItem the fallback value if name is null or not found
     * @return the matching schedule type, or defaultItem if not found
     */
    public static ScheduleTypeEnum match(String name, ScheduleTypeEnum defaultItem) {
        if (name == null) {
            return defaultItem;
        }

        return Arrays.stream(values())
                .filter(item -> item.name().equals(name))
                .findFirst()
                .orElse(defaultItem);
    }
}
