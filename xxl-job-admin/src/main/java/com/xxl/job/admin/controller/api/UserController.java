package com.xxl.job.admin.controller.api;

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

import com.xxl.job.admin.mapper.XxlJobGroupMapper;
import com.xxl.job.admin.mapper.XxlJobUserMapper;
import com.xxl.job.admin.model.XxlJobUser;
import com.xxl.job.admin.util.I18nUtil;
import com.xxl.job.admin.web.security.JwtUserInfo;
import com.xxl.job.admin.web.security.SecurityContext;
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

    @Resource private XxlJobUserMapper xxlJobUserMapper;
    @Resource private XxlJobGroupMapper xxlJobGroupMapper;

    @GetMapping
    public Response<PageModel<XxlJobUser>> pageList(
            HttpServletRequest request,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "10") int pagesize,
            @RequestParam(required = false, defaultValue = "") String username,
            @RequestParam(required = false, defaultValue = "-1") int role) {

        Response<String> adminCheck = requireAdmin(request);
        if (!adminCheck.isSuccess()) {
            return Response.ofFail(adminCheck.getMsg());
        }

        List<XxlJobUser> list = xxlJobUserMapper.pageList(offset, pagesize, username, role);
        int totalCount = xxlJobUserMapper.pageListCount(offset, pagesize, username, role);

        sanitizePasswords(list);

        PageModel<XxlJobUser> pageModel = new PageModel<>();
        pageModel.setData(list);
        pageModel.setTotal(totalCount);

        return Response.ofSuccess(pageModel);
    }

    @PostMapping
    public Response<String> insert(HttpServletRequest request, @RequestBody XxlJobUser xxlJobUser) {
        Response<String> adminCheck = requireAdmin(request);
        if (!adminCheck.isSuccess()) {
            return adminCheck;
        }

        Response<String> validationResult = validateNewUser(xxlJobUser);
        if (!validationResult.isSuccess()) {
            return validationResult;
        }

        XxlJobUser existingUser = xxlJobUserMapper.loadByUserName(xxlJobUser.getUsername());
        if (existingUser != null) {
            return Response.ofFail(I18nUtil.getString("user_username_repeat"));
        }

        String passwordHash = SHA256Tool.sha256(xxlJobUser.getPassword());
        xxlJobUser.setPassword(passwordHash);
        xxlJobUserMapper.save(xxlJobUser);

        return Response.ofSuccess();
    }

    @PutMapping("/{id}")
    public Response<String> update(
            HttpServletRequest request, @PathVariable int id, @RequestBody XxlJobUser xxlJobUser) {
        Response<String> adminCheck = requireAdmin(request);
        if (!adminCheck.isSuccess()) {
            return adminCheck;
        }

        xxlJobUser.setId(id);
        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);
        if (userInfo.getUsername().equals(xxlJobUser.getUsername())) {
            return Response.ofFail(I18nUtil.getString("user_update_loginuser_limit"));
        }

        if (StringTool.isNotBlank(xxlJobUser.getPassword())) {
            Response<String> passwordValidation = validatePassword(xxlJobUser.getPassword());
            if (!passwordValidation.isSuccess()) {
                return passwordValidation;
            }

            String passwordHash = SHA256Tool.sha256(xxlJobUser.getPassword().trim());
            xxlJobUser.setPassword(passwordHash);
        } else {
            xxlJobUser.setPassword(null);
        }

        xxlJobUserMapper.update(xxlJobUser);
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

        xxlJobUserMapper.delete(id);
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

    private Response<String> validateNewUser(XxlJobUser xxlJobUser) {
        if (StringTool.isBlank(xxlJobUser.getUsername())) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("user_username"));
        }

        xxlJobUser.setUsername(xxlJobUser.getUsername().trim());
        int usernameLength = xxlJobUser.getUsername().length();
        if (usernameLength < MIN_USERNAME_LENGTH || usernameLength > MAX_USERNAME_LENGTH) {
            return Response.ofFail(I18nUtil.getString("system_lengh_limit") + "[4-20]");
        }

        if (StringTool.isBlank(xxlJobUser.getPassword())) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("user_password"));
        }

        return validatePassword(xxlJobUser.getPassword());
    }

    private Response<String> validatePassword(String password) {
        String trimmedPassword = password.trim();
        int passwordLength = trimmedPassword.length();

        if (passwordLength < MIN_PASSWORD_LENGTH || passwordLength > MAX_PASSWORD_LENGTH) {
            return Response.ofFail(I18nUtil.getString("system_lengh_limit") + "[4-20]");
        }

        return Response.ofSuccess();
    }

    private void sanitizePasswords(List<XxlJobUser> users) {
        if (CollectionTool.isNotEmpty(users)) {
            users.forEach(user -> user.setPassword(null));
        }
    }
}
