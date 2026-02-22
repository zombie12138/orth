package com.abyss.orth.admin.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.abyss.orth.admin.model.JobInfo;
import com.abyss.orth.admin.model.dto.BatchCopyRequest;
import com.abyss.orth.admin.model.dto.BatchCopyResult;
import com.abyss.orth.admin.web.security.JwtUserInfo;
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
public interface JobService {

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
    Response<PageModel<JobInfo>> pageList(
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
     * @param jobInfo job configuration to create
     * @param userInfo current user's information
     * @return job ID if successful, error message otherwise
     */
    Response<String> add(JobInfo jobInfo, JwtUserInfo userInfo);

    /**
     * Updates existing job configuration.
     *
     * @param jobInfo job configuration with updates
     * @param userInfo current user's information
     * @return success or error message
     */
    Response<String> update(JobInfo jobInfo, JwtUserInfo userInfo);

    /**
     * Removes job and all associated data.
     *
     * @param id job ID to remove
     * @param userInfo current user's information
     * @return success or error message
     */
    Response<String> remove(int id, JwtUserInfo userInfo);

    /**
     * Starts job scheduling.
     *
     * @param id job ID to start
     * @param userInfo current user's information
     * @return success or error message
     */
    Response<String> start(int id, JwtUserInfo userInfo);

    /**
     * Stops job scheduling.
     *
     * @param id job ID to stop
     * @param userInfo current user's information
     * @return success or error message
     */
    Response<String> stop(int id, JwtUserInfo userInfo);

    /**
     * Manually triggers immediate job execution.
     *
     * @param userInfo current user's information
     * @param jobId job ID to trigger
     * @param executorParam custom executor parameters (overrides job config)
     * @param addressList target executor address (null for routing strategy)
     * @return success or error message
     */
    Response<String> trigger(
            JwtUserInfo userInfo, int jobId, String executorParam, String addressList);

    /**
     * Triggers batch job executions with logical schedule times.
     *
     * @param userInfo current user's information
     * @param jobId job ID to trigger
     * @param executorParam custom executor parameters
     * @param addressList target executor address
     * @param startTime batch start time (first schedule time)
     * @param endTime batch end time (last schedule time, required for CRON/FIX_RATE)
     * @return number of instances triggered or error message
     */
    Response<String> triggerBatch(
            JwtUserInfo userInfo,
            int jobId,
            String executorParam,
            String addressList,
            Date startTime,
            Date endTime);

    /**
     * Previews schedule times for batch trigger without executing.
     *
     * @param userInfo current user's information
     * @param jobId job ID to preview
     * @param startTime batch start time
     * @param endTime batch end time
     * @return list of formatted schedule times
     */
    Response<List<String>> previewTriggerBatch(
            JwtUserInfo userInfo, int jobId, Date startTime, Date endTime);

    /**
     * Retrieves dashboard summary statistics.
     *
     * @return map with dashboard statistics
     */
    Map<String, Object> dashboardInfo();

    /**
     * Retrieves execution statistics for chart display.
     *
     * @param startDate chart start date
     * @param endDate chart end date
     * @return map with chart data (daily counts and totals)
     */
    Response<Map<String, Object>> chartInfo(Date startDate, Date endDate);

    /**
     * Batch creates jobs from template (SuperTask pattern).
     *
     * @param request batch copy configuration
     * @return result with created job IDs and any errors
     */
    BatchCopyResult batchCopy(BatchCopyRequest request);
}
