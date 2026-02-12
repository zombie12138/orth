package com.xxl.job.admin.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.model.dto.BatchCopyRequest;
import com.xxl.job.admin.model.dto.BatchCopyResult;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.tool.response.PageModel;
import com.xxl.tool.response.Response;

/**
 * Core job management service for Orth.
 *
 * <p>Provides comprehensive job lifecycle management including CRUD operations, scheduling control,
 * manual triggers, and batch operations. All operations enforce permission checks based on user
 * roles and job group assignments.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>Job configuration management (add, update, remove)
 *   <li>Schedule control (start, stop)
 *   <li>Manual trigger (immediate and batch with schedule times)
 *   <li>Dashboard and analytics (statistics, execution charts)
 *   <li>Batch operations (template-based job creation)
 * </ul>
 *
 * @author xuxueli 2016-5-28 15:30:33
 */
public interface XxlJobService {

    /**
     * Retrieves paginated list of jobs with optional filters.
     *
     * @param offset starting offset for pagination
     * @param pagesize number of records per page
     * @param jobGroup job group filter (0 for all)
     * @param triggerStatus trigger status filter (-1 for all)
     * @param jobDesc job description search term
     * @param executorHandler handler name search term
     * @param author author search term
     * @return paginated job list
     */
    Response<PageModel<XxlJobInfo>> pageList(
            int offset,
            int pagesize,
            int jobGroup,
            int triggerStatus,
            String jobDesc,
            String executorHandler,
            String author,
            String superTaskName);

    /**
     * Creates a new job configuration.
     *
     * <p>Validates all job parameters including schedule config, glue type, routing strategy, and
     * child job dependencies. Permissions are checked based on user's job group access.
     *
     * @param jobInfo job configuration to create
     * @param loginInfo current user's login information
     * @return job ID if successful, error message otherwise
     */
    Response<String> add(XxlJobInfo jobInfo, LoginInfo loginInfo);

    /**
     * Updates existing job configuration.
     *
     * <p>Validates changes and recalculates next trigger time if schedule configuration changed.
     * Permissions are checked based on user's job group access.
     *
     * @param jobInfo job configuration with updates
     * @param loginInfo current user's login information
     * @return success or error message
     */
    Response<String> update(XxlJobInfo jobInfo, LoginInfo loginInfo);

    /**
     * Removes job and all associated data.
     *
     * <p>Deletes job configuration, execution logs, and glue logs. SuperTasks with existing
     * SubTasks cannot be deleted until associations are removed.
     *
     * @param id job ID to remove
     * @param loginInfo current user's login information
     * @return success or error message
     */
    Response<String> remove(int id, LoginInfo loginInfo);

    /**
     * Starts job scheduling.
     *
     * <p>Activates job for scheduling and calculates next trigger time. Jobs with NONE schedule
     * type cannot be started.
     *
     * @param id job ID to start
     * @param loginInfo current user's login information
     * @return success or error message
     */
    Response<String> start(int id, LoginInfo loginInfo);

    /**
     * Stops job scheduling.
     *
     * <p>Deactivates job and clears trigger times. Running job instances will complete normally.
     *
     * @param id job ID to stop
     * @param loginInfo current user's login information
     * @return success or error message
     */
    Response<String> stop(int id, LoginInfo loginInfo);

    /**
     * Manually triggers immediate job execution.
     *
     * <p>Executes job once immediately without schedule time. Can optionally override executor
     * parameters and target specific executor address.
     *
     * @param loginInfo current user's login information
     * @param jobId job ID to trigger
     * @param executorParam custom executor parameters (overrides job config)
     * @param addressList target executor address (null for routing strategy)
     * @return success or error message
     */
    Response<String> trigger(
            LoginInfo loginInfo, int jobId, String executorParam, String addressList);

    /**
     * Triggers batch job executions with logical schedule times.
     *
     * <p>Generates and triggers multiple job instances based on schedule configuration within the
     * specified time range. Each instance has a logical schedule time for backfilling scenarios.
     * Maximum 100 instances per batch.
     *
     * @param loginInfo current user's login information
     * @param jobId job ID to trigger
     * @param executorParam custom executor parameters
     * @param addressList target executor address
     * @param startTime batch start time (first schedule time)
     * @param endTime batch end time (last schedule time, required for CRON/FIX_RATE)
     * @return number of instances triggered or error message
     */
    Response<String> triggerBatch(
            LoginInfo loginInfo,
            int jobId,
            String executorParam,
            String addressList,
            Date startTime,
            Date endTime);

    /**
     * Previews schedule times for batch trigger without executing.
     *
     * <p>Calculates and returns the list of schedule times that would be used for batch trigger,
     * allowing users to verify before execution.
     *
     * @param loginInfo current user's login information
     * @param jobId job ID to preview
     * @param startTime batch start time
     * @param endTime batch end time
     * @return list of formatted schedule times
     */
    Response<List<String>> previewTriggerBatch(
            LoginInfo loginInfo, int jobId, Date startTime, Date endTime);

    /**
     * Retrieves dashboard summary statistics.
     *
     * <p>Returns counts for total jobs, execution logs, successful executions, and active
     * executors.
     *
     * @return map with dashboard statistics
     */
    Map<String, Object> dashboardInfo();

    /**
     * Retrieves execution statistics for chart display.
     *
     * <p>Returns daily execution counts (running, success, failure) for the specified date range.
     *
     * @param startDate chart start date
     * @param endDate chart end date
     * @return map with chart data (daily counts and totals)
     */
    Response<Map<String, Object>> chartInfo(Date startDate, Date endDate);

    /**
     * Batch creates jobs from template (SuperTask pattern).
     *
     * <p>Creates multiple SubTask instances from a template job with varying configurations. Useful
     * for creating parallel data collection jobs with different parameters.
     *
     * @param request batch copy configuration
     * @return result with created job IDs and any errors
     */
    BatchCopyResult batchCopy(BatchCopyRequest request);
}
