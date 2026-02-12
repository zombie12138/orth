package com.xxl.job.admin.model.dto;

import java.util.List;

/**
 * Batch copy request DTO for creating multiple SubTask instances from a SuperTask template.
 *
 * <p>Supports two operational modes:
 *
 * <ul>
 *   <li><b>Simple Mode</b>: Generates SubTasks from a parameter list with auto-generated names
 *       using a name template with {origin} and {index} placeholders.
 *   <li><b>Advanced Mode</b>: Creates SubTasks with explicit configurations per task, allowing
 *       fine-grained control over each SubTask's properties.
 * </ul>
 *
 * <p>This DTO is part of the SuperTask-SubTask pattern in the Orth scheduling framework, enabling
 * efficient batch creation of related tasks for distributed data collection workflows.
 *
 * @author Orth Team
 * @since 3.3.0
 * @see SubTaskConfig
 * @see BatchCopyResult
 */
public class BatchCopyRequest {

    /** Supported batch copy modes */
    public static final String MODE_SIMPLE = "simple";

    public static final String MODE_ADVANCED = "advanced";

    /**
     * Template job ID that will be converted into a SuperTask. The template job's configuration
     * will be used as the baseline for all created SubTasks.
     */
    private int templateJobId;

    /**
     * Batch copy mode: "simple" or "advanced".
     *
     * <ul>
     *   <li><b>simple</b>: Uses {@code params} and {@code nameTemplate} for auto-generation
     *   <li><b>advanced</b>: Uses {@code tasks} list for explicit configuration
     * </ul>
     */
    private String mode;

    // ---------------------- Simple mode fields ----------------------

    /**
     * List of super parameters for SubTask creation (simple mode only).
     *
     * <p>Each parameter string becomes the {@code executorParam} for one SubTask. The order
     * determines the {index} placeholder value in name generation.
     */
    private List<String> params;

    /**
     * Name template with placeholders for auto-generation (simple mode only).
     *
     * <p>Supported placeholders:
     *
     * <ul>
     *   <li><b>{origin}</b>: Original template job name
     *   <li><b>{index}</b>: Zero-based index in the params list
     * </ul>
     *
     * <p>Example: "{origin}_subtask_{index}" with origin="DataCollection" generates
     * "DataCollection_subtask_0", "DataCollection_subtask_1", etc.
     */
    private String nameTemplate;

    // ---------------------- Advanced mode fields ----------------------

    /**
     * List of SubTask configurations with explicit per-task settings (advanced mode only).
     *
     * <p>Each SubTaskConfig can override template properties individually, providing maximum
     * flexibility for heterogeneous task batches.
     */
    private List<SubTaskConfig> tasks;

    // ---------------------- Common override fields ----------------------

    /**
     * Job description override applied to all created SubTasks (optional).
     *
     * <p>If null, inherits from template job. In simple mode, applies to all SubTasks. In advanced
     * mode, serves as default if individual SubTaskConfig doesn't specify.
     */
    private String jobDesc;

    /**
     * Author override applied to all created SubTasks (optional).
     *
     * <p>If null, inherits from template job. Useful for tracking batch creation ownership.
     */
    private String author;

    /**
     * Schedule configuration override (e.g., cron expression) for all SubTasks (optional).
     *
     * <p>If null, inherits from template job. Format depends on {@code scheduleType}.
     */
    private String scheduleConf;

    /**
     * Schedule type override (e.g., "CRON", "FIX_RATE", "NONE") for all SubTasks (optional).
     *
     * <p>If null, inherits from template job. Must match valid ScheduleTypeEnum values.
     */
    private String scheduleType;

    /**
     * Alarm email override for failure notifications (optional).
     *
     * <p>If null, inherits from template job. Supports comma-separated multiple addresses.
     */
    private String alarmEmail;

    /**
     * Validates the request structure and required fields based on the selected mode.
     *
     * @return true if the request is valid, false otherwise
     */
    public boolean isValid() {
        // Template job ID must be positive
        if (templateJobId <= 0) {
            return false;
        }

        // Mode must be specified and valid
        if (mode == null || (!MODE_SIMPLE.equals(mode) && !MODE_ADVANCED.equals(mode))) {
            return false;
        }

        // Validate mode-specific requirements
        if (MODE_SIMPLE.equals(mode)) {
            // Simple mode requires params and nameTemplate
            return params != null
                    && !params.isEmpty()
                    && nameTemplate != null
                    && !nameTemplate.trim().isEmpty();
        } else if (MODE_ADVANCED.equals(mode)) {
            // Advanced mode requires non-empty tasks list
            if (tasks == null || tasks.isEmpty()) {
                return false;
            }
            // Each task config must have a super parameter
            for (SubTaskConfig task : tasks) {
                if (task.getSuperTaskParam() == null || task.getSuperTaskParam().trim().isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    /**
     * Checks if this is a simple mode request.
     *
     * @return true if mode is "simple"
     */
    public boolean isSimpleMode() {
        return MODE_SIMPLE.equals(mode);
    }

    /**
     * Checks if this is an advanced mode request.
     *
     * @return true if mode is "advanced"
     */
    public boolean isAdvancedMode() {
        return MODE_ADVANCED.equals(mode);
    }

    /**
     * Gets the expected number of SubTasks to be created based on the request mode.
     *
     * @return expected SubTask count, or 0 if request is invalid
     */
    public int getExpectedSubTaskCount() {
        if (MODE_SIMPLE.equals(mode) && params != null) {
            return params.size();
        } else if (MODE_ADVANCED.equals(mode) && tasks != null) {
            return tasks.size();
        }
        return 0;
    }

    public int getTemplateJobId() {
        return templateJobId;
    }

    public void setTemplateJobId(int templateJobId) {
        this.templateJobId = templateJobId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<String> getParams() {
        return params;
    }

    public void setParams(List<String> params) {
        this.params = params;
    }

    public String getNameTemplate() {
        return nameTemplate;
    }

    public void setNameTemplate(String nameTemplate) {
        this.nameTemplate = nameTemplate;
    }

    public List<SubTaskConfig> getTasks() {
        return tasks;
    }

    public void setTasks(List<SubTaskConfig> tasks) {
        this.tasks = tasks;
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
        return "BatchCopyRequest{"
                + "templateJobId="
                + templateJobId
                + ", mode='"
                + mode
                + '\''
                + ", params="
                + (params != null ? params.size() + " items" : "null")
                + ", nameTemplate='"
                + nameTemplate
                + '\''
                + ", tasks="
                + (tasks != null ? tasks.size() + " items" : "null")
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
