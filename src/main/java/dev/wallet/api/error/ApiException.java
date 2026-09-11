package dev.wallet.api.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final String field;

	public ApiException(HttpStatus status, String code, String message) {
		this(status, code, message, null);
	}

	public ApiException(HttpStatus status, String code, String message, String field) {
		super(message);
		this.status = status;
		this.code = code;
		this.field = field;
	}

	public static ApiException malformedJson() {
		return new ApiException(HttpStatus.BAD_REQUEST, "malformed_body",
				"Request body is not valid JSON.");
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public String getField() {
		return field;
	}
}