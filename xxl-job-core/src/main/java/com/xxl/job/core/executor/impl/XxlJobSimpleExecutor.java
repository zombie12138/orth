package com.xxl.job.core.executor.impl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.handler.annotation.XxlJob;

/**
 * Simple executor implementation for frameless environments.
 *
 * <p>This executor is designed for standalone applications without Spring/Spring Boot framework
 * support. It manually manages job handler registration by scanning provided bean instances for
 * {@link XxlJob} annotated methods.
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
 * executor.setXxlJobBeanList(jobHandlerBeans);
 *
 * // Start executor
 * executor.start();
 * }</pre>
 *
 * @author xuxueli 2020-11-05
 * @see XxlJobExecutor
 * @see XxlJobSpringExecutor
 */
public class XxlJobSimpleExecutor extends XxlJobExecutor {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobSimpleExecutor.class);

    /**
     * List of bean instances containing {@link XxlJob} annotated job handler methods.
     *
     * <p>These beans will be scanned during startup to register job handlers.
     */
    private List<Object> xxlJobBeanList = new ArrayList<>();

    /**
     * Gets the list of job handler beans.
     *
     * @return list of bean instances
     */
    public List<Object> getXxlJobBeanList() {
        return xxlJobBeanList;
    }

    /**
     * Sets the list of job handler beans to scan for {@link XxlJob} annotations.
     *
     * @param xxlJobBeanList list of bean instances
     */
    public void setXxlJobBeanList(List<Object> xxlJobBeanList) {
        this.xxlJobBeanList = xxlJobBeanList;
    }

    /**
     * Starts the executor.
     *
     * <p>This method:
     *
     * <ol>
     *   <li>Scans all beans in {@link #xxlJobBeanList} for {@link XxlJob} annotated methods
     *   <li>Registers found methods as job handlers
     *   <li>Starts the embedded Netty server and registry threads
     * </ol>
     *
     * @throws RuntimeException if startup fails
     */
    @Override
    public void start() {
        // Initialize job handler repository from method annotations
        initJobHandlerMethodRepository(xxlJobBeanList);

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
     * Scans bean instances for {@link XxlJob} annotated methods and registers them as job handlers.
     *
     * <p>For each bean:
     *
     * <ol>
     *   <li>Retrieves all declared methods
     *   <li>Checks each method for {@link XxlJob} annotation
     *   <li>Registers annotated methods as job handlers using the annotation's value as handler
     *       name
     * </ol>
     *
     * @param xxlJobBeanList list of bean instances to scan
     */
    private void initJobHandlerMethodRepository(List<Object> xxlJobBeanList) {
        if (xxlJobBeanList == null || xxlJobBeanList.isEmpty()) {
            return;
        }

        logger.info("Initializing job handlers from {} bean(s)", xxlJobBeanList.size());

        // Scan each bean for @XxlJob annotated methods
        for (Object bean : xxlJobBeanList) {
            Method[] methods = bean.getClass().getDeclaredMethods();
            if (methods.length == 0) {
                continue;
            }

            for (Method executeMethod : methods) {
                XxlJob xxlJob = executeMethod.getAnnotation(XxlJob.class);
                if (xxlJob != null) {
                    // Register job handler using annotation metadata
                    registryJobHandler(xxlJob, bean, executeMethod);
                }
            }
        }
    }
}
