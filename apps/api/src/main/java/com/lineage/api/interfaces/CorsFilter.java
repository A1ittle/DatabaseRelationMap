package com.lineage.api.interfaces;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Allow the Vue 2 Vite shell (and host iframes) to call {@code /api} in open /
 * demo-header mode. Credentials are header-based ({@code X-CSRF-Token},
 * {@code X-Embed-Groups}), not cookies.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String origin = request.getHeader("Origin");
		if (origin != null && !origin.trim().isEmpty()) {
			response.setHeader("Access-Control-Allow-Origin", origin);
			response.setHeader("Vary", "Origin");
			response.setHeader("Access-Control-Allow-Headers",
				"Content-Type, X-CSRF-Token, X-Embed-Groups, X-Request-Id, X-Lineage-Demo-User");
			response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
			response.setHeader("Access-Control-Max-Age", "600");
		}
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			response.setStatus(HttpServletResponse.SC_NO_CONTENT);
			return;
		}
		filterChain.doFilter(request, response);
	}
}
