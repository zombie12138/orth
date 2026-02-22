package com.abyss.orth.admin.controller.api;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abyss.orth.admin.scheduler.misfire.MisfireStrategyEnum;
import com.abyss.orth.admin.scheduler.route.ExecutorRouteStrategyEnum;
import com.abyss.orth.admin.scheduler.type.ScheduleTypeEnum;
import com.abyss.orth.admin.util.I18nUtil;
import com.abyss.orth.core.constant.ExecutorBlockStrategyEnum;
import com.abyss.orth.core.glue.GlueTypeEnum;
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
        enumData.put("ExecutorRouteStrategyEnum", toMap(ExecutorRouteStrategyEnum.values()));
        enumData.put("GlueTypeEnum", toMap(GlueTypeEnum.values()));
        enumData.put("ExecutorBlockStrategyEnum", toMap(ExecutorBlockStrategyEnum.values()));
        enumData.put("ScheduleTypeEnum", toMap(ScheduleTypeEnum.values()));
        enumData.put("MisfireStrategyEnum", toMap(MisfireStrategyEnum.values()));
        return Response.ofSuccess(enumData);
    }

    /** Converts enum values to a {name: title} map for frontend consumption. */
    private Map<String, String> toMap(ExecutorRouteStrategyEnum[] values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (ExecutorRouteStrategyEnum e : values) {
            map.put(e.name(), e.getTitle());
        }
        return map;
    }

    private Map<String, String> toMap(GlueTypeEnum[] values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (GlueTypeEnum e : values) {
            map.put(e.name(), e.getDesc());
        }
        return map;
    }

    private Map<String, String> toMap(ExecutorBlockStrategyEnum[] values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (ExecutorBlockStrategyEnum e : values) {
            map.put(e.name(), e.getTitle());
        }
        return map;
    }

    private Map<String, String> toMap(ScheduleTypeEnum[] values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (ScheduleTypeEnum e : values) {
            map.put(e.name(), e.getTitle());
        }
        return map;
    }

    private Map<String, String> toMap(MisfireStrategyEnum[] values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (MisfireStrategyEnum e : values) {
            map.put(e.name(), e.getTitle());
        }
        return map;
    }

    @GetMapping("/i18n")
    public Response<Map<String, String>> i18n() {
        Map<String, String> i18nData = I18nUtil.getAllStrings();
        return Response.ofSuccess(i18nData);
    }
}
