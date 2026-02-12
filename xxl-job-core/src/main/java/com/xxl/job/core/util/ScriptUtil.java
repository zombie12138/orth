package com.xxl.job.core.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.tool.core.ArrayTool;
import com.xxl.tool.core.MapTool;
import com.xxl.tool.io.FileTool;
import com.xxl.tool.io.IOTool;

/**
 * Utility for executing external scripts (Shell, Python, NodeJS, PHP, PowerShell).
 *
 * <p>Implementation notes:
 *
 * <ul>
 *   <li>Uses {@link ProcessBuilder} to execute scripts as OS processes, avoiding embedded
 *       interpreter limitations
 *   <li>Requires target machine to have script interpreters (bash, python3, node, etc.) in PATH
 *   <li>Streams script output (stdout/stderr) to log file in real-time
 *   <li>Python scripts should use logging module to maintain consistent output order with
 *       exceptions
 * </ul>
 */
public class ScriptUtil {

    private ScriptUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates a script file with the specified content.
     *
     * @param scriptFileName script file path (e.g., "/path/to/gluesource/666-123456789.py")
     * @param scriptContent script source code
     * @throws IOException if file creation fails
     */
    public static void markScriptFile(String scriptFileName, String scriptContent)
            throws IOException {
        FileTool.writeString(scriptFileName, scriptContent);
    }

    /**
     * Executes a script with real-time log output (backward compatible version).
     *
     * @param command interpreter command (e.g., "python3", "bash", "node")
     * @param scriptFile script file path
     * @param logFile log file path for stdout/stderr output
     * @param params script parameters (passed as command-line arguments)
     * @return exit code (0=success, non-zero=error, -1=exception)
     * @throws IOException if I/O error occurs
     */
    public static int execToFile(
            String command, String scriptFile, String logFile, String... params)
            throws IOException {
        return execToFile(command, scriptFile, logFile, null, params);
    }

    /**
     * Executes a script with environment variables and real-time log output.
     *
     * @param command interpreter command (e.g., "python3", "bash", "node")
     * @param scriptFile script file path
     * @param logFile log file path for stdout/stderr output
     * @param envVars environment variables to set (can be null)
     * @param params script parameters (passed as command-line arguments)
     * @return exit code (0=success, non-zero=error, -1=exception)
     * @throws IOException if I/O error occurs
     */
    public static int execToFile(
            String command,
            String scriptFile,
            String logFile,
            Map<String, String> envVars,
            String... params)
            throws IOException {

        FileOutputStream fileOutputStream = null;
        Thread inputThread = null;
        Thread errorThread = null;
        Process process = null;

        try {
            // Open log file in append mode
            fileOutputStream = new FileOutputStream(logFile, true);

            // Build command array
            List<String> cmdarray = new ArrayList<>();
            cmdarray.add(command);
            cmdarray.add(scriptFile);
            if (ArrayTool.isNotEmpty(params)) {
                for (String param : params) {
                    cmdarray.add(param);
                }
            }

            // Build process with environment variables
            ProcessBuilder processBuilder = new ProcessBuilder(cmdarray);
            if (MapTool.isNotEmpty(envVars)) {
                processBuilder.environment().putAll(envVars);
            }

            // Start process
            process = processBuilder.start();
            Process finalProcess = process;

            // Stream stdout and stderr to log file in separate threads
            final FileOutputStream finalFileOutputStream = fileOutputStream;
            inputThread =
                    new Thread(
                            () -> {
                                try {
                                    IOTool.copy(
                                            finalProcess.getInputStream(),
                                            finalFileOutputStream,
                                            true,
                                            false);
                                } catch (IOException e) {
                                    XxlJobHelper.log(e);
                                }
                            });
            errorThread =
                    new Thread(
                            () -> {
                                try {
                                    IOTool.copy(
                                            finalProcess.getErrorStream(),
                                            finalFileOutputStream,
                                            true,
                                            false);
                                } catch (IOException e) {
                                    XxlJobHelper.log(e);
                                }
                            });
            inputThread.start();
            errorThread.start();

            // Wait for process to complete
            int exitValue = process.waitFor();

            // Wait for log streaming threads to complete
            inputThread.join();
            errorThread.join();

            return exitValue;
        } catch (Exception e) {
            XxlJobHelper.log(e);
            return -1;
        } finally {
            // Close log file
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    XxlJobHelper.log(e);
                }
            }

            // Interrupt log streaming threads if still running
            if (inputThread != null && inputThread.isAlive()) {
                inputThread.interrupt();
            }
            if (errorThread != null && errorThread.isAlive()) {
                errorThread.interrupt();
            }

            // Destroy process
            if (process != null) {
                process.destroy();
            }
        }
    }
}
