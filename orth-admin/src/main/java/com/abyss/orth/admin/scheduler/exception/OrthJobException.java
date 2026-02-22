package com.abyss.orth.admin.scheduler.exception;

/**
 * Custom runtime exception for the Orth job scheduling framework.
 *
 * <p>This exception is used throughout the scheduler to signal business logic errors, validation
 * failures, and recoverable runtime errors. As an unchecked exception, it allows failures to
 * propagate without forcing explicit exception handling at every level.
 *
 * <p>Common scenarios for this exception:
 *
 * <ul>
 *   <li>Invalid job configuration or parameters
 *   <li>Schedule calculation failures
 *   <li>Executor routing errors
 *   <li>Misfire handling issues
 * </ul>
 *
 * @author xuxueli 2019-05-04
 */
public class OrthJobException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Creates a new exception with no message or cause. */
    public OrthJobException() {
        super();
    }

    /**
     * Creates a new exception with the specified detail message.
     *
     * @param message the detail message explaining the error
     */
    public OrthJobException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the error
     * @param cause the underlying cause of this exception
     */
    public OrthJobException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception with the specified cause.
     *
     * @param cause the underlying cause of this exception
     */
    public OrthJobException(Throwable cause) {
        super(cause);
    }
}
