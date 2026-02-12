package com.xxl.job.admin.constant;

/**
 * Job trigger status enumeration.
 *
 * <p>Defines the execution status of scheduled jobs in the Orth system. A job can be either stopped
 * (inactive, not scheduled) or running (active, being scheduled).
 *
 * @author xuxueli 2018-01-17
 */
public enum TriggerStatus {
    /** Job is stopped and will not be scheduled for execution */
    STOPPED(0, "stopped"),

    /** Job is running and will be scheduled according to its configuration */
    RUNNING(1, "running");

    private final int value;
    private final String desc;

    TriggerStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    /**
     * Gets the numeric value of this status.
     *
     * @return status value (0 for stopped, 1 for running)
     */
    public int getValue() {
        return value;
    }

    /**
     * Gets the description of this status.
     *
     * @return status description
     */
    public String getDesc() {
        return desc;
    }
}
