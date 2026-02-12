package com.xxl.job.admin.controller.biz;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.xxl.job.admin.mapper.XxlJobGroupMapper;
import com.xxl.job.admin.mapper.XxlJobInfoMapper;
import com.xxl.job.admin.model.XxlJobGroup;
import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.model.dto.BatchCopyRequest;
import com.xxl.job.admin.model.dto.BatchCopyResult;
import com.xxl.job.admin.scheduler.exception.XxlJobException;
import com.xxl.job.admin.scheduler.misfire.MisfireStrategyEnum;
import com.xxl.job.admin.scheduler.route.ExecutorRouteStrategyEnum;
import com.xxl.job.admin.scheduler.type.ScheduleTypeEnum;
import com.xxl.job.admin.service.XxlJobService;
import com.xxl.job.admin.util.I18nUtil;
import com.xxl.job.admin.util.JobGroupPermissionUtil;
import com.xxl.job.core.constant.ExecutorBlockStrategyEnum;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.sso.core.helper.XxlSsoHelper;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.tool.core.CollectionTool;
import com.xxl.tool.core.DateTool;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.response.PageModel;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Job info controller for managing job configurations.
 *
 * <p>Handles operations including:
 *
 * <ul>
 *   <li>CRUD operations for job definitions
 *   <li>Starting/stopping jobs
 *   <li>Triggering jobs manually (single and batch)
 *   <li>Importing/exporting job configurations (single and batch)
 *   <li>Batch copying jobs from templates (SuperTask pattern)
 *   <li>Previewing next trigger times
 * </ul>
 *
 * @author xuxueli 2015-12-19 16:13:16
 */
@Controller
@RequestMapping("/jobinfo")
public class JobInfoController {
    private static final Logger logger = LoggerFactory.getLogger(JobInfoController.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int NEXT_TRIGGER_PREVIEW_COUNT = 5;

    @Resource private XxlJobGroupMapper xxlJobGroupMapper;
    @Resource private XxlJobInfoMapper xxlJobInfoMapper;
    @Resource private XxlJobService xxlJobService;

    /**
     * Displays the job list page with configuration options.
     *
     * @param request the HTTP request for permission validation
     * @param model the model for view rendering
     * @param jobGroup optional job group filter
     * @return the view name for job list page
     */
    @RequestMapping
    public String index(
            HttpServletRequest request,
            Model model,
            @RequestParam(value = "jobGroup", required = false, defaultValue = "-1") int jobGroup) {

        model.addAttribute("ExecutorRouteStrategyEnum", ExecutorRouteStrategyEnum.values());
        model.addAttribute("GlueTypeEnum", GlueTypeEnum.values());
        model.addAttribute("ExecutorBlockStrategyEnum", ExecutorBlockStrategyEnum.values());
        model.addAttribute("ScheduleTypeEnum", ScheduleTypeEnum.values());
        model.addAttribute("MisfireStrategyEnum", MisfireStrategyEnum.values());

        List<XxlJobGroup> jobGroupListTotal = xxlJobGroupMapper.findAll();
        List<XxlJobGroup> jobGroupList =
                JobGroupPermissionUtil.filterJobGroupByPermission(request, jobGroupListTotal);

        if (CollectionTool.isEmpty(jobGroupList)) {
            throw new XxlJobException(I18nUtil.getString("jobgroup_empty"));
        }

        if (!isValidJobGroup(jobGroup, jobGroupList)) {
            jobGroup = -1;
        }

        model.addAttribute("JobGroupList", jobGroupList);
        model.addAttribute("jobGroup", jobGroup);

        return "biz/job.list";
    }

    /**
     * Retrieves a paginated list of jobs with optional filtering.
     *
     * @param request the HTTP request for permission validation
     * @param offset the starting offset for pagination
     * @param pagesize the page size
     * @param jobGroup the job group filter
     * @param triggerStatus the trigger status filter
     * @param jobDesc job description filter
     * @param executorHandler executor handler filter
     * @param author author filter
     * @return paginated response containing jobs
     */
    @RequestMapping("/pageList")
    @ResponseBody
    public Response<PageModel<XxlJobInfo>> pageList(
            HttpServletRequest request,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "10") int pagesize,
            @RequestParam int jobGroup,
            @RequestParam int triggerStatus,
            @RequestParam String jobDesc,
            @RequestParam String executorHandler,
            @RequestParam String author,
            @RequestParam(required = false) String superTaskName) {

        JobGroupPermissionUtil.validJobGroupPermission(request, jobGroup);
        return xxlJobService.pageList(
                offset,
                pagesize,
                jobGroup,
                triggerStatus,
                jobDesc,
                executorHandler,
                author,
                superTaskName);
    }

    /**
     * Creates a new job.
     *
     * @param request the HTTP request for permission validation
     * @param jobInfo the job to create
     * @return success or failure response with job ID
     */
    @RequestMapping("/insert")
    @ResponseBody
    public Response<String> add(HttpServletRequest request, XxlJobInfo jobInfo) {
        LoginInfo loginInfo =
                JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());
        return xxlJobService.add(jobInfo, loginInfo);
    }

    /**
     * Updates an existing job.
     *
     * @param request the HTTP request for permission validation
     * @param jobInfo the job to update
     * @return success or failure response
     */
    @RequestMapping("/update")
    @ResponseBody
    public Response<String> update(HttpServletRequest request, XxlJobInfo jobInfo) {
        LoginInfo loginInfo =
                JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());
        return xxlJobService.update(jobInfo, loginInfo);
    }

    /**
     * Deletes a job by ID.
     *
     * @param request the HTTP request for login info
     * @param ids the list of job IDs (only one allowed)
     * @return success or failure response
     */
    @RequestMapping("/delete")
    @ResponseBody
    public Response<String> delete(
            HttpServletRequest request, @RequestParam("ids[]") List<Integer> ids) {

        if (!isSingleSelection(ids)) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_choose")
                            + I18nUtil.getString("system_one")
                            + I18nUtil.getString("system_data"));
        }

        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);
        return xxlJobService.remove(ids.get(0), loginInfoResponse.getData());
    }

    /**
     * Stops a running job.
     *
     * @param request the HTTP request for login info
     * @param ids the list of job IDs (only one allowed)
     * @return success or failure response
     */
    @RequestMapping("/stop")
    @ResponseBody
    public Response<String> pause(
            HttpServletRequest request, @RequestParam("ids[]") List<Integer> ids) {

        if (!isSingleSelection(ids)) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_choose")
                            + I18nUtil.getString("system_one")
                            + I18nUtil.getString("system_data"));
        }

        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);
        return xxlJobService.stop(ids.get(0), loginInfoResponse.getData());
    }

    /**
     * Starts a stopped job.
     *
     * @param request the HTTP request for login info
     * @param ids the list of job IDs (only one allowed)
     * @return success or failure response
     */
    @RequestMapping("/start")
    @ResponseBody
    public Response<String> start(
            HttpServletRequest request, @RequestParam("ids[]") List<Integer> ids) {

        if (!isSingleSelection(ids)) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_choose")
                            + I18nUtil.getString("system_one")
                            + I18nUtil.getString("system_data"));
        }

        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);
        return xxlJobService.start(ids.get(0), loginInfoResponse.getData());
    }

    /**
     * Triggers a job manually with optional custom parameters.
     *
     * @param request the HTTP request for login info
     * @param id the job ID
     * @param executorParam optional executor parameters
     * @param addressList optional specific executor addresses
     * @return success or failure response
     */
    @RequestMapping("/trigger")
    @ResponseBody
    public Response<String> triggerJob(
            HttpServletRequest request,
            @RequestParam("id") int id,
            @RequestParam("executorParam") String executorParam,
            @RequestParam("addressList") String addressList) {
        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);
        return xxlJobService.trigger(loginInfoResponse.getData(), id, executorParam, addressList);
    }

    /**
     * Triggers a job in batch mode with logical schedule times for backfilling.
     *
     * @param request the HTTP request for login info
     * @param id the job ID
     * @param executorParam optional executor parameters
     * @param addressList optional specific executor addresses
     * @param startTimeStr the start time for batch trigger range
     * @param endTimeStr the end time for batch trigger range
     * @return success or failure response with instance count
     */
    @RequestMapping("/triggerBatch")
    @ResponseBody
    public Response<String> triggerBatch(
            HttpServletRequest request,
            @RequestParam("id") int id,
            @RequestParam("executorParam") String executorParam,
            @RequestParam("addressList") String addressList,
            @RequestParam(value = "startTime", required = false) String startTimeStr,
            @RequestParam(value = "endTime", required = false) String endTimeStr) {

        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);
        Date[] timeRange = parseTimeRange(startTimeStr, endTimeStr);

        if (timeRange == null) {
            return Response.ofFail("Invalid time format. Expected: yyyy-MM-dd HH:mm:ss");
        }

        return xxlJobService.triggerBatch(
                loginInfoResponse.getData(),
                id,
                executorParam,
                addressList,
                timeRange[0],
                timeRange[1]);
    }

    /**
     * Previews the instances that would be created by a batch trigger.
     *
     * @param request the HTTP request for login info
     * @param id the job ID
     * @param startTimeStr the start time for preview range
     * @param endTimeStr the end time for preview range
     * @return list of schedule times that would be triggered
     */
    @RequestMapping("/previewTriggerBatch")
    @ResponseBody
    public Response<List<String>> previewTriggerBatch(
            HttpServletRequest request,
            @RequestParam("id") int id,
            @RequestParam(value = "startTime", required = false) String startTimeStr,
            @RequestParam(value = "endTime", required = false) String endTimeStr) {

        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);
        Date[] timeRange = parseTimeRange(startTimeStr, endTimeStr);

        if (timeRange == null) {
            return Response.ofFail("Invalid time format. Expected: yyyy-MM-dd HH:mm:ss");
        }

        return xxlJobService.previewTriggerBatch(
                loginInfoResponse.getData(), id, timeRange[0], timeRange[1]);
    }

    /**
     * Exports job configuration(s) as JSON.
     *
     * <p>Supports both single and batch export modes: - Single: uses 'id' parameter - Batch: uses
     * 'ids[]' parameter
     *
     * @param request the HTTP request for permission validation
     * @param id optional single job ID for export
     * @param ids optional list of job IDs for batch export
     * @return JSON string containing job configuration(s)
     */
    @RequestMapping("/export")
    @ResponseBody
    public Response<String> exportJob(
            HttpServletRequest request,
            @RequestParam(value = "id", required = false) Integer id,
            @RequestParam(value = "ids[]", required = false) List<Integer> ids) {

        if (id != null) {
            return exportSingleJob(request, id);
        }

        if (CollectionTool.isNotEmpty(ids)) {
            return exportBatchJobs(request, ids);
        }

        return Response.ofFail(
                I18nUtil.getString("system_please_choose") + I18nUtil.getString("system_data"));
    }

    /**
     * Imports job configuration(s) from JSON.
     *
     * <p>Supports both single and batch import modes: - Single: JSON object starting with '{' -
     * Batch: JSON array starting with '['
     *
     * @param request the HTTP request for permission validation
     * @param jobJson the JSON string containing job configuration(s)
     * @return success or failure response with import results
     */
    @RequestMapping("/import")
    @ResponseBody
    public Response<String> importJob(HttpServletRequest request, @RequestBody String jobJson) {
        String trimmedJson = jobJson.trim();

        if (trimmedJson.startsWith("[")) {
            return importBatchJobs(request, jobJson);
        }

        if (trimmedJson.startsWith("{")) {
            return importSingleJob(request, jobJson);
        }

        return Response.ofFail(I18nUtil.getString("jobinfo_import_json_invalid"));
    }

    /**
     * Generates preview of next trigger times based on schedule configuration.
     *
     * @param scheduleType the schedule type (CRON, FIX_RATE, etc.)
     * @param scheduleConf the schedule configuration
     * @return list of next 5 trigger times in formatted string
     */
    @RequestMapping("/nextTriggerTime")
    @ResponseBody
    public Response<List<String>> nextTriggerTime(
            @RequestParam("scheduleType") String scheduleType,
            @RequestParam("scheduleConf") String scheduleConf) {

        if (StringTool.isBlank(scheduleType) || StringTool.isBlank(scheduleConf)) {
            return Response.ofSuccess(new ArrayList<>());
        }

        XxlJobInfo jobInfo = new XxlJobInfo();
        jobInfo.setScheduleType(scheduleType);
        jobInfo.setScheduleConf(scheduleConf);

        List<String> result = new ArrayList<>();
        try {
            Date lastTime = new Date();
            for (int i = 0; i < NEXT_TRIGGER_PREVIEW_COUNT; i++) {
                ScheduleTypeEnum scheduleTypeEnum =
                        ScheduleTypeEnum.match(jobInfo.getScheduleType(), ScheduleTypeEnum.NONE);
                lastTime =
                        scheduleTypeEnum
                                .getScheduleType()
                                .generateNextTriggerTime(jobInfo, lastTime);

                if (lastTime != null) {
                    result.add(DateTool.formatDateTime(lastTime));
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(
                    ">>>>>>>>>>> nextTriggerTime error. scheduleType = {}, scheduleConf= {}, error:{} ",
                    scheduleType,
                    scheduleConf,
                    e.getMessage());
            return Response.ofFail(
                    I18nUtil.getString("schedule_type")
                            + I18nUtil.getString("system_unvalid")
                            + e.getMessage());
        }

        return Response.ofSuccess(result);
    }

    /**
     * Batch copy jobs from a template (SuperTask), creating multiple SubTasks.
     *
     * <p>This implements the SuperTask template-instance pattern for creating multiple similar
     * jobs.
     *
     * @param request batch copy request containing mode and configurations
     * @return batch copy result with success/fail counts
     */
    @RequestMapping("/batchCopy")
    @ResponseBody
    public Response<BatchCopyResult> batchCopy(@RequestBody BatchCopyRequest request) {
        try {
            BatchCopyResult result = xxlJobService.batchCopy(request);
            if (result.getSuccessCount() > 0) {
                return Response.ofSuccess(result);
            }
            return Response.ofFail("All batch copy operations failed: " + result.getErrors());
        } catch (Exception e) {
            logger.error("Batch copy failed", e);
            return Response.ofFail("Batch copy error: " + e.getMessage());
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Checks if a job group ID is valid within the provided list.
     *
     * @param jobGroup the job group ID to check
     * @param jobGroupList the list of valid job groups
     * @return true if valid, false otherwise
     */
    private boolean isValidJobGroup(int jobGroup, List<XxlJobGroup> jobGroupList) {
        return CollectionTool.isNotEmpty(jobGroupList)
                && jobGroupList.stream().anyMatch(group -> group.getId() == jobGroup);
    }

    /**
     * Validates that exactly one ID is selected.
     *
     * @param ids the list of IDs
     * @return true if exactly one ID is selected, false otherwise
     */
    private boolean isSingleSelection(List<Integer> ids) {
        return CollectionTool.isNotEmpty(ids) && ids.size() == 1;
    }

    /**
     * Parses time range strings into Date objects.
     *
     * @param startTimeStr the start time string
     * @param endTimeStr the end time string
     * @return array with [startDate, endDate], or null if parsing fails
     */
    private Date[] parseTimeRange(String startTimeStr, String endTimeStr) {
        try {
            Date startTime = null;
            Date endTime = null;

            if (StringTool.isNotBlank(startTimeStr)) {
                startTime = DateTool.parseDateTime(startTimeStr);
            }
            if (StringTool.isNotBlank(endTimeStr)) {
                endTime = DateTool.parseDateTime(endTimeStr);
            }

            return new Date[] {startTime, endTime};
        } catch (Exception e) {
            logger.error("Failed to parse time parameters: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Exports a single job configuration.
     *
     * @param request the HTTP request for permission validation
     * @param id the job ID
     * @return JSON string containing job configuration
     */
    private Response<String> exportSingleJob(HttpServletRequest request, int id) {
        XxlJobInfo jobInfo = xxlJobInfoMapper.loadById(id);
        if (jobInfo == null) {
            return Response.ofFail(
                    I18nUtil.getString("jobinfo_field_id") + I18nUtil.getString("system_unvalid"));
        }

        JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());
        Map<String, Object> exportData = buildExportData(jobInfo);
        return Response.ofSuccess(GSON.toJson(exportData));
    }

    /**
     * Exports multiple job configurations.
     *
     * @param request the HTTP request for permission validation
     * @param ids the list of job IDs
     * @return JSON array containing job configurations
     */
    private Response<String> exportBatchJobs(HttpServletRequest request, List<Integer> ids) {
        List<Map<String, Object>> exportList = new ArrayList<>();

        for (Integer id : ids) {
            XxlJobInfo jobInfo = xxlJobInfoMapper.loadById(id);
            if (jobInfo == null) {
                logger.warn("Job not found for export: id={}", id);
                continue;
            }

            try {
                JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());
                exportList.add(buildExportData(jobInfo));
            } catch (Exception e) {
                logger.warn("Permission denied for job export: id={}", id);
            }
        }

        if (exportList.isEmpty()) {
            return Response.ofFail(I18nUtil.getString("system_empty"));
        }

        return Response.ofSuccess(GSON.toJson(exportList));
    }

    /**
     * Builds export data map from job info, excluding runtime fields.
     *
     * @param jobInfo the job info to export
     * @return map containing exportable job fields
     */
    private Map<String, Object> buildExportData(XxlJobInfo jobInfo) {
        Map<String, Object> exportData = new LinkedHashMap<>();
        exportData.put("jobGroup", jobInfo.getJobGroup());
        exportData.put("jobDesc", jobInfo.getJobDesc());
        exportData.put("author", jobInfo.getAuthor());
        exportData.put("alarmEmail", jobInfo.getAlarmEmail());
        exportData.put("scheduleType", jobInfo.getScheduleType());
        exportData.put("scheduleConf", jobInfo.getScheduleConf());
        exportData.put("misfireStrategy", jobInfo.getMisfireStrategy());
        exportData.put("executorRouteStrategy", jobInfo.getExecutorRouteStrategy());
        exportData.put("executorHandler", jobInfo.getExecutorHandler());
        exportData.put("executorParam", jobInfo.getExecutorParam());
        exportData.put("executorBlockStrategy", jobInfo.getExecutorBlockStrategy());
        exportData.put("executorTimeout", jobInfo.getExecutorTimeout());
        exportData.put("executorFailRetryCount", jobInfo.getExecutorFailRetryCount());
        exportData.put("glueType", jobInfo.getGlueType());
        exportData.put("glueSource", jobInfo.getGlueSource());
        exportData.put("glueRemark", jobInfo.getGlueRemark());
        exportData.put("childJobId", jobInfo.getChildJobId());
        exportData.put("superTaskId", jobInfo.getSuperTaskId());
        return exportData;
    }

    /**
     * Imports a single job from JSON.
     *
     * @param request the HTTP request for permission validation
     * @param jobJson the JSON string containing job configuration
     * @return success or failure response
     */
    private Response<String> importSingleJob(HttpServletRequest request, String jobJson) {
        XxlJobInfo jobInfo;
        try {
            jobInfo = GSON.fromJson(jobJson, XxlJobInfo.class);
        } catch (JsonSyntaxException e) {
            logger.error("Failed to parse job JSON: {}", e.getMessage());
            return Response.ofFail(I18nUtil.getString("jobinfo_import_json_invalid"));
        }

        if (jobInfo == null) {
            return Response.ofFail(I18nUtil.getString("jobinfo_import_json_invalid"));
        }

        LoginInfo loginInfo =
                JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());

        jobInfo.setId(0);
        return xxlJobService.add(jobInfo, loginInfo);
    }

    /**
     * Imports multiple jobs from JSON array.
     *
     * @param request the HTTP request for permission validation
     * @param jobJson the JSON array string containing job configurations
     * @return success or failure response with import statistics
     */
    private Response<String> importBatchJobs(HttpServletRequest request, String jobJson) {
        XxlJobInfo[] jobs;
        try {
            jobs = GSON.fromJson(jobJson, XxlJobInfo[].class);
        } catch (JsonSyntaxException e) {
            logger.error("Failed to parse job JSON array: {}", e.getMessage());
            return Response.ofFail(I18nUtil.getString("jobinfo_import_json_invalid"));
        }

        if (jobs == null || jobs.length == 0) {
            return Response.ofFail(I18nUtil.getString("jobinfo_import_json_invalid"));
        }

        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);
        if (loginInfoResponse.getCode() != 200) {
            return Response.ofFail(loginInfoResponse.getMsg());
        }

        LoginInfo loginInfo = loginInfoResponse.getData();
        int successCount = 0;
        int failCount = 0;
        StringBuilder errorMessages = new StringBuilder();

        for (int i = 0; i < jobs.length; i++) {
            XxlJobInfo job = jobs[i];
            if (job == null) {
                failCount++;
                continue;
            }

            try {
                JobGroupPermissionUtil.validJobGroupPermission(request, job.getJobGroup());
                job.setId(0);

                Response<String> result = xxlJobService.add(job, loginInfo);
                if (result.getCode() == 200) {
                    successCount++;
                } else {
                    failCount++;
                    errorMessages
                            .append("Job ")
                            .append(i + 1)
                            .append(": ")
                            .append(result.getMsg())
                            .append("; ");
                }
            } catch (Exception e) {
                failCount++;
                errorMessages
                        .append("Job ")
                        .append(i + 1)
                        .append(": ")
                        .append(e.getMessage())
                        .append("; ");
                logger.error("Failed to import job {}: {}", i + 1, e.getMessage());
            }
        }

        String resultMsg =
                String.format("Imported %d/%d jobs successfully", successCount, jobs.length);
        if (failCount > 0) {
            resultMsg += ". Failures: " + errorMessages.toString();
        }

        return successCount > 0 ? Response.ofSuccess(resultMsg) : Response.ofFail(resultMsg);
    }

    /**
     * Searches SuperTasks by ID or description for autocomplete.
     *
     * @param request the HTTP request for permission validation
     * @param jobGroup the job group ID
     * @param query search query (matches job ID or description)
     * @return list of matching jobs (max 20)
     */
    @RequestMapping("/searchSuperTask")
    @ResponseBody
    public Response<List<XxlJobInfo>> searchSuperTask(
            HttpServletRequest request, @RequestParam int jobGroup, @RequestParam String query) {
        JobGroupPermissionUtil.validJobGroupPermission(request, jobGroup);
        List<XxlJobInfo> jobs = xxlJobInfoMapper.searchByIdOrDesc(jobGroup, query);
        return Response.ofSuccess(jobs);
    }

    /**
     * Gets a job by ID (for SuperTask lookup).
     *
     * @param request the HTTP request for permission validation
     * @param id the job ID
     * @return the job info if found, failure otherwise
     */
    @RequestMapping("/getJobById")
    @ResponseBody
    public Response<XxlJobInfo> getJobById(HttpServletRequest request, @RequestParam int id) {
        XxlJobInfo job = xxlJobInfoMapper.loadById(id);
        if (job == null) {
            return Response.ofFail("Job not found");
        }

        JobGroupPermissionUtil.validJobGroupPermission(request, job.getJobGroup());
        return Response.ofSuccess(job);
    }
}
