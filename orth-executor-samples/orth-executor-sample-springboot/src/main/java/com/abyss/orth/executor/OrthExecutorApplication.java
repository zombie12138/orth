package com.abyss.orth.executor;

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
 *   <li>Automatic job handler discovery via {@code @OrthJob} annotation scanning
 * </ul>
 *
 * <h2>Configuration:</h2>
 *
 * <p>Executor configuration is managed in {@code application.properties}:
 *
 * <pre>
 * orth.job.admin.addresses=http://localhost:18080/orth-admin
 * orth.job.admin.accessToken=default_token
 * orth.job.executor.appname=orth-executor-sample
 * orth.job.executor.port=9999
 * </pre>
 *
 * <h2>Job Registration:</h2>
 *
 * <p>Jobs are automatically discovered and registered by annotating methods with {@code @OrthJob}:
 *
 * <pre>
 * &#64;Component
 * public class MyJobHandler {
 *     &#64;OrthJob("myJobHandler")
 *     public void execute() {
 *         OrthJobHelper.log("Job executing...");
 *     }
 * }
 * </pre>
 *
 * <h2>Lifecycle:</h2>
 *
 * <ol>
 *   <li>Application starts and initializes OrthJobSpringExecutor bean
 *   <li>Executor starts embedded Netty server on configured port
 *   <li>Executor registers with admin via heartbeat every 30 seconds
 *   <li>Admin discovers executor and can trigger jobs
 *   <li>On shutdown, executor deregisters and cleans up resources
 * </ol>
 *
 * @author xuxueli 2018-10-28 00:38:13
 * @see com.abyss.orth.executor.config.OrthJobConfig
 * @see com.abyss.orth.executor.jobhandler.SampleOrthJob
 */
@SpringBootApplication
public class OrthExecutorApplication {

    /**
     * Application entry point.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(OrthExecutorApplication.class, args);
    }
}
