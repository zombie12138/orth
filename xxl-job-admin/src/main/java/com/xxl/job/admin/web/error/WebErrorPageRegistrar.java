package com.xxl.job.admin.web.error;

import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.boot.web.server.ErrorPageRegistry;
import org.springframework.stereotype.Component;

/**
 * Custom error page registrar for Orth admin.
 *
 * <p>Registers a global error page handler that redirects all error responses to a unified error
 * page. This provides consistent error handling across the application for HTTP errors (404, 500,
 * etc.).
 *
 * <p>The error page endpoint should be implemented in a controller to display appropriate error
 * information to users.
 *
 * @author xuxueli 2018-01-17
 */
@Component
public class WebErrorPageRegistrar implements ErrorPageRegistrar {

    private static final String ERROR_PAGE_PATH = "/errorpage";

    /**
     * Registers the global error page.
     *
     * <p>All unhandled errors will be forwarded to this path for consistent error display.
     *
     * @param registry error page registry
     */
    @Override
    public void registerErrorPages(ErrorPageRegistry registry) {
        ErrorPage errorPage = new ErrorPage(ERROR_PAGE_PATH);
        registry.addErrorPages(errorPage);
    }
}
