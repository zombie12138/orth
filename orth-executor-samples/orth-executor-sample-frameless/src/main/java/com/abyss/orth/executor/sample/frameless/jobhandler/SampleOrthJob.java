package com.abyss.orth.executor.sample.frameless.jobhandler;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.abyss.orth.core.context.OrthJobHelper;
import com.abyss.orth.core.handler.annotation.OrthJob;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.gson.GsonTool;
import com.xxl.tool.http.HttpTool;
import com.xxl.tool.http.http.HttpResponse;
import com.xxl.tool.http.http.enums.ContentType;
import com.xxl.tool.http.http.enums.Method;

/**
 * Sample Orth Job Handlers for Frameless Executor (Bean Mode).
 *
 * <p>This class demonstrates job handlers for frameless (non-Spring) executor environments. The
 * handlers are functionally identical to Spring Boot executor handlers but are instantiated
 * directly without Spring dependency injection.
 *
 * <h2>Key Differences from Spring Boot Version:</h2>
 *
 * <ul>
 *   <li><strong>No @Component:</strong> Class is instantiated directly in {@link
 *       com.abyss.orth.executor.sample.frameless.config.FrameLessOrthJobConfig}
 *   <li><strong>No Dependency Injection:</strong> No @Resource or @Autowired fields
 *   <li><strong>Explicit Registration:</strong> Must be added to executor's job bean list manually
 * </ul>
 *
 * <h2>Job Handler Registration:</h2>
 *
 * <p>Handlers are registered in FrameLessOrthJobConfig:
 *
 * <pre>
 * orthJobExecutor.setOrthJobBeanList(Arrays.asList(
 *     new SampleOrthJob()
 * ));
 * </pre>
 *
 * <h2>Development Steps:</h2>
 *
 * <ol>
 *   <li><strong>Job Development:</strong> Create a method in this class
 *   <li><strong>Annotation Configuration:</strong> Add {@code @OrthJob(value="handlerName")}
 *       matching admin JobHandler name
 *   <li><strong>Execution Logging:</strong> Use {@code OrthJobHelper.log()} for all job output
 *   <li><strong>Result Handling:</strong> Default result is SUCCESS. Use {@code
 *       OrthJobHelper.handleFail()} to explicitly mark failures
 *   <li><strong>Registration:</strong> Add handler instance to executor's job bean list
 * </ol>
 *
 * <h2>Available Job Handlers:</h2>
 *
 * <ul>
 *   <li><strong>demoJobHandler:</strong> Simple job with logging and periodic heartbeats
 *   <li><strong>shardingJobHandler:</strong> Distributed parallel processing with shard indices
 *   <li><strong>commandJobHandler:</strong> Execute shell commands with output capture
 *   <li><strong>httpJobHandler:</strong> HTTP requests with configurable parameters and domain
 *       whitelist
 *   <li><strong>demoJobHandler2:</strong> Lifecycle demonstration with init/destroy methods
 * </ul>
 *
 * @author xuxueli 2019-12-11 21:52:51
 * @see OrthJobHelper
 * @see com.abyss.orth.core.handler.annotation.OrthJob
 */
public class SampleOrthJob {
    private static final Logger logger = LoggerFactory.getLogger(SampleOrthJob.class);

    private static final int DEMO_LOOP_COUNT = 5;
    private static final int DEMO_SLEEP_SECONDS = 2;
    private static final int DEFAULT_HTTP_TIMEOUT_MS = 3000;
    private static final int SUCCESS_EXIT_CODE = 0;

    /**
     * Simple job handler demonstration (Bean mode).
     *
     * <p>Basic job that logs messages and simulates work with periodic heartbeats.
     *
     * <p><strong>Usage:</strong> Create a job in admin with JobHandler="demoJobHandler"
     *
     * <p><strong>Parameters:</strong> None required
     *
     * @throws Exception if job execution fails
     */
    @OrthJob("demoJobHandler")
    public void demoJobHandler() throws Exception {
        OrthJobHelper.log("Orth-Job, Hello World.");

        for (int i = 0; i < DEMO_LOOP_COUNT; i++) {
            OrthJobHelper.log("beat at:" + i);
            TimeUnit.SECONDS.sleep(DEMO_SLEEP_SECONDS);
        }
    }

    /**
     * Sharding broadcast job handler demonstration.
     *
     * <p>Demonstrates distributed parallel processing using sharding. Each executor receives a
     * unique shard index for independent data partition processing.
     *
     * <p><strong>Usage:</strong> Create a job in admin with JobHandler="shardingJobHandler" and
     * Route Strategy="SHARDING_BROADCAST"
     *
     * <p><strong>Parameters:</strong> None required (shard info from context)
     *
     * @throws Exception if job execution fails
     */
    @OrthJob("shardingJobHandler")
    public void shardingJobHandler() throws Exception {
        int shardIndex = OrthJobHelper.getShardIndex();
        int shardTotal = OrthJobHelper.getShardTotal();

        OrthJobHelper.log(
                "Shard parameters: current shard index = {}, total shards = {}",
                shardIndex,
                shardTotal);

        for (int i = 0; i < shardTotal; i++) {
            if (i == shardIndex) {
                OrthJobHelper.log("Shard {}, matched - processing", i);
            } else {
                OrthJobHelper.log("Shard {}, skipped", i);
            }
        }
    }

    /**
     * Command line job handler demonstration.
     *
     * <p>Executes shell commands and captures output.
     *
     * <p><strong>Security Note:</strong> Executes arbitrary commands. Ensure proper access controls
     * in production.
     *
     * <p><strong>Usage:</strong> Create a job in admin with JobHandler="commandJobHandler"
     *
     * <p><strong>Parameters (required):</strong> Command string (e.g., "ls -la /data/logs")
     *
     * @throws Exception if command execution fails
     */
    @OrthJob("commandJobHandler")
    public void commandJobHandler() throws Exception {
        String command = OrthJobHelper.getJobParam();

        if (isCommandEmpty(command)) {
            OrthJobHelper.handleFail("command empty.");
            return;
        }

        int exitValue = executeCommand(command);

        if (exitValue != SUCCESS_EXIT_CODE) {
            OrthJobHelper.handleFail("command exit value(" + exitValue + ") is failed");
        }
    }

    private boolean isCommandEmpty(String command) {
        return command == null || command.trim().isEmpty();
    }

    private int executeCommand(String command) throws Exception {
        String[] commandArray = command.split(" ");

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(commandArray);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        logProcessOutput(process);

        process.waitFor();
        return process.exitValue();
    }

    private void logProcessOutput(Process process) throws Exception {
        try (BufferedInputStream bufferedInputStream =
                        new BufferedInputStream(process.getInputStream());
                BufferedReader bufferedReader =
                        new BufferedReader(new InputStreamReader(bufferedInputStream))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                OrthJobHelper.log(line);
            }
        }
    }

    /**
     * Cross-platform HTTP job handler demonstration.
     *
     * <p>Executes HTTP requests with full parameter control and domain whitelist security.
     *
     * <p><strong>Usage:</strong> Create a job in admin with JobHandler="httpJobHandler"
     *
     * <p><strong>Parameters (required JSON):</strong>
     *
     * <pre>
     * {
     *   "url": "http://www.baidu.com",
     *   "method": "POST",
     *   "contentType": "application/json",
     *   "headers": {"Authorization": "Bearer token"},
     *   "timeout": 5000,
     *   "data": "{\"key\": \"value\"}"
     * }
     * </pre>
     *
     * @throws Exception if HTTP request fails
     */
    @OrthJob("httpJobHandler")
    public void httpJobHandler() throws Exception {
        String param = OrthJobHelper.getJobParam();

        if (isParameterEmpty(param)) {
            OrthJobHelper.log("param[" + param + "] invalid.");
            OrthJobHelper.handleFail();
            return;
        }

        HttpJobParam httpJobParam = parseHttpJobParam(param);
        if (httpJobParam == null) {
            return;
        }

        if (!validateHttpJobParam(httpJobParam)) {
            return;
        }

        Method method = resolveHttpMethod(httpJobParam);
        if (method == null) {
            return;
        }

        ContentType contentType = resolveContentType(httpJobParam);
        applyDefaultTimeout(httpJobParam);
        executeHttpRequest(httpJobParam, method, contentType);
    }

    private boolean isParameterEmpty(String param) {
        return param == null || param.trim().isEmpty();
    }

    private HttpJobParam parseHttpJobParam(String param) {
        try {
            return GsonTool.fromJson(param, HttpJobParam.class);
        } catch (Exception e) {
            OrthJobHelper.log(new RuntimeException("HttpJobParam parse error", e));
            OrthJobHelper.handleFail();
            return null;
        }
    }

    private boolean validateHttpJobParam(HttpJobParam httpJobParam) {
        if (httpJobParam == null) {
            OrthJobHelper.log("param parse fail.");
            OrthJobHelper.handleFail();
            return false;
        }

        if (StringTool.isBlank(httpJobParam.getUrl())) {
            OrthJobHelper.log("url[" + httpJobParam.getUrl() + "] invalid.");
            OrthJobHelper.handleFail();
            return false;
        }

        if (!isValidDomain(httpJobParam.getUrl())) {
            OrthJobHelper.log("url[" + httpJobParam.getUrl() + "] not allowed.");
            OrthJobHelper.handleFail();
            return false;
        }

        return true;
    }

    private Method resolveHttpMethod(HttpJobParam httpJobParam) {
        Method method = Method.POST;

        if (StringTool.isNotBlank(httpJobParam.getMethod())) {
            try {
                Method methodParam = Method.valueOf(httpJobParam.getMethod().toUpperCase());
                if (methodParam != null) {
                    method = methodParam;
                }
            } catch (IllegalArgumentException e) {
                OrthJobHelper.log("method[" + httpJobParam.getMethod() + "] invalid.");
                OrthJobHelper.handleFail();
                return null;
            }
        }

        return method;
    }

    private ContentType resolveContentType(HttpJobParam httpJobParam) {
        ContentType contentType = ContentType.JSON;

        if (StringTool.isNotBlank(httpJobParam.getContentType())) {
            for (ContentType contentTypeParam : ContentType.values()) {
                if (contentTypeParam.getValue().equals(httpJobParam.getContentType())) {
                    contentType = contentTypeParam;
                    break;
                }
            }
        }

        return contentType;
    }

    private void applyDefaultTimeout(HttpJobParam httpJobParam) {
        if (httpJobParam.getTimeout() <= 0) {
            httpJobParam.setTimeout(DEFAULT_HTTP_TIMEOUT_MS);
        }
    }

    private void executeHttpRequest(
            HttpJobParam httpJobParam, Method method, ContentType contentType) {
        try {
            HttpResponse httpResponse =
                    HttpTool.createRequest()
                            .url(httpJobParam.getUrl())
                            .method(method)
                            .contentType(contentType)
                            .header(httpJobParam.getHeaders())
                            .cookie(httpJobParam.getCookies())
                            .body(httpJobParam.getData())
                            .form(httpJobParam.getForm())
                            .auth(httpJobParam.getAuth())
                            .execute();

            OrthJobHelper.log("StatusCode: " + httpResponse.statusCode());
            OrthJobHelper.log("Response: <br>" + httpResponse.response());
        } catch (Exception e) {
            OrthJobHelper.log(e);
            OrthJobHelper.handleFail();
        }
    }

    /**
     * Domain whitelist for HTTP job handler.
     *
     * <p>Only URLs starting with these prefixes are allowed.
     */
    private static final Set<String> DOMAIN_WHITE_LIST =
            Set.of("http://www.baidu.com", "http://cn.bing.com");

    private boolean isValidDomain(String url) {
        if (url == null || DOMAIN_WHITE_LIST.isEmpty()) {
            return false;
        }

        return DOMAIN_WHITE_LIST.stream().anyMatch(url::startsWith);
    }

    /** HTTP Job Parameter Model. */
    private static class HttpJobParam {
        private String url;
        private String method;
        private String contentType;
        private Map<String, String> headers;
        private Map<String, String> cookies;
        private int timeout;
        private String data;
        private Map<String, String> form;
        private String auth;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }

        public Map<String, String> getCookies() {
            return cookies;
        }

        public void setCookies(Map<String, String> cookies) {
            this.cookies = cookies;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public Map<String, String> getForm() {
            return form;
        }

        public void setForm(Map<String, String> form) {
            this.form = form;
        }

        public String getAuth() {
            return auth;
        }

        public void setAuth(String auth) {
            this.auth = auth;
        }
    }

    /**
     * Lifecycle job handler demonstration.
     *
     * <p>Demonstrates init and destroy lifecycle methods for resource management.
     *
     * <p><strong>Usage:</strong> Create a job in admin with JobHandler="demoJobHandler2"
     *
     * @throws Exception if job execution fails
     */
    @OrthJob(value = "demoJobHandler2", init = "init", destroy = "destroy")
    public void demoJobHandler2() throws Exception {
        OrthJobHelper.log("Orth-Job, Hello World.");
    }

    /** Initialization method called once when job handler is registered. */
    public void init() {
        logger.info("init");
    }

    /** Cleanup method called once when executor shuts down. */
    public void destroy() {
        logger.info("destroy");
    }
}
