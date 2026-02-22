package com.abyss.orth.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Orth admin application entry point.
 *
 * <p>This is the main Spring Boot application for the Orth scheduling admin console. It provides:
 *
 * <ul>
 *   <li>Web-based admin console for job management
 *   <li>RESTful APIs for job operations
 *   <li>RPC server for executor communication
 *   <li>Background scheduling and monitoring threads
 * </ul>
 *
 * <p>The application initializes all necessary components including:
 *
 * <ul>
 *   <li>Database connections and MyBatis mappers
 *   <li>Job scheduling threads (JobScheduleHelper, ring buffer)
 *   <li>Executor registry monitoring (JobRegistryHelper)
 *   <li>Job trigger thread pools (fast and slow)
 *   <li>Execution callback processing (JobCompleteHelper)
 *   <li>Alarm and monitoring threads
 * </ul>
 *
 * @author xuxueli 2018-10-28 00:38:13
 */
@SpringBootApplication
public class OrthAdminApplication {

    /**
     * Application entry point.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(OrthAdminApplication.class, args);
    }
}
