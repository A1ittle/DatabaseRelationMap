package com.lineage.api.interfaces;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CORS for the Vue 2 Vite shell (and host iframes). Only exact allowlisted
 * origins receive {@code Access-Control-Allow-Origin}. Never echo an arbitrary
 * {@code Origin}. Prefer same-origin reverse-proxy (no {@code Origin} header,
 * no ACAO). Credentials are header-based ({@code X-CSRF-Token},
 * {@code X-Embed-Groups}), not cookies.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsFilter extends OncePerRequestFilter {

	private final Set<String> allowedOrigins;

	public CorsFilter(
			@Value("${lineage.cors.allowed-origins:http://127.0.0.1:5173,http://localhost:5173,http://127.0.0.1:4173,http://localhost:4173}") String allowedOrigins) {
		this.allowedOrigins = parse(allowedOrigins);
	}

	static Set<String> parse(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return Collections.emptySet();
		}
		Set<String> set = new LinkedHashSet<String>();
		String[] parts = raw.split(",");
		for (int i = 0; i < parts.length; i++) {
			String origin = parts[i].trim();
			if (!origin.isEmpty()) {
				set.add(origin);
			}
		}
		return Collections.unmodifiableSet(set);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String origin = request.getHeader("Origin");
		if (origin != null && !origin.isEmpty() && allowedOrigins.contains(origin)) {
			response.setHeader("Access-Control-Allow-Origin", origin);
			response.setHeader("Vary", "Origin");
			response.setHeader("Access-Control-Allow-Headers",
				"Content-Type, X-CSRF-Token, X-Embed-Groups, X-Request-Id, X-Lineage-Demo-User");
			response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
			response.setHeader("Access-Control-Max-Age", "600");
		}
		else if (origin != null && !origin.isEmpty()) {
			response.setHeader("Vary", "Origin");
		}
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			response.setStatus(HttpServletResponse.SC_NO_CONTENT);
			return;
		}
		filterChain.doFilter(request, response);
	}
}
