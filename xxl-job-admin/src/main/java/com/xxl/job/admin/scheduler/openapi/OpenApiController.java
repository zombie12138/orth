package com.xxl.job.admin.scheduler.openapi;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xxl.job.admin.scheduler.config.XxlJobAdminBootstrap;
import com.xxl.job.core.constant.Const;
import com.xxl.job.core.openapi.AdminBiz;
import com.xxl.job.core.openapi.model.CallbackRequest;
import com.xxl.job.core.openapi.model.RegistryRequest;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.gson.GsonTool;
import com.xxl.tool.response.Response;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/** Created by xuxueli on 17/5/10. */
@RestController
public class OpenApiController {

    @Resource private AdminBiz adminBiz;

    /** api */
    @RequestMapping("/api/{uri}")
    public Object api(
            HttpServletRequest request,
            @PathVariable("uri") String uri,
            @RequestHeader(Const.ORTH_ACCESS_TOKEN) String accesstoken,
            @RequestBody(required = false) String requestBody) {

        // valid
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return Response.ofFail("invalid request, HttpMethod not support.");
        }
        if (StringTool.isBlank(uri)) {
            return Response.ofFail("invalid request, uri-mapping empty.");
        }
        if (StringTool.isBlank(requestBody)) {
            return Response.ofFail("invalid request, requestBody empty.");
        }

        // valid token
        if (StringTool.isNotBlank(XxlJobAdminBootstrap.getInstance().getAccessToken())
                && !XxlJobAdminBootstrap.getInstance().getAccessToken().equals(accesstoken)) {
            return Response.ofFail("The access token is wrong.");
        }

        // dispatch request
        try {
            switch (uri) {
                case "callback":
                    {
                        List<CallbackRequest> callbackParamList =
                                GsonTool.fromJson(requestBody, List.class, CallbackRequest.class);
                        return adminBiz.callback(callbackParamList);
                    }
                case "registry":
                    {
                        RegistryRequest registryParam =
                                GsonTool.fromJson(requestBody, RegistryRequest.class);
                        return adminBiz.registry(registryParam);
                    }
                case "registryRemove":
                    {
                        RegistryRequest registryParam =
                                GsonTool.fromJson(requestBody, RegistryRequest.class);
                        return adminBiz.registryRemove(registryParam);
                    }
                default:
                    return Response.ofFail("invalid request, uri-mapping(" + uri + ") not found.");
            }
        } catch (Exception e) {
            return Response.ofFail("openapi invoke error: " + e.getMessage());
        }
    }
}
