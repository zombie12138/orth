package com.abyss.orth.admin.controller.api;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.abyss.orth.admin.mapper.JobGroupMapper;
import com.abyss.orth.admin.mapper.JobInfoMapper;
import com.abyss.orth.admin.model.JobInfo;
import com.abyss.orth.admin.model.dto.BatchCopyRequest;
import com.abyss.orth.admin.model.dto.BatchCopyResult;
import com.abyss.orth.admin.scheduler.type.ScheduleTypeEnum;
import com.abyss.orth.admin.service.JobService;
import com.abyss.orth.admin.util.I18nUtil;
import com.abyss.orth.admin.util.JobGroupPermissionUtil;
import com.abyss.orth.admin.web.security.JwtUserInfo;
import com.abyss.orth.admin.web.security.SecurityContext;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.xxl.tool.core.CollectionTool;
import com.xxl.tool.core.DateTool;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.response.PageModel;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Job management REST API controller.
 *
 * @author xuxueli 2015-12-19 16:13:16
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int NEXT_TRIGGER_PREVIEW_COUNT = 10;

    @Resource private JobGroupMapper jobGroupMapper;
    @Resource private JobInfoMapper jobInfoMapper;
    @Resource private JobService orthJobService;

    @GetMapping
    public Response<PageModel<JobInfo>> pageList(
            HttpServletRequest request,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "10") int pagesize,
            @RequestParam(defaultValue = "0") int jobGroup,
            @RequestParam(defaultValue = "-1") int triggerStatus,
            @RequestParam(defaultValue = "") String jobDesc,
            @RequestParam(defaultValue = "") String executorHandler,
            @RequestParam(defaultValue = "") String author,
            @RequestParam(required = false) String superTaskName) {

        JobGroupPermissionUtil.validJobGroupPermission(request, jobGroup);
        return orthJobService.pageList(
                offset,
                pagesize,
                jobGroup,
                triggerStatus,
                jobDesc,
                executorHandler,
                author,
                superTaskName);
    }

    @PostMapping
    public Response<String> add(HttpServletRequest request, @RequestBody JobInfo jobInfo) {
        JwtUserInfo userInfo =
                JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());
        return orthJobService.add(jobInfo, userInfo);
    }

    @PutMapping("/{id}")
    public Response<String> update(
            HttpServletRequest request, @PathVariable int id, @RequestBody JobInfo jobInfo) {
        jobInfo.setId(id);
        JwtUserInfo userInfo =
                JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());
        return orthJobService.update(jobInfo, userInfo);
    }

    @DeleteMapping("/{id}")
    public Response<String> delete(HttpServletRequest request, @PathVariable int id) {
        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);
        return orthJobService.remove(id, userInfo);
    }

    @GetMapping("/{id}")
    public Response<JobInfo> getJobById(HttpServletRequest request, @PathVariable int id) {
        JobInfo job = jobInfoMapper.loadById(id);
        if (job == null) {
            return Response.ofFail("Job not found");
        }

        JobGroupPermissionUtil.validJobGroupPermission(request, job.getJobGroup());
        return Response.ofSuccess(job);
    }

    @PostMapping("/{id}/start")
    public Response<String> start(HttpServletRequest request, @PathVariable int id) {
        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);
        return orthJobService.start(id, userInfo);
    }

    @PostMapping("/{id}/stop")
    public Response<String> stop(HttpServletRequest request, @PathVariable int id) {
        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);
        return orthJobService.stop(id, userInfo);
    }

    @PostMapping("/{id}/trigger")
    public Response<String> trigger(
            HttpServletRequest request,
            @PathVariable int id,
            @RequestParam(required = false, defaultValue = "") String executorParam,
            @RequestParam(required = false, defaultValue = "") String addressList) {
        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);
        return orthJobService.trigger(userInfo, id, executorParam, addressList);
    }

    @PostMapping("/{id}/trigger-batch")
    public Response<String> triggerBatch(
            HttpServletRequest request,
            @PathVariable int id,
            @RequestParam(required = false, defaultValue = "") String executorParam,
            @RequestParam(required = false, defaultValue = "") String addressList,
            @RequestParam(value = "startTime", required = false) String startTimeStr,
            @RequestParam(value = "endTime", required = false) String endTimeStr) {

        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);
        Date[] timeRange = parseTimeRange(startTimeStr, endTimeStr);

        if (timeRange == null) {
            return Response.ofFail("Invalid time format. Expected: yyyy-MM-dd HH:mm:ss");
        }

        return orthJobService.triggerBatch(
                userInfo, id, executorParam, addressList, timeRange[0], timeRange[1]);
    }

    @GetMapping("/{id}/trigger-batch/preview")
    public Response<List<String>> previewTriggerBatch(
            HttpServletRequest request,
            @PathVariable int id,
            @RequestParam(value = "startTime", required = false) String startTimeStr,
            @RequestParam(value = "endTime", required = false) String endTimeStr) {

        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);
        Date[] timeRange = parseTimeRange(startTimeStr, endTimeStr);

        if (timeRange == null) {
            return Response.ofFail("Invalid time format. Expected: yyyy-MM-dd HH:mm:ss");
        }

        return orthJobService.previewTriggerBatch(userInfo, id, timeRange[0], timeRange[1]);
    }

    @PostMapping("/export")
    public Response<String> exportJob(
            HttpServletRequest request,
            @RequestParam(value = "id", required = false) Integer id,
            @RequestParam(value = "ids", required = false) List<Integer> ids) {

        if (id != null) {
            return exportSingleJob(request, id);
        }

        if (CollectionTool.isNotEmpty(ids)) {
            return exportBatchJobs(request, ids);
        }

        return Response.ofFail(
                I18nUtil.getString("system_please_choose") + I18nUtil.getString("system_data"));
    }

    @PostMapping("/import")
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

    @GetMapping("/next-trigger-time")
    public Response<List<String>> nextTriggerTime(
            @RequestParam("scheduleType") String scheduleType,
            @RequestParam("scheduleConf") String scheduleConf) {

        if (StringTool.isBlank(scheduleType) || StringTool.isBlank(scheduleConf)) {
            return Response.ofSuccess(new ArrayList<>());
        }

        JobInfo jobInfo = new JobInfo();
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
                    "nextTriggerTime error. scheduleType={}, scheduleConf={}, error={}",
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

    @PostMapping("/batch-copy")
    public Response<BatchCopyResult> batchCopy(@RequestBody BatchCopyRequest request) {
        try {
            BatchCopyResult result = orthJobService.batchCopy(request);
            if (result.getSuccessCount() > 0) {
                return Response.ofSuccess(result);
            }
            return Response.ofFail("All batch copy operations failed: " + result.getErrors());
        } catch (Exception e) {
            logger.error("Batch copy failed", e);
            return Response.ofFail("Batch copy error: " + e.getMessage());
        }
    }

    @GetMapping("/search-super-task")
    public Response<List<JobInfo>> searchSuperTask(
            HttpServletRequest request, @RequestParam int jobGroup, @RequestParam String query) {
        JobGroupPermissionUtil.validJobGroupPermission(request, jobGroup);
        List<JobInfo> jobs = jobInfoMapper.searchByIdOrDesc(jobGroup, query);
        return Response.ofSuccess(jobs);
    }

    @GetMapping("/search")
    public Response<List<JobInfo>> searchJobs(
            HttpServletRequest request,
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "0") int jobGroup) {
        if (jobGroup > 0) {
            JobGroupPermissionUtil.validJobGroupPermission(request, jobGroup);
            List<JobInfo> jobs = jobInfoMapper.searchByIdOrDesc(jobGroup, query);
            return Response.ofSuccess(jobs);
        }

        List<Integer> permittedGroupIds = JobGroupPermissionUtil.getPermittedGroupIds(request);
        List<JobInfo> jobs = jobInfoMapper.searchByIdOrDescMultiGroup(permittedGroupIds, query);
        return Response.ofSuccess(jobs);
    }

    // ==================== Private Helper Methods ====================

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

    private Response<String> exportSingleJob(HttpServletRequest request, int id) {
        JobInfo jobInfo = jobInfoMapper.loadById(id);
        if (jobInfo == null) {
            return Response.ofFail(
                    I18nUtil.getString("jobinfo_field_id") + I18nUtil.getString("system_unvalid"));
        }

        JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());
        Map<String, Object> exportData = buildExportData(jobInfo);
        return Response.ofSuccess(GSON.toJson(exportData));
    }

    private Response<String> exportBatchJobs(HttpServletRequest request, List<Integer> ids) {
        List<Map<String, Object>> exportList = new ArrayList<>();

        for (Integer id : ids) {
            JobInfo jobInfo = jobInfoMapper.loadById(id);
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

    private Map<String, Object> buildExportData(JobInfo jobInfo) {
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

    private Response<String> importSingleJob(HttpServletRequest request, String jobJson) {
        JobInfo jobInfo;
        try {
            jobInfo = GSON.fromJson(jobJson, JobInfo.class);
        } catch (JsonSyntaxException e) {
            logger.error("Failed to parse job JSON: {}", e.getMessage());
            return Response.ofFail(I18nUtil.getString("jobinfo_import_json_invalid"));
        }

        if (jobInfo == null) {
            return Response.ofFail(I18nUtil.getString("jobinfo_import_json_invalid"));
        }

        JwtUserInfo userInfo =
                JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());

        jobInfo.setId(0);
        return orthJobService.add(jobInfo, userInfo);
    }

    private Response<String> importBatchJobs(HttpServletRequest request, String jobJson) {
        JobInfo[] jobs;
        try {
            jobs = GSON.fromJson(jobJson, JobInfo[].class);
        } catch (JsonSyntaxException e) {
            logger.error("Failed to parse job JSON array: {}", e.getMessage());
            return Response.ofFail(I18nUtil.getString("jobinfo_import_json_invalid"));
        }

        if (jobs == null || jobs.length == 0) {
            return Response.ofFail(I18nUtil.getString("jobinfo_import_json_invalid"));
        }

        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);
        if (userInfo == null) {
            return Response.ofFail("Not authenticated");
        }

        int successCount = 0;
        int failCount = 0;
        StringBuilder errorMessages = new StringBuilder();

        for (int i = 0; i < jobs.length; i++) {
            JobInfo job = jobs[i];
            if (job == null) {
                failCount++;
                continue;
            }

            try {
                JobGroupPermissionUtil.validJobGroupPermission(request, job.getJobGroup());
                job.setId(0);

                Response<String> result = orthJobService.add(job, userInfo);
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
}
