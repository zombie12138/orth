package com.xxl.job.executor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;

/**
 * Orth Job AI Executor Configuration for Spring Boot.
 *
 * <p>This configuration class is identical to the standard executor configuration but serves an
 * AI-specialized executor application. The AI integration is handled at the job handler level, not
 * at the executor framework level.
 *
 * <h2>Configuration Properties:</h2>
 *
 * <h3>Admin Connection (Required):</h3>
 *
 * <ul>
 *   <li>{@code xxl.job.admin.addresses} - Admin scheduler addresses. Example: {@code
 *       http://localhost:18080/xxl-job-admin}
 *   <li>{@code xxl.job.admin.accessToken} - Access token for authentication
 *   <li>{@code xxl.job.admin.timeout} - HTTP request timeout in milliseconds. Default: 3000
 * </ul>
 *
 * <h3>Executor Identity (Required):</h3>
 *
 * <ul>
 *   <li>{@code xxl.job.executor.appname} - Executor app name. Example: {@code
 *       xxl-job-executor-ai-sample}
 *   <li>{@code xxl.job.executor.port} - Embedded Netty server port. Example: 9998 (different from
 *       standard executor)
 * </ul>
 *
 * <h3>Executor Network (Optional):</h3>
 *
 * <ul>
 *   <li>{@code xxl.job.executor.address} - Manual executor address. Optional
 *   <li>{@code xxl.job.executor.ip} - Manual IP override. Optional
 * </ul>
 *
 * <h3>Logging (Required):</h3>
 *
 * <ul>
 *   <li>{@code xxl.job.executor.logpath} - Job execution log directory
 *   <li>{@code xxl.job.executor.logretentiondays} - Log retention days. Default: 30
 * </ul>
 *
 * <h2>AI Framework Configuration:</h2>
 *
 * <p>AI framework configuration is separate from executor configuration:
 *
 * <h3>Ollama Configuration:</h3>
 *
 * <pre>
 * spring.ai.ollama.base-url=http://localhost:11434
 * spring.ai.ollama.chat.model=qwen3:0.6b
 * spring.ai.ollama.chat.options.temperature=0.7
 * </pre>
 *
 * <h3>Dify Configuration:</h3>
 *
 * <p>Dify credentials are typically passed as job parameters for flexibility and security, rather
 * than hardcoded in application.properties:
 *
 * <pre>
 * {
 *   "baseUrl": "http://localhost/v1",
 *   "apiKey": "app-xxx",
 *   "inputs": {"input": "user query"}
 * }
 * </pre>
 *
 * <h2>Job Handler Registration:</h2>
 *
 * <p>AI job handlers in {@link com.xxl.job.executor.jobhandler.AIXxlJob} are automatically
 * discovered and registered via {@code @XxlJob} annotation scanning.
 *
 * @author xuxueli 2017-04-28
 * @see XxlJobSpringExecutor
 * @see com.xxl.job.executor.jobhandler.AIXxlJob
 * @see com.xxl.job.core.handler.annotation.XxlJob
 */
@Configuration
public class XxlJobConfig {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobConfig.class);

    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    @Value("${xxl.job.admin.accessToken}")
    private String accessToken;

    @Value("${xxl.job.admin.timeout}")
    private int timeout;

    @Value("${xxl.job.executor.appname}")
    private String appname;

    @Value("${xxl.job.executor.address}")
    private String address;

    @Value("${xxl.job.executor.ip}")
    private String ip;

    @Value("${xxl.job.executor.port}")
    private int port;

    @Value("${xxl.job.executor.logpath}")
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays}")
    private int logRetentionDays;

    /**
     * Creates and configures the Orth job executor bean.
     *
     * <p>This bean manages the executor lifecycle for AI-specialized jobs:
     *
     * <ul>
     *   <li>Starts embedded Netty server for job triggers
     *   <li>Registers with admin via heartbeat mechanism
     *   <li>Receives and executes AI job triggers
     *   <li>Reports AI job execution results back to admin
     *   <li>Cleans up resources on shutdown
     * </ul>
     *
     * @return configured XxlJobSpringExecutor instance
     */
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        logger.info(">>>>>>>>>>> orth-job ai-executor config init.");
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAppname(appname);
        xxlJobSpringExecutor.setAddress(address);
        xxlJobSpringExecutor.setIp(ip);
        xxlJobSpringExecutor.setPort(port);
        xxlJobSpringExecutor.setAccessToken(accessToken);
        xxlJobSpringExecutor.setTimeout(timeout);
        xxlJobSpringExecutor.setLogPath(logPath);
        xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);

        return xxlJobSpringExecutor;
    }
}
