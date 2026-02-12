package com.xxl.job.admin.model;

import java.util.Date;

/**
 * Daily job execution statistics report entity.
 *
 * <p>Tracks job execution metrics aggregated by day for monitoring and analytics.
 */
public class XxlJobLogReport {

    private int id;

    private Date triggerDay;

    private int runningCount;
    private int successCount;
    private int failCount;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Date getTriggerDay() {
        return triggerDay;
    }

    public void setTriggerDay(Date triggerDay) {
        this.triggerDay = triggerDay;
    }

    public int getRunningCount() {
        return runningCount;
    }

    public void setRunningCount(int runningCount) {
        this.runningCount = runningCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }
}
