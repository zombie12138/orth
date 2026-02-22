package com.abyss.orth.admin.service.impl;

import java.text.MessageFormat;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.abyss.orth.admin.constant.TriggerStatus;
import com.abyss.orth.admin.mapper.*;
import com.abyss.orth.admin.model.JobGroup;
import com.abyss.orth.admin.model.JobInfo;
import com.abyss.orth.admin.model.JobLogReport;
import com.abyss.orth.admin.model.dto.BatchCopyRequest;
import com.abyss.orth.admin.model.dto.BatchCopyResult;
import com.abyss.orth.admin.model.dto.SubTaskConfig;
import com.abyss.orth.admin.scheduler.config.OrthAdminBootstrap;
import com.abyss.orth.admin.scheduler.cron.CronExpression;
import com.abyss.orth.admin.scheduler.misfire.MisfireStrategyEnum;
import com.abyss.orth.admin.scheduler.route.ExecutorRouteStrategyEnum;
import com.abyss.orth.admin.scheduler.thread.JobScheduleHelper;
import com.abyss.orth.admin.scheduler.trigger.TriggerTypeEnum;
import com.abyss.orth.admin.scheduler.type.ScheduleTypeEnum;
import com.abyss.orth.admin.service.JobService;
import com.abyss.orth.admin.util.I18nUtil;
import com.abyss.orth.admin.util.JobGroupPermissionUtil;
import com.abyss.orth.admin.web.security.JwtUserInfo;
import com.abyss.orth.core.constant.ExecutorBlockStrategyEnum;
import com.abyss.orth.core.glue.GlueTypeEnum;
import com.xxl.tool.core.DateTool;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.gson.GsonTool;
import com.xxl.tool.response.PageModel;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;

/**
 * Core job management service implementation for Orth.
 *
 * <p>Implements comprehensive job lifecycle management with validation, permission checks, and
 * operation logging. All operations enforce job group permissions and validate configuration
 * integrity.
 *
 * @author xuxueli 2016-5-28 15:30:33
 */
@Service
public class JobServiceImpl implements JobService {
    private static final Logger logger = LoggerFactory.getLogger(JobServiceImpl.class);
    private static final int MAX_BATCH_INSTANCES = 100;
    private static final int MIN_FIX_RATE_SECONDS = 1;

    @Resource private JobGroupMapper jobGroupMapper;

    @Resource private JobInfoMapper jobInfoMapper;

    @Resource private JobLogMapper jobLogMapper;

    @Resource private JobLogGlueMapper jobLogGlueMapper;

    @Resource private JobLogReportMapper jobLogReportMapper;

    @Override
    public Response<PageModel<JobInfo>> pageList(
            int offset,
            int pagesize,
            int jobGroup,
            int triggerStatus,
            String jobDesc,
            String executorHandler,
            String author,
            String superTaskName) {

        List<JobInfo> list =
                jobInfoMapper.pageList(
                        offset,
                        pagesize,
                        jobGroup,
                        triggerStatus,
                        jobDesc,
                        executorHandler,
                        author,
                        superTaskName);
        int totalCount =
                jobInfoMapper.pageListCount(
                        offset,
                        pagesize,
                        jobGroup,
                        triggerStatus,
                        jobDesc,
                        executorHandler,
                        author,
                        superTaskName);

        PageModel<JobInfo> pageModel = new PageModel<>();
        pageModel.setData(list);
        pageModel.setTotal(totalCount);

        return Response.ofSuccess(pageModel);
    }

    @Override
    public Response<String> add(JobInfo jobInfo, JwtUserInfo userInfo) {
        // Validate basic fields
        Response<String> basicValidation = validateBasicFields(jobInfo);
        if (!basicValidation.isSuccess()) {
            return basicValidation;
        }

        // Validate schedule configuration
        ScheduleTypeEnum scheduleTypeEnum = ScheduleTypeEnum.match(jobInfo.getScheduleType(), null);
        Response<String> scheduleValidation = validateScheduleConfig(jobInfo, scheduleTypeEnum);
        if (!scheduleValidation.isSuccess()) {
            return scheduleValidation;
        }

        // Validate glue type and handler
        Response<String> glueValidation = validateGlueConfig(jobInfo);
        if (!glueValidation.isSuccess()) {
            return glueValidation;
        }

        // Validate advanced settings
        Response<String> advancedValidation = validateAdvancedSettings(jobInfo);
        if (!advancedValidation.isSuccess()) {
            return advancedValidation;
        }

        // Validate child job IDs
        Response<String> childJobValidation = validateAndNormalizeChildJobIds(jobInfo, userInfo);
        if (!childJobValidation.isSuccess()) {
            return childJobValidation;
        }

        // Save job
        Date now = new Date();
        jobInfo.setAddTime(now);
        jobInfo.setUpdateTime(now);
        jobInfo.setGlueUpdatetime(now);
        jobInfo.setExecutorHandler(jobInfo.getExecutorHandler().trim());

        jobInfo.setSuperTaskId(sanitizeSuperTaskId(jobInfo.getSuperTaskId(), 0));
        jobInfoMapper.save(jobInfo);
        if (jobInfo.getId() < 1) {
            return Response.ofFail(
                    I18nUtil.getString("jobinfo_field_add") + I18nUtil.getString("system_fail"));
        }

        logOperation(userInfo.getUsername(), "jobinfo-save", GsonTool.toJson(jobInfo));
        return Response.ofSuccess(String.valueOf(jobInfo.getId()));
    }

    @Override
    public Response<String> update(JobInfo jobInfo, JwtUserInfo userInfo) {
        // Validate basic fields (except job group)
        if (StringTool.isBlank(jobInfo.getJobDesc())) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("jobinfo_field_jobdesc"));
        }
        if (StringTool.isBlank(jobInfo.getAuthor())) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("jobinfo_field_author"));
        }

        // Validate schedule configuration
        ScheduleTypeEnum scheduleTypeEnum = ScheduleTypeEnum.match(jobInfo.getScheduleType(), null);
        Response<String> scheduleValidation = validateScheduleConfig(jobInfo, scheduleTypeEnum);
        if (!scheduleValidation.isSuccess()) {
            return scheduleValidation;
        }

        // Validate advanced settings
        Response<String> advancedValidation = validateAdvancedSettings(jobInfo);
        if (!advancedValidation.isSuccess()) {
            return advancedValidation;
        }

        // Validate child job IDs (excluding self-reference)
        Response<String> childJobValidation =
                validateAndNormalizeChildJobIds(jobInfo, userInfo, true);
        if (!childJobValidation.isSuccess()) {
            return childJobValidation;
        }

        // Validate job group exists
        JobGroup jobGroup = jobGroupMapper.load(jobInfo.getJobGroup());
        if (jobGroup == null) {
            return Response.ofFail(
                    I18nUtil.getString("jobinfo_field_jobgroup")
                            + I18nUtil.getString("system_unvalid"));
        }

        // Load existing job
        JobInfo existingJob = jobInfoMapper.loadById(jobInfo.getId());
        if (existingJob == null) {
            return Response.ofFail(
                    I18nUtil.getString("jobinfo_field_id")
                            + I18nUtil.getString("system_not_found"));
        }

        // Calculate next trigger time if schedule changed
        long nextTriggerTime = calculateNextTriggerTime(jobInfo, existingJob, scheduleTypeEnum);
        if (nextTriggerTime == -1) {
            return Response.ofFail(
                    I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid"));
        }

        // Update job fields
        updateJobFields(existingJob, jobInfo, nextTriggerTime);
        jobInfoMapper.update(existingJob);

        logOperation(userInfo.getUsername(), "jobinfo-update", GsonTool.toJson(existingJob));
        return Response.ofSuccess();
    }

    @Override
    public Response<String> remove(int id, JwtUserInfo userInfo) {
        JobInfo jobInfo = jobInfoMapper.loadById(id);
        if (jobInfo == null) {
            return Response.ofSuccess();
        }

        // Validate job group permission
        if (!JobGroupPermissionUtil.hasJobGroupPermission(userInfo, jobInfo.getJobGroup())) {
            return Response.ofFail(I18nUtil.getString("system_permission_limit"));
        }

        // Check if this is a SuperTask with SubTasks
        int subTaskCount = jobInfoMapper.countBySuperTaskId(id);
        if (subTaskCount > 0) {
            return Response.ofFail(
                    "Cannot delete SuperTask: "
                            + subTaskCount
                            + " SubTask(s) exist. Delete SubTasks first or break association.");
        }

        // Delete job and related data
        jobInfoMapper.delete(id);
        jobLogMapper.delete(id);
        jobLogGlueMapper.deleteByJobId(id);

        logOperation(userInfo.getUsername(), "jobinfo-remove", String.valueOf(id));
        return Response.ofSuccess();
    }

    @Override
    public Response<String> start(int id, JwtUserInfo userInfo) {
        JobInfo jobInfo = jobInfoMapper.loadById(id);
        if (jobInfo == null) {
            return Response.ofFail(I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
        }

        // Validate job group permission
        if (!JobGroupPermissionUtil.hasJobGroupPermission(userInfo, jobInfo.getJobGroup())) {
            return Response.ofFail(I18nUtil.getString("system_permission_limit"));
        }

        // Validate schedule type: cannot be NONE
        ScheduleTypeEnum scheduleTypeEnum =
                ScheduleTypeEnum.match(jobInfo.getScheduleType(), ScheduleTypeEnum.NONE);
        if (scheduleTypeEnum == ScheduleTypeEnum.NONE) {
            return Response.ofFail(I18nUtil.getString("schedule_type_none_limit_start"));
        }

        // Calculate next trigger time
        long nextTriggerTime;
        try {
            Date nextValidTime =
                    scheduleTypeEnum
                            .getScheduleType()
                            .generateNextTriggerTime(
                                    jobInfo,
                                    new Date(
                                            System.currentTimeMillis()
                                                    + JobScheduleHelper.PRE_READ_MS));

            if (nextValidTime == null) {
                return Response.ofFail(
                        I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid"));
            }
            nextTriggerTime = nextValidTime.getTime();
        } catch (Exception e) {
            logger.error("Failed to calculate next trigger time: {}", e.getMessage(), e);
            return Response.ofFail(
                    I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid"));
        }

        // Start job
        jobInfo.setTriggerStatus(TriggerStatus.RUNNING.getValue());
        jobInfo.setTriggerLastTime(0);
        jobInfo.setTriggerNextTime(nextTriggerTime);
        jobInfo.setUpdateTime(new Date());
        jobInfoMapper.update(jobInfo);

        logOperation(userInfo.getUsername(), "jobinfo-start", String.valueOf(id));
        return Response.ofSuccess();
    }

    @Override
    public Response<String> stop(int id, JwtUserInfo userInfo) {
        JobInfo jobInfo = jobInfoMapper.loadById(id);
        if (jobInfo == null) {
            return Response.ofFail(I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
        }

        // Validate job group permission
        if (!JobGroupPermissionUtil.hasJobGroupPermission(userInfo, jobInfo.getJobGroup())) {
            return Response.ofFail(I18nUtil.getString("system_permission_limit"));
        }

        // Stop job
        jobInfo.setTriggerStatus(TriggerStatus.STOPPED.getValue());
        jobInfo.setTriggerLastTime(0);
        jobInfo.setTriggerNextTime(0);
        jobInfo.setUpdateTime(new Date());
        jobInfoMapper.update(jobInfo);

        logOperation(userInfo.getUsername(), "jobinfo-stop", String.valueOf(id));
        return Response.ofSuccess();
    }

    @Override
    public Response<String> trigger(
            JwtUserInfo userInfo, int jobId, String executorParam, String addressList) {
        // Validate job and permission
        JobInfo jobInfo = jobInfoMapper.loadById(jobId);
        if (jobInfo == null) {
            return Response.ofFail(I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
        }

        if (!JobGroupPermissionUtil.hasJobGroupPermission(userInfo, jobInfo.getJobGroup())) {
            return Response.ofFail(I18nUtil.getString("system_permission_limit"));
        }

        // Trigger job immediately (no schedule time)
        String params = executorParam != null ? executorParam : "";
        OrthAdminBootstrap.getInstance()
                .getJobTriggerPoolHelper()
                .trigger(jobId, TriggerTypeEnum.MANUAL, -1, null, params, addressList, null);

        logOperation(userInfo.getUsername(), "jobinfo-trigger", String.valueOf(jobId));
        return Response.ofSuccess();
    }

    @Override
    public Response<String> triggerBatch(
            JwtUserInfo userInfo,
            int jobId,
            String executorParam,
            String addressList,
            Date startTime,
            Date endTime) {

        // Validate job and permission
        JobInfo jobInfo = jobInfoMapper.loadById(jobId);
        if (jobInfo == null) {
            return Response.ofFail(I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
        }

        if (!JobGroupPermissionUtil.hasJobGroupPermission(userInfo, jobInfo.getJobGroup())) {
            return Response.ofFail(I18nUtil.getString("system_permission_limit"));
        }

        String params = executorParam != null ? executorParam : "";

        // If no start time, trigger immediately
        if (startTime == null) {
            OrthAdminBootstrap.getInstance()
                    .getJobTriggerPoolHelper()
                    .trigger(jobId, TriggerTypeEnum.MANUAL, -1, null, params, addressList, null);
            logOperation(
                    userInfo.getUsername(), "jobinfo-trigger-immediate", String.valueOf(jobId));
            return Response.ofSuccess();
        }

        // Validate schedule type
        ScheduleTypeEnum scheduleTypeEnum =
                ScheduleTypeEnum.match(jobInfo.getScheduleType(), ScheduleTypeEnum.NONE);
        if (scheduleTypeEnum == ScheduleTypeEnum.NONE) {
            return Response.ofFail(
                    I18nUtil.getString("schedule_type") + " NONE cannot be used for batch trigger");
        }

        // Validate time range
        if (endTime != null && !startTime.before(endTime)) {
            return Response.ofFail("Start time must be before end time");
        }

        // Generate and trigger instances
        try {
            List<Long> scheduleTimes =
                    generateScheduleTimes(jobInfo, scheduleTypeEnum, startTime, endTime);

            if (scheduleTimes.isEmpty()) {
                return Response.ofFail("No valid schedule times generated");
            }

            // Trigger all instances
            for (Long scheduleTime : scheduleTimes) {
                OrthAdminBootstrap.getInstance()
                        .getJobTriggerPoolHelper()
                        .trigger(
                                jobId,
                                TriggerTypeEnum.MANUAL,
                                -1,
                                null,
                                params,
                                addressList,
                                scheduleTime);
            }

            logOperation(
                    userInfo.getUsername(),
                    "jobinfo-trigger-batch",
                    jobId + " instances=" + scheduleTimes.size());

            return Response.ofSuccess(
                    "Triggered " + scheduleTimes.size() + " instance(s) successfully");

        } catch (Exception e) {
            logger.error("Batch trigger failed: jobId={}, error={}", jobId, e.getMessage(), e);
            return Response.ofFail("Batch trigger failed: " + e.getMessage());
        }
    }

    @Override
    public Response<List<String>> previewTriggerBatch(
            JwtUserInfo userInfo, int jobId, Date startTime, Date endTime) {

        // Validate job and permission
        JobInfo jobInfo = jobInfoMapper.loadById(jobId);
        if (jobInfo == null) {
            return Response.ofFail(I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
        }

        if (!JobGroupPermissionUtil.hasJobGroupPermission(userInfo, jobInfo.getJobGroup())) {
            return Response.ofFail(I18nUtil.getString("system_permission_limit"));
        }

        if (startTime == null) {
            return Response.ofSuccess(Collections.emptyList());
        }

        // Validate schedule type
        ScheduleTypeEnum scheduleTypeEnum =
                ScheduleTypeEnum.match(jobInfo.getScheduleType(), ScheduleTypeEnum.NONE);
        if (scheduleTypeEnum == ScheduleTypeEnum.NONE) {
            return Response.ofFail(
                    I18nUtil.getString("schedule_type") + " NONE cannot be used for batch trigger");
        }

        // Validate time range
        if (endTime != null && !startTime.before(endTime)) {
            return Response.ofFail("Start time must be before end time");
        }

        try {
            List<Long> scheduleTimes =
                    generateScheduleTimes(jobInfo, scheduleTypeEnum, startTime, endTime);
            List<String> scheduleTimeStrings =
                    scheduleTimes.stream().map(t -> DateTool.formatDateTime(new Date(t))).toList();
            return Response.ofSuccess(scheduleTimeStrings);

        } catch (Exception e) {
            logger.error(
                    "Preview trigger batch failed: jobId={}, error={}", jobId, e.getMessage(), e);
            return Response.ofFail("Preview failed: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> dashboardInfo() {
        int jobInfoCount = jobInfoMapper.findAllCount();

        JobLogReport logReport = jobLogReportMapper.queryLogReportTotal();
        int jobLogCount = 0;
        int jobLogSuccessCount = 0;
        if (logReport != null) {
            jobLogCount =
                    logReport.getRunningCount()
                            + logReport.getSuccessCount()
                            + logReport.getFailCount();
            jobLogSuccessCount = logReport.getSuccessCount();
        }

        // Count unique executor addresses
        Set<String> executorAddresses = new HashSet<>();
        List<JobGroup> groupList = jobGroupMapper.findAll();
        if (groupList != null && !groupList.isEmpty()) {
            for (JobGroup group : groupList) {
                if (group.getRegistryList() != null && !group.getRegistryList().isEmpty()) {
                    executorAddresses.addAll(group.getRegistryList());
                }
            }
        }

        Map<String, Object> dashboardMap = new HashMap<>();
        dashboardMap.put("jobInfoCount", jobInfoCount);
        dashboardMap.put("jobLogCount", jobLogCount);
        dashboardMap.put("jobLogSuccessCount", jobLogSuccessCount);
        dashboardMap.put("executorCount", executorAddresses.size());
        return dashboardMap;
    }

    @Override
    public Response<Map<String, Object>> chartInfo(Date startDate, Date endDate) {
        List<JobLogReport> logReportList = jobLogReportMapper.queryLogReport(startDate, endDate);

        List<String> triggerDayList = new ArrayList<>();
        List<Integer> triggerDayCountRunningList = new ArrayList<>();
        List<Integer> triggerDayCountSucList = new ArrayList<>();
        List<Integer> triggerDayCountFailList = new ArrayList<>();
        int triggerCountRunningTotal = 0;
        int triggerCountSucTotal = 0;
        int triggerCountFailTotal = 0;

        if (logReportList != null && !logReportList.isEmpty()) {
            for (JobLogReport item : logReportList) {
                triggerDayList.add(DateTool.formatDate(item.getTriggerDay()));
                triggerDayCountRunningList.add(item.getRunningCount());
                triggerDayCountSucList.add(item.getSuccessCount());
                triggerDayCountFailList.add(item.getFailCount());

                triggerCountRunningTotal += item.getRunningCount();
                triggerCountSucTotal += item.getSuccessCount();
                triggerCountFailTotal += item.getFailCount();
            }
        } else {
            // Generate empty data for last 7 days
            for (int i = -6; i <= 0; i++) {
                triggerDayList.add(DateTool.formatDate(DateTool.addDays(new Date(), i)));
                triggerDayCountRunningList.add(0);
                triggerDayCountSucList.add(0);
                triggerDayCountFailList.add(0);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("triggerDayList", triggerDayList);
        result.put("triggerDayCountRunningList", triggerDayCountRunningList);
        result.put("triggerDayCountSucList", triggerDayCountSucList);
        result.put("triggerDayCountFailList", triggerDayCountFailList);
        result.put("triggerCountRunningTotal", triggerCountRunningTotal);
        result.put("triggerCountSucTotal", triggerCountSucTotal);
        result.put("triggerCountFailTotal", triggerCountFailTotal);

        return Response.ofSuccess(result);
    }

    @Override
    public BatchCopyResult batchCopy(BatchCopyRequest request) {
        BatchCopyResult result = new BatchCopyResult();

        // Load template job
        JobInfo templateJob = jobInfoMapper.loadById(request.getTemplateJobId());
        if (templateJob == null) {
            result.addError("Template job not found: " + request.getTemplateJobId());
            return result;
        }

        // Parse configurations based on mode
        List<SubTaskConfig> configs = parseSubTaskConfigs(request, result);
        if (!result.getErrors().isEmpty()) {
            return result;
        }

        // Create SubTasks
        for (int i = 0; i < configs.size(); i++) {
            createSubTask(templateJob, configs.get(i), request, i + 1, result);
        }

        return result;
    }

    // -------------------- Private Helper Methods --------------------

    /** Validates basic job fields (group, description, author). */
    private Response<String> validateBasicFields(JobInfo jobInfo) {
        JobGroup group = jobGroupMapper.load(jobInfo.getJobGroup());
        if (group == null) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_choose")
                            + I18nUtil.getString("jobinfo_field_jobgroup"));
        }

        if (StringTool.isBlank(jobInfo.getJobDesc())) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("jobinfo_field_jobdesc"));
        }

        if (StringTool.isBlank(jobInfo.getAuthor())) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("jobinfo_field_author"));
        }

        return Response.ofSuccess();
    }

    /** Validates schedule configuration based on type. */
    private Response<String> validateScheduleConfig(
            JobInfo jobInfo, ScheduleTypeEnum scheduleTypeEnum) {
        if (scheduleTypeEnum == null) {
            return Response.ofFail(
                    I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid"));
        }

        if (scheduleTypeEnum == ScheduleTypeEnum.CRON) {
            if (jobInfo.getScheduleConf() == null
                    || !CronExpression.isValidExpression(jobInfo.getScheduleConf())) {
                return Response.ofFail("Cron" + I18nUtil.getString("system_unvalid"));
            }
        } else if (scheduleTypeEnum == ScheduleTypeEnum.FIX_RATE) {
            if (jobInfo.getScheduleConf() == null) {
                return Response.ofFail(I18nUtil.getString("schedule_type"));
            }
            try {
                int fixSecond = Integer.parseInt(jobInfo.getScheduleConf());
                if (fixSecond < MIN_FIX_RATE_SECONDS) {
                    return Response.ofFail(
                            I18nUtil.getString("schedule_type")
                                    + I18nUtil.getString("system_unvalid"));
                }
            } catch (NumberFormatException e) {
                return Response.ofFail(
                        I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid"));
            }
        }

        return Response.ofSuccess();
    }

    /** Validates glue type and handler configuration. */
    private Response<String> validateGlueConfig(JobInfo jobInfo) {
        GlueTypeEnum glueType = GlueTypeEnum.match(jobInfo.getGlueType());
        if (glueType == null) {
            return Response.ofFail(
                    I18nUtil.getString("jobinfo_field_gluetype")
                            + I18nUtil.getString("system_unvalid"));
        }

        if (glueType == GlueTypeEnum.BEAN && StringTool.isBlank(jobInfo.getExecutorHandler())) {
            return Response.ofFail(I18nUtil.getString("system_please_input") + "JobHandler");
        }

        // Fix carriage returns in shell scripts
        if (glueType == GlueTypeEnum.GLUE_SHELL && jobInfo.getGlueSource() != null) {
            jobInfo.setGlueSource(jobInfo.getGlueSource().replaceAll("\r", ""));
        }

        return Response.ofSuccess();
    }

    /** Validates advanced settings (routing, misfire, block strategy). */
    private Response<String> validateAdvancedSettings(JobInfo jobInfo) {
        if (ExecutorRouteStrategyEnum.match(jobInfo.getExecutorRouteStrategy(), null) == null) {
            return Response.ofFail(
                    I18nUtil.getString("jobinfo_field_executorRouteStrategy")
                            + I18nUtil.getString("system_unvalid"));
        }

        if (MisfireStrategyEnum.match(jobInfo.getMisfireStrategy(), null) == null) {
            return Response.ofFail(
                    I18nUtil.getString("misfire_strategy") + I18nUtil.getString("system_unvalid"));
        }

        if (ExecutorBlockStrategyEnum.match(jobInfo.getExecutorBlockStrategy(), null) == null) {
            return Response.ofFail(
                    I18nUtil.getString("jobinfo_field_executorBlockStrategy")
                            + I18nUtil.getString("system_unvalid"));
        }

        return Response.ofSuccess();
    }

    /** Validates and normalizes child job IDs. */
    private Response<String> validateAndNormalizeChildJobIds(
            JobInfo jobInfo, JwtUserInfo userInfo) {
        return validateAndNormalizeChildJobIds(jobInfo, userInfo, false);
    }

    /** Validates and normalizes child job IDs with optional self-reference check. */
    private Response<String> validateAndNormalizeChildJobIds(
            JobInfo jobInfo, JwtUserInfo userInfo, boolean checkSelfReference) {
        if (StringTool.isBlank(jobInfo.getChildJobId())) {
            return Response.ofSuccess();
        }

        String[] childJobIds = jobInfo.getChildJobId().split(",");
        StringBuilder normalizedIds = new StringBuilder();

        for (String childJobIdStr : childJobIds) {
            if (StringTool.isBlank(childJobIdStr) || !StringTool.isNumeric(childJobIdStr)) {
                return Response.ofFail(
                        MessageFormat.format(
                                I18nUtil.getString("jobinfo_field_childJobId")
                                        + "({0})"
                                        + I18nUtil.getString("system_unvalid"),
                                childJobIdStr));
            }

            int childJobId = Integer.parseInt(childJobIdStr);

            // Check self-reference for updates
            if (checkSelfReference && childJobId == jobInfo.getId()) {
                return Response.ofFail(
                        I18nUtil.getString("jobinfo_field_childJobId")
                                + "("
                                + childJobId
                                + ")"
                                + I18nUtil.getString("system_unvalid"));
            }

            // Validate child job exists
            JobInfo childJobInfo = jobInfoMapper.loadById(childJobId);
            if (childJobInfo == null) {
                return Response.ofFail(
                        MessageFormat.format(
                                I18nUtil.getString("jobinfo_field_childJobId")
                                        + "({0})"
                                        + I18nUtil.getString("system_not_found"),
                                childJobIdStr));
            }

            // Validate permission
            if (!JobGroupPermissionUtil.hasJobGroupPermission(
                    userInfo, childJobInfo.getJobGroup())) {
                return Response.ofFail(
                        MessageFormat.format(
                                I18nUtil.getString("jobinfo_field_childJobId")
                                        + "({0})"
                                        + I18nUtil.getString("system_permission_limit"),
                                childJobIdStr));
            }

            if (normalizedIds.length() > 0) {
                normalizedIds.append(",");
            }
            normalizedIds.append(childJobIdStr);
        }

        jobInfo.setChildJobId(normalizedIds.toString());
        return Response.ofSuccess();
    }

    /** Calculates next trigger time for job update if schedule changed. */
    private long calculateNextTriggerTime(
            JobInfo newJobInfo, JobInfo existingJob, ScheduleTypeEnum scheduleTypeEnum) {
        long nextTriggerTime = existingJob.getTriggerNextTime();

        boolean scheduleUnchanged =
                Objects.equals(newJobInfo.getScheduleType(), existingJob.getScheduleType())
                        && Objects.equals(
                                newJobInfo.getScheduleConf(), existingJob.getScheduleConf());

        if (existingJob.getTriggerStatus() == TriggerStatus.RUNNING.getValue()
                && !scheduleUnchanged) {
            try {
                Date nextValidTime =
                        scheduleTypeEnum
                                .getScheduleType()
                                .generateNextTriggerTime(
                                        newJobInfo,
                                        new Date(
                                                System.currentTimeMillis()
                                                        + JobScheduleHelper.PRE_READ_MS));
                if (nextValidTime == null) {
                    return -1;
                }
                nextTriggerTime = nextValidTime.getTime();
            } catch (Exception e) {
                logger.error("Failed to calculate next trigger time: {}", e.getMessage(), e);
                return -1;
            }
        }

        return nextTriggerTime;
    }

    /** Updates job fields from new data. */
    private void updateJobFields(JobInfo existingJob, JobInfo newJobInfo, long nextTriggerTime) {
        existingJob.setJobGroup(newJobInfo.getJobGroup());
        existingJob.setJobDesc(newJobInfo.getJobDesc());
        existingJob.setAuthor(newJobInfo.getAuthor());
        existingJob.setAlarmEmail(newJobInfo.getAlarmEmail());
        existingJob.setScheduleType(newJobInfo.getScheduleType());
        existingJob.setScheduleConf(newJobInfo.getScheduleConf());
        existingJob.setMisfireStrategy(newJobInfo.getMisfireStrategy());
        existingJob.setExecutorRouteStrategy(newJobInfo.getExecutorRouteStrategy());
        existingJob.setExecutorHandler(newJobInfo.getExecutorHandler().trim());
        existingJob.setExecutorParam(newJobInfo.getExecutorParam());
        existingJob.setExecutorBlockStrategy(newJobInfo.getExecutorBlockStrategy());
        existingJob.setExecutorTimeout(newJobInfo.getExecutorTimeout());
        existingJob.setExecutorFailRetryCount(newJobInfo.getExecutorFailRetryCount());
        existingJob.setChildJobId(newJobInfo.getChildJobId());
        existingJob.setSuperTaskId(
                sanitizeSuperTaskId(newJobInfo.getSuperTaskId(), existingJob.getId()));
        existingJob.setTriggerNextTime(nextTriggerTime);
        existingJob.setUpdateTime(new Date());
    }

    /** Sanitizes superTaskId: clears invalid values (null, non-positive, self-reference). */
    private int sanitizeSuperTaskId(Integer superTaskId, int jobId) {
        if (superTaskId == null || superTaskId <= 0 || superTaskId == jobId) {
            return 0;
        }
        return superTaskId;
    }

    /** Generates schedule times for batch trigger. */
    private List<Long> generateScheduleTimes(
            JobInfo jobInfo, ScheduleTypeEnum scheduleTypeEnum, Date startTime, Date endTime)
            throws Exception {
        List<Long> scheduleTimes = new ArrayList<>();

        if (scheduleTypeEnum == ScheduleTypeEnum.CRON) {
            if (endTime == null) {
                throw new IllegalArgumentException("End time is required for CRON schedule type");
            }
            generateCronScheduleTimes(jobInfo, scheduleTypeEnum, startTime, endTime, scheduleTimes);

        } else if (scheduleTypeEnum == ScheduleTypeEnum.FIX_RATE) {
            if (endTime == null) {
                throw new IllegalArgumentException(
                        "End time is required for FIX_RATE schedule type");
            }
            generateFixRateScheduleTimes(
                    jobInfo, scheduleTypeEnum, startTime, endTime, scheduleTimes);

        } else {
            // FIX_DELAY or other: single execution at start time
            scheduleTimes.add(startTime.getTime());
        }

        if (scheduleTimes.size() >= MAX_BATCH_INSTANCES) {
            logger.warn(
                    "Batch trigger hit max instances limit: jobId={}, instances={}, limit={}",
                    jobInfo.getId(),
                    scheduleTimes.size(),
                    MAX_BATCH_INSTANCES);
        }

        return scheduleTimes;
    }

    /** Generates CRON-based schedule times. */
    private void generateCronScheduleTimes(
            JobInfo jobInfo,
            ScheduleTypeEnum scheduleTypeEnum,
            Date startTime,
            Date endTime,
            List<Long> scheduleTimes)
            throws Exception {
        Date currentTime =
                scheduleTypeEnum
                        .getScheduleType()
                        .generateNextTriggerTime(jobInfo, new Date(startTime.getTime() - 1000));

        while (currentTime != null && scheduleTimes.size() < MAX_BATCH_INSTANCES) {
            if (currentTime.getTime() >= startTime.getTime() && currentTime.before(endTime)) {
                scheduleTimes.add(currentTime.getTime());
            } else if (currentTime.getTime() >= endTime.getTime()) {
                break;
            }

            currentTime =
                    scheduleTypeEnum
                            .getScheduleType()
                            .generateNextTriggerTime(jobInfo, currentTime);
        }
    }

    /** Generates fixed-rate schedule times. */
    private void generateFixRateScheduleTimes(
            JobInfo jobInfo,
            ScheduleTypeEnum scheduleTypeEnum,
            Date startTime,
            Date endTime,
            List<Long> scheduleTimes)
            throws Exception {
        Date currentTime = startTime;

        while (currentTime.before(endTime) && scheduleTimes.size() < MAX_BATCH_INSTANCES) {
            scheduleTimes.add(currentTime.getTime());
            currentTime =
                    scheduleTypeEnum
                            .getScheduleType()
                            .generateNextTriggerTime(jobInfo, currentTime);
        }
    }

    /** Parses SubTask configurations from batch copy request. */
    private List<SubTaskConfig> parseSubTaskConfigs(
            BatchCopyRequest request, BatchCopyResult result) {
        if ("simple".equals(request.getMode())) {
            return parseSimpleModeConfigs(request, result);
        } else if ("advanced".equals(request.getMode())) {
            if (request.getTasks() == null || request.getTasks().isEmpty()) {
                result.addError("Tasks list is required for advanced mode");
                return Collections.emptyList();
            }
            return request.getTasks();
        } else {
            result.addError(
                    "Invalid mode: " + request.getMode() + ". Must be 'simple' or 'advanced'");
            return Collections.emptyList();
        }
    }

    /** Parses SubTask configurations for simple mode. */
    private List<SubTaskConfig> parseSimpleModeConfigs(
            BatchCopyRequest request, BatchCopyResult result) {
        if (request.getParams() == null || request.getParams().isEmpty()) {
            result.addError("Params list is required for simple mode");
            return Collections.emptyList();
        }

        List<SubTaskConfig> configs = new ArrayList<>();
        for (String param : request.getParams()) {
            SubTaskConfig config = new SubTaskConfig();
            config.setExecutorParam(param);
            applyCommonOverrides(config, request);
            configs.add(config);
        }
        return configs;
    }

    /** Applies common overrides to SubTask config. */
    private void applyCommonOverrides(SubTaskConfig config, BatchCopyRequest request) {
        if (request.getJobDesc() != null) {
            config.setJobDesc(request.getJobDesc());
        }
        if (request.getAuthor() != null) {
            config.setAuthor(request.getAuthor());
        }
        if (request.getScheduleConf() != null) {
            config.setScheduleConf(request.getScheduleConf());
        }
        if (request.getScheduleType() != null) {
            config.setScheduleType(request.getScheduleType());
        }
        if (request.getAlarmEmail() != null) {
            config.setAlarmEmail(request.getAlarmEmail());
        }
    }

    /** Creates a single SubTask from template and config. */
    private void createSubTask(
            JobInfo templateJob,
            SubTaskConfig config,
            BatchCopyRequest request,
            int index,
            BatchCopyResult result) {
        try {
            JobInfo subTask = cloneTemplateJob(templateJob);
            subTask.setSuperTaskId(templateJob.getId());

            applyConfigOverrides(subTask, config);
            // Only auto-generate name if config didn't provide one
            if (config.getJobDesc() == null) {
                String jobName =
                        generateSubTaskName(
                                templateJob.getJobDesc(), request.getNameTemplate(), index);
                subTask.setJobDesc(jobName);
            }

            Date now = new Date();
            subTask.setAddTime(now);
            subTask.setUpdateTime(now);
            subTask.setGlueUpdatetime(now);
            subTask.setTriggerStatus(TriggerStatus.STOPPED.getValue());
            subTask.setTriggerLastTime(0);
            subTask.setTriggerNextTime(0);

            jobInfoMapper.save(subTask);
            if (subTask.getId() > 0) {
                result.addCreatedJobId(subTask.getId());
                logger.info(
                        "SubTask created: id={}, executorParam={}",
                        subTask.getId(),
                        subTask.getExecutorParam());
            } else {
                result.addError("Failed to save SubTask " + index);
            }

        } catch (Exception e) {
            logger.error("Failed to create SubTask {}: {}", index, e.getMessage(), e);
            result.addError("SubTask " + index + ": " + e.getMessage());
        }
    }

    /** Clones template job for SubTask creation. */
    private JobInfo cloneTemplateJob(JobInfo template) {
        JobInfo clone = new JobInfo();
        clone.setJobGroup(template.getJobGroup());
        clone.setJobDesc(template.getJobDesc());
        clone.setAuthor(template.getAuthor());
        clone.setAlarmEmail(template.getAlarmEmail());
        clone.setScheduleType(template.getScheduleType());
        clone.setScheduleConf(template.getScheduleConf());
        clone.setMisfireStrategy(template.getMisfireStrategy());
        clone.setExecutorRouteStrategy(template.getExecutorRouteStrategy());
        clone.setExecutorHandler(template.getExecutorHandler());
        clone.setExecutorParam(template.getExecutorParam());
        clone.setExecutorBlockStrategy(template.getExecutorBlockStrategy());
        clone.setExecutorTimeout(template.getExecutorTimeout());
        clone.setExecutorFailRetryCount(template.getExecutorFailRetryCount());
        clone.setGlueType(template.getGlueType());
        clone.setGlueSource(template.getGlueSource());
        clone.setGlueRemark(template.getGlueRemark());
        clone.setChildJobId(template.getChildJobId());
        return clone;
    }

    /** Applies SubTask config overrides. */
    private void applyConfigOverrides(JobInfo subTask, SubTaskConfig config) {
        if (config.getJobDesc() != null) {
            subTask.setJobDesc(config.getJobDesc());
        }
        if (config.getExecutorParam() != null) {
            subTask.setExecutorParam(config.getExecutorParam());
        }
        if (config.getAuthor() != null) {
            subTask.setAuthor(config.getAuthor());
        }
        if (config.getScheduleConf() != null) {
            subTask.setScheduleConf(config.getScheduleConf());
        }
        if (config.getScheduleType() != null) {
            subTask.setScheduleType(config.getScheduleType());
        }
        if (config.getAlarmEmail() != null) {
            subTask.setAlarmEmail(config.getAlarmEmail());
        }
    }

    /** Generates SubTask name from template. */
    private String generateSubTaskName(String originName, String nameTemplate, int index) {
        if (StringTool.isBlank(nameTemplate)) {
            return originName + "-" + index;
        }

        return nameTemplate
                .replace("{origin}", originName)
                .replace("{index}", String.valueOf(index));
    }

    /** Logs operation for audit trail. */
    private void logOperation(String username, String type, String content) {
        logger.info(
                ">>>>>>>>>>> orth operation log: operator={}, type={}, content={}",
                username,
                type,
                content);
    }
}
