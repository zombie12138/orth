package com.abyss.orth.core.handler.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.abyss.orth.core.context.OrthJobContext;
import com.abyss.orth.core.glue.GlueTypeEnum;
import com.abyss.orth.core.log.OrthJobFileAppender;

/**
 * Tests for {@link ScriptJobHandler}.
 *
 * <p>Covers: constructor, getter, validation, environment variables.
 */
class ScriptJobHandlerTest {

    @TempDir Path tempDir;

    private String originalLogPath;

    @BeforeEach
    void setUp() throws IOException {
        // Save and set log path
        originalLogPath = OrthJobFileAppender.getLogPath();
        OrthJobFileAppender.initLogPath(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clear context
        OrthJobContext.setOrthJobContext(null);

        // Restore log path
        if (originalLogPath != null) {
            OrthJobFileAppender.initLogPath(originalLogPath);
        }
    }

    // ==================== Constructor Tests ====================

    @Test
    void testConstructor_shouldInitializeFields() {
        // When
        ScriptJobHandler handler =
                new ScriptJobHandler(123, 1234567890L, "echo 'test'", GlueTypeEnum.GLUE_SHELL);

        // Then
        assertThat(handler.getGlueUpdatetime()).isEqualTo(1234567890L);
    }

    @Test
    void testConstructor_shouldCleanOldScriptFiles() throws IOException {
        // Given - create old script files
        Path glueSourceDir = tempDir.resolve("gluesource");
        Files.createDirectories(glueSourceDir);

        Path oldScript1 = glueSourceDir.resolve("123_1000000000.sh");
        Path oldScript2 = glueSourceDir.resolve("123_2000000000.sh");
        Path otherScript = glueSourceDir.resolve("456_3000000000.sh");

        Files.writeString(oldScript1, "old script 1");
        Files.writeString(oldScript2, "old script 2");
        Files.writeString(otherScript, "other job script");

        // When - create handler for job 123
        new ScriptJobHandler(123, 1234567890L, "echo 'test'", GlueTypeEnum.GLUE_SHELL);

        // Then - old scripts for job 123 should be deleted, other job scripts preserved
        assertThat(oldScript1).doesNotExist();
        assertThat(oldScript2).doesNotExist();
        assertThat(otherScript).exists();
    }

    // ==================== Execute Tests ====================

    @Test
    void testExecute_withNonScriptGlueType_shouldFail() throws Exception {
        // Given - BEAN type is not a script
        Path logDir = tempDir.resolve("2024-01-15");
        Files.createDirectories(logDir);
        Path logFile = logDir.resolve("123.log");
        String logFileName = logFile.toString();

        OrthJobContext context =
                new OrthJobContext(
                        123, "param", 789L, System.currentTimeMillis(), logFileName, 0, 1, null);
        OrthJobContext.setOrthJobContext(context);

        ScriptJobHandler handler =
                new ScriptJobHandler(123, 1234567890L, "not a script", GlueTypeEnum.BEAN);

        // When
        handler.execute();

        // Then - should set fail status
        assertThat(context.getHandleCode()).isEqualTo(OrthJobContext.HANDLE_CODE_FAIL);
        assertThat(context.getHandleMsg()).contains("glueType");
        assertThat(context.getHandleMsg()).contains("invalid");
    }

    @Test
    void testExecute_withGroovyGlueType_shouldFail() throws Exception {
        // Given - GLUE_GROOVY is not a script type
        Path logDir = tempDir.resolve("2024-01-15");
        Files.createDirectories(logDir);
        Path logFile = logDir.resolve("124.log");
        String logFileName = logFile.toString();

        OrthJobContext context =
                new OrthJobContext(
                        124, null, 790L, System.currentTimeMillis(), logFileName, 0, 1, null);
        OrthJobContext.setOrthJobContext(context);

        ScriptJobHandler handler =
                new ScriptJobHandler(124, 1234567890L, "not a script", GlueTypeEnum.GLUE_GROOVY);

        // When
        handler.execute();

        // Then - should set fail status
        assertThat(context.getHandleCode()).isEqualTo(OrthJobContext.HANDLE_CODE_FAIL);
        assertThat(context.getHandleMsg()).contains("invalid");
    }

    // ==================== Getter Tests ====================

    @Test
    void testGetGlueUpdatetime_shouldReturnUpdatetime() {
        // Given
        long updatetime = 1234567890L;
        ScriptJobHandler handler =
                new ScriptJobHandler(123, updatetime, "echo 'test'", GlueTypeEnum.GLUE_SHELL);

        // When
        long result = handler.getGlueUpdatetime();

        // Then
        assertThat(result).isEqualTo(updatetime);
    }
}
