package com.xxl.job.admin.controller.api;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import com.xxl.job.admin.mapper.XxlJobGroupMapper;
import com.xxl.job.admin.mapper.XxlJobInfoMapper;
import com.xxl.job.admin.mapper.XxlJobLogMapper;
import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.model.XxlJobLog;
import com.xxl.job.admin.scheduler.config.XxlJobAdminBootstrap;
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
 * Job log management REST API controller.
 *
 * @author xuxueli 2015-12-19 16:13:16
 */
@RestController
@RequestMapping("/api/v1/logs")
public class LogController {

    private static final Logger logger = LoggerFactory.getLogger(LogController.class);

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

    @GetMapping
    public Response<PageModel<XxlJobLog>> pageList(
            HttpServletRequest request,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "10") int pagesize,
            @RequestParam int jobGroup,
            @RequestParam int jobId,
            @RequestParam int logStatus,
            @RequestParam(required = false, defaultValue = "") String filterTime) {

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

    @GetMapping("/{id}/content")
    public Response<LogResult> logDetailCat(
            @PathVariable("id") long logId,
            @RequestParam(required = false, defaultValue = "1") int fromLineNum) {
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

    @PostMapping("/{id}/kill")
    public Response<String> logKill(HttpServletRequest request, @PathVariable("id") long id) {
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

    @PostMapping("/clear")
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

    // ==================== Private Helper Methods ====================

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

    private Response<String> sendKillRequest(XxlJobLog log, XxlJobInfo jobInfo) {
        try {
            ExecutorBiz executorBiz = XxlJobAdminBootstrap.getExecutorBiz(log.getExecutorAddress());
            return executorBiz.kill(new KillRequest(jobInfo.getId()));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return Response.ofFail(e.getMessage());
        }
    }

    private void updateLogAfterKill(XxlJobLog log, Response<String> killResult) {
        log.setHandleCode(XxlJobContext.HANDLE_CODE_FAIL);
        log.setHandleMsg(
                I18nUtil.getString("joblog_kill_log_byman")
                        + ":"
                        + (killResult.getMsg() != null ? killResult.getMsg() : ""));
        log.setHandleTime(new Date());
        XxlJobAdminBootstrap.getInstance().getJobCompleter().complete(log);
    }

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

    private void markLogEndIfComplete(XxlJobLog jobLog, Response<LogResult> logResult) {
        if (logResult.getData().getFromLineNum() > logResult.getData().getToLineNum()
                && jobLog.getHandleCode() > 0) {
            logResult.getData().setEnd(true);
        }
    }

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
