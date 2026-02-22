package com.abyss.orth.core.openapi.model;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Log retrieval request.
 *
 * <p>The admin sends this request to executors to retrieve job execution log content. Logs are
 * retrieved in chunks for streaming display in the admin UI.
 *
 * <p>Pagination:
 *
 * <ul>
 *   <li>{@link #fromLineNum} - Start reading from this line (0=start of file)
 *   <li>Response includes actual range read and whether execution is complete
 * </ul>
 *
 * <p>Log file location:
 *
 * <pre>
 * {logPath}/{yyyy-MM-dd}/{logId}.log
 * </pre>
 *
 * @author xuxueli 2020-04-11 22:27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogRequest implements Serializable {
    private static final long serialVersionUID = 42L;

    /** Log creation timestamp (used to determine log file date directory) */
    private long logDateTim;

    /** Log ID (used as log file name) */
    private long logId;

    /** Starting line number to read from (0-based) */
    private int fromLineNum;
}
