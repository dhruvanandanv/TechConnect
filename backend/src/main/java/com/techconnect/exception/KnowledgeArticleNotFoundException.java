package com.techconnect.exception;

public class KnowledgeArticleNotFoundException extends RuntimeException {
    public KnowledgeArticleNotFoundException(String message) {
        super(message);
    }
}
