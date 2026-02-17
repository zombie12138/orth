package com.xxl.job.admin.controller.api;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xxl.job.admin.mapper.XxlJobInfoMapper;
import com.xxl.job.admin.mapper.XxlJobLogGlueMapper;
import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.model.XxlJobLogGlue;
import com.xxl.job.admin.util.I18nUtil;
import com.xxl.job.admin.util.JobGroupPermissionUtil;
import com.xxl.job.admin.web.security.JwtUserInfo;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.gson.GsonTool;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * GLUE job code management REST API controller.
 *
 * @author xuxueli 2015-12-19 16:13:16
 */
@RestController
@RequestMapping("/api/v1/jobs/{jobId}/code")
public class GlueCodeController {

    private static final Logger logger = LoggerFactory.getLogger(GlueCodeController.class);
    private static final int MIN_REMARK_LENGTH = 4;
    private static final int MAX_REMARK_LENGTH = 100;
    private static final int MAX_CODE_BACKUPS = 30;

    @Resource private XxlJobInfoMapper xxlJobInfoMapper;
    @Resource private XxlJobLogGlueMapper xxlJobLogGlueMapper;

    @GetMapping
    public Response<Map<String, Object>> getCode(
            HttpServletRequest request, @PathVariable int jobId) {
        XxlJobInfo jobInfo = xxlJobInfoMapper.loadById(jobId);
        if (jobInfo == null) {
            return Response.ofFail(I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
        }

        if (GlueTypeEnum.BEAN == GlueTypeEnum.match(jobInfo.getGlueType())) {
            return Response.ofFail(I18nUtil.getString("jobinfo_glue_gluetype_unvalid"));
        }

        JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());

        List<XxlJobLogGlue> jobLogGlues = xxlJobLogGlueMapper.findByJobId(jobId);

        Map<String, Object> result = new HashMap<>();
        result.put("jobInfo", jobInfo);
        result.put("jobLogGlues", jobLogGlues);
        result.put("GlueTypeEnum", GlueTypeEnum.values());

        return Response.ofSuccess(result);
    }

    @PutMapping
    public Response<String> save(
            HttpServletRequest request,
            @PathVariable int jobId,
            @RequestParam("glueSource") String glueSource,
            @RequestParam("glueRemark") String glueRemark) {

        Response<String> validationResult = validateCodeInput(glueSource, glueRemark);
        if (!validationResult.isSuccess()) {
            return validationResult;
        }

        XxlJobInfo existingJob = xxlJobInfoMapper.loadById(jobId);
        if (existingJob == null) {
            return Response.ofFail(I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
        }

        JwtUserInfo userInfo =
                JobGroupPermissionUtil.validJobGroupPermission(request, existingJob.getJobGroup());

        updateJobCode(existingJob, glueSource, glueRemark);
        saveCodeBackup(existingJob, glueSource, glueRemark);
        cleanOldCodeBackups(existingJob.getId());
        logCodeUpdate(userInfo, existingJob, glueSource, glueRemark);

        return Response.ofSuccess();
    }

    // ==================== Private Helper Methods ====================

    private Response<String> validateCodeInput(String glueSource, String glueRemark) {
        if (StringTool.isBlank(glueSource)) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("jobinfo_glue_source"));
        }

        if (glueRemark == null) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("jobinfo_glue_remark"));
        }

        int remarkLength = glueRemark.length();
        if (remarkLength < MIN_REMARK_LENGTH || remarkLength > MAX_REMARK_LENGTH) {
            return Response.ofFail(I18nUtil.getString("jobinfo_glue_remark_limit"));
        }

        return Response.ofSuccess();
    }

    private void updateJobCode(XxlJobInfo jobInfo, String glueSource, String glueRemark) {
        jobInfo.setGlueSource(glueSource);
        jobInfo.setGlueRemark(glueRemark);
        jobInfo.setGlueUpdatetime(new Date());
        jobInfo.setUpdateTime(new Date());
        xxlJobInfoMapper.update(jobInfo);
    }

    private void saveCodeBackup(XxlJobInfo jobInfo, String glueSource, String glueRemark) {
        XxlJobLogGlue logGlue = new XxlJobLogGlue();
        logGlue.setJobId(jobInfo.getId());
        logGlue.setGlueType(jobInfo.getGlueType());
        logGlue.setGlueSource(glueSource);
        logGlue.setGlueRemark(glueRemark);
        logGlue.setAddTime(new Date());
        logGlue.setUpdateTime(new Date());

        xxlJobLogGlueMapper.save(logGlue);
    }

    private void cleanOldCodeBackups(int jobId) {
        xxlJobLogGlueMapper.removeOld(jobId, MAX_CODE_BACKUPS);
    }

    private void logCodeUpdate(
            JwtUserInfo userInfo, XxlJobInfo jobInfo, String glueSource, String glueRemark) {
        XxlJobLogGlue logEntry = new XxlJobLogGlue();
        logEntry.setJobId(jobInfo.getId());
        logEntry.setGlueType(jobInfo.getGlueType());
        logEntry.setGlueSource(glueSource);
        logEntry.setGlueRemark(glueRemark);

        logger.info(
                ">>>>>>>>>>> orth operation log: operator = {}, type = {}, content = {}",
                userInfo.getUsername(),
                "jobcode-update",
                GsonTool.toJson(logEntry));
    }
}
