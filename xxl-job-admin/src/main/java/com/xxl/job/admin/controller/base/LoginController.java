package com.xxl.job.admin.controller.base;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import com.xxl.job.admin.mapper.XxlJobUserMapper;
import com.xxl.job.admin.model.XxlJobUser;
import com.xxl.job.admin.util.I18nUtil;
import com.xxl.sso.core.annotation.XxlSso;
import com.xxl.sso.core.helper.XxlSsoHelper;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.encrypt.SHA256Tool;
import com.xxl.tool.id.UUIDTool;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Login controller for authentication and password management.
 *
 * <p>Handles operations including:
 *
 * <ul>
 *   <li>User login with cookie-based session
 *   <li>User logout
 *   <li>Password updates for logged-in users
 * </ul>
 *
 * @author xuxueli 2015-12-19 16:13:16
 */
@Controller
@RequestMapping("/auth")
public class LoginController {

    private static final int MIN_PASSWORD_LENGTH = 4;
    private static final int MAX_PASSWORD_LENGTH = 20;
    private static final String REMEMBER_ME_FLAG = "on";

    @Resource private XxlJobUserMapper xxlJobUserMapper;

    /**
     * Displays the login page or redirects to home if already authenticated.
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param modelAndView the model and view
     * @return the login view or redirect to home
     */
    @RequestMapping("/login")
    @XxlSso(login = false)
    public ModelAndView login(
            HttpServletRequest request, HttpServletResponse response, ModelAndView modelAndView) {

        Response<LoginInfo> loginInfoResponse =
                XxlSsoHelper.loginCheckWithCookie(request, response);

        if (loginInfoResponse.isSuccess()) {
            modelAndView.setView(new RedirectView("/", true, false));
            return modelAndView;
        }

        return new ModelAndView("base/login");
    }

    /**
     * Processes login request and creates session.
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param userName the username
     * @param password the password
     * @param ifRemember the "remember me" flag
     * @return success or failure response
     */
    @RequestMapping(value = "/doLogin", method = RequestMethod.POST)
    @ResponseBody
    @XxlSso(login = false)
    public Response<String> doLogin(
            HttpServletRequest request,
            HttpServletResponse response,
            String userName,
            String password,
            String ifRemember) {

        if (StringTool.isBlank(userName) || StringTool.isBlank(password)) {
            return Response.ofFail(I18nUtil.getString("login_param_empty"));
        }

        boolean rememberMe = REMEMBER_ME_FLAG.equals(ifRemember);
        XxlJobUser user = xxlJobUserMapper.loadByUserName(userName);

        if (user == null) {
            return Response.ofFail(I18nUtil.getString("login_param_unvalid"));
        }

        String passwordHash = SHA256Tool.sha256(password);
        if (!passwordHash.equals(user.getPassword())) {
            return Response.ofFail(I18nUtil.getString("login_param_unvalid"));
        }

        LoginInfo loginInfo = new LoginInfo(String.valueOf(user.getId()), UUIDTool.getSimpleUUID());
        Response<String> result = XxlSsoHelper.loginWithCookie(loginInfo, response, rememberMe);

        return Response.of(result.getCode(), result.getMsg());
    }

    /**
     * Processes logout request and clears session.
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @return success or failure response
     */
    @RequestMapping(value = "/logout", method = RequestMethod.POST)
    @ResponseBody
    @XxlSso(login = false)
    public Response<String> logout(HttpServletRequest request, HttpServletResponse response) {
        Response<String> result = XxlSsoHelper.logoutWithCookie(request, response);
        return Response.of(result.getCode(), result.getMsg());
    }

    /**
     * Updates the password for the currently logged-in user.
     *
     * @param request the HTTP request for login info
     * @param oldPassword the old password
     * @param password the new password
     * @return success or failure response
     */
    @RequestMapping("/updatePwd")
    @ResponseBody
    @XxlSso
    public Response<String> updatePwd(
            HttpServletRequest request, String oldPassword, String password) {

        Response<String> validationResult = validatePasswordUpdate(oldPassword, password);
        if (!validationResult.isSuccess()) {
            return validationResult;
        }

        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);
        XxlJobUser existingUser =
                xxlJobUserMapper.loadByUserName(loginInfoResponse.getData().getUserName());

        String oldPasswordHash = SHA256Tool.sha256(oldPassword);
        if (!oldPasswordHash.equals(existingUser.getPassword())) {
            return Response.ofFail(
                    I18nUtil.getString("change_pwd_field_oldpwd")
                            + I18nUtil.getString("system_unvalid"));
        }

        String newPasswordHash = SHA256Tool.sha256(password);
        existingUser.setPassword(newPasswordHash);
        xxlJobUserMapper.update(existingUser);

        return Response.ofSuccess();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Validates password update input parameters.
     *
     * @param oldPassword the old password
     * @param newPassword the new password
     * @return success if valid, failure with error message otherwise
     */
    private Response<String> validatePasswordUpdate(String oldPassword, String newPassword) {
        if (StringTool.isBlank(oldPassword)) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("change_pwd_field_oldpwd"));
        }

        if (StringTool.isBlank(newPassword)) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("change_pwd_field_oldpwd"));
        }

        String trimmedPassword = newPassword.trim();
        int passwordLength = trimmedPassword.length();

        if (passwordLength < MIN_PASSWORD_LENGTH || passwordLength > MAX_PASSWORD_LENGTH) {
            return Response.ofFail(I18nUtil.getString("system_lengh_limit") + "[4-20]");
        }

        return Response.ofSuccess();
    }
}
