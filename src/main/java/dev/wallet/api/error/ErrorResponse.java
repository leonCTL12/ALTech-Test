package dev.wallet.api.error;

public record ErrorResponse(String code, String message, String field) {
}