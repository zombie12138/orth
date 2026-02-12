package com.xxl.job.admin.controller.base;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.xxl.job.admin.constant.Consts;
import com.xxl.job.admin.model.dto.XxlBootResourceDTO;
import com.xxl.job.admin.service.XxlJobService;
import com.xxl.job.admin.util.I18nUtil;
import com.xxl.sso.core.annotation.XxlSso;
import com.xxl.sso.core.helper.XxlSsoHelper;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Index controller for main application navigation and dashboard.
 *
 * <p>Handles operations including:
 *
 * <ul>
 *   <li>Main index page with menu navigation
 *   <li>Dashboard with job statistics
 *   <li>Chart data for visualization
 *   <li>Help page
 *   <li>Error page handling
 * </ul>
 *
 * @author xuxueli 2015-12-19 16:13:16
 */
@Controller
public class IndexController {

    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Resource private XxlJobService xxlJobService;

    /**
     * Displays the main index page with navigation menu.
     *
     * @param request the HTTP request for permission validation
     * @param model the model for view rendering
     * @return the view name for index page
     */
    @RequestMapping("/")
    @XxlSso
    public String index(HttpServletRequest request, Model model) {
        List<XxlBootResourceDTO> resourceList = buildMenuResources(request);
        model.addAttribute("resourceList", resourceList);
        return "base/index";
    }

    /**
     * Displays the dashboard page with job statistics.
     *
     * @param request the HTTP request
     * @param model the model for view rendering
     * @return the view name for dashboard page
     */
    @RequestMapping("/dashboard")
    @XxlSso
    public String dashboard(HttpServletRequest request, Model model) {
        Map<String, Object> dashboardData = xxlJobService.dashboardInfo();
        model.addAllAttributes(dashboardData);
        return "base/dashboard";
    }

    /**
     * Retrieves chart data for job statistics visualization.
     *
     * @param startDate the start date for chart data
     * @param endDate the end date for chart data
     * @return chart data response
     */
    @RequestMapping("/chartInfo")
    @ResponseBody
    public Response<Map<String, Object>> chartInfo(
            @RequestParam("startDate") Date startDate, @RequestParam("endDate") Date endDate) {
        return xxlJobService.chartInfo(startDate, endDate);
    }

    /**
     * Displays the help page.
     *
     * @return the view name for help page
     */
    @RequestMapping("/help")
    @XxlSso
    public String help() {
        return "base/help";
    }

    /**
     * Displays the error page with exception information.
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param mv the model and view
     * @return the error page model and view
     */
    @RequestMapping(value = "/errorpage")
    @XxlSso(login = false)
    public ModelAndView errorPage(
            HttpServletRequest request, HttpServletResponse response, ModelAndView mv) {

        String exceptionMsg = "HTTP Status Code: " + response.getStatus();
        mv.addObject("exceptionMsg", exceptionMsg);
        mv.setViewName("common/common.errorpage");

        return mv;
    }

    /**
     * Initializes the WebDataBinder for date formatting.
     *
     * @param binder the web data binder
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_PATTERN);
        dateFormat.setLenient(false);
        binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true));
    }

    // ==================== Private Helper Methods ====================

    /**
     * Builds the menu resource list based on user permissions.
     *
     * @param request the HTTP request for permission validation
     * @return filtered list of menu resources
     */
    private List<XxlBootResourceDTO> buildMenuResources(HttpServletRequest request) {
        Response<LoginInfo> loginInfoResponse = XxlSsoHelper.loginCheckWithAttr(request);

        List<XxlBootResourceDTO> allResources = createAllMenuResources();
        List<XxlBootResourceDTO> filteredResources =
                filterResourcesByPermission(allResources, loginInfoResponse.getData());

        return filteredResources.stream()
                .sorted(Comparator.comparing(XxlBootResourceDTO::getOrder))
                .collect(Collectors.toList());
    }

    /**
     * Creates the complete list of menu resources.
     *
     * @return list of all menu resources
     */
    private List<XxlBootResourceDTO> createAllMenuResources() {
        return Arrays.asList(
                new XxlBootResourceDTO(
                        1,
                        0,
                        I18nUtil.getString("job_dashboard_name"),
                        1,
                        "",
                        "/dashboard",
                        "fa-home",
                        1,
                        0,
                        null),
                new XxlBootResourceDTO(
                        2,
                        0,
                        I18nUtil.getString("jobinfo_name"),
                        1,
                        "",
                        "/jobinfo",
                        " fa-clock-o",
                        2,
                        0,
                        null),
                new XxlBootResourceDTO(
                        3,
                        0,
                        I18nUtil.getString("joblog_name"),
                        1,
                        "",
                        "/joblog",
                        " fa-database",
                        3,
                        0,
                        null),
                new XxlBootResourceDTO(
                        4,
                        0,
                        I18nUtil.getString("jobgroup_name"),
                        1,
                        Consts.ADMIN_ROLE,
                        "/jobgroup",
                        " fa-cloud",
                        4,
                        0,
                        null),
                new XxlBootResourceDTO(
                        5,
                        0,
                        I18nUtil.getString("user_manage"),
                        1,
                        Consts.ADMIN_ROLE,
                        "/user",
                        "fa-users",
                        5,
                        0,
                        null),
                new XxlBootResourceDTO(
                        9,
                        0,
                        I18nUtil.getString("admin_help"),
                        1,
                        "",
                        "/help",
                        "fa-book",
                        6,
                        0,
                        null));
    }

    /**
     * Filters menu resources based on user role permissions.
     *
     * @param resources the list of all resources
     * @param loginInfo the logged-in user info
     * @return filtered list of resources user has access to
     */
    private List<XxlBootResourceDTO> filterResourcesByPermission(
            List<XxlBootResourceDTO> resources, LoginInfo loginInfo) {

        boolean isAdmin = XxlSsoHelper.hasRole(loginInfo, Consts.ADMIN_ROLE).isSuccess();

        if (isAdmin) {
            return resources;
        }

        return resources.stream()
                .filter(resource -> StringTool.isBlank(resource.getPermission()))
                .collect(Collectors.toList());
    }
}
