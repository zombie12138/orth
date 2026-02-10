package com.xxl.job.admin.model.dto;

import java.util.List;

/**
 * Batch copy request for creating multiple SubTasks from a SuperTask template
 *
 * @author xxl-job
 */
public class BatchCopyRequest {

    /** Template job ID (will become SuperTask) */
    private int templateJobId;

    /** Mode: "simple" or "advanced" */
    private String mode;

    // ---------------------- Simple mode fields ----------------------

    /** List of super parameters (simple mode) */
    private List<String> params;

    /** Name template with placeholders: {origin}, {index} (simple mode) */
    private String nameTemplate;

    // ---------------------- Advanced mode fields ----------------------

    /** List of SubTask configurations (advanced mode) */
    private List<SubTaskConfig> tasks;

    // ---------------------- Common override fields ----------------------

    /** Job description override (optional) */
    private String jobDesc;

    /** Author override (optional) */
    private String author;

    /** Schedule configuration override (optional) */
    private String scheduleConf;

    /** Schedule type override (optional) */
    private String scheduleType;

    /** Alarm email override (optional) */
    private String alarmEmail;

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
}
