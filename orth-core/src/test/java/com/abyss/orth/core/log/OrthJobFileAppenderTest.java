package com.abyss.orth.core.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.abyss.orth.core.openapi.model.LogResult;

/**
 * Tests for {@link OrthJobFileAppender}.
 *
 * <p>Covers: log path initialization, file name generation, append log, read log.
 */
class OrthJobFileAppenderTest {

    @TempDir Path tempDir;

    private String originalLogPath;

    @BeforeEach
    void setUp() throws IOException {
        // Save original log path
        originalLogPath = OrthJobFileAppender.getLogPath();
        // Initialize with temp directory
        OrthJobFileAppender.initLogPath(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        // Restore original log path
        if (originalLogPath != null) {
            OrthJobFileAppender.initLogPath(originalLogPath);
        }
    }

    // ==================== Init Log Path Tests ====================

    @Test
    void testInitLogPath_withValidPath_shouldSetPaths() throws IOException {
        // When
        OrthJobFileAppender.initLogPath(tempDir.toString());

        // Then
        assertThat(OrthJobFileAppender.getLogPath()).isEqualTo(tempDir.toString());
        assertThat(OrthJobFileAppender.getGlueSrcPath()).contains("gluesource");
    }

    @Test
    void testInitLogPath_shouldCreateDirectories() throws IOException {
        // Given
        Path newLogPath = tempDir.resolve("custom-log-path");

        // When
        OrthJobFileAppender.initLogPath(newLogPath.toString());

        // Then
        assertThat(newLogPath).exists();
        assertThat(newLogPath.resolve("gluesource")).exists();
    }

    @Test
    void testGetCallbackLogPath_shouldReturnPath() {
        // When
        String callbackPath = OrthJobFileAppender.getCallbackLogPath();

        // Then
        assertThat(callbackPath).isNotNull();
        assertThat(callbackPath).contains("callbacklogs");
    }

    // ==================== Make Log File Name Tests ====================

    @Test
    void testMakeLogFileName_shouldCreateDateDirectory() {
        // Given
        Date triggerDate = new Date();
        long logId = 123L;

        // When
        String logFileName = OrthJobFileAppender.makeLogFileName(triggerDate, logId);

        // Then
        assertThat(logFileName).contains(tempDir.toString());
        assertThat(logFileName).endsWith("123.log");
        assertThat(logFileName).matches(".*\\d{4}-\\d{2}-\\d{2}.*"); // Contains date
    }

    @Test
    void testMakeLogFileName_withDifferentLogIds_shouldCreateDifferentFiles() {
        // Given
        Date triggerDate = new Date();

        // When
        String logFileName1 = OrthJobFileAppender.makeLogFileName(triggerDate, 111L);
        String logFileName2 = OrthJobFileAppender.makeLogFileName(triggerDate, 222L);

        // Then
        assertThat(logFileName1).endsWith("111.log");
        assertThat(logFileName2).endsWith("222.log");
        assertThat(logFileName1).isNotEqualTo(logFileName2);
    }

    // ==================== Append Log Tests ====================

    @Test
    void testAppendLog_withValidInput_shouldWriteToFile() throws IOException {
        // Given
        Date triggerDate = new Date();
        String logFileName = OrthJobFileAppender.makeLogFileName(triggerDate, 456L);

        // When
        OrthJobFileAppender.appendLog(logFileName, "Test log message 1");
        OrthJobFileAppender.appendLog(logFileName, "Test log message 2");

        // Then
        String content = Files.readString(Path.of(logFileName));
        assertThat(content).contains("Test log message 1");
        assertThat(content).contains("Test log message 2");
    }

    @Test
    void testAppendLog_withNullFileName_shouldNotThrowException() {
        // When & Then - should complete without error
        OrthJobFileAppender.appendLog(null, "Test message");
    }

    @Test
    void testAppendLog_withBlankFileName_shouldNotThrowException() {
        // When & Then - should complete without error
        OrthJobFileAppender.appendLog("   ", "Test message");
    }

    @Test
    void testAppendLog_withNullContent_shouldNotThrowException() {
        // Given
        Date triggerDate = new Date();
        String logFileName = OrthJobFileAppender.makeLogFileName(triggerDate, 789L);

        // When & Then - should complete without error
        OrthJobFileAppender.appendLog(logFileName, null);
    }

    // ==================== Read Log Tests ====================

    @Test
    void testReadLog_withValidFile_shouldReturnContent() throws IOException {
        // Given
        Date triggerDate = new Date();
        String logFileName = OrthJobFileAppender.makeLogFileName(triggerDate, 999L);
        OrthJobFileAppender.appendLog(logFileName, "Line 1");
        OrthJobFileAppender.appendLog(logFileName, "Line 2");
        OrthJobFileAppender.appendLog(logFileName, "Line 3");

        // When
        LogResult result = OrthJobFileAppender.readLog(logFileName, 1);

        // Then
        assertThat(result.getFromLineNum()).isEqualTo(1);
        assertThat(result.getToLineNum()).isEqualTo(3);
        assertThat(result.getLogContent()).contains("Line 1");
        assertThat(result.getLogContent()).contains("Line 2");
        assertThat(result.getLogContent()).contains("Line 3");
        assertThat(result.isEnd()).isFalse();
    }

    @Test
    void testReadLog_fromMiddleLine_shouldSkipEarlierLines() throws IOException {
        // Given
        Date triggerDate = new Date();
        String logFileName = OrthJobFileAppender.makeLogFileName(triggerDate, 888L);
        OrthJobFileAppender.appendLog(logFileName, "Line 1");
        OrthJobFileAppender.appendLog(logFileName, "Line 2");
        OrthJobFileAppender.appendLog(logFileName, "Line 3");

        // When
        LogResult result = OrthJobFileAppender.readLog(logFileName, 2);

        // Then
        assertThat(result.getFromLineNum()).isEqualTo(2);
        assertThat(result.getToLineNum()).isEqualTo(3);
        assertThat(result.getLogContent()).doesNotContain("Line 1");
        assertThat(result.getLogContent()).contains("Line 2");
        assertThat(result.getLogContent()).contains("Line 3");
    }

    @Test
    void testReadLog_withNullFileName_shouldReturnError() {
        // When
        LogResult result = OrthJobFileAppender.readLog(null, 1);

        // Then
        assertThat(result.getFromLineNum()).isEqualTo(1);
        assertThat(result.getToLineNum()).isEqualTo(0);
        assertThat(result.getLogContent()).contains("logFile not found");
        assertThat(result.isEnd()).isTrue();
    }

    @Test
    void testReadLog_withBlankFileName_shouldReturnError() {
        // When
        LogResult result = OrthJobFileAppender.readLog("   ", 1);

        // Then
        assertThat(result.getFromLineNum()).isEqualTo(1);
        assertThat(result.getToLineNum()).isEqualTo(0);
        assertThat(result.getLogContent()).contains("logFile not found");
        assertThat(result.isEnd()).isTrue();
    }

    @Test
    void testReadLog_withNonExistentFile_shouldReturnError() {
        // Given
        String nonExistentFile = tempDir.resolve("non-existent.log").toString();

        // When
        LogResult result = OrthJobFileAppender.readLog(nonExistentFile, 1);

        // Then
        assertThat(result.getFromLineNum()).isEqualTo(1);
        assertThat(result.getToLineNum()).isEqualTo(0);
        assertThat(result.getLogContent()).contains("logFile not exists");
        assertThat(result.isEnd()).isTrue();
    }

    @Test
    void testReadLog_fromBeyondFileEnd_shouldReturnEmptyContent() throws IOException {
        // Given
        Date triggerDate = new Date();
        String logFileName = OrthJobFileAppender.makeLogFileName(triggerDate, 777L);
        OrthJobFileAppender.appendLog(logFileName, "Only line");

        // When - request from line 10 (beyond file end)
        LogResult result = OrthJobFileAppender.readLog(logFileName, 10);

        // Then
        assertThat(result.getFromLineNum()).isEqualTo(10);
        assertThat(result.getToLineNum()).isEqualTo(0);
        assertThat(result.getLogContent()).isEmpty();
        assertThat(result.isEnd()).isFalse();
    }
}
