package com.xxl.job.core.handler.impl;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.util.ScriptUtil;

/** Created by xuxueli on 17/4/27. */
public class ScriptJobHandler extends IJobHandler {

    /** ISO 8601 date-time formatter for environment variables */
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    private int jobId;
    private long glueUpdatetime;
    private String gluesource;
    private GlueTypeEnum glueType;

    public ScriptJobHandler(
            int jobId, long glueUpdatetime, String gluesource, GlueTypeEnum glueType) {
        this.jobId = jobId;
        this.glueUpdatetime = glueUpdatetime;
        this.gluesource = gluesource;
        this.glueType = glueType;

        // clean old script file
        File glueSrcPath = new File(XxlJobFileAppender.getGlueSrcPath());
        if (glueSrcPath.exists()) {
            File[] glueSrcFileList = glueSrcPath.listFiles();
            if (glueSrcFileList != null) {
                for (File glueSrcFileItem : glueSrcFileList) {
                    if (glueSrcFileItem.getName().startsWith(jobId + "_")) {
                        glueSrcFileItem.delete();
                    }
                }
            }
        }
    }

    public long getGlueUpdatetime() {
        return glueUpdatetime;
    }

    @Override
    public void execute() throws Exception {

        // valid
        if (!glueType.isScript()) {
            XxlJobHelper.handleFail("glueType[" + glueType + "] invalid.");
            return;
        }

        // cmd
        String cmd = glueType.getCmd();

        // make script file
        String scriptFileName =
                XxlJobFileAppender.getGlueSrcPath()
                        .concat(File.separator)
                        .concat(String.valueOf(jobId))
                        .concat("_")
                        .concat(String.valueOf(glueUpdatetime))
                        .concat(glueType.getSuffix());
        File scriptFile = new File(scriptFileName);
        if (!scriptFile.exists()) {
            ScriptUtil.markScriptFile(scriptFileName, gluesource);
        }

        // log file
        String logFileName = XxlJobContext.getXxlJobContext().getLogFileName();

        // script params：0=param、1=shardIndex、2=shardTotal
        String jobParam = XxlJobHelper.getJobParam();
        String[] scriptParams = new String[3];
        scriptParams[0] = jobParam != null ? jobParam : "";
        scriptParams[1] = String.valueOf(XxlJobContext.getXxlJobContext().getShardIndex());
        scriptParams[2] = String.valueOf(XxlJobContext.getXxlJobContext().getShardTotal());

        // Build environment variables for script execution
        Map<String, String> envVars = buildEnvironmentVariables();

        // invoke
        XxlJobHelper.log("----------- script file:" + scriptFileName + " -----------");
        int exitValue =
                ScriptUtil.execToFile(cmd, scriptFileName, logFileName, envVars, scriptParams);

        if (exitValue == 0) {
            XxlJobHelper.handleSuccess();
            return;
        } else {
            XxlJobHelper.handleFail("script exit value(" + exitValue + ") is failed");
            return;
        }
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
        XxlJobContext context = XxlJobContext.getXxlJobContext();
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
                    "ORTH_SCHEDULE_TIME",
                    ISO_FORMATTER.format(Instant.ofEpochMilli(scheduleTime)));
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
