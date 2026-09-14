package com.lineage.api.interfaces;

import java.io.IOException;
import java.util.UUID;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String ATTR = "lineage.requestId";
	public static final String HEADER = "X-Request-Id";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = request.getHeader(HEADER);
		if (requestId == null || requestId.trim().isEmpty() || requestId.length() > 200) {
			requestId = UUID.randomUUID().toString();
		}
		request.setAttribute(ATTR, requestId);
		response.setHeader(HEADER, requestId);
		filterChain.doFilter(request, response);
	}

	public static String from(HttpServletRequest request) {
		if (request == null) {
			return "unknown";
		}
		Object value = request.getAttribute(ATTR);
		if (value instanceof String && !((String) value).isEmpty()) {
			return (String) value;
		}
		return "unknown";
	}
}
