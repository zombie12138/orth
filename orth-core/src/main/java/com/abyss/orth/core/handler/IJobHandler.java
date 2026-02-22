package com.abyss.orth.core.handler;

/**
 * Abstract base class for job handlers.
 *
 * <p>Defines the lifecycle contract for job execution: {@code init()} → {@code execute()} → {@code
 * destroy()}. Implementations should use {@link com.abyss.orth.core.context.OrthJobHelper} to
 * access job context and log execution details.
 */
public abstract class IJobHandler {

    /**
     * Executes the job logic.
     *
     * <p>Invoked by the executor when a scheduling request is received. Use {@link
     * com.abyss.orth.core.context.OrthJobHelper} to access job parameters, log messages, and set
     * result status.
     *
     * @throws Exception if job execution fails
     */
    public abstract void execute() throws Exception;

    /**
     * Initializes the handler.
     *
     * <p>Invoked once when the JobThread starts, before the first execution. Override to perform
     * setup tasks like resource allocation or connection initialization.
     *
     * @throws Exception if initialization fails
     */
    public void init() throws Exception {
        // Default: no initialization needed
    }

    /**
     * Destroys the handler.
     *
     * <p>Invoked once when the JobThread stops, after the last execution. Override to perform
     * cleanup tasks like resource release or connection closure.
     *
     * @throws Exception if cleanup fails
     */
    public void destroy() throws Exception {
        // Default: no cleanup needed
    }
}
