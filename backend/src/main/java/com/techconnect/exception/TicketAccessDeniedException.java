package com.techconnect.exception;

public class TicketAccessDeniedException extends RuntimeException {
    public TicketAccessDeniedException(String message) {
        super(message);
    }
}
