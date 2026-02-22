package com.abyss.orth.admin.model.dto;

/**
 * SubTask configuration DTO for advanced batch copy mode.
 *
 * <p>This DTO represents the configuration for a single SubTask to be created from a SuperTask
 * template. It is used exclusively in {@link BatchCopyRequest#MODE_ADVANCED} mode, where each
 * SubTask can have individually customized properties.
 *
 * <p>All fields are optional overrides. If not specified, the SubTask inherits the corresponding
 * value from the SuperTask template or the BatchCopyRequest's common override fields.
 *
 * <p>Inheritance hierarchy (highest to lowest priority):
 *
 * <ol>
 *   <li>SubTaskConfig-specific value (this object's fields)
 *   <li>BatchCopyRequest common override value
 *   <li>SuperTask template value
 * </ol>
 *
 * @author Orth Team
 * @since 3.3.0
 * @see BatchCopyRequest
 */
public class SubTaskConfig {

    /**
     * Executor parameter for this SubTask. This is the primary use case for Fork SuperTask: same
     * code, different parameters (e.g., different download paths, data shards, region codes).
     */
    private String executorParam;

    /**
     * Job description override for this specific SubTask (optional).
     *
     * <p>If null, falls back to BatchCopyRequest's {@code jobDesc}, then to the SuperTask
     * template's description.
     */
    private String jobDesc;

    /**
     * Author override for this specific SubTask (optional).
     *
     * <p>If null, falls back to BatchCopyRequest's {@code author}, then to the SuperTask template's
     * author.
     */
    private String author;

    /**
     * Schedule configuration override (e.g., cron expression, fixed rate interval) for this
     * specific SubTask (optional).
     *
     * <p>If null, falls back to BatchCopyRequest's {@code scheduleConf}, then to the SuperTask
     * template's schedule configuration. Format depends on {@code scheduleType}.
     */
    private String scheduleConf;

    /**
     * Schedule type override (e.g., "CRON", "FIX_RATE", "NONE") for this specific SubTask
     * (optional).
     *
     * <p>If null, falls back to BatchCopyRequest's {@code scheduleType}, then to the SuperTask
     * template's schedule type. Must match valid ScheduleTypeEnum values.
     */
    private String scheduleType;

    /**
     * Alarm email override for failure notifications (optional).
     *
     * <p>If null, falls back to BatchCopyRequest's {@code alarmEmail}, then to the SuperTask
     * template's alarm email. Supports comma-separated multiple addresses.
     */
    private String alarmEmail;

    /**
     * Checks if this config has any override values set.
     *
     * @return true if at least one override field is non-null
     */
    public boolean hasOverrides() {
        return executorParam != null
                || jobDesc != null
                || author != null
                || scheduleConf != null
                || scheduleType != null
                || alarmEmail != null;
    }

    public String getExecutorParam() {
        return executorParam;
    }

    public void setExecutorParam(String executorParam) {
        this.executorParam = executorParam;
    }

    public String getJobDesc() {
        return jobDesc;
    }

    public void setJobDesc(String jobDesc) {
        this.jobDesc = jobDesc;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getScheduleConf() {
        return scheduleConf;
    }

    public void setScheduleConf(String scheduleConf) {
        this.scheduleConf = scheduleConf;
    }

    public String getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(String scheduleType) {
        this.scheduleType = scheduleType;
    }

    public String getAlarmEmail() {
        return alarmEmail;
    }

    public void setAlarmEmail(String alarmEmail) {
        this.alarmEmail = alarmEmail;
    }

    @Override
    public String toString() {
        return "SubTaskConfig{"
                + "executorParam='"
                + executorParam
                + '\''
                + ", jobDesc='"
                + jobDesc
                + '\''
                + ", author='"
                + author
                + '\''
                + ", scheduleConf='"
                + scheduleConf
                + '\''
                + ", scheduleType='"
                + scheduleType
                + '\''
                + ", alarmEmail='"
                + alarmEmail
                + '\''
                + '}';
    }
}
