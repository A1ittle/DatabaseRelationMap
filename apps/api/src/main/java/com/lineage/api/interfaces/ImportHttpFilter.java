package com.lineage.api.interfaces;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lineage.api.interfaces.dto.ErrorBody;

/**
 * CSRF (any non-empty token in open/demo mode) for mutating import and lineage
 * routes, 50 MiB payload cap, and optional {@code X-Embed-Groups}. OIDC is out
 * of scope — see {@code lineage.security.mode}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ImportHttpFilter extends OncePerRequestFilter {

	static final long MAX_BYTES = 50L * 1024L * 1024L;

	private final ObjectMapper objectMapper;
	private final String securityMode;

	public ImportHttpFilter(ObjectMapper objectMapper,
			@Value("${lineage.security.mode:open}") String securityMode) {
		this.objectMapper = objectMapper;
		this.securityMode = securityMode == null ? "open" : securityMode.trim();
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (path == null) {
			return true;
		}
		return !(path.startsWith("/api/imports") || path.startsWith("/api/lineage"));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		List<String> groups = EmbedGroups.parse(request.getHeader(EmbedGroups.HEADER));
		if (groups != null) {
			request.setAttribute(EmbedGroups.PRESENT_ATTR, Boolean.TRUE);
			request.setAttribute(EmbedGroups.ATTR, groups);
		}
		if ("POST".equalsIgnoreCase(request.getMethod())) {
			String lengthHeader = request.getHeader("Content-Length");
			if (lengthHeader != null) {
				try {
					long length = Long.parseLong(lengthHeader.trim());
					if (length > MAX_BYTES) {
						writeError(request, response, 413, "PAYLOAD_TOO_LARGE", false,
							"request body exceeds 50 MiB");
						return;
					}
				}
				catch (NumberFormatException ignored) {
					writeError(request, response, 400, "INVALID_ARGUMENT", false, "Content-Length is invalid");
					return;
				}
			}
			String csrf = request.getHeader("X-CSRF-Token");
			if (csrf == null || csrf.trim().isEmpty()) {
				writeError(request, response, 400, "INVALID_ARGUMENT", false, "X-CSRF-Token is required");
				return;
			}
		}
		if (!allow(request, response)) {
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean allow(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String mode = securityMode.toLowerCase();
		if ("open".equals(mode) || mode.isEmpty()) {
			return true;
		}
		if ("demo-header".equals(mode)) {
			String user = request.getHeader("X-Lineage-Demo-User");
			if (user == null || user.trim().isEmpty()) {
				writeError(request, response, 401, "UNAUTHENTICATED", false,
					"X-Lineage-Demo-User is required in demo-header mode");
				return false;
			}
			return true;
		}
		writeError(request, response, 401, "UNAUTHENTICATED", false,
			"OIDC is not implemented; set lineage.security.mode=open for local/dev");
		return false;
	}

	private void writeError(HttpServletRequest request, HttpServletResponse response, int status, String code,
			boolean retryable, String message) throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		ErrorBody body = new ErrorBody(code, message, RequestIdFilter.from(request), retryable);
		objectMapper.writeValue(response.getOutputStream(), body);
	}
}
