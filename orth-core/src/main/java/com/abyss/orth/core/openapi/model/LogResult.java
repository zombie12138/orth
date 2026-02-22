package com.abyss.orth.core.openapi.model;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Job execution log query result.
 *
 * <p>This model represents a segment of job execution log content with pagination support. It's
 * used by the /log endpoint to retrieve log content in chunks.
 *
 * <p>Pagination:
 *
 * <ul>
 *   <li>{@link #fromLineNum} - Starting line number (inclusive)
 *   <li>{@link #toLineNum} - Ending line number (inclusive)
 *   <li>{@link #end} - Indicates if this is the last segment (job execution completed)
 * </ul>
 *
 * @author xuxueli 2017-03-23
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogResult implements Serializable {
    private static final long serialVersionUID = 42L;

    /** Starting line number of this log segment (1-based) */
    private int fromLineNum;

    /** Ending line number of this log segment (1-based) */
    private int toLineNum;

    /** Log content for this segment */
    private String logContent;

    /** True if job execution is complete (no more log lines will be appended) */
    private boolean end;
}
