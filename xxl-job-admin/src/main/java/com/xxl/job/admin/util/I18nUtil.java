package com.xxl.job.admin.util;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.xxl.tool.core.PropTool;
import com.xxl.tool.freemarker.FtlTool;
import com.xxl.tool.gson.GsonTool;

import freemarker.template.Configuration;

/**
 * Internationalization (i18n) utility for Orth admin.
 *
 * <p>Provides centralized access to localized message resources and handles initialization of
 * internationalized enum values. Supports multiple locales including Chinese (Simplified and
 * Traditional) and English.
 *
 * <p>This utility is Spring-managed and initializes on application startup, loading the appropriate
 * message properties file and configuring Freemarker integration.
 *
 * @author xuxueli 2018-01-17 20:39:06
 */
@Component
public class I18nUtil implements InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(I18nUtil.class);
    private static final List<String> SUPPORTED_LOCALES = List.of("zh_CN", "zh_TC", "en");
    private static final String DEFAULT_LOCALE = "zh_CN";

    @Value("${xxl.job.i18n}")
    private String i18n;

    @Autowired private Configuration configuration;

    private static I18nUtil single = null;
    private static Properties prop = null;

    @Override
    public void afterPropertiesSet() throws Exception {
        // Initialize Freemarker shared variable
        configuration.setSharedVariable(
                "I18nUtil", FtlTool.generateStaticModel(I18nUtil.class.getName()));

        // Set singleton instance
        single = this;

        // Initialize internationalized enum values
        initI18nEnum();
    }

    /**
     * Gets the configured locale, defaulting to zh_CN if invalid.
     *
     * @return locale code (e.g., "zh_CN", "zh_TC", "en")
     */
    public String getI18n() {
        return SUPPORTED_LOCALES.contains(i18n) ? i18n : DEFAULT_LOCALE;
    }

    private static I18nUtil getSingle() {
        return single;
    }

    /**
     * Loads i18n properties file for the configured locale.
     *
     * <p>Properties are cached after first load for performance. The appropriate properties file is
     * loaded based on the configured locale.
     *
     * @return loaded properties
     */
    public static Properties loadI18nProp() {
        if (prop != null) {
            return prop;
        }

        String locale = getSingle().getI18n();
        String i18nFile = MessageFormat.format("i18n/message_{0}.properties", locale);
        prop = PropTool.loadProp(i18nFile);
        return prop;
    }

    /**
     * Gets the localized message for the specified key.
     *
     * @param key message key
     * @return localized message, or null if key not found
     */
    public static String getString(String key) {
        return loadI18nProp().getProperty(key);
    }

    /**
     * Gets multiple localized messages as JSON.
     *
     * <p>If no keys are specified, returns all messages. Otherwise, returns only the messages for
     * the specified keys.
     *
     * @param keys message keys (optional - if empty, returns all messages)
     * @return JSON object containing key-value pairs of messages
     */
    public static String getMultString(String... keys) {
        Map<String, String> map = new HashMap<>();
        Properties properties = loadI18nProp();

        if (keys != null && keys.length > 0) {
            for (String key : keys) {
                map.put(key, properties.getProperty(key));
            }
        } else {
            for (String key : properties.stringPropertyNames()) {
                map.put(key, properties.getProperty(key));
            }
        }

        return GsonTool.toJson(map);
    }

    /**
     * Initializes internationalized titles for enums.
     *
     * <p>Sets localized display titles for enum constants that support i18n, such as executor block
     * strategy types.
     */
    private void initI18nEnum() {
        // ExecutorBlockStrategyEnum titles are now immutable (set in enum constructor)
        // No need to update titles at runtime
    }
}
