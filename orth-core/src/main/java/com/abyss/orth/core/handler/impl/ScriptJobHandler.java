package com.abyss.orth.core.handler.impl;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.abyss.orth.core.context.OrthJobContext;
import com.abyss.orth.core.context.OrthJobHelper;
import com.abyss.orth.core.glue.GlueTypeEnum;
import com.abyss.orth.core.handler.IJobHandler;
import com.abyss.orth.core.log.OrthJobFileAppender;
import com.abyss.orth.core.util.ScriptUtil;

/**
 * Job handler for executing external scripts (Shell, Python, NodeJS, PHP, PowerShell).
 *
 * <p>This handler supports GLUE-mode dynamic scripts that are stored in the database and written to
 * local filesystem before execution. Scripts are executed as OS processes via {@link ScriptUtil}
 * using the target machine's installed interpreters.
 *
 * <p><strong>Lifecycle:</strong>
 *
 * <ol>
 *   <li>Constructor: Clean old script files for same jobId
 *   <li>Execute: Write script to disk → Build env vars → Execute via ProcessBuilder → Capture exit
 *       code
 *   <li>Script files persist on disk for caching (same glueUpdatetime = cache hit)
 * </ol>
 *
 * <p><strong>Environment variables passed to scripts:</strong>
 *
 * <ul>
 *   <li>ORTH_JOB_ID: Job ID
 *   <li>ORTH_JOB_PARAM: Job parameters
 *   <li>ORTH_LOG_ID: Log ID for correlation
 *   <li>ORTH_SCHEDULE_TIME: Theoretical schedule time (ISO 8601, empty for manual triggers)
 *   <li>ORTH_TRIGGER_TIME: Actual trigger time (ISO 8601)
 *   <li>ORTH_SHARD_INDEX: Current shard index (0-based)
 *   <li>ORTH_SHARD_TOTAL: Total shard count
 *   <li>ORTH_SUPER_TASK_PARAM: SuperTask parameter (empty for standalone jobs)
 * </ul>
 *
 * <p><strong>Script parameters (positional args):</strong>
 *
 * <ul>
 *   <li>$1 / sys.argv[1]: Job parameters (executorParams)
 *   <li>$2 / sys.argv[2]: Shard index
 *   <li>$3 / sys.argv[3]: Shard total
 * </ul>
 *
 * @author xuxueli
 * @since 1.0.0
 */
public class ScriptJobHandler extends IJobHandler {

    /** ISO 8601 date-time formatter for environment variables */
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    /** Number of script positional parameters (jobParam, shardIndex, shardTotal) */
    private static final int SCRIPT_PARAM_COUNT = 3;

    /** Exit code indicating successful script execution */
    private static final int EXIT_CODE_SUCCESS = 0;

    /** Job ID (used for script file naming: {jobId}_{glueUpdatetime}{suffix}) */
    private final int jobId;

    /** GLUE script last update timestamp (milliseconds, used for cache invalidation) */
    private final long glueUpdatetime;

    /** GLUE script source code (max 64KB) */
    private final String gluesource;

    /** GLUE type (GLUE_SHELL, GLUE_PYTHON, GLUE_NODEJS, GLUE_PHP, GLUE_POWERSHELL) */
    private final GlueTypeEnum glueType;

    /**
     * Constructs a new script job handler and cleans old script files.
     *
     * <p>Cleanup strategy: Deletes all previous script versions for this jobId to prevent disk
     * bloat. Script files are named {jobId}_{glueUpdatetime}{suffix}, so outdated versions have
     * different timestamps.
     *
     * @param jobId job ID (must be positive)
     * @param glueUpdatetime GLUE script update timestamp (milliseconds)
     * @param gluesource GLUE script source code (must not be null or empty)
     * @param glueType GLUE type (must be a script type, not BEAN)
     * @throws IllegalArgumentException if glueType is not a script type
     */
    public ScriptJobHandler(
            int jobId, long glueUpdatetime, String gluesource, GlueTypeEnum glueType) {
        if (glueType == null || !glueType.isScript()) {
            throw new IllegalArgumentException(
                    "Invalid glueType for ScriptJobHandler: " + glueType);
        }
        if (gluesource == null || gluesource.trim().isEmpty()) {
            throw new IllegalArgumentException("Script source code cannot be null or empty");
        }

        this.jobId = jobId;
        this.glueUpdatetime = glueUpdatetime;
        this.gluesource = gluesource;
        this.glueType = glueType;

        // Clean old script files for this jobId to prevent disk bloat
        cleanOldScriptFiles();
    }

    /**
     * Deletes old script files for this jobId (previous glueUpdatetime versions).
     *
     * <p>This prevents disk bloat when scripts are frequently updated. Files are named
     * {jobId}_{glueUpdatetime}{suffix}, so different timestamps indicate old versions.
     */
    private void cleanOldScriptFiles() {
        File glueSrcPath = new File(OrthJobFileAppender.getGlueSrcPath());
        if (!glueSrcPath.exists()) {
            return;
        }

        String filePrefix = jobId + "_";
        File[] glueSrcFileList = glueSrcPath.listFiles();
        if (glueSrcFileList == null) {
            return;
        }

        for (File glueSrcFileItem : glueSrcFileList) {
            if (glueSrcFileItem.getName().startsWith(filePrefix)) {
                if (!glueSrcFileItem.delete()) {
                    OrthJobHelper.log(
                            "Failed to delete old script file: " + glueSrcFileItem.getName());
                }
            }
        }
    }

    /**
     * Gets GLUE script update timestamp for cache comparison.
     *
     * @return GLUE update timestamp in milliseconds
     */
    public long getGlueUpdatetime() {
        return glueUpdatetime;
    }

    /**
     * Executes the GLUE script with environment variables and positional parameters.
     *
     * <p><strong>Execution flow:</strong>
     *
     * <ol>
     *   <li>Validate glueType is a script type (guard clause)
     *   <li>Write script to disk if not cached (same jobId + glueUpdatetime = cache hit)
     *   <li>Build environment variables (ORTH_* env vars)
     *   <li>Build positional parameters (jobParam, shardIndex, shardTotal)
     *   <li>Execute script via ProcessBuilder, stream output to log file
     *   <li>Handle exit code: 0 = success, non-zero = failure
     * </ol>
     *
     * <p><strong>Security notes:</strong>
     *
     * <ul>
     *   <li>Scripts execute with same permissions as executor process (no sandboxing)
     *   <li>Admin users can inject arbitrary code via GLUE editor
     *   <li>Script timeout is enforced by executor timeout setting
     * </ul>
     *
     * @throws Exception if script file creation or execution fails
     */
    @Override
    public void execute() throws Exception {
        // Guard clause: Validate glueType (should never fail due to constructor validation)
        if (!glueType.isScript()) {
            OrthJobHelper.handleFail(
                    "Invalid glueType for script execution: "
                            + glueType
                            + ". Expected GLUE_SHELL, GLUE_PYTHON, etc.");
            return;
        }

        // Get interpreter command (e.g., "bash", "python3", "node")
        String cmd = glueType.getCmd();

        // Build script file path: {glueSrcPath}/{jobId}_{glueUpdatetime}{suffix}
        String scriptFileName = buildScriptFilePath();

        // Write script to disk if not cached (idempotent)
        ensureScriptFileExists(scriptFileName);

        // Get log file path for stdout/stderr redirection
        String logFileName = OrthJobContext.getOrthJobContext().getLogFileName();

        // Build positional parameters: [jobParam, shardIndex, shardTotal]
        String[] scriptParams = buildScriptParameters();

        // Build environment variables: ORTH_* env vars
        Map<String, String> envVars = buildEnvironmentVariables();

        // Execute script and capture exit code
        OrthJobHelper.log("----------- orth script file: " + scriptFileName + " -----------");
        int exitValue =
                ScriptUtil.execToFile(cmd, scriptFileName, logFileName, envVars, scriptParams);

        // Handle exit code
        if (exitValue == EXIT_CODE_SUCCESS) {
            OrthJobHelper.handleSuccess();
        } else {
            OrthJobHelper.handleFail("orth script execution failed with exit code: " + exitValue);
        }
    }

    /**
     * Builds the full script file path using jobId, glueUpdatetime, and file suffix.
     *
     * <p>Path format: {glueSrcPath}/{jobId}_{glueUpdatetime}{suffix}
     *
     * <p>Example: /data/applogs/orth/gluesource/666_1234567890.py
     *
     * @return absolute script file path
     */
    private String buildScriptFilePath() {
        return OrthJobFileAppender.getGlueSrcPath()
                + File.separator
                + jobId
                + "_"
                + glueUpdatetime
                + glueType.getSuffix();
    }

    /**
     * Ensures script file exists on disk (writes if missing, skips if cached).
     *
     * <p>Cache hit: Same jobId + glueUpdatetime → file already exists → skip write
     *
     * <p>Cache miss: New glueUpdatetime → file doesn't exist → write source code to disk
     *
     * @param scriptFileName absolute script file path
     * @throws Exception if file creation fails
     */
    private void ensureScriptFileExists(String scriptFileName) throws Exception {
        File scriptFile = new File(scriptFileName);
        if (!scriptFile.exists()) {
            ScriptUtil.markScriptFile(scriptFileName, gluesource);
            OrthJobHelper.log("orth script file created (cache miss): " + scriptFileName);
        } else {
            OrthJobHelper.log("orth script file cached (cache hit): " + scriptFileName);
        }
    }

    /**
     * Builds positional parameters for script execution.
     *
     * <p>Parameters: [jobParam, shardIndex, shardTotal]
     *
     * <p>Scripts access these as: $1/$2/$3 (Shell), sys.argv[1]/[2]/[3] (Python), process.argv[2]
     * (NodeJS)
     *
     * @return script positional parameters (length = 3)
     */
    private String[] buildScriptParameters() {
        OrthJobContext context = OrthJobContext.getOrthJobContext();
        String[] scriptParams = new String[SCRIPT_PARAM_COUNT];
        scriptParams[0] = OrthJobHelper.getJobParam() != null ? OrthJobHelper.getJobParam() : "";
        scriptParams[1] = String.valueOf(context.getShardIndex());
        scriptParams[2] = String.valueOf(context.getShardTotal());
        return scriptParams;
    }

    /**
     * Build environment variables map from job context for script execution.
     *
     * <p>Environment variables set:
     *
     * <ul>
     *   <li>ORTH_JOB_ID: Job ID
     *   <li>ORTH_JOB_PARAM: Job parameters
     *   <li>ORTH_LOG_ID: Log ID
     *   <li>ORTH_SCHEDULE_TIME: Theoretical schedule time (ISO 8601), empty for manual triggers
     *   <li>ORTH_TRIGGER_TIME: Actual trigger time (ISO 8601)
     *   <li>ORTH_SHARD_INDEX: Shard index
     *   <li>ORTH_SHARD_TOTAL: Shard total
     * </ul>
     *
     * @return environment variables map
     */
    private Map<String, String> buildEnvironmentVariables() {
        OrthJobContext context = OrthJobContext.getOrthJobContext();
        Map<String, String> envVars = new HashMap<>();

        // Job info
        envVars.put("ORTH_JOB_ID", String.valueOf(context.getJobId()));
        envVars.put("ORTH_JOB_PARAM", context.getJobParam() != null ? context.getJobParam() : "");

        // Log info
        envVars.put("ORTH_LOG_ID", String.valueOf(context.getLogId()));

        // Schedule time (ISO 8601 format, empty for manual triggers)
        Long scheduleTime = context.getScheduleTime();
        if (scheduleTime != null) {
            envVars.put(
                    "ORTH_SCHEDULE_TIME", ISO_FORMATTER.format(Instant.ofEpochMilli(scheduleTime)));
        } else {
            envVars.put("ORTH_SCHEDULE_TIME", "");
        }

        // Trigger time (actual dispatch time, ISO 8601 format)
        envVars.put(
                "ORTH_TRIGGER_TIME",
                ISO_FORMATTER.format(Instant.ofEpochMilli(context.getLogDateTime())));

        // Shard info
        envVars.put("ORTH_SHARD_INDEX", String.valueOf(context.getShardIndex()));
        envVars.put("ORTH_SHARD_TOTAL", String.valueOf(context.getShardTotal()));

        return envVars;
    }
}
