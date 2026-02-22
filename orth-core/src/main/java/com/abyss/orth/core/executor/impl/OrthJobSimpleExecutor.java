package com.abyss.orth.core.executor.impl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.abyss.orth.core.executor.OrthJobExecutor;
import com.abyss.orth.core.handler.annotation.OrthJob;

/**
 * Simple executor implementation for frameless environments.
 *
 * <p>This executor is designed for standalone applications without Spring/Spring Boot framework
 * support. It manually manages job handler registration by scanning provided bean instances for
 * {@link OrthJob} annotated methods.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * // Create executor
 * OrthJobSimpleExecutor executor = new OrthJobSimpleExecutor();
 * executor.setAdminAddresses("http://localhost:18080/orth-job-admin");
 * executor.setAppname("my-executor");
 * executor.setAddress("127.0.0.1:9999");
 * executor.setPort(9999);
 *
 * // Register job handler beans
 * List<Object> jobHandlerBeans = new ArrayList<>();
 * jobHandlerBeans.add(new MyJobHandler());
 * executor.setOrthJobBeanList(jobHandlerBeans);
 *
 * // Start executor
 * executor.start();
 * }</pre>
 *
 * @author xuxueli 2020-11-05
 * @see OrthJobExecutor
 * @see OrthJobSpringExecutor
 */
public class OrthJobSimpleExecutor extends OrthJobExecutor {
    private static final Logger logger = LoggerFactory.getLogger(OrthJobSimpleExecutor.class);

    /**
     * List of bean instances containing {@link OrthJob} annotated job handler methods.
     *
     * <p>These beans will be scanned during startup to register job handlers.
     */
    private List<Object> orthJobBeanList = new ArrayList<>();

    /**
     * Gets the list of job handler beans.
     *
     * @return list of bean instances
     */
    public List<Object> getOrthJobBeanList() {
        return orthJobBeanList;
    }

    /**
     * Sets the list of job handler beans to scan for {@link OrthJob} annotations.
     *
     * @param orthJobBeanList list of bean instances
     */
    public void setOrthJobBeanList(List<Object> orthJobBeanList) {
        this.orthJobBeanList = orthJobBeanList;
    }

    /**
     * Starts the executor.
     *
     * <p>This method:
     *
     * <ol>
     *   <li>Scans all beans in {@link #orthJobBeanList} for {@link OrthJob} annotated methods
     *   <li>Registers found methods as job handlers
     *   <li>Starts the embedded Netty server and registry threads
     * </ol>
     *
     * @throws RuntimeException if startup fails
     */
    @Override
    public void start() {
        // Initialize job handler repository from method annotations
        initJobHandlerMethodRepository(orthJobBeanList);

        // Start executor framework (server, registry, etc.)
        try {
            super.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start simple executor", e);
        }
    }

    /**
     * Stops the executor and releases all resources.
     *
     * <p>This method stops the embedded server, registry threads, and cleans up job handler
     * threads.
     */
    @Override
    public void destroy() {
        super.destroy();
    }

    /**
     * Scans bean instances for {@link OrthJob} annotated methods and registers them as job
     * handlers.
     *
     * <p>For each bean:
     *
     * <ol>
     *   <li>Retrieves all declared methods
     *   <li>Checks each method for {@link OrthJob} annotation
     *   <li>Registers annotated methods as job handlers using the annotation's value as handler
     *       name
     * </ol>
     *
     * @param orthJobBeanList list of bean instances to scan
     */
    private void initJobHandlerMethodRepository(List<Object> orthJobBeanList) {
        if (orthJobBeanList == null || orthJobBeanList.isEmpty()) {
            return;
        }

        logger.info("Initializing job handlers from {} bean(s)", orthJobBeanList.size());

        // Scan each bean for @OrthJob annotated methods
        for (Object bean : orthJobBeanList) {
            Method[] methods = bean.getClass().getDeclaredMethods();
            if (methods.length == 0) {
                continue;
            }

            for (Method executeMethod : methods) {
                OrthJob orthJob = executeMethod.getAnnotation(OrthJob.class);
                if (orthJob != null) {
                    // Register job handler using annotation metadata
                    registryJobHandler(orthJob, bean, executeMethod);
                }
            }
        }
    }
}
