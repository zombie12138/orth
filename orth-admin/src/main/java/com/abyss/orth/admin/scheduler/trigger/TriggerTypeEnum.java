package com.abyss.orth.admin.scheduler.trigger;

import java.util.Arrays;
import java.util.Optional;

import com.abyss.orth.admin.util.I18nUtil;

/**
 * Defines the trigger type for job executions in the Orth scheduler.
 *
 * <p>The trigger type indicates how and why a job execution was initiated. This information is
 * recorded in the execution log and can be used for auditing, debugging, and monitoring purposes.
 *
 * <p>Trigger types and their meanings:
 *
 * <ul>
 *   <li><b>MANUAL</b>: User-initiated execution through the admin console
 *   <li><b>CRON</b>: Automatic execution based on cron schedule
 *   <li><b>RETRY</b>: Automatic retry after a previous execution failure
 *   <li><b>PARENT</b>: Triggered as a child job after parent job completion
 *   <li><b>API</b>: Triggered via external API call (RESTful endpoint)
 *   <li><b>MISFIRE</b>: Compensatory execution for missed scheduled time
 * </ul>
 *
 * @author xuxueli 2018-09-16
 */
public enum TriggerTypeEnum {
    /** User-initiated manual trigger through admin console */
    MANUAL(I18nUtil.getString("jobconf_trigger_type_manual")),

    /** Automatic trigger based on cron schedule */
    CRON(I18nUtil.getString("jobconf_trigger_type_cron")),

    /** Automatic retry trigger after failure */
    RETRY(I18nUtil.getString("jobconf_trigger_type_retry")),

    /** Child job trigger after parent job completion */
    PARENT(I18nUtil.getString("jobconf_trigger_type_parent")),

    /** External API-initiated trigger */
    API(I18nUtil.getString("jobconf_trigger_type_api")),

    /** Misfire compensation trigger for missed execution */
    MISFIRE(I18nUtil.getString("jobconf_trigger_type_misfire"));

    private final String title;

    TriggerTypeEnum(String title) {
        this.title = title;
    }

    /**
     * Returns the internationalized display title for this trigger type.
     *
     * @return the localized display name
     */
    public String getTitle() {
        return title;
    }

    /**
     * Finds a trigger type by its enum name.
     *
     * @param name the enum constant name (e.g., "MANUAL", "CRON")
     * @return an Optional containing the matching trigger type, or empty if not found
     */
    public static Optional<TriggerTypeEnum> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(type -> type.name().equals(name)).findFirst();
    }
}
