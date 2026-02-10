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
 * index controller
 *
 * @author xuxueli 2015-12-19 16:13:16
 */
@Controller
@RequestMapping("/jobinfo")
public class JobInfoController {
    private static Logger logger = LoggerFactory.getLogger(JobInfoController.class);

    @Resource private XxlJobGroupMapper xxlJobGroupMapper;
    @Resource private XxlJobInfoMapper xxlJobInfoMapper;
    @Resource private XxlJobService xxlJobService;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @RequestMapping
    public String index(
            HttpServletRequest request,
            Model model,
            @RequestParam(value = "jobGroup", required = false, defaultValue = "-1") int jobGroup) {

        // 枚举-字典
        model.addAttribute(
                "ExecutorRouteStrategyEnum", ExecutorRouteStrategyEnum.values()); // 路由策略-列表
        model.addAttribute("GlueTypeEnum", GlueTypeEnum.values()); // Glue类型-字典
        model.addAttribute(
                "ExecutorBlockStrategyEnum", ExecutorBlockStrategyEnum.values()); // 阻塞处理策略-字典
        model.addAttribute("ScheduleTypeEnum", ScheduleTypeEnum.values()); // 调度类型
        model.addAttribute("MisfireStrategyEnum", MisfireStrategyEnum.values()); // 调度过期策略

        // 执行器列表
        List<XxlJobGroup> jobGroupListTotal = xxlJobGroupMapper.findAll();

        // filter group
        List<XxlJobGroup> jobGroupList =
                JobGroupPermissionUtil.filterJobGroupByPermission(request, jobGroupListTotal);
        if (CollectionTool.isEmpty(jobGroupList)) {
            throw new XxlJobException(I18nUtil.getString("jobgroup_empty"));
        }

        // parse jobGroup
        if (!(CollectionTool.isNotEmpty(jobGroupList)
                && jobGroupList.stream().map(XxlJobGroup::getId).toList().contains(jobGroup))) {
            jobGroup = -1;
        }

        model.addAttribute("JobGroupList", jobGroupList);
        model.addAttribute("jobGroup", jobGroup);

        return "biz/job.list";
    }

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

        // valid jobGroup permission
        JobGroupPermissionUtil.validJobGroupPermission(request, jobGroup);

        // page
        return xxlJobService.pageList(
                offset, pagesize, jobGroup, triggerStatus, jobDesc, executorHandler, author, superTaskName);
    }

    @RequestMapping("/insert")
    @ResponseBody
    public Response<String> add(HttpServletRequest request, XxlJobInfo jobInfo) {
        // valid permission
        LoginInfo loginInfo =
                JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());

        // opt
        return xxlJobService.add(jobInfo, loginInfo);
    }

    @RequestMapping("/update")
    @ResponseBody
    public Response<String> update(HttpServletRequest request, XxlJobInfo jobInfo) {
        // valid permission
        LoginInfo loginInfo =
                JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());

        // opt
        return xxlJobService.update(jobInfo, loginInfo);
    }

    @RequestMapping("/delete")
    @ResponseBody
    public Response<String> delete(
            HttpServletRequest request, @RequestParam("ids[]") List<Integer> ids) {

        // valid
        if (CollectionTool.isEmpty(ids) || ids.size() != 1) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_choose")
                            + I18nUtil.getString("system_one")
                            + I18nUtil.getString("system_data"));
        }

        // invoke
        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);
        return xxlJobService.remove(ids.get(0), loginInfoResponse.getData());
    }

    @RequestMapping("/stop")
    @ResponseBody
    public Response<String> pause(
            HttpServletRequest request, @RequestParam("ids[]") List<Integer> ids) {

        // valid
        if (CollectionTool.isEmpty(ids) || ids.size() != 1) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_choose")
                            + I18nUtil.getString("system_one")
                            + I18nUtil.getString("system_data"));
        }

        // invoke
        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);
        return xxlJobService.stop(ids.get(0), loginInfoResponse.getData());
    }

    @RequestMapping("/start")
    @ResponseBody
    public Response<String> start(
            HttpServletRequest request, @RequestParam("ids[]") List<Integer> ids) {

        // valid
        if (CollectionTool.isEmpty(ids) || ids.size() != 1) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_choose")
                            + I18nUtil.getString("system_one")
                            + I18nUtil.getString("system_data"));
        }

        // invoke
        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);
        return xxlJobService.start(ids.get(0), loginInfoResponse.getData());
    }

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

        // Parse time strings to Date objects
        Date startTime = null;
        Date endTime = null;

        try {
            if (StringTool.isNotBlank(startTimeStr)) {
                startTime = DateTool.parseDateTime(startTimeStr);
            }
            if (StringTool.isNotBlank(endTimeStr)) {
                endTime = DateTool.parseDateTime(endTimeStr);
            }
        } catch (Exception e) {
            logger.error("Failed to parse time parameters: {}", e.getMessage());
            return Response.ofFail("Invalid time format. Expected: yyyy-MM-dd HH:mm:ss");
        }

        return xxlJobService.triggerBatch(
                loginInfoResponse.getData(), id, executorParam, addressList, startTime, endTime);
    }

    @RequestMapping("/previewTriggerBatch")
    @ResponseBody
    public Response<List<String>> previewTriggerBatch(
            HttpServletRequest request,
            @RequestParam("id") int id,
            @RequestParam(value = "startTime", required = false) String startTimeStr,
            @RequestParam(value = "endTime", required = false) String endTimeStr) {
        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);

        // Parse time strings to Date objects
        Date startTime = null;
        Date endTime = null;

        try {
            if (StringTool.isNotBlank(startTimeStr)) {
                startTime = DateTool.parseDateTime(startTimeStr);
            }
            if (StringTool.isNotBlank(endTimeStr)) {
                endTime = DateTool.parseDateTime(endTimeStr);
            }
        } catch (Exception e) {
            logger.error("Failed to parse time parameters: {}", e.getMessage());
            return Response.ofFail("Invalid time format. Expected: yyyy-MM-dd HH:mm:ss");
        }

        return xxlJobService.previewTriggerBatch(
                loginInfoResponse.getData(), id, startTime, endTime);
    }

    @RequestMapping("/export")
    @ResponseBody
    public Response<String> exportJob(
            HttpServletRequest request,
            @RequestParam(value = "id", required = false) Integer id,
            @RequestParam(value = "ids[]", required = false) List<Integer> ids) {

        // Determine single or batch export
        if (id != null) {
            // Single export (backward compatible)
            return exportSingleJob(request, id);
        } else if (CollectionTool.isNotEmpty(ids)) {
            // Batch export
            return exportBatchJobs(request, ids);
        } else {
            return Response.ofFail(
                    I18nUtil.getString("system_please_choose") + I18nUtil.getString("system_data"));
        }
    }

    private Response<String> exportSingleJob(HttpServletRequest request, int id) {
        // Load job
        XxlJobInfo jobInfo = xxlJobInfoMapper.loadById(id);
        if (jobInfo == null) {
            return Response.ofFail(
                    I18nUtil.getString("jobinfo_field_id") + I18nUtil.getString("system_unvalid"));
        }

        // Valid permission
        JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());

        // Build export data
        Map<String, Object> exportData = buildExportData(jobInfo);
        return Response.ofSuccess(GSON.toJson(exportData));
    }

    private Response<String> exportBatchJobs(HttpServletRequest request, List<Integer> ids) {
        List<Map<String, Object>> exportList = new ArrayList<>();

        for (Integer id : ids) {
            XxlJobInfo jobInfo = xxlJobInfoMapper.loadById(id);
            if (jobInfo == null) {
                logger.warn("Job not found for export: id={}", id);
                continue;
            }

            // Valid permission (skip if no permission)
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

    private Map<String, Object> buildExportData(XxlJobInfo jobInfo) {
        // Build export data (exclude id, timestamps, trigger status)
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

    @RequestMapping("/import")
    @ResponseBody
    public Response<String> importJob(HttpServletRequest request, @RequestBody String jobJson) {
        String trimmedJson = jobJson.trim();

        // Detect if JSON is array or object
        if (trimmedJson.startsWith("[")) {
            // Batch import
            return importBatchJobs(request, jobJson);
        } else if (trimmedJson.startsWith("{")) {
            // Single import (backward compatible)
            return importSingleJob(request, jobJson);
        } else {
            return Response.ofFail(I18nUtil.getString("jobinfo_import_json_invalid"));
        }
    }

    private Response<String> importSingleJob(HttpServletRequest request, String jobJson) {
        // Parse JSON
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

        // Valid permission
        LoginInfo loginInfo =
                JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());

        // Reset id to 0 to create new job
        jobInfo.setId(0);

        // Create job via service
        return xxlJobService.add(jobInfo, loginInfo);
    }

    private Response<String> importBatchJobs(HttpServletRequest request, String jobJson) {
        // Parse JSON array
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

        // Get login info
        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);
        if (loginInfoResponse.getCode() != 200) {
            return Response.ofFail(loginInfoResponse.getMsg());
        }
        LoginInfo loginInfo = loginInfoResponse.getData();

        // Import each job
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
                // Valid permission
                JobGroupPermissionUtil.validJobGroupPermission(request, job.getJobGroup());

                // Reset id to 0 to create new job
                job.setId(0);

                // Create job via service
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

        // Build result message
        String resultMsg =
                String.format("Imported %d/%d jobs successfully", successCount, jobs.length);
        if (failCount > 0) {
            resultMsg += ". Failures: " + errorMessages.toString();
        }

        return successCount > 0 ? Response.ofSuccess(resultMsg) : Response.ofFail(resultMsg);
    }

    @RequestMapping("/nextTriggerTime")
    @ResponseBody
    public Response<List<String>> nextTriggerTime(
            @RequestParam("scheduleType") String scheduleType,
            @RequestParam("scheduleConf") String scheduleConf) {

        // valid
        if (StringTool.isBlank(scheduleType) || StringTool.isBlank(scheduleConf)) {
            return Response.ofSuccess(new ArrayList<>());
        }

        // param
        XxlJobInfo paramXxlJobInfo = new XxlJobInfo();
        paramXxlJobInfo.setScheduleType(scheduleType);
        paramXxlJobInfo.setScheduleConf(scheduleConf);

        // generate
        List<String> result = new ArrayList<>();
        try {
            Date lastTime = new Date();
            for (int i = 0; i < 5; i++) {

                // generate next trigger time
                ScheduleTypeEnum scheduleTypeEnum =
                        ScheduleTypeEnum.match(
                                paramXxlJobInfo.getScheduleType(), ScheduleTypeEnum.NONE);
                lastTime =
                        scheduleTypeEnum
                                .getScheduleType()
                                .generateNextTriggerTime(paramXxlJobInfo, lastTime);

                // collect data
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
                    (I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid"))
                            + e.getMessage());
        }
        return Response.ofSuccess(result);
    }

    /**
     * Search SuperTasks by ID or description
     *
     * @param request HTTP request
     * @param jobGroup job group ID
     * @param query search query (job ID or description)
     * @return list of matching jobs
     */
    @RequestMapping("/searchSuperTask")
    @ResponseBody
    public Response<List<XxlJobInfo>> searchSuperTask(
            HttpServletRequest request,
            @RequestParam int jobGroup,
            @RequestParam String query) {
        // valid permission
        JobGroupPermissionUtil.validJobGroupPermission(request, jobGroup);

        // search jobs by ID or description
        List<XxlJobInfo> jobs = xxlJobInfoMapper.searchByIdOrDesc(jobGroup, query);
        return Response.ofSuccess(jobs);
    }

    /**
     * Get job by ID (for SuperTask lookup)
     *
     * @param request HTTP request
     * @param id job ID
     * @return job info
     */
    @RequestMapping("/getJobById")
    @ResponseBody
    public Response<XxlJobInfo> getJobById(HttpServletRequest request, @RequestParam int id) {
        XxlJobInfo job = xxlJobInfoMapper.loadById(id);
        if (job == null) {
            return Response.ofFail("Job not found");
        }

        // valid permission
        JobGroupPermissionUtil.validJobGroupPermission(request, job.getJobGroup());

        return Response.ofSuccess(job);
    }

    /**
     * Batch copy jobs from a template (SuperTask), creating multiple SubTasks
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
            } else {
                return Response.ofFail("All batch copy operations failed: " + result.getErrors());
            }
        } catch (Exception e) {
            logger.error("Batch copy failed", e);
            return Response.ofFail("Batch copy error: " + e.getMessage());
        }
    }
}
