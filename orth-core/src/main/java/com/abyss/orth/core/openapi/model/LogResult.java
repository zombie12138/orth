package com.abyss.orth.core.openapi.model;

import java.io.Serializable;

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
 *   <li>{@link #isEnd} - Indicates if this is the last segment (job execution completed)
 * </ul>
 *
 * @author xuxueli 2017-03-23
 */
public class LogResult implements Serializable {
    private static final long serialVersionUID = 42L;

    /** Starting line number of this log segment (1-based) */
    private int fromLineNum;

    /** Ending line number of this log segment (1-based) */
    private int toLineNum;

    /** Log content for this segment */
    private String logContent;

    /** True if job execution is complete (no more log lines will be appended) */
    private boolean isEnd;

    /** Default constructor for JSON deserialization */
    public LogResult() {}

    /**
     * Constructs a log result with all fields.
     *
     * @param fromLineNum starting line number
     * @param toLineNum ending line number
     * @param logContent log content
     * @param isEnd true if this is the final segment
     */
    public LogResult(int fromLineNum, int toLineNum, String logContent, boolean isEnd) {
        this.fromLineNum = fromLineNum;
        this.toLineNum = toLineNum;
        this.logContent = logContent;
        this.isEnd = isEnd;
    }

    public int getFromLineNum() {
        return fromLineNum;
    }

    public void setFromLineNum(int fromLineNum) {
        this.fromLineNum = fromLineNum;
    }

    public int getToLineNum() {
        return toLineNum;
    }

    public void setToLineNum(int toLineNum) {
        this.toLineNum = toLineNum;
    }

    public String getLogContent() {
        return logContent;
    }

    public void setLogContent(String logContent) {
        this.logContent = logContent;
    }

    public boolean isEnd() {
        return isEnd;
    }

    public void setEnd(boolean end) {
        isEnd = end;
    }
}
