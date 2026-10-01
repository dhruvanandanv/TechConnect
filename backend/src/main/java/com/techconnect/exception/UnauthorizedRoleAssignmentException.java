package com.techconnect.exception;

public class UnauthorizedRoleAssignmentException extends RuntimeException {
    public UnauthorizedRoleAssignmentException(String message) {
        super(message);
    }
}
