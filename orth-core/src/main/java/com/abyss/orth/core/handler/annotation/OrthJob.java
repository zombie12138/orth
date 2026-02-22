package com.abyss.orth.core.handler.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as an Orth job handler.
 *
 * <p>Annotated methods are automatically discovered and registered by {@link
 * com.abyss.orth.core.executor.impl.OrthJobSpringExecutor} during Spring context initialization.
 *
 * <p>Method signature requirements:
 *
 * <ul>
 *   <li>Must be {@code public void}
 *   <li>Can have no parameters, or any parameters (will receive null values)
 *   <li>Access job context via {@link com.abyss.orth.core.context.OrthJobHelper}
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Component
 * public class MyJobHandler {
 *     @OrthJob("myJobName")
 *     public void execute() {
 *         String param = OrthJobHelper.getJobParam();
 *         // ... job logic ...
 *         OrthJobHelper.handleSuccess();
 *     }
 *
 *     public void init() {
 *         // Optional initialization
 *     }
 *
 *     public void destroy() {
 *         // Optional cleanup
 *     }
 * }
 * }</pre>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface OrthJob {

    /**
     * Job handler name (must be unique).
     *
     * <p>This name is used to identify the handler in the admin console when configuring jobs.
     *
     * @return handler name
     */
    String value();

    /**
     * Optional initialization method name.
     *
     * <p>Invoked once when the JobThread starts, before the first execution. The method must:
     *
     * <ul>
     *   <li>Be a no-arg method in the same class
     *   <li>Be named exactly as specified (case-sensitive)
     * </ul>
     *
     * @return initialization method name (empty string if none)
     */
    String init() default "";

    /**
     * Optional cleanup method name.
     *
     * <p>Invoked once when the JobThread stops, after the last execution. The method must:
     *
     * <ul>
     *   <li>Be a no-arg method in the same class
     *   <li>Be named exactly as specified (case-sensitive)
     * </ul>
     *
     * @return cleanup method name (empty string if none)
     */
    String destroy() default "";
}
