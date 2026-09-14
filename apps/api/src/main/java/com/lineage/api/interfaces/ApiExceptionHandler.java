package com.lineage.api.interfaces;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.lineage.api.application.ApiException;
import com.lineage.api.interfaces.dto.ErrorBody;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorBody> handleApi(ApiException ex, HttpServletRequest request) {
		return ResponseEntity.status(ex.getStatus())
			.body(new ErrorBody(ex.getCode(), ex.getMessage(), RequestIdFilter.from(request), ex.isRetryable()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorBody> handleUnreadable(HttpMessageNotReadableException ex,
			HttpServletRequest request) {
		String path = request.getRequestURI();
		String code = path != null && path.startsWith("/api/imports") ? "IMPORT_INVALID" : "INVALID_ARGUMENT";
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new ErrorBody(code, "request body is not valid JSON", RequestIdFilter.from(request), false));
	}

	@ExceptionHandler({ MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ErrorBody> handleBadRequest(Exception ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new ErrorBody("INVALID_ARGUMENT", ex.getMessage(), RequestIdFilter.from(request), false));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorBody> handleUnexpected(Exception ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
			.body(new ErrorBody("TEMPORARILY_UNAVAILABLE", "unexpected server error", RequestIdFilter.from(request),
				true));
	}
}
