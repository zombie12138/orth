package com.xxl.job.admin.controller.biz;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.xxl.job.admin.constant.Consts;
import com.xxl.job.admin.mapper.XxlJobGroupMapper;
import com.xxl.job.admin.mapper.XxlJobUserMapper;
import com.xxl.job.admin.model.XxlJobGroup;
import com.xxl.job.admin.model.XxlJobUser;
import com.xxl.job.admin.util.I18nUtil;
import com.xxl.sso.core.annotation.XxlSso;
import com.xxl.sso.core.helper.XxlSsoHelper;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.tool.core.CollectionTool;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.encrypt.SHA256Tool;
import com.xxl.tool.response.PageModel;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Job user controller for managing user accounts.
 *
 * <p>Handles operations including:
 *
 * <ul>
 *   <li>Listing and paginating users
 *   <li>Creating new user accounts
 *   <li>Updating user information and passwords
 *   <li>Deleting user accounts with validation
 * </ul>
 *
 * @author xuxueli 2019-05-04 16:39:50
 */
@Controller
@RequestMapping("/user")
public class JobUserController {

    private static final int MIN_USERNAME_LENGTH = 4;
    private static final int MAX_USERNAME_LENGTH = 20;
    private static final int MIN_PASSWORD_LENGTH = 4;
    private static final int MAX_PASSWORD_LENGTH = 20;

    @Resource private XxlJobUserMapper xxlJobUserMapper;
    @Resource private XxlJobGroupMapper xxlJobGroupMapper;

    /**
     * Displays the user list page.
     *
     * @param model the model for view rendering
     * @return the view name for user list page
     */
    @RequestMapping
    @XxlSso(role = Consts.ADMIN_ROLE)
    public String index(Model model) {
        List<XxlJobGroup> groupList = xxlJobGroupMapper.findAll();
        model.addAttribute("groupList", groupList);
        return "biz/user.list";
    }

    /**
     * Retrieves a paginated list of users.
     *
     * @param offset the starting offset for pagination
     * @param pagesize the page size
     * @param username optional filter by username
     * @param role the role filter
     * @return paginated response containing users
     */
    @RequestMapping("/pageList")
    @ResponseBody
    @XxlSso(role = Consts.ADMIN_ROLE)
    public Response<PageModel<XxlJobUser>> pageList(
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "10") int pagesize,
            @RequestParam String username,
            @RequestParam int role) {

        List<XxlJobUser> list = xxlJobUserMapper.pageList(offset, pagesize, username, role);
        int totalCount = xxlJobUserMapper.pageListCount(offset, pagesize, username, role);

        sanitizePasswords(list);

        PageModel<XxlJobUser> pageModel = new PageModel<>();
        pageModel.setData(list);
        pageModel.setTotal(totalCount);

        return Response.ofSuccess(pageModel);
    }

    /**
     * Creates a new user account.
     *
     * @param xxlJobUser the user to create
     * @return success or failure response
     */
    @RequestMapping("/insert")
    @ResponseBody
    @XxlSso(role = Consts.ADMIN_ROLE)
    public Response<String> insert(XxlJobUser xxlJobUser) {
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

    /**
     * Updates an existing user account.
     *
     * @param request the HTTP request for login info
     * @param xxlJobUser the user to update
     * @return success or failure response
     */
    @RequestMapping("/update")
    @ResponseBody
    @XxlSso(role = Consts.ADMIN_ROLE)
    public Response<String> update(HttpServletRequest request, XxlJobUser xxlJobUser) {
        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);
        if (loginInfoResponse.getData().getUserName().equals(xxlJobUser.getUsername())) {
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

    /**
     * Deletes a user account by ID.
     *
     * @param request the HTTP request for login info
     * @param ids the list of user IDs (only one allowed)
     * @return success or failure response
     */
    @RequestMapping("/delete")
    @ResponseBody
    @XxlSso(role = Consts.ADMIN_ROLE)
    public Response<String> delete(
            HttpServletRequest request, @RequestParam("ids[]") List<Integer> ids) {

        if (CollectionTool.isEmpty(ids) || ids.size() != 1) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_choose")
                            + I18nUtil.getString("system_one")
                            + I18nUtil.getString("system_data"));
        }

        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);
        if (ids.contains(Integer.parseInt(loginInfoResponse.getData().getUserId()))) {
            return Response.ofFail(I18nUtil.getString("user_update_loginuser_limit"));
        }

        xxlJobUserMapper.delete(ids.get(0));
        return Response.ofSuccess();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Validates a new user's username and password.
     *
     * @param xxlJobUser the user to validate
     * @return success if valid, failure with error message otherwise
     */
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

    /**
     * Validates a password meets length requirements.
     *
     * @param password the password to validate
     * @return success if valid, failure with error message otherwise
     */
    private Response<String> validatePassword(String password) {
        String trimmedPassword = password.trim();
        int passwordLength = trimmedPassword.length();

        if (passwordLength < MIN_PASSWORD_LENGTH || passwordLength > MAX_PASSWORD_LENGTH) {
            return Response.ofFail(I18nUtil.getString("system_lengh_limit") + "[4-20]");
        }

        return Response.ofSuccess();
    }

    /**
     * Removes passwords from user list for security.
     *
     * @param users the list of users to sanitize
     */
    private void sanitizePasswords(List<XxlJobUser> users) {
        if (CollectionTool.isNotEmpty(users)) {
            users.forEach(user -> user.setPassword(null));
        }
    }
}
