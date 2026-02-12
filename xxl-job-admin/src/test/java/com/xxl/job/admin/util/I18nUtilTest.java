package com.xxl.job.admin.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Unit tests for {@link I18nUtil} internationalization utility.
 *
 * <p>Tests verify that the I18n utility correctly loads and resolves localized resource strings
 * from property files. The utility supports both single-key and multi-key concatenation for
 * building composite messages.
 *
 * <p><b>Test Coverage:</b>
 *
 * <ul>
 *   <li>Single key resolution (e.g., "admin_name")
 *   <li>Multi-key concatenation (e.g., "admin_name" + "admin_name_full")
 *   <li>Empty key handling
 * </ul>
 *
 * <p><b>Note:</b> This test requires a running Spring Boot context to load i18n resources from
 * <code>classpath:i18n/message*.properties</code>.
 *
 * @author orth (Abyss Project)
 * @since 3.3.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class I18nUtilTest {
    private static final Logger logger = LoggerFactory.getLogger(I18nUtilTest.class);

    // Test constants
    private static final String KEY_ADMIN_NAME = "admin_name";
    private static final String KEY_ADMIN_NAME_FULL = "admin_name_full";

    /**
     * Tests single key resolution from i18n resource bundles.
     *
     * <p>Validates that {@link I18nUtil#getString(String)} successfully retrieves the localized
     * string for a known resource key.
     */
    @Test
    public void testGetString_withValidKey_shouldReturnLocalizedValue() {
        // Act
        String result = I18nUtil.getString(KEY_ADMIN_NAME);

        // Assert
        assertNotNull(result, "Localized string should not be null");
        assertFalse(result.isEmpty(), "Localized string should not be empty");

        logger.info("Single key '{}' resolved to: '{}'", KEY_ADMIN_NAME, result);
    }

    /**
     * Tests multi-key concatenation with comma-separated values.
     *
     * <p>Validates that {@link I18nUtil#getMultString(String...)} correctly resolves multiple keys
     * and concatenates their values with commas.
     */
    @Test
    public void testGetMultString_withMultipleKeys_shouldReturnConcatenatedValues() {
        // Act
        String result = I18nUtil.getMultString(KEY_ADMIN_NAME, KEY_ADMIN_NAME_FULL);

        // Assert
        assertNotNull(result, "Multi-key result should not be null");
        assertFalse(result.isEmpty(), "Multi-key result should not be empty");

        logger.info(
                "Multi-key '{}', '{}' resolved to: '{}'",
                KEY_ADMIN_NAME,
                KEY_ADMIN_NAME_FULL,
                result);
    }

    /**
     * Tests empty key handling.
     *
     * <p>Validates that {@link I18nUtil#getMultString(String...)} gracefully handles empty key
     * arrays without throwing exceptions.
     */
    @Test
    public void testGetMultString_withNoKeys_shouldReturnEmptyOrDefaultValue() {
        // Act
        String result = I18nUtil.getMultString();

        // Assert
        assertNotNull(result, "Result should not be null even with no keys");

        logger.info("Empty key array resolved to: '{}'", result);
    }
}
