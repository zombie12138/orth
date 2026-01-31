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
 * 1、内嵌编译器如"PythonInterpreter"无法引用扩展包，因此推荐使用java调用控制台进程方式"Runtime.getRuntime().exec()"来运行脚本(shell或python)；
 * 2、因为通过java调用控制台进程方式实现，需要保证目标机器PATH路径正确配置对应编译器；
 * 3、暂时脚本执行日志只能在脚本执行结束后一次性获取，无法保证实时性；因此为确保日志实时性，可改为将脚本打印的日志存储在指定的日志文件上； 4、python
 * 异常输出优先级高于标准输出，体现在Log文件中，因此推荐通过logging方式打日志保持和异常信息一致；否则用prinf日志顺序会错乱
 *
 * <p>Created by xuxueli on 17/2/25.
 */
public class ScriptUtil {

    /**
     * make script file
     *
     * @param scriptFileName script file name
     * @param scriptContent script content
     * @throws IOException exception
     */
    public static void markScriptFile(String scriptFileName, String scriptContent)
            throws IOException {
        // make file: filePath/gluesource/666-123456789.py
        FileTool.writeString(scriptFileName, scriptContent);

        /*FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(scriptFileName);
            fileOutputStream.write(scriptContent.getBytes("UTF-8"));
            fileOutputStream.close();
        } catch (Exception e) {
            throw e;
        }finally{
            if(fileOutputStream != null){
                fileOutputStream.close();
            }
        }*/
    }

    /**
     * Execute script with real-time log output (backward compatible version)
     *
     * @param command command
     * @param scriptFile script file
     * @param logFile log file
     * @param params params
     * @return exit code
     * @throws IOException exception
     */
    public static int execToFile(
            String command, String scriptFile, String logFile, String... params)
            throws IOException {
        return execToFile(command, scriptFile, logFile, null, params);
    }

    /**
     * Execute script with environment variables and real-time log output
     *
     * @param command command (e.g., "python3", "bash")
     * @param scriptFile script file path
     * @param logFile log file path for output
     * @param envVars environment variables to set (can be null)
     * @param params script parameters
     * @return exit code (0=success, non-zero=error)
     * @throws IOException exception
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
            // 1、build file OutputStream
            fileOutputStream = new FileOutputStream(logFile, true);

            // 2、build command
            List<String> cmdarray = new ArrayList<>();
            cmdarray.add(command);
            cmdarray.add(scriptFile);
            if (ArrayTool.isNotEmpty(params)) {
                for (String param : params) {
                    cmdarray.add(param);
                }
            }

            // 3、build process with ProcessBuilder (supports environment variables)
            ProcessBuilder processBuilder = new ProcessBuilder(cmdarray);

            // Set environment variables if provided
            if (MapTool.isNotEmpty(envVars)) {
                Map<String, String> environment = processBuilder.environment();
                environment.putAll(envVars);
            }

            // 4、start process
            process = processBuilder.start();
            Process finalProcess = process;

            // 5、read script log: inputStream + errStream
            final FileOutputStream finalFileOutputStream = fileOutputStream;
            inputThread =
                    new Thread(
                            () -> {
                                try {
                                    // Stream copy (Input auto-close, Output keep open)
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

            // 6、process：wait for result
            int exitValue = process.waitFor(); // exit code: 0=success, 1=error

            // 7、thread join, wait for log
            inputThread.join();
            errorThread.join();

            return exitValue;
        } catch (Exception e) {
            XxlJobHelper.log(e);
            return -1;
        } finally {
            // 8、close file OutputStream
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    XxlJobHelper.log(e);
                }
            }
            // 9、interrupt thread
            if (inputThread != null && inputThread.isAlive()) {
                inputThread.interrupt();
            }
            if (errorThread != null && errorThread.isAlive()) {
                errorThread.interrupt();
            }
            // 10、process destroy
            if (process != null) {
                process.destroy();
                // process.destroyForcibly();
            }
        }
    }

    /**
     * 脚本执行，日志文件实时输出
     *
     * <p>优点：支持将目标数据实时输出到指定日志文件中去 缺点： 标准输出和错误输出优先级固定，可能和脚本中顺序不一致 Java无法实时获取
     * <!-- commons-exec -->
     * <dependency> <groupId>org.apache.commons</groupId> <artifactId>commons-exec</artifactId>
     * <version>${commons-exec.version}</version> </dependency>
     *
     * @param command
     * @param scriptFile
     * @param logFile
     * @param params
     * @return
     * @throws IOException
     */
    /*public static int execToFileB(String command, String scriptFile, String logFile, String... params) throws IOException {
        // 标准输出：print （null if watchdog timeout）
        // 错误输出：logging + 异常 （still exists if watchdog timeout）
        // 标准输入

        FileOutputStream fileOutputStream = null;   //
        try {
            fileOutputStream = new FileOutputStream(logFile, true);
            PumpStreamHandler streamHandler = new PumpStreamHandler(fileOutputStream, fileOutputStream, null);

            // command
            CommandLine commandline = new CommandLine(command);
            commandline.addArgument(scriptFile);
            if (params!=null && params.length>0) {
                commandline.addArguments(params);
            }

            // exec
            DefaultExecutor exec = new DefaultExecutor();
            exec.setExitValues(null);
            exec.setStreamHandler(streamHandler);
            int exitValue = exec.execute(commandline);  // exit code: 0=success, 1=error
            return exitValue;
        } catch (Exception e) {
            XxlJobLogger.log(e);
            return -1;
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    XxlJobLogger.log(e);
                }

            }
        }
    }*/

}
