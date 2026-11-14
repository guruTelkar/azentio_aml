package com.azentio.aml.api;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(TransactionConflictException.class)
	ResponseEntity<ApiError> transactionConflict(TransactionConflictException exception) {
		return response(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<ApiError> resourceNotFound(ResourceNotFoundException exception) {
		return response(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
	}

	@ExceptionHandler(BadRequestException.class)
	ResponseEntity<ApiError> badRequest(BadRequestException exception) {
		return response(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> validationFailed(MethodArgumentNotValidException exception) {
		Map<String, String> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
				.collect(Collectors.toMap(
						error -> error.getField(),
						error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
						(first, ignored) -> first));
		return response(HttpStatus.BAD_REQUEST, "Request validation failed", fieldErrors);
	}

	@ExceptionHandler({
			HttpMessageNotReadableException.class,
			MethodArgumentTypeMismatchException.class,
			MissingServletRequestParameterException.class
	})
	ResponseEntity<ApiError> malformedRequest(Exception exception) {
		return response(HttpStatus.BAD_REQUEST, "Request contains malformed or invalid values", Map.of());
	}

	private static ResponseEntity<ApiError> response(
			HttpStatus status,
			String message,
			Map<String, String> fieldErrors) {
		return ResponseEntity.status(status).body(new ApiError(
				Instant.now(), status.value(), status.getReasonPhrase(), message, fieldErrors));
	}
}
