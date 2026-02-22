package com.abyss.orth.core.executor.impl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import com.abyss.orth.core.executor.OrthJobExecutor;
import com.abyss.orth.core.glue.GlueFactory;
import com.abyss.orth.core.handler.annotation.OrthJob;

/**
 * Spring-integrated Orth job executor.
 *
 * <p>Automatically discovers and registers job handlers annotated with {@link OrthJob} during
 * Spring context initialization. Integrates with Spring lifecycle for graceful startup/shutdown.
 */
public class OrthJobSpringExecutor extends OrthJobExecutor
        implements ApplicationContextAware, SmartInitializingSingleton, DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(OrthJobSpringExecutor.class);

    // ---------------------- Configuration ----------------------

    /**
     * Comma-separated list of package prefixes to exclude from scanning.
     *
     * <p>Example: {@code "org.springframework.,com.example.internal."}
     *
     * <p>Beans in these packages will not be scanned for {@code @OrthJob} annotations.
     */
    private String excludedPackage = "org.springframework.,spring.";

    public void setExcludedPackage(String excludedPackage) {
        this.excludedPackage = excludedPackage;
    }

    // ---------------------- Lifecycle Management ----------------------

    /**
     * Initializes executor after all singletons are instantiated.
     *
     * <p>Lifecycle: 1. Scan for @OrthJob methods 2. Initialize Groovy glue factory 3. Start base
     * executor (Netty server, registry thread, etc.)
     */
    @Override
    public void afterSingletonsInstantiated() {
        // Scan and register job handler methods
        scanJobHandlerMethod(applicationContext);

        // Initialize Groovy glue factory for dynamic job compilation
        GlueFactory.refreshInstance(1);

        // Start base executor framework
        try {
            super.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start Orth executor", e);
        }
    }

    /** Destroys executor on Spring context shutdown. */
    @Override
    public void destroy() {
        super.destroy();
    }

    /**
     * Scans Spring context for @OrthJob annotated methods and registers handlers.
     *
     * @param applicationContext Spring application context
     */
    private void scanJobHandlerMethod(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return;
        }

        // Build excluded package list
        List<String> excludedPackageList = buildExcludedPackageList();

        // Scan all beans (allowEagerInit=false to avoid premature initialization)
        String[] beanNames = applicationContext.getBeanNamesForType(Object.class, false, false);

        for (String beanName : beanNames) {
            // Skip excluded beans
            if (shouldSkipBean(applicationContext, beanName, excludedPackageList)) {
                continue;
            }

            // Find @OrthJob annotated methods
            Map<Method, OrthJob> annotatedMethods =
                    findAnnotatedMethods(applicationContext, beanName);
            if (annotatedMethods == null || annotatedMethods.isEmpty()) {
                continue;
            }

            // Register job handlers
            Object jobBean = applicationContext.getBean(beanName);
            annotatedMethods.forEach(
                    (method, annotation) -> {
                        registryJobHandler(annotation, jobBean, method);
                    });
        }
    }

    /** Builds list of excluded package prefixes. */
    private List<String> buildExcludedPackageList() {
        List<String> list = new ArrayList<>();
        if (excludedPackage != null) {
            for (String pkg : excludedPackage.split(",")) {
                String trimmed = pkg.trim();
                if (!trimmed.isEmpty()) {
                    list.add(trimmed);
                }
            }
        }
        return list;
    }

    /** Checks if bean should be skipped during scanning. */
    private boolean shouldSkipBean(
            ApplicationContext applicationContext,
            String beanName,
            List<String> excludedPackageList) {
        if (!(applicationContext instanceof BeanDefinitionRegistry registry)) {
            return false;
        }

        // Skip if not a bean definition
        if (!registry.containsBeanDefinition(beanName)) {
            return true;
        }

        BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);

        // Skip excluded package beans
        String beanClassName = beanDefinition.getBeanClassName();
        if (isExcluded(excludedPackageList, beanClassName)) {
            logger.debug("Skipping excluded package bean: {} (class: {})", beanName, beanClassName);
            return true;
        }

        // Skip lazy-init beans
        if (beanDefinition.isLazyInit()) {
            logger.debug("Skipping lazy-init bean: {}", beanName);
            return true;
        }

        return false;
    }

    /** Finds all @OrthJob annotated methods in a bean. */
    private Map<Method, OrthJob> findAnnotatedMethods(
            ApplicationContext applicationContext, String beanName) {
        Class<?> beanClass = applicationContext.getType(beanName, false);
        if (beanClass == null) {
            logger.debug("Skipping bean with null class: {}", beanName);
            return null;
        }

        try {
            return MethodIntrospector.selectMethods(
                    beanClass,
                    (Method method) ->
                            AnnotatedElementUtils.findMergedAnnotation(method, OrthJob.class));
        } catch (Throwable ex) {
            logger.error("Failed to resolve @OrthJob methods for bean: {}", beanName, ex);
            return null;
        }
    }

    /**
     * Checks if bean class is in an excluded package.
     *
     * @param excludedPackageList list of package prefixes to exclude
     * @param beanClassName fully qualified class name
     * @return true if bean should be excluded from scanning
     */
    private boolean isExcluded(List<String> excludedPackageList, String beanClassName) {
        if (excludedPackageList == null || excludedPackageList.isEmpty() || beanClassName == null) {
            return false;
        }

        return excludedPackageList.stream().anyMatch(beanClassName::startsWith);
    }

    // ---------------------- Spring Context ----------------------

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        OrthJobSpringExecutor.applicationContext = applicationContext;
    }

    /**
     * Gets the Spring application context.
     *
     * @return application context
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
