package com.abyss.orth.executor.sample.frameless;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.abyss.orth.executor.sample.frameless.config.FrameLessOrthJobConfig;

/**
 * Orth Job Frameless Executor Application.
 *
 * <p>This is a standalone executor application that demonstrates how to integrate the Orth
 * distributed job scheduling framework without Spring or other dependency injection frameworks.
 * This approach is suitable for:
 *
 * <ul>
 *   <li>Lightweight microservices with minimal dependencies
 *   <li>Legacy applications without Spring Boot
 *   <li>Embedded systems or resource-constrained environments
 *   <li>Simple batch processing workers
 * </ul>
 *
 * <h2>Key Differences from Spring Boot Executor:</h2>
 *
 * <ul>
 *   <li><strong>Manual Configuration:</strong> No auto-configuration, explicit setup required
 *   <li><strong>Direct Instantiation:</strong> Job handlers instantiated directly, not managed by
 *       Spring
 *   <li><strong>Properties File:</strong> Configuration loaded from {@code
 *       orth-executor.properties}
 *   <li><strong>Explicit Lifecycle:</strong> Manual start/stop of executor resources
 * </ul>
 *
 * <h2>Configuration:</h2>
 *
 * <p>All configuration is loaded from {@code orth-executor.properties} on the classpath:
 *
 * <pre>
 * xxl.job.admin.addresses=http://localhost:18080/orth-admin
 * xxl.job.admin.accessToken=default_token
 * xxl.job.admin.timeout=3000
 * xxl.job.executor.appname=orth-executor-frameless-sample
 * xxl.job.executor.address=
 * xxl.job.executor.ip=
 * xxl.job.executor.port=9997
 * xxl.job.executor.logpath=/data/applogs/orth/jobhandler
 * xxl.job.executor.logretentiondays=30
 * </pre>
 *
 * <h2>Job Handler Registration:</h2>
 *
 * <p>Unlike Spring Boot executor, job handlers must be explicitly registered:
 *
 * <pre>
 * orthJobExecutor.setOrthJobBeanList(Arrays.asList(
 *     new SampleOrthJob(),
 *     new AnotherJobHandler()
 * ));
 * </pre>
 *
 * <h2>Lifecycle:</h2>
 *
 * <ol>
 *   <li>Application starts and calls {@code FrameLessOrthJobConfig.initOrthJobExecutor()}
 *   <li>Executor loads configuration from properties file
 *   <li>Job handlers are registered with executor
 *   <li>Embedded Netty server starts on configured port
 *   <li>Executor registers with admin via heartbeat every 30 seconds
 *   <li>Application blocks until interrupted (Ctrl+C or kill signal)
 *   <li>On shutdown, executor deregisters and cleans up resources
 * </ol>
 *
 * <h2>Running the Application:</h2>
 *
 * <pre>
 * # Build JAR
 * mvn clean package
 *
 * # Run standalone
 * java -jar orth-executor-sample-frameless-*.jar
 *
 * # Or with explicit classpath
 * java -cp "target/orth-executor-sample-frameless-*.jar:target/lib/*" \
 *   com.abyss.orth.executor.sample.frameless.OrthFramelessApplication
 * </pre>
 *
 * <h2>Graceful Shutdown:</h2>
 *
 * <p>The application handles interruption signals (SIGINT, SIGTERM) and performs cleanup:
 *
 * <ul>
 *   <li>Stops accepting new job triggers
 *   <li>Waits for running jobs to complete
 *   <li>Deregisters from admin
 *   <li>Shuts down Netty server and thread pools
 * </ul>
 *
 * @author xuxueli 2018-10-31 19:05:43
 * @see FrameLessOrthJobConfig
 * @see com.abyss.orth.executor.sample.frameless.jobhandler.SampleOrthJob
 */
public class OrthFramelessApplication {
    private static final Logger logger = LoggerFactory.getLogger(OrthFramelessApplication.class);

    private static final long KEEPALIVE_INTERVAL_HOURS = 1;

    /**
     * Application entry point.
     *
     * <p>Initializes executor, blocks until interrupted, then performs cleanup.
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        try {
            initializeExecutor();
            blockUntilShutdown();
        } catch (Exception e) {
            logger.error("Application error", e);
        } finally {
            cleanupExecutor();
        }
    }

    /**
     * Initializes and starts the Orth job executor.
     *
     * @throws Exception if initialization fails
     */
    private static void initializeExecutor() throws Exception {
        logger.info("Starting orth-job frameless executor...");
        FrameLessOrthJobConfig.getInstance().initOrthJobExecutor();
        logger.info("Orth-job frameless executor started successfully");
    }

    /**
     * Blocks the main thread until application is interrupted.
     *
     * <p>Enters an infinite loop that sleeps for 1 hour intervals. This keeps the application alive
     * while the executor's background threads handle job execution. When interrupted (via Ctrl+C or
     * kill signal), the loop breaks and cleanup begins.
     */
    private static void blockUntilShutdown() {
        logger.info("Application running. Press Ctrl+C to shutdown.");
        while (true) {
            try {
                TimeUnit.HOURS.sleep(KEEPALIVE_INTERVAL_HOURS);
            } catch (InterruptedException e) {
                logger.info("Shutdown signal received");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Performs graceful cleanup of executor resources.
     *
     * <p>Stops the executor, deregisters from admin, and releases all resources.
     */
    private static void cleanupExecutor() {
        logger.info("Shutting down orth-job frameless executor...");
        FrameLessOrthJobConfig.getInstance().destroyOrthJobExecutor();
        logger.info("Orth-job frameless executor stopped");
    }
}
