package com.xxl.job.admin.web.xxlsso;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.xxl.sso.core.auth.interceptor.XxlSsoWebInterceptor;
import com.xxl.sso.core.bootstrap.XxlSsoBootstrap;

import jakarta.annotation.Resource;

/**
 * Single Sign-On (SSO) configuration for Orth admin.
 *
 * <p>Configures the SSO bootstrap and web interceptor for session management and authentication.
 * The interceptor validates user sessions on each request, excluding configured paths (e.g., login
 * page, static resources).
 *
 * <p>Configuration properties:
 *
 * <ul>
 *   <li>xxl-sso.token.key: Encryption key for session tokens
 *   <li>xxl-sso.token.timeout: Session timeout in milliseconds
 *   <li>xxl-sso.client.excluded.paths: Paths to exclude from authentication
 *   <li>xxl-sso.client.login.path: Login page path for redirects
 * </ul>
 *
 * @author xuxueli 2018-11-15
 */
@Configuration
public class XxlSsoConfig implements WebMvcConfigurer {

    @Value("${xxl-sso.token.key}")
    private String tokenKey;

    @Value("${xxl-sso.token.timeout}")
    private long tokenTimeout;

    @Value("${xxl-sso.client.excluded.paths}")
    private String excludedPaths;

    @Value("${xxl-sso.client.login.path}")
    private String loginPath;

    @Resource private SimpleLoginStore loginStore;

    /**
     * Configures SSO bootstrap with login store and token settings.
     *
     * <p>The bootstrap manages session lifecycle including token generation, validation, and
     * expiration. It is initialized on application startup and destroyed on shutdown.
     *
     * @return configured SSO bootstrap
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public XxlSsoBootstrap xxlSsoBootstrap() {
        XxlSsoBootstrap bootstrap = new XxlSsoBootstrap();
        bootstrap.setLoginStore(loginStore);
        bootstrap.setTokenKey(tokenKey);
        bootstrap.setTokenTimeout(tokenTimeout);
        return bootstrap;
    }

    /**
     * Registers SSO web interceptor for authentication.
     *
     * <p>The interceptor validates session tokens on all requests except excluded paths.
     * Unauthenticated requests are redirected to the login page.
     *
     * @param registry interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        XxlSsoWebInterceptor webInterceptor = new XxlSsoWebInterceptor(excludedPaths, loginPath);
        registry.addInterceptor(webInterceptor).addPathPatterns("/**");
    }
}
