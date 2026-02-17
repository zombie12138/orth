package com.xxl.job.admin.web.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.xxl.job.admin.scheduler.exception.XxlJobException;
import com.xxl.tool.response.Response;

/**
 * Global exception handler for Orth admin REST API.
 *
 * <p>Provides unified JSON error responses for all exceptions. Expected business exceptions
 * (XxlJobException) are logged at debug level, while unexpected exceptions are logged as errors.
 *
 * @author xuxueli 2016-1-6 19:22:18
 */
@RestControllerAdvice
public class WebHandlerExceptionResolver {

    private static final Logger logger = LoggerFactory.getLogger(WebHandlerExceptionResolver.class);

    @ExceptionHandler(XxlJobException.class)
    public Response<String> handleXxlJobException(XxlJobException ex) {
        logger.debug("Business exception: {}", ex.getMessage());
        return Response.ofFail(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Response<String> handleException(Exception ex) {
        logger.error("Unhandled exception in web layer: {}", ex.getMessage(), ex);
        return Response.ofFail(ex.toString());
    }
}
