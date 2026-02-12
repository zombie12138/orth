package com.xxl.job.executor.sample.frameless.config;

import java.util.Arrays;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.core.executor.impl.XxlJobSimpleExecutor;
import com.xxl.job.executor.sample.frameless.jobhandler.SampleXxlJob;
import com.xxl.tool.core.PropTool;

/**
 * Frameless Orth Job Executor Configuration.
 *
 * <p>This configuration class manages the lifecycle of a frameless (non-Spring) Orth job executor.
 * It demonstrates manual configuration and initialization without dependency injection frameworks.
 *
 * <h2>Design Pattern:</h2>
 *
 * <p>Uses the Singleton pattern to ensure only one executor instance exists per JVM. This is
 * critical because:
 *
 * <ul>
 *   <li>Only one Netty server should bind to the configured port
 *   <li>Heartbeat registration should occur from a single source
 *   <li>Job execution context must be consistent across handlers
 * </ul>
 *
 * <h2>Configuration Loading:</h2>
 *
 * <p>Configuration is loaded from {@code xxl-job-executor.properties} on the classpath using {@link
 * PropTool}. This properties file must contain:
 *
 * <pre>
 * # Admin Connection
 * xxl.job.admin.addresses=http://localhost:18080/xxl-job-admin
 * xxl.job.admin.accessToken=default_token
 * xxl.job.admin.timeout=3000
 *
 * # Executor Identity
 * xxl.job.executor.appname=xxl-job-executor-frameless-sample
 * xxl.job.executor.port=9997
 *
 * # Executor Network (optional)
 * xxl.job.executor.address=
 * xxl.job.executor.ip=
 *
 * # Logging
 * xxl.job.executor.logpath=/data/applogs/xxl-job/jobhandler
 * xxl.job.executor.logretentiondays=30
 * </pre>
 *
 * <h2>Job Handler Registration:</h2>
 *
 * <p>Unlike Spring Boot executor with automatic {@code @XxlJob} scanning, frameless executor
 * requires explicit handler registration:
 *
 * <pre>
 * xxlJobExecutor.setXxlJobBeanList(Arrays.asList(
 *     new SampleXxlJob(),
 *     new CustomJobHandler()
 * ));
 * </pre>
 *
 * <p>Each handler instance should have methods annotated with {@code @XxlJob} that define job
 * handler names matching those configured in admin.
 *
 * <h2>Executor Lifecycle:</h2>
 *
 * <h3>Initialization ({@link #initXxlJobExecutor()}):</h3>
 *
 * <ol>
 *   <li>Load configuration from properties file
 *   <li>Create XxlJobSimpleExecutor instance
 *   <li>Configure admin connection, identity, and logging
 *   <li>Register job handler instances
 *   <li>Start embedded Netty server
 *   <li>Begin heartbeat registration with admin
 * </ol>
 *
 * <h3>Cleanup ({@link #destroyXxlJobExecutor()}):</h3>
 *
 * <ol>
 *   <li>Stop accepting new job triggers
 *   <li>Wait for running jobs to complete (with timeout)
 *   <li>Deregister from admin
 *   <li>Shutdown Netty server
 *   <li>Release thread pools and resources
 * </ol>
 *
 * <h2>Comparison with Spring Boot Executor:</h2>
 *
 * <table border="1">
 *   <tr>
 *     <th>Aspect</th>
 *     <th>Spring Boot Executor</th>
 *     <th>Frameless Executor</th>
 *   </tr>
 *   <tr>
 *     <td>Configuration</td>
 *     <td>application.properties + @Value</td>
 *     <td>xxl-job-executor.properties + manual loading</td>
 *   </tr>
 *   <tr>
 *     <td>Job Discovery</td>
 *     <td>Automatic @XxlJob scanning</td>
 *     <td>Explicit setXxlJobBeanList()</td>
 *   </tr>
 *   <tr>
 *     <td>Lifecycle</td>
 *     <td>Spring ApplicationContext managed</td>
 *     <td>Manual init/destroy calls</td>
 *   </tr>
 *   <tr>
 *     <td>Dependencies</td>
 *     <td>Spring Boot, Spring Context</td>
 *     <td>xxl-job-core only</td>
 *   </tr>
 * </table>
 *
 * @author xuxueli 2018-10-31 19:05:43
 * @see XxlJobSimpleExecutor
 * @see SampleXxlJob
 */
public class FrameLessXxlJobConfig {
    private static final Logger logger = LoggerFactory.getLogger(FrameLessXxlJobConfig.class);

    private static final String PROPERTIES_FILE = "xxl-job-executor.properties";

    private static final FrameLessXxlJobConfig instance = new FrameLessXxlJobConfig();

    private XxlJobSimpleExecutor xxlJobExecutor;

    /** Private constructor to enforce singleton pattern. */
    private FrameLessXxlJobConfig() {}

    /**
     * Gets the singleton instance of this configuration.
     *
     * @return singleton instance
     */
    public static FrameLessXxlJobConfig getInstance() {
        return instance;
    }

    /**
     * Initializes and starts the Orth job executor.
     *
     * <p>This method:
     *
     * <ol>
     *   <li>Loads executor configuration from properties file
     *   <li>Creates and configures XxlJobSimpleExecutor instance
     *   <li>Registers job handler instances
     *   <li>Starts embedded Netty server
     *   <li>Begins heartbeat registration with admin
     * </ol>
     *
     * <p><strong>Thread Safety:</strong> This method is not thread-safe. It should only be called
     * once during application initialization.
     */
    public void initXxlJobExecutor() {
        Properties xxlJobProp = loadExecutorProperties();

        xxlJobExecutor = new XxlJobSimpleExecutor();
        configureExecutor(xxlJobProp);
        registerJobHandlers();
        startExecutor();
    }

    /**
     * Loads executor configuration from properties file.
     *
     * @return loaded properties
     */
    private Properties loadExecutorProperties() {
        logger.info("Loading executor properties from: {}", PROPERTIES_FILE);
        return PropTool.loadProp(PROPERTIES_FILE);
    }

    /**
     * Configures executor with properties.
     *
     * @param xxlJobProp configuration properties
     */
    private void configureExecutor(Properties xxlJobProp) {
        xxlJobExecutor.setAdminAddresses(xxlJobProp.getProperty("xxl.job.admin.addresses"));
        xxlJobExecutor.setAccessToken(xxlJobProp.getProperty("xxl.job.admin.accessToken"));
        xxlJobExecutor.setTimeout(parseIntProperty(xxlJobProp, "xxl.job.admin.timeout", 3000));

        xxlJobExecutor.setAppname(xxlJobProp.getProperty("xxl.job.executor.appname"));
        xxlJobExecutor.setAddress(xxlJobProp.getProperty("xxl.job.executor.address"));
        xxlJobExecutor.setIp(xxlJobProp.getProperty("xxl.job.executor.ip"));
        xxlJobExecutor.setPort(parseIntProperty(xxlJobProp, "xxl.job.executor.port", 9999));

        xxlJobExecutor.setLogPath(xxlJobProp.getProperty("xxl.job.executor.logpath"));
        xxlJobExecutor.setLogRetentionDays(
                parseIntProperty(xxlJobProp, "xxl.job.executor.logretentiondays", 30));
    }

    /**
     * Parses integer property with default value.
     *
     * @param properties properties object
     * @param key property key
     * @param defaultValue default value if parsing fails
     * @return parsed integer or default value
     */
    private int parseIntProperty(Properties properties, String key, int defaultValue) {
        try {
            String value = properties.getProperty(key);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse property {}, using default: {}", key, defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * Registers job handler instances with executor.
     *
     * <p>Add additional job handler instances to the list as needed:
     *
     * <pre>
     * xxlJobExecutor.setXxlJobBeanList(Arrays.asList(
     *     new SampleXxlJob(),
     *     new MyCustomJobHandler(),
     *     new AnotherJobHandler()
     * ));
     * </pre>
     */
    private void registerJobHandlers() {
        logger.info("Registering job handlers...");
        xxlJobExecutor.setXxlJobBeanList(Arrays.asList(new SampleXxlJob()));
        logger.info("Job handlers registered successfully");
    }

    /**
     * Starts the executor and begins accepting job triggers.
     *
     * <p>This starts the embedded Netty server and heartbeat thread.
     */
    private void startExecutor() {
        try {
            logger.info("Starting executor...");
            xxlJobExecutor.start();
            logger.info("Executor started successfully");
        } catch (Exception e) {
            logger.error("Failed to start executor", e);
        }
    }

    /**
     * Stops the executor and releases all resources.
     *
     * <p>This method performs graceful shutdown:
     *
     * <ol>
     *   <li>Stops accepting new job triggers
     *   <li>Waits for running jobs to complete (with timeout)
     *   <li>Deregisters from admin
     *   <li>Shuts down Netty server
     *   <li>Releases thread pools
     * </ol>
     *
     * <p><strong>Thread Safety:</strong> This method is not thread-safe. It should only be called
     * once during application shutdown.
     */
    public void destroyXxlJobExecutor() {
        if (xxlJobExecutor != null) {
            logger.info("Stopping executor...");
            xxlJobExecutor.destroy();
            logger.info("Executor stopped successfully");
        }
    }
}
