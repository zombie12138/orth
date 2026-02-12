// package com.xxl.job.admin.web.interceptor;
//
// import com.xxl.job.admin.util.I18nUtil;
// import com.xxl.tool.freemarker.FtlTool;
// import jakarta.servlet.http.HttpServletRequest;
// import jakarta.servlet.http.HttpServletResponse;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.web.servlet.AsyncHandlerInterceptor;
// import org.springframework.web.servlet.ModelAndView;
// import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
// import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//
/// **
// * Common data interceptor for Freemarker template integration.
// *
// * @author xuxueli 2015-12-12 18:09:04
// * @deprecated This interceptor is deprecated. Use Spring Boot's built-in internationalization
// *             support with {@link com.xxl.job.admin.util.I18nUtil} directly in controllers.
// *             Migration: Remove Freemarker static model injection and use MessageSource or
// *             I18nUtil.getString() in controller methods. Orth has moved away from server-side
// *             template rendering to API-based architecture.
// */
// @Deprecated
// @Configuration
// public class CommonDataInterceptor implements WebMvcConfigurer {
//
//	@Override
//	public void addInterceptors(InterceptorRegistry registry) {
//		registry.addInterceptor(new AsyncHandlerInterceptor() {
//			@Override
//			public void postHandle(HttpServletRequest request,
//								   HttpServletResponse response,
//								   Object handler,
//								   ModelAndView modelAndView) throws Exception {
//
//				// static method
//				if (modelAndView != null) {
//					modelAndView.addObject("I18nUtil", FtlTool.generateStaticModel(I18nUtil.class.getName()));
//				}
//
//			}
//		}).addPathPatterns("/**");
//	}
//
// }
