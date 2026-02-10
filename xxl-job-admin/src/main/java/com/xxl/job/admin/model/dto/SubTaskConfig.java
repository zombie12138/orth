package com.xxl.job.admin.model.dto;

/**
 * SubTask configuration for advanced batch copy mode
 *
 * @author xxl-job
 */
public class SubTaskConfig {

    /** Executor parameter (optional override) */
    private String executorParam;

    /** Job description (optional override) */
    private String jobDesc;

    /** Author (optional override) */
    private String author;

    /** Schedule configuration (optional override) */
    private String scheduleConf;

    /** Schedule type (optional override) */
    private String scheduleType;

    /** Alarm email (optional override) */
    private String alarmEmail;

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
}
