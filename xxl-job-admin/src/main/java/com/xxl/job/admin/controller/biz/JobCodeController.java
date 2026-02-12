package com.xxl.job.admin.controller.biz;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.xxl.job.admin.mapper.XxlJobInfoMapper;
import com.xxl.job.admin.mapper.XxlJobLogGlueMapper;
import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.model.XxlJobLogGlue;
import com.xxl.job.admin.util.I18nUtil;
import com.xxl.job.admin.util.JobGroupPermissionUtil;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.gson.GsonTool;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Job code controller for managing GLUE job scripts and code.
 *
 * <p>Handles operations including:
 *
 * <ul>
 *   <li>Viewing and editing job code (GLUE scripts)
 *   <li>Saving code changes with version history
 *   <li>Managing code backup retention (max 30 versions)
 * </ul>
 *
 * @author xuxueli 2015-12-19 16:13:16
 */
@Controller
@RequestMapping("/jobcode")
public class JobCodeController {

    private static final Logger logger = LoggerFactory.getLogger(JobCodeController.class);
    private static final int MIN_REMARK_LENGTH = 4;
    private static final int MAX_REMARK_LENGTH = 100;
    private static final int MAX_CODE_BACKUPS = 30;

    @Resource private XxlJobInfoMapper xxlJobInfoMapper;
    @Resource private XxlJobLogGlueMapper xxlJobLogGlueMapper;

    /**
     * Displays the job code editor page.
     *
     * @param request the HTTP request for permission validation
     * @param model the model for view rendering
     * @param jobId the job ID
     * @return the view name for job code editor page
     */
    @RequestMapping
    public String index(HttpServletRequest request, Model model, @RequestParam("jobId") int jobId) {
        XxlJobInfo jobInfo = xxlJobInfoMapper.loadById(jobId);
        List<XxlJobLogGlue> jobLogGlues = xxlJobLogGlueMapper.findByJobId(jobId);

        if (jobInfo == null) {
            throw new RuntimeException(I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
        }

        if (GlueTypeEnum.BEAN == GlueTypeEnum.match(jobInfo.getGlueType())) {
            throw new RuntimeException(I18nUtil.getString("jobinfo_glue_gluetype_unvalid"));
        }

        JobGroupPermissionUtil.validJobGroupPermission(request, jobInfo.getJobGroup());

        model.addAttribute("GlueTypeEnum", GlueTypeEnum.values());
        model.addAttribute("jobInfo", jobInfo);
        model.addAttribute("jobLogGlues", jobLogGlues);

        return "biz/job.code";
    }

    /**
     * Saves job code changes and creates a backup in version history.
     *
     * @param request the HTTP request for permission validation
     * @param id the job ID
     * @param glueSource the source code content
     * @param glueRemark the version remark/description
     * @return success or failure response
     */
    @RequestMapping("/save")
    @ResponseBody
    public Response<String> save(
            HttpServletRequest request,
            @RequestParam("id") int id,
            @RequestParam("glueSource") String glueSource,
            @RequestParam("glueRemark") String glueRemark) {

        Response<String> validationResult = validateCodeInput(glueSource, glueRemark);
        if (!validationResult.isSuccess()) {
            return validationResult;
        }

        XxlJobInfo existingJob = xxlJobInfoMapper.loadById(id);
        if (existingJob == null) {
            return Response.ofFail(I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
        }

        LoginInfo loginInfo =
                JobGroupPermissionUtil.validJobGroupPermission(request, existingJob.getJobGroup());

        updateJobCode(existingJob, glueSource, glueRemark);
        saveCodeBackup(existingJob, glueSource, glueRemark);
        cleanOldCodeBackups(existingJob.getId());
        logCodeUpdate(loginInfo, existingJob, glueSource, glueRemark);

        return Response.ofSuccess();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Validates code input parameters.
     *
     * @param glueSource the source code
     * @param glueRemark the version remark
     * @return success if valid, failure with error message otherwise
     */
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

    /**
     * Updates the job with new code.
     *
     * @param jobInfo the job to update
     * @param glueSource the new source code
     * @param glueRemark the version remark
     */
    private void updateJobCode(XxlJobInfo jobInfo, String glueSource, String glueRemark) {
        jobInfo.setGlueSource(glueSource);
        jobInfo.setGlueRemark(glueRemark);
        jobInfo.setGlueUpdatetime(new Date());
        jobInfo.setUpdateTime(new Date());
        xxlJobInfoMapper.update(jobInfo);
    }

    /**
     * Saves a backup of the code in version history.
     *
     * @param jobInfo the job info
     * @param glueSource the source code
     * @param glueRemark the version remark
     */
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

    /**
     * Removes old code backups keeping only the most recent versions.
     *
     * @param jobId the job ID
     */
    private void cleanOldCodeBackups(int jobId) {
        xxlJobLogGlueMapper.removeOld(jobId, MAX_CODE_BACKUPS);
    }

    /**
     * Logs the code update operation.
     *
     * @param loginInfo the user performing the update
     * @param jobInfo the job info
     * @param glueSource the source code
     * @param glueRemark the version remark
     */
    private void logCodeUpdate(
            LoginInfo loginInfo, XxlJobInfo jobInfo, String glueSource, String glueRemark) {
        XxlJobLogGlue logEntry = new XxlJobLogGlue();
        logEntry.setJobId(jobInfo.getId());
        logEntry.setGlueType(jobInfo.getGlueType());
        logEntry.setGlueSource(glueSource);
        logEntry.setGlueRemark(glueRemark);

        logger.info(
                ">>>>>>>>>>> orth operation log: operator = {}, type = {}, content = {}",
                loginInfo.getUserName(),
                "jobcode-update",
                GsonTool.toJson(logEntry));
    }
}
