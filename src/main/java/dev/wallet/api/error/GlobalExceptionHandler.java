package dev.wallet.api.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
		return toResponse(ex);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException ex) {
		return toResponse(ApiException.malformedJson());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
		log.error("Unhandled exception", ex);
		return toResponse(new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
				"internal_error", "An unexpected error occurred."));
	}

	private ResponseEntity<ErrorResponse> toResponse(ApiException ex) {
		return ResponseEntity.status(ex.getStatus())
				.body(new ErrorResponse(ex.getCode(), ex.getMessage(), ex.getField()));
	}
}