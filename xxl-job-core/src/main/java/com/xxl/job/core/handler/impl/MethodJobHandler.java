package com.xxl.job.core.handler.impl;

import java.lang.reflect.Method;

import com.xxl.job.core.handler.IJobHandler;

/**
 * Method-based job handler that executes annotated methods via reflection.
 *
 * <p>Wraps a Spring bean method annotated with {@code @XxlJob} and invokes it when the job is
 * triggered. Supports optional init/destroy lifecycle methods.
 */
public class MethodJobHandler extends IJobHandler {

    private final Object target;
    private final Method method;
    private final Method initMethod;
    private final Method destroyMethod;

    /**
     * Constructs a method-based job handler.
     *
     * @param target the bean instance containing the job method
     * @param method the job execution method
     * @param initMethod optional initialization method (may be null)
     * @param destroyMethod optional cleanup method (may be null)
     */
    public MethodJobHandler(Object target, Method method, Method initMethod, Method destroyMethod) {
        this.target = target;
        this.method = method;
        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;
    }

    @Override
    public void execute() throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();
        // Pass null array for methods with parameters (cannot be primitive types)
        Object[] args = paramTypes.length > 0 ? new Object[paramTypes.length] : null;
        method.invoke(target, args);
    }

    @Override
    public void init() throws Exception {
        if (initMethod != null) {
            initMethod.invoke(target);
        }
    }

    @Override
    public void destroy() throws Exception {
        if (destroyMethod != null) {
            destroyMethod.invoke(target);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "%s[%s#%s]", super.toString(), target.getClass().getName(), method.getName());
    }
}
