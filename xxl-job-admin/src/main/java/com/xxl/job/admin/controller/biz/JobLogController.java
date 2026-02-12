package com.xxl.job.admin.controller.biz;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

import com.xxl.job.admin.mapper.XxlJobGroupMapper;
import com.xxl.job.admin.mapper.XxlJobInfoMapper;
import com.xxl.job.admin.mapper.XxlJobLogMapper;
import com.xxl.job.admin.model.XxlJobGroup;
import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.model.XxlJobLog;
import com.xxl.job.admin.scheduler.config.XxlJobAdminBootstrap;
import com.xxl.job.admin.scheduler.exception.XxlJobException;
import com.xxl.job.admin.service.XxlJobService;
import com.xxl.job.admin.util.I18nUtil;
import com.xxl.job.admin.util.JobGroupPermissionUtil;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.openapi.ExecutorBiz;
import com.xxl.job.core.openapi.model.KillRequest;
import com.xxl.job.core.openapi.model.LogRequest;
import com.xxl.job.core.openapi.model.LogResult;
import com.xxl.tool.core.CollectionTool;
import com.xxl.tool.core.DateTool;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.response.PageModel;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Job log controller for managing and viewing job execution logs.
 *
 * <p>Handles operations including:
 *
 * <ul>
 *   <li>Viewing and filtering job execution logs
 *   <li>Killing running jobs
 *   <li>Clearing old logs with various retention policies
 *   <li>Viewing detailed log content from executors
 * </ul>
 *
 * @author xuxueli 2015-12-19 16:13:16
 */
@Controller
@RequestMapping("/joblog")
public class JobLogController {
    private static final Logger logger = LoggerFactory.getLogger(JobLogController.class);

    private static final int CLEAR_TYPE_ONE_MONTH = 1;
    private static final int CLEAR_TYPE_THREE_MONTHS = 2;
    private static final int CLEAR_TYPE_SIX_MONTHS = 3;
    private static final int CLEAR_TYPE_ONE_YEAR = 4;
    private static final int CLEAR_TYPE_1K = 5;
    private static final int CLEAR_TYPE_10K = 6;
    private static final int CLEAR_TYPE_30K = 7;
    private static final int CLEAR_TYPE_100K = 8;
    private static final int CLEAR_TYPE_ALL = 9;
    private static final int BATCH_DELETE_SIZE = 1000;

    @Resource private XxlJobGroupMapper xxlJobGroupMapper;
    @Resource public XxlJobInfoMapper xxlJobInfoMapper;
    @Resource public XxlJobLogMapper xxlJobLogMapper;
    @Resource private XxlJobService xxlJobService;

    /**
     * Displays the job log list page with filtered job groups and jobs.
     *
     * @param request the HTTP request
     * @param model the model for view rendering
     * @param jobGroup the job group filter (optional)
     * @param jobId the job ID filter (optional)
     * @return the view name for log list page
     */
    @RequestMapping
    public String index(
            HttpServletRequest request,
            Model model,
            @RequestParam(value = "jobGroup", required = false, defaultValue = "0")
                    Integer jobGroup,
            @RequestParam(value = "jobId", required = false, defaultValue = "0") Integer jobId) {

        List<XxlJobGroup> jobGroupListTotal = xxlJobGroupMapper.findAll();
        List<XxlJobGroup> jobGroupList =
                JobGroupPermissionUtil.filterJobGroupByPermission(request, jobGroupListTotal);

        if (CollectionTool.isEmpty(jobGroupList)) {
            throw new XxlJobException(I18nUtil.getString("jobgroup_empty"));
        }

        jobGroup = resolveJobGroup(jobId, jobGroup, jobGroupListTotal, jobGroupList);
        List<XxlJobInfo> jobInfoList = xxlJobInfoMapper.getJobsByGroup(jobGroup);
        jobId = resolveJobId(jobId, jobInfoList);

        model.addAttribute("JobGroupList", jobGroupList);
        model.addAttribute("jobInfoList", jobInfoList);
        model.addAttribute("jobGroup", jobGroup);
        model.addAttribute("jobId", jobId);

        return "biz/log.list";
    }

    /**
     * Retrieves a paginated list of job logs with optional filtering.
     *
     * @param request the HTTP request for permission validation
     * @param offset the starting offset for pagination
     * @param pagesize the page size
     * @param jobGroup the job group ID
     * @param jobId the job ID
     * @param logStatus the log status filter
     * @param filterTime the time range filter (format: "start - end")
     * @return paginated response containing job logs
     */
    @RequestMapping("/pageList")
    @ResponseBody
    public Response<PageModel<XxlJobLog>> pageList(
            HttpServletRequest request,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "10") int pagesize,
            @RequestParam int jobGroup,
            @RequestParam int jobId,
            @RequestParam int logStatus,
            @RequestParam String filterTime) {

        JobGroupPermissionUtil.validJobGroupPermission(request, jobGroup);

        if (jobId < 1) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_choose") + I18nUtil.getString("jobinfo_job"));
        }

        Date[] timeRange = parseFilterTime(filterTime);
        List<XxlJobLog> list =
                xxlJobLogMapper.pageList(
                        offset, pagesize, jobGroup, jobId, timeRange[0], timeRange[1], logStatus);
        int totalCount =
                xxlJobLogMapper.pageListCount(
                        offset, pagesize, jobGroup, jobId, timeRange[0], timeRange[1], logStatus);

        PageModel<XxlJobLog> pageModel = new PageModel<>();
        pageModel.setData(list);
        pageModel.setTotal(totalCount);

        return Response.ofSuccess(pageModel);
    }

    /**
     * Kills a running job by log ID.
     *
     * @param request the HTTP request for permission validation
     * @param id the log ID
     * @return success or failure response
     */
    @RequestMapping("/logKill")
    @ResponseBody
    public Response<String> logKill(HttpServletRequest request, @RequestParam("id") long id) {
        XxlJobLog log = xxlJobLogMapper.load(id);
        XxlJobInfo jobInfo = xxlJobInfoMapper.loadById(log.getJobId());

        if (jobInfo == null) {
            return Response.ofFail(I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
        }

        if (log.getTriggerCode() != XxlJobContext.HANDLE_CODE_SUCCESS) {
            return Response.ofFail(I18nUtil.getString("joblog_kill_log_limit"));
        }

        JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());

        Response<String> killResult = sendKillRequest(log, jobInfo);

        if (killResult.getCode() == XxlJobContext.HANDLE_CODE_SUCCESS) {
            updateLogAfterKill(log, killResult);
            return Response.ofSuccess(killResult.getMsg());
        }

        return Response.ofFail(killResult.getMsg());
    }

    /**
     * Clears old job logs based on retention policy type.
     *
     * @param request the HTTP request for permission validation
     * @param jobGroup the job group ID
     * @param jobId the job ID
     * @param type the clear type (1-9 representing different retention policies)
     * @return success or failure response
     */
    @RequestMapping("/clearLog")
    @ResponseBody
    public Response<String> clearLog(
            HttpServletRequest request,
            @RequestParam("jobGroup") int jobGroup,
            @RequestParam("jobId") int jobId,
            @RequestParam("type") int type) {

        JobGroupPermissionUtil.validJobGroupPermission(request, jobGroup);

        if (jobId < 1) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_choose") + I18nUtil.getString("jobinfo_job"));
        }

        Date clearBeforeTime = null;
        int clearBeforeNum = 0;

        switch (type) {
            case CLEAR_TYPE_ONE_MONTH:
                clearBeforeTime = DateTool.addMonths(new Date(), -1);
                break;
            case CLEAR_TYPE_THREE_MONTHS:
                clearBeforeTime = DateTool.addMonths(new Date(), -3);
                break;
            case CLEAR_TYPE_SIX_MONTHS:
                clearBeforeTime = DateTool.addMonths(new Date(), -6);
                break;
            case CLEAR_TYPE_ONE_YEAR:
                clearBeforeTime = DateTool.addYears(new Date(), -1);
                break;
            case CLEAR_TYPE_1K:
                clearBeforeNum = 1000;
                break;
            case CLEAR_TYPE_10K:
                clearBeforeNum = 10000;
                break;
            case CLEAR_TYPE_30K:
                clearBeforeNum = 30000;
                break;
            case CLEAR_TYPE_100K:
                clearBeforeNum = 100000;
                break;
            case CLEAR_TYPE_ALL:
                clearBeforeNum = 0;
                break;
            default:
                return Response.ofFail(I18nUtil.getString("joblog_clean_type_unvalid"));
        }

        clearLogsBatch(jobGroup, jobId, clearBeforeTime, clearBeforeNum);
        return Response.ofSuccess();
    }

    /**
     * Displays the detailed log page for a specific log entry.
     *
     * @param request the HTTP request for permission validation
     * @param id the log ID
     * @param model the model for view rendering
     * @return the view name for log detail page
     */
    @RequestMapping("/logDetailPage")
    public String logDetailPage(
            HttpServletRequest request, @RequestParam("id") long id, Model model) {

        XxlJobLog jobLog = xxlJobLogMapper.load(id);
        if (jobLog == null) {
            throw new RuntimeException(I18nUtil.getString("joblog_logid_unvalid"));
        }

        JobGroupPermissionUtil.validJobGroupPermission(request, jobLog.getJobGroup());
        XxlJobInfo jobInfo = xxlJobInfoMapper.loadById(jobLog.getJobId());

        model.addAttribute("triggerCode", jobLog.getTriggerCode());
        model.addAttribute("handleCode", jobLog.getHandleCode());
        model.addAttribute("logId", jobLog.getId());
        model.addAttribute("jobInfo", jobInfo);

        return "biz/log.detail";
    }

    /**
     * Retrieves log content from executor starting from a specific line number.
     *
     * @param logId the log ID
     * @param fromLineNum the starting line number
     * @return log result containing log content and metadata
     */
    @RequestMapping("/logDetailCat")
    @ResponseBody
    public Response<LogResult> logDetailCat(
            @RequestParam("logId") long logId, @RequestParam("fromLineNum") int fromLineNum) {
        try {
            XxlJobLog jobLog = xxlJobLogMapper.load(logId);
            if (jobLog == null) {
                return Response.ofFail(I18nUtil.getString("joblog_logid_unvalid"));
            }

            ExecutorBiz executorBiz =
                    XxlJobAdminBootstrap.getExecutorBiz(jobLog.getExecutorAddress());
            Response<LogResult> logResult =
                    executorBiz.log(
                            new LogRequest(jobLog.getTriggerTime().getTime(), logId, fromLineNum));

            if (logResult.getData() != null) {
                markLogEndIfComplete(jobLog, logResult);
                sanitizeLogContent(logResult);
            }

            return logResult;
        } catch (Exception e) {
            logger.error("logId({}) logDetailCat error: {}", logId, e.getMessage(), e);
            return Response.ofFail(e.getMessage());
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Resolves the job group ID based on provided parameters.
     *
     * @param jobId the job ID
     * @param jobGroup the job group ID
     * @param jobGroupListTotal all available job groups
     * @param jobGroupList filtered job groups by permission
     * @return the resolved job group ID
     */
    private Integer resolveJobGroup(
            Integer jobId,
            Integer jobGroup,
            List<XxlJobGroup> jobGroupListTotal,
            List<XxlJobGroup> jobGroupList) {

        if (jobId > 0) {
            XxlJobInfo jobInfo = xxlJobInfoMapper.loadById(jobId);
            if (jobInfo == null) {
                throw new RuntimeException(
                        I18nUtil.getString("jobinfo_field_id")
                                + I18nUtil.getString("system_unvalid"));
            }
            return jobInfo.getJobGroup();
        }

        if (jobGroup > 0) {
            Integer finalJobGroup = jobGroup;
            boolean groupExists =
                    jobGroupListTotal.stream().anyMatch(item -> item.getId() == finalJobGroup);
            if (!groupExists) {
                return jobGroupList.get(0).getId();
            }
            return jobGroup;
        }

        return jobGroupList.get(0).getId();
    }

    /**
     * Resolves the job ID based on the available jobs in the group.
     *
     * @param jobId the job ID
     * @param jobInfoList list of jobs in the group
     * @return the resolved job ID (0 if invalid or empty list)
     */
    private Integer resolveJobId(Integer jobId, List<XxlJobInfo> jobInfoList) {
        if (CollectionTool.isEmpty(jobInfoList)) {
            return 0;
        }

        boolean jobExists = jobInfoList.stream().anyMatch(job -> job.getId() == jobId);
        return jobExists ? jobId : jobInfoList.get(0).getId();
    }

    /**
     * Parses the filter time string into start and end dates.
     *
     * @param filterTime the time range string (format: "start - end")
     * @return array with [startDate, endDate], or [null, null] if invalid
     */
    private Date[] parseFilterTime(String filterTime) {
        Date[] result = new Date[2];

        if (StringTool.isNotBlank(filterTime)) {
            String[] parts = filterTime.split(" - ");
            if (parts.length == 2) {
                result[0] = DateTool.parseDateTime(parts[0]);
                result[1] = DateTool.parseDateTime(parts[1]);
            }
        }

        return result;
    }

    /**
     * Sends a kill request to the executor.
     *
     * @param log the job log
     * @param jobInfo the job info
     * @return the kill response
     */
    private Response<String> sendKillRequest(XxlJobLog log, XxlJobInfo jobInfo) {
        try {
            ExecutorBiz executorBiz = XxlJobAdminBootstrap.getExecutorBiz(log.getExecutorAddress());
            return executorBiz.kill(new KillRequest(jobInfo.getId()));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return Response.ofFail(e.getMessage());
        }
    }

    /**
     * Updates the log after a successful kill operation.
     *
     * @param log the job log to update
     * @param killResult the kill result
     */
    private void updateLogAfterKill(XxlJobLog log, Response<String> killResult) {
        log.setHandleCode(XxlJobContext.HANDLE_CODE_FAIL);
        log.setHandleMsg(
                I18nUtil.getString("joblog_kill_log_byman")
                        + ":"
                        + (killResult.getMsg() != null ? killResult.getMsg() : ""));
        log.setHandleTime(new Date());
        XxlJobAdminBootstrap.getInstance().getJobCompleter().complete(log);
    }

    /**
     * Clears logs in batches based on retention criteria.
     *
     * @param jobGroup the job group ID
     * @param jobId the job ID
     * @param clearBeforeTime the cutoff date (null if using count-based clearing)
     * @param clearBeforeNum the number of logs to keep (0 for all)
     */
    private void clearLogsBatch(int jobGroup, int jobId, Date clearBeforeTime, int clearBeforeNum) {
        List<Long> logIds;
        do {
            logIds =
                    xxlJobLogMapper.findClearLogIds(
                            jobGroup, jobId, clearBeforeTime, clearBeforeNum, BATCH_DELETE_SIZE);
            if (CollectionTool.isNotEmpty(logIds)) {
                xxlJobLogMapper.clearLog(logIds);
            }
        } while (CollectionTool.isNotEmpty(logIds));
    }

    /**
     * Marks the log as ended if all content has been read and job is complete.
     *
     * @param jobLog the job log
     * @param logResult the log result to update
     */
    private void markLogEndIfComplete(XxlJobLog jobLog, Response<LogResult> logResult) {
        if (logResult.getData().getFromLineNum() > logResult.getData().getToLineNum()
                && jobLog.getHandleCode() > 0) {
            logResult.getData().setEnd(true);
        }
    }

    /**
     * Sanitizes log content to prevent XSS attacks while preserving safe HTML tags.
     *
     * @param logResult the log result containing content to sanitize
     */
    private void sanitizeLogContent(Response<LogResult> logResult) {
        if (logResult.getData() == null
                || StringTool.isBlank(logResult.getData().getLogContent())) {
            return;
        }

        String content = logResult.getData().getLogContent();
        Map<String, String> safeTags = new HashMap<>();
        safeTags.put("<br>", "###TAG_BR###");
        safeTags.put("<b>", "###TAG_BOLD###");
        safeTags.put("</b>", "###TAG_BOLD_END###");

        for (Map.Entry<String, String> entry : safeTags.entrySet()) {
            content = content.replace(entry.getKey(), entry.getValue());
        }

        content = HtmlUtils.htmlEscape(content, "UTF-8");

        for (Map.Entry<String, String> entry : safeTags.entrySet()) {
            content = content.replace(entry.getValue(), entry.getKey());
        }

        logResult.getData().setLogContent(content);
    }
}
