// package com.xxl.job.admin.util;
//
// import jakarta.servlet.http.Cookie;
// import jakarta.servlet.http.HttpServletRequest;
// import jakarta.servlet.http.HttpServletResponse;
//
/// **
// * Cookie utility for session management.
// *
// * @author xuxueli 2015-12-12 18:01:06
// * @deprecated This utility is deprecated. Use Spring Security's session management or
// *             JWT-based authentication instead.
// *             Migration: Replace cookie-based session storage with stateless JWT tokens.
// *             For SSO integration, see {@link com.xxl.job.admin.web.xxlsso.XxlSsoConfig}.
// *             Orth prefers token-based authentication for better scalability in distributed
// *             deployments.
// */
// @Deprecated
// public class CookieUtil {
//
//	// Default cache time, unit: seconds, 2H
//	private static final int COOKIE_MAX_AGE = Integer.MAX_VALUE;
//	// Save path, root path
//	private static final String COOKIE_PATH = "/";
//
//	/**
//	 * Save cookie
//	 *
//	 * @param response
//	 * @param key
//	 * @param value
//	 * @param ifRemember
//	 */
//	public static void set(HttpServletResponse response, String key, String value, boolean
// ifRemember) {
//		int age = ifRemember?COOKIE_MAX_AGE:-1;
//		set(response, key, value, null, COOKIE_PATH, age, true);
//	}
//
//	/**
//	 * Save cookie with options
//	 *
//	 * @param response
//	 * @param key
//	 * @param value
//	 * @param maxAge
//	 */
//	private static void set(HttpServletResponse response, String key, String value, String domain,
// String path, int maxAge, boolean isHttpOnly) {
//		Cookie cookie = new Cookie(key, value);
//		if (domain != null) {
//			cookie.setDomain(domain);
//		}
//		cookie.setPath(path);
//		cookie.setMaxAge(maxAge);
//		cookie.setHttpOnly(isHttpOnly);
//		response.addCookie(cookie);
//	}
//
//	/**
//	 * Get cookie value
//	 *
//	 * @param request
//	 * @param key
//	 * @return
//	 */
//	public static String getValue(HttpServletRequest request, String key) {
//		Cookie cookie = get(request, key);
//		if (cookie != null) {
//			return cookie.getValue();
//		}
//		return null;
//	}
//
//	/**
//	 * Get cookie object
//	 *
//	 * @param request
//	 * @param key
//	 */
//	private static Cookie get(HttpServletRequest request, String key) {
//		Cookie[] arr_cookie = request.getCookies();
//		if (arr_cookie != null && arr_cookie.length > 0) {
//			for (Cookie cookie : arr_cookie) {
//				if (cookie.getName().equals(key)) {
//					return cookie;
//				}
//			}
//		}
//		return null;
//	}
//
//	/**
//	 * Remove cookie
//	 *
//	 * @param request
//	 * @param response
//	 * @param key
//	 */
//	public static void remove(HttpServletRequest request, HttpServletResponse response, String key) {
//		Cookie cookie = get(request, key);
//		if (cookie != null) {
//			set(response, key, "", null, COOKIE_PATH, 0, true);
//		}
//	}
//
// }
