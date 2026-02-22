package com.abyss.orth.executor.jobhandler;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.abyss.orth.core.context.OrthJobHelper;
import com.abyss.orth.core.handler.annotation.OrthJob;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.gson.GsonTool;
import com.xxl.tool.http.HttpTool;
import com.xxl.tool.http.http.HttpResponse;
import com.xxl.tool.http.http.enums.ContentType;
import com.xxl.tool.http.http.enums.Method;

/**
 * Sample Orth Job Handlers (Bean Mode).
 *
 * <p>This class demonstrates various types of job handlers using the Bean mode approach. Each
 * handler is a method annotated with {@code @OrthJob}, which automatically registers it with the
 * executor for discovery by the admin scheduler.
 *
 * <h2>Development Steps:</h2>
 *
 * <ol>
 *   <li><strong>Job Development:</strong> Create a method in a Spring Bean component
 *   <li><strong>Annotation Configuration:</strong> Add {@code @OrthJob(value="handlerName", init =
 *       "initMethod", destroy = "destroyMethod")} where value matches the JobHandler name in admin
 *   <li><strong>Execution Logging:</strong> Use {@code OrthJobHelper.log()} for all job output
 *   <li><strong>Result Handling:</strong> Default result is SUCCESS. Use {@code
 *       OrthJobHelper.handleFail()} or {@code OrthJobHelper.handleSuccess()} to explicitly set
 *       results
 * </ol>
 *
 * <h2>Job Handler Types:</h2>
 *
 * <ul>
 *   <li><strong>Simple Job:</strong> Basic execution with logging
 *   <li><strong>Sharding Job:</strong> Distributed parallel processing with shard indices
 *   <li><strong>Command Job:</strong> Execute shell commands with output capture
 *   <li><strong>HTTP Job:</strong> Cross-platform HTTP requests with configurable parameters
 *   <li><strong>Lifecycle Job:</strong> Custom initialization and cleanup logic
 * </ul>
 *
 * <h2>Parameter Passing:</h2>
 *
 * <p>Job parameters are retrieved using {@code OrthJobHelper.getJobParam()} and can be:
 *
 * <ul>
 *   <li>Plain text strings
 *   <li>JSON objects for structured data
 *   <li>Command line arguments for shell execution
 * </ul>
 *
 * <h2>Context Information:</h2>
 *
 * <p>Access execution context via {@code OrthJobHelper}:
 *
 * <ul>
 *   <li>{@code getJobId()} - Job configuration ID
 *   <li>{@code getJobLogId()} - Current execution log ID
 *   <li>{@code getShardIndex()} / {@code getShardTotal()} - Sharding information
 *   <li>{@code getBroadcastIndex()} / {@code getBroadcastTotal()} - Broadcast information
 * </ul>
 *
 * @author xuxueli 2019-12-11 21:52:51
 * @see OrthJobHelper
 * @see com.abyss.orth.core.handler.annotation.OrthJob
 */
@Component
public class SampleOrthJob {
    private static final Logger logger = LoggerFactory.getLogger(SampleOrthJob.class);

    private static final int DEMO_LOOP_COUNT = 5;
    private static final int DEMO_SLEEP_SECONDS = 2;
    private static final int DEFAULT_HTTP_TIMEOUT_MS = 3000;
    private static final int SUCCESS_EXIT_CODE = 0;

    /**
     * Simple job handler demonstration (Bean mode).
     *
     * <p>This is a basic job that logs messages and simulates work with periodic heartbeats. It
     * demonstrates:
     *
     * <ul>
     *   <li>Basic logging with {@code OrthJobHelper.log()}
     *   <li>Long-running job with periodic status updates
     *   <li>Default SUCCESS result (no explicit result setting needed)
     * </ul>
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
        // default success
    }

    /**
     * Sharding broadcast job handler demonstration.
     *
     * <p>This job demonstrates distributed parallel processing using sharding. When routing
     * strategy is set to SHARDING_BROADCAST in admin, each executor receives a unique shard index
     * (0 to N-1) and can process its assigned data partition independently.
     *
     * <p><strong>Use Cases:</strong>
     *
     * <ul>
     *   <li>Processing large datasets partitioned by ID range, date range, or hash
     *   <li>Distributed batch data collection from multiple sources
     *   <li>Parallel ETL pipelines where each executor handles different data segments
     * </ul>
     *
     * <p><strong>Sharding Example:</strong>
     *
     * <pre>
     * Total executors: 3
     * Executor A receives: shardIndex=0, shardTotal=3 (processes IDs % 3 == 0)
     * Executor B receives: shardIndex=1, shardTotal=3 (processes IDs % 3 == 1)
     * Executor C receives: shardIndex=2, shardTotal=3 (processes IDs % 3 == 2)
     * </pre>
     *
     * <p><strong>Usage:</strong> Create a job in admin with:
     *
     * <ul>
     *   <li>JobHandler="shardingJobHandler"
     *   <li>Route Strategy="SHARDING_BROADCAST"
     * </ul>
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
     * <p>This job executes shell commands and captures their output. It supports any command
     * available in the executor's environment and logs stdout/stderr in real-time.
     *
     * <p><strong>Security Note:</strong> This handler executes arbitrary commands. Ensure proper
     * access controls and input validation in production environments.
     *
     * <p><strong>Usage:</strong> Create a job in admin with JobHandler="commandJobHandler"
     *
     * <p><strong>Parameters (required):</strong> Command string with arguments
     *
     * <pre>
     * Examples:
     * - "ls -la /data/logs"
     * - "pwd"
     * - "python /opt/scripts/collect_data.py --date 2026-02-08"
     * </pre>
     *
     * <p><strong>Exit Code Handling:</strong>
     *
     * <ul>
     *   <li>Exit code 0: Job SUCCESS
     *   <li>Non-zero exit code: Job FAIL with error message
     * </ul>
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

    /**
     * Checks if command parameter is empty.
     *
     * @param command command string to validate
     * @return true if command is null or empty
     */
    private boolean isCommandEmpty(String command) {
        return command == null || command.trim().isEmpty();
    }

    /**
     * Executes a shell command and logs its output.
     *
     * @param command command string to execute
     * @return process exit code (0 for success)
     * @throws Exception if command execution fails
     */
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

    /**
     * Logs process output line by line.
     *
     * @param process running process to read output from
     * @throws Exception if reading output fails
     */
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
     * <p>This job executes HTTP requests with full control over method, headers, body, and timeout.
     * It's useful for:
     *
     * <ul>
     *   <li>Triggering webhooks or external APIs
     *   <li>Data collection from REST endpoints
     *   <li>Integration with third-party services
     *   <li>Service health checks
     * </ul>
     *
     * <p><strong>Security:</strong> Domain whitelist enforced via {@link #DOMAIN_WHITE_LIST}.
     * Modify this set to allow additional domains.
     *
     * <p><strong>Usage:</strong> Create a job in admin with JobHandler="httpJobHandler"
     *
     * <p><strong>Parameters (required):</strong> JSON object with HTTP configuration
     *
     * <p><strong>Simple Example:</strong>
     *
     * <pre>
     * {
     *   "url": "http://www.baidu.com",
     *   "method": "GET",
     *   "data": "hello world"
     * }
     * </pre>
     *
     * <p><strong>Complete Example:</strong>
     *
     * <pre>
     * {
     *   "url": "http://api.example.com/data",
     *   "method": "POST",
     *   "contentType": "application/json",
     *   "headers": {
     *     "Authorization": "Bearer token123",
     *     "X-Request-ID": "job-12345"
     *   },
     *   "cookies": {
     *     "session": "abc123"
     *   },
     *   "timeout": 5000,
     *   "data": "{\"key\": \"value\"}",
     *   "form": {
     *     "field1": "value1"
     *   },
     *   "auth": "Basic dXNlcjpwYXNz"
     * }
     * </pre>
     *
     * <p><strong>Parameter Fields:</strong>
     *
     * <ul>
     *   <li>{@code url} (required): Target URL
     *   <li>{@code method} (optional): HTTP method (GET, POST, PUT, DELETE, etc.). Default: POST
     *   <li>{@code contentType} (optional): Content-Type header. Default: application/json
     *   <li>{@code headers} (optional): Custom headers map
     *   <li>{@code cookies} (optional): Cookies map
     *   <li>{@code timeout} (optional): Request timeout in ms. Default: 3000
     *   <li>{@code data} (optional): Request body as string
     *   <li>{@code form} (optional): Form data map (auto sets content-type to
     *       application/x-www-form-urlencoded)
     *   <li>{@code auth} (optional): Authorization header value
     * </ul>
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

    /**
     * Checks if parameter is empty.
     *
     * @param param parameter string to validate
     * @return true if parameter is null or empty
     */
    private boolean isParameterEmpty(String param) {
        return param == null || param.trim().isEmpty();
    }

    /**
     * Parses JSON parameter into HttpJobParam object.
     *
     * @param param JSON string to parse
     * @return parsed HttpJobParam or null if parsing fails
     */
    private HttpJobParam parseHttpJobParam(String param) {
        try {
            return GsonTool.fromJson(param, HttpJobParam.class);
        } catch (Exception e) {
            OrthJobHelper.log(new RuntimeException("HttpJobParam parse error", e));
            OrthJobHelper.handleFail();
            return null;
        }
    }

    /**
     * Validates HTTP job parameters.
     *
     * @param httpJobParam parameters to validate
     * @return true if parameters are valid
     */
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

    /**
     * Resolves HTTP method from parameter.
     *
     * @param httpJobParam parameters containing method
     * @return resolved Method enum or null if invalid
     */
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

    /**
     * Resolves content type from parameter.
     *
     * @param httpJobParam parameters containing content type
     * @return resolved ContentType enum
     */
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

    /**
     * Applies default timeout if not specified.
     *
     * @param httpJobParam parameters to modify
     */
    private void applyDefaultTimeout(HttpJobParam httpJobParam) {
        if (httpJobParam.getTimeout() <= 0) {
            httpJobParam.setTimeout(DEFAULT_HTTP_TIMEOUT_MS);
        }
    }

    /**
     * Executes HTTP request and logs response.
     *
     * @param httpJobParam request parameters
     * @param method HTTP method
     * @param contentType content type
     */
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
     * <p>Only URLs starting with these prefixes are allowed. Modify this set to add additional
     * trusted domains in production.
     */
    private static final Set<String> DOMAIN_WHITE_LIST =
            Set.of("http://www.baidu.com", "http://cn.bing.com");

    /**
     * Validates if URL domain is in whitelist.
     *
     * @param url URL to validate
     * @return true if URL starts with whitelisted domain
     */
    private boolean isValidDomain(String url) {
        if (url == null || DOMAIN_WHITE_LIST.isEmpty()) {
            return false;
        }

        return DOMAIN_WHITE_LIST.stream().anyMatch(url::startsWith);
    }

    /**
     * HTTP Job Parameter Model.
     *
     * <p>Encapsulates all HTTP request configuration for the HTTP job handler.
     */
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
     * Lifecycle job handler demonstration with custom initialization and cleanup.
     *
     * <p>This job demonstrates the use of init and destroy lifecycle methods. These methods are
     * called once when the job handler is first registered and when the executor shuts down,
     * respectively.
     *
     * <p><strong>Use Cases:</strong>
     *
     * <ul>
     *   <li>Initialize database connections or resource pools
     *   <li>Load configuration or cache data on startup
     *   <li>Clean up resources on shutdown
     *   <li>Close file handles or network connections
     * </ul>
     *
     * <p><strong>Usage:</strong> Create a job in admin with JobHandler="demoJobHandler2"
     *
     * <p><strong>Parameters:</strong> None required
     *
     * @throws Exception if job execution fails
     */
    @OrthJob(value = "demoJobHandler2", init = "init", destroy = "destroy")
    public void demoJobHandler2() throws Exception {
        OrthJobHelper.log("Orth-Job, Hello World.");
    }

    /**
     * Initialization method called once when job handler is registered.
     *
     * <p>Use this method to initialize resources needed by the job handler.
     */
    public void init() {
        logger.info("init");
    }

    /**
     * Cleanup method called once when executor shuts down.
     *
     * <p>Use this method to release resources and perform cleanup.
     */
    public void destroy() {
        logger.info("destroy");
    }
}
