package com.abyss.orth.admin.scheduler.trigger;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.abyss.orth.admin.mapper.JobGroupMapper;
import com.abyss.orth.admin.mapper.JobInfoMapper;
import com.abyss.orth.admin.mapper.JobLogMapper;
import com.abyss.orth.admin.model.JobGroup;
import com.abyss.orth.admin.model.JobInfo;
import com.abyss.orth.admin.model.JobLog;
import com.abyss.orth.admin.scheduler.config.OrthAdminBootstrap;
import com.abyss.orth.admin.scheduler.route.ExecutorRouteStrategyEnum;
import com.abyss.orth.admin.util.I18nUtil;
import com.abyss.orth.core.constant.ExecutorBlockStrategyEnum;
import com.abyss.orth.core.context.OrthJobContext;
import com.abyss.orth.core.openapi.ExecutorBiz;
import com.abyss.orth.core.openapi.model.TriggerRequest;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.exception.ThrowableTool;
import com.xxl.tool.http.IPTool;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;

/**
 * Job trigger coordinator that orchestrates job execution across distributed executors.
 *
 * <p>Handles trigger request routing, sharding, SuperTask inheritance, and executor communication.
 * Supports multiple routing strategies including round-robin, consistent hashing, failover, and
 * broadcast sharding.
 *
 * <p>Key responsibilities:
 *
 * <ul>
 *   <li>Load job and executor group configuration
 *   <li>Apply SuperTask template inheritance (code, handler, GLUE)
 *   <li>Route triggers to appropriate executors using configured strategy
 *   <li>Handle sharding broadcast for parallel execution across all executors
 *   <li>Create execution logs with trigger diagnostics
 *   <li>Communicate with remote executors via RPC
 * </ul>
 *
 * @author xuxueli 2017/7/13
 */
@Component
public class JobTrigger {
    private static final Logger logger = LoggerFactory.getLogger(JobTrigger.class);

    // Constants for sharding
    private static final int SHARDING_PARAM_PARTS = 2;
    private static final int SHARDING_INDEX_PART = 0;
    private static final int SHARDING_TOTAL_PART = 1;
    private static final String SHARDING_DELIMITER = "/";
    private static final int DEFAULT_SHARDING_INDEX = 0;
    private static final int DEFAULT_SHARDING_TOTAL = 1;

    // Constants for address types
    private static final int ADDRESS_TYPE_AUTO = 0;
    private static final int ADDRESS_TYPE_MANUAL = 1;

    // Constants for log messages
    private static final String LOG_TRIGGER_START = ">>>>>>>>>>> orth trigger start, jobId:{}";
    private static final String LOG_TRIGGER_END = ">>>>>>>>>>> orth trigger end, jobId:{}";
    private static final String LOG_TRIGGER_ERROR =
            ">>>>>>>>>>> orth trigger error, please check if the executor[{}] is running.";
    private static final String LOG_JOB_INVALID = ">>>>>>>>>>> orth trigger fail, invalid jobId={}";
    private static final String LOG_SUPERTASK_NOT_FOUND =
            ">>>>>>>>>>> orth trigger fail, SuperTask not found, jobId={}, superTaskId={}";

    // Constants for error messages
    private static final String MSG_ADDRESS_ROUTER_FAIL = "Address router failed";
    private static final String MSG_ADDRESS_ROUTE_FAIL_PREFIX = "address route fail";
    private static final String MSG_ADDRESS_ROUTE_FAIL_DEFAULT = "address route fail.";
    private static final String MSG_TRIGGER_SUCCESS = "success";
    private static final String MSG_TRIGGER_ERROR_PREFIX = "error";
    private static final String MSG_TRIGGER_FAIL = "fail";

    // HTML formatting constants
    private static final String HTML_LINE_BREAK = "<br>";
    private static final String HTML_COLON = "：";
    private static final String HTML_SEPARATOR_START =
            "<br><br><span style=\"color:#00c0ef;\" > >>>>>>>>>>>";
    private static final String HTML_SEPARATOR_END = "<<<<<<<<<<< </span><br>";
    private static final String HTML_PAREN_OPEN = "(";
    private static final String HTML_PAREN_CLOSE = ")";

    // Constants for labels
    private static final String LABEL_JOB_HANDLER = "JobHandler";
    private static final String LABEL_ADDRESS = "address";
    private static final String LABEL_CODE = "code";
    private static final String LABEL_MSG = "msg";

    @Resource private JobInfoMapper jobInfoMapper;
    @Resource private JobGroupMapper jobGroupMapper;
    @Resource private JobLogMapper jobLogMapper;

    /**
     * Triggers job execution on remote executors.
     *
     * <p>This is the main entry point for all job triggers. It handles job loading, SuperTask
     * inheritance, sharding parameter parsing, and routing logic.
     *
     * @param jobId unique identifier of the job to trigger
     * @param triggerType the type of trigger (CRON, MANUAL, API, RETRY, PARENT, etc.)
     * @param failRetryCount number of retry attempts on failure; if negative, uses job
     *     configuration
     * @param executorShardingParam explicit sharding parameter in "index/total" format; if null,
     *     computed automatically
     * @param executorParam runtime execution parameter; if non-null, overrides job's configured
     *     parameter
     * @param addressList comma-separated executor addresses; if non-null, overrides executor group
     *     registry
     * @param scheduleTime theoretical schedule time in milliseconds since epoch; null for
     *     manual/API triggers
     */
    public void trigger(
            int jobId,
            TriggerTypeEnum triggerType,
            int failRetryCount,
            String executorShardingParam,
            String executorParam,
            String addressList,
            Long scheduleTime) {

        // Guard: Load job data
        JobInfo jobInfo = jobInfoMapper.loadById(jobId);
        if (jobInfo == null) {
            logger.warn(LOG_JOB_INVALID, jobId);
            return;
        }

        // Apply runtime parameter override
        if (executorParam != null) {
            jobInfo.setExecutorParam(executorParam);
        }

        // Guard: Apply SuperTask inheritance
        if (!applySuperTaskInheritance(jobInfo)) {
            return; // SuperTask not found, already logged
        }

        // Resolve final retry count
        int finalFailRetryCount = resolveFailRetryCount(failRetryCount, jobInfo);

        // Load executor group
        JobGroup group = jobGroupMapper.load(jobInfo.getJobGroup());

        // Apply manual address override
        applyAddressOverride(group, addressList);

        // Parse and route trigger
        int[] shardingParam = parseShardingParam(executorShardingParam);
        routeTrigger(jobInfo, group, finalFailRetryCount, triggerType, shardingParam, scheduleTime);
    }

    /**
     * Applies SuperTask template inheritance if job is a SubTask.
     *
     * <p>SubTasks inherit code-related fields (handler, GLUE type, source, update time) from their
     * SuperTask parent while maintaining their own scheduling and parameter configuration.
     *
     * @param jobInfo the job to check and modify
     * @return true if no SuperTask or inheritance succeeded; false if SuperTask reference is
     *     invalid
     */
    private boolean applySuperTaskInheritance(JobInfo jobInfo) {
        if (jobInfo.getSuperTaskId() == null || jobInfo.getSuperTaskId() <= 0) {
            return true; // Not a SubTask, no inheritance needed
        }

        // Self-reference guard: treat as standalone
        if (jobInfo.getSuperTaskId().equals(jobInfo.getId())) {
            return true;
        }

        JobInfo superTask = jobInfoMapper.loadById(jobInfo.getSuperTaskId());
        if (superTask == null) {
            logger.warn(LOG_SUPERTASK_NOT_FOUND, jobInfo.getId(), jobInfo.getSuperTaskId());
            return false;
        }

        // Inherit code-related fields from SuperTask template
        jobInfo.setExecutorHandler(superTask.getExecutorHandler());
        jobInfo.setGlueType(superTask.getGlueType());
        jobInfo.setGlueSource(superTask.getGlueSource());
        jobInfo.setGlueUpdatetime(superTask.getGlueUpdatetime());
        return true;
    }

    /**
     * Resolves the effective fail retry count.
     *
     * @param requestedRetryCount the retry count from trigger request
     * @param jobInfo the job configuration
     * @return the requested count if non-negative, otherwise job's configured count
     */
    private int resolveFailRetryCount(int requestedRetryCount, JobInfo jobInfo) {
        return requestedRetryCount >= 0 ? requestedRetryCount : jobInfo.getExecutorFailRetryCount();
    }

    /**
     * Applies manual address list override to executor group.
     *
     * @param group the executor group to modify
     * @param addressList comma-separated addresses; if blank, no override applied
     */
    private void applyAddressOverride(JobGroup group, String addressList) {
        if (StringTool.isNotBlank(addressList)) {
            group.setAddressType(ADDRESS_TYPE_MANUAL);
            group.setAddressList(addressList.trim());
        }
    }

    /**
     * Parses sharding parameter from string format "index/total".
     *
     * @param executorShardingParam the sharding parameter string
     * @return int array [index, total] if valid; null if invalid or null input
     */
    private int[] parseShardingParam(String executorShardingParam) {
        if (executorShardingParam == null) {
            return null;
        }

        String[] parts = executorShardingParam.split(SHARDING_DELIMITER);
        if (parts.length != SHARDING_PARAM_PARTS) {
            return null;
        }

        if (!StringTool.isNumeric(parts[SHARDING_INDEX_PART])
                || !StringTool.isNumeric(parts[SHARDING_TOTAL_PART])) {
            return null;
        }

        return new int[] {
            Integer.parseInt(parts[SHARDING_INDEX_PART]),
            Integer.parseInt(parts[SHARDING_TOTAL_PART])
        };
    }

    /**
     * Routes trigger to executors based on routing strategy and sharding configuration.
     *
     * @param jobInfo the job configuration
     * @param group the executor group
     * @param finalFailRetryCount the resolved retry count
     * @param triggerType the trigger type
     * @param shardingParam explicit sharding parameter; null for auto-routing
     * @param scheduleTime theoretical schedule time
     */
    private void routeTrigger(
            JobInfo jobInfo,
            JobGroup group,
            int finalFailRetryCount,
            TriggerTypeEnum triggerType,
            int[] shardingParam,
            Long scheduleTime) {

        ExecutorRouteStrategyEnum routeStrategy =
                ExecutorRouteStrategyEnum.match(jobInfo.getExecutorRouteStrategy(), null);

        // Check for sharding broadcast mode
        boolean isShardingBroadcast = ExecutorRouteStrategyEnum.SHARDING_BROADCAST == routeStrategy;
        boolean hasRegistryList =
                group.getRegistryList() != null && !group.getRegistryList().isEmpty();
        boolean needsBroadcast = isShardingBroadcast && hasRegistryList && shardingParam == null;

        if (needsBroadcast) {
            // Broadcast to all executors with individual shard indices
            broadcastToAllExecutors(group, jobInfo, finalFailRetryCount, triggerType, scheduleTime);
        } else {
            // Single executor routing
            int[] effectiveSharding =
                    shardingParam != null
                            ? shardingParam
                            : new int[] {DEFAULT_SHARDING_INDEX, DEFAULT_SHARDING_TOTAL};
            processTrigger(
                    group,
                    jobInfo,
                    finalFailRetryCount,
                    triggerType,
                    effectiveSharding[SHARDING_INDEX_PART],
                    effectiveSharding[SHARDING_TOTAL_PART],
                    scheduleTime);
        }
    }

    /**
     * Broadcasts trigger to all registered executors with unique shard indices.
     *
     * @param group the executor group with registry list
     * @param jobInfo the job configuration
     * @param finalFailRetryCount the retry count
     * @param triggerType the trigger type
     * @param scheduleTime theoretical schedule time
     */
    private void broadcastToAllExecutors(
            JobGroup group,
            JobInfo jobInfo,
            int finalFailRetryCount,
            TriggerTypeEnum triggerType,
            Long scheduleTime) {

        List<String> registryList = group.getRegistryList();
        int totalShards = registryList.size();

        for (int shardIndex = 0; shardIndex < totalShards; shardIndex++) {
            processTrigger(
                    group,
                    jobInfo,
                    finalFailRetryCount,
                    triggerType,
                    shardIndex,
                    totalShards,
                    scheduleTime);
        }
    }

    /**
     * Processes a single trigger execution including logging and remote RPC call.
     *
     * <p>Creates execution log, resolves executor address via routing strategy, sends trigger to
     * executor, and records detailed diagnostics for troubleshooting.
     *
     * @param group executor group (registry list may be empty)
     * @param jobInfo job configuration
     * @param finalFailRetryCount the resolved fail-retry count
     * @param triggerType the trigger type
     * @param shardIndex shard index for broadcast routing (0-based)
     * @param shardTotal total shard count for broadcast routing
     * @param scheduleTime theoretical schedule time in milliseconds; null for manual/API triggers
     */
    private void processTrigger(
            JobGroup group,
            JobInfo jobInfo,
            int finalFailRetryCount,
            TriggerTypeEnum triggerType,
            int shardIndex,
            int shardTotal,
            Long scheduleTime) {

        // Resolve execution strategies
        ExecutorBlockStrategyEnum blockStrategy =
                ExecutorBlockStrategyEnum.match(
                        jobInfo.getExecutorBlockStrategy(),
                        ExecutorBlockStrategyEnum.SERIAL_EXECUTION);
        ExecutorRouteStrategyEnum routeStrategy =
                ExecutorRouteStrategyEnum.match(jobInfo.getExecutorRouteStrategy(), null);

        // Format sharding parameter for broadcast mode
        String shardingParam = formatShardingParam(routeStrategy, shardIndex, shardTotal);

        // Step 1: Create execution log
        JobLog jobLog = createJobLog(jobInfo, scheduleTime);
        logger.debug(LOG_TRIGGER_START, jobLog.getId());

        // Step 2: Build trigger request
        TriggerRequest triggerRequest =
                buildTriggerRequest(
                        jobInfo, jobLog, shardIndex, shardTotal, scheduleTime, shardingParam);

        // Step 3: Resolve executor address
        AddressResolutionResult addressResult =
                resolveExecutorAddress(group, routeStrategy, shardIndex, triggerRequest);

        // Step 4: Trigger remote executor
        Response<String> triggerResult = executeTrigger(triggerRequest, addressResult.address);

        // Step 5: Build diagnostics message
        String diagnosticsMessage =
                buildDiagnosticsMessage(
                        triggerType,
                        group,
                        routeStrategy,
                        blockStrategy,
                        jobInfo,
                        finalFailRetryCount,
                        shardingParam,
                        addressResult,
                        triggerResult);

        // Step 6: Update execution log
        updateJobLog(
                jobLog,
                addressResult.address,
                jobInfo,
                shardingParam,
                finalFailRetryCount,
                triggerResult,
                diagnosticsMessage);

        logger.debug(LOG_TRIGGER_END, jobLog.getId());
    }

    /**
     * Formats sharding parameter string for broadcast mode.
     *
     * @param routeStrategy the routing strategy
     * @param shardIndex the shard index
     * @param shardTotal the total shard count
     * @return formatted "index/total" string for broadcast; null otherwise
     */
    private String formatShardingParam(
            ExecutorRouteStrategyEnum routeStrategy, int shardIndex, int shardTotal) {
        if (ExecutorRouteStrategyEnum.SHARDING_BROADCAST != routeStrategy) {
            return null;
        }
        return String.valueOf(shardIndex) + SHARDING_DELIMITER + String.valueOf(shardTotal);
    }

    /**
     * Creates and persists a new job execution log.
     *
     * @param jobInfo the job configuration
     * @param scheduleTime theoretical schedule time; null for manual triggers
     * @return the persisted job log with generated ID
     */
    private JobLog createJobLog(JobInfo jobInfo, Long scheduleTime) {
        JobLog jobLog = new JobLog();
        jobLog.setJobGroup(jobInfo.getJobGroup());
        jobLog.setJobId(jobInfo.getId());
        jobLog.setTriggerTime(new Date());

        if (scheduleTime != null) {
            jobLog.setScheduleTime(new Date(scheduleTime));
        }

        jobLogMapper.save(jobLog);
        return jobLog;
    }

    /**
     * Builds trigger request with all execution parameters.
     *
     * @param jobInfo the job configuration
     * @param jobLog the execution log
     * @param shardIndex the shard index
     * @param shardTotal the total shard count
     * @param scheduleTime theoretical schedule time
     * @param shardingParam formatted sharding parameter
     * @return the configured trigger request
     */
    private TriggerRequest buildTriggerRequest(
            JobInfo jobInfo,
            JobLog jobLog,
            int shardIndex,
            int shardTotal,
            Long scheduleTime,
            String shardingParam) {

        TriggerRequest request = new TriggerRequest();
        request.setJobId(jobInfo.getId());
        request.setExecutorHandler(jobInfo.getExecutorHandler());
        request.setExecutorParams(jobInfo.getExecutorParam());
        request.setExecutorBlockStrategy(jobInfo.getExecutorBlockStrategy());
        request.setExecutorConcurrency(jobInfo.getExecutorConcurrency());
        request.setExecutorTimeout(jobInfo.getExecutorTimeout());
        request.setLogId(jobLog.getId());
        request.setLogDateTime(jobLog.getTriggerTime().getTime());
        request.setGlueType(jobInfo.getGlueType());
        request.setGlueSource(jobInfo.getGlueSource());
        request.setGlueUpdatetime(jobInfo.getGlueUpdatetime().getTime());
        request.setBroadcastIndex(shardIndex);
        request.setBroadcastTotal(shardTotal);
        request.setScheduleTime(scheduleTime);
        return request;
    }

    /**
     * Resolves executor address using routing strategy.
     *
     * @param group the executor group
     * @param routeStrategy the routing strategy
     * @param shardIndex the shard index for broadcast mode
     * @param triggerRequest the trigger request for routing context
     * @return address resolution result containing address and routing response
     */
    private AddressResolutionResult resolveExecutorAddress(
            JobGroup group,
            ExecutorRouteStrategyEnum routeStrategy,
            int shardIndex,
            TriggerRequest triggerRequest) {

        List<String> registryList = group.getRegistryList();

        // Guard: Check if registry list is empty
        if (registryList == null || registryList.isEmpty()) {
            Response<String> errorResponse =
                    Response.of(
                            OrthJobContext.HANDLE_CODE_FAIL,
                            I18nUtil.getString("jobconf_trigger_address_empty"));
            return new AddressResolutionResult(null, errorResponse);
        }

        // Sharding broadcast: Direct index mapping
        if (ExecutorRouteStrategyEnum.SHARDING_BROADCAST == routeStrategy) {
            String address = resolveShardingAddress(registryList, shardIndex);
            return new AddressResolutionResult(address, Response.ofSuccess(address));
        }

        // Other strategies: Use router
        Response<String> routeResult =
                routeStrategy.getRouter().route(triggerRequest, registryList);

        String address = routeResult.isSuccess() ? routeResult.getData() : null;
        return new AddressResolutionResult(address, routeResult);
    }

    /**
     * Resolves executor address for sharding broadcast mode.
     *
     * @param registryList the list of registered executors
     * @param shardIndex the shard index
     * @return the executor address at shard index, or first address if index out of bounds
     */
    private String resolveShardingAddress(List<String> registryList, int shardIndex) {
        if (shardIndex < registryList.size()) {
            return registryList.get(shardIndex);
        }
        return registryList.get(0); // Fallback to first executor
    }

    /**
     * Executes trigger on remote executor or returns failure response.
     *
     * @param triggerRequest the trigger request containing job parameters
     * @param address the executor address; null if routing failed
     * @return trigger execution result
     */
    private Response<String> executeTrigger(TriggerRequest triggerRequest, String address) {
        if (address == null) {
            return Response.of(OrthJobContext.HANDLE_CODE_FAIL, MSG_ADDRESS_ROUTER_FAIL);
        }

        Response<String> runResult;
        try {
            ExecutorBiz executorBiz = OrthAdminBootstrap.getExecutorBiz(address);
            runResult = executorBiz.run(triggerRequest);
        } catch (Exception e) {
            logger.error(LOG_TRIGGER_ERROR, address, e);
            runResult = Response.of(OrthJobContext.HANDLE_CODE_FAIL, ThrowableTool.toString(e));
        }
        return formatRPCResult(runResult, address);
    }

    /**
     * Formats RPC result with execution details.
     *
     * @param runResult the raw RPC result
     * @param address the executor address
     * @return formatted result with HTML diagnostics
     */
    private Response<String> formatRPCResult(Response<String> runResult, String address) {
        StringBuilder resultBuilder =
                new StringBuilder(I18nUtil.getString("jobconf_trigger_run") + HTML_COLON);
        resultBuilder
                .append(HTML_LINE_BREAK)
                .append(LABEL_ADDRESS)
                .append(HTML_COLON)
                .append(address);
        resultBuilder
                .append(HTML_LINE_BREAK)
                .append(LABEL_CODE)
                .append(HTML_COLON)
                .append(runResult.getCode());
        resultBuilder
                .append(HTML_LINE_BREAK)
                .append(LABEL_MSG)
                .append(HTML_COLON)
                .append(runResult.getMsg());

        runResult.setMsg(resultBuilder.toString());
        return runResult;
    }

    /**
     * Builds comprehensive diagnostics message for execution log.
     *
     * @param triggerType the trigger type
     * @param group the executor group
     * @param routeStrategy the routing strategy
     * @param blockStrategy the block strategy
     * @param jobInfo the job configuration
     * @param finalFailRetryCount the retry count
     * @param shardingParam the sharding parameter
     * @param addressResult the address resolution result
     * @param triggerResult the trigger execution result
     * @return HTML-formatted diagnostics message
     */
    private String buildDiagnosticsMessage(
            TriggerTypeEnum triggerType,
            JobGroup group,
            ExecutorRouteStrategyEnum routeStrategy,
            ExecutorBlockStrategyEnum blockStrategy,
            JobInfo jobInfo,
            int finalFailRetryCount,
            String shardingParam,
            AddressResolutionResult addressResult,
            Response<String> triggerResult) {

        StringBuilder msg = new StringBuilder();

        // Configuration section
        appendTriggerConfig(
                msg,
                triggerType,
                group,
                routeStrategy,
                blockStrategy,
                jobInfo,
                finalFailRetryCount,
                shardingParam);

        // Execution section
        appendExecutionResult(msg, addressResult, jobInfo, triggerResult);

        return msg.toString();
    }

    /** Appends trigger configuration section to diagnostics message. */
    private void appendTriggerConfig(
            StringBuilder msg,
            TriggerTypeEnum triggerType,
            JobGroup group,
            ExecutorRouteStrategyEnum routeStrategy,
            ExecutorBlockStrategyEnum blockStrategy,
            JobInfo jobInfo,
            int finalFailRetryCount,
            String shardingParam) {

        // Trigger type
        msg.append(I18nUtil.getString("jobconf_trigger_type"))
                .append(HTML_COLON)
                .append(triggerType.getTitle());

        // Admin address
        msg.append(HTML_LINE_BREAK)
                .append(I18nUtil.getString("jobconf_trigger_admin_adress"))
                .append(HTML_COLON)
                .append(IPTool.getIp());

        // Registry type
        msg.append(HTML_LINE_BREAK)
                .append(I18nUtil.getString("jobconf_trigger_exe_regtype"))
                .append(HTML_COLON)
                .append(
                        group.getAddressType() == ADDRESS_TYPE_AUTO
                                ? I18nUtil.getString("jobgroup_field_addressType_0")
                                : I18nUtil.getString("jobgroup_field_addressType_1"));

        // Registry addresses
        msg.append(HTML_LINE_BREAK)
                .append(I18nUtil.getString("jobconf_trigger_exe_regaddress"))
                .append(HTML_COLON)
                .append(group.getRegistryList());

        // Route strategy
        msg.append(HTML_LINE_BREAK)
                .append(I18nUtil.getString("jobinfo_field_executorRouteStrategy"))
                .append(HTML_COLON)
                .append(routeStrategy.getTitle());
        if (shardingParam != null) {
            msg.append(HTML_PAREN_OPEN).append(shardingParam).append(HTML_PAREN_CLOSE);
        }

        // Block strategy
        msg.append(HTML_LINE_BREAK)
                .append(I18nUtil.getString("jobinfo_field_executorBlockStrategy"))
                .append(HTML_COLON)
                .append(blockStrategy.getTitle());

        // Timeout
        msg.append(HTML_LINE_BREAK)
                .append(I18nUtil.getString("jobinfo_field_timeout"))
                .append(HTML_COLON)
                .append(jobInfo.getExecutorTimeout());

        // Retry count
        msg.append(HTML_LINE_BREAK)
                .append(I18nUtil.getString("jobinfo_field_executorFailRetryCount"))
                .append(HTML_COLON)
                .append(finalFailRetryCount);
    }

    /** Appends execution result section to diagnostics message. */
    private void appendExecutionResult(
            StringBuilder msg,
            AddressResolutionResult addressResult,
            JobInfo jobInfo,
            Response<String> triggerResult) {

        // Section separator
        msg.append(HTML_SEPARATOR_START)
                .append(I18nUtil.getString("jobconf_trigger_run"))
                .append(HTML_SEPARATOR_END);

        // Executor address
        msg.append(HTML_LINE_BREAK)
                .append(I18nUtil.getString("joblog_field_executorAddress"))
                .append(HTML_COLON);

        if (StringTool.isNotBlank(addressResult.address)) {
            msg.append(addressResult.address);
        } else {
            msg.append(MSG_ADDRESS_ROUTE_FAIL_PREFIX);
            if (addressResult.routeResponse != null && !addressResult.routeResponse.isSuccess()) {
                String routeMsg = addressResult.routeResponse.getMsg();
                if (routeMsg != null) {
                    msg.append(", ").append(routeMsg);
                }
            } else {
                msg.append(".");
            }
        }

        // Job handler
        if (StringTool.isNotBlank(jobInfo.getExecutorHandler())) {
            msg.append(HTML_LINE_BREAK)
                    .append(LABEL_JOB_HANDLER)
                    .append(HTML_COLON)
                    .append(jobInfo.getExecutorHandler());
        }

        // Executor parameters
        msg.append(HTML_LINE_BREAK)
                .append(I18nUtil.getString("jobinfo_field_executorparam"))
                .append(HTML_COLON)
                .append(jobInfo.getExecutorParam());

        // Trigger result
        msg.append(HTML_LINE_BREAK)
                .append(I18nUtil.getString("joblog_field_triggerMsg"))
                .append(HTML_COLON);

        if (triggerResult.isSuccess()) {
            msg.append(MSG_TRIGGER_SUCCESS);
        } else {
            msg.append(MSG_TRIGGER_ERROR_PREFIX);
            String errorMsg = triggerResult.getMsg();
            if (errorMsg != null) {
                msg.append(", ").append(errorMsg);
            }
        }
    }

    /**
     * Updates job log with execution results.
     *
     * @param jobLog the job log to update
     * @param address the resolved executor address
     * @param jobInfo the job configuration
     * @param shardingParam the sharding parameter
     * @param finalFailRetryCount the retry count
     * @param triggerResult the trigger execution result
     * @param diagnosticsMessage the formatted diagnostics message
     */
    private void updateJobLog(
            JobLog jobLog,
            String address,
            JobInfo jobInfo,
            String shardingParam,
            int finalFailRetryCount,
            Response<String> triggerResult,
            String diagnosticsMessage) {

        jobLog.setExecutorAddress(address);
        jobLog.setExecutorHandler(jobInfo.getExecutorHandler());
        jobLog.setExecutorParam(jobInfo.getExecutorParam());
        jobLog.setExecutorShardingParam(shardingParam);
        jobLog.setExecutorFailRetryCount(finalFailRetryCount);
        jobLog.setTriggerCode(triggerResult.getCode());
        jobLog.setTriggerMsg(diagnosticsMessage);

        jobLogMapper.updateTriggerInfo(jobLog);
    }

    /** Result of address resolution containing both address and routing response. */
    private static class AddressResolutionResult {
        final String address;
        final Response<String> routeResponse;

        AddressResolutionResult(String address, Response<String> routeResponse) {
            this.address = address;
            this.routeResponse = routeResponse;
        }
    }
}
