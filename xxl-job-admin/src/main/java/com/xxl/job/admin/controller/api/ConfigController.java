package com.xxl.job.admin.controller.api;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xxl.job.admin.scheduler.misfire.MisfireStrategyEnum;
import com.xxl.job.admin.scheduler.route.ExecutorRouteStrategyEnum;
import com.xxl.job.admin.scheduler.type.ScheduleTypeEnum;
import com.xxl.job.admin.util.I18nUtil;
import com.xxl.job.core.constant.ExecutorBlockStrategyEnum;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.tool.response.Response;

/**
 * Configuration data REST API controller.
 *
 * <p>Provides enum values and i18n strings that the frontend needs for rendering forms and
 * dropdowns. Replaces the model attributes that were previously passed via Freemarker templates.
 */
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    @GetMapping("/enums")
    public Response<Map<String, Object>> enums() {
        Map<String, Object> enumData = new HashMap<>();
        enumData.put("ExecutorRouteStrategyEnum", ExecutorRouteStrategyEnum.values());
        enumData.put("GlueTypeEnum", GlueTypeEnum.values());
        enumData.put("ExecutorBlockStrategyEnum", ExecutorBlockStrategyEnum.values());
        enumData.put("ScheduleTypeEnum", ScheduleTypeEnum.values());
        enumData.put("MisfireStrategyEnum", MisfireStrategyEnum.values());
        return Response.ofSuccess(enumData);
    }

    @GetMapping("/i18n")
    public Response<Map<String, String>> i18n() {
        Map<String, String> i18nData = I18nUtil.getAllStrings();
        return Response.ofSuccess(i18nData);
    }
}
