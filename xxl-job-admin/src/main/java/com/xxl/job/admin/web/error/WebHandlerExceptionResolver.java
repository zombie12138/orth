package com.xxl.job.admin.web.error;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import com.xxl.job.admin.scheduler.exception.XxlJobException;
import com.xxl.tool.gson.GsonTool;
import com.xxl.tool.response.Response;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Global exception handler for Orth admin web layer.
 *
 * <p>Provides unified exception handling for both JSON API endpoints and traditional web pages.
 * Returns appropriate error responses based on the request type:
 *
 * <ul>
 *   <li>JSON endpoints (annotated with @ResponseBody): Returns JSON error response
 *   <li>Web pages: Forwards to error page view with exception details
 * </ul>
 *
 * <p>Expected exceptions (XxlJobException) are logged at debug level, while unexpected exceptions
 * are logged as errors with full stack traces.
 *
 * @author xuxueli 2016-1-6 19:22:18
 */
@Component
public class WebHandlerExceptionResolver implements HandlerExceptionResolver {
    private static final Logger logger = LoggerFactory.getLogger(WebHandlerExceptionResolver.class);
    private static final String ERROR_VIEW = "common/common.errorpage";
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    /**
     * Resolves exceptions thrown during request handling.
     *
     * <p>Determines response format based on handler type and returns appropriate error
     * representation (JSON or view).
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param handler request handler that threw the exception
     * @param ex exception that was thrown
     * @return ModelAndView for error display
     */
    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        logException(ex);

        boolean isJsonRequest = isJsonRequest(handler);
        return isJsonRequest ? handleJsonError(response, ex) : handleViewError(ex);
    }

    /**
     * Logs exception with appropriate level.
     *
     * <p>Expected business exceptions are not logged (or logged at debug level), while unexpected
     * exceptions are logged as errors.
     *
     * @param ex exception to log
     */
    private void logException(Exception ex) {
        if (!(ex instanceof XxlJobException)) {
            logger.error("Unhandled exception in web layer: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Determines if request expects JSON response.
     *
     * @param handler request handler
     * @return true if handler is annotated with @ResponseBody
     */
    private boolean isJsonRequest(Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return false;
        }

        HandlerMethod method = (HandlerMethod) handler;
        return method.getMethodAnnotation(ResponseBody.class) != null;
    }

    /**
     * Handles exception for JSON API endpoints.
     *
     * <p>Writes JSON error response with HTTP 200 status and error details.
     *
     * @param response HTTP response
     * @param ex exception to handle
     * @return empty ModelAndView
     */
    private ModelAndView handleJsonError(HttpServletResponse response, Exception ex) {
        try {
            String errorJson = GsonTool.toJson(Response.ofFail(ex.toString()));
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(JSON_CONTENT_TYPE);
            response.getWriter().println(errorJson);
        } catch (IOException e) {
            logger.error("Failed to write JSON error response: {}", e.getMessage(), e);
        }
        return new ModelAndView();
    }

    /**
     * Handles exception for web page requests.
     *
     * <p>Forwards to error page view with exception message.
     *
     * @param ex exception to handle
     * @return ModelAndView with error page and exception details
     */
    private ModelAndView handleViewError(Exception ex) {
        ModelAndView mv = new ModelAndView();
        mv.addObject("exceptionMsg", ex.toString());
        mv.setViewName(ERROR_VIEW);
        return mv;
    }
}
