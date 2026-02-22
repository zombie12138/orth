package com.abyss.orth.admin.controller.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.abyss.orth.admin.mapper.JobInfoMapper;
import com.abyss.orth.admin.mapper.JobRegistryMapper;
import com.abyss.orth.admin.model.JobGroup;
import com.abyss.orth.admin.model.JobRegistry;
import com.abyss.orth.admin.util.I18nUtil;
import com.abyss.orth.admin.util.JobGroupPermissionUtil;
import com.abyss.orth.admin.web.security.JwtUserInfo;
import com.abyss.orth.admin.web.security.SecurityContext;
import com.abyss.orth.core.constant.Const;
import com.abyss.orth.core.constant.RegistType;
import com.xxl.tool.core.CollectionTool;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.http.HttpTool;
import com.xxl.tool.response.PageModel;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Executor group management REST API controller. Admin-only operations.
 *
 * @author xuxueli 2016-10-02 20:52:56
 */
@RestController
@RequestMapping("/api/v1/executor-groups")
public class ExecutorGroupController {

    private static final int MIN_APPNAME_LENGTH = 4;
    private static final int MAX_APPNAME_LENGTH = 64;
    private static final int ADDRESS_TYPE_AUTO = 0;
    private static final int ADDRESS_TYPE_MANUAL = 1;
    private static final int ADMIN_ROLE = 1;

    @Resource public JobInfoMapper jobInfoMapper;
    @Resource public JobGroupMapper jobGroupMapper;
    @Resource private JobRegistryMapper jobRegistryMapper;

    @GetMapping
    public Response<PageModel<JobGroup>> pageList(
            HttpServletRequest request,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "10") int pagesize,
            @RequestParam(required = false) String appname,
            @RequestParam(required = false) String title) {

        Response<String> adminCheck = requireAdmin(request);
        if (!adminCheck.isSuccess()) {
            return Response.ofFail(adminCheck.getMsg());
        }

        List<JobGroup> list = jobGroupMapper.pageList(offset, pagesize, appname, title);
        int totalCount = jobGroupMapper.pageListCount(offset, pagesize, appname, title);

        PageModel<JobGroup> pageModel = new PageModel<>();
        pageModel.setData(list);
        pageModel.setTotal(totalCount);

        return Response.ofSuccess(pageModel);
    }

    @PostMapping
    public Response<String> insert(HttpServletRequest request, @RequestBody JobGroup orthJobGroup) {
        Response<String> adminCheck = requireAdmin(request);
        if (!adminCheck.isSuccess()) {
            return adminCheck;
        }

        Response<String> validationResult = validateJobGroup(orthJobGroup);
        if (!validationResult.isSuccess()) {
            return validationResult;
        }

        if (orthJobGroup.getAddressType() == ADDRESS_TYPE_MANUAL) {
            Response<String> addressValidation = validateAddressList(orthJobGroup.getAddressList());
            if (!addressValidation.isSuccess()) {
                return addressValidation;
            }
        }

        orthJobGroup.setUpdateTime(new Date());
        int result = jobGroupMapper.save(orthJobGroup);
        return result > 0 ? Response.ofSuccess() : Response.ofFail();
    }

    @PutMapping("/{id}")
    public Response<String> update(
            HttpServletRequest request, @PathVariable int id, @RequestBody JobGroup orthJobGroup) {
        Response<String> adminCheck = requireAdmin(request);
        if (!adminCheck.isSuccess()) {
            return adminCheck;
        }

        orthJobGroup.setId(id);
        Response<String> validationResult = validateBasicFields(orthJobGroup);
        if (!validationResult.isSuccess()) {
            return validationResult;
        }

        if (orthJobGroup.getAddressType() == ADDRESS_TYPE_AUTO) {
            updateAutoRegisteredAddresses(orthJobGroup);
        } else {
            Response<String> addressValidation = validateAddressList(orthJobGroup.getAddressList());
            if (!addressValidation.isSuccess()) {
                return addressValidation;
            }
        }

        orthJobGroup.setUpdateTime(new Date());
        int result = jobGroupMapper.update(orthJobGroup);
        return result > 0 ? Response.ofSuccess() : Response.ofFail();
    }

    @DeleteMapping("/{id}")
    public Response<String> delete(HttpServletRequest request, @PathVariable int id) {
        Response<String> adminCheck = requireAdmin(request);
        if (!adminCheck.isSuccess()) {
            return adminCheck;
        }

        int jobCount = jobInfoMapper.pageListCount(0, 10, id, -1, null, null, null, 0);
        if (jobCount > 0) {
            return Response.ofFail(I18nUtil.getString("jobgroup_del_limit_0"));
        }

        List<JobGroup> allGroups = jobGroupMapper.findAll();
        if (allGroups.size() == 1) {
            return Response.ofFail(I18nUtil.getString("jobgroup_del_limit_1"));
        }

        int result = jobGroupMapper.remove(id);
        return result > 0 ? Response.ofSuccess() : Response.ofFail();
    }

    @GetMapping("/{id}")
    public Response<JobGroup> loadById(HttpServletRequest request, @PathVariable int id) {
        Response<String> adminCheck = requireAdmin(request);
        if (!adminCheck.isSuccess()) {
            return Response.ofFail(adminCheck.getMsg());
        }

        JobGroup jobGroup = jobGroupMapper.load(id);
        return jobGroup != null ? Response.ofSuccess(jobGroup) : Response.ofFail();
    }

    @GetMapping("/permitted")
    public Response<List<JobGroup>> permittedGroups(HttpServletRequest request) {
        List<JobGroup> allGroups = jobGroupMapper.findAll();
        List<JobGroup> filtered =
                JobGroupPermissionUtil.filterJobGroupByPermission(request, allGroups);
        return Response.ofSuccess(filtered);
    }

    // ==================== Private Helper Methods ====================

    private Response<String> requireAdmin(HttpServletRequest request) {
        JwtUserInfo userInfo = SecurityContext.getCurrentUser(request);
        if (userInfo == null || userInfo.getRole() != ADMIN_ROLE) {
            return Response.ofFail(I18nUtil.getString("system_permission_limit"));
        }
        return Response.ofSuccess();
    }

    private Response<String> validateJobGroup(JobGroup orthJobGroup) {
        Response<String> basicValidation = validateBasicFields(orthJobGroup);
        if (!basicValidation.isSuccess()) {
            return basicValidation;
        }

        if (containsInvalidCharacters(orthJobGroup.getTitle())) {
            return Response.ofFail(
                    I18nUtil.getString("jobgroup_field_title")
                            + I18nUtil.getString("system_unvalid"));
        }

        return Response.ofSuccess();
    }

    private Response<String> validateBasicFields(JobGroup orthJobGroup) {
        if (StringTool.isBlank(orthJobGroup.getAppname())) {
            return Response.ofFail(I18nUtil.getString("system_please_input") + "AppName");
        }

        int appnameLength = orthJobGroup.getAppname().length();
        if (appnameLength < MIN_APPNAME_LENGTH || appnameLength > MAX_APPNAME_LENGTH) {
            return Response.ofFail(I18nUtil.getString("jobgroup_field_appname_length"));
        }

        if (containsInvalidCharacters(orthJobGroup.getAppname())) {
            return Response.ofFail("AppName" + I18nUtil.getString("system_unvalid"));
        }

        if (StringTool.isBlank(orthJobGroup.getTitle())) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("jobgroup_field_title"));
        }

        return Response.ofSuccess();
    }

    private Response<String> validateAddressList(String addressList) {
        if (StringTool.isBlank(addressList)) {
            return Response.ofFail(I18nUtil.getString("jobgroup_field_addressType_limit"));
        }

        if (containsInvalidCharacters(addressList)) {
            return Response.ofFail(
                    I18nUtil.getString("jobgroup_field_registryList")
                            + I18nUtil.getString("system_unvalid"));
        }

        String[] addresses = addressList.split(",");
        for (String address : addresses) {
            if (StringTool.isBlank(address)) {
                return Response.ofFail(I18nUtil.getString("jobgroup_field_registryList_unvalid"));
            }

            if (!isValidHttpUrl(address)) {
                return Response.ofFail(
                        I18nUtil.getString("jobgroup_field_registryList_unvalid") + "[2]");
            }
        }

        return Response.ofSuccess();
    }

    private void updateAutoRegisteredAddresses(JobGroup orthJobGroup) {
        List<String> registryList = findRegistryByAppName(orthJobGroup.getAppname());
        String addressListStr = null;

        if (CollectionTool.isNotEmpty(registryList)) {
            Collections.sort(registryList);
            addressListStr = String.join(",", registryList);
        }

        orthJobGroup.setAddressList(addressListStr);
    }

    private List<String> findRegistryByAppName(String appnameParam) {
        Map<String, List<String>> appAddressMap = new HashMap<>();
        List<JobRegistry> registries = jobRegistryMapper.findAll(Const.DEAD_TIMEOUT, new Date());

        if (CollectionTool.isEmpty(registries)) {
            return null;
        }

        registries.stream()
                .filter(registry -> RegistType.EXECUTOR.name().equals(registry.getRegistryGroup()))
                .forEach(
                        registry -> {
                            String appname = registry.getRegistryKey();
                            appAddressMap
                                    .computeIfAbsent(appname, k -> new ArrayList<>())
                                    .add(registry.getRegistryValue());
                        });

        return appAddressMap.get(appnameParam);
    }

    private boolean containsInvalidCharacters(String value) {
        return value != null && (value.contains(">") || value.contains("<"));
    }

    private boolean isValidHttpUrl(String url) {
        return HttpTool.isHttp(url) || HttpTool.isHttps(url);
    }
}
