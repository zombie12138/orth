package com.abyss.orth.executor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.abyss.orth.core.executor.impl.OrthJobSpringExecutor;

/**
 * Orth Job Executor Configuration for Spring Boot.
 *
 * <p>This configuration class initializes and configures the Orth job executor within a Spring Boot
 * application. It reads configuration from {@code application.properties} and creates an {@link
 * OrthJobSpringExecutor} bean that manages job execution and communication with the admin
 * scheduler.
 *
 * <h2>Configuration Properties:</h2>
 *
 * <h3>Admin Connection (Required):</h3>
 *
 * <ul>
 *   <li>{@code orth.job.admin.addresses} - Admin scheduler addresses (comma-separated for cluster).
 *       Example: {@code http://localhost:18080/orth-admin}
 *   <li>{@code orth.job.admin.accessToken} - Access token for authentication. Must match admin
 *       configuration. Empty string disables authentication.
 *   <li>{@code orth.job.admin.timeout} - HTTP request timeout in milliseconds. Default: 3000.
 * </ul>
 *
 * <h3>Executor Identity (Required):</h3>
 *
 * <ul>
 *   <li>{@code orth.job.executor.appname} - Executor app name, used for grouping executors. Must
 *       match the executor group configured in admin. Example: {@code orth-executor-sample}
 *   <li>{@code orth.job.executor.port} - Embedded Netty server port for receiving job triggers.
 *       Default: 9999. Must be unique per executor on the same host.
 * </ul>
 *
 * <h3>Executor Network (Optional):</h3>
 *
 * <ul>
 *   <li>{@code orth.job.executor.address} - Manual executor address registration. Optional. Format:
 *       {@code http://ip:port}. Leave empty for auto-registration via IP detection.
 *   <li>{@code orth.job.executor.ip} - Manual IP override for registration. Optional. Leave empty
 *       for automatic IP detection. Useful for multi-NIC or container environments.
 * </ul>
 *
 * <h3>Logging (Required):</h3>
 *
 * <ul>
 *   <li>{@code orth.job.executor.logpath} - Directory path for job execution logs. Example: {@code
 *       /data/applogs/orth/jobhandler}
 *   <li>{@code orth.job.executor.logretentiondays} - Number of days to retain logs before cleanup.
 *       Default: 30. Set to -1 to disable cleanup.
 * </ul>
 *
 * <h3>Job Handler Scanning (Optional):</h3>
 *
 * <ul>
 *   <li>{@code orth.job.executor.excludedpackage} - Comma-separated list of package prefixes to
 *       exclude from job handler scanning. Example: {@code com.example.internal}. Leave empty to
 *       scan all packages.
 * </ul>
 *
 * <h2>Executor Registration:</h2>
 *
 * <p>On startup, the executor:
 *
 * <ol>
 *   <li>Starts embedded Netty server on configured port
 *   <li>Detects local IP (or uses configured IP)
 *   <li>Sends heartbeat to admin every 30 seconds with address: {@code http://<ip>:<port>/}<br>
 *   <li>Admin updates executor group address cache based on heartbeats
 * </ol>
 *
 * <h2>Network Configuration Tips:</h2>
 *
 * <p><strong>Multi-NIC or Container Environments:</strong> For complex network scenarios (Docker,
 * Kubernetes, multi-NIC), you can use Spring Cloud Commons {@code InetUtils} for IP customization:
 *
 * <pre>
 * 1. Add dependency:
 *    &lt;dependency&gt;
 *        &lt;groupId&gt;org.springframework.cloud&lt;/groupId&gt;
 *        &lt;artifactId&gt;spring-cloud-commons&lt;/artifactId&gt;
 *        &lt;version&gt;${version}&lt;/version&gt;
 *    &lt;/dependency&gt;
 *
 * 2. Configure preferred network in application.properties:
 *    spring.cloud.inetutils.preferred-networks=192.168.1.
 *
 * 3. Inject InetUtils and get IP:
 *    String ip = inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();
 *    orthJobSpringExecutor.setIp(ip);
 * </pre>
 *
 * <h2>Job Handler Discovery:</h2>
 *
 * <p>The executor automatically scans Spring beans for methods annotated with {@code @OrthJob} and
 * registers them as job handlers. The annotation's value becomes the handler name used in admin.
 *
 * @author xuxueli 2017-04-28
 * @see OrthJobSpringExecutor
 * @see com.abyss.orth.core.handler.annotation.OrthJob
 */
@Configuration
public class OrthJobConfig {
    private static final Logger logger = LoggerFactory.getLogger(OrthJobConfig.class);

    @Value("${orth.job.admin.addresses}")
    private String adminAddresses;

    @Value("${orth.job.admin.accessToken}")
    private String accessToken;

    @Value("${orth.job.admin.timeout}")
    private int timeout;

    @Value("${orth.job.executor.appname}")
    private String appname;

    @Value("${orth.job.executor.address}")
    private String address;

    @Value("${orth.job.executor.ip}")
    private String ip;

    @Value("${orth.job.executor.port}")
    private int port;

    @Value("${orth.job.executor.logpath}")
    private String logPath;

    @Value("${orth.job.executor.logretentiondays}")
    private int logRetentionDays;

    @Value("${orth.job.executor.excludedpackage}")
    private String excludedPackage;

    /**
     * Creates and configures the Orth job executor bean.
     *
     * <p>This bean manages the executor lifecycle:
     *
     * <ul>
     *   <li>Starts embedded Netty server on initialization
     *   <li>Registers with admin via heartbeat mechanism
     *   <li>Receives and executes job triggers
     *   <li>Reports execution results back to admin
     *   <li>Cleans up resources on shutdown
     * </ul>
     *
     * @return configured OrthJobSpringExecutor instance
     */
    @Bean
    public OrthJobSpringExecutor orthJobExecutor() {
        logger.info(">>>>>>>>>>> orth-job config init.");
        OrthJobSpringExecutor orthJobSpringExecutor = new OrthJobSpringExecutor();
        orthJobSpringExecutor.setAdminAddresses(adminAddresses);
        orthJobSpringExecutor.setAppname(appname);
        orthJobSpringExecutor.setAddress(address);
        orthJobSpringExecutor.setIp(ip);
        orthJobSpringExecutor.setPort(port);
        orthJobSpringExecutor.setAccessToken(accessToken);
        orthJobSpringExecutor.setTimeout(timeout);
        orthJobSpringExecutor.setLogPath(logPath);
        orthJobSpringExecutor.setLogRetentionDays(logRetentionDays);
        orthJobSpringExecutor.setExcludedPackage(excludedPackage);

        return orthJobSpringExecutor;
    }
}
