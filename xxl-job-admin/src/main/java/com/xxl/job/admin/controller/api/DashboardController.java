package com.xxl.job.admin.controller.api;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xxl.job.admin.constant.Consts;
import com.xxl.job.admin.model.dto.XxlBootResourceDTO;
import com.xxl.job.admin.service.XxlJobService;
import com.xxl.job.admin.util.I18nUtil;
import com.xxl.job.admin.web.security.JwtUserInfo;
import com.xxl.job.admin.web.security.SecurityContext;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Dashboard and navigation controller.
 *
 * @author xuxueli 2015-12-19 16:13:16
 */
@RestController
@RequestMapping("/api/v1")
public class DashboardController {

    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Resource private XxlJobService xxlJobService;

    @GetMapping("/dashboard")
    public Response<Map<String, Object>> dashboard() {
        Map<String, Object> dashboardData = xxlJobService.dashboardInfo();
        return Response.ofSuccess(dashboardData);
    }

    @GetMapping("/dashboard/chart")
    public Response<Map<String, Object>> chartInfo(
            @RequestParam("startDate") Date startDate, @RequestParam("endDate") Date endDate) {
        return xxlJobService.chartInfo(startDate, endDate);
    }

    @GetMapping("/menus")
    public Response<List<XxlBootResourceDTO>> menus(HttpServletRequest request) {
        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);
        List<XxlBootResourceDTO> menuResources = buildMenuResources(userInfo);
        return Response.ofSuccess(menuResources);
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_PATTERN);
        dateFormat.setLenient(false);
        binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true));
    }

    private List<XxlBootResourceDTO> buildMenuResources(JwtUserInfo userInfo) {
        List<XxlBootResourceDTO> allResources = createAllMenuResources();
        List<XxlBootResourceDTO> filteredResources =
                filterResourcesByPermission(allResources, userInfo);

        return filteredResources.stream()
                .sorted(Comparator.comparing(XxlBootResourceDTO::getOrder))
                .collect(Collectors.toList());
    }

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

    private List<XxlBootResourceDTO> filterResourcesByPermission(
            List<XxlBootResourceDTO> resources, JwtUserInfo userInfo) {
        boolean isAdmin = SecurityContext.isAdmin(userInfo);

        if (isAdmin) {
            return resources;
        }

        return resources.stream()
                .filter(resource -> StringTool.isBlank(resource.getPermission()))
                .collect(Collectors.toList());
    }
}
