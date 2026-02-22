package com.abyss.orth.admin.model;

import java.util.Date;

import lombok.Data;

/**
 * Daily job execution statistics report entity.
 *
 * <p>Tracks job execution metrics aggregated by day for monitoring and analytics.
 */
@Data
public class JobLogReport {

    private int id;

    private Date triggerDay;

    private int runningCount;
    private int successCount;
    private int failCount;
}
