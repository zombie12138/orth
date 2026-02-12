package com.xxl.job.admin.model.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Batch copy result DTO containing operation statistics and outcomes for SubTask creation.
 *
 * <p>This result object aggregates success/failure counts, created job IDs, and detailed error
 * messages from batch SubTask creation operations. It supports incremental result building via
 * {@link #addCreatedJobId(int)} and {@link #addError(String)} methods.
 *
 * <p>Typical usage pattern:
 *
 * <pre>{@code
 * BatchCopyResult result = new BatchCopyResult();
 * for (SubTaskConfig config : request.getTasks()) {
 *     try {
 *         int jobId = createSubTask(config);
 *         result.addCreatedJobId(jobId);
 *     } catch (Exception e) {
 *         result.addError("Failed to create task: " + e.getMessage());
 *     }
 * }
 * }</pre>
 *
 * @author Orth Team
 * @since 3.3.0
 * @see BatchCopyRequest
 */
public class BatchCopyResult {

    /**
     * Number of successfully created SubTasks. Automatically incremented when {@link
     * #addCreatedJobId(int)} is called.
     */
    private int successCount;

    /**
     * Number of failed SubTask creation attempts. Automatically incremented when {@link
     * #addError(String)} is called.
     */
    private int failCount;

    /**
     * Ordered list of successfully created job IDs. The order reflects the creation sequence, which
     * may be useful for debugging or post-processing.
     */
    private List<Integer> createdJobIds;

    /**
     * List of error messages for failed creation attempts. Each message should provide context
     * about the failure (e.g., which parameter/config caused the error).
     */
    private List<String> errors;

    /**
     * Constructs a new BatchCopyResult with empty collections. Use {@link #addCreatedJobId(int)}
     * and {@link #addError(String)} to populate results incrementally.
     */
    public BatchCopyResult() {
        this.createdJobIds = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    /**
     * Checks if the batch operation was completely successful (no failures).
     *
     * @return true if failCount is 0, false otherwise
     */
    public boolean isFullSuccess() {
        return failCount == 0;
    }

    /**
     * Checks if the batch operation was completely failed (no successes).
     *
     * @return true if successCount is 0, false otherwise
     */
    public boolean isFullFailure() {
        return successCount == 0;
    }

    /**
     * Checks if the batch operation had partial success (some succeeded, some failed).
     *
     * @return true if both successCount and failCount are greater than 0
     */
    public boolean isPartialSuccess() {
        return successCount > 0 && failCount > 0;
    }

    /**
     * Gets the total number of operations attempted (success + failure).
     *
     * @return total operation count
     */
    public int getTotalCount() {
        return successCount + failCount;
    }

    /**
     * Records a successful SubTask creation and increments the success counter.
     *
     * @param jobId the ID of the successfully created job (must be positive)
     * @throws IllegalArgumentException if jobId is not positive
     */
    public void addCreatedJobId(int jobId) {
        if (jobId <= 0) {
            throw new IllegalArgumentException("Job ID must be positive, got: " + jobId);
        }
        this.createdJobIds.add(jobId);
        this.successCount++;
    }

    /**
     * Records a failure with an error message and increments the failure counter.
     *
     * @param error descriptive error message (should not be null or empty for meaningful logs)
     */
    public void addError(String error) {
        if (error == null || error.trim().isEmpty()) {
            error = "Unknown error"; // Defensive fallback for null/empty errors
        }
        this.errors.add(error);
        this.failCount++;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        if (successCount < 0) {
            throw new IllegalArgumentException("Success count cannot be negative");
        }
        this.successCount = successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        if (failCount < 0) {
            throw new IllegalArgumentException("Fail count cannot be negative");
        }
        this.failCount = failCount;
    }

    /**
     * Gets an unmodifiable view of created job IDs to prevent external modification.
     *
     * @return unmodifiable list of created job IDs
     */
    public List<Integer> getCreatedJobIds() {
        return Collections.unmodifiableList(createdJobIds);
    }

    /**
     * Replaces the internal job ID list (for deserialization or batch updates).
     *
     * @param createdJobIds new list of job IDs
     */
    public void setCreatedJobIds(List<Integer> createdJobIds) {
        this.createdJobIds = createdJobIds != null ? createdJobIds : new ArrayList<>();
    }

    /**
     * Gets an unmodifiable view of error messages to prevent external modification.
     *
     * @return unmodifiable list of error messages
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * Replaces the internal error list (for deserialization or batch updates).
     *
     * @param errors new list of error messages
     */
    public void setErrors(List<String> errors) {
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "BatchCopyResult{"
                + "successCount="
                + successCount
                + ", failCount="
                + failCount
                + ", createdJobIds="
                + createdJobIds
                + ", errors="
                + errors
                + ", totalCount="
                + getTotalCount()
                + ", status="
                + (isFullSuccess()
                        ? "FULL_SUCCESS"
                        : isFullFailure() ? "FULL_FAILURE" : "PARTIAL_SUCCESS")
                + '}';
    }
}
