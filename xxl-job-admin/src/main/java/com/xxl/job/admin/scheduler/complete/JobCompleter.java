package com.xxl.job.admin.scheduler.complete;

import java.text.MessageFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.xxl.job.admin.mapper.XxlJobInfoMapper;
import com.xxl.job.admin.mapper.XxlJobLogMapper;
import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.model.XxlJobLog;
import com.xxl.job.admin.scheduler.config.XxlJobAdminBootstrap;
import com.xxl.job.admin.scheduler.trigger.TriggerTypeEnum;
import com.xxl.job.admin.util.I18nUtil;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;

/**
 * Job Completion Handler
 *
 * <p>Manages job completion state transitions and child job triggering. This component handles the
 * final state of job execution, including:
 *
 * <ul>
 *   <li>Updating job log with execution results
 *   <li>Triggering child jobs on successful completion
 *   <li>Truncating oversized execution messages
 * </ul>
 *
 * <p><b>State Machine Flow:</b>
 *
 * <ol>
 *   <li>Job execution completes (success/fail)
 *   <li>Process child jobs (if success and configured)
 *   <li>Truncate execution message if needed
 *   <li>Persist final state to database
 * </ol>
 *
 * <p><b>Thread Safety:</b> This component is stateless and thread-safe. Multiple concurrent job
 * completions are supported.
 *
 * <p><b>Failure Handling:</b> Child job triggering failures do not affect parent job completion
 * status. Each child trigger is isolated.
 *
 * @author xuxueli 2020-10-30 20:43:10
 * @since 3.3.0
 */
@Component
public class JobCompleter {
    private static final Logger logger = LoggerFactory.getLogger(JobCompleter.class);

    // Constants for message handling
    private static final int MAX_HANDLE_MSG_LENGTH = 15000;
    private static final String CHILD_JOB_DELIMITER = ",";
    private static final int INVALID_JOB_ID = -1;
    private static final int SHARD_INDEX_NOT_SPECIFIED = -1;

    // HTML formatting for child job trigger messages
    private static final String CHILD_TRIGGER_HEADER_TEMPLATE =
            "<br><br><span style=\"color:#00c0ef;\"> >>>>>>>>>>>"
                    + "%s"
                    + "<<<<<<<<<<< </span><br>";

    @Resource private XxlJobInfoMapper xxlJobInfoMapper;
    @Resource private XxlJobLogMapper xxlJobLogMapper;

    /**
     * Completes a job execution and persists the final state.
     *
     * <p>This is the main entry point for job completion. It orchestrates:
     *
     * <ol>
     *   <li>Child job processing (triggers on success)
     *   <li>Message truncation (prevents database overflow)
     *   <li>State persistence (updates xxl_job_log)
     * </ol>
     *
     * <p><b>Important:</b> This method should be called only once per job execution to avoid
     * duplicate child job triggers.
     *
     * @param xxlJobLog the job log with execution results (handle code, message, time)
     * @return number of rows updated (1 on success, 0 if log not found)
     */
    public int complete(XxlJobLog xxlJobLog) {
        // Process child jobs first (may append to handle message)
        processChildJob(xxlJobLog);

        // Truncate handle message if it exceeds database limit (TEXT = 64KB)
        truncateHandleMessageIfNeeded(xxlJobLog);

        // Persist final state to database
        return xxlJobLogMapper.updateHandleInfo(xxlJobLog);
    }

    /**
     * Triggers child jobs if execution succeeded and child jobs are configured.
     *
     * <p><b>Trigger Conditions:</b>
     *
     * <ul>
     *   <li>Parent job executed successfully (HANDLE_CODE_SUCCESS)
     *   <li>Job definition has non-empty childJobId field
     *   <li>Child job ID is valid (positive integer)
     *   <li>Child job ID != parent job ID (prevents self-loop)
     * </ul>
     *
     * <p><b>Child Job Semantics:</b> Child jobs are triggered with:
     *
     * <ul>
     *   <li>Trigger type: PARENT
     *   <li>Schedule time: inherited from parent (preserves logical data slot)
     *   <li>Executor params: inherited from parent job definition
     * </ul>
     *
     * <p><b>Side Effects:</b> Appends child trigger results to parent job's handle message.
     *
     * @param xxlJobLog the parent job log (modified in-place)
     */
    private void processChildJob(XxlJobLog xxlJobLog) {
        // Guard clause: only process children on successful execution
        if (xxlJobLog.getHandleCode() != XxlJobContext.HANDLE_CODE_SUCCESS) {
            return;
        }

        XxlJobInfo xxlJobInfo = xxlJobInfoMapper.loadById(xxlJobLog.getJobId());

        // Guard clause: job definition not found
        if (xxlJobInfo == null) {
            return;
        }

        // Guard clause: no child jobs configured
        if (StringTool.isBlank(xxlJobInfo.getChildJobId())) {
            return;
        }

        // Propagate parent's schedule time to child jobs
        Long scheduleTime =
                xxlJobLog.getScheduleTime() != null ? xxlJobLog.getScheduleTime().getTime() : null;

        // Trigger all child jobs and collect results
        String triggerResultMessage =
                triggerChildJobs(xxlJobInfo, xxlJobLog.getJobId(), scheduleTime);

        // Append trigger results to parent job message
        xxlJobLog.setHandleMsg(xxlJobLog.getHandleMsg() + triggerResultMessage);
    }

    /**
     * Triggers all configured child jobs and builds formatted result message.
     *
     * @param parentJobInfo parent job definition with child job IDs
     * @param parentJobId parent job ID (used to prevent self-triggering)
     * @param scheduleTime parent's logical schedule time in millis (may be null)
     * @return formatted HTML message with trigger results
     */
    private String triggerChildJobs(XxlJobInfo parentJobInfo, int parentJobId, Long scheduleTime) {
        StringBuilder messageBuilder = new StringBuilder();

        // Add header
        String header =
                String.format(
                        CHILD_TRIGGER_HEADER_TEMPLATE,
                        I18nUtil.getString("jobconf_trigger_child_run"));
        messageBuilder.append(header);

        // Parse child job IDs
        String[] childJobIds = parentJobInfo.getChildJobId().split(CHILD_JOB_DELIMITER);

        // Trigger each child job
        for (int i = 0; i < childJobIds.length; i++) {
            String childIdStr = childJobIds[i].trim();
            int childIndex = i + 1;

            // Parse child job ID
            int childJobId = parseChildJobId(childIdStr);

            if (childJobId == INVALID_JOB_ID) {
                // Invalid child ID
                appendInvalidChildMessage(
                        messageBuilder, childIndex, childJobIds.length, childIdStr);
                continue;
            }

            if (childJobId == parentJobId) {
                // Self-reference detected, skip to prevent infinite loop
                logger.debug(
                        "orth scheduler: JobCompleter skipping child job ID {} (self-reference)",
                        childJobId);
                continue;
            }

            // Trigger child job
            triggerChild(
                    childJobId,
                    messageBuilder,
                    childIndex,
                    childJobIds.length,
                    childIdStr,
                    scheduleTime);
        }

        return messageBuilder.toString();
    }

    /**
     * Parses a child job ID string to integer.
     *
     * @param childIdStr child job ID as string
     * @return parsed job ID, or INVALID_JOB_ID if parsing fails
     */
    private int parseChildJobId(String childIdStr) {
        if (StringTool.isBlank(childIdStr) || !StringTool.isNumeric(childIdStr)) {
            return INVALID_JOB_ID;
        }

        try {
            int jobId = Integer.parseInt(childIdStr);
            return jobId > 0 ? jobId : INVALID_JOB_ID;
        } catch (NumberFormatException e) {
            return INVALID_JOB_ID;
        }
    }

    /**
     * Triggers a single child job and appends result message.
     *
     * @param childJobId child job ID to trigger
     * @param messageBuilder message builder for results
     * @param childIndex current child index (1-based)
     * @param totalChildren total number of children
     * @param childIdStr child job ID as string (for display)
     * @param scheduleTime parent's logical schedule time in millis (may be null)
     */
    private void triggerChild(
            int childJobId,
            StringBuilder messageBuilder,
            int childIndex,
            int totalChildren,
            String childIdStr,
            Long scheduleTime) {

        // Trigger child job, preserving parent's schedule time
        XxlJobAdminBootstrap.getInstance()
                .getJobTriggerPoolHelper()
                .trigger(
                        childJobId,
                        TriggerTypeEnum.PARENT,
                        SHARD_INDEX_NOT_SPECIFIED,
                        null, // executorShardingParam
                        null, // executorParam (inherited from job definition)
                        null, // addressList
                        scheduleTime);

        // Build result message (currently always success, async trigger)
        Response<String> triggerResult = Response.ofSuccess();

        String resultMsg =
                MessageFormat.format(
                        I18nUtil.getString("jobconf_callback_child_msg1"),
                        childIndex,
                        totalChildren,
                        childIdStr,
                        triggerResult.isSuccess()
                                ? I18nUtil.getString("system_success")
                                : I18nUtil.getString("system_fail"),
                        triggerResult.getMsg());

        messageBuilder.append(resultMsg);
    }

    /**
     * Appends a message for an invalid child job ID.
     *
     * @param messageBuilder message builder
     * @param childIndex current child index (1-based)
     * @param totalChildren total number of children
     * @param childIdStr invalid child job ID string
     */
    private void appendInvalidChildMessage(
            StringBuilder messageBuilder, int childIndex, int totalChildren, String childIdStr) {
        String invalidMsg =
                MessageFormat.format(
                        I18nUtil.getString("jobconf_callback_child_msg2"),
                        childIndex,
                        totalChildren,
                        childIdStr);
        messageBuilder.append(invalidMsg);
    }

    /**
     * Truncates handle message if it exceeds database limit.
     *
     * <p><b>Rationale:</b> MySQL TEXT column supports up to 64KB. Truncating at 15,000 characters
     * provides safety margin for multi-byte UTF-8 characters.
     *
     * @param xxlJobLog job log (modified in-place)
     */
    private void truncateHandleMessageIfNeeded(XxlJobLog xxlJobLog) {
        String handleMsg = xxlJobLog.getHandleMsg();
        if (handleMsg != null && handleMsg.length() > MAX_HANDLE_MSG_LENGTH) {
            xxlJobLog.setHandleMsg(handleMsg.substring(0, MAX_HANDLE_MSG_LENGTH));
        }
    }
}
