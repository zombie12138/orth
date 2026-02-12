package com.xxl.job.admin.controller.biz;

import java.util.*;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.xxl.job.admin.constant.Consts;
import com.xxl.job.admin.mapper.XxlJobGroupMapper;
import com.xxl.job.admin.mapper.XxlJobInfoMapper;
import com.xxl.job.admin.mapper.XxlJobRegistryMapper;
import com.xxl.job.admin.model.XxlJobGroup;
import com.xxl.job.admin.model.XxlJobRegistry;
import com.xxl.job.admin.util.I18nUtil;
import com.xxl.job.core.constant.Const;
import com.xxl.job.core.constant.RegistType;
import com.xxl.sso.core.annotation.XxlSso;
import com.xxl.tool.core.CollectionTool;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.http.HttpTool;
import com.xxl.tool.response.PageModel;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;

/**
 * Job group controller for managing executor groups.
 *
 * <p>Handles operations for executor group management including:
 *
 * <ul>
 *   <li>Listing and paginating job groups
 *   <li>Creating and updating executor groups
 *   <li>Managing manual/auto address registration
 *   <li>Deleting job groups with validation
 * </ul>
 *
 * @author xuxueli 2016-10-02 20:52:56
 */
@Controller
@RequestMapping("/jobgroup")
public class JobGroupController {

    private static final int MIN_APPNAME_LENGTH = 4;
    private static final int MAX_APPNAME_LENGTH = 64;
    private static final int ADDRESS_TYPE_AUTO = 0;
    private static final int ADDRESS_TYPE_MANUAL = 1;

    @Resource public XxlJobInfoMapper xxlJobInfoMapper;
    @Resource public XxlJobGroupMapper xxlJobGroupMapper;
    @Resource private XxlJobRegistryMapper xxlJobRegistryMapper;

    /**
     * Displays the job group list page.
     *
     * @param model the model for view rendering
     * @return the view name for group list page
     */
    @RequestMapping
    @XxlSso(role = Consts.ADMIN_ROLE)
    public String index(Model model) {
        return "biz/group.list";
    }

    /**
     * Retrieves a paginated list of job groups.
     *
     * @param offset the starting offset for pagination
     * @param pagesize the page size
     * @param appname optional filter by application name
     * @param title optional filter by group title
     * @return paginated response containing job groups
     */
    @RequestMapping("/pageList")
    @ResponseBody
    @XxlSso(role = Consts.ADMIN_ROLE)
    public Response<PageModel<XxlJobGroup>> pageList(
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "10") int pagesize,
            String appname,
            String title) {

        List<XxlJobGroup> list = xxlJobGroupMapper.pageList(offset, pagesize, appname, title);
        int totalCount = xxlJobGroupMapper.pageListCount(offset, pagesize, appname, title);

        PageModel<XxlJobGroup> pageModel = new PageModel<>();
        pageModel.setData(list);
        pageModel.setTotal(totalCount);

        return Response.ofSuccess(pageModel);
    }

    /**
     * Creates a new job group.
     *
     * @param xxlJobGroup the job group to create
     * @return success or failure response
     */
    @RequestMapping("/insert")
    @ResponseBody
    @XxlSso(role = Consts.ADMIN_ROLE)
    public Response<String> insert(XxlJobGroup xxlJobGroup) {
        Response<String> validationResult = validateJobGroup(xxlJobGroup);
        if (!validationResult.isSuccess()) {
            return validationResult;
        }

        if (xxlJobGroup.getAddressType() == ADDRESS_TYPE_MANUAL) {
            Response<String> addressValidation = validateAddressList(xxlJobGroup.getAddressList());
            if (!addressValidation.isSuccess()) {
                return addressValidation;
            }
        }

        xxlJobGroup.setUpdateTime(new Date());
        int result = xxlJobGroupMapper.save(xxlJobGroup);
        return result > 0 ? Response.ofSuccess() : Response.ofFail();
    }

    /**
     * Updates an existing job group.
     *
     * @param xxlJobGroup the job group to update
     * @return success or failure response
     */
    @RequestMapping("/update")
    @ResponseBody
    @XxlSso(role = Consts.ADMIN_ROLE)
    public Response<String> update(XxlJobGroup xxlJobGroup) {
        Response<String> validationResult = validateBasicFields(xxlJobGroup);
        if (!validationResult.isSuccess()) {
            return validationResult;
        }

        if (xxlJobGroup.getAddressType() == ADDRESS_TYPE_AUTO) {
            updateAutoRegisteredAddresses(xxlJobGroup);
        } else {
            Response<String> addressValidation = validateAddressList(xxlJobGroup.getAddressList());
            if (!addressValidation.isSuccess()) {
                return addressValidation;
            }
        }

        xxlJobGroup.setUpdateTime(new Date());
        int result = xxlJobGroupMapper.update(xxlJobGroup);
        return result > 0 ? Response.ofSuccess() : Response.ofFail();
    }

    /**
     * Deletes a job group by ID.
     *
     * @param ids the list of IDs (only one allowed)
     * @return success or failure response
     */
    @RequestMapping("/delete")
    @ResponseBody
    @XxlSso(role = Consts.ADMIN_ROLE)
    public Response<String> delete(@RequestParam("ids[]") List<Integer> ids) {
        if (CollectionTool.isEmpty(ids) || ids.size() != 1) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_choose")
                            + I18nUtil.getString("system_one")
                            + I18nUtil.getString("system_data"));
        }

        int id = ids.get(0);

        int jobCount = xxlJobInfoMapper.pageListCount(0, 10, id, -1, null, null, null, null);
        if (jobCount > 0) {
            return Response.ofFail(I18nUtil.getString("jobgroup_del_limit_0"));
        }

        List<XxlJobGroup> allGroups = xxlJobGroupMapper.findAll();
        if (allGroups.size() == 1) {
            return Response.ofFail(I18nUtil.getString("jobgroup_del_limit_1"));
        }

        int result = xxlJobGroupMapper.remove(id);
        return result > 0 ? Response.ofSuccess() : Response.ofFail();
    }

    /**
     * Loads a job group by ID.
     *
     * @param id the job group ID
     * @return the job group if found, failure otherwise
     */
    @RequestMapping("/loadById")
    @ResponseBody
    @XxlSso(role = Consts.ADMIN_ROLE)
    public Response<XxlJobGroup> loadById(@RequestParam("id") int id) {
        XxlJobGroup jobGroup = xxlJobGroupMapper.load(id);
        return jobGroup != null ? Response.ofSuccess(jobGroup) : Response.ofFail();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Validates the job group basic fields and address information.
     *
     * @param xxlJobGroup the job group to validate
     * @return success if valid, failure with error message otherwise
     */
    private Response<String> validateJobGroup(XxlJobGroup xxlJobGroup) {
        Response<String> basicValidation = validateBasicFields(xxlJobGroup);
        if (!basicValidation.isSuccess()) {
            return basicValidation;
        }

        if (containsInvalidCharacters(xxlJobGroup.getTitle())) {
            return Response.ofFail(
                    I18nUtil.getString("jobgroup_field_title")
                            + I18nUtil.getString("system_unvalid"));
        }

        return Response.ofSuccess();
    }

    /**
     * Validates basic fields: appname and title.
     *
     * @param xxlJobGroup the job group to validate
     * @return success if valid, failure with error message otherwise
     */
    private Response<String> validateBasicFields(XxlJobGroup xxlJobGroup) {
        if (StringTool.isBlank(xxlJobGroup.getAppname())) {
            return Response.ofFail(I18nUtil.getString("system_please_input") + "AppName");
        }

        int appnameLength = xxlJobGroup.getAppname().length();
        if (appnameLength < MIN_APPNAME_LENGTH || appnameLength > MAX_APPNAME_LENGTH) {
            return Response.ofFail(I18nUtil.getString("jobgroup_field_appname_length"));
        }

        if (containsInvalidCharacters(xxlJobGroup.getAppname())) {
            return Response.ofFail("AppName" + I18nUtil.getString("system_unvalid"));
        }

        if (StringTool.isBlank(xxlJobGroup.getTitle())) {
            return Response.ofFail(
                    I18nUtil.getString("system_please_input")
                            + I18nUtil.getString("jobgroup_field_title"));
        }

        return Response.ofSuccess();
    }

    /**
     * Validates the address list for manual registration type.
     *
     * @param addressList comma-separated list of executor addresses
     * @return success if valid, failure with error message otherwise
     */
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

    /**
     * Updates the job group with auto-registered executor addresses.
     *
     * @param xxlJobGroup the job group to update
     */
    private void updateAutoRegisteredAddresses(XxlJobGroup xxlJobGroup) {
        List<String> registryList = findRegistryByAppName(xxlJobGroup.getAppname());
        String addressListStr = null;

        if (CollectionTool.isNotEmpty(registryList)) {
            Collections.sort(registryList);
            addressListStr = String.join(",", registryList);
        }

        xxlJobGroup.setAddressList(addressListStr);
    }

    /**
     * Finds registered executor addresses by application name.
     *
     * @param appnameParam the application name to search for
     * @return list of registered addresses, or null if none found
     */
    private List<String> findRegistryByAppName(String appnameParam) {
        Map<String, List<String>> appAddressMap = new HashMap<>();
        List<XxlJobRegistry> registries =
                xxlJobRegistryMapper.findAll(Const.DEAD_TIMEOUT, new Date());

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

    /**
     * Checks if a string contains invalid characters (HTML tags).
     *
     * @param value the string to check
     * @return true if contains invalid characters, false otherwise
     */
    private boolean containsInvalidCharacters(String value) {
        return value != null && (value.contains(">") || value.contains("<"));
    }

    /**
     * Validates if a string is a valid HTTP or HTTPS URL.
     *
     * @param url the URL to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidHttpUrl(String url) {
        return HttpTool.isHttp(url) || HttpTool.isHttps(url);
    }
}
