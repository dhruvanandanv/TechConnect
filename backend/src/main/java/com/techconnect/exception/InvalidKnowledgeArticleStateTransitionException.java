package com.techconnect.exception;

public class InvalidKnowledgeArticleStateTransitionException extends RuntimeException {
    public InvalidKnowledgeArticleStateTransitionException(String message) {
        super(message);
    }
}
