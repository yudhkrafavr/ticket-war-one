package com.ticket_war.war.exception;

import java.time.Instant;

public record ApiErrorResponse(Instant timestamp, int status, String error, String message) {
}
