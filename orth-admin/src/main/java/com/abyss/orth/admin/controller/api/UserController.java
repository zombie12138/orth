package com.abyss.orth.admin.controller.api;

import java.util.List;

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
import com.abyss.orth.admin.mapper.JobUserMapper;
import com.abyss.orth.admin.model.JobUser;
import com.abyss.orth.admin.util.I18nUtil;
import com.abyss.orth.admin.web.security.JwtUserInfo;
import com.abyss.orth.admin.web.security.SecurityContext;
import com.xxl.tool.core.CollectionTool;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.encrypt.SHA256Tool;
import com.xxl.tool.response.PageModel;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * User management REST API controller. Admin-only operations.
 *
 * @author xuxueli 2019-05-04 16:39:50
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final int MIN_USERNAME_LENGTH = 4;
    private static final int MAX_USERNAME_LENGTH = 20;
    private static final int MIN_PASSWORD_LENGTH = 4;
    private static final int MAX_PASSWORD_LENGTH = 20;
    private static final int ADMIN_ROLE = 1;

    @Resource private JobUserMapper jobUserMapper;
    @Resource private JobGroupMapper jobGroupMapper;

    @GetMapping
    public Response<PageModel<JobUser>> pageList(
            HttpServletRequest request,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "10") int pagesize,
            @RequestParam(required = false, defaultValue = "") String username,
            @RequestParam(required = false, defaultValue = "-1") int role) {

        Response<String> adminCheck = requireAdmin(request);
        if (!adminCheck.isSuccess()) {
            return Response.ofFail(adminCheck.getMsg());
        }

        List<JobUser> list = jobUserMapper.pageList(offset, pagesize, username, role);
        int totalCount = jobUserMapper.pageListCount(offset, pagesize, username, role);

        sanitizePasswords(list);

        PageModel<JobUser> pageModel = new PageModel<>();
        pageModel.setData(list);
        pageModel.setTotal(totalCount);

        return Response.ofSuccess(pageModel);
    }

    @PostMapping
    public Response<String> insert(HttpServletRequest request, @RequestBody JobUser orthJobUser) {
        Response<String> adminCheck = requireAdmin(request);
        if (!adminCheck.isSuccess()) {
            return adminCheck;
        }

        Response<String> validationResult = validateNewUser(orthJobUser);
        if (!validationResult.isSuccess()) {
            return validationResult;
        }

        JobUser existingUser = jobUserMapper.loadByUserName(orthJobUser.getUsername());
        if (existingUser != null) {
            return Response.ofFail(I18nUtil.getString("user_username_repeat"));
        }

        String passwordHash = SHA256Tool.sha256(orthJobUser.getPassword());
        orthJobUser.setPassword(passwordHash);
        jobUserMapper.save(orthJobUser);

        return Response.ofSuccess();
    }

    @PutMapping("/{id}")
    public Response<String> update(
            HttpServletRequest request, @PathVariable int id, @RequestBody JobUser orthJobUser) {
        Response<String> adminCheck = requireAdmin(request);
        if (!adminCheck.isSuccess()) {
            return adminCheck;
        }

        orthJobUser.setId(id);
        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);
        if (userInfo.getUsername().equals(orthJobUser.getUsername())) {
            return Response.ofFail(I18nUtil.getString("user_update_loginuser_limit"));
        }

        if (StringTool.isNotBlank(orthJobUser.getPassword())) {
            Response<String> passwordValidation = validatePassword(orthJobUser.getPassword());
            if (!passwordValidation.isSuccess()) {
                return passwordValidation;
            }

            String passwordHash = SHA256Tool.sha256(orthJobUser.getPassword().trim());
            orthJobUser.setPassword(passwordHash);
        } else {
            orthJobUser.setPassword(null);
        }

        jobUserMapper.update(orthJobUser);
        return Response.ofSuccess();
    }

    @DeleteMapping("/{id}")
    public Response<String> delete(HttpServletRequest request, @PathVariable int id) {
        Response<String> adminCheck = requireAdmin(request);
        if (!adminCheck.isSuccess()) {
            return adminCheck;
        }

        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);
        if (id == userInfo.getUserId()) {
            return Response.ofFail(I18nUtil.getString("user_update_loginuser_limit"));
        }

        jobUserMapper.delete(id);
        return Response.ofSuccess();
    }

    // ==================== Private Helper Methods ====================

    private Response<String> requireAdmin(HttpServletRequest request) {
        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);
        if (userInfo == null || userInfo.getRole() != ADMIN_ROLE) {
            return Response.ofFail(I18nUtil.getString("system_permission_limit"));
        }
        return Response.ofSuccess();
    }

    private Response<String> validateNewUser(JobUser orthJobUser) {
        if (StringTool.isBlank(orthJobUser.getUsername())) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("user_username"));
        }

        orthJobUser.setUsername(orthJobUser.getUsername().trim());
        int usernameLength = orthJobUser.getUsername().length();
        if (usernameLength < MIN_USERNAME_LENGTH || usernameLength > MAX_USERNAME_LENGTH) {
            return Response.ofFail(I18nUtil.getString("system_lengh_limit") + "[4-20]");
        }

        if (StringTool.isBlank(orthJobUser.getPassword())) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("user_password"));
        }

        return validatePassword(orthJobUser.getPassword());
    }

    private Response<String> validatePassword(String password) {
        String trimmedPassword = password.trim();
        int passwordLength = trimmedPassword.length();

        if (passwordLength < MIN_PASSWORD_LENGTH || passwordLength > MAX_PASSWORD_LENGTH) {
            return Response.ofFail(I18nUtil.getString("system_lengh_limit") + "[4-20]");
        }

        return Response.ofSuccess();
    }

    private void sanitizePasswords(List<JobUser> users) {
        if (CollectionTool.isNotEmpty(users)) {
            users.forEach(user -> user.setPassword(null));
        }
    }
}
