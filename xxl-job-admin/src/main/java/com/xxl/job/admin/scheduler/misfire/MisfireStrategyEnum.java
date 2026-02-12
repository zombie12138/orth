package com.xxl.job.admin.scheduler.misfire;

import java.util.Arrays;

import com.xxl.job.admin.scheduler.misfire.strategy.MisfireDoNothing;
import com.xxl.job.admin.scheduler.misfire.strategy.MisfireFireOnceNow;
import com.xxl.job.admin.util.I18nUtil;

/**
 * Defines misfire handling strategies for the Orth scheduler.
 *
 * <p>A misfire occurs when a job's scheduled trigger time is delayed by more than the configured
 * threshold (typically 5+ seconds). This can happen due to scheduler downtime, system overload, or
 * thread pool saturation. Each strategy defines how to handle these missed executions.
 *
 * <p>Available strategies:
 *
 * <ul>
 *   <li><b>DO_NOTHING</b>: Skip the missed execution entirely; wait for next scheduled time
 *   <li><b>FIRE_ONCE_NOW</b>: Execute immediately to compensate for the missed trigger
 * </ul>
 *
 * <p>Strategy selection considerations:
 *
 * <ul>
 *   <li>Use DO_NOTHING for jobs where timeliness is critical and late execution is meaningless
 *       (e.g., market-close data snapshots)
 *   <li>Use FIRE_ONCE_NOW for data consistency jobs where every execution must complete (e.g.,
 *       batch ETL pipelines)
 * </ul>
 *
 * @author xuxueli 2020-10-29
 */
public enum MisfireStrategyEnum {

    /** Skip the missed execution and wait for next scheduled time */
    DO_NOTHING(I18nUtil.getString("misfire_strategy_do_nothing"), new MisfireDoNothing()),

    /** Execute immediately to compensate for the missed trigger */
    FIRE_ONCE_NOW(I18nUtil.getString("misfire_strategy_fire_once_now"), new MisfireFireOnceNow());

    private final String title;
    private final MisfireHandler misfireHandler;

    MisfireStrategyEnum(String title, MisfireHandler misfireHandler) {
        this.title = title;
        this.misfireHandler = misfireHandler;
    }

    /**
     * Returns the internationalized display title for this misfire strategy.
     *
     * @return the localized display name
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the handler implementation for this misfire strategy.
     *
     * @return the misfire handling strategy
     */
    public MisfireHandler getMisfireHandler() {
        return misfireHandler;
    }

    /**
     * Finds a misfire strategy by its enum name, with fallback to a default.
     *
     * <p>This method is used during job configuration parsing to safely resolve misfire strategy
     * settings.
     *
     * @param name the enum constant name (e.g., "DO_NOTHING", "FIRE_ONCE_NOW")
     * @param defaultItem the fallback value if name is null or not found
     * @return the matching misfire strategy, or defaultItem if not found
     */
    public static MisfireStrategyEnum match(String name, MisfireStrategyEnum defaultItem) {
        if (name == null) {
            return defaultItem;
        }

        return Arrays.stream(values())
                .filter(item -> item.name().equals(name))
                .findFirst()
                .orElse(defaultItem);
    }
}
