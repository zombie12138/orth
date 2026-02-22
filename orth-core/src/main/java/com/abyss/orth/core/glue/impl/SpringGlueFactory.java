package com.abyss.orth.core.glue.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotationUtils;

import com.abyss.orth.core.executor.impl.OrthJobSpringExecutor;
import com.abyss.orth.core.glue.GlueFactory;

import jakarta.annotation.Resource;

/**
 * Spring-aware GLUE factory with automatic dependency injection.
 *
 * <p>This factory extends {@link GlueFactory} to provide Spring bean injection into dynamically
 * compiled GLUE job handlers. It scans GLUE instance fields for {@link Resource} and {@link
 * Autowired} annotations and injects matching beans from the Spring application context.
 *
 * <p>Supported injection annotations:
 *
 * <ul>
 *   <li>{@link Resource} - Injects by bean name (from annotation) or field name, fallback to type
 *   <li>{@link Autowired} + {@link Qualifier} - Injects by qualifier value or type
 * </ul>
 *
 * <p>Example GLUE script with Spring injection:
 *
 * <pre>{@code
 * import com.abyss.orth.core.handler.IJobHandler;
 * import jakarta.annotation.Resource;
 *
 * public class MyGlueJob extends IJobHandler {
 *     @Resource
 *     private UserService userService;  // Auto-injected by Spring factory
 *
 *     @Override
 *     public void execute() throws Exception {
 *         userService.processUsers();
 *     }
 * }
 * }</pre>
 *
 * @author xuxueli 2018-11-01
 * @see GlueFactory
 * @see OrthJobSpringExecutor
 */
public class SpringGlueFactory extends GlueFactory {
    private static final Logger logger = LoggerFactory.getLogger(SpringGlueFactory.class);

    /**
     * Injects Spring beans into GLUE instance fields.
     *
     * <p>Process for each field:
     *
     * <ol>
     *   <li>Skip static fields
     *   <li>Check for {@link Resource} annotation:
     *       <ul>
     *         <li>Try by bean name from annotation
     *         <li>Fallback to field name
     *         <li>Fallback to field type
     *       </ul>
     *   <li>Check for {@link Autowired} annotation:
     *       <ul>
     *         <li>Try by {@link Qualifier} value if present
     *         <li>Fallback to field type
     *       </ul>
     *   <li>Inject resolved bean via reflection
     * </ol>
     *
     * @param instance GLUE job handler instance to inject dependencies into
     */
    @Override
    public void injectService(Object instance) {
        if (instance == null) {
            return;
        }

        if (OrthJobSpringExecutor.getApplicationContext() == null) {
            logger.warn(
                    "Spring application context not available, skipping dependency injection for"
                            + " GLUE instance");
            return;
        }

        Field[] fields = instance.getClass().getDeclaredFields();
        for (Field field : fields) {
            // Skip static fields
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            Object fieldBean = resolveFieldBean(field);

            if (fieldBean != null) {
                injectFieldBean(instance, field, fieldBean);
            }
        }
    }

    /**
     * Resolves a Spring bean for a given field based on {@link Resource} or {@link Autowired}
     * annotations.
     *
     * @param field field to resolve bean for
     * @return resolved bean instance or null if not found
     */
    private Object resolveFieldBean(Field field) {
        // Try @Resource annotation
        Resource resource = AnnotationUtils.getAnnotation(field, Resource.class);
        if (resource != null) {
            return resolveByResource(field, resource);
        }

        // Try @Autowired annotation
        Autowired autowired = AnnotationUtils.getAnnotation(field, Autowired.class);
        if (autowired != null) {
            return resolveByAutowired(field);
        }

        return null;
    }

    /**
     * Resolves bean by {@link Resource} annotation.
     *
     * <p>Resolution order:
     *
     * <ol>
     *   <li>By bean name from {@link Resource#name()}
     *   <li>By field name
     *   <li>By field type
     * </ol>
     *
     * @param field field to resolve
     * @param resource Resource annotation
     * @return resolved bean or null
     */
    private Object resolveByResource(Field field, Resource resource) {
        try {
            // Try by explicit bean name from annotation
            if (resource.name() != null && !resource.name().isEmpty()) {
                return OrthJobSpringExecutor.getApplicationContext().getBean(resource.name());
            }

            // Try by field name
            return OrthJobSpringExecutor.getApplicationContext().getBean(field.getName());
        } catch (Exception e) {
            // Fallback to type-based lookup
        }

        // Fallback: lookup by field type
        try {
            return OrthJobSpringExecutor.getApplicationContext().getBean(field.getType());
        } catch (Exception e) {
            logger.debug(
                    "Failed to resolve bean for field {} by Resource annotation", field.getName());
            return null;
        }
    }

    /**
     * Resolves bean by {@link Autowired} annotation.
     *
     * <p>Resolution order:
     *
     * <ol>
     *   <li>By {@link Qualifier} value if present
     *   <li>By field type
     * </ol>
     *
     * @param field field to resolve
     * @return resolved bean or null
     */
    private Object resolveByAutowired(Field field) {
        try {
            // Check for @Qualifier annotation
            Qualifier qualifier = AnnotationUtils.getAnnotation(field, Qualifier.class);
            if (qualifier != null && qualifier.value() != null && !qualifier.value().isEmpty()) {
                return OrthJobSpringExecutor.getApplicationContext().getBean(qualifier.value());
            }

            // Fallback: lookup by field type
            return OrthJobSpringExecutor.getApplicationContext().getBean(field.getType());
        } catch (Exception e) {
            logger.debug(
                    "Failed to resolve bean for field {} by Autowired annotation", field.getName());
            return null;
        }
    }

    /**
     * Injects a resolved bean into a field via reflection.
     *
     * @param instance target instance
     * @param field field to inject into
     * @param fieldBean bean to inject
     */
    private void injectFieldBean(Object instance, Field field, Object fieldBean) {
        try {
            field.setAccessible(true);
            field.set(instance, fieldBean);
            logger.debug("Injected bean into field {}: {}", field.getName(), fieldBean.getClass());
        } catch (Exception e) {
            logger.error(
                    "Failed to inject bean into field {}: {}", field.getName(), e.getMessage(), e);
        }
    }
}
