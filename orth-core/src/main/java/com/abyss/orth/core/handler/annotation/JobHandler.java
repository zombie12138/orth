package com.abyss.orth.core.handler.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking job handler classes (DEPRECATED).
 *
 * <p>This annotation was used in Orth versions prior to 2.0 to mark classes as job handlers. It has
 * been replaced by {@link OrthJob} which provides method-level granularity and better Spring
 * integration.
 *
 * <p><strong>Migration Guide:</strong>
 *
 * <pre>{@code
 * // Old approach (deprecated):
 * @JobHandler(value = "myJobHandler")
 * public class MyJobHandler extends IJobHandler {
 *     @Override
 *     public void execute() throws Exception {
 *         // job logic
 *     }
 * }
 *
 * // New approach (recommended):
 * @Component
 * public class MyJobHandlers {
 *     @OrthJob("myJobHandler")
 *     public void myJob() {
 *         // job logic
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Deprecation Timeline:</strong>
 *
 * <ul>
 *   <li>Deprecated since: 2.0.0 (2019-01-18)
 *   <li>Will be removed in: 4.0.0
 *   <li>Replacement: {@link OrthJob}
 * </ul>
 *
 * @author xuxueli
 * @since 1.0.0
 * @deprecated Use {@link OrthJob} instead for method-level job handler registration
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Deprecated
public @interface JobHandler {

    /**
     * Job handler name used for routing job executions.
     *
     * <p>This name must match the 'JobHandler' field in the admin console job configuration. The
     * name should be unique across all job handlers in the same executor instance.
     *
     * <p><strong>Naming conventions:</strong>
     *
     * <ul>
     *   <li>Use camelCase format (e.g., "dataCollector", "emailNotifier")
     *   <li>Avoid special characters except underscores
     *   <li>Keep length under 255 characters
     *   <li>Must not be empty or null
     * </ul>
     *
     * @return the unique job handler name
     */
    String value();
}
