package com.xxl.job.admin.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch copy result containing success/fail counts and created job IDs
 *
 * @author xxl-job
 */
public class BatchCopyResult {

    /** Number of successfully created SubTasks */
    private int successCount;

    /** Number of failed creations */
    private int failCount;

    /** List of created job IDs */
    private List<Integer> createdJobIds;

    /** List of error messages */
    private List<String> errors;

    public BatchCopyResult() {
        this.createdJobIds = new ArrayList<>();
        this.errors = new ArrayList<>();
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

    public List<Integer> getCreatedJobIds() {
        return createdJobIds;
    }

    public void setCreatedJobIds(List<Integer> createdJobIds) {
        this.createdJobIds = createdJobIds;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public void addCreatedJobId(int jobId) {
        this.createdJobIds.add(jobId);
        this.successCount++;
    }

    public void addError(String error) {
        this.errors.add(error);
        this.failCount++;
    }
}
