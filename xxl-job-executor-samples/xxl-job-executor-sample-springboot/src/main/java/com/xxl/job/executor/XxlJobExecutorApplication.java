package com.xxl.job.executor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Orth Job Executor Spring Boot Application.
 *
 * <p>This is a standard Spring Boot executor application that demonstrates how to integrate the
 * Orth distributed job scheduling framework into a Spring Boot environment.
 *
 * <h2>Key Features:</h2>
 *
 * <ul>
 *   <li>Auto-registration with Orth admin scheduler via heartbeat mechanism (every 30 seconds)
 *   <li>Embedded Netty server for receiving job triggers from admin
 *   <li>Support for multiple job handler types: Bean, Script, GLUE, HTTP, Command
 *   <li>Automatic job handler discovery via {@code @XxlJob} annotation scanning
 * </ul>
 *
 * <h2>Configuration:</h2>
 *
 * <p>Executor configuration is managed in {@code application.properties}:
 *
 * <pre>
 * xxl.job.admin.addresses=http://localhost:18080/xxl-job-admin
 * xxl.job.admin.accessToken=default_token
 * xxl.job.executor.appname=xxl-job-executor-sample
 * xxl.job.executor.port=9999
 * </pre>
 *
 * <h2>Job Registration:</h2>
 *
 * <p>Jobs are automatically discovered and registered by annotating methods with {@code @XxlJob}:
 *
 * <pre>
 * &#64;Component
 * public class MyJobHandler {
 *     &#64;XxlJob("myJobHandler")
 *     public void execute() {
 *         XxlJobHelper.log("Job executing...");
 *     }
 * }
 * </pre>
 *
 * <h2>Lifecycle:</h2>
 *
 * <ol>
 *   <li>Application starts and initializes XxlJobSpringExecutor bean
 *   <li>Executor starts embedded Netty server on configured port
 *   <li>Executor registers with admin via heartbeat every 30 seconds
 *   <li>Admin discovers executor and can trigger jobs
 *   <li>On shutdown, executor deregisters and cleans up resources
 * </ol>
 *
 * @author xuxueli 2018-10-28 00:38:13
 * @see com.xxl.job.executor.config.XxlJobConfig
 * @see com.xxl.job.executor.jobhandler.SampleXxlJob
 */
@SpringBootApplication
public class XxlJobExecutorApplication {

    /**
     * Application entry point.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(XxlJobExecutorApplication.class, args);
    }
}
